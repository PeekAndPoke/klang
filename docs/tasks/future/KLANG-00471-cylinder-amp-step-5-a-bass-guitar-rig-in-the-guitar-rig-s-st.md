# KLANG-00471: Cylinder amp step 5: a bass guitar rig in the guitar rig's style

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

Step 5 of KLANG-00466 (the guitar amp on the cylinder), asked for by the maintainer on 2026-11-18: a bass guitar
rig "in the same style" as the guitar rig. The band's bass today is one fixed instrument; the guitar has a rig
with a menu per stage. This ticket is the design of the bass's rig and how the two songs take it.

It is a design to try by ear, not a decision about the sound. Every number in it is a starting value from general
knowledge of the gear; none was listened to.

Needs KLANG-00467 (step 1). Related: KLANG-00464 (the bass and the drums shared like the guitar rig).

## Read first

- `/klang-music-writing`; the guitar rig's header comment in Der Schmetterling (the rules of the menu: the stock
  preset of every stage is the sound before the stage existed; pick presets one stage at a time, in signal order;
  a preset you cannot name in an A/B gets deleted, not tuned).
- KLANG-00469 (step 3): the shape of a cylinder amp and of the switch.
- The bass in both songs (`let bass = ...`, `bass_shape`, `bass_arrange` in Der Schmetterling; `bass`,
  `bassGuitar` in Kokon).

## What the bass is today (2026-11-18)

One instrument, nearly the same in both songs:

- a sub sine plus sine partials at 2f to 6f, the fundamental left to the sub (`harmonics(6, 0.9)`; Kokon has
  `harmonics(8, 1.2)`);
- a bell cut at the snare (`snareHz`, q 3, -3 dB), INSIDE the instrument;
- `.mul(0.2)`, then `distort(0.1, "soft")`: at that level the soft stage hardly bends, so the bass is clean;
- on the pattern: an envelope of `adsr(0.003, 5.0, 0.0, 0.015)` (Kokon: release 0.040), `hpf(35)` (Kokon: 30), a
  gain of 0.20 (Kokon: 0.55);
- in Der Schmetterling a second snare cut on the arrangement (`notch(freq = snareHz, q = 1.0)`) and, since
  2026-11-17, a compressor on its cylinder (threshold -20, ratio 3, attack 0.015, release 0.15).

Its header comment promises "sub sine + parallel saturated grind, mud band filtered out between them". The code
has no grind and no mud filter: the comment describes an older bass.

## What a metal bass does in the band

- It plays the rhythm guitar's riff an octave lower and owns 40 to 120 Hz. The guitars are highpassed at 100 to
  120 Hz on purpose, so the weight of the wall is the bass's.
- Its lows stay clean and very even; its mids and top are clipped (the "grind", roughly 700 Hz to 2.5 kHz). That
  band overlaps the guitars, and it is what makes guitars and bass read as one instrument.
- It is compressed hard, before the clipper, so the clipper sees every note at the same level.

## The rig

`string -> pickup` per note, in the voice. `compressor -> grind -> preamp -> cab -> level` on the bass's cylinder.
Every stage has a menu; `*Stock` is the bass of today.

### Strings (the voice)

Tells: how the note starts, how fast the top dies, how much fundamental there is.

