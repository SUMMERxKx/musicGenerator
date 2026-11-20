/*
 * Samar Khajuria   T00714740
 *
 * COMP 4980 - Procedural Content Generation
 * Category 3: computationally generated music
 *
 * Procedural game music generator.
 *
 * Each level gets a short piece in C with three parts: a melody, a bass line
 * and drums. The piece is four phrases long in the shape A A' B A. The first
 * phrase is made up, the second is a slightly changed copy of it, the third
 * is a new phrase that goes somewhere else, and the last brings the first one
 * back and ends on C. Every bar has a chord, and the melody lands on a note of
 * that chord at the start of each bar so the melody and the bass agree.
 *
 * The game gives one thing: the level number. It is the random seed, so a
 * level always gets the same music back. It also sets the difficulty: harder
 * levels play faster, use more short notes, add drums and a busier bass line,
 * play louder, and switch from a major key to a minor one.
 */

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.sound.midi.*;

public class MusicGenerator {

    // Two markers used in place of a note. A rest is silence, a hold means the
    // note before keeps ringing. Every real note is 0 or more, so they never clash.
    static int rest = -1;
    static int hold = -2;

    // The notes of the scale, counted in keys up from C. Major sounds bright,
    // minor sounds darker and is used for the harder levels.
    static int[] majorScale = {0, 2, 4, 5, 7, 9, 11};
    static int[] minorScale = {0, 2, 3, 5, 7, 8, 10};
    static int minorFromLevel = 4;

    // The melody starts at middle C (60) and the bass two octaves lower (36).
    // A melody note is a step of the scale: 0 is C, 7 is the next C up, 11 is
    // the G above that, which is as high as the melody goes.
    static int melodyBase = 60;
    static int bassBase = 36;
    static int highestStep = 11;

    static int levels = 5;

    // The piece is 16 bars of 4 beats, split into 4 phrases of 4 bars. Each beat
    // is cut in half, so the smallest note is half a beat.
    static int beatsPerBar = 4;
    static int barsPerPhrase = 4;
    static int phrases = 4;
    static int bars = barsPerPhrase * phrases;
    static int beats = bars * beatsPerBar;
    static int slotsPerBeat = 2;
    static int slots = beats * slotsPerBeat;

    // Level 1 plays at 80 beats per minute, and every level after is 30 faster.
    static int tempoBase = 80;
    static int tempoIncrement = 30;

    // Chances out of 100. A beat is split into two short notes more often on
    // harder levels: 10 at level 1, then 15 more per level.
    static int splitBase = 10;
    static int splitIncrement = 15;
    static int restChance = 10;
    static int leapChance = 15;
    static int variationChance = 30;

    // Chords, written as the scale step the chord is built on: 0 is C, 3 is F,
    // 4 is G, 5 is A. A phrase has one chord per bar. The A phrase always comes
    // home to C at the end, the B phrase ends on G so it leads back into A.
    static int[][] phraseAChords = {
        {0, 5, 3, 0},
        {0, 3, 4, 0},
        {0, 5, 4, 0},
        {0, 4, 3, 0}
    };
    static int[][] phraseBChords = {
        {5, 3, 0, 4},
        {3, 4, 5, 4},
        {3, 0, 3, 4},
        {5, 4, 3, 4}
    };

    // What the bass plays in one bar on each level, half a beat per number.
    // 0 is the chord's own note, 4 is the fifth above it, 7 is the octave.
    static int[][] bassPatterns = {
        {0, hold, hold, hold, hold, hold, hold, hold},
        {0, hold, hold, hold, 4, hold, hold, hold},
        {0, hold, 4, hold, 0, hold, 4, hold},
        {0, hold, 4, hold, 7, hold, 4, hold},
        {0, 7, 0, 7, 4, 7, 4, 7}
    };

    // MIDI settings. Channel 10 is always the drum kit, and Java counts from 0.
    // The drums are a kick, a snare, a closed hi-hat and a crash cymbal.
    static int melodyChannel = 0;
    static int bassChannel = 1;
    static int bassSound = 33;
    static int drumChannel = 9;
    static int[] drumNotes = {36, 38, 42, 49};
    static int ticksPerBeat = 480;
    static int ticksPerSlot = ticksPerBeat / slotsPerBeat;

    static String outputFolder = "output";


    // Level 1 gets the base tempo, level 2 gets one step more, and so on.
    static int tempoForLevel(int level) {
        return tempoBase + tempoIncrement * (level - 1);
    }


