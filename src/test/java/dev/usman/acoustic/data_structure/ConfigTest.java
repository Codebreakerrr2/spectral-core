package dev.usman.acoustic.data_structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {

    @Test
    void acceptsValidConfiguration() {
        for (windowType window : windowType.values()) {
            assertAll(
                    () -> assertDoesNotThrow(() -> new config(48000, 1024, 512, window)),
                    () -> assertDoesNotThrow(() -> new config(48000, 1024, 1024, window)),
                    () -> assertDoesNotThrow(() -> new config(1, 1, 1, window))
            );
        }
    }

    @Test
    void rejectsNonPositiveSampleRate() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(0, 1024, 512, windowType.HANN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(-1, 1024, 512, windowType.HANN))
        );
    }

    @Test
    void rejectsNonPositiveBlockSize() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(48000, 0, 1, windowType.HANN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(48000, -1, 1, windowType.HANN))
        );
    }

    @Test
    void rejectsHopSizeOutsideValidRange() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(48000, 1024, 0, windowType.HANN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(48000, 1024, -1, windowType.HANN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new config(48000, 1024, 1025, windowType.HANN))
        );
    }

    @Test
    void rejectsNullWindowType() {
        assertThrows(IllegalArgumentException.class,
                () -> new config(48000, 1024, 512, null));
    }
}
