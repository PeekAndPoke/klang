# Klang Instrument Design Reference (Ignitor DSL)

> Paste this into any LLM to design custom instruments with the Osc builder.
> For KlangScript syntax basics, see `klangscript-basics.md`.
> For pattern language, see `sprudel-reference.md`.

Every file starts with:

```javascript
import * from "stdlib"
import * from "sprudel"
```

---

## Quick Start

### Simple plucked synth

```javascript
import * from "stdlib"
import * from "sprudel"

let myPluck = Osc.register("myPluck",
  Osc.saw()
    .lowpass(Osc.constant(2000).plus(Osc.constant(3000).adsr(0.001, 0.3, 0.0, 0.1)))
    .adsr(0.005, 0.3, 0.0, 0.05)
)

note("c3 e3 g3 c4").sound(myPluck).gain(0.5)
```

### Lush pad

```javascript
let pad = Osc.register("pad",
  Osc.supersaw()
    .analog(0.3)
    .lowpass(Osc.sine(0.3).plus(1).times(1000).plus(1500))
    .adsr(0.3, 0.5, 0.8, 1.5)
)

chord("<Am C F G>").voicing().sound(pad).gain(0.2).room(0.3).rsize(6)
```

### FM bell

```javascript
let bell = Osc.register("bell",
  Osc.sine()
    .fm(Osc.sine(), 2.3, 400)
    .adsr(0.001, 1.5, 0.0, 0.5)
)

note("c5 e5 g5 c6").sound(bell).gain(0.3).room(0.2)
```

---

## The Osc Builder API

Instruments are built by composing `Osc` nodes into signal graphs, then registering them:

```javascript
let name = Osc.register("name", oscGraph)
// Use in patterns:
note("c3 e3 g3").sound(name)
// or:
note("c3 e3 g3").sound("name")
```

### Oscillator Primitives

All accept optional `freq` param. Omit for voice note frequency, pass Hz for fixed frequency (e.g. LFO).

| Method                | Description                              |
|-----------------------|------------------------------------------|
| `Osc.sine(freq?)`     | Pure sine wave                           |
| `Osc.saw(freq?)`      | Sawtooth, anti-aliased (PolyBLEP)        |
| `Osc.square(freq?)`   | Square wave, anti-aliased                |
| `Osc.triangle(freq?)` | Triangle wave                            |
| `Osc.ramp(freq?)`     | Reverse sawtooth                         |
| `Osc.zawtooth(freq?)` | Naive sawtooth (brighter, no anti-alias) |
| `Osc.impulse(freq?)`  | Single-sample impulse per cycle          |
| `Osc.pulze(freq?)`    | Variable duty-cycle pulse                |

### Super Oscillators (Unison/Detuned)

Multiple detuned copies for thick, lush sounds.

| Method                                                  | Description             |
|---------------------------------------------------------|-------------------------|
| `Osc.supersaw(freq?, voices?, freqSpread?, analog?)`    | Detuned sawtooth chorus |
| `Osc.supersine(freq?, voices?, freqSpread?, analog?)`   | Detuned sine chorus     |
| `Osc.supersquare(freq?, voices?, freqSpread?, analog?)` | Detuned square chorus   |
| `Osc.supertri(freq?, voices?, freqSpread?, analog?)`    | Detuned triangle chorus |
| `Osc.superramp(freq?, voices?, freqSpread?, analog?)`   | Detuned ramp chorus     |

**Parameters** (all optional, apply to every super oscillator):

| Param        | Default | Description                             |
|--------------|---------|-----------------------------------------|
| `voices`     | 8       | Number of detuned voices                |
| `freqSpread` | 0.2     | Frequency spread between voices         |
| `analog`     | 0.0     | Random per-voice pitch drift (0 = none) |

```javascript
// Args order: supersaw(freq, voices, freqSpread, analog)

// Thin 3-voice supersaw
Osc.supersaw(Osc.freq(), /* voices */ 3, /* freqSpread */ 0.1)

// Wide 12-voice pad with analog drift
Osc.supersaw(Osc.freq(), /* voices */ 12, /* freqSpread */ 0.3, /* analog */ 0.2)
```

