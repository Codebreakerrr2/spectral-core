package dev.usman.acoustic;

import dev.usman.acoustic.algos.Algos;
import dev.usman.acoustic.data_structure.config;
import dev.usman.acoustic.data_structure.spectralFrame;
import java.util.ArrayDeque;
import java.util.Deque;

/** Stateful mono sample analyzer. Use one instance per stream; not thread-safe. */
public class SpectralAnalyzer {

    private final config config;
    private final double[] window;
    private final Deque<double[]> packets = new ArrayDeque<>();
    private int firstPacketOffset;
    private long bufferedSamples;
    private long startSampleIndex;

    public SpectralAnalyzer(config config) {
        if (config == null) {
            throw new IllegalArgumentException("config darf nicht null sein");
        }
        this.config = config;
        this.window = Algos.createWindow(config.window(), config.Blocksize());
    }

    /**
     * Copies the packet and returns exactly one frame if a complete block is buffered,
     * otherwise null. Further complete blocks stay buffered. Pass an empty array to
     * retrieve the next buffered frame without adding samples.
     *
     * @param samples finite, real-valued mono samples (possibly empty)
     * @return the oldest available frame, or null for an incomplete block
     * @throws IllegalArgumentException if samples is null or contains NaN/infinity
     */
    public spectralFrame acceptSamples(double[] samples) {
        addSamples(samples);
        if (bufferedSamples < config.Blocksize()) {
            return null;
        }

        double[] block = getBlock();
        Algos.applyWindow(block, window);
        // Stream-relative time, rounded down; avoid multiplying the entire index.
        long timestamp = (startSampleIndex / config.SampleRate()) * 1_000_000_000L
                + (startSampleIndex % config.SampleRate()) * 1_000_000_000L / config.SampleRate();
        spectralFrame frame = new spectralFrame(startSampleIndex, timestamp,
                config.SampleRate() / (double) config.Blocksize(), Algos.transform(block));

        advance(config.hopSize());
        startSampleIndex += config.hopSize();
        return frame;
    }

    /** Discards all buffered samples and restarts sample indices and timestamps at zero. */
    public void reset() {
        packets.clear();
        firstPacketOffset = 0;
        bufferedSamples = 0;
        startSampleIndex = 0;
    }

    private void addSamples(double[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("samples darf nicht null sein");
        }
        for (double sample : samples) {
            if (!Double.isFinite(sample)) {
                throw new IllegalArgumentException("samples muessen endlich sein");
            }
        }
        if (samples.length > 0) {
            packets.addLast(samples.clone());
            bufferedSamples += samples.length;
        }
    }

    private double[] getBlock() {
        double[] block = new double[config.Blocksize()];
        int copied = 0;
        int offset = firstPacketOffset;
        for (double[] packet : packets) {
            int count = Math.min(packet.length - offset, block.length - copied);
            System.arraycopy(packet, offset, block, copied, count);
            copied += count;
            if (copied == block.length) {
                break;
            }
            offset = 0;
        }
        return block;
    }

    private void advance(int count) {
        bufferedSamples -= count;
        while (count > 0) {
            int available = packets.getFirst().length - firstPacketOffset;
            if (count < available) {
                firstPacketOffset += count;
                return;
            }
            count -= available;
            packets.removeFirst();
            firstPacketOffset = 0;
        }
    }
}
