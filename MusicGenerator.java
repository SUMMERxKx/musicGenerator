/*
 * COMP 4980 - Procedural game music generator
 *
 * The melody uses two notes: a low C and a high C. It mostly alternates
 * between them, but every so often it repeats a note instead of swapping,
 * which is what makes each level sound different. A drum hits every beat.
 *
 * The game gives one thing: the level number. That sets the tempo, so the
 * harder the level, the faster the music.
 */

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.sound.midi.*;

public class MusicGenerator {

    /* The only two notes in the whole piece. 60 is middle C, 72 is the C above it. */
    static final int LOW_NOTE = 60;
    static final int HIGH_NOTE = 72;

    /* How much music to make. */
    static final int LEVELS = 5;
    static final int BEATS = 32;

    /* Tempo on the first and last level, in beats per minute. */
    static final int TEMPO_EASY = 80;
    static final int TEMPO_HARD = 200;

    /* Out of 100, how often a note repeats instead of swapping to the other one.
     * 0 gives a perfect low-high-low-high. Bigger numbers break it up more. */
    static final int REPEAT_PERCENT = 25;

    /* MIDI settings. Channel 10 is always the drum kit, and Java counts from 0. */
    static final int DRUM_NOTE = 36;
    static final int DRUM_CHANNEL = 9;
    static final int TICKS_PER_BEAT = 480;

    static final String OUT = "output";


    /* Level 1 is the slowest, the last level is the fastest, evenly spaced between. */
    static int tempoForLevel(int level) {
        if (LEVELS == 1) {
            return TEMPO_EASY;
        }
        return TEMPO_EASY + (TEMPO_HARD - TEMPO_EASY) * (level - 1) / (LEVELS - 1);
    }


    /* Builds the notes for one level. The level is the random seed, so a level
     * always gets the same music back. */
    static int[] makePattern(int level) {
        Random rng = new Random(level);
        int[] notes = new int[BEATS];
        int current = LOW_NOTE;

        for (int i = 0; i < BEATS; i++) {
            notes[i] = current;

            /* Usually swap to the other note, sometimes stay on this one. */
            if (rng.nextInt(100) >= REPEAT_PERCENT) {
                if (current == LOW_NOTE) {
                    current = HIGH_NOTE;
                } else {
                    current = LOW_NOTE;
                }
            }
        }
        return notes;
    }


    /* How many beats played the same note as the beat before. */
    static int countRepeats(int[] notes) {
        int repeats = 0;
        for (int i = 1; i < notes.length; i++) {
            if (notes[i] == notes[i - 1]) {
                repeats++;
            }
        }
        return repeats;
    }


    /* Writes the music as a MIDI file: one track for the notes, one for the drum. */
    static void saveMidi(int[] notes, int tempo, String path) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
        Track melody = sequence.createTrack();
        Track drums = sequence.createTrack();

        melody.add(tempoEvent(tempo));

        for (int i = 0; i < notes.length; i++) {
            int start = i * TICKS_PER_BEAT;

            melody.add(noteEvent(ShortMessage.NOTE_ON, 0, notes[i], 100, start));
            melody.add(noteEvent(ShortMessage.NOTE_OFF, 0, notes[i], 0, start + TICKS_PER_BEAT));

            drums.add(noteEvent(ShortMessage.NOTE_ON, DRUM_CHANNEL, DRUM_NOTE, 90, start));
            drums.add(noteEvent(ShortMessage.NOTE_OFF, DRUM_CHANNEL, DRUM_NOTE, 0, start + 120));
        }

        MidiSystem.write(sequence, 1, new File(path));
    }


    /* One note starting or stopping at a point in time. */
    static MidiEvent noteEvent(int type, int channel, int note, int loudness, int tick)
            throws Exception {
        return new MidiEvent(new ShortMessage(type, channel, note, loudness), tick);
    }


    /* MIDI stores tempo as how many microseconds one beat lasts, split into
     * three bytes. This is the only place that has to fiddle with bytes. */
    static MidiEvent tempoEvent(int beatsPerMinute) throws Exception {
        int microseconds = 60000000 / beatsPerMinute;
        byte[] data = new byte[3];
        data[0] = (byte) (microseconds >> 16);
        data[1] = (byte) (microseconds >> 8);
        data[2] = (byte) microseconds;
        return new MidiEvent(new MetaMessage(0x51, data, 3), 0);
    }


    /* Draws every level's pattern into one picture. Each beat is a block:
     * high notes sit on the top row, low notes on the bottom row. */
    static void savePicture(int[][] patterns, String path) throws Exception {
        int left = 90;
        int blockSize = 26;
        int rowHeight = 80;
        int width = left + BEATS * blockSize + 30;
        int height = 60 + LEVELS * rowHeight;

        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = picture.createGraphics();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, width, height);

        Color grey = new Color(60, 60, 60);
        Color blue = new Color(55, 138, 221);
        Color orange = new Color(239, 159, 39);

        pen.setColor(grey);
        pen.setFont(new Font("SansSerif", Font.PLAIN, 14));
        pen.drawString("Each block is one beat.  Top row = high C,  bottom row = low C.", 20, 28);

        for (int level = 0; level < LEVELS; level++) {
            int top = 50 + level * rowHeight;
            int[] notes = patterns[level];

            pen.setColor(grey);
            pen.setFont(new Font("SansSerif", Font.PLAIN, 13));
            pen.drawString("Level " + (level + 1), 20, top + 30);
            pen.drawString(tempoForLevel(level + 1) + " bpm", 20, top + 48);

            for (int i = 0; i < notes.length; i++) {
                int x = left + i * blockSize;

                if (notes[i] == HIGH_NOTE) {
                    pen.setColor(blue);
                    pen.fillRect(x, top + 6, blockSize - 4, 20);
                } else {
                    pen.setColor(orange);
                    pen.fillRect(x, top + 32, blockSize - 4, 20);
                }
            }

            pen.setColor(new Color(210, 210, 210));
            pen.drawLine(20, top + rowHeight - 8, width - 20, top + rowHeight - 8);
        }

        pen.dispose();
        ImageIO.write(picture, "png", new File(path));
    }



    public static void main(String[] args) throws Exception {
        /* Drawing starts up Java's window system, which leaves a thread running
         * and stops the program from exiting on its own. We only write a file,
         * so tell it there is no screen. This has to come before any drawing. */
        System.setProperty("java.awt.headless", "true");

        new File(OUT).mkdirs();

        int[][] patterns = new int[LEVELS][];

        System.out.println("level   tempo   seconds   notes/sec   repeats");

        for (int level = 1; level <= LEVELS; level++) {
            int tempo = tempoForLevel(level);
            int[] notes = makePattern(level);
            patterns[level - 1] = notes;

            saveMidi(notes, tempo, OUT + "/level" + level + ".mid");

            /* One note per beat, so the piece lasts BEATS beats at this tempo. */
            double seconds = BEATS * 60.0 / tempo;

            System.out.printf("%-8d%-8d%-10.1f%-12.2f%d%n",
                    level, tempo, seconds, BEATS / seconds, countRepeats(notes));
        }

        savePicture(patterns, OUT + "/patterns.png");

        System.out.println();
        System.out.println("wrote " + LEVELS + " MIDI files and patterns.png into " + OUT + "/");
    }
}
