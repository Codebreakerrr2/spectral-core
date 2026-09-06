# Spectral-Analyzer
## Beschreibung
Der Analyzer dient dazu um Audio Samples in fester Blockgroße zu analyzieren. Dabei erstellt der Analyzer anhand der Samplerate und Blockgroße einen 
Frequenzraster. für jeden Blockgröß wird dann berechnet wie stark und in welcher Phase die jeweiligen Frequenzmuster darin vertreten sind.
Das Ergebnis ist eine Frame, das die ganzen Berechnungen beinhaletet. 


## Datenstrukturen
config = {SampleRate, Blocksize, hopSize, WindowType}.

spectralFrame = {startSampleIndex, TimeStampNano, binSpacingHZ, [Real, Imaginary] }.



## Schnittstellen:

### spectralAnalyzer(config = {SampleRate, Blocksize, hopSize, WindowType}).
#### Vertrag:
samplerate > 0
Blocksize > 0 
hopSize > 0 und hopSize <= Blocksize
windowType nur die bekannten 
Im falle eines nicht zutreffens wird eine Illegalargument Exception. 

### acceptSamples(samplePacket) returns frame.
#### Vertrag:
Der Aufrufer ubergibt der Funktion acceptSamples(samplePacket) eine folge von Samples, wird die von Analyzer festgelegte Blockgroße erreicht, liefert der Analyzer den Frame, ansonsten wird die Samples in Analyzer gespeichert bis beim nächsten Aufruf die Blockgroße mit der Eingabe und dem Gespeicherten samples erreicht wird.

### reset()
#### Vertrag:
Die Funktion entleert die angenomene bisher gespeicherte Samples in Analyzer. 

## Algorithmen

