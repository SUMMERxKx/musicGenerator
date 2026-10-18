# Two-Note Procedural Music Generator

COMP 4980, portfolio element 2 (Category 3: computationally generated music)

Games use procedural content generation (PCG) so players get new content every time they
play. This project does the same for background music, with the smallest generator that
still works.

The melody uses **only two notes**: a low C and a high C. It mostly alternates between
them, but on every beat there is a 25% chance it repeats the note instead of swapping, so
each level gets a different pattern. A bass drum marks every beat.

The one thing the game controls is the **difficulty level**, and it sets the **tempo**.
The harder the level, the faster the music.

## How to run

```
javac MusicGenerator.java
java MusicGenerator
```

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
| 1 | 80 | 24.0 | 1.33 | 8 |
| 2 | 110 | 17.5 | 1.83 | 9 |
| 3 | 140 | 13.7 | 2.33 | 8 |
| 4 | 170 | 11.3 | 2.83 | 7 |
| 5 | 200 | 9.6 | 3.33 | 8 |

The tempo rises from 80 to 200 bpm, so the same 32-note pattern goes from 24.0 s down to
9.6 s and the note rate rises by a factor of 2.5. The repeat count stays between 7 and 9,
close to the 7.75 expected from a 0.25 repeat chance over 31 transitions, which shows the
randomness is independent of the difficulty: the levels differ in speed, not in how
irregular they sound.

A game only has to store the level number. The music is rebuilt from it every time, so no
audio files need shipping.

## Settings

Everything worth changing is at the top of `MusicGenerator.java`:

```java
static final int LOW_NOTE = 60;             // the two notes
static final int HIGH_NOTE = 72;
static final int LEVELS = 5;                // how many levels
static final int BEATS = 32;                // notes per piece
static final int TEMPO_EASY = 80;           // level 1 speed
static final int TEMPO_HARD = 200;          // last level speed
static final double REPEAT_CHANCE = 0.25;   // how random it gets
```

Set `REPEAT_CHANCE` to `0.0` for a strict low-high-low-high, or raise it to break the
pulse more often.
