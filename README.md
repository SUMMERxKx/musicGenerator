# Procedural game music generator

A small Java program that writes the background music for game levels instead of the
game shipping audio files. Written for COMP 4980, Category 3, computationally generated
music.

The first version of this program used only two notes and one random decision per beat.
It worked, but it was a very simple generator. This version keeps the same idea, one
level number in and one piece of music out, and makes far more of the musical decisions
itself: the key, the chords, the tune, the rhythm, the bass line and the drums.

## How it works

Every piece is in C. The melody picks its notes from a seven note scale rather than from
a fixed list, so it can move up and down like a real tune. It stays between middle C
(MIDI 60) and the G about an octave and a half above it.

### Shape

A piece is sixteen bars of four beats, which is sixty four beats. The bars are grouped
into four phrases of four bars each, in the shape A A' B A.

The first phrase, A, is made up from scratch. The second, A', is a copy of A with some
notes nudged one step up or down, so it sounds like the same idea played a little
differently. The third, B, is a new phrase that starts higher and wanders somewhere else.
The last phrase brings A back, and its final note drops to the low C so the piece sounds
finished rather than just stopping. Repeating and varying a phrase like this is what
makes it sound like a tune rather than a string of random notes.

### Chords

Every bar has a chord. The program has four chord patterns for the A phrases and four
for the B phrase, and picks one of each. The A patterns all start and end on C, so A
always feels settled. The B patterns end on G, which leaves the music hanging and pulls
it back into the last A.

At the start of every bar the melody moves to whichever note of that bar's chord is
nearest to where it already is. The bass line plays the same chords, so the two parts
always agree on the strong beats.

### Melody

Between those chord notes the melody wanders. Usually it moves one or two steps up or
down. Fifteen times in a hundred it leaps three or four steps instead, which gives the
tune some shape. If a move would take it out of range it goes the other way.

Each beat is cut in half, so a beat can be one long note, two short notes, or a rest.
Rests never land on the first beat of a bar, and the last two beats of each phrase hold
the phrase's end note.

### Bass and drums

The bass plays the chord's own note an octave or two below the melody, sometimes with the
note five steps above it or the octave above it, depending on the level. It uses a bass
guitar sound so it stands apart from the piano melody.

The drums are a kick, a snare, a closed hi-hat and a crash cymbal, and each level adds a
layer on top of the one before.

### Seed

The level number is the seed for the random numbers. Replaying a level gives back the
exact same music, while a different level gives something different. That means a game
only has to remember which level the player is on, not store an audio file for it.

## Difficulty

The one thing the game controls is the level, and it now changes much more than the
speed.

| Level | Tempo | Key | Length | Melody notes | Bass | Drums |
|---|---|---|---|---|---|---|
| 1 | 80 bpm | C major | 48.0 s | 63 | one note per bar | kick |
| 2 | 110 bpm | C major | 34.9 s | 67 | two notes per bar | kick, snare |
| 3 | 140 bpm | C major | 27.4 s | 75 | four notes per bar | adds hi-hat, crash and some fills |
| 4 | 170 bpm | C minor | 22.6 s | 91 | four notes per bar, up to the octave | hi-hat on every half beat, extra kicks |
| 5 | 200 bpm | C minor | 19.2 s | 88 | eight notes per bar | a fill at the end of every phrase |

Tempo starts at 80 beats per minute and every level adds 30. The chance of a beat being
split into two short notes starts at 10 in 100 and goes up by 15 each level, so the
melody gets busier as well as faster. Levels 4 and 5 switch to a minor scale, which
sounds darker and more tense. Each level also plays a little louder than the one before.

Note counts do not have to rise every single level, because each level is its own
random draw, and level 5 happens to come out with a few fewer than level 4. The trend is still
clearly upward, and level 5 still plays the most notes per second.

## What it produces

Five MIDI files, one per level, plus a picture of all five pieces drawn like a piano roll.
Melody notes are blue and bass notes orange, higher notes sit higher, and longer notes
are wider. The grey dots underneath are the drums, and thin lines mark where each phrase
starts. The whole run takes about a second.

MIDI rather than recorded audio because the program is deciding notes, not sounds. One
level comes out at between 1 and 4 KB.

## What it does not do

It is always in C, and the chords come from a short fixed list rather than being made up.
The form is always A A' B A. Nobody has sat down and listened to it properly either, so
the idea that faster, busier and darker music feels harder is borrowed from convention
rather than something I tested.

Samar Khajuria