### Noise Sources

| Method                  | Description                             |
|-------------------------|-----------------------------------------|
| `Osc.whitenoise()`      | Flat spectrum noise                     |
| `Osc.brownnoise()`      | Low-frequency weighted (-6 dB/oct)      |
| `Osc.pinknoise()`       | Balanced noise (-3 dB/oct)              |
| `Osc.perlin(rate?)`     | Smooth organic noise (default rate=1.0) |
| `Osc.berlin(rate?)`     | Angular piecewise-linear noise          |
| `Osc.dust(density?)`    | Sparse random impulses (default 0.2)    |
| `Osc.crackle(density?)` | Bipolar random crackle                  |

### Physical Models

| Method                                                 | Description                     |
|--------------------------------------------------------|---------------------------------|
| `Osc.pluck(freq?)`                                     | Karplus-Strong plucked string   |
| `Osc.superpluck(freq?, voices?, freqSpread?, analog?)` | Unison plucked strings (chorus) |

### Utility

| Method                            | Description                                     |
|-----------------------------------|-------------------------------------------------|
| `Osc.freq()`                      | Voice note frequency (use as param source)      |
| `Osc.param(name, default, desc?)` | Named parameter slot (overridable at play time) |
| `Osc.constant(value)`             | Fixed value (not overridable)                   |
| `Osc.silence()`                   | Zero output                                     |

---

## Processing Chain (Extension Methods)

Chain methods onto any `Osc` node. All numeric params accept either a number or another `Osc` node for audio-rate
modulation.

### Filters

| Method                      | Description                         |
|-----------------------------|-------------------------------------|
| `.lowpass(cutoffHz, q?)`    | Resonant lowpass (default q=0.707)  |
| `.highpass(cutoffHz, q?)`   | Resonant highpass                   |
| `.warmth(cutoffHz)`         | Gentle one-pole lowpass (-6 dB/oct) |
| `.onePoleLowpass(cutoffHz)` | Same as warmth                      |
| `.bandpass(cutoffHz, q?)`   | Bandpass filter                     |
| `.notch(cutoffHz, q?)`      | Band-reject (notch) filter          |

### Envelope

| Method                                   | Description                                                    |
|------------------------------------------|----------------------------------------------------------------|
| `.adsr(attack, decay, sustain, release)` | ADSR amplitude envelope (all in seconds, sustain is 0-1 level) |

### Effects

| Method                                  | Description                                |
|-----------------------------------------|--------------------------------------------|
| `.drive(amount, driveType?)`            | Pre-amplification (type: "linear")         |
| `.clip(shape?)`                         | Pure waveshaping without drive             |
| `.distort(amount, shape?)`              | Drive + clip combined                      |
| `.crush(amount)`                        | Bit-depth reduction                        |
| `.coarse(amount)`                       | Sample-rate reduction                      |
| `.phaser(rate, depth, center?, sweep?)` | Allpass phaser (center/sweep default 1000) |
| `.tremolo(rate, depth)`                 | Amplitude LFO modulation                   |

Distortion/clip shapes: `"soft"` (tanh, default), `"hard"`, `"gentle"`, `"cubic"`, `"diode"`, `"fold"`, `"chebyshev"`,
`"rectify"`, `"exp"`

### FM Synthesis

| Method                         | Description                                                        |
|--------------------------------|--------------------------------------------------------------------|
| `.fm(modulator, ratio, depth)` | FM synthesis with modulator, frequency ratio, and modulation depth |

The modulator is another `Osc` node. `ratio` sets the modulator frequency relative to the carrier. `depth` is the
modulation amount in Hz.

### Pitch Modulation

| Method                                              | Description                                |
|-----------------------------------------------------|--------------------------------------------|
| `.detune(semitones)`                                | Shift pitch by semitones                   |
| `.octaveUp()`                                       | +12 semitones                              |
| `.octaveDown()`                                     | -12 semitones                              |
| `.vibrato(rate, depth)`                             | Sinusoidal pitch LFO                       |
| `.accelerate(amount)`                               | Exponential pitch ramp over voice duration |
| `.pitchEnvelope(amount, attack?, decay?, release?)` | Pitch sweep envelope (amount in semitones) |