| Preset | Tells | Sketch |
|---|---|---|
| `bassStringStock` | no attack of its own; a top that never dies; the fundamental a pure sine | today's sub plus partials, unchanged |
| `bassStringPick` | the click of the pick; the clank on the frets; the top gone in a third of a second while the body rings | a saw (a pickup hears the string's speed, and a plucked string's speed has a saw's spectrum); a lowpass that starts open and closes to about 900 Hz; a short band of noise near 3 kHz at the onset |
| `bassStringFinger` | a soft thump instead of a click; a rounder top from the first moment | the same saw, the lowpass starting lower (about 2.5 kHz) and closing to 700 Hz, no noise |

```
// Picked roundwound: a saw, a top that dies first, the pick on the winding.
let bassStringPick = (pAnalog) => Ign.saw(x => x.analog(pAnalog))
  .lowpass(900, 0.7, f => f.env(30).adsr(0.001, 0.30, 0.0, 0.05))       // open at the pick, 900 Hz once it rings
  .plus(Ign.pinknoise().adsr(0.001, 0.010, 0.0, 0.010).bandpass(3000, 1.5).mul(0.08))
```

### Pickups (the voice)

Tells: where on the string it sits (near the bridge it hears less fundamental), its resonance, its output.

| Preset | Tells | Sketch |
|---|---|---|
| `bassPickupStock` | none | `x => x` |
| `bassPickupP` | the middle of the string: the full fundamental, a punch in the low mids, a soft top | `lowpass(2800, 1.3)`, a band at 220 Hz, q 0.8, +2 dB |
| `bassPickupJ` | near the bridge: thinner at the bottom, a nasal growl, a brighter top, lower output | `lowpass(3800, 1.8)`, a band at 80 Hz, q 0.7, -3 dB, a band at 800 Hz, q 1.0, +3 dB, `mul(0.85)` |
| `bassPickupMM` | the active humbucker: hot and hard, a small scoop, the top wide open | `lowpass(4800, 2.0)`, a band at 500 Hz, q 0.7, -2 dB, `mul(1.3)` |

### Compressor (the chain, first)

Tells: whether the pluck gets through, how flat the body is.

| Preset | Tells | Sketch |
|---|---|---|
| `bassCompStock` | none | `k => k` |
| `bassCompPluck` | slow: the pluck passes, then the body is held | `compressor(threshold = -12, ratio = 4, knee = 6, attack = 0.015, release = 0.15)`, `mul(1.5)` |
| `bassCompSquash` | fast: every note the same, flat as a board | `compressor(threshold = -18, ratio = 8, knee = 4, attack = 0.002, release = 0.08)`, `mul(2.0)` |

Inside the rig the signal sits near full scale (the clippers bend around 1.0), so these thresholds are high
compared with a compressor behind an amp.

### Grind (the chain)

Tells: where the cut sits, what clips, how much of it comes back under the lows.

| Preset | Tells | Sketch |
|---|---|---|
| `bassGrindStock` | none | `k => k` |
| `bassGrindSplit` | clean lows, clipped mids and top: the modern metal bass | `bands`: under 220 Hz untouched; over it `distort(0.45, "tube")`, `lowpass(4500)`, `mul(0.55)` |
| `bassGrindFuzz` | everything clips, lopsided; the clean lows are laid under it again | `parallel`: `lowpass(150)` with two passes; beside it `distort(0.70, "asym")`, `lowpass(3200)`, `mul(0.30)` |

```
let bassGrindSplit = Kat.fn(k => k.bands(b => b
  .band(low => low)                                                     // under the cut: as it came
  .cut(220)
  .band(high => high.distort(0.45, "tube").lowpass(4500).mul(0.55))))   // over it: clipped, the fizz cut
```

`bands` splits so that the bands add up to the input again; a `parallel` of a lowpass and a highpass does not
(their phases differ around the cut). `bassGrindFuzz` uses `parallel` on purpose: the fuzz is the whole signal.

### Preamp (the chain)

Tells: one tube stage or none, and what it does to the mids.

| Preset | Tells | Sketch |
|---|---|---|
| `bassPreampStock` | today's: a soft stage that hardly bends | `mul(0.2)`, `distort(0.1, "soft")` |
| `bassPreampTube` | one warm stage, the low mids thicken | `distort(0.20, "tube")`, `highpass(30)`, `mul(1.2)` |
| `bassPreampScoop` | the smile: lows and top up, the mids out of the guitars' way | the tube stage, then bands at 60 Hz +3, 500 Hz q 0.6 -5, 2500 Hz +3 |
| `bassPreampGrowl` | the opposite: a mid push that speaks through a wall of guitars | the tube stage, then a band at 900 Hz, q 0.9, +4 dB |

### Cab (the chain)

Tells: where the box stops at the bottom, its bump, where the top ends.

| Preset | Tells | Sketch |
|---|---|---|
| `bassCabStock` | no cab: straight to the desk | `k => k` |
| `bassCab8x10` | sealed tens: tight, a bump at 110 Hz, nothing over 4 kHz | `highpass(45, 0.9)`, bands at 110 Hz +3 and 1 kHz +2, `lowpass(4200)` with two passes |
| `bassCab1x15` | one fifteen: deeper, slower, dark | `highpass(38, 1.0)`, a band at 75 Hz +4, `lowpass(2800)` with two passes |
| `bassCab4x10T` | tens with a tweeter: the clank gets out | `highpass(50, 0.8)`, bands at 120 Hz +2.5 and 3 kHz +2.5, `lowpass(8000)` with two passes |

### The amp and the instrument

```
// The bass: the string and the pickup. The envelope is the pattern's (adsr), as today.
let makeBass = (string, pickup) => pickup(string).classic()

// The amp: the compressor at the base rate, the clippers in one region, the cab, the old voice gain as the level,
// then the cylinder's usual strip.
let makeBassAmp = (comp, grind, preamp, cab, level) => Katalyst(k => k
  .serial(comp)
  .oversampleAtLeast(4, r => r.serial(grind, preamp))
  .serial(cab)
  .mul(level)
  .classic())

let bassAmpStock = makeBassAmp(bassCompStock, bassGrindStock, bassPreampStock, bassCabStock, 0.20)    // today's bass
let bassAmpMetal = makeBassAmp(bassCompSquash, bassGrindSplit, bassPreampGrowl, bassCab8x10, 0.20)    // a first guess
```

## How the two songs take it

- **The instrument splits** as the guitar's does: `makeBass(bassStringStock, bassPickupStock)` is today's
  oscillators; everything behind them moves to the chain on the bass's cylinder (4 in Der Schmetterling, 15 in
  Kokon), attached with `.katalyst(...)`.
