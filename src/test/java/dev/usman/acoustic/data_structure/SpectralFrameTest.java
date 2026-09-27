package dev.usman.acoustic.data_structure;

import dev.usman.acoustic.data_structure.spectralFrame.complex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpectralFrameTest {
    @Test
    void protectsBinsFromExternalMutation() {
        complex original = new complex(1, 2);
        complex[] bins = {original};
        spectralFrame frame = new spectralFrame(0, 0, 1, bins);
        bins[0] = new complex(99, 99);
        frame.bins()[0] = null;
        assertEquals(original, frame.bins()[0]);
    }

    @Test
    void rejectsInvalidMetadataAndBins() {
        complex[] bins = {new complex(0, 0)};
        assertThrows(IllegalArgumentException.class, () -> new spectralFrame(-1, 0, 1, bins));
        assertThrows(IllegalArgumentException.class, () -> new spectralFrame(0, -1, 1, bins));
        for (double spacing : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new spectralFrame(0, 0, spacing, bins));
        }
        assertThrows(IllegalArgumentException.class, () -> new spectralFrame(0, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new spectralFrame(0, 0, 1, new complex[0]));
        assertThrows(IllegalArgumentException.class, () -> new spectralFrame(0, 0, 1, new complex[]{null}));
    }
}