### Analog Drift

| Method            | Description                                 |
|-------------------|---------------------------------------------|
| `.analog(amount)` | Perlin noise pitch jitter for analog warmth |

### Arithmetic (Signal Mixing)

| Method          | Description                        |
|-----------------|------------------------------------|
| `.plus(other)`  | Add two signals (layering)         |
| `.minus(other)` | Subtract signal                    |
| `.times(other)` | Multiply signals (ring modulation) |
| `.mul(factor)`  | Scale amplitude                    |
| `.div(divisor)` | Divide amplitude                   |

---

## Parameter System

### `Osc.param(name, default)` — Overridable at play time

```javascript
let bass = Osc.register("bass",
  Osc.saw()
    .lowpass(Osc.param("cutoff", 800, "filter cutoff"))
    .adsr(0.005, 0.2, 0.0, 0.05)
)

// Override param in pattern:
note("c2").sound(bass).oscParam("cutoff", 1200)
```

### `Osc.constant(value)` — Fixed, not overridable

Use when you want an exact locked value:

```javascript
Osc.saw().lowpass(Osc.constant(2000))  // always 2000 Hz, cannot be overridden
```

### `Osc.freq()` — Voice note frequency

Special node that outputs the voice's current note frequency:

```javascript
Osc.sine()  // freq defaults to Osc.freq() when omitted
Osc.sine(Osc.freq())  // equivalent to above
Osc.sine(5)  // fixed 5 Hz (for LFO use)
```

### Audio-rate Modulation

Any parameter can accept an Osc node instead of a number:

```javascript
// Filter cutoff modulated by LFO
Osc.saw().lowpass(Osc.sine(0.3).plus(1).times(1000).plus(500))

// Tremolo via multiplication
Osc.saw().times(Osc.sine(4).plus(1).mul(0.5))  // 4 Hz tremolo

// Vibrato via frequency modulation
Osc.sine(Osc.freq().plus(Osc.sine(5).mul(10)))  // 5 Hz vibrato, 10 Hz depth
```

---

## Built-in Presets

| Name          | Aliases                  | Signal Graph                                                    |
|---------------|--------------------------|-----------------------------------------------------------------|
| `sine`        | `sin`                    | Sine(Freq)                                                      |
| `sawtooth`    | `saw`                    | Sawtooth(Freq)                                                  |
| `square`      | `sqr`, `pulse`           | Square(Freq)                                                    |
| `triangle`    | `tri`                    | Triangle(Freq)                                                  |
| `ramp`        |                          | Ramp(Freq)                                                      |
| `zawtooth`    | `zaw`                    | Zawtooth(Freq)                                                  |
| `pulze`       |                          | Pulze(Freq, duty=0.5)                                           |
| `impulse`     |                          | Impulse(Freq)                                                   |
| `supersaw`    |                          | SuperSaw(Freq, voices=8, spread=0.2)                            |
| `supersine`   |                          | SuperSine(Freq, voices=8, spread=0.2)                           |
| `supersquare` | `supersqr`, `superpulse` | SuperSquare(Freq, voices=8, spread=0.2)                         |
| `supertri`    |                          | SuperTri(Freq, voices=8, spread=0.2)                            |
| `superramp`   |                          | SuperRamp(Freq, voices=8, spread=0.2)                           |
| `pluck`       | `ks`, `string`           | Pluck(Freq, decay=0.996, brightness=0.5, pick=0.5, stiffness=0) |
| `superpluck`  |                          | SuperPluck(Freq, voices=8, spread=0.2, ...)                     |
| `whitenoise`  | `white`                  | WhiteNoise                                                      |
| `brownnoise`  | `brown`                  | BrownNoise                                                      |
| `pinknoise`   | `pink`                   | PinkNoise                                                       |
| `perlinnoise` | `perlin`                 | PerlinNoise(rate=1)                                             |
| `berlinnoise` | `berlin`                 | BerlinNoise(rate=1)                                             |
| `dust`        |                          | Dust(density=0.2)                                               |
| `crackle`     |                          | Crackle(density=0.2)                                            |
| `sgpad`       |                          | (Saw + Saw.detune(0.1)) / 2 -> onePoleLowpass(3000)             |
| `sgbell`      |                          | Sine.fm(Sine, ratio=1.4, depth=300, decay=0.5)                  |
| `sgbuzz`      |                          | Square.lowpass(2000)                                            |

