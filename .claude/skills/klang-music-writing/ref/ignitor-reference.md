# Klang Instrument Design Reference (Ignitor DSL)

> Paste this into any LLM to design custom instruments with the Ignitor builder.
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

let myPluck = Ignitor.saw()
    .lowpass(Ignitor.constant(2000).plus(Ignitor.constant(3000).adsr(0.001, 0.3, 0.0, 0.1)))
    .adsr(0.005, 0.3, 0.0, 0.05)
    .classic()

note("c3 e3 g3 c4").sound(myPluck).adsrOff().gain(0.5)
```

> ⚠️ **End every instrument in `.classic()`, and mind `.adsrOff()`.** `.classic()` (the last call) gives the
> instrument the pattern's voice doors (`.lpf(...)`, `.adsr(...)`, `.crush(...)`) and ONE amplitude envelope.
> An instrument that does not END in it gets the pattern's voice doors only where its own tree reads the slots
> (phase 3 step 8). When the instrument carries
> its own amplitude `.adsr(...)`, add `.adsrOff()` on the pattern: it switches `classic()`'s envelope off, so the
> two do not multiply (twice the dB slope, the note dying faster and quieter than the numbers say), and
> the voice ends on the instrument's own envelope when its `.adsr(...)` (with a fixed release) is the last thing
> built before `.classic()`; anything built after it, a stage of the instrument's own or a filter or other stage the
> pattern writes, hands the note-off to a short teardown fade. Either way the note ends cleanly. Leave it off (keep `classic()`'s envelope) when the instrument's
> `.adsr(...)` only modulates something, e.g. a filter cutoff.
>
> The examples below all follow this rule.

### Lush pad

```javascript
let pad = Ignitor.supersaw()
    .analog(0.3)
    .lowpass(Ignitor.sine(0.3).plus(1).times(1000).plus(1500))
    .adsr(0.3, 0.5, 0.8, 1.5)
    .classic()

chord("<Am C F G>").voicing().sound(pad).adsrOff().gain(0.2).reverb(wet = 0.3, size = 6)
```

### FM bell

```javascript
let bell = Ignitor.sine()
    .fm(Ignitor.sine(), 2.3, 400)
    .adsr(0.001, 1.5, 0.0, 0.5)
    .classic()

note("c5 e5 g5 c6").sound(bell).adsrOff().gain(0.3).reverb(wet = 0.2, size = 4)
```

---

## The Ignitor Builder API

Instruments are built by composing `Ignitor` nodes into signal graphs, then registering them:

```javascript
let name = ignitorGraph
// Use in patterns:
note("c3 e3 g3").sound(name)
// or:
note("c3 e3 g3").sound("name")
```

### Oscillator Primitives

All accept optional `freq` param. Omit for voice note frequency, pass Hz for fixed frequency (e.g. LFO).

| Method                | Description                              |
|-----------------------|------------------------------------------|
| `Ignitor.sine(freq?)`     | Pure sine wave; its builder adds partial banks (below) |
| `Ignitor.saw(freq?)`      | Sawtooth, soft analog flyback            |
| `Ignitor.square(freq?)`   | Square wave, anti-aliased                |
| `Ignitor.tri(freq?)`      | Triangle wave                            |
| `Ignitor.ramp(freq?)`     | Reverse sawtooth                         |
| `Ignitor.zawtooth(freq?)` | Naive sawtooth (brighter, no anti-alias) |
| `Ignitor.impulse(freq?)`  | Single-sample impulse per cycle          |
| `Ignitor.pulze(freq?)`    | Variable duty-cycle pulse; one `duty` modulator may be shared by pulses at any pitches unless it reads `Ignitor.freq()` anywhere (see `phase(x)` below for the rule) |

### Super Oscillators (Unison/Detuned)

Multiple detuned copies for thick, lush sounds.

| Method                                     | Description             |
|--------------------------------------------|-------------------------|
| `Ignitor.supersaw(freq?, configure?)`    | Detuned sawtooth chorus |
| `Ignitor.supersine(freq?, configure?)`   | Detuned sine chorus     |
| `Ignitor.supersquare(freq?, configure?)` | Detuned square chorus   |
| `Ignitor.supertri(freq?, configure?)`    | Detuned triangle chorus |
| `Ignitor.superramp(freq?, configure?)`   | Detuned ramp chorus     |

**Every oscillator door is `Ignitor.name(freq?, configure?)`.** `freq` first (omit for the note's pitch, Hz for a
fixed frequency, `Ignitor.sine(5)` is an LFO), then a `configure` lambda that receives the oscillator's BUILDER
and returns it. The builder carries exactly that oscillator's knobs; processing (`.lowpass()`, `.adsr()`,
`.mul()`, ...) goes on the returned sound, OUTSIDE the lambda:

```javascript
Ignitor.supersaw(x => x.voices(9).spread(0.1).analog(0.2)).lowpass(800).adsr(0.01, 0.3, 0.5, 0.5)
```

**`phase(x)`: where in its cycle an oscillator runs** (every periodic oscillator's builder: `sine`, `saw`, `ramp`,
`square`, `pulze`, `tri`, `zawtooth`, `zamp`, `impulse` and the five super oscillators; not the plucks, not the
noises). A fraction of one cycle added to the phase every sample, default 0: 0.5 is half a cycle on, and it wraps
with no clamp (1.25 is 0.25, -0.25 is 0.75). A number is the start phase; a signal moves the phase while the note
plays, which is phase modulation (a jump clicks). Phase 0 is where each shape always started: the sine at `sin(0)`,
rising; the saw and zawtooth at -1, the bottom of the rise; the ramp and zamp at +1, the top of the fall; the square
at -1, the foot of its rising edge; the raw pulze at +1, the start of its high plateau (its instant edge sits at the
wrap); the triangle at -1, its lowest point; the impulse on its spike. On a super oscillator it shifts the whole
stack (every voice by the same fraction of its own cycle, the spread of start phases kept); since the voices draw
new random start phases on every note, a constant there is not audible, a moving phase is. On a sine with partial
banks every partial moves by the same fraction of its own cycle (0.5 inverts the wave, 0.25 starts every partial on
its peak), and a partial that joins mid-note at a phase other than 0 or 0.5 enters with a step. A fast-moving phase
also squeezes the soft edges of the saw and square family, which then alias like their raw twins. One phase LFO
may be shared by layers at different pitches (`let wob = Ignitor.sine(30).mul(0.3)` in two layers is one LFO). The
rule: a shared modulator that reads `Ignitor.freq()` anywhere (its rate, its depth, a scaling next to it, as in
`wob.mul(Ignitor.freq().recip().mul(220))`) renders once per pitch, so every LFO only in it runs too fast; build it once per
layer (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`).

```javascript
Ignitor.sine(4, x => x.phase(0.25))                      // an LFO that starts at its peak
// two layers on opposite tremolos: the saw is loud while the square is quiet, and back
Ignitor.saw().mul(Ignitor.sine(4).range(0.5, 1)).plus(Ignitor.square().mul(Ignitor.sine(4, x => x.phase(0.5)).range(0.5, 1)))
Ignitor.sine(x => x.phase(Ignitor.sine(5).mul(0.2)))     // phase modulation by a 5 Hz LFO: a vibrato of about 6 Hz either way
```

