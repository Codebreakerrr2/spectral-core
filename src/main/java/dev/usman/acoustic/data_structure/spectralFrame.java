package dev.usman.acoustic.data_structure;

/** A spectrum with stream-relative timing and all complex frequency bins in DFT order. */
public record spectralFrame(long startSampleIndex,
                            long TimeStampNano,
                            double binSpacingHZ,
                            complex[] bins) {
    public record complex(double real, double imaginary) {}

    public spectralFrame {
        if (startSampleIndex < 0) throw new IllegalArgumentException("startSampleIndex muss >= 0 sein");
        if (TimeStampNano < 0) throw new IllegalArgumentException("TimeStampNano muss >= 0 sein");
        if (!Double.isFinite(binSpacingHZ) || binSpacingHZ <= 0) {
            throw new IllegalArgumentException("binSpacingHZ muss endlich und groesser als 0 sein");
        }
        if (bins == null || bins.length == 0) {
            throw new IllegalArgumentException("bins darf nicht null oder leer sein");
        }
        bins = bins.clone();
        for (complex bin : bins) {
            if (bin == null) throw new IllegalArgumentException("bins darf keine null-Eintraege enthalten");
        }
    }

    /** Returns a copy so callers cannot modify an existing frame. */
    @Override
    public complex[] bins() {
        return bins.clone();
    }
}