---

## Instrument Recipes

### Woodwinds

**Flute** — Sine + triangle + breath noise + vibrato

```javascript
let flute = Osc.register("flute",
    Osc.sine()
        .plus(Osc.triangle().mul(0.3))
        .plus(Osc.perlin(12).mul(0.2).lowpass(4000).highpass(800).adsr(0.01, 0.12, 0.02, 0.01))
        .plus(Osc.perlin(8).mul(0.05))
        .lowpass(3000).highpass(400)
        .analog(0.15).vibrato(4.5, 0.012)
        .pitchEnvelope(1.5, 0.01, 0.06)
        .adsr(0.06, 0.15, 0.75, 0.2)
)
```

**Clarinet** — Triangle (odd harmonics) + light square + breath

```javascript
let clarinet = Osc.register("clarinet",
    Osc.triangle().mul(0.7)
        .plus(Osc.square().mul(0.15))
        .plus(Osc.sine().mul(0.15))
        .plus(Osc.perlin(6).mul(0.02))
        .plus(Osc.perlin(10).mul(0.08).adsr(0.02, 0.1, 0.0, 0.01))
        .lowpass(2800).highpass(150).warmth(4000)
        .vibrato(5, 0.003)
        .pitchEnvelope(0.5, 0.01, 0.06)
        .adsr(0.04, 0.08, 0.9, 0.1)
)
```

**Alto Saxophone** — Square + saw (reed buzz + conical bore)

```javascript
let alto = Osc.register("alto",
    Osc.square().mul(0.6)
        .plus(Osc.saw().mul(0.3))
        .plus(Osc.sine().mul(0.1))
        .plus(Osc.perlin(10).mul(0.04))
        .plus(Osc.perlin(15).mul(0.12).adsr(0.02, 0.15, 0.0, 0.01))
        .lowpass(3500).highpass(200)
        .vibrato(4.5, 0.015)
        .pitchEnvelope(-2, 0.01, 0.12)
        .adsr(0.03, 0.1, 0.85, 0.12)
)
```

### Guitars

**Acoustic Guitar** — Karplus-Strong with natural filtering

```javascript
let acoustic = Osc.register("acoustic",
    Osc.pluck().highpass(80).lowpass(4000)
)
```

**Steel String** — Brighter attack with filter envelope

```javascript
let steel = Osc.register("steel",
    Osc.pluck()
        .lowpass(Osc.constant(5000).plus(Osc.constant(3000).adsr(0.001, 0.4, 0.0, 0.1)))
        .highpass(100)
)
```

**12-String** — Unison pluck for chorus effect

```javascript
let twelve = Osc.register("12string",
    Osc.superpluck()
        .lowpass(Osc.constant(4000).plus(Osc.constant(2000).adsr(0.001, 0.5, 0.0, 0.1)))
        .highpass(100)
)
```

**Electric Distorted**

```javascript
let crunch = Osc.register("crunch",
    Osc.pluck().lowpass(8000).distort(0.6).lowpass(4000).highpass(150)
)
```

### Synth Pads

**Fat Analog Pad** — Supersaw with LFO-modulated filter

```javascript
let fatpad = Osc.register("fatpad",
    Osc.supersaw()
        .analog(0.3)
        .lowpass(Osc.sine(0.3).plus(1).times(1000).plus(1500))
        .adsr(0.2, 0.5, 0.7, 1.0)
)
```

### Synth Bass

**Plucky Bass** — Saw with fast filter envelope

```javascript
let bass = Osc.register("pluckbass",
    Osc.saw()
        .lowpass(Osc.param("cutoff", 800, "filter cutoff"))
        .adsr(0.005, 0.2, 0.0, 0.05)
)
```

### Synth Leads

**Bitcrushed Lead**

```javascript
let crunchlead = Osc.register("crunchlead",
    Osc.square().crush(6).lowpass(3000).adsr(0.01, 0.1, 0.8, 0.3)
)
```

