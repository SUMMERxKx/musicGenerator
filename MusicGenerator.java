// COMP 4980 - Procedural game music generator
//
// The idea is deliberately small:
//   - the melody only ever uses TWO notes, a low C and a high C
//   - it mostly alternates between them: low, high, low, high, ...
//   - sometimes it randomly repeats a note instead, so no two runs are the same
//     (that gives patterns like low, low, high, high)
//   - a kick drum plays on every beat so it sounds like game music
//   - the TEMPO comes from the difficulty level: harder level = faster music
//
// It writes one MIDI file per level, plus one picture showing the patterns.
//
// Java can write MIDI on its own (javax.sound.midi is part of the standard
// library), so this program needs no outside libraries at all.
//
// How to run it:
//   javac MusicGenerator.java
//   java MusicGenerator

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

public class MusicGenerator {

    // ---------------------------------------------------------------------
    // SETTINGS - change these to change the music
    // ---------------------------------------------------------------------

    // The only two notes in the whole piece.
    // In MIDI every note is a number: 60 is middle C, and +12 is the same
    // note one octave higher. So these are a low C and a high C.
    static final int LOW_NOTE = 60;    // C4
    static final int HIGH_NOTE = 72;   // C5

    static final int LEVELS = 5;       // how many levels to write music for
    static final int BEATS = 32;       // how many notes each piece has

    static final int TEMPO_EASY = 80;  // beats per minute on level 1
    static final int TEMPO_HARD = 200; // beats per minute on the last level

    // Chance of repeating the same note instead of swapping to the other one.
    // 0.0 would be a perfect low-high-low-high. 0.25 keeps it mostly regular
    // but lets a repeat sneak in every few notes, which is the random part.
    static final double REPEAT_CHANCE = 0.25;

    static final int DRUM_NOTE = 36;   // bass drum in General MIDI
    static final int DRUM_CHANNEL = 9; // channel 9 is always percussion

    static final int TICKS_PER_BEAT = 480;
    static final String OUT = "output";

    // ---------------------------------------------------------------------
    // THE GENERATOR
    // ---------------------------------------------------------------------

    // Works out the tempo for a level: level 1 is slow, the last level is fast.
    static int tempoForLevel(int level) {
        if (LEVELS <= 1) {
            return TEMPO_EASY;
        }
        double hardness = (double) (level - 1) / (LEVELS - 1);   // 0.0 to 1.0
        return (int) Math.round(TEMPO_EASY + (TEMPO_HARD - TEMPO_EASY) * hardness);
    }

    // Makes the note pattern for one piece.
    // Gives back an array of MIDI note numbers, one per beat.
    static int[] makePattern(int level) {
        Random rng = new Random(level);   // same level always gives the same music

        int[] notes = new int[BEATS];
        int current = LOW_NOTE;           // always start on the low note

        for (int i = 0; i < BEATS; i++) {
            notes[i] = current;

            // Decide the next note. Normally swap to the other one,
            // but sometimes repeat this one instead.
            if (rng.nextDouble() >= REPEAT_CHANCE) {
                if (current == LOW_NOTE) {
                    current = HIGH_NOTE;
                } else {
                    current = LOW_NOTE;
                }
            }
        }
        return notes;
    }

    // ---------------------------------------------------------------------
    // WRITING THE MIDI FILE
    // ---------------------------------------------------------------------

    static void saveMidi(int[] notes, int tempo, String path) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
        Track melody = sequence.createTrack();
        Track drums = sequence.createTrack();

        addTempo(melody, tempo);

        // the melody: one note per beat
        for (int i = 0; i < notes.length; i++) {
            long start = (long) i * TICKS_PER_BEAT;
            long end = start + TICKS_PER_BEAT;
            melody.add(noteEvent(ShortMessage.NOTE_ON, 0, notes[i], 100, start));
            melody.add(noteEvent(ShortMessage.NOTE_OFF, 0, notes[i], 0, end));
        }

        // the drum: a kick on every beat
        for (int i = 0; i < notes.length; i++) {
            long start = (long) i * TICKS_PER_BEAT;
            drums.add(noteEvent(ShortMessage.NOTE_ON, DRUM_CHANNEL, DRUM_NOTE, 90, start));
            drums.add(noteEvent(ShortMessage.NOTE_OFF, DRUM_CHANNEL, DRUM_NOTE, 0, start + 120));
        }

