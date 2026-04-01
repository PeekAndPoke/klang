# Instrument Prototypes — ExciterDsl

A collection of instrument designs built with the Osc DSL.
These are starting points — tweak parameters to taste.

## Woodwinds

### Flute

```javascript
let flute = Osc.register("flute",
    Osc.sine()
        .plus(Osc.triangle().mul(0.3))
        .plus(
            Osc.perlin(12).mul(0.2)
                .lowpass(4000)
                .highpass(800)
                .adsr(0.01, 0.12, 0.02, 0.01)
        )
        .plus(Osc.perlin(8).mul(0.05))
        .lowpass(3000)
        .highpass(400)
        .analog(0.15)
        .vibrato(4.5, 0.012)
        .pitchEnvelope(1.5, 0.01, 0.06)
        .adsr(0.06, 0.15, 0.75, 0.2)
)
```

### Clarinet

```javascript
let clarinet = Osc.register("clarinet",
    Osc.triangle().mul(0.7)
        .plus(Osc.square().mul(0.15))
        .plus(Osc.sine().mul(0.15))
        .plus(Osc.perlin(6).mul(0.02))
        .plus(
            Osc.perlin(10).mul(0.08).adsr(0.02, 0.1, 0.0, 0.01)
        )
        .lowpass(2800)
        .highpass(150)
        .warmth(4000)
        .vibrato(5, 0.003)
        .pitchEnvelope(0.5, 0.01, 0.06)
        .adsr(0.04, 0.08, 0.9, 0.1)
)
```

### Jazz Clarinet

```javascript
let jazzClar = Osc.register("jazzclar",
    Osc.triangle().mul(0.6)
        .plus(Osc.square().mul(0.25))
        .plus(Osc.sine().mul(0.15))
        .plus(Osc.perlin(8).mul(0.03))
        .plus(
            Osc.perlin(12).mul(0.1).adsr(0.02, 0.12, 0.0, 0.01)
        )
        .lowpass(3500)
        .highpass(150)
        .vibrato(4.5, 0.01)
        .pitchEnvelope(-1, 0.01, 0.1)
        .adsr(0.03, 0.1, 0.85, 0.12)
)
```

### Alto Saxophone

```javascript
let alto = Osc.register("alto",
    Osc.square().mul(0.6)
        .plus(Osc.saw().mul(0.3))
        .plus(Osc.sine().mul(0.1))
        .plus(Osc.perlin(10).mul(0.04))
        .plus(
            Osc.perlin(15).mul(0.12).adsr(0.02, 0.15, 0.0, 0.01)
        )
        .lowpass(3500)
        .highpass(200)
        .vibrato(4.5, 0.015)
        .pitchEnvelope(-2, 0.01, 0.12)
        .adsr(0.03, 0.1, 0.85, 0.12)
)
```

### Tenor Saxophone

```javascript
let tenor = Osc.register("tenor",
    Osc.square().mul(0.5)
        .plus(Osc.saw().mul(0.4))
        .plus(Osc.sine().mul(0.1))
        .plus(Osc.perlin(8).mul(0.05))
        .plus(
            Osc.perlin(12).mul(0.15).adsr(0.02, 0.2, 0.0, 0.01)
        )
        .lowpass(2500)
        .highpass(120)
        .vibrato(4, 0.018)
        .pitchEnvelope(-3, 0.01, 0.15)
        .adsr(0.04, 0.12, 0.8, 0.15)
)
```

### Soprano Saxophone

```javascript
let soprano = Osc.register("soprano",
    Osc.square().mul(0.5)
        .plus(Osc.saw().mul(0.35))
        .plus(Osc.sine().mul(0.15))
        .plus(Osc.perlin(12).mul(0.03))
        .plus(
            Osc.perlin(18).mul(0.1).adsr(0.01, 0.1, 0.0, 0.01)
        )
        .lowpass(5000)
        .highpass(300)
        .vibrato(5, 0.012)
        .pitchEnvelope(-1.5, 0.01, 0.08)
        .adsr(0.02, 0.08, 0.85, 0.1)
)
```

