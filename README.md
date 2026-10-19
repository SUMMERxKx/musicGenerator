# Two-Note Procedural Music Generator

COMP 4980 - Category 3: computationally generated music

Games use procedural content generation (PCG) so players get new content every time they
play. This project does the same for background music, with the smallest generator that
still works.

The melody uses **only two notes**: a low C and a high C. It mostly alternates between
them, but on every beat there is a 25% chance it repeats the note instead of swapping, so
each level gets a different pattern. A bass drum marks every beat.

Level 1 plays at 80 bpm and every level after is 30 faster, so level 5 runs at 200.

The one thing the game controls is the **difficulty level**, and it sets the **tempo**.
The harder the level, the faster the music.

## How to run

```
java MusicGenerator.java
```

That single command compiles and runs the program, because the whole thing is one file.
Java 11 and later allow this; on an older Java, compile first with `javac
MusicGenerator.java` and then run `java MusicGenerator`.

There is nothing to install. MIDI writing (`javax.sound.midi`) and drawing (`java.awt`)
both come with Java, so the program has no outside dependencies.

It finishes in under a second and writes everything into `output/`.

## Files

| File | What it is |
|---|---|
| `MusicGenerator.java` | the whole program - settings, generator, MIDI output and the picture |
| `output/level1.mid` to `level5.mid` | the music, one file per level, opens in GarageBand or MuseScore |
| `output/patterns.png` | all five patterns drawn as blocks |
| `Music_Report.docx` | the 2-page IEEE report |
| `Code_Documentation.docx` | how to run it, how it works and use cases |

## Results

| Level | Tempo (bpm) | Length (s) | Notes/s | Repeats |
|---|---|---|---|---|
| 1 | 80 | 24.0 | 1.33 | 7 |
| 2 | 110 | 17.5 | 1.83 | 7 |
| 3 | 140 | 13.7 | 2.33 | 6 |
| 4 | 170 | 11.3 | 2.83 | 8 |
| 5 | 200 | 9.6 | 3.33 | 14 |

The tempo rises from 80 to 200 bpm, so the same 32-note pattern goes from 24.0 s down to
9.6 s and the note rate rises by a factor of 2.5. The repeat count ranges from 6 to 14
with a mean of 8.4, against the 7.75 expected from a 25% repeat chance over 31 decisions.
With only 31 decisions per level that spread is ordinary randomness, not an effect of
difficulty: the repeat chance is a constant and is never read from the level number, so
the levels differ in speed alone.

A game only has to store the level number. The music is rebuilt from it every time, so no
audio files need shipping.

## Settings

Everything worth changing is at the top of `MusicGenerator.java`:

```java
static int lowNote = 60;          // the two notes
static int highNote = 72;
static int levels = 5;            // how many levels
static int beats = 32;            // notes per piece
static int tempoBase = 80;        // level 1 speed
static int tempoIncrement = 30;   // how much faster each level gets
static int noteRandomness = 25;   // out of 100, how messy the pattern is
```

Set `noteRandomness` to `0` for a strict low-high-low-high, or raise it to break the
pulse more often. Everything is whole numbers, so there are no type conversions to read
past.