        MidiSystem.write(sequence, 1, new File(path));
    }

    static MidiEvent noteEvent(int type, int channel, int note, int velocity, long tick)
            throws Exception {
        return new MidiEvent(new ShortMessage(type, channel, note, velocity), tick);
    }

    // the tempo is stored as how many microseconds one beat lasts
    static void addTempo(Track track, int beatsPerMinute) throws Exception {
        int microsecondsPerBeat = 60000000 / beatsPerMinute;
        byte[] data = {
            (byte) ((microsecondsPerBeat >> 16) & 0xFF),
            (byte) ((microsecondsPerBeat >> 8) & 0xFF),
            (byte) (microsecondsPerBeat & 0xFF)
        };
        track.add(new MidiEvent(new MetaMessage(0x51, data, data.length), 0));
    }

    // ---------------------------------------------------------------------
    // THE PICTURE
    //
    // One row per level. Each beat is a block: a low block for the low note
    // and a high block for the high note. You can see the pattern alternating,
    // and you can see where the random repeats happen.
    // ---------------------------------------------------------------------

    static void savePicture(int[][] allPatterns, String path) throws Exception {
        int left = 90;
        int cell = 26;
        int rowHeight = 80;
        int width = left + BEATS * cell + 30;
        int height = 60 + LEVELS * rowHeight;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(60, 60, 60));
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Each block is one beat.  Top row = high C,  bottom row = low C.", 20, 28);

        for (int level = 0; level < LEVELS; level++) {
            int top = 50 + level * rowHeight;
            int[] pattern = allPatterns[level];

            g.setColor(new Color(60, 60, 60));
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString("Level " + (level + 1), 20, top + 30);
            g.drawString(tempoForLevel(level + 1) + " bpm", 20, top + 48);

            for (int i = 0; i < pattern.length; i++) {
                int x = left + i * cell;
                int y;
                if (pattern[i] == HIGH_NOTE) {
                    y = top + 6;                 // high note sits on the top row
                    g.setColor(new Color(55, 138, 221));
                } else {
                    y = top + 32;                // low note sits on the bottom row
                    g.setColor(new Color(239, 159, 39));
                }
                g.fillRect(x, y, cell - 4, 20);
            }

            g.setColor(new Color(210, 210, 210));
            g.setStroke(new BasicStroke(1));
            g.drawLine(20, top + rowHeight - 8, width - 20, top + rowHeight - 8);
        }

        g.dispose();
        ImageIO.write(image, "png", new File(path));
    }

    // ---------------------------------------------------------------------
    // MAIN
    // ---------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();

        int[][] allPatterns = new int[LEVELS][];

        System.out.println("level   tempo   seconds   notes/sec   repeats   pattern starts");
        for (int level = 1; level <= LEVELS; level++) {
            int tempo = tempoForLevel(level);
            int[] notes = makePattern(level);
            allPatterns[level - 1] = notes;

            String path = OUT + "/level" + level + ".mid";
            saveMidi(notes, tempo, path);

            // how long the piece lasts: one note per beat, at this many beats per minute
            double seconds = BEATS * 60.0 / tempo;

            System.out.printf("%-8d%-8d%-10.1f%-12.2f%-10d%s%n",
                    level, tempo, seconds, BEATS / seconds, countRepeats(notes), firstFew(notes));
        }

        savePicture(allPatterns, OUT + "/patterns.png");

        System.out.println();
        System.out.println("wrote " + LEVELS + " MIDI files and patterns.png into " + OUT + "/");
    }

    // Counts how many times the pattern repeated a note instead of swapping.
    // This is the randomness showing up: with REPEAT_CHANCE of 0.25 we expect
    // about a quarter of the beats to be repeats.
    static int countRepeats(int[] notes) {
        int repeats = 0;
        for (int i = 1; i < notes.length; i++) {
            if (notes[i] == notes[i - 1]) {
                repeats = repeats + 1;
            }
        }
        return repeats;
    }

    // shows the first few notes as "low high low high ..." so you can see the pattern
    static String firstFew(int[] notes) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (notes[i] == HIGH_NOTE) {
                text.append("high ");
            } else {
                text.append("low ");
            }
        }
        return text.toString().trim() + " ...";
    }
}