## Guitars

### Acoustic Guitar

```javascript
let acoustic = Osc.register("acoustic",
    Osc.pluck()
        .highpass(80)
        .lowpass(4000)
)
```

### Steel String

```javascript
let steel = Osc.register("steel",
    Osc.pluck()
        .lowpass(Osc.constant(5000).plus(
            Osc.constant(3000).adsr(0.001, 0.4, 0.0, 0.1)
        ))
        .highpass(100)
)
```

### Nylon (Classical)

```javascript
let nylon = Osc.register("nylon",
    Osc.pluck()
        .lowpass(2000)
        .warmth(3000)
        .highpass(80)
)
```

### 12-String

```javascript
let twelve = Osc.register("12string",
    Osc.superpluck()
        .lowpass(Osc.constant(4000).plus(
            Osc.constant(2000).adsr(0.001, 0.5, 0.0, 0.1)
        ))
        .highpass(100)
)
```

### Electric Clean

```javascript
let electric = Osc.register("electric",
    Osc.pluck()
        .lowpass(6000)
        .highpass(200)
        .phaser(0.3, 0.3)
)
```

### Electric Distorted

```javascript
let crunch = Osc.register("crunch",
    Osc.pluck()
        .lowpass(8000)
        .distort(0.6)
        .lowpass(4000)
        .highpass(150)
)
```

## Synth Pads

### Fat Analog Pad

```javascript
let fatpad = Osc.register("fatpad",
    Osc.supersaw()
        .analog(0.3)
        .lowpass(Osc.sine(0.3).plus(1).times(1000).plus(1500))
        .adsr(0.2, 0.5, 0.7, 1.0)
)
```

## Synth Leads

### Plucky Bass

```javascript
let bass = Osc.register("pluckbass",
    Osc.saw()
        .lowpass(Osc.param("cutoff", 800, "filter cutoff"))
        .adsr(0.005, 0.2, 0.0, 0.05)
)
```

### Bitcrushed Lead

```javascript
let crunchlead = Osc.register("crunchlead",
    Osc.square()
        .crush(6)
        .lowpass(3000)
        .adsr(0.01, 0.1, 0.8, 0.3)
)
```

## Bells & Mallet Percussion

### Glockenspiel

```javascript
let glock = Osc.register("glock",
    Osc.sine().mul(0.5)
        .plus(Osc.sine().detune(19.02).mul(0.3))
        .plus(Osc.sine().detune(27.86).mul(0.15))
        .plus(Osc.sine().detune(31.02).mul(0.1))
        .plus(
            Osc.whitenoise()
                .highpass(6000)
                .mul(0.15)
                .adsr(0.001, 0.02, 0.0, 0.005)
        )
        .lowpass(Osc.constant(8000).plus(
            Osc.constant(4000).adsr(0.001, 0.8, 0.0, 0.1)
        ))
        .adsr(0.001, 1.5, 0.0, 0.3)
)
```

### Celesta

```javascript
let celesta = Osc.register("celesta",
    Osc.sine().mul(0.6)
        .plus(Osc.sine().detune(19.02).mul(0.2))
        .plus(Osc.sine().detune(27.86).mul(0.08))
        .plus(
            Osc.perlin(20).mul(0.05).adsr(0.005, 0.03, 0.0, 0.01)
        )
        .lowpass(Osc.constant(5000).plus(
            Osc.constant(2000).adsr(0.001, 0.5, 0.0, 0.1)
        ))
        .warmth(6000)
        .adsr(0.005, 1.2, 0.0, 0.4)
)
```

### Music Box

```javascript
let musicbox = Osc.register("musicbox",
    Osc.sine().mul(0.6)
        .plus(Osc.sine().detune(12).mul(0.3))
        .plus(Osc.sine().detune(24).mul(0.1))
        .plus(
            Osc.whitenoise()
                .highpass(10000)
                .mul(0.1)
                .adsr(0.001, 0.01, 0.0, 0.005)
        )
        .lowpass(6000)
        .adsr(0.001, 0.6, 0.0, 0.1)
)
```