- **The pattern's gain** becomes 1.0, the old gain the chain's level (0.20 in Der Schmetterling, 0.55 in Kokon).
- **The snare cut** leaves the instrument and becomes ONE stage at the end of the chain. Der Schmetterling has two
  today (the bell in the instrument, the notch on the arrangement); which one stays is heard, not assumed.
- **`hpf(35)` stays on the pattern.** It now sits before the amp, which is where a rumble filter belongs.
- **Der Schmetterling's cylinder compressor** stays with the stock rig (it is today's sound). With a compressor in
  the rig it is a second one; whether it stays is heard.
- **The same switch** as in step 3, until the maintainer has compared.

## The listening stages

| Stage | What changes | What to expect |
|---|---|---|
| A, the port | the stock rig on the cylinder against today's bass | the same. The bass plays one note at a time, so nothing new meets in the amp. A difference means the port is wrong. |
| B, the menu | one stage at a time, in signal order: string, pickup, compressor, grind, preamp, cab, the rest held | each preset is kept, tuned or deleted by whether its tells can be named |
| C, with the band | the bass against the rhythm guitars' amp | the grind band and the guitars: one instrument or two |

## Decide before implementing

- Whether the string menu is wanted. Today's sub and partials are a deliberate design (the partials are "the
  band a small speaker can play"); a saw through a pickup is the more real string and the less controlled one.
- The first song: Der Schmetterling is proposed, after its guitars (KLANG-00469).
- Whether the compressor counts as a stage of the rig or stays a mixing tool on the pattern.

## What is not known

- Every number above.
- UNVERIFIED: the exact words in the sketches (`Ign.saw(x => x.analog(...))`, a filter's `env(...)` and `adsr(...)`
  together, `bands` with an empty first processor). Check each against its door before writing it into a song.
- Whether a compressor at the head of a chain, in front of a region, behaves as written. No built-in song does it.

## Acceptance

- The stock rig on the cylinder compiles in both songs, and the maintainer hears no difference to the bass of
  today.
- The menu exists in the song with its tells as comments, and the maintainer has gone through it once.
- What was kept, what was deleted and why is recorded here.

## History

- 2026-11-18: created, as step 5 of KLANG-00466.
