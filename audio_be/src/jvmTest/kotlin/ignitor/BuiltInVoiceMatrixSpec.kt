/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.DoorFilter
import io.peekandpoke.klang.audio_be.voices.withClassicSlots
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.endsInClassic
import kotlin.random.Random

/**
 * **Every built-in sound, untouched and with `analog 2, lpf 1200`: a BASELINE** (signal-flow plan section 12),
 * frozen when the voice strip retired (phase 3 step 9, 2026-09-27). Each built-in name renders one note through the
 * real `VoiceFactory`, the row's settings sent as `classic()`'s slots.
 *
 * Until step 9 this spec rendered each row a second time with the name's SOURCE registered as an authored
 * instrument, so the voice strip ran after it, and compared the two (phase 3 step 6): every row bit-identical,
 * except the recorded cost of section 8 of `docs/tasks-archive/2026-09/20260928-builtin-instruments.md` (`perlin`, `berlin` and `crackle`
 * draw from the voice's stream when CONSTRUCTED, so with `analog > 0` and one filter the tree's filter draw comes
 * after theirs). Before the strip was deleted, every row's built-in render was fingerprinted on the tree that still
 * had it ([rawBitsHash]); those fingerprints are pinned here, regenerated at a listening checkpoint when a change
 * is meant to move a row, never hand-edited, never derived from the tree under test.
 *
 * Trimmed by the test consolidation (2026-09-28) to the two rows only this spec guards: `untouched`, the one
 * bit-level fingerprint of each built-in's SOURCE, and `analog 2, lpf 1200`, the section 8 draw-order record. The
 * per-stage rows it dropped ran the same `classic()` chain once per name; the stage laws are their own specs', the
 * whole-voice bits of the stages, on `saw`, `ClassicVoiceBaselineSpec`'s.
 *
 * `ClassicVoiceBaselineSpec` is the deep table (one source, the slots, both rates); this one is wide (every
 * built-in, one rate). JVM only, like the fingerprints of that table.
 */
class BuiltInVoiceMatrixSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 170
    val gateSec = 0.25

    val sources = builtInSources()

    val registry = IgnitorRegistry().apply { registerDefaults() }

    fun render(data: VoiceData, doors: DoorFields = DoorFields()): DoubleArray {
        val onsetSec = 37.0 / sampleRate
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data.withClassicSlots(doors),
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

    /** A row: a title, the voice's own bag (not `classic()` slots), and the typed door settings. */
    class Row(val title: String, val own: Map<String, Double>?, val doors: DoorFields = DoorFields())

    val rows = listOf(
        Row("untouched", null),
        Row("analog 2, lpf 1200: one filter", mapOf("analog" to 2.0), DoorFields(filters = listOf(DoorFilter.LowPass(1200.0, 0.707)))),
    )

    val untouched = mutableMapOf<String, DoubleArray>()

    fun untouchedOf(name: String): DoubleArray =
        untouched.getOrPut(name) { render(VoiceData.empty.copy(freqHz = 220.0, sound = name)) }

    for ((name, _) in sources) {
        for (row in rows) {
            "$name: ${row.title}" {
                val base = VoiceData.empty.copy(freqHz = 220.0, oscParams = row.own)
                val builtIn = render(base.copy(sound = name), row.doors)
                val hash = builtIn.rawBitsHash()

                println("MATRIX-BASELINE | \"$name | ${row.title}\" to \"$hash\",")

                withClue("the frozen fingerprint") { hash shouldBe BASELINE["$name | ${row.title}"] }

                if (name != "silence" && row.title != "untouched") {
                    withClue("engagement: the row's settings change the voice") {
                        builtIn.toList() shouldNotBe untouchedOf(name).toList()
                    }
                }
            }
        }
    }

    "every built-in ends in classic(), every one has its fingerprinted rows, and every sounding one is heard" {
        registry.names().filter { registry.get(it)?.endsInClassic() == true }.toSet() shouldBe sources.keys
        BASELINE.keys shouldBe sources.keys.flatMap { name -> rows.map { "$name | ${it.title}" } }.toSet()

        for ((name, _) in sources) {
            if (name == "silence") {
                continue
            }

            withClue(name) { untouchedOf(name).any { it != 0.0 } shouldBe true }
        }
    }
})

