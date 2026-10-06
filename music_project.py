# COMP 4980 - Portfolio Element 2
# Procedural music generator for games
#
# What this program does:
#   1. Makes short pieces of music (8 bars long) using simple rules (no AI).
#   2. There are 4 versions of the generator (A, B, C, D).
#      Each version adds one more rule, so we can see what each rule does.
#   3. Saves the pieces as MIDI files (they open in GarageBand, MuseScore, etc).
#   4. Makes 200 pieces with each version and measures them.
#   5. Checks the generator the way a PCG system should be checked:
#      is it fast, does it follow its rules, and does it still give variety?
#
# How to run it:   python3 music_project.py
# Everything it makes is saved in the "results" folder.

import random   # for random numbers
import os       # for making folders
import csv      # for writing the note lists as .csv files
import time     # for timing how fast the generator is

import numpy as np                 # for averages and standard deviation
import matplotlib
matplotlib.use("Agg")              # save pictures straight to a file (no window pops up)
import matplotlib.pyplot as plt    # for drawing the piano roll pictures
from midiutil import MIDIFile      # for writing MIDI files


# ---------------------------------------------------------------------------
# SETTINGS
# ---------------------------------------------------------------------------

TEMPO = 100          # speed of the music in beats per minute
BARS = 8             # every piece is 8 bars long
BEATS_PER_BAR = 4    # 4 beats in a bar (4/4 time)
NUM_TESTS = 200      # how many pieces we make per version for the comparison
SAMPLE_SEED = 1      # the seed used for the 4 sample MIDI files
LEVELS = 3           # for the game demo we make one piece for each of 3 levels
OUT = "results"      # the folder where everything is saved

# In MIDI every note is a number. 60 is middle C (C4), 61 is C#4, 62 is D4 ...
# Going up 12 numbers = going up one octave, so 72 is C5 and 84 is C6.
LOW = 60             # lowest melody note (C4)
HIGH = 84            # highest melody note (C6)

# The notes of the C major scale (C D E F G A B), counted from C.
# C=0, D=2, E=4, F=5, G=7, A=9, B=11  (the black keys 1, 3, 6, 8, 10 are left out)
C_MAJOR = [0, 2, 4, 5, 7, 9, 11]

# SCALE = every C major note between LOW and HIGH.
# pitch % 12 tells us which note it is (C, D, E ...) no matter the octave.
SCALE = []
for pitch in range(LOW, HIGH + 1):
    if pitch % 12 in C_MAJOR:
        SCALE.append(pitch)
# SCALE is now [60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84]

# Note lengths in beats: 0.5 = eighth note, 1 = quarter note, 2 = half note
DURATIONS = [0.5, 1, 2]
DURATION_WEIGHTS = [3, 5, 2]   # quarter notes are the most common, half notes the least

# How many places to move along the scale from one note to the next.
# -1 means "go down one note in the scale", +2 means "go up two notes", and so on.
STEPS = [-4, -3, -2, -1, 0, 1, 2, 3, 4]
# How likely each move is. A move of 1 step (weight 6) is 6 times as likely
# as a jump of 4 steps (weight 1), so the melody mostly moves to nearby notes.
STEP_WEIGHTS = [1, 1, 3, 6, 2, 6, 3, 1, 1]

# Other weights we try in the "step weight check", to show why we picked STEP_WEIGHTS.
WEIGHT_SETTINGS = {
    "Flat (all equal)": [1, 1, 1, 1, 1, 1, 1, 1, 1],
    "Chosen": STEP_WEIGHTS,
    "Steps only": [0, 0, 0, 6, 2, 6, 0, 0, 0],
}

# The chords, written the same way as C_MAJOR (C=0, D=2, E=4 ...)
CHORDS = {
    "C": [0, 4, 7],    # C E G
    "Am": [9, 0, 4],   # A C E
    "F": [5, 9, 0],    # F A C
    "G": [7, 11, 2],   # G B D
}
# The bass note that is played for each chord (low notes, below the melody)
BASS_NOTE = {"C": 48, "Am": 45, "F": 41, "G": 43}
# The order of the chords. This loop is used in lots of pop songs.
LOOP = ["C", "Am", "F", "G"]

# Note names, used to turn a MIDI number like 61 into a name like "C#4"
NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


