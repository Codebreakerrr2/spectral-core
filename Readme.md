# spectral-core

Java-Bibliothek zur blockweisen Spektralanalyse von Mono-Audiosamples. Der Analyzer
puffert eingehende Samples, wendet ein Fenster an und berechnet komplexe
Frequenz-Bins mit Real- und Imaginärteil.

**Ein Aufruf von `acceptSamples(double[])` liefert genau einen `spectralFrame`,
sobald mindestens ein vollständiger Block vorliegt.** Sind noch zu wenige Samples
vorhanden, ist das Ergebnis `null`. Auch wenn das Paket mehrere Blöcke enthält,
wird pro Aufruf nur ein Frame berechnet und zurückgegeben.

## Bauen und testen

Benötigt wird JDK 17 oder neuer, passend zum enthaltenen Gradle-9-Wrapper.
Die Bibliothek wird für Java 17 gebaut und hat keine externen Laufzeitabhängigkeiten.
Beim ersten Build lädt Gradle die benötigten Build- und Testabhängigkeiten herunter.

```powershell
# Windows / PowerShell
.\gradlew.bat build
.\gradlew.bat test
```

```sh
# Linux / macOS
./gradlew build
./gradlew test
```

Das JAR liegt unter `build/libs/spectral-core-1.0-SNAPSHOT.jar`.
Der Testbericht liegt unter `build/reports/tests/test/index.html`.
Das Projekt ist eine Bibliothek; das JAR hat keinen eigenen Programmeinstieg.

## Verwendung

Das folgende Beispiel kann als `Example.java` gespeichert werden:

```java
import dev.usman.acoustic.SpectralAnalyzer;
import dev.usman.acoustic.data_structure.config;
import dev.usman.acoustic.data_structure.spectralFrame;
import dev.usman.acoustic.data_structure.windowType;

public class Example {
    public static void main(String[] args) {
        var analyzer = new SpectralAnalyzer(
                new config(48_000, 4, 4, windowType.RECTANGULAR));

        // Zwei Samples reichen noch nicht für einen Block.
        System.out.println(analyzer.acceptSamples(new double[]{1, 2})); // null

        // Jetzt liegen zehn Samples vor. Trotzdem kommt nur EIN Frame zurück.
        spectralFrame first = analyzer.acceptSamples(
                new double[]{3, 4, 5, 6, 7, 8, 9, 10});
        System.out.println(first.startSampleIndex()); // 0: Samples 1, 2, 3, 4

        // Ein leeres Paket holt den nächsten gepufferten Frame ab.
        spectralFrame second = analyzer.acceptSamples(new double[0]);
        System.out.println(second.startSampleIndex()); // 4: Samples 5, 6, 7, 8
        System.out.println(analyzer.acceptSamples(new double[0])); // null

        // Die übrigen Samples 9 und 10 bleiben erhalten.
        spectralFrame third = analyzer.acceptSamples(new double[]{11, 12});
        System.out.println(third.startSampleIndex()); // 8

        var bins = first.bins();
        double magnitude = Math.hypot(bins[1].real(), bins[1].imaginary());
        double phase = Math.atan2(bins[1].imaginary(), bins[1].real());
        System.out.printf("Betrag: %.4f, Phase: %.4f rad%n", magnitude, phase);

        analyzer.reset();
    }
}
```

Nach dem Build lässt sich das Beispiel unter PowerShell ausführen:

```powershell
javac -encoding UTF-8 -cp build/libs/spectral-core-1.0-SNAPSHOT.jar Example.java
java -cp ".;build/libs/spectral-core-1.0-SNAPSHOT.jar" Example
```

Unter Linux/macOS ist das Trennzeichen im Klassenpfad `:` statt `;`.

## API-Vertrag

### `config(int SampleRate, int Blocksize, int hopSize, windowType window)`

| Parameter | Bedeutung | Bedingung |
| --- | --- | --- |
| `SampleRate` | Samples pro Sekunde | größer als 0 |
| `Blocksize` | Samples pro Analyseblock | größer als 0 |
| `hopSize` | Vorschub nach jedem ausgegebenen Frame | 1 bis einschließlich `Blocksize` |
| `window` | Fensterfunktion | darf nicht `null` sein |

Ungültige Konfigurationen und `new SpectralAnalyzer(null)` werfen
`IllegalArgumentException`. Die bestehenden Typ- und Zugriffsnamen einschließlich
ihrer Groß-/Kleinschreibung bleiben erhalten.

### `spectralFrame acceptSamples(double[] samples)`

1. Das Paket wird kopiert und hinten an den Puffer angehängt. Spätere Änderungen
   am übergebenen Array beeinflussen den Analyzer nicht.
2. Bei weniger als `Blocksize` gepufferten Samples wird `null` zurückgegeben.
3. Sonst werden die ältesten `Blocksize` Samples gefenstert und in genau einen
   Frame umgewandelt.
4. Danach rückt der Puffer um genau `hopSize` Samples vor. Alle übrigen Samples
   bleiben gespeichert, auch weitere vollständige Blöcke.

Bei `hopSize == Blocksize` schließen Frames direkt aneinander an.
Bei `hopSize < Blocksize` überlappen sie um `Blocksize - hopSize` Samples.
Die Startindizes sind `0`, `hopSize`, `2 * hopSize`, usw.; sie sind unabhängig von
Paketgrenzen. Neue Pakete werden auch dann hinten angehängt, wenn bereits ein
vollständiger Block wartet.

