/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.ChainAudioFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * `body(...)` and `vowel(...)` are authored in the same `filters` list as `lpf`/`hpf`, but they do
 * NOT belong to the per-voice chain: `VoiceFactory` pulls them out (`:108-110`) and hands them to
 * the voice's `body` / `vowel` fields, from which the orbit-level Katalyst picks them up.
 *
 * **That routing is the entire point of the 2026-07-04 body-to-orbit move.** A resonator left in the
 * per-voice chain runs once per voice, so `superimpose` — which renders a note two or more times —
 * would stack the same resonance super-additively and the body would get louder the thicker the
 * patch. Moved to the orbit it runs once regardless.
 *
 * Audit finding F5 lists this as untested and that was correct: `VoiceFactoryFilterOrderSpec`
 * exercises `makeVoice` but never puts a Body or Formant alongside LP/HP, so nothing checked that
 * the split happens at all.
 *
 * (F5's other two bullets fared differently. The `FilterModRenderer` drift path turned out to be
 * well covered by `FilterEnvSemitoneSpec`'s *"drift stays a pure multiplier OUTSIDE the semitone
 * exponent"* row — the finding was wrong. `VoiceFactory.toModulator` is genuinely unreached, since
 * every `makeVoice` spec omits `FilterDef.envelope`; that half stays open.)
 */
class VoiceFactoryBodyVowelRoutingSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun voiceOf(filters: List<FilterDef>): Voice {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        return factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = VoiceData.empty.copy(
                    freqHz = 440.0,
                    sound = "triangle",
                    filters = FilterDefs(filters),
                ),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    // `mainFilter` is only a ChainAudioFilter when more than one filter survives the split — with a
    // single one the factory hands back the filter itself, and with none it is null. Pulling a Body
    // out of a two-filter list therefore changes the SHAPE of mainFilter, not just its length, so a
    // helper that assumed a chain would fail on exactly the case this spec exists to test.
    fun chainOf(voice: Voice): List<Any> = when (val f = voice.mainFilter) {
        null -> emptyList()
        is ChainAudioFilter -> f.filters
        else -> listOf(f)
    }

    val body = FilterDef.Body(
        bands = listOf(FilterDef.Body.Mode(freq = 120.0, db = 9.0, q = 12.0)),
        mix = 1.0,
    )
    val vowel = FilterDef.Formant(
        bands = listOf(FilterDef.Formant.Band(freq = 700.0, db = 6.0, q = 8.0)),
        mix = 1.0,
    )
    val lpf = FilterDef.LowPass(freq = 1000.0, q = 0.707)
    val hpf = FilterDef.HighPass(freq = 200.0, q = 0.707)

    "a Body authored alongside lpf/hpf is routed to the orbit, not baked into the voice chain" {
        val voice = voiceOf(listOf(lpf, body, hpf))
        val chain = chainOf(voice)

        // Two filters, not three: the Body is gone from the per-voice chain...
        chain.size shouldBe 2
        chain[0].shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
        // ...and the survivors keep their authored order.
        chain[1].shouldBeInstanceOf<LowPassHighPassFilters.SvfHPF>()

        // ...but it is not dropped — it rides on the voice for the Katalyst to pick up.
        voice.body shouldBe body
        voice.vowel.shouldBeNull()
    }

    "a Formant is routed the same way" {
        val voice = voiceOf(listOf(lpf, vowel))
        val chain = chainOf(voice)

        chain.size shouldBe 1
        chain[0].shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()

        voice.vowel shouldBe vowel
        voice.body.shouldBeNull()

        // The single survivor is handed back unwrapped, not as a one-element chain.
        voice.mainFilter.shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
    }

    "Body and Formant together leave only the tunable filters in the chain" {
        val voice = voiceOf(listOf(body, lpf, vowel, hpf))
        val chain = chainOf(voice)

        chain.size shouldBe 2
        voice.body shouldBe body
        voice.vowel shouldBe vowel
    }

    "with no resonators authored, both fields stay null and the chain is untouched" {
        val voice = voiceOf(listOf(lpf, hpf))

        // The positive control for the three rows above: without it, a `makeVoice` that dropped
        // EVERY filter would satisfy their size assertions just as well.
        chainOf(voice).size shouldBe 2
        voice.body.shouldBeNull()
        voice.vowel.shouldBeNull()
    }
})
