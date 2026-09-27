package dev.usman.acoustic.algos;

import dev.usman.acoustic.data_structure.spectralFrame.complex;
import dev.usman.acoustic.data_structure.windowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AlgosTest {

    private static final double EPSILON = 1e-10;

    @ParameterizedTest
    @EnumSource(windowType.class)
    void createWindowReturnsFiniteSymmetricWeights(windowType type) {
        for (int size : new int[]{2, 3, 16, 17, 1024}) {
            double[] weights = Algos.createWindow(type, size);
            assertEquals(size, weights.length);
            for (int i = 0; i < size; i++) {
                assertTrue(Double.isFinite(weights[i]));
                assertEquals(weights[i], weights[size - 1 - i], EPSILON);
            }
        }
    }

    @Test
    void rectangularWindowContainsOnlyOnes() {
        assertArrayEquals(new double[]{1, 1, 1, 1}, Algos.createWindow(windowType.RECTANGULAR, 4), EPSILON);
    }

    @Test
    void standardWindowsHaveExpectedWeights() {
        assertWindow(windowType.HANN, new double[]{0, 0.5, 1, 0.5, 0});
        assertWindow(windowType.HAMMING, new double[]{0.08, 0.54, 1, 0.54, 0.08});
        assertWindow(windowType.BLACKMAN, new double[]{0, 0.34, 1, 0.34, 0});
        assertWindow(windowType.BLACKMAN_HARRIS, new double[]{0.00006, 0.21747, 1, 0.21747, 0.00006});
        assertWindow(windowType.BARTLETT, new double[]{0, 0.5, 1, 0.5, 0});
        assertWindow(windowType.FLAT_TOP, new double[]{-0.000421051, -0.05473684, 1.000000003,
                -0.05473684, -0.000421051});
    }

    @Test
    void kaiserUsesBetaEightPointSix() {
        // Reference values for I0(8.6 * sqrt(1 - x*x)) / I0(8.6).
        assertWindow(windowType.KAISER, new double[]{0.00133251399790242, 0.340393622440188,
                1.0, 0.340393622440188, 0.00133251399790242});
    }

    @ParameterizedTest
    @EnumSource(windowType.class)
    void singleSampleWindowHasUnitWeight(windowType type) {
        assertArrayEquals(new double[]{1}, Algos.createWindow(type, 1));
    }

    @Test
    void createWindowRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> Algos.createWindow(null, 4));
        assertThrows(IllegalArgumentException.class, () -> Algos.createWindow(windowType.HANN, 0));
        assertThrows(IllegalArgumentException.class, () -> Algos.createWindow(windowType.HANN, -1));
    }

    @Test
    void applyWindowMultipliesSamplesByCorrespondingWeights() {
        double[] samples = {2, -4, 8};
        double[] weights = {0.5, 0.25, 0};
        Algos.applyWindow(samples, weights);
        assertArrayEquals(new double[]{1, -1, 0}, samples, EPSILON);
        assertArrayEquals(new double[]{0.5, 0.25, 0}, weights);
        assertDoesNotThrow(() -> Algos.applyWindow(new double[0], new double[0]));
    }

    @Test
    void applyWindowRejectsInvalidArgumentsBeforeMutation() {
        double[] samples = {2, 4};
        assertThrows(IllegalArgumentException.class, () -> Algos.applyWindow(samples, new double[]{0}));
        assertArrayEquals(new double[]{2, 4}, samples);
        assertThrows(IllegalArgumentException.class, () -> Algos.applyWindow(null, samples));
        assertThrows(IllegalArgumentException.class, () -> Algos.applyWindow(samples, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 8, 15, 16, 32, 64, 127, 256, 1024})
    void impulseHasUnitMagnitudeAndCorrectPhase(int size) {
        double[] samples = new double[size];
        int impulsePosition = size / 2;
        samples[impulsePosition] = 1;
        complex[] bins = Algos.transform(samples);
        assertEquals(size, bins.length);
        for (int k = 0; k < size; k++) {
            double angle = -2 * Math.PI * k * impulsePosition / size;
            assertEquals(Math.cos(angle), bins[k].real(), EPSILON);
            assertEquals(Math.sin(angle), bins[k].imaginary(), EPSILON);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 7, 8, 16, 100, 128, 1024})
    void sinusoidsHaveExpectedMagnitudeAndPhase(int size) {
        double[] samples = new double[size];
        for (int n = 0; n < size; n++) {
            double angle = 2 * Math.PI * n / size;
            samples[n] = 2 + 3 * Math.cos(angle) + 4 * Math.sin(angle);
        }
        complex[] bins = Algos.transform(samples);
        for (int k = 0; k < size; k++) {
            double real = k == 0 ? 2.0 * size : (k == 1 || k == size - 1 ? 1.5 * size : 0);
            double imaginary = k == 1 ? -2.0 * size : (k == size - 1 ? 2.0 * size : 0);
            assertEquals(real, bins[k].real(), EPSILON);
            assertEquals(imaginary, bins[k].imaginary(), EPSILON);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 32, 128, 256})
    void fftMatchesReferenceDftAndPreservesInput(int size) {
        double[] samples = new Random(size).doubles(size, -1.0, 1.0).toArray();
        double[] original = samples.clone();
        complex[] bins = Algos.transform(samples);
        assertArrayEquals(original, samples);
        for (int k = 0; k < size; k++) {
            double real = 0;
            double imaginary = 0;
            for (int n = 0; n < size; n++) {
                double angle = 2 * Math.PI * k * n / size;
                real += samples[n] * Math.cos(angle);
                imaginary -= samples[n] * Math.sin(angle);
            }
            assertEquals(real, bins[k].real(), EPSILON);
            assertEquals(imaginary, bins[k].imaginary(), EPSILON);
        }
    }

    @Test
    void alternatingSamplesAppearOnlyAtNyquist() {
        complex[] bins = Algos.transform(new double[]{1, -1, 1, -1});
        for (int k = 0; k < bins.length; k++) {
            assertEquals(k == 2 ? 4 : 0, bins[k].real(), EPSILON);
            assertEquals(0, bins[k].imaginary(), EPSILON);
        }
    }

    @Test
    void transformRejectsInvalidSamples() {
        assertThrows(IllegalArgumentException.class, () -> Algos.transform(null));
        assertThrows(IllegalArgumentException.class, () -> Algos.transform(new double[0]));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Algos.transform(new double[]{invalid}));
        }
    }

    private static void assertWindow(windowType type, double[] expected) {
        assertArrayEquals(expected, Algos.createWindow(type, expected.length), EPSILON);
    }
}
