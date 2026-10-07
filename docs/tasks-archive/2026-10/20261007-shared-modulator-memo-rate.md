# One stateful modulator shared by two oscillators at different pitches runs twice per block

Status: **done 2026-10-07**, with the residues documented: fixed for a shared modulator that reads no `Freq` (the
reported cases) and for one pitch mod over several pitched sources at one pitch (B-1, with Sakura and Irish Lament
retuned to keep their sound); two residues stay, each an author rule (maintainer, 2026-10-07) (see "What was done" at the end). Found in review (round 2 of
`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`, code role). Pre-existing for `duty`; the oscillator
`phase` input follows the same path and inherits it.

## What

Equal DSL subtrees share one built Ignitor behind a `MemoizingIgnitor` (`audio_be/.../ignitor/MemoizingIgnitor.kt`)
whose cache key includes `freqHz`. An oscillator renders its control inputs (`duty`, `phase`) at its OWN resolved
frequency (`actualFreq`, `Ignitors.kt`: `duty.generate(dutyBuf, actualFreq, ctx)`, `phaseIn.render(offsets,
actualFreq, ctx)`). When two oscillators at different pitches carry the same modulator subtree, every call misses the
memo, so the stateful modulator inside (an LFO) generates twice per block: it runs at double speed, and each
oscillator gets alternate blocks of it, a stepped, double-rate modulation on both. At the same pitch the memo hits and
the share is correct. The comment at `IgnitorDslRuntime.kt` (the memo wrap) calls the miss "honest", which is true of
the samples but not of the state.

Scenario: `let wob = Ign.sine(30).mul(0.3); Ign.saw(x => x.phase(wob)).plus(Ign.saw(Ign.freq().mul(1.5), x =>
x.phase(wob)))`, the natural way to write "one LFO on two layers".

## Measured (review round 2, 40 blocks at 220 Hz, the shared tree against the sum of the layers built separately)

| case | max difference |
|---|---:|
| a phase LFO (`Sine(30) * 0.3`) on a saw and a ramp, same pitch | 0.0 |
| the same phase LFO on a saw at the note and a saw at 1.5 times it | 3.60 |
| a PWM `duty` LFO (`Sine(30) * 0.2 + 0.5`) on two `Pulze`, the same two pitches (pre-existing) | 4.0 |

## Until it lands

Authors build a separate modulator per oscillator (the warning is in `.claude/skills/klang-music-writing/ref/
ignitor-reference.md`, at the `phase` paragraph and the `pulze` row).

## Possible fixes (to be designed, not decided)

- An oscillator's control inputs that do not read `Freq` get the voice's `freqHz` rather than `actualFreq`, so the
  memo hits (but `Freq` inside a modulator means the carrier's own frequency today, consistently with `harmonics`).
- The memo keeps one cache per distinct freq per block instead of re-rendering a stateful subtree.

## The failing spec to start from

The review's probe (it prints the differences; turn the prints into `maxDiff shouldBe 0.0` assertions for the
"different freq" cases, which then fail until the fix):

```kotlin
/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

class ZzProbePhaseShareR2Spec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    fun renderNode(dsl: IgnitorDsl, freqHz: Double, blocks: Int): DoubleArray {
        val c = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(ignitorParams = null, random = c.random, freqHz = freqHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            c.updateOffsetAndLength(0, blockFrames)
            ignitor.generate(buffer, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    fun probe(label: String, a: IgnitorDsl, b: IgnitorDsl) {
        val shared = renderNode(IgnitorDsl.Plus(a, b), 220.0, 40)
        val sa = renderNode(a, 220.0, 40)
        val sb = renderNode(b, 220.0, 40)
        var worst = 0.0

        for (i in shared.indices) {
            worst = maxOf(worst, abs(shared[i] - (sa[i] + sb[i])))
        }

        println("PROBE3 $label maxDiff(shared tree, sum of separately built layers) = $worst")
    }

    "PROBE 3: one phase LFO shared by two oscillators at different frequencies" {
        val lfo = IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0)), c(0.3))
        val upper = IgnitorDsl.Times(IgnitorDsl.Freq, c(1.5))

        probe(
            "phase, same freq",
            IgnitorDsl.Saw(analog = c(0.0), phase = lfo),
            IgnitorDsl.Ramp(analog = c(0.0), phase = lfo),
        )
        probe(
            "phase, different freq",
            IgnitorDsl.Saw(analog = c(0.0), phase = lfo),
            IgnitorDsl.Saw(freq = upper, analog = c(0.0), phase = lfo),
        )
        val duty = IgnitorDsl.Plus(IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0)), c(0.2)), c(0.5))
        probe(
            "duty (pre-existing), different freq",
            IgnitorDsl.Pulze(analog = c(0.0), duty = duty),
            IgnitorDsl.Pulze(freq = upper, analog = c(0.0), duty = duty),
        )
    }
})
```

## What was done (2026-10-07)

