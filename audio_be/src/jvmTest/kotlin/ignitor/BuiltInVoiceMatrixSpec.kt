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
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.DoorFilter
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.withClassicSlots
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.endsInClassic
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.random.Random

/**
 * **Every built-in sound, untouched and with `analog 2, lpf 1200`: the BASELINE of the instruments' sound.** Each
 * built-in name renders one note through the real `VoiceFactory`, the row's settings sent as `classic()`'s slots,
 * and the render's raw bits are pinned ([rawBitsHash]). It is the one wide guard against an accidental change to
 * any built-in's sound.
 *
 * History: frozen when the voice strip retired (phase 3 step 9, 2026-09-27), as "the strip's sound" through the
 * migration; every row was then bit-identical to the strip except four, the recorded cost of section 8 of
 * KLANG-00237 (builtin instruments) (`perlin`, `berlin` and `crackle` draw from the
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
                val base = VoiceData.empty.copy(freqHz = 220.0, ignitorParams = row.own?.toKlangMap())
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

    "pluck and superpluck at stiffness 0.5: the stiffness allpass, pinned" {
        // The matrix's rows run every built-in, and stiffness moves only the Karplus pair, so this row is its own.
        // It pins the allpass law and its state across blocks, which the rows above (stiffness 0) never reach.
        for (name in listOf("pluck", "superpluck")) {
            val data = VoiceData.empty.copy(freqHz = 220.0, sound = name, ignitorParams = klangMapOf("stiffness" to 0.5))
            val stiff = render(data)
            val hash = stiff.rawBitsHash()

            println("MATRIX-STIFF | \"$name\" to \"$hash\",")

            withClue("$name: the frozen fingerprint") { hash shouldBe STIFF_BASELINE[name] }
            withClue("$name: engagement, the stiffness changes the voice") { stiff.toList() shouldNotBe untouchedOf(name).toList() }
        }
    }
})

/**
 * The pins. Regenerated for the decisions pass of 2026-11-11 (sound changes the maintainer decided, each row by its
 * change): the square family and `sgbuzz` (the square's one-sample flank, KLANG-00430 L3-02), the even-count stacks
 * `supersaw`, `superramp`, `supersine`, `supertri` (both middle voices' jitter scale, L3-12; the supersquare family by
 * both), `brown` (its leak rate-scaled: rounding at 48 kHz, L3-10), `perlin` (the hashed lattice, L3-09) and the Karplus
 * rows (the real pick position and its default 0.28, KLANG-00426 L3-08).
 */
