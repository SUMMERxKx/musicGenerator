# Two-note music generator

I wrote this for COMP 4980, Category 3, computationally generated music. It's a small
Java program that generates the background music for game levels instead of shipping
audio files.

The melody only uses two notes, MIDI 60 and 72, which is a low C and the C an octave
above it. It mostly just alternates between them, but on each beat there's a 25% chance
it repeats the note it played instead of swapping. That's the only thing making the
levels sound different from each other. A bass drum hits every beat underneath.

The level number is the seed for the random generator, so replaying a level gives you
the exact same music back, and a different level gives you something different. The
level also sets the tempo. Level 1 is 80 bpm and every level after that adds 30, so
level 5 runs at 200.

It is deliberately tiny. Two notes and one random decision per beat is about as small as
a generator can get and still produce something that varies, and I wanted the whole
thing to fit in one readable file.

## Running it

```
java MusicGenerator.java
```

That's the whole thing. Java 11 or newer runs a single file directly, so there's nothing
to compile first and no libraries to install. MIDI writing and drawing both come with
Java. On an older version, run `javac MusicGenerator.java` and then `java MusicGenerator`.

It takes under a second and writes five MIDI files and one PNG into `output/`. The .mid
files open in GarageBand or MuseScore, and the PNG draws all five patterns as blocks so
you can see them side by side.

## Results

| Level | Tempo | Length |
|---|---|---|
| 1 | 80 bpm | 24.0 s |
| 2 | 110 bpm | 17.5 s |
| 3 | 140 bpm | 13.7 s |
| 4 | 170 bpm | 11.3 s |
| 5 | 200 bpm | 9.6 s |

Every level is the same 32 notes long, so as the tempo climbs the piece gets shorter.
The repeat chance is a constant and never looks at the level number, so the levels only
really differ in speed and in which pattern the seed happened to produce.

The numbers worth tweaking are all at the top of the file, including `noteRandomness`.
Set it to 0 and you get a strict low-high-low-high with no variation at all.

## Also in here

`Music_Report.docx` is the report and `Code_Documentation.docx` explains how the code
works and what it could be used for.

Samar Khajuria