What we built: the memo drops its freq key for a subtree whose output cannot depend on the freq.

- `MemoizingIgnitor` has a `freqInvariant` flag; when set, the cache key is `(voiceElapsedFrames, offset, length)`.
  The build sets it at the first share (`IgnitorBuildCache.onShared`; it was the memo wrap at first, see round 1)
  when the subtree reads no `Freq` (`IgnitorBuildCache.usesMusicalFreq`, the predicate the D13 detune fold already relies on: runtime code
  consumes the freq argument only to forward it) and carries no accumulated pitch mod (the memo wraps the mod too,
  and a vibrato's knobs may read `Freq` where the subtree does not). Hand-built graphs and the tremolo's floored
  depth keep the old key (the flag defaults to false).
- One place covers every door that rewrites the freq argument: the oscillators' control inputs at `actualFreq`
  (`phase`, `duty`, and every other knob), FM's modulator at `freq x ratio`, and any future door.
- `SharedModulatorRateSpec` (the review's probe, as assertions, 40 blocks at 220 Hz against the layers built
  separately): phase LFO at the same pitch 0.0 (control, unchanged); phase LFO on a saw at the note and at 1.5 times
  it 3.60 before, 0.0 after; duty LFO on two pulses 4.0 before, 0.0 after; an fm modulator at ratio 2 also on the
  spine 2.80 before, 0.0 after; and a predicate row (true for the LFO, false for a `Freq` reader at any depth and
  for a subtree under a pitch mod).
- Bit-identity: an unshared memo (one consumer) never reads the key; a share at the same pitch hit before and hits
  now. Only a share of a `Freq`-free subtree called at two freqs in one window renders differently, and that is the
  fix. The render that backs it: the coordinator's 18-song corpus, raw and pcm hashes identical before and after the
  first fix, rendered 2026-10-06. No song in it shares a pitch-free LFO across layers at two pitches, so it exercises
  the unchanged path only; the specs carry the changed one. The final-tree render: the coordinator rendered the
  18-song corpus on 2026-10-07; 16 songs are bit-identical to main, and Die Kirschblüte (Sakura) and The Synthsale
  Piper's Farewell (Irish Lament) differ by their retune (B-1).
- Mutations (each inside one build-lock call, restored with `cp`, verified with `cmp`; final tree): no key
  resolution at the share (the old behaviour): the 3 different-pitch rows red (3.60, 4.0, 2.80); the memo ignoring the
  flag: those rows red; dropping the pitch-mod guard, or the `Freq` predicate: the predicate row red.

**Alternatives rejected.** (1) The oscillators pass the voice freq rather than `actualFreq` to inputs that read no
`Freq`: the same effect, but at every read site of every oscillator, FM, and every door to come, where the memo is
one place. (2) One cache per distinct freq per block: it keeps the samples per caller but not the state, a stateful
node rendered at two freqs still advances twice.

**The residues, each an author rule (maintainer, 2026-10-07).**

1. **A shared modulator that reads `Ignitor.freq()` anywhere (its rate, its depth, a scaling next to it) renders once
   per pitch; build it once per layer.** It keeps its freq key, so read by oscillators at two pitches it renders twice
   per block and every stateful node only in it advances twice, an LFO whose rate tracks the pitch
   (`Ignitor.sine(Ignitor.freq().div(8))`) and a pitch-free LFO scaled by the pitch (`wob * (220 / Ignitor.freq())` as
   a `duty`: 4.0; `Ignitor.sine(30).mul(Ignitor.freq().mul(0.002))` as a `phase`: 3.61) alike. Under each oscillator
   `Freq` means that oscillator's own pitch (the `actualFreq` convention, as for `harmonics` and FM's params), so the
   readers want different signals; the honest fix is two instances (a pitch context per re-anchoring door, as D13 did
   for `detune`), build-time weight the maintainer declined. A narrower fix was built in round 1 and dropped (below).
2. **A layer detuned under a pitch mod that keeps its freq key renders the whole mod again** (an `fm`, whose `freq`
   defaults to the note, or a vibrato whose depth reads `Ignitor.freq()`, over `saw + saw.detune(12)`: 3.82 and 3.41,
   review round 2). Give such a layer its own mod. See B-1.

Both are pinned by RESIDUE rows in `SharedModulatorRateSpec` (asserting the difference), which flip consciously the
day a fix lands.

### Round 1 and 2 (review, 2026-10-07): the single-reader cliff, built and dropped

