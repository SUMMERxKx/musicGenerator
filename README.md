# Two-note music generator

A small Java program that writes the background music for game levels instead of the
game shipping audio files. Written for COMP 4980, Category 3, computationally generated
music.

Most generated music tries to sound rich. I went the other way and asked how small a
generator can get while still producing something that varies and still reacting to the
game. The answer turned out to be two notes and one random decision per beat.

## How it works

The melody only ever uses two notes, MIDI 60 and 72. That is a low C and the C an octave
above it. Because they are an octave apart they are the same note name, so they always
sound right together and there is no key or scale to worry about.

Each piece is thirty two notes long and plays one note per beat. The generator normally
swaps between the two notes, which on its own would give low, high, low, high forever and
the same piece every time. So on each beat it rolls a number from 0 to 99, and if that
number lands under 25 it repeats the current note instead of swapping. A repeat knocks
the rest of the pattern onto the opposite foot, and that is the only thing making one
level sound different from another.

Underneath all of it a bass drum hits every beat, which gives the repeats something
steady to push against so they read as variation rather than mistakes.

The level number is the seed for the random numbers. Replaying a level gives back the
exact same music, while a different level gives something different. That means a game
only has to remember which level the player is on, not store an audio file for it.

## Difficulty

The one thing the game controls is the level, and it sets the tempo. Level 1 plays at 80
beats per minute and every level after that adds 30.

| Level | Tempo | Length |
|---|---|---|
| 1 | 80 bpm | 24.0 s |
| 2 | 110 bpm | 17.5 s |
| 3 | 140 bpm | 13.7 s |
| 4 | 170 bpm | 11.3 s |
| 5 | 200 bpm | 9.6 s |

Every level is the same thirty two notes, so as the tempo climbs the piece gets shorter
and busier. Nothing else changes, which is deliberate. The levels still sound like the
same piece of music being played with more urgency rather than five unrelated tracks.

## What it produces

Five MIDI files, one per level, plus a picture showing all five patterns drawn as blocks
so you can see where the repeats landed. The whole run takes under a second.

MIDI rather than recorded audio because the program is deciding notes, not sounds. One
level comes out at around 600 bytes.

## What it does not do

Two notes is not a melody in any honest sense. The result is closer to an adaptive pulse
than a tune, the rhythm never varies, and difficulty only ever changes the speed. Nobody
has sat down and listened to it properly either, so the idea that faster music feels
harder is borrowed from convention rather than something I tested.

Samar Khajuria