    static int[] scaleForLevel(int level) {
        if (level >= minorFromLevel) {
            return minorScale;
        }
        return majorScale;
    }


    static int splitChanceForLevel(int level) {
        return splitBase + splitIncrement * (level - 1);
    }


    // Harder levels are played harder: 78 at level 1, up to 110 at level 5.
    static int loudnessForLevel(int level) {
        return Math.min(127, 70 + 8 * level);
    }


    // Turns a scale step into a MIDI note. Step 9 in major is 7 steps (one
    // octave, 12 keys) plus step 2 of the scale (E, 4 keys), so base + 16.
    static int noteForStep(int step, int base, int[] scale) {
        return base + 12 * (step / 7) + scale[step % 7];
    }


    /* Picks the chord for every bar of the piece. The A chords are used for the
     * first, second and last phrase, the B chords for the third. */
    static int[] makeChords(Random rng) {
        int[] a = phraseAChords[rng.nextInt(phraseAChords.length)];
        int[] b = phraseBChords[rng.nextInt(phraseBChords.length)];
        int[][] form = {a, a, b, a};

        int[] chords = new int[bars];
        for (int bar = 0; bar < bars; bar++) {
            chords[bar] = form[bar / barsPerPhrase][bar % barsPerPhrase];
        }
        return chords;
    }


    // A chord is three notes: the step it is built on, two steps up and four
    // steps up. This finds the chord note in range that is nearest the given one.
    static int nearestChordStep(int step, int chord) {
        int best = chord;
        for (int candidate = 0; candidate <= highestStep; candidate++) {
            int distance = ((candidate - chord) % 7 + 7) % 7;
            boolean inChord = distance == 0 || distance == 2 || distance == 4;

            if (inChord && Math.abs(candidate - step) < Math.abs(best - step)) {
                best = candidate;
            }
        }
        return best;
    }


    /* Moves the melody on from one note to the next. Usually it moves one or two
     * steps up or down, which sounds like a tune. Now and then it leaps three or
     * four steps. If a move would go out of range it goes the other way. */
    static int nextStep(int step, Random rng) {
        int size = 1 + rng.nextInt(2);
        if (rng.nextInt(100) < leapChance) {
            size = 3 + rng.nextInt(2);
        }
        if (rng.nextBoolean()) {
            size = -size;
        }

        int next = step + size;
        if (next < 0 || next > highestStep) {
            next = step - size;
        }
        return next;
    }


    /* Builds one phrase of melody as scale steps, half a beat per slot.
     * On the first beat of a bar the melody moves to the nearest note of that
     * bar's chord. On the other beats it wanders, and each beat is either one
     * note, two short notes, or a rest. The last two beats hold the end note. */
    static int[] makePhrase(int[] chords, int firstBar, int start, int end,
            int level, Random rng) {
        int beatsInPhrase = barsPerPhrase * beatsPerBar;
        int[] phrase = new int[beatsInPhrase * slotsPerBeat];
        int current = start;

        for (int beat = 0; beat < beatsInPhrase; beat++) {
            int slot = beat * slotsPerBeat;
            int chord = chords[firstBar + beat / beatsPerBar];

            if (beat >= beatsInPhrase - 2) {
                if (beat == beatsInPhrase - 2) {
                    phrase[slot] = end;
                } else {
                    phrase[slot] = hold;
                }
                phrase[slot + 1] = hold;
                continue;
            }

            if (beat % beatsPerBar == 0) {
                current = nearestChordStep(current, chord);
            } else {
                current = nextStep(current, rng);
            }

            boolean downbeat = beat % beatsPerBar == 0;

            if (!downbeat && rng.nextInt(100) < restChance) {
                phrase[slot] = rest;
                phrase[slot + 1] = rest;
            } else if (rng.nextInt(100) < splitChanceForLevel(level)) {
                phrase[slot] = current;
                current = nextStep(current, rng);
                phrase[slot + 1] = current;
            } else {
                phrase[slot] = current;
                phrase[slot + 1] = hold;
            }
        }
        return phrase;
    }