Round 1 found the first residue's narrower half (a pitch-free LFO whose only reader is a shared `Freq`-reading
expression) and the implementer closed it with "re-pitching doors": a build-time depth counter around every pitched
source's knobs and FM's modulator, and a sweep that made every freq-invariant memo cache when a door saw a share. The
round-2 review counted it as build-time weight of the kind the stone rule asks to consult on, 18 arms paired by
convention with no per-arm guard, for an unshipped shape, while an author rule was needed anyway; the maintainer
dropped it on 2026-10-07. What stays from it: the cost lesson. Resolving the freq key at every memo wrap (round 0) made
a voice build about five times slower (16-layer tree 15 to 77 us, built-in supersaw 0.9 to 3.6 us); it is resolved at
a share only. Measured on the final tree against the runtime of `HEAD` (JVM, 2000 builds, median of 7): 16-layer
tree 16.3 / 16.5 us, built-in sine 0.79 / 0.78 us, built-in supersaw 0.93 / 0.87 us, all within run-to-run noise.

### B-1 (maintainer decision 2026-10-07): one pitch mod over several pitched sources

Found in review round 1 (reviewer B): a pitch mod (`vibrato`, `fm`, `pitchMod`, and the stateless `pitchEnvelope` and
`accelerate`) hands its mod Ignitor to every pitched source under it (`applyMod`), and each source generated it. The
mod's state (the vibrato LFO, the fm modulator, any LFO in their knobs) advanced once per SOURCE per block: a vibrato
over two pitched layers ran at twice its written rate, each layer on alternate blocks of the LFO.

What we built, with the memo already there: the pitch-mod arm puts its mod (its own times the mods around it) behind
ONE `MemoizingIgnitor` that always caches per block (`combineMods` in `IgnitorDslRuntime.kt`). Its freq key is resolved
eagerly, once per pitch-mod node: dropped when the node's knobs and the outer mod read no `Freq`, so a layer detuned
under a vibrato shares the one LFO. A mod that keeps the key (FM, or a `Freq`-reading knob) is shared by layers at one
pitch and rendered again by a detuned layer (the second residue above). The stateless laws render the same numbers
either way. Round 1 counted readers so a one-reader mod could skip the copy; round 2 measured that the copy costs
nothing visible and removed the counting (a one-source vibrato voice renders 1738 to 1778 ns/block always caching,
1737 delegating, 1718 on `HEAD`'s runtime: within noise), and a row pins that one source renders the very bits the mod
applied by hand renders.

Rows in `SharedModulatorRateSpec`, each the shared tree against the layers each given its own mod: vibrato over sine
and tri (2.07 without the fix), over a saw and a saw at 1.5 times the note (3.94), with an LFO as its depth (1.98),
over a saw and the same saw detuned an octave (3.93), over a plain and a pitch-enveloped source (2.79), and fm over a
sine and a tri (2.04); all 0.0 now. Mutations on the final tree (one lock call each, `cp` restore, `cmp` OK): the mod's
memo delegating instead of caching: 6 rows red; the mod keeping its freq key: the detune row red; the memo's copy one
sample short: the one-source row and 10 others red.

**Songs, adjusted so they render what was heard when they were tuned** (each measured on the voice alone, 48 kHz, the
old engine with the old text against the fixed engine with the new text; not bit-identical, because the old stream
was stepped and gave each layer a different slice of the LFO):

| song, instrument | pitched layers under the vibrato | old text | new text | max diff | RMS diff (relative to the voice) |
|---|---:|---|---|---:|---:|
| Sakura, `shaku` | 2 (sine, tri; the perlins are noise) | `vibrato(2, perlin(1)...)` | `vibrato(4, perlin(2)...)` | 0.0059 at peak 0.105 | -33.3 dB |
| Irish Lament, `blockfloete` | 7 (sine, tri, saw, four detuned sines) | `vibrato(1/2, 0.1)` | `vibrato(7/2, 0.1)` | 0.0013 at peak 0.140 | -39.2 dB |

For comparison, the old text on the fixed engine differs by +0.4 dB (Sakura) and +1.7 dB (Irish Lament) RMS, the
whole vibrato; `vibrato(4, perlin(1))` for Sakura -26 dB, `vibrato(3/2)` for the flute +0.7 dB. The residual is the
per-layer LFO offset of the old engine (up to six blocks, 16 ms, on the flute) and the block steps; the rate and depth
are matched, so we expect it to be inaudible; the maintainer's ear decides. The pitch envelopes in Der Schmetterling,
Kokon, Sandsturm, Sakura's kick and the frozen songs are stateless laws: unchanged. Sprudel's `vibrato`/`accelerate`
run on the voice's pitch strip, one per voice: not affected.

Not adjusted, reported: the woodwind prototypes in `docs/instrument-prototypes.md` (flute, clarinet, jazz clarinet,
alto, tenor, soprano) and the three in `.claude/skills/klang-music-writing/ref/ignitor-reference.md` (flute, clarinet,
alto) put a 4 to 5 Hz vibrato over two or three pitched layers, so they were heard at 9 to 15 Hz. They are reference
patches, not tuned songs, and the written rates are the musical ones; with the fix they sound as written. The
whitepaper (`src/jsMain/resources/klang-whitepaper.html`) quotes Sakura's old `shaku` line; updating published text is
the maintainer's call.