**Sine partial banks** (`Ignitor.sine` builder knobs; `docs/plans/sine-partial-banks.md`). The sine can carry banks
of sine partials at multiples of ITS OWN frequency, rendered in one pass: `harmonics(count, rolloff = 1)` adds
`count` partials at `2f, 3f, 4f ...`, `octaves(count, rolloff = 1)` at `2f, 4f, 8f ...`, `suboctaves(count,
rolloff = 1)` at `f/2, f/4 ...`. An added partial at `m * f` or at `f / m` has gain `m ^ -rolloff` of its bank:
rolloff 1 is the sawtooth law, 2 is triangle-soft, 0 is flat. `fundamental(gain)`
levels the sine itself (0 = overtones only). `analogSpread(0..1)` sets whether the partials drift as one
oscillator (0) or each on its own lane (1, default) under `analog`; it is the same knob the super
oscillators carry, over partials instead of voices. Every knob is a signal read once per block.
Partials at or above Nyquist stay silent; there is no lower limit. Multiples follow the door's `freq`, so
`Ignitor.sine(Ignitor.freq().mul(2), x => x.harmonics(3))` is the even series `2f, 4f, 6f, 8f`. A sub-octave at a
comparable level moves the perceived pitch down an octave (the missing-fundamental effect), which is the point of
a sub oscillator. Without bank knobs `Ignitor.sine` is the plain sine.

```javascript
Ignitor.sine(x => x.harmonics(7))                          // bass: f plus 2f .. 8f, the ear rebuilds 41 Hz on a phone speaker
Ignitor.sine(x => x.harmonics(7).fundamental(0)).mul(0.5)  // the overtones only, on their own fader next to a sub sine
Ignitor.sine(x => x.suboctaves(1, 0))                      // the classic sub oscillator: f and f/2 at equal level
Ignitor.sine(Ignitor.freq().mul(2), x => x.octaves(5)).mul(1/2) // 2f .. 64f at 1/2 .. 1/64: an octave stack over a saw
Ignitor.sine(x => x.harmonics(12, Ignitor.param("rolloff", 1))) // brightness from the pattern
```

**Builder knobs of the super oscillators** (each returns the builder): `voices(x)` (default 8), `spread(x)`
(default 0.2), `analog(x)` (per-voice pitch drift), `analogSpread(0..1)` (how much the voices drift AGAINST each
other under `analog`: 1, the default, is a lane per voice, the organic unison of separate oscillators; 0 is one
shared walk, so the stack wobbles as a single oscillator and its unison detune stays static),
`spreadPower(x)`, `sideAtten(x)`, `gainJitter(x)`,
`centerJitter(x)`, and the combined phase-pool call
`phasePool(on, kMin, kMax, drawTries, poolSize, refreshEvery, selection, warmup)`, banded start-phase
selection for consistent low-note fundamentals, off by default. Every param is an optional literal,
so named-arg subsets work: `.phasePool()` = on with family defaults,
`.phasePool(kMin = 0.05, kMax = 0.25)` = the hollow-pad band (the band is a timbre control),
`.phasePool(refreshEvery = 0)` = frozen vocabulary. All-named or all-positional — KlangScript
forbids mixing. Defaults: band 0.30–0.55 (saw family) / 0.40–0.65 (supertri) / 0.50–0.80
(supersine), drawTries 5/16/40, poolSize 256 (cap 1024), refreshEvery 10, selection "normal[:width[:outliers]]" (default: median-centered serving over the pool vocabulary; width 0 = always the median take, 0.1 tight, 0.5 default, 1.5 ≈ random-with-center-edge; outliers 0..1 = chance of an EXTREME take at the vocabulary edge — directly at kMin/kMax when the band is reachable, "normal:0.1:0.05" = tight + 5% wild plucks; "random"; "roundrobin" opt-in — cycling can gargle), warmup 16 (eagerly seeded entries; 0 = fully lazy).
⚠️ Enabling the pool lifts the low-note fundamental (+2 dB measured on average — more on the
notes the old random draw was cancelling) — on a finished song, retrim the low end once after
switching it on.

```javascript
// Configure only what you want, inside the lambda:

// Thin 3-voice supersaw
Ignitor.supersaw(x => x.voices(3).spread(0.1))

// Wide 12-voice pad with analog drift, then processing outside the lambda
Ignitor.supersaw(x => x.voices(12).spread(0.3).analog(0.2)).lowpass(2000)

// Fixed frequency goes first: a 55 Hz drone
Ignitor.supersaw(55, x => x.voices(7))

// There is NO .voices()/.analog() on the sound itself any more; they are builder knobs (the pattern-level unison(voices, spread, pan) is a different door).
```

### Noise Sources

| Method                                      | Description                                                     |
|---------------------------------------------|-----------------------------------------------------------------|
| `Ignitor.whitenoise(color?)`                    | Flat spectrum; `color` tilts it (see below)                     |
| `Ignitor.brownnoise(leak?)`                     | Low-frequency weighted (-6 dB/oct); `leak` = white-leak         |
| `Ignitor.pinknoise()`                           | Balanced noise (-3 dB/oct) — canonical exact pink, no knobs     |
| `Ignitor.perlin(rate?, octaves?, persistence?)` | Smooth organic noise; fBm via `octaves`/`persistence`           |
| `Ignitor.berlin(rate?, octaves?, persistence?)` | Angular piecewise-linear noise; same fBm knobs                  |
| `Ignitor.dust(density?, tail?, bipolar?)`       | Sparse random impulses (default density 0.2)                    |
| `Ignitor.crackle(chaos?)`                       | Chaotic crackle (bipolar pops); `chaos` ≈1.0 sparse … 2.0 dense |

