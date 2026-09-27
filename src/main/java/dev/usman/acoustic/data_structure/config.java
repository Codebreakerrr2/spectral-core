package dev.usman.acoustic.data_structure;

public record config(int SampleRate,
                     int Blocksize,
                     int hopSize,
                     windowType window) {

    public config {
        if (SampleRate <= 0) {
            throw new IllegalArgumentException("SampleRate muss groesser als 0 sein");
        }
        if (Blocksize <= 0) {
            throw new IllegalArgumentException("Blocksize muss groesser als 0 sein");
        }
        if (hopSize <= 0 || hopSize > Blocksize) {
            throw new IllegalArgumentException("hopSize muss groesser als 0 und hoechstens Blocksize sein");
        }
        if (window == null) {
            throw new IllegalArgumentException("window darf nicht null sein");
        }
    }
}
