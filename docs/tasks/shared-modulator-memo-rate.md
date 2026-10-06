# One stateful modulator shared by two oscillators at different pitches runs twice per block

Status: **queued 2026-10-06**, found in review (round 2 of `docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`, code role). Not
started. Pre-existing for `duty`; the oscillator `phase` input follows the same path and inherits it.

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
