/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.onePoleLpfCoeff
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.random.Random

/**
 * Guards the `warmth -> onepole(freq)` unification (2026-08-24):
 *
 * 1. The sprudel door (oscParam `"onepole"` in Hz, applied by [IgnitorRegistry]) and the
 *    engine one-pole (`Ignitor.onePoleLowpass`, which the ignitor door's `onepole(freq)`
 *    reaches via `IgnitorDslRuntime`) build the SAME filter — bit-identical output.
 * 2. The migration conversion is pinned: the old `warmth(w)` coefficient was `a = 1 - w`
 *    over the shared kernel `y += a(x - y)`, so `freq = sr/π · atan((1-w)/w)`; at 48 kHz
 *    `warmth(0.5)` is exactly `onepole(12000)` (coefficient 0.5). If `onePoleLpfCoeff`
 *    ever changes its mapping, the conversion table in the KDoc/migration is wrong — this
 *    row is the tripwire.
 */
class OnepoleParitySpec : StringSpec({

    val blockFrames = 128
    val blocks = 8
    val frames = blocks * blockFrames

    fun ctx() = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = frames * 2,
        gateEndFrame = frames * 2,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
    )

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.offset = 0
        c.length = blockFrames
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(out, blk * blockFrames, 0, blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        var peak = 0.0
        for (v in out) {
            if (abs(v) > peak) {
                peak = abs(v)
            }
        }
        (peak > 1e-6) shouldBe true
        return out
    }

    "IgnitorRegistry's oscParam route is bit-identical to applying onePoleLowpass(Hz) by hand" {
        // The one row that pins BOTH the wiring and the UNIT: build the same "sine" exciter
        // twice — once through the oscParam key ("onepole" in Hz), once plain plus a manual
        // .onePoleLowpass(800.0). A dropped registry line, a changed key, a halved value, or
        // a reinstated coefficient interpretation (the old warmth semantics) all go red.
        val registry = IgnitorRegistry().apply { registerDefaults() }
        fun exciter(params: Map<String, Double>?): Ignitor {
            val data = VoiceData.empty.copy(freqHz = 220.0, sound = "sine", oscParams = params)
            return registry.createExciter("sine", data, freqHz = 220.0, random = Random(7))
                ?.ignitor ?: error("no exciter")
        }
        val viaOscParam = render(exciter(mapOf("onepole" to 800.0)))
        val manual = render(exciter(null).onePoleLowpass(800.0))
        for (i in 0 until frames) {
            viaOscParam[i].toRawBits() shouldBe manual[i].toRawBits()
        }
    }

    "live path frequency response: |H| at the nominal cutoff is pinned (~0.437, all-pole)" {
        // The absolute-response guard for the LIVE one-pole (OnePoleLowpassIgnitor). The
        // legacy OnePoleLPF spec rows now guard a test-only class; without this row a
        // mutant that stops calling onePoleLpfCoeff (e.g. fc/sampleRate) keeps every other
        // row green while retuning all onepole() sites.
        //
        // NOTE the value: this kernel is the ALL-POLE one-pole (`y += a(x-y)`, no Nyquist
        // zero), whose magnitude at the nominal fc is a/|1-(1-a)e^(-jw)| ≈ 0.437 at
        // 1 kHz / 48 kHz — NOT the textbook -3 dB. That is the shipped sound (and what the
        // old `warmth` was); the pin is the measured truth, not a design claim.
        fun rms(chain: Ignitor, freqHz: Double): Double {
            val c = ctx()
            c.offset = 0
            c.length = blockFrames
            val buf = AudioBuffer(blockFrames)
            var sum = 0.0
            var n = 0
            repeat(blocks) { blk ->
                chain.generate(buf, freqHz, c)
                if (blk >= blocks / 2) { // steady state only
                    for (i in 0 until blockFrames) {
                        sum += buf[i] * buf[i]
                        n++
                    }
                }
                c.voiceElapsedFrames += blockFrames
            }
            return kotlin.math.sqrt(sum / n)
        }
        val fc = 1000.0
        val plain = rms(Ignitors.sine(), fc)
        val filtered = rms(Ignitors.sine().onePoleLowpass(fc), fc)
        (filtered / plain) shouldBe (0.437 plusOrMinus 0.02)
    }

    "conversion pin: onepole(12000) at 48 kHz has coefficient 0.5 == the old warmth(0.5)" {
        // a = K/(1+K), K = tan(π·fc/sr); fc = 12000, sr = 48000 → K = tan(π/4) = 1 → a = 0.5.
        // The migration formula fc = sr/π·atan((1-w)/w) inverts exactly this mapping.
        onePoleLpfCoeff(12000.0, 48000.0) shouldBe (0.5 plusOrMinus 1e-12)
    }
})