### Bells & Mallet Percussion

**Glockenspiel** — Detuned sine partials + noise transient

```javascript
let glock = Osc.register("glock",
    Osc.sine().mul(0.5)
        .plus(Osc.sine().detune(19.02).mul(0.3))
        .plus(Osc.sine().detune(27.86).mul(0.15))
        .plus(Osc.sine().detune(31.02).mul(0.1))
        .plus(Osc.whitenoise().highpass(6000).mul(0.15).adsr(0.001, 0.02, 0.0, 0.005))
        .lowpass(Osc.constant(8000).plus(Osc.constant(4000).adsr(0.001, 0.8, 0.0, 0.1)))
        .adsr(0.001, 1.5, 0.0, 0.3)
)
```

**FM Bell** — Inharmonic FM for metallic character

```javascript
let bell = Osc.register("fmbell",
    Osc.sine().fm(Osc.sine(), 2.3, 400)
        .adsr(0.001, 1.5, 0.0, 0.5)
)
```

**Marimba** — Sine with fast-decaying overtones + wood attack

```javascript
let marimba = Osc.register("marimba",
    Osc.sine().mul(0.7)
        .plus(Osc.sine().detune(12).mul(0.15).adsr(0.001, 0.08, 0.0, 0.02))
        .plus(Osc.sine().detune(19.02).mul(0.08).adsr(0.001, 0.04, 0.0, 0.01))
        .plus(Osc.perlin(15).mul(0.12).lowpass(1500).highpass(200).adsr(0.001, 0.03, 0.0, 0.005))
        .lowpass(2500).warmth(3000)
        .pitchEnvelope(1, 0.001, 0.04)
        .adsr(0.005, 0.5, 0.0, 0.08)
)
```

**Vibraphone** — Detuned sines with tremolo

```javascript
let vibes = Osc.register("vibes",
    Osc.sine().mul(0.5)
        .plus(Osc.sine().detune(19.02).mul(0.25))
        .plus(Osc.sine().detune(27.86).mul(0.12))
        .plus(Osc.whitenoise().highpass(4000).mul(0.06).adsr(0.001, 0.02, 0.0, 0.005))
        .lowpass(6000)
        .tremolo(5.5, 0.3)
        .adsr(0.003, 2.0, 0.0, 0.5)
)
```

**Music Box** — Bright octave-stacked sines

```javascript
let musicbox = Osc.register("musicbox",
    Osc.sine().mul(0.6)
        .plus(Osc.sine().detune(12).mul(0.3))
        .plus(Osc.sine().detune(24).mul(0.1))
        .plus(Osc.whitenoise().highpass(10000).mul(0.1).adsr(0.001, 0.01, 0.0, 0.005))
        .lowpass(6000)
        .adsr(0.001, 0.6, 0.0, 0.1)
)
```

### Synth Percussion

**Synth Kick** — Sine with pitch envelope

```javascript
let kick = Osc.register("kick",
    Osc.sine()
        .pitchEnvelope(24, 0.001, 0.04)
        .adsr(0.001, 0.2, 0.0, 0.02)
)
```

**Hi-Hat** — Filtered white noise

```javascript
let hat = Osc.register("hat",
    Osc.whitenoise()
        .highpass(8000)
        .adsr(0.001, 0.05, 0.0, 0.01)
)
```

**Rim** — Sine + noise transient

```javascript
let rim = Osc.register("rim",
    Osc.sine(800)
        .plus(Osc.whitenoise().highpass(4000).mul(0.3))
        .lowpass(3000)
        .adsr(0.001, 0.03, 0.0, 0.005)
)
```

---

## Design Principles

### Waveform Selection

| Family       | Core waveform                         | Why                                      |
|--------------|---------------------------------------|------------------------------------------|
| Flute        | Sine + triangle                       | Open pipe, mostly fundamental            |
| Clarinet     | Triangle + square                     | Closed pipe, odd harmonics               |
| Saxophone    | Square + saw                          | Reed buzz + conical bore (all harmonics) |
| Guitar       | Pluck (Karplus-Strong)                | Physical string model                    |
| Bells/metal  | Detuned sines at inharmonic intervals | Inharmonic partials = metallic character |
| Marimba/wood | Sine + fast-decaying overtones        | Wood absorbs overtones quickly           |
| Pads         | Supersaw/supersine                    | Multiple voices = thick, full texture    |
| Bass         | Saw or square                         | Rich harmonics for warmth                |
| Leads        | Saw, square, or FM                    | Bright, cuts through mix                 |