`acceptSamples(new double[0])` liefert den nächsten wartenden Frame oder `null`.
Zum vollständigen Abholen gepufferter Frames kann der Aufrufer diese Methode
wiederholt aufrufen. Wer dauerhaft mehr Samples zuführt als pro Aufruf durch
`hopSize` verbraucht werden, muss zusätzliche Abrufe einplanen, sonst wächst der
Puffer. Unvollständige Blöcke werden nicht automatisch mit Nullen aufgefüllt.

`null`, `NaN` und unendliche Samplewerte führen zu `IllegalArgumentException`, ohne
den bisherigen Puffer zu verändern. Samples sind reelle Mono-Werte; Dateidekodierung
und das Aufteilen mehrerer Audiokanäle übernimmt der Aufrufer. Pro Stream wird
eine eigene Instanz benötigt. Gleichzeitige Aufrufe auf derselben Instanz sind
nicht unterstützt.

### `void reset()`

Verwirft alle gepufferten Samples einschließlich Überlappung und noch nicht
abgeholter vollständiger Blöcke. Der nächste Frame beginnt wieder bei Sampleindex
und Zeitstempel 0. Konfiguration und Fenster bleiben erhalten.

### `spectralFrame`

| Feld | Bedeutung |
| --- | --- |
| `startSampleIndex` | Index des ersten Samples im Block seit Erstellung oder Reset |
| `TimeStampNano` | Zeit dieses Samples relativ zum Streambeginn in Nanosekunden, abgerundet |
| `binSpacingHZ` | Frequenzabstand: `SampleRate / (double) Blocksize` |
| `bins` | Alle `Blocksize` komplexen Bins als `complex(real, imaginary)` |

Der Zeitstempel wird aus dem Sampleindex berechnet, nicht aus der Systemzeit:
`floor(startSampleIndex * 1_000_000_000 / SampleRate)`.
`bins()` liefert eine Kopie des Arrays, sodass ein ausgegebener Frame stabil bleibt.

## Spektrum und Fenster

Die Vorwärtstransformation berechnet für das gefensterte Signal `x[n]`:

```text
X[k] = Summe für n = 0 .. N-1 von x[n] * exp(-i * 2π * k * n / N)
```

Die Bins sind unnormiert und folgen der üblichen DFT-Reihenfolge: Bin 0 ist der
Gleichanteil, danach folgen die positiven Frequenzen bis `floor(N / 2)` und
anschließend die negativen Frequenzen. Bei geradem `N` ist Bin `N / 2` die
Nyquist-Frequenz. Für die hinteren Bins lautet die Frequenz
`(k - N) * binSpacingHZ`. Diese Konvention entspricht der
[NumPy-DFT-Definition](https://numpy.org/doc/2.2/reference/routines.fft.html).

`Math.hypot(real, imaginary)` ergibt den Betrag und `Math.atan2(imaginary, real)`
die Phase in Radiant. Es erfolgt keine automatische Amplituden- oder
Fensterkorrektur. Beispielsweise hat ein binzentrierter Kosinus mit Amplitude 1
beim Rechteckfenster den Betrag `N / 2` in seinem positiven und negativen Bin
(außer bei Gleichanteil und Nyquist).

| `windowType` | Fenster |
| --- | --- |
| `RECTANGULAR` | Rechteck, alle Gewichte 1 |
| `HANN` | Hann |
| `HAMMING` | Hamming |
| `BLACKMAN` | Blackman mit drei Kosinustermen |
| `BLACKMAN_HARRIS` | Blackman-Harris mit vier Kosinustermen |
| `BARTLETT` | Dreieck mit Randwerten 0 |
| `KAISER` | Kaiser mit festem `beta = 8.6` |
| `FLAT_TOP` | Flat-Top mit fünf Kosinustermen |

Alle Fenster werden symmetrisch mit dem Nenner `N - 1` berechnet. Für `N = 1`
ist das Gewicht immer 1. Die Definitionen orientieren sich an den symmetrischen
[SciPy-Fenstern](https://docs.scipy.org/doc/scipy/reference/signal.windows.html).

`Algos.createWindow(window, blockSize)` erzeugt die Gewichte.
`Algos.applyWindow(samples, weights)` multipliziert Samples **in-place** mit den
Gewichten; beide Arrays müssen gesetzt und gleich lang sein.
`Algos.transform(samples)` liefert das vollständige Spektrum, ohne die Eingabe
zu verändern; die Eingabe darf nicht leer sein und muss ausschließlich endliche Werte
enthalten.

Für Zweierpotenzen nutzt die Transformation eine Radix-2-FFT mit `O(N log N)`.
Andere positive Blockgrößen sind ebenfalls erlaubt und nutzen eine direkte DFT
mit `O(N²)`. Für große Blöcke sind deshalb Zweierpotenzen wie 1024 oder 2048
sinnvoll.

## Tests

Die JUnit-Tests prüfen einzelne Frames bei großen Paketen, Restpuffer,
Überlappung, zufällige Paketgrenzen, Reset, Zeitstempel, Eingabevalidierung und
Arraykopien. Numerische Tests prüfen Fenstergewichte, Impulse, Sinus-/Kosinus-
Signale, die Nyquist-Frequenz und die FFT gegen eine Referenz-DFT.