# ---------------------------------------------------------------------------
# HELPER FUNCTIONS
#
# In this program a note is a tuple of 3 values: (pitch, start, length)
#   pitch  = the MIDI number of the note, for example 60
#   start  = the beat where the note starts (the first beat of the piece is 0)
#   length = how many beats the note lasts
# A melody is just a list of these notes.
# ---------------------------------------------------------------------------

def note_name(pitch):
    # turns a MIDI number into a note name, for example 60 -> "C4"
    letter = NAMES[pitch % 12]
    octave = pitch // 12 - 1
    return letter + str(octave)


def chord_for_bar(bar):
    # gives back the chord for a bar (bars are counted from 0).
    # The last two bars are always G then C, because that sounds like an ending.
    if bar == BARS - 1:
        return "C"
    if bar == BARS - 2:
        return "G"
    # every other bar just follows the loop C, Am, F, G, C, Am, ...
    return LOOP[bar % len(LOOP)]


def free_rhythm(rng):
    # Rhythm for versions A and B.
    # Each note length is picked with the same chance, and bar lines are ignored.
    total_beats = BARS * BEATS_PER_BAR
    lengths = []
    beat = 0
    while beat < total_beats:
        length = rng.choice(DURATIONS)
        # if the last note would go past the end of the piece, cut it short
        if length > total_beats - beat:
            length = total_beats - beat
        lengths.append(length)
        beat = beat + length
    return lengths


def ruled_rhythm(rng):
    # Rhythm for versions C and D.
    # Note lengths are picked using DURATION_WEIGHTS, and a note is only
    # allowed if it fits in what is left of the bar (so no note crosses a bar line).
    lengths = []
    for bar in range(BARS):
        beats_left = BEATS_PER_BAR
        while beats_left > 0:
            # make a list of the lengths that still fit in this bar
            options = []
            weights = []
            for i in range(len(DURATIONS)):
                if DURATIONS[i] <= beats_left:
                    options.append(DURATIONS[i])
                    weights.append(DURATION_WEIGHTS[i])
            # pick one of them (rng.choices gives back a list, so we take item [0])
            length = rng.choices(options, weights)[0]
            lengths.append(length)
            beats_left = beats_left - length
    return lengths


def add_starts(pitches, lengths):
    # puts a list of pitches and a list of lengths together into a list of notes,
    # working out where each note starts as it goes
    notes = []
    beat = 0
    for i in range(len(pitches)):
        notes.append((pitches[i], beat, lengths[i]))
        beat = beat + lengths[i]
    return notes


# ---------------------------------------------------------------------------
# THE 4 VERSIONS
#
# Each version takes a seed and gives back two lists: (melody, bass).
# The seed starts the random number generator, so the same seed always
# gives the same piece. Versions A, B and C have no bass, so their bass list is empty.
# ---------------------------------------------------------------------------

def version_a(seed):
    # VERSION A: pure random. Any note in the range, any length.
    rng = random.Random(seed)
    lengths = free_rhythm(rng)
    pitches = []
    for length in lengths:
        pitches.append(rng.randint(LOW, HIGH))
    return add_starts(pitches, lengths), []


def version_b(seed):
    # VERSION B: same as A, but the notes are only picked from the C major scale.
    rng = random.Random(seed)
    lengths = free_rhythm(rng)
    pitches = []
    for length in lengths:
        pitches.append(rng.choice(SCALE))
    return add_starts(pitches, lengths), []


