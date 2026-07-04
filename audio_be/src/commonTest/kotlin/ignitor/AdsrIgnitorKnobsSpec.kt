/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ADSR_EXP_K
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs

/**
 * The two opt-in ADSR knobs on the ignitor envelope `.adsr(...)`: `declickSeconds` and `expK`.
 *
 * Both default to today's behaviour (declick off; `expK == ADSR_EXP_K`), so the first test pins the
 * defaults and the others prove each knob reaches the audio. A constant DC = 1.0 upstream is used so
 * the rendered output IS the envelope gain per sample, isolating it from any oscillator motion.
 */
class AdsrIgnitorKnobsSpec : StringSpec({

    val sampleRate = 44100

    fun ctx(blockFrames: Int): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames,
        gateEndFrame = blockFrames,
        releaseFrames = 0,
        voiceEndFrame = blockFrames,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    // Constant DC = 1.0 source: with it, `.adsr(...)` output equals the envelope gain per sample.
    val dc: Ignitor = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, c: IgniteContext) {
            val end = c.offset + c.length
            for (i in c.offset until end) buffer[i] = 1.0
        }
    }

    fun render(sig: Ignitor, blockFrames: Int): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, 440.0, ctx(blockFrames))
        return buf
    }

    "defaults are behaviour-identical: bare adsr() == explicit declick=0, expK=ADSR_EXP_K" {
        val n = 22050
        val bare = render(dc.adsr(0.05, 0.2, 0.4, 0.1), n)
        val explicit = render(dc.adsr(0.05, 0.2, 0.4, 0.1, declickSeconds = 0.0, expK = ADSR_EXP_K), n)
        for (i in 0 until n) explicit[i] shouldBe bare[i]
    }

    "declickSeconds>0 rounds the attack→decay corner (lower 2nd-difference at the join)" {
        val n = 22050
        val attack = 0.1
        val join = (attack * sampleRate).toInt() // 4410 — the attack→decay slope discontinuity

        // 2nd difference peaks at a slope corner; the de-click one-pole spreads it out.
        fun maxCornerNearJoin(buf: AudioBuffer): Double {
            var m = 0.0
            for (i in (join - 200) until (join + 200)) {
                val corner = abs(buf[i + 1] - 2.0 * buf[i] + buf[i - 1])
                if (corner > m) m = corner
            }
            return m
        }

        val raw = render(dc.adsr(attack, 0.2, 0.4, 0.1, declickSeconds = 0.0), n)
        val smoothed = render(dc.adsr(attack, 0.2, 0.4, 0.1, declickSeconds = 0.001), n)
        maxCornerNearJoin(smoothed) shouldBeLessThan maxCornerNearJoin(raw)
    }

    "larger expK makes the exponential decay drop faster (steeper early)" {
        val n = 22050
        val decay = 0.4
        val decFrames = (decay * sampleRate).toInt()
        val sampleAt = decFrames / 5 // ~20% into the (instant-attack) decay

        // Decay curve is Exponential by default; sustain 0 so the decay runs to 0.
        val gentle = render(dc.adsr(0.0, decay, 0.0, 0.1, expK = 1.5), n)
        val steep = render(dc.adsr(0.0, decay, 0.0, 0.1, expK = 6.0), n)
        // Steeper curvature drops faster → lower gain at the same early decay point.
        steep[sampleAt] shouldBeLessThan gentle[sampleAt]
    }

    // ── the slot bridge: oscParam overrides reach the new params (sprudel / custom-ignitor path) ──
    "oscParam override reaches the declickSeconds slot" {
        val n = 22050
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            attackSec = IgnitorDsl.Constant(0.1),
            decaySec = IgnitorDsl.Constant(0.2),
            sustainLevel = IgnitorDsl.Constant(0.4),
            releaseSec = IgnitorDsl.Constant(0.1),
        )
        val bufDefault = render(dsl.toExciter(), n)
        val bufOverride = render(dsl.toExciter(mapOf("declickSeconds" to 0.002)), n)
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "oscParam override reaches the expK slot" {
        val n = 22050
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            attackSec = IgnitorDsl.Constant(0.0),
            decaySec = IgnitorDsl.Constant(0.4),
            sustainLevel = IgnitorDsl.Constant(0.0),
            releaseSec = IgnitorDsl.Constant(0.1),
        )
        val bufDefault = render(dsl.toExciter(), n)
        val bufOverride = render(dsl.toExciter(mapOf("expK" to 8.0)), n)
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }
})
