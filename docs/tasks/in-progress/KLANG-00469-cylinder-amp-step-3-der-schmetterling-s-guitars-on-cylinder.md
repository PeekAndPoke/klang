# KLANG-00469: Cylinder amp step 3: Der Schmetterling's guitars on cylinder amps

> **Status (2026-11-19): IN PROGRESS.** Klang-Main-Dev: step 3 starts, on the maintainer's rework of 2026-11-19
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

Step 3 of KLANG-00466 (the guitar amp on the cylinder): Der Schmetterling's three guitars move from one amp per
note to one amp per guitarist, and its two rhythm guitars share one amp (the maintainer, 2026-11-18). The old rig
stays in the song behind one switch until the maintainer has compared the two.

Needs KLANG-00467 (step 1). KLANG-00468 (step 2) is not needed to start: it only makes the chain cheaper.

## Reworked by the maintainer (2026-11-19, to Klang-Main-Dev)

"Please make a copy of the current version. Then a full port of the song, not a switch please." This replaces D11
of KLANG-00466 and every line below that speaks of the switch (`ampOnCylinder`), of "both positions", of parts
written without `Ign.fn` / `Kat.fn` so that they serve both variants, and of deleting the loser after the verdict.
What holds now:

- **The copy.** The song as it is today becomes a second built-in song, playable beside the port, so the two can
  be compared by ear: its own file (`DerSchmetterlingAmpPerNote.kt`), its own id and title ("Der Schmetterling (amp
  per note)"), listed right after Der Schmetterling in `BuiltInSongs`. Its code is today's, byte for byte, apart
  from what a second song needs (id, title, and nothing that would make two songs export under one module name).
  It is the reference; it is not edited after that. Whether it stays is the maintainer's to say after listening.
- **The port.** Der Schmetterling itself is ported in full: the voice keeps the string and the pickup (pickups stay
  `Ign.fn`), every other rig part becomes a chain function written with `Kat.fn(...)`, and the amp runs on the
  guitarist's cylinder. No switch, no old branch left in the song.
- **`drive` ports as `drive`.** Step 1 added it to a chain: `powerStock` keeps `drive(0.3)`, not `mul(2.29)`.
- **The target is the decided shape** (decision 4 of KLANG-00466): the two rhythm guitars through ONE amp on one
  cylinder (stage B below), with the ticket's start values, the wood of the first rhythm guitar in the file, and a
  comment naming the choices that are the maintainer's by ear (the wood, how hard the amp is hit). Stage A (each
  guitar on a cylinder of its own) is not shipped; it is the configuration the port is MEASURED in against the
  copy, since there the two should be close.
- The header comment is rewritten for the new signal path, and the stale lines go, as "After the verdict" says.

## Read first

- `/klang-music-writing`, and the song: `apps/common/src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt`.
- KLANG-00466, "What every step may rely on" and "For the agent that carries a step out".
- KLANG-00472 (the caricatures reviewed): what may not be ideal in the parts. This step ports the parts AS THEY
  ARE; a change to a part's character is not this step.
- `git status` and `git log -3` on the song first. The maintainer edits it daily: the numbers below are those of
  2026-11-18, port the numbers the file holds.

## What the song has today (2026-11-18)

- **The parts**: four pickups, four pedals, four preamps, three power amps, four cabs, `snareCut`, and
  `guitarShape` ("the microphone"), each an `Ign.fn`.
- **Two rigs**, each one oversampling region around everything:
  `rhythmRig = serial(pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12, guitarShape)`
  and `melodyRig = serial(pickupSingle, pedalBoost, preampCrunch, powerPushPull, snareCut, cab4x12, guitarShape)`.