def walk(rng, lengths, use_chords, step_weights=STEP_WEIGHTS):
    # Makes the melody for versions C and D with a "weighted random walk":
    # from the current note we move a few places up or down the scale,
    # and small moves are more likely than big ones (see STEP_WEIGHTS).
    #
    # We keep track of a position (index) in SCALE instead of the pitch,
    # so moving "one step up" is just index + 1.

    index = SCALE.index(72)            # start on C5, the middle of the range
    pitches = [SCALE[index]]           # the first note
    beat = lengths[0]                  # the beat where the next note starts

    for i in range(1, len(lengths)):
        # 1. make a list of every place we are allowed to move to
        options = []
        weights = []
        for k in range(len(STEPS)):
            new_index = index + STEPS[k]
            # skip moves with weight 0, and moves that go off the end of the scale
            if step_weights[k] > 0 and new_index >= 0 and new_index < len(SCALE):
                options.append(new_index)
                weights.append(step_weights[k])

        # 2. VERSION D only: if this note starts a new bar, only keep the
        #    options that are notes of that bar's chord
        if use_chords and beat % BEATS_PER_BAR == 0:
            bar = int(beat // BEATS_PER_BAR)
            chord = CHORDS[chord_for_bar(bar)]
            chord_options = []
            chord_weights = []
            for k in range(len(options)):
                if SCALE[options[k]] % 12 in chord:
                    chord_options.append(options[k])
                    chord_weights.append(weights[k])
            # only use the chord notes if there was at least one
            if len(chord_options) > 0:
                options = chord_options
                weights = chord_weights

        # 3. pick where to move, using the weights
        index = rng.choices(options, weights)[0]
        pitches.append(SCALE[index])
        beat = beat + lengths[i]

    # The piece should sound finished, so change the last note
    # to whichever C (60, 72 or 84) is closest to it.
    last = pitches[-1]
    closest_c = 60
    for c in [60, 72, 84]:
        if abs(c - last) < abs(closest_c - last):
            closest_c = c
    pitches[-1] = closest_c

    return pitches


def version_c(seed, step_weights=STEP_WEIGHTS):
    # VERSION C: the weighted walk on the scale + the rhythm rules + end on C.
    # (step_weights can be changed, which is used by the step weight check)
    rng = random.Random(seed)
    lengths = ruled_rhythm(rng)
    pitches = walk(rng, lengths, False, step_weights)
    return add_starts(pitches, lengths), []


def version_d(seed):
    # VERSION D: version C + chords. The first note of each bar has to fit
    # the chord, and a bass line plays one long note per bar.
    rng = random.Random(seed)
    lengths = ruled_rhythm(rng)
    pitches = walk(rng, lengths, True)

    bass = []
    for bar in range(BARS):
        bass_pitch = BASS_NOTE[chord_for_bar(bar)]
        start = bar * BEATS_PER_BAR
        bass.append((bass_pitch, start, BEATS_PER_BAR))   # the note lasts the whole bar

    return add_starts(pitches, lengths), bass


VERSION_NAMES = ["A_random", "B_scale", "C_rules", "D_harmony"]


def make_piece(version_name, seed):
    # calls the right version function for a name from VERSION_NAMES
    if version_name == "A_random":
        return version_a(seed)
    elif version_name == "B_scale":
        return version_b(seed)
    elif version_name == "C_rules":
        return version_c(seed)
    else:
        return version_d(seed)


# ---------------------------------------------------------------------------
# MEASURING THE PIECES
# ---------------------------------------------------------------------------

def note_at(melody, beat):
    # gives back the pitch that is playing at a beat (or None if nothing is playing)
    for pitch, start, length in melody:
        if start <= beat < start + length:
            return pitch
    return None


def measure(melody):
    # Works out all the measurements for one melody and gives them back in a dictionary.

    # a list of just the pitches
    pitches = []
    for pitch, start, length in melody:
        pitches.append(pitch)

    # how far (in semitones) the melody moves from each note to the next one
    moves = []
    for i in range(1, len(pitches)):
        moves.append(abs(pitches[i] - pitches[i - 1]))

    # how many notes are in the key of C major
    in_key = 0
    for pitch in pitches:
        if pitch % 12 in C_MAJOR:
            in_key = in_key + 1

    # how many moves are small (2 semitones or less) and how many are
    # big leaps (more than 7 semitones, so bigger than a fifth)
    small_moves = 0
    big_leaps = 0
    for move in moves:
        if move <= 2:
            small_moves = small_moves + 1
        if move > 7:
            big_leaps = big_leaps + 1

    # how many notes cross a bar line (start in one bar and end in the next).
    # We take 0.001 off the end so a note that ends exactly on the bar line
    # does not count.
    crossing = 0
    for pitch, start, length in melody:
        start_bar = int(start // BEATS_PER_BAR)
        end_bar = int((start + length - 0.001) // BEATS_PER_BAR)
        if start_bar != end_bar:
            crossing = crossing + 1

    # how many bars have a note of that bar's chord playing on beat 1
    on_chord = 0
    for bar in range(BARS):
        pitch = note_at(melody, bar * BEATS_PER_BAR)
        if pitch is not None and pitch % 12 in CHORDS[chord_for_bar(bar)]:
            on_chord = on_chord + 1

    # does the last note end on a C?  (100 = yes, 0 = no, so it can be averaged as a %)
    if pitches[-1] % 12 == 0:
        ends_on_c = 100
    else:
        ends_on_c = 0

    return {
        "In key (%)": 100 * in_key / len(pitches),
        "Avg move (semitones)": np.mean(moves),
        "Small moves (%)": 100 * small_moves / len(moves),
        "Big leaps (%)": 100 * big_leaps / len(moves),
        "Cross bar line (%)": 100 * crossing / len(melody),
        "Ends on C (%)": ends_on_c,
        "Chord tone on beat 1 (%)": 100 * on_chord / BARS,
        "Range (semitones)": max(pitches) - min(pitches),
    }


# the names of the measurements, in the order they are printed
METRIC_NAMES = ["In key (%)", "Avg move (semitones)", "Small moves (%)", "Big leaps (%)",
                "Cross bar line (%)", "Ends on C (%)", "Chord tone on beat 1 (%)", "Range (semitones)"]


def get_values(stats, metric):
    # stats is a list of dictionaries from measure().
    # This gives back a list with just one measurement from each of them.
    values = []
    for s in stats:
        values.append(s[metric])
    return values


def pitch_grid(melody):
    # The note playing on every eighth note of the piece (8 bars x 4 beats x 2 = 64 spots).
    # This lets us compare two pieces spot by spot.
    grid = []
    for i in range(BARS * BEATS_PER_BAR * 2):
        grid.append(note_at(melody, i * 0.5))
    return grid


def difference(pieces):
    # Diversity check: on average, at how many eighth-note spots (in %) do two
    # different pieces play a different note? We compare every pair of the
    # first 100 pieces.
    grids = []
    for melody in pieces[:100]:
        grids.append(pitch_grid(melody))

    diffs = []
    for i in range(len(grids)):
        for j in range(i + 1, len(grids)):   # j starts after i, so each pair is only done once
            same = 0
            for spot in range(len(grids[i])):
                if grids[i][spot] == grids[j][spot]:
                    same = same + 1
            diffs.append(100 * (1 - same / len(grids[i])))
    return np.mean(diffs)


# ---------------------------------------------------------------------------
# SAVING THE RESULTS
# ---------------------------------------------------------------------------

def save_midi(melody, bass, path):
    # writes the melody and the bass to a MIDI file
    midi = MIDIFile(2)                     # 2 tracks: track 0 = melody, track 1 = bass
    midi.addTempo(0, 0, TEMPO)
    midi.addProgramChange(0, 0, 0, 0)      # track 0 plays piano (instrument 0)
    midi.addProgramChange(1, 1, 0, 32)     # track 1 plays acoustic bass (instrument 32)
    for pitch, start, length in melody:
        midi.addNote(0, 0, pitch, start, length, 100)   # 100 = how loud
    for pitch, start, length in bass:
        midi.addNote(1, 1, pitch, start, length, 80)    # bass a bit quieter
    with open(path, "wb") as f:            # "wb" = write in binary, because MIDI is not text
        midi.writeFile(f)


def save_csv(melody, bass, path):
    # writes every note as one row of a spreadsheet, so you can read the piece
    with open(path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["part", "bar", "beat", "note", "midi", "length_beats"])
        for part, notes in [("melody", melody), ("bass", bass)]:
            for pitch, start, length in notes:
                bar = int(start // BEATS_PER_BAR) + 1          # bars counted from 1
                beat = start % BEATS_PER_BAR + 1               # beats counted from 1
                if beat == int(beat):
                    beat = int(beat)                           # write 2 instead of 2.0
                writer.writerow([part, bar, beat, note_name(pitch), pitch, length])


def draw_piano_rolls(samples, path):
    # Draws each piece as a "piano roll": time goes left to right, pitch goes
    # up, and every note is a box. Blue boxes = melody, orange boxes = bass.
    # samples is a dictionary: name -> (melody, bass)
    names = list(samples.keys())
    fig, axes = plt.subplots(len(names), 1, figsize=(11, 2.25 * len(names)), sharex=True)

    for i in range(len(names)):
        ax = axes[i]                       # the picture for this piece
        melody = samples[names[i]][0]
        bass = samples[names[i]][1]

        # one box per note: (x, y) of the corner, then width and height
        for pitch, start, length in melody:
            ax.add_patch(plt.Rectangle((start, pitch - 0.4), length - 0.05, 0.8, color="tab:blue"))
        for pitch, start, length in bass:
            ax.add_patch(plt.Rectangle((start, pitch - 0.4), length - 0.05, 0.8, color="tab:orange"))

        # a grey line at every bar line
        for bar in range(BARS + 1):
            ax.axvline(bar * BEATS_PER_BAR, color="grey", lw=0.5)

        # if there is a bass, the picture has to go lower so the bass fits
        if len(bass) > 0:
            lowest = 38
        else:
            lowest = LOW - 2
        ax.set_ylim(lowest, HIGH + 2)
        ax.set_xlim(0, BARS * BEATS_PER_BAR)
        ax.set_ylabel("MIDI note")
        ax.set_title(names[i].replace("_", " "), fontsize=10, loc="left")

    axes[-1].set_xlabel("Beat")
    fig.tight_layout()
    fig.savefig(path, dpi=200)
    plt.close(fig)


# ---------------------------------------------------------------------------
# THE STEPS OF THE PROGRAM
#
# Each step adds its lines of text to the list "lines". At the end all the
# lines are printed and saved to results/summary.txt.
#
# Formatting used for the tables:
#   "abc".ljust(26)  -> "abc" with spaces after it, so it is 26 characters wide
#   "abc".rjust(12)  -> "abc" with spaces before it, so it is 12 characters wide
#   f"{x:12.1f}"     -> the number x, 12 characters wide, with 1 decimal place
# ---------------------------------------------------------------------------

def table_header():
    # the top row of a table: an empty space, then the 4 version names
    header = " " * 26
    for name in VERSION_NAMES:
        header = header + name.rjust(12)
    return header


def step1_samples():
    # STEP 1: make one sample piece with each version, save it, and draw them all
    samples = {}
    for name in VERSION_NAMES:
        melody, bass = make_piece(name, SAMPLE_SEED)
        samples[name] = (melody, bass)
        save_midi(melody, bass, OUT + "/midi/sample_" + name + ".mid")
        save_csv(melody, bass, OUT + "/notes/sample_" + name + ".csv")
    draw_piano_rolls(samples, OUT + "/piano_rolls.png")


def step2_compare(lines):
    # STEP 2: make 200 pieces with each version and print the average of every measurement.
    # Gives back the pieces and their measurements, because the next steps use them too.
    lines.append(f"Comparison: {NUM_TESTS} pieces per version, {BARS} bars, C major, seeds 0 to {NUM_TESTS - 1}\n")
    lines.append(table_header())

    all_pieces = {}   # version name -> list of 200 melodies
    all_stats = {}    # version name -> list of 200 measurement dictionaries
    for name in VERSION_NAMES:
        all_pieces[name] = []
        all_stats[name] = []
        for seed in range(NUM_TESTS):
            melody, bass = make_piece(name, seed)
            all_pieces[name].append(melody)
            all_stats[name].append(measure(melody))

    # one row per measurement, one column per version
    for metric in METRIC_NAMES:
        row = metric.ljust(26)
        for name in VERSION_NAMES:
            average = np.mean(get_values(all_stats[name], metric))
            row = row + f"{average:12.1f}"
        lines.append(row)

    return all_pieces, all_stats


def step3_pcg_checks(lines, all_pieces, all_stats):
    # STEP 3: the PCG checks: speed, reliability and diversity
    lines.append("\n\nPCG checks\n")
    lines.append(table_header())

    # SPEED: time how long it takes to make 200 pieces, then divide by 200
    row = "Time per piece (ms)".ljust(26)
    for name in VERSION_NAMES:
        start_time = time.perf_counter()
        for seed in range(NUM_TESTS):
            make_piece(name, seed)
        seconds = time.perf_counter() - start_time
        ms_per_piece = seconds * 1000 / NUM_TESTS
        row = row + f"{ms_per_piece:12.2f}"
    lines.append(row)

    # RELIABILITY: a piece follows all the rules if every note is in key,
    # no note crosses a bar line, and it ends on C
    row = "Follows all rules".ljust(26)
    for name in VERSION_NAMES:
        ok = 0
        for s in all_stats[name]:
            if s["In key (%)"] == 100 and s["Cross bar line (%)"] == 0 and s["Ends on C (%)"] == 100:
                ok = ok + 1
        row = row + (str(ok) + "/" + str(NUM_TESTS)).rjust(12)
    lines.append(row)

    # DIVERSITY 1: how many of the 200 pieces are different from all the others
    row = "Unique pieces".ljust(26)
    for name in VERSION_NAMES:
        unique = []
        for melody in all_pieces[name]:
            if melody not in unique:
                unique.append(melody)
        row = row + (str(len(unique)) + "/" + str(NUM_TESTS)).rjust(12)
    lines.append(row)

    # DIVERSITY 2: how different two pieces are, note by note (see difference())
    row = "Different notes (%)".ljust(26)
    for name in VERSION_NAMES:
        row = row + f"{difference(all_pieces[name]):12.1f}"
    lines.append(row)

    piece_seconds = BARS * BEATS_PER_BAR * 60 / TEMPO   # 32 beats at 100 beats per minute
    lines.append(f"one piece lasts {piece_seconds:.1f} s at {TEMPO} bpm")
    lines.append("different notes = share of eighth-note positions where two pieces play different notes (first 100 pieces)")


def step4_sample_size(lines, all_stats):
    # STEP 4: why 200 pieces? We look at the average of "small moves" with
    # 25, 50, 100, 200 and 400 pieces, and how sure we can be about it
    # (the 95% margin of error). If the average stops changing, we have enough pieces.
    lines.append("\n\nSample size check: small moves (%), mean +- 95% margin of error\n")
    header = "pieces".ljust(8)
    for name in VERSION_NAMES:
        header = header + name.rjust(16)
    lines.append(header)

    for n in [25, 50, 100, 200, 400]:
        row = str(n).ljust(8)
        for name in VERSION_NAMES:
            if n <= NUM_TESTS:
                # we already have these pieces from step 2, so use the first n
                values = get_values(all_stats[name][:n], "Small moves (%)")
            else:
                # 400 is more than we made, so make them now
                values = []
                for seed in range(n):
                    melody, bass = make_piece(name, seed)
                    values.append(measure(melody)["Small moves (%)"])
            average = np.mean(values)
            # margin of error = 1.96 x standard deviation / square root of n
            margin = 1.96 * np.std(values, ddof=1) / np.sqrt(n)
            row = row + f"{average:10.1f} +-{margin:4.1f}"
        lines.append(row)


def step5_step_weights(lines):
    # STEP 5: why these step weights? Make 200 pieces with version C for each
    # setting in WEIGHT_SETTINGS and compare them.
    lines.append("\n\nStep weight check (version C, 200 pieces each)\n")
    lines.append(" " * 20 + "avg move".rjust(10) + "small moves %".rjust(15)
                 + "range".rjust(8) + "different %".rjust(13))

    for label, weights in WEIGHT_SETTINGS.items():
        melodies = []
        stats = []
        for seed in range(NUM_TESTS):
            melody, bass = version_c(seed, weights)
            melodies.append(melody)
            stats.append(measure(melody))

        avg_move = np.mean(get_values(stats, "Avg move (semitones)"))
        small = np.mean(get_values(stats, "Small moves (%)"))
        note_range = np.mean(get_values(stats, "Range (semitones)"))
        different = difference(melodies)
        lines.append(label.ljust(20) + f"{avg_move:10.1f}{small:15.1f}{note_range:8.1f}{different:13.1f}")


def step6_game_demo(lines):
    # STEP 6: game demo. Each level uses (1000 + level number) as its seed,
    # so a level always gets the same music, and the game only has to remember
    # the level number instead of storing an audio file.
    levels = {}
    for level in range(1, LEVELS + 1):
        melody, bass = version_d(1000 + level)
        levels["Level_" + str(level)] = (melody, bass)
        save_midi(melody, bass, OUT + "/midi/level_" + str(level) + ".mid")
    draw_piano_rolls(levels, OUT + "/game_levels.png")
    lines.append(f"\n\nGame demo: level_1.mid to level_{LEVELS}.mid, version D with seed 1000 + level number")


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------

def main():
    # make the output folders (exist_ok=True means no error if they are already there)
    os.makedirs(OUT + "/midi", exist_ok=True)
    os.makedirs(OUT + "/notes", exist_ok=True)

    lines = []   # all the text for summary.txt

    step1_samples()
    all_pieces, all_stats = step2_compare(lines)
    step3_pcg_checks(lines, all_pieces, all_stats)
    step4_sample_size(lines, all_stats)
    step5_step_weights(lines)
    step6_game_demo(lines)

    # print everything and save it to summary.txt
    text = "\n".join(lines)
    print(text)
    with open(OUT + "/summary.txt", "w") as f:
        f.write(text)


# only run main() when this file is run directly (not when it is imported)
if __name__ == "__main__":
    main()