    /* The A' phrase: a copy of A where some notes are nudged one step up or
     * down. Notes at the start of a bar and the end note stay put, so it still
     * fits the chords and still sounds like the same phrase. */
    static int[] varyPhrase(int[] phrase, Random rng) {
        int[] varied = phrase.clone();
        int lastSlots = 2 * slotsPerBeat;

        for (int slot = 0; slot < varied.length - lastSlots; slot++) {
            boolean barStart = slot % (beatsPerBar * slotsPerBeat) == 0;

            if (varied[slot] >= 0 && !barStart && rng.nextInt(100) < variationChance) {
                int moved = varied[slot] + 1;
                if (rng.nextBoolean()) {
                    moved = varied[slot] - 1;
                }
                if (moved >= 0 && moved <= highestStep) {
                    varied[slot] = moved;
                }
            }
        }
        return varied;
    }


    /* Builds the whole melody in the shape A A' B A and turns it into MIDI
     * notes. A starts on G and ends on the high C, B starts higher and ends on
     * G, and the very last note is dropped to the low C so the piece finishes. */
    static int[] makeMelody(int level, int[] chords, Random rng) {
        int[] phraseA = makePhrase(chords, 0, 4, 7, level, rng);
        int[] phraseA2 = varyPhrase(phraseA, rng);
        int[] phraseB = makePhrase(chords, 2 * barsPerPhrase, 9, 4, level, rng);
        int[][] form = {phraseA, phraseA2, phraseB, phraseA};

        int[] steps = new int[slots];
        int phraseSlots = phraseA.length;
        for (int slot = 0; slot < slots; slot++) {
            steps[slot] = form[slot / phraseSlots][slot % phraseSlots];
        }
        steps[slots - 2 * slotsPerBeat] = 0;

        int[] scale = scaleForLevel(level);
        int[] notes = new int[slots];
        for (int slot = 0; slot < slots; slot++) {
            if (steps[slot] >= 0) {
                notes[slot] = noteForStep(steps[slot], melodyBase, scale);
            } else {
                notes[slot] = steps[slot];
            }
        }
        return notes;
    }


    // The bass plays this level's pattern in every bar, built on the bar's chord.
    static int[] makeBass(int level, int[] chords) {
        int[] scale = scaleForLevel(level);
        int[] pattern = bassPatterns[Math.min(level, bassPatterns.length) - 1];
        int[] notes = new int[slots];

        for (int slot = 0; slot < slots; slot++) {
            int chord = chords[slot / pattern.length];
            int part = pattern[slot % pattern.length];

            if (part >= 0) {
                notes[slot] = noteForStep(chord + part, bassBase, scale);
            } else {
                notes[slot] = part;
            }
        }
        return notes;
    }


    /* Builds the drums. One row per drum, one number per half beat, and the
     * number is how hard it is hit (0 means it is not hit). Each level adds a
     * layer on top of the one before:
     *   level 1: kick on beats 1 and 3
     *   level 2: snare on beats 2 and 4
     *   level 3: hi-hat on every beat, a crash at each new phrase, and a snare
     *            fill at the end of a phrase now and then
     *   level 4: hi-hat on every half beat, and an extra kick some bars
     *   level 5: a fill at the end of every phrase */
    static int[][] makeDrums(int level, Random rng) {
        int[][] hits = new int[drumNotes.length][slots];
        int barSlots = beatsPerBar * slotsPerBeat;

        for (int bar = 0; bar < bars; bar++) {
            int first = bar * barSlots;
            boolean phraseEnd = bar % barsPerPhrase == barsPerPhrase - 1;

            hits[0][first] = 100;
            hits[0][first + 4] = 90;

            if (level >= 2) {
                hits[1][first + 2] = 90;
                hits[1][first + 6] = 90;
            }

            if (level >= 3) {
                for (int slot = 0; slot < barSlots; slot += 2) {
                    hits[2][first + slot] = 70;
                }
                if (bar % barsPerPhrase == 0) {
                    hits[3][first] = 100;
                }
            }

            if (level >= 4) {
                for (int slot = 1; slot < barSlots; slot += 2) {
                    hits[2][first + slot] = 50;
                }
                if (rng.nextInt(100) < 50) {
                    hits[0][first + 5] = 80;
                }
            }

            boolean fill = level >= 5 || (level >= 3 && rng.nextInt(100) < 50);
            if (phraseEnd && fill) {
                for (int slot = 6; slot < barSlots; slot++) {
                    hits[1][first + slot] = 80 + 10 * (slot - 6);
                }
            }
        }
        return hits;
    }


