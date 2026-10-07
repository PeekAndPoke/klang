/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.DoorFilter
import io.peekandpoke.klang.audio_be.voices.withClassicSlots
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.endsInClassic
import kotlin.random.Random

/**
 * **Every built-in sound, untouched and with `analog 2, lpf 1200`: the BASELINE of the instruments' sound.** Each
 * built-in name renders one note through the real `VoiceFactory`, the row's settings sent as `classic()`'s slots,
 * and the render's raw bits are pinned ([rawBitsHash]). It is the one wide guard against an accidental change to
 * any built-in's sound.
 *
 * History: frozen when the voice strip retired (phase 3 step 9, 2026-09-27), as "the strip's sound" through the
 * migration; every row was then bit-identical to the strip except four, the recorded cost of section 8 of
 * `docs/tasks-archive/2026-09/20260928-builtin-instruments.md` (`perlin`, `berlin` and `crackle` draw from the
 * voice's stream when CONSTRUCTED, so with `analog > 0` and one filter the tree's filter draw comes after theirs;
 * the `analog 2, lpf 1200` row keeps that draw order guarded). At the phase 3 end checkpoint (2026-10-02, the
 * maintainer) it became the baseline of today's tree: the pins were confirmed unchanged that day, and
 * `ClassicVoiceBaselineSpec`, the deep table of `saw` through every slot, was retired (its job done; the stage laws
 * are their own specs').
 *
 * When a change is MEANT to move a row (a sound decision, heard), regenerate the pins from the printed
 * `MATRIX-BASELINE` lines and say so in the commit; never hand-edit one, never derive them from the tree under test
 * in the spec itself. JVM only (Kotlin/JS math rounds differently).
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
            blockFrames = blockFrames,
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

            val cylinder = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    /** A row: a title, the voice's own bag (not `classic()` slots), and the typed door settings. */
    class Row(val title: String, val own: Map<String, Double>?, val doors: DoorFields = DoorFields())

    val rows = listOf(
        Row("untouched", null),
        Row("analog 2, lpf 1200: one filter", mapOf("analog" to 2.0), DoorFields(filters = listOf(DoorFilter.LowPass(freq = 1200.0, q = 0.707)))),
    )

    val untouched = mutableMapOf<String, DoubleArray>()

    fun untouchedOf(name: String): DoubleArray =
        untouched.getOrPut(name) { render(VoiceData.empty.copy(freqHz = 220.0, sound = name)) }

    for ((name, _) in sources) {
        for (row in rows) {
            "$name: ${row.title}" {
                val base = VoiceData.empty.copy(freqHz = 220.0, ignitorParams = row.own)
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
    "sine | analog 2, lpf 1200: one filter" to "cf3bbf63df05d6b2",
    "sin | untouched" to "5ba82d7f94bbf57a",
    "sin | analog 2, lpf 1200: one filter" to "cf3bbf63df05d6b2",
    "sawtooth | untouched" to "4b669e5216868b15",
    "sawtooth | analog 2, lpf 1200: one filter" to "d2fc3cb8eed6a515",
    "saw | untouched" to "4b669e5216868b15",
    "saw | analog 2, lpf 1200: one filter" to "d2fc3cb8eed6a515",
    "square | untouched" to "7c6f7e025547b001",
    "square | analog 2, lpf 1200: one filter" to "b3ae980f0a5f7ba6",
    "sqr | untouched" to "7c6f7e025547b001",
    "sqr | analog 2, lpf 1200: one filter" to "b3ae980f0a5f7ba6",
    "pulse | untouched" to "7c6f7e025547b001",
    "pulse | analog 2, lpf 1200: one filter" to "b3ae980f0a5f7ba6",
    "pulze | untouched" to "dc55ce37022534e8",
    "pulze | analog 2, lpf 1200: one filter" to "3e029e749f282c41",
    "triangle | untouched" to "a8a9de0818149759",
    "triangle | analog 2, lpf 1200: one filter" to "291b6500e676ab75",
    "tri | untouched" to "a8a9de0818149759",
    "tri | analog 2, lpf 1200: one filter" to "291b6500e676ab75",
    "ramp | untouched" to "cb669e5296868b15",
    "ramp | analog 2, lpf 1200: one filter" to "e20b3554be3668c7",
    "zamp | untouched" to "2c4854b53f9dfcb6",
    "zamp | analog 2, lpf 1200: one filter" to "780b8879b4861238",
    "zawtooth | untouched" to "ac4854b5bf9dfcb6",
    "zawtooth | analog 2, lpf 1200: one filter" to "9989c83e91aad501",
    "zaw | untouched" to "ac4854b5bf9dfcb6",
    "zaw | analog 2, lpf 1200: one filter" to "9989c83e91aad501",
    "impulse | untouched" to "362b71493e0046e6",
    "impulse | analog 2, lpf 1200: one filter" to "cc2e9163bb0e83a6",
    "silence | untouched" to "91a125c50dc05500",
    "silence | analog 2, lpf 1200: one filter" to "91a125c50dc05500",
    "supersaw | untouched" to "161dac49c5e840ee",
    "supersaw | analog 2, lpf 1200: one filter" to "dc207f658e9f044c",
    "supersine | untouched" to "ed2a586ad38c0bf9",
    "supersine | analog 2, lpf 1200: one filter" to "c240c449fc9f905e",
    "supersquare | untouched" to "99f54f81e86e9498",
    "supersquare | analog 2, lpf 1200: one filter" to "21880e7f1a9950ba",
    "supersqr | untouched" to "99f54f81e86e9498",
    "supersqr | analog 2, lpf 1200: one filter" to "21880e7f1a9950ba",
    "superpulse | untouched" to "99f54f81e86e9498",
    "superpulse | analog 2, lpf 1200: one filter" to "21880e7f1a9950ba",
    "supertri | untouched" to "fde7abdc2c88f63f",
    "supertri | analog 2, lpf 1200: one filter" to "3a4a5e564cd2dc71",
    "superramp | untouched" to "961dac4945e840ee",
    "superramp | analog 2, lpf 1200: one filter" to "55df96858316147a",
    "whitenoise | untouched" to "690f4b2a70bce931",
    "whitenoise | analog 2, lpf 1200: one filter" to "1fcea81e1e35d33d",
    "white | untouched" to "690f4b2a70bce931",
    "white | analog 2, lpf 1200: one filter" to "1fcea81e1e35d33d",
    "brownnoise | untouched" to "19887512478f2b25",
    "brownnoise | analog 2, lpf 1200: one filter" to "0184df506f09e905",
    "brown | untouched" to "19887512478f2b25",
    "brown | analog 2, lpf 1200: one filter" to "0184df506f09e905",
    "pinknoise | untouched" to "b282af068c92d995",
    "pinknoise | analog 2, lpf 1200: one filter" to "dd783fc3efa10f40",
    "pink | untouched" to "b282af068c92d995",
    "pink | analog 2, lpf 1200: one filter" to "dd783fc3efa10f40",
    "perlinnoise | untouched" to "051f8f1412f4ca67",
    "perlinnoise | analog 2, lpf 1200: one filter" to "585c70ca2e408541",  // diverged from the strip at HEAD: the section 8 draw order
    "perlin | untouched" to "051f8f1412f4ca67",
    "perlin | analog 2, lpf 1200: one filter" to "585c70ca2e408541",  // diverged from the strip at HEAD: the section 8 draw order
    "berlinnoise | untouched" to "084bf568d0361049",
    "berlinnoise | analog 2, lpf 1200: one filter" to "a257b145edfebfae",  // diverged from the strip at HEAD: the section 8 draw order
    "berlin | untouched" to "084bf568d0361049",
    "berlin | analog 2, lpf 1200: one filter" to "a257b145edfebfae",  // diverged from the strip at HEAD: the section 8 draw order
    "dust | untouched" to "14bc02d96c31e662",
    "dust | analog 2, lpf 1200: one filter" to "0b02f4c96eebfb34",
    "crackle | untouched" to "b33d221dd774d76a",
    "crackle | analog 2, lpf 1200: one filter" to "8a72c07ad59a04a1",  // diverged from the strip at HEAD: the section 8 draw order
    "pluck | untouched" to "d97ca175b17e19fa",
    "pluck | analog 2, lpf 1200: one filter" to "afb99770762cf8a7",
    "ks | untouched" to "d97ca175b17e19fa",
    "ks | analog 2, lpf 1200: one filter" to "afb99770762cf8a7",
    "string | untouched" to "d97ca175b17e19fa",
    "string | analog 2, lpf 1200: one filter" to "afb99770762cf8a7",
    "superpluck | untouched" to "1fb765b8ac225e5d",
    "superpluck | analog 2, lpf 1200: one filter" to "280703cba5ab4800",
    "sgpad | untouched" to "06c94960732c7ff5",
    "sgpad | analog 2, lpf 1200: one filter" to "dbec5959524c5b20",
    "sgbell | untouched" to "c6e219205c09ba37",
    "sgbell | analog 2, lpf 1200: one filter" to "01cdbcbfc2b29a2c",
    "sgbuzz | untouched" to "3612c7b2e99b9f9f",
    "sgbuzz | analog 2, lpf 1200: one filter" to "ab39851ae017881b",
    "eqdemo | untouched" to "f58847ba293b4ac3",
    "eqdemo | analog 2, lpf 1200: one filter" to "8a8cdb05c981be0c",
)