**Noise knobs** (all default to today's behavior — a bare call is unchanged):

| Knob          | On                | Meaning                                                                                    |
|---------------|-------------------|--------------------------------------------------------------------------------------------|
| `color`       | `whitenoise`      | Spectral tilt −1..1: `0` flat (default), `<0` darken toward pink/brown, `>0` brighten      |
| `leak`        | `brownnoise`      | Per-sample white-leak (default 0.02): lower = deeper/slower brown, higher = brighter       |
| `octaves`     | `perlin`/`berlin` | fBm octaves: `1` plain (default, perf-neutral), higher = more fractal detail (capped at 8) |
| `persistence` | `perlin`/`berlin` | fBm amplitude falloff per octave (default 0.5; lower = quieter upper octaves)              |
| `tail`        | `dust`            | Heavy-tailed amplitude exponent: `1` uniform (default), `>1` = mostly-tiny / rare-loud     |
| `bipolar`     | `dust`            | `>0.5` = random ±sign pops; default `0` = unipolar                                         |
| `chaos`       | `crackle`         | Drives the chaotic map: ~1.0 sparse, 1.5 = clear crackle (default), ~2.0 dense/noisy       |

> ⚠ `Ignitor.crackle()` is a **chaotic generator** now (SuperCollider's Crackle map → bipolar pops), no longer a
> dust alias. For the old sparse-impulse behavior, use `Ignitor.dust()`.

### Physical Models

| Method                                    | Description                     |
|-------------------------------------------|---------------------------------|
| `Ignitor.pluck(freq?, configure?)`      | Karplus-Strong plucked string; knobs `feedback` (0.996, the loop feedback per pass), `brightness` (0.5), `pickPosition` (0.5), `stiffness` (0), `analog` |
| `Ignitor.superpluck(freq?, configure?)` | Unison plucked strings; adds `voices` (8), `spread` (0.2) and `analogSpread` (1): `Ignitor.superpluck(x => x.voices(6).feedback(0.995))` |

### Utility

| Method                            | Description                                     |
|-----------------------------------|-------------------------------------------------|
| `Ignitor.freq()`                      | Voice note frequency (use as param source)      |
| `Ignitor.param(name, default, desc?)` | Named parameter slot (overridable at play time) |
| `Ignitor.constant(value)`             | Fixed value (not overridable)                   |
| `Ignitor.silence()`                   | Zero output                                     |

### Dispatch / Selection

| Method                    | Description                                                            |
|---------------------------|------------------------------------------------------------------------|
| `Ignitor.variants(a, b, ...)` | Bundles multiple ignitors into one. Per-event index picks which child. |

`Ignitor.variants(...)` lets a single sound expose several flavours of itself,
selected per note via the `soundIndex` field. Same dispatch mechanism that
picks sample-bank variants (`bd:0` / `bd:1`), now applied to ignitor graphs.

Index sources, in order of precedence:

- `:n` suffix on a note name — `note("a:1 b:2")` or `s("bd:1")`
- `:variant` suffix on a scale step — `seq("0 1 2:1").scale("c:minor")`
- `.n("0 1 0 1")` pattern alongside `note(...)`

Indices wrap with floor-mod semantics: `children[index.mod(N)]`. Negative
indices wrap from the end, overflow wraps to zero. Missing `soundIndex`
defaults to child 0.

```javascript
// Open vs. palm-muted guitar — same scale, two timbres
let open  = Ignitor.saw().lowpass(2200).adsr(0.005, 0.3, 0.4, 0.4)
let muted = Ignitor.saw().lowpass(1800).distort(0.7, "tube", 4)
                     .adsr(0.002, 0.08, 0.0, 0.04)
let guitar = Ignitor.variants(open, muted).classic()

// Inline: c4 and d4 ring out, e4 and f4 chug
seq("0 1 2:1 3:1").scale("c4:major").sound(guitar).adsrOff().gain(0.3)
```

**Composition tips:**

- `Ignitor.variants(a, b).lowpass(400)` wraps both variants in a shared filter —
  whichever variant is picked flows through the same downstream chain. Build
  the dispatch first, then attach shared post-processing.
- Nested `Ignitor.variants(...)` all dispatch on the *same* `soundIndex` —
  letting one index drive correlated changes deep in the tree.
- A child may be a plain number, a constant: `Ignitor.sine(Ignitor.variants(400, 1200))`
  plays 400 Hz on `a` and 1200 Hz on `a:1`. Anything else (a string, a lambda) is a
  script error at the call.

---

## Processing Chain (Extension Methods)

Chain methods onto any `Ignitor` node. All numeric params accept either a number or another `Ignitor` node for audio-rate
modulation.

### Filters

| Method                      | Description                         |
|-----------------------------|-------------------------------------|
| `.lowpass(freq, q?, x => ...)`  | Resonant lowpass (default q=0.707); builder: `passes`, `analog`, `humanize`, `env`, `adsr`                |
| `.highpass(freq, q?, x => ...)` | Resonant highpass, the same builder                                                                   |
| `.onepole(freq)`                | Gentle one-pole lowpass (-6 dB/oct)                                                                   |
| `.bandpass(freq, q?, x => ...)` | Bandpass filter; builder as lowpass without `passes`                                                  |
| `.notch(freq, q?, x => ...)`    | Band-reject (notch) filter, the same builder as bandpass                                              |

The door carries the filter's musical inputs, `freq` and `q`; every secondary knob sits on the
builder the lambda receives: `.lowpass(800, 1.8, x => x.passes(2).analog(3))`. The lambda floats
past an omitted q: `.lowpass(800, x => x.analog(3))`.

- `passes(n)` is the cascade count: `2` = 24 dB/oct, `3` = 36, coerced to 1..16. At the default q
  the cascade stays -3 dB AT the cutoff; a resonant q compounds across stages, and so does
  `analog` (every stage gets the full drive).
- `env(semitones)` and `adsr(attack, decay, sustain, release)` are the cutoff
  envelope: naming EITHER switches it on and the other fills from the shared constants (depth 7
  semitones; stages 0.01 / 0.1 / 1.0 / 0.1). `.lowpass(800, x => x.env(24).adsr(0.005, 0.3, 0.2, 0.2))`
  is a pluck. The `adsr` takes its own lambda to shape the stages with the chain's curves,
  `x => x.env(24).adsr(0.01, 0.3, 0.2, 0.5, e => e.curves("lin", "exp", "exp"))`; unshaped stages
  are exponential (`"exp"`, the house curve every envelope defaults to since 2026-09-25).
- `humanize()` gives each note its own cutoff tolerance and a slow drift, scaled by `analog`.

### Equalizer

All sections of one `.eq()` run in one pass instead of one node each, which removes the scratch
buffer, the extra buffer read/write traffic and the virtual call for every section after the
first. (The per-sample filter loop itself stays: the core runs one loop per section by design.) The saving grows with the
section count and is much larger in the browser and on weak hardware than on desktop JVM, where
it is small. Measure your own patch rather than assuming a rate.

You write `band()` and `tap()` sections yourself inside the `.eq(e => e...)` lambda, and plain
`.lowpass()/.highpass()/.bandpass()/.notch()` written after it are folded into the same pass
automatically, so there is no need to rewrite them as bands.

**Only NEIGHBOURING filters merge, and nothing is ever reordered.** Anything else between two
filters is a wall: `.distort()`, `.drive()`, `.shape()`, `.crush()`, `.mul()`, `.shimmer()`,
`.tremolo()`, `.vibrato()` and friends. So `.lowpass(5000).distort(0.4).lowpass(3000)` is two
passes, not one. Moving the distort to the end of the chain would make it one, though that is a
different patch and a different sound, so make that choice by ear rather than for the saving.

Two kinds of filter are never folded at all: `.onepole()`, and any filter with a
non-zero or Ignitor-slot `analog`. On `.lowpass()/.highpass()` that analog switches on a saturating
character the fused EQ does not reproduce; on `.bandpass()/.notch()` it produces no sound of its
own, and the filter stays out of the fusion for a subtler reason: reading the value each block is
itself observable when it is an expression.

A filter whose input is shared with another chain still folds, into its own pass; sharing only
stops two chains merging into ONE pass, because that would compute the shared part twice.

Fusing is meant to be inaudible. To check by ear, put `.optimizer(0)` on the sound to render it
exactly as written, and compare.

| Method                  | Description                                                                                        |
|-------------------------|----------------------------------------------------------------------------------------------------|
| `.eq(e => e...)`        | Opens the EQ and configures its sections in the lambda; `band`/`tap` exist only on that builder    |
| `.optimizer(0)`         | Renders a sound exactly as written, with no filter fusion; for A/B-ing the fusion by ear           |
| `band(freq, q?, db?)`   | **Serial** peaking band: `db` dB gain at `freq`, `q` = width (defaults q=0.707, db=0)             |
| `tap(freq, q?, gain?)`  | **Parallel** boost: bandpasses the EQ INPUT and mixes it back in (defaults q=0.707, gain=1.0)        |

**The difference matters and it is audible.** `.band()` sections apply one after another, so
they compound: two overlapping +6 dB bands give about +12 dB where they overlap, like any DAW EQ.
`.tap()` sections all read the sound going INTO the eq and mix back onto it, so they add rather
than compound. Converting a parallel tap bank into serial bands measured **+4.5 dB too hot around
1200 Hz** on a real guitar patch (figure measured pre-C2; re-measure under the unity-peak taps).

```javascript
// EQ bands: shaping a sound, gains in dB
Ignitor.saw().eq(e => e.band(3500, 0.7, 6).band(300, 1.0, -4))      // presence lift, mud cut

// Parallel boosts: the classic guitar mids + presence lift, gains are plain multipliers
Ignitor.saw().eq(e => e.tap(850, 0.707, 1.7).tap(2500, 0.7, 5.0))
```

Use `tap()` when you are stacking resonant boosts onto a sound, `band()` when you are shaping
with EQ bands. They mix freely in one `.eq(e => e...)` lambda, in written order.

⚠ `band(1200, 6)` sets **q**, not gain: the second positional arg is `q` and db stays 0, which
is silent. Write `band(freq = 1200, db = 6)` when you mean gain. KlangScript forbids MIXING
positional and named arguments, so name them all or pass all three positionally.

⚠ `.eq(...)` directly on an eq continues it. An `.eq(...)` written *after* other filters opens a
SECOND eq, so the one-pass saving applies per eq, not across the whole line.

⚠ Both `q` values are the ordinary width scale: `.band(f, 0.707)` and `.bandpass(f, 0.707)` span
the same 1.90 octaves. What differs is CONVERSION. A tap keeps its numbers verbatim
(`signal.add(signal.bandpass(f, Q).mul(g))` becomes `.tap(f, Q, g)`), but rewriting that tap as a
`.band()` needs a WIDER setting, because a tap's audible bump is wider than the bandpass inside
it: use `db = 20*log10(1 + g)` and `q = Q / sqrt(1 + g)` (since C2 the tap is unity-peak, so
`q` is out of the level equation). Example: `tap(850, 0.707, 1.7)` becomes
`band(850, 0.430, 8.63)`.

⚠ Everything is control-rate (read once per block). For `.band()` that includes `db`, which
moves filter coefficients, so an LFO on `db` zippers exactly like an LFO on a cutoff; use a VCA
(`.mul(...)`) for a smooth gain ride. For `.tap()` the same applies to `gain`, which is a mix
multiplier rather than a coefficient: a moving `gain` steps per block, whereas the chained
`signal.add(signal.bandpass(...).mul(lfo))` is smooth per sample. Keep tap gains constant or
Ignitor-slot driven.

Since C2 of the filter unification the engine bandpass is **unity-peak**, so on a `.tap()` `q`
is a pure WIDTH control: the boost at `freq` is `1 + gain` for ANY `q`. Tighten a tap by raising
`q`; the level stays put, and `gain` alone decides how loud the band comes back. `.tap(freq)` at
its defaults is still a **+6 dB lift** (`1 + 1 = 2`) while `.band(freq)` at its defaults is
transparent.

⚠ `band`/`tap` exist only inside the `.eq(e => e...)` lambda; `.eq()` returns the plain sound, so
`.eq().band(...)` is an error. To add bands after a chained filter, open a new `.eq(e => e...)`.

⚠ Order matters when mixing them: a `.band()` earlier in the list cannot shape a later `.tap()`,
because a tap always reads the sound entering the eq.
`.band(freq = 3000, db = -12).tap(3000, 1.0, 5.0)` re-injects the 3 kHz the band just removed.
Put taps first unless you want that.

### Envelope

| Method                                             | Description                                                    |
|----------------------------------------------------|----------------------------------------------------------------|
| `.adsr(attack, decay, sustain, release, e => ...)` | ADSR amplitude envelope (all in seconds, sustain is 0-1 level) |

The lambda is optional and receives the envelope's builder: `curves(attack, decay, release)` shapes
the stages (`"exp"`, the default, `"linear"`, `"square"`, `"cube"`, `"scurve"`, `"invsquare"`, with
their short names) and `declick(seconds)` rounds the gain's corners (0 = off, the default; about
0.0005 is gentle): `.adsr(0.005, 1.0, 0.0, 0.03, e => e.curves("linear", "linear", "linear"))`.

### Effects

| Method                                  | Description                                |
|-----------------------------------------|--------------------------------------------|
| `.drive(amount)`                        | Pre-amplification: gain, no curve          |
| `.shape(shape?, oversample?)`           | The waveshaper curve alone, no gain        |
| `.distort(amount, shape?, oversample?)` | `.drive()` + `.shape()` in one node        |
| `.crush(bits)`                          | Bit-depth reduction                        |
| `.coarse(factor)`                       | Sample-rate reduction                      |
| `.phaser(wet, rate, center?, sweep?, x => x.floor(f))` | Allpass phaser: wet FIRST, wet and rate required, center/sweep default 1000; the dry floor (default 0) is the builder knob |
| `.shimmer(wet?, feedback?, tone?, pitches?, x => x.floor(f))` | Granular pitch-shift cloud: wet 0.5, feedback 0.5, tone 4000, pitches `[0, 7, 12]`; dry floor on the builder |
| `.tremolo(rate, depth, x => x.shape(name).range(from, to))` | Amplitude LFO: rate in Hz, depth 0 to 1; the builder sets the LFO shape (`"sine"` default, `"triangle"`, `"square"`, `"sawtooth"`, `"ramp"`), which is the oscillator of that name; the square, sawtooth and ramp get a 16 ms soft edge. `range(from, to)` places the swing in the -1..1 language of `range`, the gain being `1 + depth * that`: default `range(-1, 0)`, the dip from 1 to `1 - depth`; `range(0, 1)` swells upward to `1 + depth`, `range(-1, 1)` both ways; raw, no clamp |

`.drive()`, `.shape()` and `.distort()` are one family: `drive` is gain with no curve,
`shape` is the curve with no gain, and `distort(amount, shape)` is exactly `drive(amount).shape(shape)`.
Reach for the pair instead of the bundle only when something must sit BETWEEN them, e.g.
`.drive(3).lowpass(800, 1.0, x => x.analog(3)).shape("tube")`: drive into a saturating filter, then shape.
Sprudel has only `distort()`; its voice model cannot express a node between the two.

Distort / shape curves: `"soft"` (tanh, default), `"hard"`, `"gentle"`, `"cubic"`, `"diode"`, `"fold"`, `"chebyshev"`,
`"rectify"`, `"exp"`, `"softsat"`, `"tube"`, `"linearfold"`, `"zerosquare"`, `"sineshaper"`, `"asym"`, `"stompbox"`.
An unknown name is `"soft"`. The node carries the curve as its index in that list, so the door also takes the
number, or an `Ignitor.param(...)` slot carrying it (read once per note).

Oversample factor (on `.distort` / `.shape`): user-facing factor, floored to power of 2. `0` or `1` = off,
`2` = 2x, `4` = 4x, `8` = 8x. Suppresses aliasing for heavy / bright distortion (e.g. `"exp"`, `"fold"`,
`"hard"`). Example: `Ignitor.saw().distort(0.8, "exp", 4)`. Read once per note; a number or an `Ignitor.param(...)` slot.

Tremolo shapes are read once per note, the rate every block, a fixed depth every block and a moving depth
every sample (a moving depth at or below 0 leaves the signal unchanged). A `"square"` at depth 1 is silence for about
half of each cycle, on purpose; a voice with a tremolo is not cut short by the silence culler unless you set `cull(...)`.

### FM Synthesis

| Method                         | Description                                                        |
|--------------------------------|--------------------------------------------------------------------|
| `.fm(modulator, ratio, depth, x => x.adsr(a, d, s, r)?)` | FM synthesis with modulator, frequency ratio, and modulation depth; the builder's `adsr` is the modulation-index envelope |

The modulator is another `Ignitor` node. `ratio` sets the modulator frequency relative to the carrier. `depth` is the
modulation amount in Hz. Without the lambda the depth is constant; `x => x.adsr(0.001, 0.5, 0, 0.05)` is the decaying
bell of the built-in `sgbell`.

A pitch node means what it wraps (since 2026-10-09):

- above the fm it moves the whole operator, the note's pitch, and the timbre stays put:
  `Ignitor.sine().fm(Ignitor.sine(), 3.5, 400).vibrato(6, 0.5)`; so do sprudel's `vib`, `penv` and `accelerate` on an
  fm instrument that ends in `.classic()`, and an outer `.fm(...)` (two fms in a row: the inner modulator follows the
  outer one; sprudel's `fm` over an fm instrument is that shape, `s("sgbell").fm(...)`); a modulator with an absolute frequency (`Ignitor.sine(330)`) stays at it;
- on the modulator it moves the modulator alone: `Ignitor.sine().fm(Ignitor.sine().vibrato(6, 0.5), 3.5, 400)`;
- on the carrier it moves the carrier alone, so the ratio wobbles: `Ignitor.sine().vibrato(6, 0.5).fm(Ignitor.sine(), 3.5, 400)`.

One fm serves one pitch: over a detuned stack (`(x + x.detune(7)).fm(m, ...)`) give each layer its own fm, inside its
detune, and sum them: `x.fm(m1, ...) + x.fm(m2, ...).detune(7)`.

### Pitch Modulation

| Method                                              | Description                                |
|-----------------------------------------------------|--------------------------------------------|
| `.detune(semitones)`                                | Shift pitch by semitones                   |
| `.octaveUp()`                                       | +12 semitones                              |
| `.octaveDown()`                                     | -12 semitones                              |
| `.vibrato(rate, semitones)`                         | Sinusoidal pitch LFO; the depth may be a signal (followed sample by sample), at or below 0 no vibrato |
| `.accelerate(semitones)`                            | Exponential pitch glide from the onset to the gate close, held through the release (12 = one octave) |
| `.pitchEnvelope(semitones, x => x.adsr(a, d, s, r))` | Pitch sweep envelope (SEMITONES at peak); the `adsr`'s own lambda shapes it with `curves` |
| `.pitchModSemitones(mod)`                           | Pitch by any signal or number, in SEMITONES: `2^(mod / 12)` (12 = an octave up, 0 = the note) |
| `.pitchMod(mod)`                                    | Pitch by any signal, LINEAR: `1 + mod` (1.0 = an octave up, -1.0 stops the oscillator; FM's law) |

`pitchModSemitones` is the vibrato's law for any signal: `Ignitor.saw().pitchModSemitones(Ignitor.sine(5).mul(0.5))`
wobbles half a semitone either way at 5 Hz. `pitchMod` is linear, so a symmetric swing bends further down than up
(a swing of 0.5 is 7 semitones up and 12 down); reach for it to write FM by hand.

`pitchEnvelope` is an ADSR on the pitch, the chain `adsr`'s pattern: up to `semitones` over the attack, down to the
sustain (a share of `semitones`, usually 0 = the note) over the decay, and from the gate's end back to the note over
the release. `x => x.adsr(0.001, 0.04, 0, 0)` is a kick's sweep. Without the lambda: `adsr(0.01, 0.1, 0, 0)`. Its
stages are exponential unless its `adsr` shapes them (since 2026-09-25; the drop reaches the note sooner than a linear
one): `x => x.adsr(0.001, 0.04, 0, 0, e => e.curves("linear", "linear", "linear"))` gives a straight, slower sweep.

A pitch mod over a stack (`Ignitor.sine().plus(Ignitor.tri()).vibrato(5, 0.1)`) is ONE modulator for the whole stack:
every pitched layer bends with the same LFO, at the written rate. One exception: a layer detuned with `.detune` under
an `fm`, or under a pitch mod whose knobs read `Ignitor.freq()`, renders the whole mod again, LFO and modulator
included; give such a layer its own mod. Until 2026-10-07 a pitch mod ran once per pitched layer, so a vibrato over
three layers was heard at three times its rate; a patch tuned before then may sound slower now
(`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`, section B-1).

### Analog Drift

| Knob                                   | Description                                                                                                      |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `x => x.analog(n)` (oscillator builders) | How analog: one unitless character scale, 0 ideal, 1 to 8 usual, 10 strong. An oscillator's tell is its pitch drift, about one cent of peak per unit (a fast jitter and a slow wander) |

### Arithmetic (Signal Mixing)

| Method          | Description                        |
|-----------------|------------------------------------|
| `.plus(other)`  | Add two signals (layering)         |
| `.minus(other)` | Subtract signal                    |
| `.times(other)` | Multiply signals (ring modulation) |
| `.mul(factor)`  | Scale amplitude                    |
| `.div(divisor)` | Divide amplitude                   |
| `.range(from, to)` | Let a `-1..1` signal swing between `from` and `to` (the LFO scaler) |
| `.rangex(from, to)` | The same, exponentially: equal steps are equal ratios (for frequencies) |

Where the swing sits is up to the two values of `range`, the same word, parameter names and result as sprudel's `range(from, to)`
(sprudel's signals start from `0..1`, the oscillators from `-1..1`; `x.range(200, 400)` lands on 200..400 on both):

| Call             | The signal moves                         |
|------------------|------------------------------------------|
| `range(0, 1)`    | only upward, between 0 and 1             |
| `range(-1, 0)`   | only downward, between -1 and 0          |
| `range(-1, 1)`   | both ways, centred on 0 (the oscillator) |
| `range(-0.5, 1)` | mostly upward, dipping a little below 0  |

From `0..1` to `-1..1` without a range: `x.mul(2).minus(1)`.

`rangex(from, to)` is the exponential twin, the same as sprudel's `rangex`: `-1` gives `from`, `0` the geometric
mean `sqrt(from * to)`, `1` gives `to`, so each octave of a frequency sweep takes the same share of the swing (with
a saw or a triangle, the same time; a sine lingers at its ends). Values at or below 0 are coerced to 0.0001.

```javascript
// The cutoff sweeps four octaves (200 to 3200 Hz) evenly, through 800 Hz in the middle
Ignitor.saw().lowpass(Ignitor.sine(0.2).rangex(200, 3200))
```

### Composition: `.through(...)`

`x.through(a, b, c)` runs the signal through functions of a signal, in the order written: it is exactly
`c(b(a(x)))`, the same node as the nested calls, with any number of stages (`through()` with none is `x` itself). A rig is
then a value, and a rig is a stage too:

```javascript
let pedal  = x => x.distort(0.4, "soft")
let cab    = x => x.highpass(100).lowpass(5000)
let rig    = x => x.through(pedal, cab)
let guitar = Ignitor.saw().through(rig).adsr(0.005, 0.8, 0.0, 0.05).classic()
```

Serial, one stage into the next. Do not confuse it with sprudel's `apply(f, g)`, an alias of `layer`, which runs
each function on the pattern and STACKS the results. The Katalyst builder has the same door:
`Katalyst(k => k.through(hall, ceiling))`.

---

## Parameter System

### `Ignitor.param(name, default)` — Overridable at play time

```javascript
let bass = Ignitor.saw()
    .lowpass(Ignitor.param("cutoff", 800, "filter cutoff"))
    .adsr(0.005, 0.2, 0.0, 0.05)
    .classic()

// Override param in pattern:
note("c2").sound(bass).adsrOff().ignp("cutoff", 1200)
```

The pattern setter is `ignitorParam(slot, value)`, `ignp` for short. `slot` is the slot's name or the param
object itself, so a param held in a variable needs no typed name. Only the NAME is written; the default stays the
one the instrument declared:

```javascript
let cutoff = Ignitor.param("cutoff", 800)
let bass = Ignitor.saw().lowpass(cutoff).adsr(0.005, 0.2, 0.0, 0.05).classic()

note("c2").sound(bass).adsrOff().ignp("cutoff", 1200)   // by name
note("c2").sound(bass).adsrOff().ignp(cutoff, 1200)     // by the param object

// a classic slot, by object: this is the slot lpf.freq, which the lpf door writes too
note("c3").sound(Ignitor.saw().classic()).ignp(Ignitor.slot.lpf.freq, 1200)
```

### `Ignitor.constant(value)` — Fixed, not overridable

Use when you want an exact locked value:

```javascript
Ignitor.saw().lowpass(Ignitor.constant(2000))  // always 2000 Hz, cannot be overridden
```

### `Ignitor.freq()` — Voice note frequency

Special node that outputs the voice's current note frequency:

```javascript
Ignitor.sine()  // freq defaults to Ignitor.freq() when omitted
Ignitor.sine(Ignitor.freq())  // equivalent to above
Ignitor.sine(5)  // fixed 5 Hz (for LFO use)
```

### `.classic()`: the pattern's voice doors on your instrument

`.classic()` wraps a sound in the classic synth voice: the FM, the pitch envelope, the accelerate and the vibrato (on the
source), onepole, crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo and the amplitude envelope, in that
order. The pattern's `fm`, `penv`, `accelerate` and `vib` reach only an instrument with `.classic()`. Make it the LAST call: an instrument whose
tree ends in `.classic()` is a whole voice that ends on its own envelope. `adsrOff()` switches that envelope off, and
the voice ends on the instrument's own envelope when its `.adsr(...)` (with a fixed release) is the last thing
built before `.classic()`; anything built after it, a stage of the instrument's own or a filter or other stage the
pattern writes, hands the note-off to a short teardown fade. Either way the note ends cleanly. A stage after it (`.classic().mul(0.5)`)
still gets the doors, but the root is no longer the envelope, so the voice adds its short teardown fade after the
tree and `endsInClassic()` is false for it. An `.optimizer(...)`
hint after it is fine; it is not a stage. Every stage is a slot the pattern's
doors fill, and a stage the note does not write is not built, so an untouched `.classic()` costs one
envelope (the voice defaults: `adsr(0.01, 0.1, 1.0, 0.05)`). No arguments.

```javascript
let guitar = Ignitor.saw().distort(0.4, "tube").classic()
// the pattern's doors reach classic()'s slots: .lpf(1800) writes lpf.freq, .adsr(release = 0.2) the envelope.
// Do NOT add .adsrOff() here: it switches classic()'s OWN envelope off (use it only when the instrument
// brings its own amplitude envelope, which then shapes the note instead).
note("c3 e3 g3").sound(guitar).lpf(1800).adsr(release = 0.2)
```

The slots it places are grouped per stage on `Ignitor.slot`, named after the sprudel
readers: `Ignitor.slot.lpf.freq`, `.q`, `.passes`, `.env`, `.attack`, `.decay`, `.sustain`, `.release` (the
same on `hpf`; `bpf` and `notch` without `passes`), `Ignitor.slot.crush.bits`, `Ignitor.slot.coarse.factor`,
`Ignitor.slot.distort.amount|shape|oversample`, `Ignitor.slot.tremolo.depth|rate|shape`,
`Ignitor.slot.adsr.attack|decay|sustain|release|on`, `Ignitor.slot.onepole`, `Ignitor.slot.adsrCurves.attack|decay|release`,
the vibrato `Ignitor.slot.vibrato.rate|semitones` (`semitones` is the switch, unset = off), the accelerate
`Ignitor.slot.accelerate` (flat, the switch, unset = off), the FM `Ignitor.slot.fm.ratio|depth|attack|decay|sustain|release`
(`depth` is the switch, unset = off), the pitch envelope
`Ignitor.slot.penv.semitones|attack|decay|sustain|release` (`semitones` is the switch, unset = off)
and its curves `Ignitor.slot.penvCurves.attack|decay|release`, and the filter envelope curves
`Ignitor.slot.lpfCurves|hpfCurves|bpfCurves|notchCurves.attack|decay|release` (unset = exponential).

Want another order? Write your own tail from the same slots, as far as a door takes them:
`Ignitor.saw().highpass(Ignitor.slot.hpf.freq, Ignitor.slot.hpf.q).crush(Ignitor.slot.crush.bits).lowpass(Ignitor.slot.lpf.freq)`.
The doors take a slot for every filter's `freq`, `q`, `env` and envelope stages, for `crush`, `coarse`,
the tremolo's knobs and the envelope's stages and curves. Three groups ONLY `.classic()` can place:
- `lpf.passes` / `hpf.passes`: the filter builder's `passes(n)` takes a number, not a slot;
- `adsr.on`: no door has the switch (on a door, not writing `adsr()` already means no envelope);
- `distort.*`: the `distort` door builds a drive into a shaper whose shaper always runs and caps its
  output, while `.classic()` uses the one distort node that switches drive AND shape off together and
  renders the retired voice strip's exact law (no cap, so a hot shape can go past 1.0; the drive inside the
  oversampler). A slot on the door's distort would shape every note, written or not.

**Every voice is its tree (phase 3 step 9, 2026-09-27).** The BUILT-IN sounds (`sound("saw")`) and the
SAMPLES are `.classic()` voices, and so is every authored instrument whose tree ENDS in `.classic()`: the pattern
doors (`.lpf(...)`, `.adsr(...)`, `.crush(...)`) reach their slots. An authored instrument WITHOUT `.classic()`
plays as its bare tree: the voice doors reach it only where its own tree reads the slots, it has no default envelope, and it ends on the short
teardown fade unless its own root envelope ends it. A door beats an `ignp` on the same slot (sprudel's
`toVoiceData()` writes the door's value last). If your instrument has a long own tail (a pad's release), write the
pattern's `adsr(release = ...)` to match it: `classic()`'s envelope releases over its own slot (0.05 s by default).

### Aliases and slot objects

`Ign` is a second name for `Ignitor`: the same object, every member (`Ign.sine()` is `Ignitor.sine()`,
`Ign.slot.lpf.freq` is `Ignitor.slot.lpf.freq`). `Kat` is the short name of `Katalyst` the same way (`Kat(k => ...)`,
`Kat.slot.reverb.wet`). The songs use `Ign` (and `ignp`) and spell `Katalyst` out; this reference spells the full names.

`Katalyst.slot.<stage>.<knob>` holds the knobs of the classic chain as objects, one group per stage (`body`, `vowel`,
`delay`, `reverb`, `phaser`, `compressor`, `gain`, `duck`), and `Katalyst.param(name, default)` makes a knob of your
own. `katalystParam(slot, value)`, `katp` for short, takes the name or the object, like `ignp`:

```javascript
let room = Katalyst.param("room", 2)
let bus = Katalyst(k => k.reverb(0.5, room))

note("c3 e3").s("saw").katalyst(bus).katp(room, "<2 9>")
note("c3 e3 g3").s("supersaw").reverb(wet = 0.4).katp(Katalyst.slot.reverb.size, "<2 8>")
```

The two kinds of param are different types, `Ignitor.param` for the instrument and `Katalyst.param` for the chain,
and the wrong one is a script error at the call, with the fix in the message. `ignp(Katalyst.param(...), 1)` says
`a Katalyst param passed to ignp; use katp`. An `Ignitor.param` handed to a chain knob, as in
`Katalyst(k => k.reverb(0.5, cutoff))` with the `cutoff` of the setter example above, says
`an Ignitor param in a Katalyst chain; use Kat.param`. A Katalyst param has no arithmetic
(`Katalyst.param("room", 5).mul(2)` is an error). An expression over an Ignitor param on a chain knob is accepted,
but it is folded once when the chain is built and does not listen to `katp`: hand the knob a `Katalyst.param` and
do the arithmetic on the pattern side.

### Audio-rate Modulation

Any parameter can accept an Ignitor node instead of a number:

```javascript
// Filter cutoff modulated by LFO
Ignitor.saw().lowpass(Ignitor.sine(0.3).range(500, 2500))

// Tremolo via multiplication
Ignitor.saw().times(Ignitor.sine(4).range(0, 1))  // 4 Hz tremolo

// Vibrato via frequency modulation
Ignitor.sine(Ignitor.freq().plus(Ignitor.sine(5).mul(10)))  // 5 Hz vibrato, 10 Hz depth
```

---

## Built-in Presets

| Name          | Aliases                  | Signal Graph                                                    |
|---------------|--------------------------|-----------------------------------------------------------------|
| `sine`        | `sin`                    | Sine(Freq)                                                      |
| `sawtooth`    | `saw`                    | Saw(Freq)                                                       |
| `square`      | `sqr`, `pulse`           | Square(Freq)                                                    |
| `triangle`    | `tri`                    | Tri(Freq)                                                       |
| `ramp`        |                          | Ramp(Freq)                                                      |
| `zawtooth`    | `zaw`                    | Zawtooth(Freq)                                                  |
| `pulze`       |                          | Pulze(Freq, duty=0.5)                                           |
| `impulse`     |                          | Impulse(Freq)                                                   |
| `supersaw`    |                          | SuperSaw(Freq, voices=8, spread=0.2)                            |
| `supersine`   |                          | SuperSine(Freq, voices=8, spread=0.2)                           |
| `supersquare` | `supersqr`, `superpulse` | SuperSquare(Freq, voices=8, spread=0.2)                         |
| `supertri`    |                          | SuperTri(Freq, voices=8, spread=0.2)                            |
| `superramp`   |                          | SuperRamp(Freq, voices=8, spread=0.2)                           |
| `pluck`       | `ks`, `string`           | Pluck(Freq, feedback=0.996, brightness=0.5, pick=0.5, stiffness=0) |
| `superpluck`  |                          | SuperPluck(Freq, voices=8, spread=0.2, ...)                     |
| `whitenoise`  | `white`                  | WhiteNoise(color=0)                                             |
| `brownnoise`  | `brown`                  | BrownNoise(leak=0.02)                                           |
| `pinknoise`   | `pink`                   | PinkNoise                                                       |
| `perlinnoise` | `perlin`                 | PerlinNoise(rate=1, octaves=1, persistence=0.5)                 |
| `berlinnoise` | `berlin`                 | BerlinNoise(rate=1, octaves=1, persistence=0.5)                 |
| `dust`        |                          | Dust(density=0.2, tail=1, bipolar=0)                            |
| `crackle`     |                          | Crackle(chaos=1.5) — chaotic, NOT a dust alias                  |
| `sgpad`       |                          | (Saw + Saw.detune(0.1)) / 2 -> onepole(3000)             |
| `sgbell`      |                          | Sine.fm(Sine, ratio=1.4, depth=300, decay=0.5)                  |
| `sgbuzz`      |                          | Square.lowpass(2000)                                            |

---

## Instrument Recipes

Every recipe ends in `.classic()`, so the pattern's doors reach it (see the `.classic()` section). A recipe
whose last call before `.classic()` is its own amplitude `.adsr(...)` is played with `.adsrOff()` on the
pattern, so the two envelopes do not multiply; the others keep `classic()`'s envelope.

### Woodwinds

**Flute** — Sine + triangle + breath noise + vibrato

```javascript
let flute = Ignitor.sine()
        .plus(Ignitor.tri().mul(0.3))
        .plus(Ignitor.perlin(12).mul(0.2).lowpass(4000).highpass(800).adsr(0.01, 0.12, 0.02, 0.01))
        .plus(Ignitor.perlin(8).mul(0.05))
        .lowpass(3000).highpass(400)
        .analog(0.15).vibrato(4.5, 0.012)
        .pitchEnvelope(1.5, x => x.adsr(0.01, 0.06, 0, 0))
        .adsr(0.06, 0.15, 0.75, 0.2).classic()
```

**Clarinet** — Triangle (odd harmonics) + light square + breath

```javascript
let clarinet = Ignitor.tri().mul(0.7)
        .plus(Ignitor.square().mul(0.15))
        .plus(Ignitor.sine().mul(0.15))
        .plus(Ignitor.perlin(6).mul(0.02))
        .plus(Ignitor.perlin(10).mul(0.08).adsr(0.02, 0.1, 0.0, 0.01))
        .lowpass(2800).highpass(150).onepole(4000)
        .vibrato(5, 0.003)
        .pitchEnvelope(0.5, x => x.adsr(0.01, 0.06, 0, 0))
        .adsr(0.04, 0.08, 0.9, 0.1).classic()
```

**Alto Saxophone** — Square + saw (reed buzz + conical bore)

```javascript
let alto = Ignitor.square().mul(0.6)
        .plus(Ignitor.saw().mul(0.3))
        .plus(Ignitor.sine().mul(0.1))
        .plus(Ignitor.perlin(10).mul(0.04))
        .plus(Ignitor.perlin(15).mul(0.12).adsr(0.02, 0.15, 0.0, 0.01))
        .lowpass(3500).highpass(200)
        .vibrato(4.5, 0.015)
        .pitchEnvelope(-2, x => x.adsr(0.01, 0.12, 0, 0))
        .adsr(0.03, 0.1, 0.85, 0.12).classic()
```

### Guitars

**Acoustic Guitar** — Karplus-Strong with natural filtering

```javascript
let acoustic = Ignitor.pluck().highpass(80).lowpass(4000).classic()
```

**Steel String** — Brighter attack with filter envelope

```javascript
let steel = Ignitor.pluck()
        .lowpass(Ignitor.constant(5000).plus(Ignitor.constant(3000).adsr(0.001, 0.4, 0.0, 0.1)))
        .highpass(100).classic()
```

**12-String** — Unison pluck for chorus effect

```javascript
let twelve = Ignitor.superpluck()
        .lowpass(Ignitor.constant(4000).plus(Ignitor.constant(2000).adsr(0.001, 0.5, 0.0, 0.1)))
        .highpass(100).classic()
```

**Electric Distorted**

```javascript
let crunch = Ignitor.pluck().lowpass(8000).distort(0.6).lowpass(4000).highpass(150).classic()
```

### Synth Pads

**Fat Analog Pad** — Supersaw with LFO-modulated filter

```javascript
let fatpad = Ignitor.supersaw()
        .analog(0.3)
        .lowpass(Ignitor.sine(0.3).plus(1).times(1000).plus(1500))
        .adsr(0.2, 0.5, 0.7, 1.0).classic()
```

### Synth Bass

**Plucky Bass** — Saw with fast filter envelope

```javascript
let bass = Ignitor.saw()
        .lowpass(Ignitor.param("cutoff", 800, "filter cutoff"))
        .adsr(0.005, 0.2, 0.0, 0.05).classic()
```

### Synth Leads

**Bitcrushed Lead**

```javascript
let crunchlead = Ignitor.square().crush(6).lowpass(3000).adsr(0.01, 0.1, 0.8, 0.3).classic()
```

### Bells & Mallet Percussion

**Glockenspiel, harmonic** (a 90s-ringtone bell): sines on the 3rd, 5th and 6th harmonics + noise transient. For a
metal bar use the bar's inharmonic ratios 2.756 and 5.404 (`detune(17.55)`, `detune(29.21)`); both in
`docs/instrument-prototypes.md`, chosen by ear 2026-10-02.

```javascript
let glock = Ignitor.sine().mul(0.5)
        .plus(Ignitor.sine().detune(19.02).mul(0.3))
        .plus(Ignitor.sine().detune(27.86).mul(0.15))
        .plus(Ignitor.sine().detune(31.02).mul(0.1))
        .plus(Ignitor.whitenoise().highpass(6000).mul(0.15).adsr(0.001, 0.02, 0.0, 0.005))
        .lowpass(Ignitor.constant(8000).plus(Ignitor.constant(4000).adsr(0.001, 0.8, 0.0, 0.1)))
        .adsr(0.001, 1.5, 0.0, 0.3).classic()
```

**FM Bell** — Inharmonic FM for metallic character

```javascript
let bell = Ignitor.sine().fm(Ignitor.sine(), 2.3, 400)
        .adsr(0.001, 1.5, 0.0, 0.5).classic()
```

**Marimba** — Sine with fast-decaying overtones + wood attack

```javascript
let marimba = Ignitor.sine().mul(0.7)
        .plus(Ignitor.sine().detune(12).mul(0.15).adsr(0.001, 0.08, 0.0, 0.02))
        .plus(Ignitor.sine().detune(19.02).mul(0.08).adsr(0.001, 0.04, 0.0, 0.01))
        .plus(Ignitor.perlin(15).mul(0.12).lowpass(1500).highpass(200).adsr(0.001, 0.03, 0.0, 0.005))
        .lowpass(2500).onepole(3000)
        .pitchEnvelope(1, x => x.adsr(0.001, 0.04, 0, 0))
        .adsr(0.005, 0.5, 0.0, 0.08).classic()
```

**Vibraphone** — Detuned sines with tremolo

```javascript
let vibes = Ignitor.sine().mul(0.5)
        .plus(Ignitor.sine().detune(19.02).mul(0.25))
        .plus(Ignitor.sine().detune(27.86).mul(0.12))
        .plus(Ignitor.whitenoise().highpass(4000).mul(0.06).adsr(0.001, 0.02, 0.0, 0.005))
        .lowpass(6000)
        .tremolo(5.5, 0.3)
        .adsr(0.003, 2.0, 0.0, 0.5).classic()
```

**Music Box** — Bright octave-stacked sines

```javascript
let musicbox = Ignitor.sine().mul(0.6)
        .plus(Ignitor.sine().detune(12).mul(0.3))
        .plus(Ignitor.sine().detune(24).mul(0.1))
        .plus(Ignitor.whitenoise().highpass(10000).mul(0.1).adsr(0.001, 0.01, 0.0, 0.005))
        .lowpass(6000)
        .adsr(0.001, 0.6, 0.0, 0.1).classic()
```

### Synth Percussion

**Synth Kick** — Sine with pitch envelope

```javascript
let kick = Ignitor.sine()
        .pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))
        .adsr(0.001, 0.2, 0.0, 0.02).classic()
```

**Hi-Hat** — Filtered white noise

```javascript
let hat = Ignitor.whitenoise()
        .highpass(8000)
        .adsr(0.001, 0.05, 0.0, 0.01).classic()
```

**Rim** — Sine + noise transient

```javascript
let rim = Ignitor.sine(800)
        .plus(Ignitor.whitenoise().highpass(4000).mul(0.3))
        .lowpass(3000)
        .adsr(0.001, 0.03, 0.0, 0.005).classic()
```

**Vinyl crackle** — old-record texture from primitives (no dedicated generator; the Motor stays raw)

Layer dust through band/high-pass for the "tick" ring, plus a quiet hiss bed. The dust authenticity knobs
do the heavy lifting: `bipolar` gives natural ±pops, and a high `tail` makes pops mostly-tiny / rare-loud
(the vinyl signature). Build it once in KlangScript and reuse it via export/import.

```javascript
// sparse loud pops + denser quiet crackle, both heavy-tailed & bipolar, through a resonant ring,
// over a faint pink-noise hiss bed and an optional sub-rumble
let vinyl = Ignitor.dust(0.08, /* tail */ 6, /* bipolar */ 1).bandpass(2500, 4)
        .plus(Ignitor.dust(0.02, /* tail */ 3, /* bipolar */ 1).bandpass(1500).mul(0.6))
        .plus(Ignitor.pinknoise().highpass(3000).mul(0.03))   // hiss bed
        .plus(Ignitor.brownnoise(0.005).lowpass(120).mul(0.04)).classic() // optional deep rumble
```

For a busier, more "broken-groove" crackle, swap in `Ignitor.crackle(1.7)` (the chaotic generator) as the
pop source instead of the heavy-tailed dust.

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
Ignitor.perlin(rate).mul(amount).adsr(fast_attack, short_decay, 0, short_release)
```

**Filter envelope** (brightness decay):

```javascript
.lowpass(Ignitor.constant(base).plus(Ignitor.constant(sweep).adsr(attack, decay, sustain, release)))
```

**Per-partial envelopes** (bells, mallet):

```javascript
Ignitor.sine().mul(0.5)                                                // fundamental
    .plus(Ignitor.sine().detune(12).mul(0.3).adsr(0.001, 0.3, 0, 0))  // octave, decays faster
    .plus(Ignitor.sine().detune(19).mul(0.1).adsr(0.001, 0.1, 0, 0))  // fifth+oct, even faster
```

**Pitch scoop** (attack transient):

```javascript
.pitchEnvelope(semitones, x => x.adsr(attack, decay, 0, 0))
// Positive = pitch drops (mallet percussion)
// Negative = pitch scoops up (saxophone, brass)
```

**Material character** (lowpass cutoff defines material):

- Wood: ~2500 Hz
- Brass: ~3500 Hz
- Metal/glass: 6000-10000+ Hz

**LFO modulation** (use low-frequency Ignitor as modulation source):

```javascript
// Filter LFO: sine at 0.3 Hz modulating cutoff 500-2500 Hz
.lowpass(Ignitor.sine(0.3).range(500, 2500))

// range(from, to) maps the oscillator's -1..1 onto from..to
```

**Layering oscillators** (additive synthesis):

```javascript
Ignitor.sine()                      // fundamental
    .plus(Ignitor.saw().mul(0.3))   // add brightness
    .plus(Ignitor.perlin(8).mul(0.05))  // add organic movement
    .mul(0.5)                   // normalize level
```

---

## Complete Example: Custom Instruments in a Track

```javascript
import * from "stdlib"
import * from "sprudel"

// Custom instruments
let koto = Ignitor.pluck()
    .plus(Ignitor.sine().detune(12).mul(0.1).adsr(0.001, 0.3, 0.0, 0.05))
    .lowpass(Ignitor.constant(5000).plus(Ignitor.constant(3000).adsr(0.001, 0.3, 0.0, 0.05)))
    .highpass(200).classic()

let pad = Ignitor.supersine(x => x.analog(0.3))
    .lowpass(Ignitor.sine(0.08).plus(1).times(300).plus(800))
    .adsr(0.8, 0.5, 0.9, 2.0).classic()

let kick = Ignitor.sine()
    .pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))
    .adsr(0.001, 0.2, 0.0, 0.02).classic()

let sub = Ignitor.sine().lowpass(200)
    .adsr(0.005, 0.3, 0.0, 0.05).classic()

// Composition
stack(
  // Melody
  note("a4 b4 c5 b4 a4 [b4 a4] f4@2")
    .sound(koto).legato(0.8).slow(4)
    .superimpose(fast(2).gain(0.075).pan(0.0), fast(2).gain(0.075).pan(1.0)),

  // Pad chords
  note("a2 d2 a2 f2").sound(pad).adsrOff().slow(4).legato(1.5).gain(0.25),

  // Bass
  note("a1 d2 a1 f1").sound(sub).adsrOff().slow(4).legato(1.5).gain(0.75),

  // Drums
  note("a1 ~ ~ ~ ~ ~ ~ ~").sound(kick).adsrOff().gain(0.8),
  sound("~ ~ ~ ~ cp ~ ~ ~").gain(0.4),
  sound("hh*8").gain(0.3)
).reverb(wet = 0.2, size = 5).delay(wet = 0.15, time = pure(1/8).div(cps))
```