    // How many slots a note lasts: its own slot plus every hold after it.
    static int lengthOfNote(int[] notes, int slot) {
        int length = 1;
        while (slot + length < notes.length && notes[slot + length] == hold) {
            length++;
        }
        return length;
    }


    // How many notes a part actually plays, not counting holds and rests.
    static int countNotes(int[] notes) {
        int count = 0;
        for (int i = 0; i < notes.length; i++) {
            if (notes[i] >= 0) {
                count++;
            }
        }
        return count;
    }


    // Adds the notes of one part to a track. Notes at the start of a bar are
    // played a little louder so the beat can be felt.
    static void addPart(Track track, int[] notes, int channel, int loudness)
            throws Exception {
        int barSlots = beatsPerBar * slotsPerBeat;

        for (int slot = 0; slot < notes.length; slot++) {
            if (notes[slot] < 0) {
                continue;
            }
            int start = slot * ticksPerSlot;
            int stop = start + lengthOfNote(notes, slot) * ticksPerSlot;

            int played = loudness;
            if (slot % barSlots == 0) {
                played = Math.min(127, loudness + 10);
            }

            track.add(noteEvent(ShortMessage.NOTE_ON, channel, notes[slot], played, start));
            track.add(noteEvent(ShortMessage.NOTE_OFF, channel, notes[slot], 0, stop));
        }
    }