private val BASELINE: Map<String, String> = mapOf(
    "sine | untouched" to "5ba82d7f94bbf57a",
    "sine | analog 2, lpf 1200: one filter" to "3ca17d8c6bb7707d",
    "sin | untouched" to "5ba82d7f94bbf57a",
    "sin | analog 2, lpf 1200: one filter" to "3ca17d8c6bb7707d",
    "sawtooth | untouched" to "4b669e5216868b15",
    "sawtooth | analog 2, lpf 1200: one filter" to "a23413763e0b6e67",
    "saw | untouched" to "4b669e5216868b15",
    "saw | analog 2, lpf 1200: one filter" to "a23413763e0b6e67",
    "square | untouched" to "7c6f7e025547b001",
    "square | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "sqr | untouched" to "7c6f7e025547b001",
    "sqr | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "pulse | untouched" to "7c6f7e025547b001",
    "pulse | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "pulze | untouched" to "dc55ce37022534e8",
    "pulze | analog 2, lpf 1200: one filter" to "9fd309ba072f8a8d",
    "triangle | untouched" to "a8a9de0818149759",
    "triangle | analog 2, lpf 1200: one filter" to "c2bb1752cf93ccc7",
    "tri | untouched" to "a8a9de0818149759",
    "tri | analog 2, lpf 1200: one filter" to "c2bb1752cf93ccc7",
    "ramp | untouched" to "cb669e5296868b15",
    "ramp | analog 2, lpf 1200: one filter" to "d64b31a7458ede38",
    "zamp | untouched" to "2c4854b53f9dfcb6",
    "zamp | analog 2, lpf 1200: one filter" to "51c17f5ba96e011c",
    "zawtooth | untouched" to "ac4854b5bf9dfcb6",
    "zawtooth | analog 2, lpf 1200: one filter" to "c1c53ee5afde889c",
    "zaw | untouched" to "ac4854b5bf9dfcb6",
    "zaw | analog 2, lpf 1200: one filter" to "c1c53ee5afde889c",
    "impulse | untouched" to "362b71493e0046e6",
    "impulse | analog 2, lpf 1200: one filter" to "5fa938c5cffbbd36",
    "silence | untouched" to "91a125c50dc05500",
    "silence | analog 2, lpf 1200: one filter" to "91a125c50dc05500",
    "supersaw | untouched" to "161dac49c5e840ee",
    "supersaw | analog 2, lpf 1200: one filter" to "8e3a1389fa17e37c",
    "supersine | untouched" to "ed2a586ad38c0bf9",
    "supersine | analog 2, lpf 1200: one filter" to "eb033afc9053a6bb",
    "supersquare | untouched" to "99f54f81e86e9498",
    "supersquare | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "supersqr | untouched" to "99f54f81e86e9498",
    "supersqr | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "superpulse | untouched" to "99f54f81e86e9498",
    "superpulse | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "supertri | untouched" to "fde7abdc2c88f63f",
    "supertri | analog 2, lpf 1200: one filter" to "e556acfbb120e6e6",
    "superramp | untouched" to "961dac4945e840ee",
    "superramp | analog 2, lpf 1200: one filter" to "f3cd9a8f425df2ee",
    "whitenoise | untouched" to "690f4b2a70bce931",
    "whitenoise | analog 2, lpf 1200: one filter" to "2979ac7198287352",
    "white | untouched" to "690f4b2a70bce931",
    "white | analog 2, lpf 1200: one filter" to "2979ac7198287352",
    "brownnoise | untouched" to "19887512478f2b25",
    "brownnoise | analog 2, lpf 1200: one filter" to "8a97be42012984b1",
    "brown | untouched" to "19887512478f2b25",
    "brown | analog 2, lpf 1200: one filter" to "8a97be42012984b1",
    "pinknoise | untouched" to "b282af068c92d995",
    "pinknoise | analog 2, lpf 1200: one filter" to "2b46442da51532ea",
    "pink | untouched" to "b282af068c92d995",
    "pink | analog 2, lpf 1200: one filter" to "2b46442da51532ea",
    "perlinnoise | untouched" to "051f8f1412f4ca67",
    "perlinnoise | analog 2, lpf 1200: one filter" to "40facc761b9705b5",  // diverged from the strip at HEAD: the section 8 draw order
    "perlin | untouched" to "051f8f1412f4ca67",
    "perlin | analog 2, lpf 1200: one filter" to "40facc761b9705b5",  // diverged from the strip at HEAD: the section 8 draw order
    "berlinnoise | untouched" to "084bf568d0361049",
    "berlinnoise | analog 2, lpf 1200: one filter" to "783af6355564ecba",  // diverged from the strip at HEAD: the section 8 draw order
    "berlin | untouched" to "084bf568d0361049",
    "berlin | analog 2, lpf 1200: one filter" to "783af6355564ecba",  // diverged from the strip at HEAD: the section 8 draw order
    "dust | untouched" to "14bc02d96c31e662",
    "dust | analog 2, lpf 1200: one filter" to "91d27b4249e12a49",
    "crackle | untouched" to "b33d221dd774d76a",
    "crackle | analog 2, lpf 1200: one filter" to "f2401f7da408d430",  // diverged from the strip at HEAD: the section 8 draw order
    "pluck | untouched" to "d97ca175b17e19fa",
    "pluck | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "ks | untouched" to "d97ca175b17e19fa",
    "ks | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "string | untouched" to "d97ca175b17e19fa",
    "string | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "superpluck | untouched" to "1fb765b8ac225e5d",
    "superpluck | analog 2, lpf 1200: one filter" to "3fefd5bb04cf3a90",
    "sgpad | untouched" to "06c94960732c7ff5",
    "sgpad | analog 2, lpf 1200: one filter" to "da450286ec2b7af3",
    "sgbell | untouched" to "c6e219205c09ba37",
    "sgbell | analog 2, lpf 1200: one filter" to "fc7fccabc1942154",
    "sgbuzz | untouched" to "3612c7b2e99b9f9f",
    "sgbuzz | analog 2, lpf 1200: one filter" to "5a637a5d4dc066a2",
    "eqdemo | untouched" to "f58847ba293b4ac3",
    "eqdemo | analog 2, lpf 1200: one filter" to "9fbb80fd1c8e9206",
)
