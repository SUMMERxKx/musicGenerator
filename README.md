# Procedural Music Generator for Games

COMP 4980, portfolio element 2 (Category 3: computationally generated music)

Games use procedural content generation (PCG) so players get new content every time they play. This project tries the same idea for background music. It makes short pieces using simple rules (no AI), saves them as MIDI files, and checks if the generator does what a PCG system should: run fast, follow its rules every time, still give variety, and let the designer control it.

The music is built in four versions, each adding one more rule, so the effect of every rule can be measured:

- A: pure random notes and lengths
- B: notes picked only from the C major scale
- C: a weighted random walk along the scale, weighted note lengths that never cross a bar line, and an ending on C
- D: version C plus a chord progression (C, Am, F, G) with a bass line, where the first note of every bar fits the chord

## How to run

Install the libraries once:

```
pip3 install numpy matplotlib midiutil
```

Then, from this folder:

```
python3 music_project.py
```

It takes a few seconds. The seeds are fixed, so the output is the same every time.

## Files

| File | What it is |
|---|---|
| `music_project.py` | All of the code (scale, melody, rhythm, harmony, MIDI output, comparison, PCG checks) |
| `results/midi/sample_*.mid` | One sample piece per version, opens in GarageBand, MuseScore, etc. |
| `results/midi/level_*.mid` | Game demo: one piece per level, the level number is the seed |
| `results/notes/` | The four samples as CSV note lists |
| `results/piano_rolls.png` | The four samples drawn as piano rolls |
| `results/game_levels.png` | The three level pieces drawn as piano rolls |
| `results/summary.txt` | All numbers: comparison, PCG checks, sample size check, step weight check |
| `Music Report.docx` | The 2-page IEEE report |
| `Code Documentation.docx` | How to run the code, how it works and use cases |

## Main results

- The scale alone makes every note fit the key, but the melody still jumps about 8.7 semitones per note. The weighted walk brings that down to 2.6.
- Versions C and D follow all their rules in 200 out of 200 pieces, and every piece is different from the others.
- A piece takes about 0.1 ms to generate and lasts 19.2 s, so a game can make new music for every level instantly. It only needs to store the level number, not an audio file.
- 200 pieces per version is enough: the margin of error is about 1%, and the order of the versions is already the same at 25 pieces.
- The chosen step weights are in between "jumps around" (all weights equal) and "barely moves" (steps only, where the range shrinks to about an octave).
