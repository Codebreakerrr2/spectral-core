package dev.usman.acoustic.algos;

import dev.usman.acoustic.data_structure.windowType;
import dev.usman.acoustic.data_structure.spectralFrame.complex;

/** Window functions and unnormalized forward Fourier transforms. */
public final class Algos {
    private static final double KAISER_BETA = 8.6;

    private Algos() {
    }

    /** Creates a symmetric window (denominator N - 1); a single sample has weight 1. */
    public static double[] createWindow(windowType window, int blockSize) {
        if (window == null || blockSize <= 0) {
            throw new IllegalArgumentException("window muss gesetzt und blockSize groesser als 0 sein");
        }
        double[] weights = new double[blockSize];
        if (blockSize == 1) {
            weights[0] = 1.0;
            return weights;
        }
        double kaiserScale = window == windowType.KAISER ? besselI0(KAISER_BETA) : 1.0;
        for (int i = 0; i < blockSize; i++) {
            double position = i / (double) (blockSize - 1);
            double angle = 2.0 * Math.PI * position;
            weights[i] = switch (window) {
                case RECTANGULAR -> 1.0;
                case HANN -> 0.5 - 0.5 * Math.cos(angle);
                case HAMMING -> 0.54 - 0.46 * Math.cos(angle);
                case BLACKMAN -> 0.42 - 0.5 * Math.cos(angle) + 0.08 * Math.cos(2 * angle);
                case BLACKMAN_HARRIS -> 0.35875 - 0.48829 * Math.cos(angle)
                        + 0.14128 * Math.cos(2 * angle) - 0.01168 * Math.cos(3 * angle);
                case BARTLETT -> 1.0 - Math.abs(2.0 * position - 1.0);
                case KAISER -> besselI0(KAISER_BETA * Math.sqrt(
                        Math.max(0.0, 1.0 - Math.pow(2.0 * position - 1.0, 2)))) / kaiserScale;
                case FLAT_TOP -> 0.21557895 - 0.41663158 * Math.cos(angle)
                        + 0.277263158 * Math.cos(2 * angle) - 0.083578947 * Math.cos(3 * angle)
                        + 0.006947368 * Math.cos(4 * angle);
            };
        }
        return weights;
    }

    /** Multiplies samples by the window in place; the arrays must have equal lengths. */
    public static void applyWindow(double[] samples, double[] window) {
        if (samples == null || window == null || samples.length != window.length) {
            throw new IllegalArgumentException("samples und window muessen gesetzt und gleich lang sein");
        }
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= window[i];
        }
    }

    /**
     * Returns all N complex bins of the forward DFT, without changing samples.
     * Uses a radix-2 FFT for power-of-two sizes and a direct DFT otherwise.
     */
    public static complex[] transform(double[] samples) {
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("samples darf nicht null oder leer sein");
        }
        for (double sample : samples) {
            if (!Double.isFinite(sample)) {
                throw new IllegalArgumentException("samples muessen endlich sein");
            }
        }
        int size = samples.length;
        if ((size & (size - 1)) != 0) {
            return directTransform(samples);
        }

        double[] real = samples.clone();
        double[] imaginary = new double[size];
        int bits = Integer.numberOfTrailingZeros(size);
        for (int i = 1; i < size; i++) {
            int reversed = Integer.reverse(i) >>> (Integer.SIZE - bits);
            if (i < reversed) {
                double value = real[i];
                real[i] = real[reversed];
                real[reversed] = value;
            }
        }

        // Stage count avoids an overflowing size-doubling loop at very large sizes.
        for (int stage = 1; stage <= bits; stage++) {
            int length = 1 << stage;
            int half = length / 2;
            for (int j = 0; j < half; j++) {
                double angle = -2.0 * Math.PI * j / length;
                double cosine = Math.cos(angle);
                double sine = Math.sin(angle);
                for (int even = j; even < size; even += length) {
                    int odd = even + half;
                    double oddReal = cosine * real[odd] - sine * imaginary[odd];
                    double oddImaginary = sine * real[odd] + cosine * imaginary[odd];
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                }
            }
        }

        complex[] bins = new complex[size];
        for (int i = 0; i < size; i++) {
            bins[i] = new complex(real[i], imaginary[i]);
        }
        return bins;
    }

    private static complex[] directTransform(double[] samples) {
        int size = samples.length;
        complex[] bins = new complex[size];
        for (int k = 0; k < size; k++) {
            double real = 0.0;
            double imaginary = 0.0;
            for (int n = 0; n < size; n++) {
                double angle = -2.0 * Math.PI * ((long) k * n % size) / size;
                real += samples[n] * Math.cos(angle);
                imaginary += samples[n] * Math.sin(angle);
            }
            bins[k] = new complex(real, imaginary);
        }
        return bins;
    }

    private static double besselI0(double value) {
        double sum = 1.0;
        double term = 1.0;
        double quarterSquare = value * value / 4.0;
        for (int k = 1; k < 100; k++) {
            term *= quarterSquare / (k * k);
            sum += term;
            if (term <= sum * 1e-16) {
                break;
            }
        }
        return sum;
    }
}