    // Writes the music as a MIDI file: one track each for melody, bass and drums.
    static void saveMidi(int[] melody, int[] bass, int[][] drums, int tempo,
            int loudness, String path) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, ticksPerBeat);
        Track melodyTrack = sequence.createTrack();
        Track bassTrack = sequence.createTrack();
        Track drumTrack = sequence.createTrack();

        melodyTrack.add(tempoEvent(tempo));

        // The bass channel is switched from the default piano to a bass guitar.
        ShortMessage sound = new ShortMessage(ShortMessage.PROGRAM_CHANGE, bassChannel, bassSound, 0);
        bassTrack.add(new MidiEvent(sound, 0));

        addPart(melodyTrack, melody, melodyChannel, loudness);
        addPart(bassTrack, bass, bassChannel, loudness - 10);

        for (int drum = 0; drum < drumNotes.length; drum++) {
            for (int slot = 0; slot < slots; slot++) {
                if (drums[drum][slot] > 0) {
                    int start = slot * ticksPerSlot;
                    int hit = Math.min(127, drums[drum][slot]);
                    drumTrack.add(noteEvent(ShortMessage.NOTE_ON, drumChannel, drumNotes[drum], hit, start));
                    drumTrack.add(noteEvent(ShortMessage.NOTE_OFF, drumChannel, drumNotes[drum], 0, start + 120));
                }
            }
        }

        MidiSystem.write(sequence, 1, new File(path));
    }


    // One note starting or stopping at a point in time.
    static MidiEvent noteEvent(int type, int channel, int note, int loudness, int tick)
            throws Exception {
        return new MidiEvent(new ShortMessage(type, channel, note, loudness), tick);
    }


    /* MIDI stores the tempo as how many microseconds one beat lasts. That number
     * is too big for one byte, so it is split across three, the same way 365 can
     * be split into 3 hundreds, 6 tens and 5 ones. 0x51 is MIDI's code for tempo. */
    static MidiEvent tempoEvent(int beatsPerMinute) throws Exception {
        int microseconds = 60000000 / beatsPerMinute;
        byte[] data = new byte[3];
        data[0] = (byte) (microseconds / 65536);
        data[1] = (byte) (microseconds / 256 % 256);
        data[2] = (byte) (microseconds % 256);
        return new MidiEvent(new MetaMessage(0x51, data, 3), 0);
    }


    /* Draws every level's music into one picture, like a piano roll. Time runs
     * left to right. Melody notes are blue and bass notes orange, and higher
     * notes sit higher. A wider block is a longer note. The grey dots along the
     * bottom are the drums, one line each for kick, snare, hi-hat and crash.
     * Thin lines mark where each phrase starts. */
    static void savePicture(int[][] melodies, int[][] basses, int[][][] drums, String path)
            throws Exception {
        int left = 190;
        int slotWidth = 8;
        int rowHeight = 200;
        int width = left + slots * slotWidth + 30;
        int height = 20 + levels * rowHeight;

        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = picture.createGraphics();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, width, height);

        Color grey = new Color(60, 60, 60);
        Color light = new Color(210, 210, 210);
        Color blue = new Color(55, 138, 221);
        Color orange = new Color(239, 159, 39);
        String[] phraseNames = {"A", "A'", "B", "A"};
        int phraseSlots = slots / phrases;

        for (int level = 0; level < levels; level++) {
            int top = 10 + level * rowHeight;

            pen.setColor(grey);
            pen.setFont(new Font("SansSerif", Font.PLAIN, 34));
            pen.drawString("Level " + (level + 1), 20, top + 40);
            pen.setFont(new Font("SansSerif", Font.PLAIN, 26));
            pen.drawString(tempoForLevel(level + 1) + " bpm", 20, top + 80);
            if (level + 1 >= minorFromLevel) {
                pen.drawString("C minor", 20, top + 114);
            } else {
                pen.drawString("C major", 20, top + 114);
            }

            pen.setFont(new Font("SansSerif", Font.PLAIN, 16));
            for (int phrase = 0; phrase < phrases; phrase++) {
                int x = left + phrase * phraseSlots * slotWidth;
                pen.setColor(light);
                pen.drawLine(x - 2, top + 4, x - 2, top + rowHeight - 14);
                pen.setColor(grey);
                pen.drawString(phraseNames[phrase], x + 2, top + 16);
            }

            int[] melody = melodies[level];
            int[] bass = basses[level];

            for (int slot = 0; slot < slots; slot++) {
                int x = left + slot * slotWidth;

                if (melody[slot] >= 0) {
                    int y = top + 24 + (79 - melody[slot]) * 4;
                    pen.setColor(blue);
                    pen.fillRect(x, y, lengthOfNote(melody, slot) * slotWidth - 2, 5);
                }

                if (bass[slot] >= 0) {
                    int y = top + 110 + (57 - bass[slot]) * 2;
                    pen.setColor(orange);
                    pen.fillRect(x, y, lengthOfNote(bass, slot) * slotWidth - 2, 4);
                }

                for (int drum = 0; drum < drumNotes.length; drum++) {
                    if (drums[level][drum][slot] > 0) {
                        pen.setColor(grey);
                        pen.fillRect(x, top + 160 + drum * 7, slotWidth - 3, 4);
                    }
                }
            }

            pen.setColor(light);
            pen.drawLine(20, top + rowHeight - 8, width - 20, top + rowHeight - 8);
        }

        pen.dispose();
        ImageIO.write(picture, "png", new File(path));
    }



    public static void main(String[] args) throws Exception {

        System.setProperty("java.awt.headless", "true");

        new File(outputFolder).mkdirs();

        int[][] melodies = new int[levels][];
        int[][] basses = new int[levels][];
        int[][][] drums = new int[levels][][];

        System.out.println("level\ttempo\tkey\tseconds\tnotes\tnotes/sec");

        for (int level = 1; level <= levels; level++) {
            int tempo = tempoForLevel(level);

            // The level is the random seed. Every part takes its numbers from
            // the same generator in the same order, so a level always comes out
            // the same.
            Random rng = new Random(level);
            int[] chords = makeChords(rng);
            int[] melody = makeMelody(level, chords, rng);
            int[] bass = makeBass(level, chords);
            int[][] drumHits = makeDrums(level, rng);

            melodies[level - 1] = melody;
            basses[level - 1] = bass;
            drums[level - 1] = drumHits;

            saveMidi(melody, bass, drumHits, tempo, loudnessForLevel(level),
                    outputFolder + "/level" + level + ".mid");

            // The piece is 64 beats long whatever the level, so its length in
            // seconds only depends on the tempo.
            double seconds = beats * 60.0 / tempo;
            int notes = countNotes(melody);
            double notesPerSecond = notes / seconds;

            // Math.round only gives whole numbers, so multiply first and divide
            // after: 13.714 x 10 is 137.14, rounds to 137, divided back is 13.7.
            double secondsShown = Math.round(seconds * 10) / 10.0;
            double rateShown = Math.round(notesPerSecond * 100) / 100.0;

            String key = "major";
            if (level >= minorFromLevel) {
                key = "minor";
            }

            System.out.println(level + "\t" + tempo + "\t" + key + "\t" + secondsShown
                    + "\t" + notes + "\t" + rateShown);
        }

        savePicture(melodies, basses, drums, outputFolder + "/patterns.png");

        System.out.println();
        System.out.println("wrote " + levels + " MIDI files and patterns.png into " + outputFolder + "/");
    }
}
