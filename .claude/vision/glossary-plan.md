# Klang Audio Motor — Glossary Plan

> Status: Draft — 2026-04-08

## Purpose

Create a **disambiguated vocabulary** across two domains that normally reuse the same words:
**Audio** (signal processing) and **Pattern** (event sequencing).

This glossary is not a passive reference — it's the **conceptual skeleton** of the platform.
The two-domain split IS the mental model for how Klang works.

---

## Guiding Principles

- **"Behind Glass"** — plain-language first, technical depth available but never required
- **Three-beat definitions** — *what it does* / *what it sounds like* / *what you control*
- **Bridge to conventional terms** — Motor-coined vocabulary links to standard music/DSP terminology
- **Zero overlap** between audio and pattern vocabularies

---

## Domain 1: Audio (Signal Domain)

*What happens to the waveform — the sound itself.*

### Pipeline Stages (in signal chain order)

#### Tuning

Priming filter cutoffs before the chain runs. The engine adjusts control parameters
at the start of each audio block so that filters respond to modulation sources.

*In traditional synthesis: control-rate modulation, parameter automation.*

#### Degrading

Irreversibly reducing resolution. Throws away information to create lo-fi, digital artifacts.

- **Crush** — reduces bit depth. Fewer amplitude steps = grittier, more stepped sound.
  Like hearing audio through a cheap walkie-talkie. You control the `crush` amount.
- **Coarse** — reduces sample rate. Fewer time steps = aliased, metallic quality.
  Like audio from a 1980s sampler. You control the `coarse` amount.

*In traditional synthesis: bit-crushing, sample-rate reduction, decimation.*

#### Carving

Removing frequencies to shape the sound's character. A carved sound is sharper,
more focused, less muddy.

- **LP (Low-Pass)** — lets lows through, dulls the highs. Sound gets darker, warmer —
  like hearing music through a wall. You control the `cutoff` frequency.
- **HP (High-Pass)** — lets highs through, removes the bottom. Sound gets thinner, airier —
  like turning down the bass knob. You control the `cutoff` frequency.
- **BP (Band-Pass)** — keeps only a frequency band, removes everything above and below.
  Sounds nasal, telephone-like. You control the `center` frequency and `width`.
- **Notch** — removes one frequency band, keeps everything else.
  Opposite of band-pass. You control what to cut out.

*In traditional synthesis: subtractive filtering, EQ.*

#### Contouring

Shaping the sound's amplitude over time — how it begins, lives, and dies.

- **ADSR (Attack, Decay, Sustain, Release)** — four stages that define the volume envelope:
    - **Attack** — how quickly the sound reaches full volume (fast = percussive, slow = swelling)
    - **Decay** — how quickly it falls from peak to sustain level
    - **Sustain** — the steady-state volume while a note is held
    - **Release** — how quickly it fades after the note ends

A piano has fast attack, no sustain. A violin has slow attack, full sustain.
You control A/D/S/R times and levels.

*In traditional synthesis: amplitude envelope, VCA (Voltage-Controlled Amplifier).*

#### Saturating

Adding harmonics through nonlinear waveshaping. Pushes the signal into a transfer curve
that clips or folds it, generating new overtones.

Gentle saturation = warm, analog-like. Heavy saturation = aggressive, fuzzy.
You control the `distort` amount and `shape` (soft clip, hard clip, fold, etc.).

*In traditional synthesis: distortion, overdrive, waveshaping, fuzz.*

#### Animating

Periodic cycling of a parameter, driven by an LFO. Makes the sound move and breathe.

- **Tremolo** — amplitude animation. Volume goes up and down rhythmically.
  Like a guitarist's amp tremolo. You control `rate` (speed) and `depth` (intensity).
- **Phaser** — spectral animation. Sweeping notches move through the frequency spectrum.
  Creates a "whooshing" or "jet" quality. You control `rate`, `depth`, `center`, and `sweep`.

*In traditional synthesis: LFO modulation, tremolo, phasing.*

### Audio — Additional Concepts

#### Mixing

Combining multiple sound sources. When orbits/channels play simultaneously, their signals
are summed together. You control each channel's `gain` (volume level).

*In traditional production: mixing, gain staging, summing.*

#### Spatializing

Placing the sound in the stereo field. Controls where you hear it — left, center, right,
or anywhere in between. You control `pan` (0.0 = left, 0.5 = center, 1.0 = right).

*In traditional production: panning, stereo imaging.*

---

## Domain 2: Pattern (Event Domain)

*What happens to the sequence — when and how notes play.*

### Pattern Operations (by category)

#### Sequencing

The fundamental concept: events happen in an order, one after another, within a cycle.
A pattern is a sequence of musical instructions — notes, rests, chords — that repeats.

#### Cycling

The pattern repeats. Every pattern in Klang loops — one cycle is one complete pass through
the sequence. The cycle length is tied to tempo (BPM). This is the heartbeat of everything.