private val BASELINE: Map<String, String> = mapOf(
    "sine | untouched" to "62f1c90e075ecde9",
    "sine | analog 2, lpf 1200: one filter" to "75697152a8a421c9",
    "sin | untouched" to "62f1c90e075ecde9",
    "sin | analog 2, lpf 1200: one filter" to "75697152a8a421c9",
    "sawtooth | untouched" to "21c75446c8c77ca7",
    "sawtooth | analog 2, lpf 1200: one filter" to "f347875e29330c89",
    "saw | untouched" to "21c75446c8c77ca7",
    "saw | analog 2, lpf 1200: one filter" to "f347875e29330c89",
    "square | untouched" to "865d1fc16d85f252",
    "square | analog 2, lpf 1200: one filter" to "3c117a2fffb7006c",
    "sqr | untouched" to "865d1fc16d85f252",
    "sqr | analog 2, lpf 1200: one filter" to "3c117a2fffb7006c",
    "pulse | untouched" to "865d1fc16d85f252",
    "pulse | analog 2, lpf 1200: one filter" to "3c117a2fffb7006c",
    "pulze | untouched" to "ca3cd2328019ff5f",
    "pulze | analog 2, lpf 1200: one filter" to "cc2e12216fd05de2",
    "triangle | untouched" to "51c68cbcfbd8f60b",
    "triangle | analog 2, lpf 1200: one filter" to "9240ef26347b8d8d",
    "tri | untouched" to "51c68cbcfbd8f60b",
    "tri | analog 2, lpf 1200: one filter" to "9240ef26347b8d8d",
    "ramp | untouched" to "21c75446c8c77ca7",
    "ramp | analog 2, lpf 1200: one filter" to "7d7c21fd2e65334a",
    "zamp | untouched" to "8a94685ac34afc37",
    "zamp | analog 2, lpf 1200: one filter" to "29b06af1992e915c",
    "zawtooth | untouched" to "8a94685ac34afc37",
    "zawtooth | analog 2, lpf 1200: one filter" to "a07865d48334c99d",
    "zaw | untouched" to "8a94685ac34afc37",
    "zaw | analog 2, lpf 1200: one filter" to "a07865d48334c99d",
    "impulse | untouched" to "c7dd76444689cb47",
    "impulse | analog 2, lpf 1200: one filter" to "71aaaf289cd32a31",
    "silence | untouched" to "91a125c50dc05500",
    "silence | analog 2, lpf 1200: one filter" to "91a125c50dc05500",
    "supersaw | untouched" to "f4bf7822738d61a7",
    "supersaw | analog 2, lpf 1200: one filter" to "89fecead5731fc66",
    "supersine | untouched" to "5eb541415b6ad2be",
    "supersine | analog 2, lpf 1200: one filter" to "e2c8b629c4f45804",
    "supersquare | untouched" to "63649dd1a162e1ec",
    "supersquare | analog 2, lpf 1200: one filter" to "51be4cc08407a473",
    "supersqr | untouched" to "63649dd1a162e1ec",
    "supersqr | analog 2, lpf 1200: one filter" to "51be4cc08407a473",
    "superpulse | untouched" to "63649dd1a162e1ec",
    "superpulse | analog 2, lpf 1200: one filter" to "51be4cc08407a473",
    "supertri | untouched" to "6cb932789c6db7fb",
    "supertri | analog 2, lpf 1200: one filter" to "8d898bc16de6af28",
    "superramp | untouched" to "f4bf7822738d61a7",
    "superramp | analog 2, lpf 1200: one filter" to "6557b1d011151da5",
    "whitenoise | untouched" to "a56631ea03d23763",
    "whitenoise | analog 2, lpf 1200: one filter" to "50adcb4ac8d0a779",
    "white | untouched" to "a56631ea03d23763",
    "white | analog 2, lpf 1200: one filter" to "50adcb4ac8d0a779",
    "brownnoise | untouched" to "19cfa69bb3bb2646",
    "brownnoise | analog 2, lpf 1200: one filter" to "e5d4ddf0cd274d05",
    "brown | untouched" to "19cfa69bb3bb2646",
    "brown | analog 2, lpf 1200: one filter" to "e5d4ddf0cd274d05",
    "pinknoise | untouched" to "079d43c80e248eb5",
    "pinknoise | analog 2, lpf 1200: one filter" to "6d42451d273b145e",
    "pink | untouched" to "079d43c80e248eb5",
    "pink | analog 2, lpf 1200: one filter" to "6d42451d273b145e",
    "perlinnoise | untouched" to "f529a493ca6902e6",
    "perlinnoise | analog 2, lpf 1200: one filter" to "e2b8bc764f985955",  // diverged from the strip at HEAD: the section 8 draw order
    "perlin | untouched" to "f529a493ca6902e6",
    "perlin | analog 2, lpf 1200: one filter" to "e2b8bc764f985955",  // diverged from the strip at HEAD: the section 8 draw order
    "berlinnoise | untouched" to "9cba8e57e1824000",
    "berlinnoise | analog 2, lpf 1200: one filter" to "2b5412945ef5ebed",  // diverged from the strip at HEAD: the section 8 draw order
    "berlin | untouched" to "9cba8e57e1824000",
    "berlin | analog 2, lpf 1200: one filter" to "2b5412945ef5ebed",  // diverged from the strip at HEAD: the section 8 draw order
    "dust | untouched" to "42b9214f23b04006",
    "dust | analog 2, lpf 1200: one filter" to "923b6705a573bf48",
    "crackle | untouched" to "83695e0e1a22326d",
    "crackle | analog 2, lpf 1200: one filter" to "824e4c8c6af51213",  // diverged from the strip at HEAD: the section 8 draw order
    "pluck | untouched" to "08ad12f0505692f1",
    "pluck | analog 2, lpf 1200: one filter" to "1a59ee5309a1f20e",
    "ks | untouched" to "08ad12f0505692f1",
    "ks | analog 2, lpf 1200: one filter" to "1a59ee5309a1f20e",
    "string | untouched" to "08ad12f0505692f1",
    "string | analog 2, lpf 1200: one filter" to "1a59ee5309a1f20e",
    "superpluck | untouched" to "5589da9e86ce0f8d",
    "superpluck | analog 2, lpf 1200: one filter" to "61adb818f60de8a3",
    "sgpad | untouched" to "aa9b1c4ae8c74283",
    "sgpad | analog 2, lpf 1200: one filter" to "723617ba5fa39851",
    "sgbell | untouched" to "63e5233c49c2d209",
    "sgbell | analog 2, lpf 1200: one filter" to "e10ba8aae71aaf61",
    "sgbuzz | untouched" to "691cb22b0e56c7e4",
    "sgbuzz | analog 2, lpf 1200: one filter" to "90a686fb6c19ba2e",
    "eqdemo | untouched" to "e70faaae5189e96d",
    "eqdemo | analog 2, lpf 1200: one filter" to "daa5a7b87e4f9b77",
)

/**
 * The stiffness row's pins, captured from the code before tidy-up step 11 (`fb24e509`, review round 2), when the string
 * still lived in the two nodes, and regenerated with the Karplus rows of [BASELINE] when the pluck was tuned and its burst
 * lost its DC (KLANG-00426, a sound change the maintainer asked for), and with the saw, square and Karplus rows when
 * the saw and square began to read their shape through a one-sample box and the string's read became cubic (KLANG-00430,
 * audit findings L3-02 and L3-07), and with the Karplus rows when the pluck got its real pick position (KLANG-00426,
 * L3-08, 2026-11-11); regenerate them only for a sound decision.
 */
private val STIFF_BASELINE: Map<String, String> = mapOf(
    "pluck" to "b639c568c82fa317",
    "superpluck" to "0c789ed6c1f713ed",
)