- **`makeGuitar(rig)`**: the string (a supersaw, the pitch envelope, the pick's noise, the envelope), then
  `rig(signal).classic()`.
- **Three guitars**, each on its own cylinder:

  | | sound | cylinder | pan | unison | body | gain | bus |
  |---|---|---|---|---|---|---|---|
  | guitar 1 | `guitarMelody` | 1 | 0.5 | 11 | spruce 0.15 | 0.280 | compressor -14.9, fader 0.40 |
  | guitar 2 | `guitar` | 2 | 0.10 and 0.90 | 7 | maple 0.15 | 0.220 | the same |
  | guitar 3 | `guitar` | 3 | 0.20 and 0.80 | 5 | mahogany 0.15 | 0.220 | the same |

  The three share a reverb written on their stack (`.reverb(wet = 0.25, size = 2.0)`).

## The new shape

The voice keeps the string and the pickup. Pedal, preamp, power amp, snare cut, cab and level trim become a
declared chain on the guitarist's cylinder, followed by `classic()`, so the pattern's `body`, `reverb`,
`compressor` and `katp("gain.gain", ...)` keep working.

### 1. The parts become plain functions

While both variants live, a part is written WITHOUT `Ign.fn` and without `Kat.fn`, so the same part serves the
voice (the old variant) and the cylinder (the new one). Every word a part uses exists on both after step 1.

```
let ampOnCylinder = true     // false: one amp per note, the rig until 2026-11. For the A/B; goes with its verdict.

// Pickups: they stay in the voice, as they are (Ign.fn).

// Pedals
let pedalStock    = x => x
let pedalScreamer = x => x.parallel(
    clean => clean,
    dirt => dirt.highpass(720).distort(0.20, "soft").lowpass(4200).mul(0.4)
  ).mul(0.50)
let pedalFuzz     = x => x.distort(0.70, "asym").lowpass(4500).mul(0.28)
let pedalBoost    = x => x.parallel(clean => clean, dirt => dirt.highpass(300).mul(1.4)).mul(0.6)

// Preamps
let preampStock    = x => x.distort(0.20, "tube").highpass(100).distort(0.50, "soft").highpass(100)
let preampClean    = x => x.distort(0.12, "tube").highpass(80).eq(e => e.band(freq = 3500, q = 0.7, db = 2.0)).mul(3.5)
let preampCrunch   = x => x.highpass(120).distort(0.35, "tube").highpass(120).distort(0.18, "asym").highpass(100)
  .lowpass(5500).mul(0.62)
let preampHighGain = x => x.highpass(120).distort(0.15, "tube").highpass(120).distort(0.09, "asym").highpass(100)
  .distort(0.35, "soft").lowpass(4800).mul(0.67)

// Power amps
let powerStock    = x => x.mul(2.29)        // was drive(0.3): a gain of 10^(0.3 * 1.2), no curve
let powerPushPull = x => x.distort(0.25, "soft").eq(e => e.band(freq = 4000, q = 0.7, db = 2.0)).mul(1.6)
let powerClassA   = x => x.distort(0.25, "asym").mul(1.3)

// Cabs
let cabStock = x => x.lowpass(5000).lowpass(5000)
let cab4x12  = x => x.eq(e => e
    .band(freq =  160, q = 0.6, db = 3.80)
    .band(freq =  800, q = 0.5, db = 4.60)
    .band(freq = 2700, q = 1.9, db = 2.00))
  .lowpass(5000, 0.707, f => f.passes(3))
  .highpass(100, 0.707, f => f.passes(2))
// cab1x12 and cabCombo the same way.

// The snare's notch: this song's own stage, not a part of the shared rig.
let snareCut = x => x.eq(e => e.band(freq = snareHz, q = 3.0, db = -4))

// From rig level to mix level. Was guitarShape, "the microphone".
let ampLevel = x => x.mul(0.18).parallel(c => c, c => c.shape("soft"))
```

Keep every comment the song has on its parts (the tells); only the wrapper changes.

### 2. The stages of each amp, the voice and the chain

```
// The stages after the pickup, in signal order: a part like any other.
let rhythmStages = x => x.serial(pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12, ampLevel)
let melodyStages = x => x.serial(pedalBoost, preampCrunch, powerPushPull, snareCut, cab4x12, ampLevel)

let makeGuitar = (pickup, stages) => {
  // ... the string, exactly as today, ending in `signal` ...

  // One amp per guitarist: the voice ends at the pickup. One amp per note: the whole rig in the voice, as it was.
  return (ampOnCylinder
      ? pickup(signal)
      : signal.oversampleAtLeast(4, y => y.serial(pickup, stages))
    ).classic()
}

// The amp: the stages in one region, the old voice gain as its level, then the cylinder's usual strip.
let makeAmp = (stages, level) => Katalyst(k => k
  .oversampleAtLeast(4, r => r.serial(stages))
  .mul(level)
  .classic())

let rhythmAmp = makeAmp(rhythmStages, 0.220)     // 0.220 was guitar 2's and 3's gain
let melodyAmp = makeAmp(melodyStages, 0.280)     // 0.280 was guitar 1's gain

let guitar       = makeGuitar(pickupHumbucker, rhythmStages)
let guitarMelody = makeGuitar(pickupSingle, melodyStages)
```

- The old variant must be the song of today, note for note: `serial(pickup, stages)` inside the region is the
  same chain of nodes as today's flat `serial(...)`.
- In the new variant the pickup runs outside the region, at the base rate. It is linear, so that is the same
  filter for less work.

### 3. The arrangements

Everything a cylinder does is written ONCE per cylinder, in a function both patterns apply. Two patterns on one
cylinder that write different bus settings would take turns (only the newest voice's are heard).

```
// One amp for both rhythm guitarists.
let rhythmBus = x => x.cylinder(2).katalyst(rhythmAmp)
  .gain(1.0)                                   // how hard the amp is hit; see the listening stages below
  .body(material = "maple", wet = 0.15)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.40)

let melodyBus = x => x.cylinder(1).katalyst(melodyAmp)
  .gain(1.0)
  .body(material = "spruce", wet = 0.15)
  .compressor(threshold = -14.9, ratio = 4, knee = 6, attack = 0.001, release = beats(0.1))
  .katp("gain.gain", 0.40)
```

- `body(...)` moves out of the three `guitarN_shape` functions into the bus functions.
- Each `guitarN_arrange` keeps its `scale`, `mute` and `late`, and takes its bus through the switch: the new bus
  function, or today's lines (`.cylinder(n).gain(g)` and the rest) unchanged.
- Guitars 2 and 3 must end up with EXACTLY the same bus settings. The two woods (maple, mahogany) become one;
  which one is the maintainer's choice, maple is only the first in the file.

## The listening stages

Three stages, each one change, so that what is heard can be named.

| Stage | What changes | Start values | What to expect |
|---|---|---|---|
| A, the port | every guitar on its own cylinder with its own amp chain (guitar 3 keeps cylinder 3 and its own copy of `rhythmBus`) | `.gain(1.0)` on all three | close to the per-note rig: the lines are single notes, so little meets in the amp. A clear difference here means the port is wrong; find it before going on. |
| B, one amp | guitars 2 and 3 both on cylinder 2 | `.gain(0.7)` on both, the compressor and the fader as they are | the two lines are mostly an octave, a fifth or both apart, so they should fuse into power chords; where they are a third apart it gets rougher. Louder too: two guitars now feed one compressor. |
| C, tuning | by ear, with the maintainer, from KLANG-00472 | | |

Known differences that are NOT faults of the port:

- **Level and pan come before the amp now.** The voice's `gain` and `velocity` set how hard the amp is hit, and so
  does the number of notes sounding. A guitar's loudness in the mix is the chain's `mul(level)` and the fader.
- **Left and right are two amps.** Guitars 2 and 3 are doubled left and right, so each side's amp gets one copy
  of each guitar and a little of the other side's (pan 0.10 leaves 0.16 of the signal on the far side).
- **The centred guitar reaches each amp 3 dB lower** than a hard-panned one (the pan law is constant power), so
  guitar 1 is driven a little less than before at `.gain(1.0)`. Try 1.4.
- **The bus `distort`'s DC blocker sits at 7.64 Hz**, the voice's near 35 Hz. The lopsided stages (`tube`, `asym`)
  shift the signal's centre with the level; on the cylinder that shift recovers more slowly. The highpasses after
  those stages take most of it.
- **Notes overlap in the amp**: a release tail and the next note's attack intermodulate, as in a real amp.

## What the agent can check without ears

- The song compiles in both positions of the switch (`:apps:common:jvmTest`, `BuiltInSongsSmokeTest`).
- That a second voice naming the chain already in service starts no chain swap. Read `KatalystChainRequestSpec`
  and `CylinderChainSwapSpec` (`audio_be`, commonTest, `cylinders/`); if no row pins it, add one, mutation-checked,
  before relying on it.
- If the song can be rendered offline (`/klang-music-recording`, `audio/ref/verification.md`): render guitar 3
  alone in both positions of the switch in stage A and report the level and a third-octave table of the two. If
  it cannot, say so plainly.
- Nothing else. How it sounds is the maintainer's to say.

## After the verdict

- The loser is deleted: the switch, the losing branch of `makeGuitar` and of the arrangements.
- If the cylinder amp stays, the parts are wrapped in `Kat.fn(...)` (the editor then knows their type), the
  song's header comment is rewritten for the new signal path, and the stale lines go (the header's "tone stack"
  that has no stage, the cab comment about "once the Katalyst DSL lands").
- The verdict, in the maintainer's words, is recorded in KLANG-00466.
- Der Schmetterling is an import for other songs (`peekandpoke/der-schmetterling`); nothing it exports changes its
  name in this step.

## Acceptance

- Both positions of the switch compile, and the old one is the song as it was.
- The maintainer has listened to stages A and B and said which variant the song keeps.
- Committed by path, the song only, with what was and was not checked in the message.

## History

- 2026-11-18: created, as step 3 of KLANG-00466.
- 2026-11-19: reworked by the maintainer: a copy of the current song and a full port, no switch.
- 2026-11-19: IN PROGRESS. Klang-Main-Dev: step 3 starts, on the maintainer's rework of 2026-11-19

## Built (2026-11-19), waiting for the maintainer's ear

Built by an Opus worker, coordinated and checked by Klang-Main-Dev. The ticket stays in progress: its acceptance is
the maintainer's listening.

- **The copy:** `DerSchmetterlingAmpPerNote.kt`, "Der Schmetterling (amp per note)", right after the original in
  `BuiltInSongs`. Its code equals the song of 5d1a35995 character for character (31687 characters, the same
  SHA-256); only the val, the id and the title differ. Not published as an importable module, so no export collides.
- **The port:** the voice ends at the pickup; all 19 other rig parts are `Kat.fn` chain parts with their stages,
  numbers and comments unchanged (a reviewer diffed them); `guitarShape` is `ampLevel` (it was not exported);
  `powerStock` keeps `drive(0.3)`. `makeAmp(stages, level)`: the stages in a 4x region, then the old voice gain as
  the level, then `classic()`. Rhythm guitars 2 and 3 share `rhythmBus` on cylinder 2; the melody guitar has
  `melodyBus` on cylinder 1; cylinder 3 is unused.
- **The maintainer's by ear, each with a comment in the song:** the shared rhythm amp's wood (maple, guitar 2's;
  mahogany was guitar 3's); how hard the rhythm amp is hit (`.gain(0.7)` per guitar); how hard the melody amp is hit
  (`.gain(1.0)`; centred it reaches each side's amp 3 dB lower than before, 1.4 would make that up).
- **Exported functions keep their names and changed what they do:** `guitarN_shape` no longer applies `body`;
  `guitarN_arrange` no longer sets the voice gain and now carries the amp, `body`, the compressor and the fader. No
  song in the repo imports Der Schmetterling.
- **Engine checks** (the maintainer: "for engine correctness only ... do not overdo it"): the song specs green
  (`:apps:common:jvmTest` 28, `:apps:studio:jvmTest` 172); both rhythm guitars carry one chain name and equal bus
  settings; a new row in `CylinderChainSwapSpec` pins that a second voice naming the chain in service on a sounding
  cylinder starts no swap (three guards hold it; the row goes red only when all fail); the pass merges the rhythm
  amp's 11 eq stages to 8 and the melody amp's 10 to 6, the same bits either way; one render of guitar 2 alone, 16
  cycles, port against copy at equal drive: peak 0.104 against 0.127, RMS 0.0264 against 0.0257, 125 Hz to 3 kHz
  within 0.9 dB, no bad sample. The studio and notstrom compile for the browser.
- **Review:** one round (`reviewer-opus-high`), CLEAN; 1 MINOR and 1 NIT applied.
- **Not done:** no one has listened. No browser tests. The song benchmark was not run (it writes into
  `docs/benchmarks/`): `./gradlew :apps:studio:runSongBenchmark --args=live` has a case for the copy now.
- **Found, older, not touched:** `SongBenchmarkCases.ungated()` looks for the seed line
  `seed(timeOfDay.mul(60*60*60*24))`, which the song no longer has, so the ledger's live rows would fail.
