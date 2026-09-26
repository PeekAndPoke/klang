/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * **Every built-in sound, as a built-in, against its own source through the voice strip** (phase 3
 * step 6). Each built-in name renders the same note twice through the real `VoiceFactory`:
 *
 *  - BUILT-IN: the name as `registerDefaults` registers it (the shape `IgnitorRegistry.registerBuiltIn`
 *    writes, the voice strip off), the row's settings on the typed `VoiceData` fields the sprudel doors write today;
 *  - STRIP: the SAME source (`builtInSources()`) registered as an authored instrument, so the voice strip
 *    runs after it, with the same fields: what that name was before step 6.
 *
 * Every row is bit-identical, except the rows this spec names as the recorded cost of section 8 of
 * `docs/tasks/builtin-instruments.md`: `perlin`, `berlin` and `crackle` draw from the voice's stream when
 * they are CONSTRUCTED, so with `analog > 0` and one filter the tree's filter draw comes after theirs,
 * where the strip's came before. Every other source draws when it renders, after both.
 *
 * `ClassicStripParitySpec` is the deep table (one source, every slot, both rates); this one is wide
 * (every built-in, a row per stage group, one rate). JVM only: about a minute here, several in a
 * browser, past the JS test runner's 30 s no-activity window.
 */
class BuiltInVoiceMatrixSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 170
    val gateSec = 0.25

    val sources = builtInSources()

    /** The sources that take their rng draws at construction (see the class KDoc). */
    val constructionDrawers = setOf("perlin", "perlinnoise", "berlin", "berlinnoise", "crackle")

    val registry = IgnitorRegistry().apply {
        registerDefaults()

        for ((name, source) in sources) {
            register("strip-$name", source)
        }
    }

    fun render(data: VoiceData): DoubleArray {
        val onsetSec = 37.0 / sampleRate
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
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = onsetSec,
                gateEndTime = onsetSec + gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = noSamples,
        ) ?: error("makeVoice returned null for ${data.sound}")

        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(blocks * blockFrames)

        repeat(blocks) { block ->
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    fun filters(vararg defs: FilterDef): FilterDefs = FilterDefs(defs.toList())

    /** A row: a title, the voice's own bag (not `classic()` slots), and the typed fields. */
    class Row(val title: String, val own: Map<String, Double>?, val settings: VoiceData.() -> VoiceData)

    val rows = listOf(
        Row("untouched", null) { this },
        Row("lpf 900 with an envelope", null) {
            copy(filters = filters(FilterDef.LowPass(900.0, 1.5, envelope = FilterEnvDef(decay = 0.1, depth = 18.0))))
        },
        Row("hpf 300 and lpf 3000", null) {
            copy(filters = filters(FilterDef.HighPass(300.0, 0.707), FilterDef.LowPass(3000.0, 0.707)))
        },
        Row("adsr 0.02 / 0.1 / 0.4 / 0.1", null) { copy(adsr = AdsrDef.Std(attack = 0.02, decay = 0.1, sustain = 0.4, release = 0.1)) },
        Row("adsrOff", null) { copy(adsr = AdsrDef.Std(on = false)) },
        Row("tremolo square", null) { copy(tremoloDepth = 0.8, tremoloSync = 6.0, tremoloShape = "square") },
        Row("distort 0.6 tube x2", null) { copy(distort = 0.6, distortShape = "tube", distortOversample = 2) },
        Row("crush 5", null) { copy(crush = 5.0) },
        Row("coarse 3", null) { copy(coarse = 3.0) },
        Row("onepole 1500", mapOf("onepole" to 1500.0)) { this },
        Row("analog 2, lpf 1200: one filter", mapOf("analog" to 2.0)) { copy(filters = filters(FilterDef.LowPass(1200.0, 0.707))) },
    )

    /**
     * Rows whose settings cannot change that source, so their engagement check is skipped (identity is
     * still asserted): the raw pulse and the impulse only ever emit values the crush quantizer maps onto
     * themselves.
     */
    val quantizerFixedPoints = setOf("pulze:crush 5", "impulse:crush 5")

    val untouchedStrip = mutableMapOf<String, DoubleArray>()

    fun untouchedStripOf(name: String): DoubleArray =
        untouchedStrip.getOrPut(name) { render(VoiceData.empty.copy(freqHz = 220.0, sound = "strip-$name")) }

    for ((name, _) in sources) {
        for (row in rows) {
            val divergent = name in constructionDrawers && row.own?.containsKey("analog") == true

            "$name: ${if (divergent) "DIVERGENT (construction draws, section 8)" else "IDENTICAL"}: ${row.title}" {
                val base = VoiceData.empty.copy(freqHz = 220.0, oscParams = row.own)
                val builtIn = render(base.copy(sound = name).(row.settings)())
                val strip = render(base.copy(sound = "strip-$name").(row.settings)())
                val mismatch = strip.indices.firstOrNull { strip[it].toRawBits() != builtIn[it].toRawBits() } ?: -1

                if (name != "silence" && row.title != "untouched" && "$name:${row.title}" !in quantizerFixedPoints) {
                    withClue("engagement: the row's settings change the strip voice") {
                        strip.toList() shouldNotBe untouchedStripOf(name).toList()
                    }
                }

                if (divergent) {
                    withClue("recorded as divergent, and it still is") { mismatch shouldNotBe -1 }
                } else {
                    withClue("first mismatching frame") { mismatch shouldBe -1 }
                }
            }
        }
    }

    "the matrix covers every built-in and hears them: the untouched strip side of every sounding source is not silence" {
        registry.names().filter { registry.isBuiltIn(it) }.toSet() shouldBe sources.keys

        for ((name, _) in sources) {
            if (name == "silence") {
                continue
            }

            withClue(name) { untouchedStripOf(name).any { it != 0.0 } shouldBe true }
        }
    }
})