#### Arranging

Reordering events within a cycle without adding or removing them.

- **rev** — play the pattern backwards
- **palindrome** — play forward then backward
- **rotate** — shift events in time within the cycle

*The sequence stays the same size, just reorganized.*

#### Pacing

Changing the speed or density of events.

- **fast** — compress events into less time (double speed = twice as dense)
- **slow** — stretch events over more time (half speed = twice as sparse)

*Changes how many events fit in one cycle.*

#### Gating

Conditionally selecting which events play. Introduces variation over time by applying
rules about when something happens.

- **every** — apply a transformation every Nth cycle
- **when** — apply only when a condition is true
- **sometimesBy** — apply with a probability

*Events are allowed through or blocked — like a gate opening and closing.*

#### Mapping

Transforming values per event. Takes each event and computes a new value from the old one.

- **+12** — transpose up an octave
- **scale** — map values into a musical scale
- **transpose** — shift pitch by a fixed amount

*The pattern structure stays the same; the values change.*

#### Layering

Combining multiple patterns into one. Patterns play simultaneously, their events interleaved.

- **stack** — play multiple patterns at the same time
- **layer** — overlay patterns on top of each other
- **superimpose** — add a transformed copy on top of the original

*More events, same time span.*

#### Spawning

Generating new events that didn't exist in the original pattern.

- **random** — pick a random value
- **choose** — pick from a set
- **shuffle** — randomize order

*Creates new material from rules, not from existing events.*

---

## Abbreviations & Technical Terms

| Abbreviation | Full Name                       | Plain Explanation                                                                                       |
|--------------|---------------------------------|---------------------------------------------------------------------------------------------------------|
| **ADSR**     | Attack, Decay, Sustain, Release | The four stages of a volume envelope — how a sound is born, lives, and dies                             |
| **BP**       | Band-Pass (filter)              | Keeps only one frequency band, removes everything above and below                                       |
| **BPM**      | Beats Per Minute                | Tempo — how fast the music plays. 120 BPM = 2 beats per second                                          |
| **dB**       | Decibel                         | Unit for measuring loudness. 0 dB = max, negative values = quieter                                      |
| **DSP**      | Digital Signal Processing       | The field of processing audio as numbers — everything the audio engine does                             |
| **HP**       | High-Pass (filter)              | Lets high frequencies through, removes lows                                                             |
| **Hz**       | Hertz                           | Frequency unit. 440 Hz = the note A4. Higher Hz = higher pitch                                          |
| **kHz**      | Kilohertz                       | 1000 Hz. Filter cutoffs and sample rates are often in kHz                                               |
| **LFO**      | Low Frequency Oscillator        | A slow wave (usually below 20 Hz) used to animate parameters — not heard directly, but felt as movement |
| **LP**       | Low-Pass (filter)               | Lets low frequencies through, removes highs                                                             |
| **VCA**      | Voltage-Controlled Amplifier    | A volume knob controlled by a signal (like an ADSR envelope). In Klang, this is the "contouring" stage  |

---

## Klang-Specific Terms

| Term          | What It Is                                                                                             |
|---------------|--------------------------------------------------------------------------------------------------------|
| **Cylinder**  | An independent effect bus — one engine running its own signal chain. Up to 16 can run in parallel      |
| **Ignitor**   | The sound source — the thing that creates the initial waveform (oscillator, sample, or custom exciter) |
| **Katalyst**  | Post-processing that refines the sound after the Ignitor creates it                                    |
| **Injection** | The preparation stage — parameters and modulation sources fed into the Cylinder before ignition        |
| **Fusion**    | Where multiple Cylinders are mixed together into the final output                                      |
| **Orbit**     | Where a pattern meets a signal chain — the bridge between the event domain and the audio domain        |
| **Sprudel**   | Klang's pattern language for describing musical sequences                                              |

---

## The Two-Domain Mental Model

```
    PATTERN (event domain)              AUDIO (signal domain)
    What happens WHEN                   What it SOUNDS LIKE
    ─────────────────────               ──────────────────────
    sequencing                          tuning
    cycling                             degrading
    arranging                           carving
    pacing                              contouring
    gating                              saturating
    mapping                             animating
    layering                            mixing
    spawning                            spatializing
```

> "In Klang, there are two kinds of things happening:
> **what the sound sounds like** and **when and how it plays**.
> We keep these separate because it helps you think clearly about what you're changing."

---

## Open Questions

- Should Motor-specific terms (Cylinder, Ignitor, etc.) appear inline in the domain maps
  or only in their own section?
- How should this vocabulary map to SDK method names? (e.g. `carve()`, `degrade()`, `contour()`)
- Which terms need interactive audio examples in tutorials?
- Should the glossary page be searchable / filterable, or purely browsable by domain?