### Common Techniques

**Breath noise on attack** (woodwinds):

```javascript
Osc.perlin(rate).mul(amount).adsr(fast_attack, short_decay, 0, short_release)
```

**Filter envelope** (brightness decay):

```javascript
.lowpass(Osc.constant(base).plus(Osc.constant(sweep).adsr(attack, decay, sustain, release)))
```

**Per-partial envelopes** (bells, mallet):

```javascript
Osc.sine().mul(0.5)                                                // fundamental
    .plus(Osc.sine().detune(12).mul(0.3).adsr(0.001, 0.3, 0, 0))  // octave, decays faster
    .plus(Osc.sine().detune(19).mul(0.1).adsr(0.001, 0.1, 0, 0))  // fifth+oct, even faster
```

**Pitch scoop** (attack transient):

```javascript
.pitchEnvelope(semitones, attack, decay)
// Positive = pitch drops (mallet percussion)
// Negative = pitch scoops up (saxophone, brass)
```

**Material character** (lowpass cutoff defines material):

- Wood: ~2500 Hz
- Brass: ~3500 Hz
- Metal/glass: 6000-10000+ Hz

**LFO modulation** (use low-frequency Osc as modulation source):

```javascript
// Filter LFO: sine at 0.3 Hz modulating cutoff 500-2500 Hz
.lowpass(Osc.sine(0.3).plus(1).times(1000).plus(500))

// The pattern: Osc.lfo(freq).plus(1) maps -1..1 to 0..2
// Then .times(range/2).plus(center) maps to your desired range
```

**Layering oscillators** (additive synthesis):

```javascript
Osc.sine()                      // fundamental
    .plus(Osc.saw().mul(0.3))   // add brightness
    .plus(Osc.perlin(8).mul(0.05))  // add organic movement
    .mul(0.5)                   // normalize level
```

---

## Complete Example: Custom Instruments in a Track

```javascript
import * from "stdlib"
import * from "sprudel"

// Custom instruments
let koto = Osc.register("koto", Osc.pluck()
    .plus(Osc.sine().detune(12).mul(0.1).adsr(0.001, 0.3, 0.0, 0.05))
    .lowpass(Osc.constant(5000).plus(Osc.constant(3000).adsr(0.001, 0.3, 0.0, 0.05)))
    .highpass(200)
)

let pad = Osc.register("pad", Osc.supersine().analog(0.3)
    .lowpass(Osc.sine(0.08).plus(1).times(300).plus(800))
    .adsr(0.8, 0.5, 0.9, 2.0)
)

let kick = Osc.register("kick", Osc.sine()
    .pitchEnvelope(24, 0.001, 0.04)
    .adsr(0.001, 0.2, 0.0, 0.02)
)

let sub = Osc.register("sub", Osc.sine().lowpass(200)
    .adsr(0.005, 0.3, 0.0, 0.05)
)

// Composition
stack(
  // Melody
  note("a4 b4 c5 b4 a4 [b4 a4] f4@2")
    .sound(koto).legato(0.8).slow(4)
    .superimpose(fast(2).gain(0.075).pan(0.0), fast(2).gain(0.075).pan(1.0)),

  // Pad chords
  note("a2 d2 a2 f2").sound(pad).slow(4).legato(1.5).gain(0.25),

  // Bass
  note("a1 d2 a1 f1").sound(sub).slow(4).legato(1.5).gain(0.75),

  // Drums
  note("a1 ~ ~ ~ ~ ~ ~ ~").sound(kick).gain(0.8),
  sound("~ ~ ~ ~ cp ~ ~ ~").gain(0.4),
  sound("hh*8").gain(0.3)
).room(0.2).rsize(5).delay(0.15).delaytime(pure(1/8).div(cps))
```