### FM Bell

```javascript
let bell = Osc.register("fmbell",
    Osc.sine().fm(Osc.sine(), 2.3, 400)
        .adsr(0.001, 1.5, 0.0, 0.5)
)
```

### Marimba

```javascript
let marimba = Osc.register("marimba",
    Osc.sine().mul(0.7)
        .plus(
            Osc.sine().detune(12).mul(0.15)
                .adsr(0.001, 0.08, 0.0, 0.02)
        )
        .plus(
            Osc.sine().detune(19.02).mul(0.08)
                .adsr(0.001, 0.04, 0.0, 0.01)
        )
        .plus(
            Osc.perlin(15).mul(0.12)
                .lowpass(1500)
                .highpass(200)
                .adsr(0.001, 0.03, 0.0, 0.005)
        )
        .lowpass(2500)
        .warmth(3000)
        .pitchEnvelope(1, 0.001, 0.04)
        .adsr(0.005, 0.5, 0.0, 0.08)
)
```

### Low Marimba

```javascript
let lowMarimba = Osc.register("lowmarimba",
    Osc.sine().mul(0.8)
        .plus(
            Osc.sine().detune(12).mul(0.1)
                .adsr(0.001, 0.1, 0.0, 0.02)
        )
        .plus(
            Osc.perlin(10).mul(0.1)
                .lowpass(800)
                .adsr(0.001, 0.04, 0.0, 0.005)
        )
        .lowpass(1800)
        .warmth(2000)
        .pitchEnvelope(0.5, 0.001, 0.05)
        .adsr(0.005, 0.8, 0.0, 0.1)
)
```

### Vibraphone

```javascript
let vibes = Osc.register("vibes",
    Osc.sine().mul(0.5)
        .plus(Osc.sine().detune(19.02).mul(0.25))
        .plus(Osc.sine().detune(27.86).mul(0.12))
        .plus(
            Osc.whitenoise()
                .highpass(4000)
                .mul(0.06)
                .adsr(0.001, 0.02, 0.0, 0.005)
        )
        .lowpass(6000)
        .tremolo(5.5, 0.3)
        .adsr(0.003, 2.0, 0.0, 0.5)
)
```

## Percussion

### Hi-Hat

```javascript
let hat = Osc.register("hat",
    Osc.whitenoise()
        .highpass(8000)
        .adsr(0.001, 0.05, 0.0, 0.01)
)
```

---

## Design Principles

### Waveform choice by instrument family

| Family       | Core waveform                  | Why                                      |
|--------------|--------------------------------|------------------------------------------|
| Flute        | Sine + triangle                | Open pipe, mostly fundamental            |
| Clarinet     | Triangle + light square        | Closed pipe, odd harmonics only          |
| Saxophone    | Square + saw                   | Reed buzz + conical bore (all harmonics) |
| Guitar       | Pluck (Karplus-Strong)         | Physical string model                    |
| Bells/metal  | Detuned sines                  | Inharmonic partials = metallic character |
| Marimba/wood | Sine + fast-decaying overtones | Wood absorbs overtones quickly           |

### Common techniques

- **Breath noise on attack**: `Osc.perlin(rate).mul(amount).adsr(fast attack, short decay, 0, short release)`
- **Filter envelope**: `lowpass(Osc.constant(base).plus(Osc.constant(sweep).adsr(...)))`
- **Per-partial envelopes**: each overtone gets its own ADSR (higher partials decay faster)
- **Pitch scoop**: `pitchEnvelope(semitones, attack, decay)` — negative for sax (scoop up), positive for mallet (pitch
  drop)
- **Material character**: lowpass cutoff defines material — wood ~2500Hz, brass ~3500Hz, metal ~8000Hz+
