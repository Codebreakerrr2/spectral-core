package dev.usman.acoustic;

import dev.usman.acoustic.data_structure.config;
import dev.usman.acoustic.data_structure.spectralFrame;
import dev.usman.acoustic.data_structure.spectralFrame.complex;
import dev.usman.acoustic.data_structure.windowType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpectralAnalyzerTest {
    private static final double EPSILON = 1e-9;

    @Test
    void acceptSamplesReturnsNullUntilBlockIsComplete() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        assertNull(analyzer.acceptSamples(new double[0]));
        assertNull(analyzer.acceptSamples(new double[]{1, 2, 3}));
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void acceptSamplesReturnsFrameForExactlyOneBlock() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        spectralFrame frame = analyzer.acceptSamples(new double[]{1, 2, 3, 4});
        assertNotNull(frame);
        assertEquals(0, frame.startSampleIndex());
        assertEquals(0, frame.TimeStampNano());
        assertSpectrum(frame, new double[]{10, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void acceptSamplesAccumulatesSamplesAcrossCalls() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        assertNull(analyzer.acceptSamples(new double[]{1}));
        assertNull(analyzer.acceptSamples(new double[]{2, 3}));
        assertSpectrum(analyzer.acceptSamples(new double[]{4}),
                new double[]{10, -2, -2, -2}, new double[]{0, 2, 0, -2});
    }

    @Test
    void acceptSamplesKeepsRemainingSamplesForNextCall() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        // More than three blocks: none may be skipped or silently consumed.
        spectralFrame first = analyzer.acceptSamples(new double[]{1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4});
        spectralFrame second = analyzer.acceptSamples(new double[0]);
        spectralFrame third = analyzer.acceptSamples(new double[0]);
        assertDcFrame(first, 0, 4);
        assertDcFrame(second, 4, 8);
        assertDcFrame(third, 8, 12);
        assertNull(analyzer.acceptSamples(new double[0]));
        assertDcFrame(analyzer.acceptSamples(new double[]{4, 4, 4}), 12, 16);
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void newPacketsFollowAlreadyBufferedBlocks() {
        SpectralAnalyzer analyzer = analyzer(2, 2);
        assertDcFrame(analyzer.acceptSamples(new double[]{1, 1, 2, 2}), 0, 2);
        assertDcFrame(analyzer.acceptSamples(new double[]{3, 3}), 2, 4);
        assertDcFrame(analyzer.acceptSamples(new double[0]), 4, 6);
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void acceptSamplesAdvancesByHopSize() {
        SpectralAnalyzer analyzer = analyzer(4, 2);
        spectralFrame first = analyzer.acceptSamples(new double[]{1, 2, 3, 4, 5, 6, 7, 8});
        spectralFrame second = analyzer.acceptSamples(new double[0]);
        spectralFrame third = analyzer.acceptSamples(new double[0]);
        assertEquals(0, first.startSampleIndex());
        assertEquals(2, second.startSampleIndex());
        assertEquals(4, third.startSampleIndex());
        assertSpectrum(first, new double[]{10, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertSpectrum(second, new double[]{18, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertSpectrum(third, new double[]{26, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertNull(analyzer.acceptSamples(new double[0]));
        spectralFrame fourth = analyzer.acceptSamples(new double[]{9, 10});
        assertEquals(6, fourth.startSampleIndex());
        assertSpectrum(fourth, new double[]{34, -2, -2, -2}, new double[]{0, 2, 0, -2});
    }

    @Test
    void overlappingWindowsDoNotModifyBufferedSamples() {
        SpectralAnalyzer analyzer = new SpectralAnalyzer(new config(48000, 3, 1, windowType.HANN));
        spectralFrame first = analyzer.acceptSamples(new double[]{1, 2, 3, 4});
        spectralFrame second = analyzer.acceptSamples(new double[0]);
        assertEquals(2.0, first.bins()[0].real(), EPSILON);
        assertEquals(3.0, second.bins()[0].real(), EPSILON);
        assertEquals(1, second.startSampleIndex());
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void resetDiscardsBufferedSamples() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        assertNull(analyzer.acceptSamples(new double[]{8, 9, 10}));
        analyzer.reset();
        assertNull(analyzer.acceptSamples(new double[]{1, 1}));
        assertDcFrame(analyzer.acceptSamples(new double[]{1, 1}), 0, 4);
    }

    @Test
    void resetDiscardsCompleteBlocksAndOverlapAndRestartsTiming() {
        SpectralAnalyzer analyzer = analyzer(4, 1);
        analyzer.acceptSamples(new double[]{9, 8, 7, 6, 5, 4, 3, 2});
        analyzer.acceptSamples(new double[0]);
        analyzer.reset();
        analyzer.reset();
        assertNull(analyzer.acceptSamples(new double[0]));
        spectralFrame frame = analyzer.acceptSamples(new double[]{1, 1, 1, 1});
        assertDcFrame(frame, 0, 4);
        assertEquals(0, frame.TimeStampNano());
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void frameContainsExpectedBinSpacing() {
        SpectralAnalyzer analyzer = new SpectralAnalyzer(new config(44100, 1024, 512, windowType.HANN));
        spectralFrame frame = analyzer.acceptSamples(new double[1024]);
        assertEquals(44100 / 1024.0, frame.binSpacingHZ(), EPSILON);
    }

    @Test
    void timestampsAreSampleBasedWithoutCumulativeRounding() {
        SpectralAnalyzer analyzer = new SpectralAnalyzer(new config(3, 2, 1, windowType.RECTANGULAR));
        assertEquals(0, analyzer.acceptSamples(new double[5]).TimeStampNano());
        assertEquals(333_333_333L, analyzer.acceptSamples(new double[0]).TimeStampNano());
        assertEquals(666_666_666L, analyzer.acceptSamples(new double[0]).TimeStampNano());
        assertEquals(1_000_000_000L, analyzer.acceptSamples(new double[0]).TimeStampNano());
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void copiesPacketsAndLeavesCallerArraysUntouched() {
        SpectralAnalyzer analyzer = analyzer(4, 4);
        double[] packet = {1, 2, 3};
        assertNull(analyzer.acceptSamples(packet));
        Arrays.fill(packet, 99);
        double[] rest = {4, 5, 6, 7, 8};
        assertSpectrum(analyzer.acceptSamples(rest),
                new double[]{10, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertArrayEquals(new double[]{4, 5, 6, 7, 8}, rest);
        Arrays.fill(rest, 99);
        assertSpectrum(analyzer.acceptSamples(new double[0]),
                new double[]{26, -2, -2, -2}, new double[]{0, 2, 0, -2});
    }

    @Test
    void acceptsSingleSampleBlocksForEveryWindow() {
        for (windowType window : windowType.values()) {
            SpectralAnalyzer analyzer = new SpectralAnalyzer(new config(48000, 1, 1, window));
            assertDcFrame(analyzer.acceptSamples(new double[]{2, 3}), 0, 2);
            assertDcFrame(analyzer.acceptSamples(new double[0]), 1, 3);
            assertNull(analyzer.acceptSamples(new double[0]));
        }
    }

    @Test
    void rejectsInvalidInputWithoutLosingOrAppendingSamples() {
        assertThrows(IllegalArgumentException.class, () -> new SpectralAnalyzer(null));
        SpectralAnalyzer analyzer = analyzer(4, 4);
        assertNull(analyzer.acceptSamples(new double[]{1, 2}));
        assertThrows(IllegalArgumentException.class, () -> analyzer.acceptSamples(null));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> analyzer.acceptSamples(new double[]{99, invalid}));
        }
        assertSpectrum(analyzer.acceptSamples(new double[]{3, 4}),
                new double[]{10, -2, -2, -2}, new double[]{0, 2, 0, -2});
        assertNull(analyzer.acceptSamples(new double[0]));
    }

    @Test
    void packetBoundariesDoNotChangeFramesOrLoseSamples() {
        double[] signal = new Random(17).doubles(150, -1.0, 1.0).toArray();
        for (int size : new int[]{1, 3, 8, 15}) {
            for (int hop = 1; hop <= size; hop++) {
                SpectralAnalyzer analyzer = analyzer(size, hop);
                Random random = new Random(size * 100L + hop);
                int nextStart = 0;
                for (int offset = 0; offset < signal.length;) {
                    int end = Math.min(signal.length, offset + 1 + random.nextInt(20));
                    spectralFrame frame = analyzer.acceptSamples(Arrays.copyOfRange(signal, offset, end));
                    nextStart = checkAvailableFrame(frame, signal, nextStart, end, size, hop);
                    offset = end;
                    // Vary whether complete blocks stay buffered between packets.
                    if (random.nextBoolean()) {
                        while ((frame = analyzer.acceptSamples(new double[0])) != null) {
                            nextStart = checkAvailableFrame(frame, signal, nextStart, end, size, hop);
                        }
                        assertTrue(nextStart + size > end);
                    }
                }
                spectralFrame frame;
                while ((frame = analyzer.acceptSamples(new double[0])) != null) {
                    nextStart = checkAvailableFrame(frame, signal, nextStart, signal.length, size, hop);
                }
                assertEquals(((signal.length - size) / hop + 1) * hop, nextStart);
            }
        }
    }

    private static int checkAvailableFrame(spectralFrame frame, double[] signal, int start,
                                          int available, int size, int hop) {
        if (start + size > available) {
            assertNull(frame);
            return start;
        }
        assertNotNull(frame);
        assertEquals(start, frame.startSampleIndex());
        complex[] bins = frame.bins();
        assertEquals(size, bins.length);
        // Inverse reconstruction checks every sample, including phase and non-DC bins.
        for (int n = 0; n < size; n++) {
            double reconstructed = 0.0;
            for (int k = 0; k < size; k++) {
                double angle = 2 * Math.PI * k * n / size;
                reconstructed += bins[k].real() * Math.cos(angle) - bins[k].imaginary() * Math.sin(angle);
            }
            assertEquals(signal[start + n], reconstructed / size, EPSILON);
        }
        return start + hop;
    }

    private static SpectralAnalyzer analyzer(int size, int hop) {
        return new SpectralAnalyzer(new config(48000, size, hop, windowType.RECTANGULAR));
    }

    private static void assertDcFrame(spectralFrame frame, long start, double dc) {
        assertNotNull(frame);
        assertEquals(start, frame.startSampleIndex());
        complex[] bins = frame.bins();
        for (int k = 0; k < bins.length; k++) {
            assertEquals(k == 0 ? dc : 0, bins[k].real(), EPSILON);
            assertEquals(0, bins[k].imaginary(), EPSILON);
        }
    }

    private static void assertSpectrum(spectralFrame frame, double[] real, double[] imaginary) {
        assertNotNull(frame);
        complex[] bins = frame.bins();
        assertEquals(real.length, bins.length);
        for (int k = 0; k < bins.length; k++) {
            assertEquals(real[k], bins[k].real(), EPSILON, "real bin " + k);
            assertEquals(imaginary[k], bins[k].imaginary(), EPSILON, "imaginary bin " + k);
        }
    }
}
