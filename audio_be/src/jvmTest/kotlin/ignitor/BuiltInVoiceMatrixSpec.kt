/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.voices.DoorAdsr
import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.DoorFilter
import io.peekandpoke.klang.audio_be.voices.DoorFilterEnv
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
 * **Every built-in sound, one voice per stage group: a BASELINE** (signal-flow plan section 12), frozen when the
 * voice strip retired (phase 3 step 9, 2026-09-27). Each built-in name renders one note through the real
 * `VoiceFactory`, the row's settings sent as `classic()`'s slots.
 *
 * Until step 9 this spec rendered each row a second time with the name's SOURCE registered as an authored
 * instrument, so the voice strip ran after it, and compared the two (phase 3 step 6): every row bit-identical,
 * except the recorded cost of section 8 of `docs/tasks/builtin-instruments.md` (`perlin`, `berlin` and `crackle`
 * draw from the voice's stream when CONSTRUCTED, so with `analog > 0` and one filter the tree's filter draw comes
 * after theirs). Before the strip was deleted, every row's built-in render was fingerprinted on the tree that still
 * had it ([rawBitsHash]); those fingerprints are pinned here, regenerated at a listening checkpoint when a change
 * is meant to move a row, never hand-edited, never derived from the tree under test.
 *
 * `ClassicVoiceBaselineSpec` is the deep table (one source, every slot, both rates); this one is wide (every
 * built-in, a row per stage group, one rate). JVM only: about a minute here, several in a browser, past the JS
 * test runner's 30 s no-activity window.
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
        Row("lpf 900 with an envelope", null, DoorFields(filters = listOf(DoorFilter.LowPass(900.0, 1.5, envelope = DoorFilterEnv(decay = 0.1, depth = 18.0))))),
        Row("hpf 300 and lpf 3000", null, DoorFields(filters = listOf(DoorFilter.HighPass(300.0, 0.707), DoorFilter.LowPass(3000.0, 0.707)))),
        Row("adsr 0.02 / 0.1 / 0.4 / 0.1", null, DoorFields(adsr = DoorAdsr(attack = 0.02, decay = 0.1, sustain = 0.4, release = 0.1))),
        Row("adsrOff", null, DoorFields(adsr = DoorAdsr(on = false))),
        Row("tremolo square", null, DoorFields(tremoloDepth = 0.8, tremoloSync = 6.0, tremoloShape = "square")),
        Row("distort 0.6 tube x2", null, DoorFields(distort = 0.6, distortShape = "tube", distortOversample = 2)),
        Row("crush 5", null, DoorFields(crush = 5.0)),
        Row("coarse 3", null, DoorFields(coarse = 3.0)),
        Row("onepole 1500", mapOf("onepole" to 1500.0)),
        Row("analog 2, lpf 1200: one filter", mapOf("analog" to 2.0), DoorFields(filters = listOf(DoorFilter.LowPass(1200.0, 0.707)))),
    )

    /**
     * Rows whose settings cannot change that source, so their engagement check is skipped (identity is
     * still asserted): the raw pulse and the impulse only ever emit values the crush quantizer maps onto
     * themselves.
     */
    val quantizerFixedPoints = setOf("pulze:crush 5", "impulse:crush 5")

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

                if (name != "silence" && row.title != "untouched" && "$name:${row.title}" !in quantizerFixedPoints) {
                    withClue("engagement: the row's settings change the voice") {
                        builtIn.toList() shouldNotBe untouchedOf(name).toList()
                    }
                }
            }
        }
    }

    "every built-in ends in classic(), every one has a fingerprinted row per stage group, and every sounding one is heard" {
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
    "sine | lpf 900 with an envelope" to "9292153b011d6d3c",
    "sine | hpf 300 and lpf 3000" to "4bbedbaf8a2f58ce",
    "sine | adsr 0.02 / 0.1 / 0.4 / 0.1" to "d37883bb1a391b70",
    "sine | adsrOff" to "2e7ff48c9460cec5",
    "sine | tremolo square" to "47e5d3b799c17c76",
    "sine | distort 0.6 tube x2" to "62221f98116d0811",
    "sine | crush 5" to "53b872305edc240b",
    "sine | coarse 3" to "60efa55b98e1a1d4",
    "sine | onepole 1500" to "9e249c29c92533b0",
    "sine | analog 2, lpf 1200: one filter" to "3ca17d8c6bb7707d",
    "sin | untouched" to "5ba82d7f94bbf57a",
    "sin | lpf 900 with an envelope" to "9292153b011d6d3c",
    "sin | hpf 300 and lpf 3000" to "4bbedbaf8a2f58ce",
    "sin | adsr 0.02 / 0.1 / 0.4 / 0.1" to "d37883bb1a391b70",
    "sin | adsrOff" to "2e7ff48c9460cec5",
    "sin | tremolo square" to "47e5d3b799c17c76",
    "sin | distort 0.6 tube x2" to "62221f98116d0811",
    "sin | crush 5" to "53b872305edc240b",
    "sin | coarse 3" to "60efa55b98e1a1d4",
    "sin | onepole 1500" to "9e249c29c92533b0",
    "sin | analog 2, lpf 1200: one filter" to "3ca17d8c6bb7707d",
    "sawtooth | untouched" to "4b669e5216868b15",
    "sawtooth | lpf 900 with an envelope" to "9d77be70ec1fbe65",
    "sawtooth | hpf 300 and lpf 3000" to "c310a2dc7a4341f7",
    "sawtooth | adsr 0.02 / 0.1 / 0.4 / 0.1" to "1ee8d84ba6380458",
    "sawtooth | adsrOff" to "1bfff8ff9d4dedda",
    "sawtooth | tremolo square" to "ae37c458e1c79c7b",
    "sawtooth | distort 0.6 tube x2" to "15cf8eaeaf81a5c7",
    "sawtooth | crush 5" to "19b7f07cbf283269",
    "sawtooth | coarse 3" to "2eb21636ead45d47",
    "sawtooth | onepole 1500" to "5e3b57da74eabf05",
    "sawtooth | analog 2, lpf 1200: one filter" to "a23413763e0b6e67",
    "saw | untouched" to "4b669e5216868b15",
    "saw | lpf 900 with an envelope" to "9d77be70ec1fbe65",
    "saw | hpf 300 and lpf 3000" to "c310a2dc7a4341f7",
    "saw | adsr 0.02 / 0.1 / 0.4 / 0.1" to "1ee8d84ba6380458",
    "saw | adsrOff" to "1bfff8ff9d4dedda",
    "saw | tremolo square" to "ae37c458e1c79c7b",
    "saw | distort 0.6 tube x2" to "15cf8eaeaf81a5c7",
    "saw | crush 5" to "19b7f07cbf283269",
    "saw | coarse 3" to "2eb21636ead45d47",
    "saw | onepole 1500" to "5e3b57da74eabf05",
    "saw | analog 2, lpf 1200: one filter" to "a23413763e0b6e67",
    "square | untouched" to "7c6f7e025547b001",
    "square | lpf 900 with an envelope" to "8acf0d00a2a08e31",
    "square | hpf 300 and lpf 3000" to "2a67b30d89c8fd8e",
    "square | adsr 0.02 / 0.1 / 0.4 / 0.1" to "565cb1f270b60c5f",
    "square | adsrOff" to "aa5840da8267f653",
    "square | tremolo square" to "f2356f0152892550",
    "square | distort 0.6 tube x2" to "a5bfa0d2cd46ad03",
    "square | crush 5" to "6c2bd74ce567515f",
    "square | coarse 3" to "a4695bd49c841abd",
    "square | onepole 1500" to "637887da291c39a9",
    "square | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "sqr | untouched" to "7c6f7e025547b001",
    "sqr | lpf 900 with an envelope" to "8acf0d00a2a08e31",
    "sqr | hpf 300 and lpf 3000" to "2a67b30d89c8fd8e",
    "sqr | adsr 0.02 / 0.1 / 0.4 / 0.1" to "565cb1f270b60c5f",
    "sqr | adsrOff" to "aa5840da8267f653",
    "sqr | tremolo square" to "f2356f0152892550",
    "sqr | distort 0.6 tube x2" to "a5bfa0d2cd46ad03",
    "sqr | crush 5" to "6c2bd74ce567515f",
    "sqr | coarse 3" to "a4695bd49c841abd",
    "sqr | onepole 1500" to "637887da291c39a9",
    "sqr | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "pulse | untouched" to "7c6f7e025547b001",
    "pulse | lpf 900 with an envelope" to "8acf0d00a2a08e31",
    "pulse | hpf 300 and lpf 3000" to "2a67b30d89c8fd8e",
    "pulse | adsr 0.02 / 0.1 / 0.4 / 0.1" to "565cb1f270b60c5f",
    "pulse | adsrOff" to "aa5840da8267f653",
    "pulse | tremolo square" to "f2356f0152892550",
    "pulse | distort 0.6 tube x2" to "a5bfa0d2cd46ad03",
    "pulse | crush 5" to "6c2bd74ce567515f",
    "pulse | coarse 3" to "a4695bd49c841abd",
    "pulse | onepole 1500" to "637887da291c39a9",
    "pulse | analog 2, lpf 1200: one filter" to "012c58eda6fe1568",
    "pulze | untouched" to "dc55ce37022534e8",
    "pulze | lpf 900 with an envelope" to "a237ff4783d574a6",
    "pulze | hpf 300 and lpf 3000" to "31e5aae55ac52542",
    "pulze | adsr 0.02 / 0.1 / 0.4 / 0.1" to "138444c82aeadf85",
    "pulze | adsrOff" to "1ebc3e32c6bd12f9",
    "pulze | tremolo square" to "9b9eee50f6c5536d",
    "pulze | distort 0.6 tube x2" to "a602d487177b3234",
    "pulze | crush 5" to "dc55ce37022534e8",
    "pulze | coarse 3" to "dc55ce37022534e8",
    "pulze | onepole 1500" to "ae867bc78207b040",
    "pulze | analog 2, lpf 1200: one filter" to "9fd309ba072f8a8d",
    "triangle | untouched" to "a8a9de0818149759",
    "triangle | lpf 900 with an envelope" to "d6eb47f75c97cffa",
    "triangle | hpf 300 and lpf 3000" to "70726c7ecb24dc8d",
    "triangle | adsr 0.02 / 0.1 / 0.4 / 0.1" to "46b028b7f23a4cd4",
    "triangle | adsrOff" to "ad06455cdc1bd109",
    "triangle | tremolo square" to "5cb9901e243cf49d",
    "triangle | distort 0.6 tube x2" to "bda1c95ed25a3079",
    "triangle | crush 5" to "82680e29e0608f6a",
    "triangle | coarse 3" to "aa596b1ec317f48f",
    "triangle | onepole 1500" to "f09c6d56c905e0c7",
    "triangle | analog 2, lpf 1200: one filter" to "c2bb1752cf93ccc7",
    "tri | untouched" to "a8a9de0818149759",
    "tri | lpf 900 with an envelope" to "d6eb47f75c97cffa",
    "tri | hpf 300 and lpf 3000" to "70726c7ecb24dc8d",
    "tri | adsr 0.02 / 0.1 / 0.4 / 0.1" to "46b028b7f23a4cd4",
    "tri | adsrOff" to "ad06455cdc1bd109",
    "tri | tremolo square" to "5cb9901e243cf49d",
    "tri | distort 0.6 tube x2" to "bda1c95ed25a3079",
    "tri | crush 5" to "82680e29e0608f6a",
    "tri | coarse 3" to "aa596b1ec317f48f",
    "tri | onepole 1500" to "f09c6d56c905e0c7",
    "tri | analog 2, lpf 1200: one filter" to "c2bb1752cf93ccc7",
    "ramp | untouched" to "cb669e5296868b15",
    "ramp | lpf 900 with an envelope" to "1d77be706c1fbe65",
    "ramp | hpf 300 and lpf 3000" to "4310a2dcfa4341f7",
    "ramp | adsr 0.02 / 0.1 / 0.4 / 0.1" to "9ee8d84b26380458",
    "ramp | adsrOff" to "9bfff8ff1d4dedda",
    "ramp | tremolo square" to "2e37c45861c79c7b",
    "ramp | distort 0.6 tube x2" to "bd3db8074248eb12",
    "ramp | crush 5" to "7d3e60e7d338594a",
    "ramp | coarse 3" to "aeb216366ad45d47",
    "ramp | onepole 1500" to "de3b57daf4eabf05",
    "ramp | analog 2, lpf 1200: one filter" to "d64b31a7458ede38",
    "zamp | untouched" to "2c4854b53f9dfcb6",
    "zamp | lpf 900 with an envelope" to "1036851659dc967d",
    "zamp | hpf 300 and lpf 3000" to "a1169bca03f32897",
    "zamp | adsr 0.02 / 0.1 / 0.4 / 0.1" to "c496bc600cc1c78b",
    "zamp | adsrOff" to "f018694da3b7f30c",
    "zamp | tremolo square" to "a221dbbd9720ec30",
    "zamp | distort 0.6 tube x2" to "bd5bc783d0bffcd0",
    "zamp | crush 5" to "3086438cc496226f",
    "zamp | coarse 3" to "521e30937168ffc8",
    "zamp | onepole 1500" to "ef04d631410c7cba",
    "zamp | analog 2, lpf 1200: one filter" to "51c17f5ba96e011c",
    "zawtooth | untouched" to "ac4854b5bf9dfcb6",
    "zawtooth | lpf 900 with an envelope" to "90368516d9dc967d",
    "zawtooth | hpf 300 and lpf 3000" to "21169bca83f32897",
    "zawtooth | adsr 0.02 / 0.1 / 0.4 / 0.1" to "4496bc608cc1c78b",
    "zawtooth | adsrOff" to "7018694d23b7f30c",
    "zawtooth | tremolo square" to "2221dbbd1720ec30",
    "zawtooth | distort 0.6 tube x2" to "66e01e7c30008bcb",
    "zawtooth | crush 5" to "9aee9d20d758cb5f",
    "zawtooth | coarse 3" to "d21e3093f168ffc8",
    "zawtooth | onepole 1500" to "6f04d631c10c7cba",
    "zawtooth | analog 2, lpf 1200: one filter" to "c1c53ee5afde889c",
    "zaw | untouched" to "ac4854b5bf9dfcb6",
    "zaw | lpf 900 with an envelope" to "90368516d9dc967d",
    "zaw | hpf 300 and lpf 3000" to "21169bca83f32897",
    "zaw | adsr 0.02 / 0.1 / 0.4 / 0.1" to "4496bc608cc1c78b",
    "zaw | adsrOff" to "7018694d23b7f30c",
    "zaw | tremolo square" to "2221dbbd1720ec30",
    "zaw | distort 0.6 tube x2" to "66e01e7c30008bcb",
    "zaw | crush 5" to "9aee9d20d758cb5f",
    "zaw | coarse 3" to "d21e3093f168ffc8",
    "zaw | onepole 1500" to "6f04d631c10c7cba",
    "zaw | analog 2, lpf 1200: one filter" to "c1c53ee5afde889c",
    "impulse | untouched" to "362b71493e0046e6",
    "impulse | lpf 900 with an envelope" to "a21899f027cd8413",
    "impulse | hpf 300 and lpf 3000" to "8d1e175172f400b8",
    "impulse | adsr 0.02 / 0.1 / 0.4 / 0.1" to "40136ea49fcb2fa3",
    "impulse | adsrOff" to "e8bcf3090b2fbade",
    "impulse | tremolo square" to "35f7e47adbd5a85b",
    "impulse | distort 0.6 tube x2" to "71a0443436b0e2bf",
    "impulse | crush 5" to "362b71493e0046e6",
    "impulse | coarse 3" to "5fe73b9c16f70b17",
    "impulse | onepole 1500" to "881d34780568f1bd",
    "impulse | analog 2, lpf 1200: one filter" to "5fa938c5cffbbd36",
    "silence | untouched" to "91a125c50dc05500",
    "silence | lpf 900 with an envelope" to "91a125c50dc05500",
    "silence | hpf 300 and lpf 3000" to "91a125c50dc05500",
    "silence | adsr 0.02 / 0.1 / 0.4 / 0.1" to "91a125c50dc05500",
    "silence | adsrOff" to "91a125c50dc05500",
    "silence | tremolo square" to "91a125c50dc05500",
    "silence | distort 0.6 tube x2" to "91a125c50dc05500",
    "silence | crush 5" to "91a125c50dc05500",
    "silence | coarse 3" to "91a125c50dc05500",
    "silence | onepole 1500" to "91a125c50dc05500",
    "silence | analog 2, lpf 1200: one filter" to "91a125c50dc05500",
    "supersaw | untouched" to "161dac49c5e840ee",
    "supersaw | lpf 900 with an envelope" to "a3c25e1d95ed50ce",
    "supersaw | hpf 300 and lpf 3000" to "475e88b3847440b8",
    "supersaw | adsr 0.02 / 0.1 / 0.4 / 0.1" to "8d2a385ea32e46cf",
    "supersaw | adsrOff" to "12225ccdbfd5c650",
    "supersaw | tremolo square" to "32f4b7461687de31",
    "supersaw | distort 0.6 tube x2" to "94b6ff2c28e80ae9",
    "supersaw | crush 5" to "23de304e5e6ae131",
    "supersaw | coarse 3" to "0fe8b6d7d49abeb2",
    "supersaw | onepole 1500" to "e35dd8ea4f2fe80b",
    "supersaw | analog 2, lpf 1200: one filter" to "8e3a1389fa17e37c",
    "supersine | untouched" to "ed2a586ad38c0bf9",
    "supersine | lpf 900 with an envelope" to "57a880630a6133ce",
    "supersine | hpf 300 and lpf 3000" to "99a1e6fb15b8cd60",
    "supersine | adsr 0.02 / 0.1 / 0.4 / 0.1" to "f49af8ae07e46b29",
    "supersine | adsrOff" to "e48708992ebfc310",
    "supersine | tremolo square" to "268cd61553ed40d6",
    "supersine | distort 0.6 tube x2" to "253365b27132f317",
    "supersine | crush 5" to "d2e5e37239510177",
    "supersine | coarse 3" to "b2c526cf3a3bc17e",
    "supersine | onepole 1500" to "25d38372639d339f",
    "supersine | analog 2, lpf 1200: one filter" to "eb033afc9053a6bb",
    "supersquare | untouched" to "99f54f81e86e9498",
    "supersquare | lpf 900 with an envelope" to "55cea7c585c3ccac",
    "supersquare | hpf 300 and lpf 3000" to "c69a2b11aa06e7c2",
    "supersquare | adsr 0.02 / 0.1 / 0.4 / 0.1" to "e3f976aa2b17cc07",
    "supersquare | adsrOff" to "62e93687b13a5d42",
    "supersquare | tremolo square" to "cb7f0b475ddd681e",
    "supersquare | distort 0.6 tube x2" to "e001e6140e8da309",
    "supersquare | crush 5" to "2c8a4b94a855fa53",
    "supersquare | coarse 3" to "b7f9551e819eeb6d",
    "supersquare | onepole 1500" to "346d7d9ab2ef38e3",
    "supersquare | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "supersqr | untouched" to "99f54f81e86e9498",
    "supersqr | lpf 900 with an envelope" to "55cea7c585c3ccac",
    "supersqr | hpf 300 and lpf 3000" to "c69a2b11aa06e7c2",
    "supersqr | adsr 0.02 / 0.1 / 0.4 / 0.1" to "e3f976aa2b17cc07",
    "supersqr | adsrOff" to "62e93687b13a5d42",
    "supersqr | tremolo square" to "cb7f0b475ddd681e",
    "supersqr | distort 0.6 tube x2" to "e001e6140e8da309",
    "supersqr | crush 5" to "2c8a4b94a855fa53",
    "supersqr | coarse 3" to "b7f9551e819eeb6d",
    "supersqr | onepole 1500" to "346d7d9ab2ef38e3",
    "supersqr | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "superpulse | untouched" to "99f54f81e86e9498",
    "superpulse | lpf 900 with an envelope" to "55cea7c585c3ccac",
    "superpulse | hpf 300 and lpf 3000" to "c69a2b11aa06e7c2",
    "superpulse | adsr 0.02 / 0.1 / 0.4 / 0.1" to "e3f976aa2b17cc07",
    "superpulse | adsrOff" to "62e93687b13a5d42",
    "superpulse | tremolo square" to "cb7f0b475ddd681e",
    "superpulse | distort 0.6 tube x2" to "e001e6140e8da309",
    "superpulse | crush 5" to "2c8a4b94a855fa53",
    "superpulse | coarse 3" to "b7f9551e819eeb6d",
    "superpulse | onepole 1500" to "346d7d9ab2ef38e3",
    "superpulse | analog 2, lpf 1200: one filter" to "7cc7034d5d934930",
    "supertri | untouched" to "fde7abdc2c88f63f",
    "supertri | lpf 900 with an envelope" to "7bdd7b329d203da1",
    "supertri | hpf 300 and lpf 3000" to "c42bef9f8d43e6a0",
    "supertri | adsr 0.02 / 0.1 / 0.4 / 0.1" to "7b6aac89329c70ea",
    "supertri | adsrOff" to "b01968047c860231",
    "supertri | tremolo square" to "fa87bd8dd3eb23f2",
    "supertri | distort 0.6 tube x2" to "7e6d81eafa97bb07",
    "supertri | crush 5" to "dbb45254cb728fa9",
    "supertri | coarse 3" to "77405897cde43a66",
    "supertri | onepole 1500" to "c7ca89488ddd7eb1",
    "supertri | analog 2, lpf 1200: one filter" to "e556acfbb120e6e6",
    "superramp | untouched" to "961dac4945e840ee",
    "superramp | lpf 900 with an envelope" to "23c25e1d15ed50ce",
    "superramp | hpf 300 and lpf 3000" to "c75e88b3047440b8",
    "superramp | adsr 0.02 / 0.1 / 0.4 / 0.1" to "0d2a385e232e46cf",
    "superramp | adsrOff" to "92225ccd3fd5c650",
    "superramp | tremolo square" to "b2f4b7469687de31",
    "superramp | distort 0.6 tube x2" to "9013ca8ce6a6ec71",
    "superramp | crush 5" to "68ce9f8964b6a4c2",
    "superramp | coarse 3" to "8fe8b6d7549abeb2",
    "superramp | onepole 1500" to "635dd8eacf2fe80b",
    "superramp | analog 2, lpf 1200: one filter" to "f3cd9a8f425df2ee",
    "whitenoise | untouched" to "690f4b2a70bce931",
    "whitenoise | lpf 900 with an envelope" to "1b09f3c35bbf36fa",
    "whitenoise | hpf 300 and lpf 3000" to "6ff4dff214d86453",
    "whitenoise | adsr 0.02 / 0.1 / 0.4 / 0.1" to "04b9b2c3a3516254",
    "whitenoise | adsrOff" to "25e0dc2cc4b49b45",
    "whitenoise | tremolo square" to "bcb1891dd4e547e0",
    "whitenoise | distort 0.6 tube x2" to "86bb64eda4689e42",
    "whitenoise | crush 5" to "a5619f72f2fe24b5",
    "whitenoise | coarse 3" to "b637a1a237685e07",
    "whitenoise | onepole 1500" to "3b151d1f082ffc2e",
    "whitenoise | analog 2, lpf 1200: one filter" to "2979ac7198287352",
    "white | untouched" to "690f4b2a70bce931",
    "white | lpf 900 with an envelope" to "1b09f3c35bbf36fa",
    "white | hpf 300 and lpf 3000" to "6ff4dff214d86453",
    "white | adsr 0.02 / 0.1 / 0.4 / 0.1" to "04b9b2c3a3516254",
    "white | adsrOff" to "25e0dc2cc4b49b45",
    "white | tremolo square" to "bcb1891dd4e547e0",
    "white | distort 0.6 tube x2" to "86bb64eda4689e42",
    "white | crush 5" to "a5619f72f2fe24b5",
    "white | coarse 3" to "b637a1a237685e07",
    "white | onepole 1500" to "3b151d1f082ffc2e",
    "white | analog 2, lpf 1200: one filter" to "2979ac7198287352",
    "brownnoise | untouched" to "19887512478f2b25",
    "brownnoise | lpf 900 with an envelope" to "e284d0eba1e2f17a",
    "brownnoise | hpf 300 and lpf 3000" to "3e83efd4726bdd59",
    "brownnoise | adsr 0.02 / 0.1 / 0.4 / 0.1" to "87600384d8dcfe9b",
    "brownnoise | adsrOff" to "36d1419b84a6a52e",
    "brownnoise | tremolo square" to "3a46b432140e2d77",
    "brownnoise | distort 0.6 tube x2" to "7edc76c52d00b134",
    "brownnoise | crush 5" to "0c3b757c8962ade1",
    "brownnoise | coarse 3" to "0c52b249fdeeb6fc",
    "brownnoise | onepole 1500" to "cd4892beb3aeec61",
    "brownnoise | analog 2, lpf 1200: one filter" to "8a97be42012984b1",
    "brown | untouched" to "19887512478f2b25",
    "brown | lpf 900 with an envelope" to "e284d0eba1e2f17a",
    "brown | hpf 300 and lpf 3000" to "3e83efd4726bdd59",
    "brown | adsr 0.02 / 0.1 / 0.4 / 0.1" to "87600384d8dcfe9b",
    "brown | adsrOff" to "36d1419b84a6a52e",
    "brown | tremolo square" to "3a46b432140e2d77",
    "brown | distort 0.6 tube x2" to "7edc76c52d00b134",
    "brown | crush 5" to "0c3b757c8962ade1",
    "brown | coarse 3" to "0c52b249fdeeb6fc",
    "brown | onepole 1500" to "cd4892beb3aeec61",
    "brown | analog 2, lpf 1200: one filter" to "8a97be42012984b1",
    "pinknoise | untouched" to "b282af068c92d995",
    "pinknoise | lpf 900 with an envelope" to "af74ca30bcd9f5f1",
    "pinknoise | hpf 300 and lpf 3000" to "4981001557683bae",
    "pinknoise | adsr 0.02 / 0.1 / 0.4 / 0.1" to "32b4bbb3c3403e5a",
    "pinknoise | adsrOff" to "50b6c84fcac1c338",
    "pinknoise | tremolo square" to "19ce8e85c7a3e364",
    "pinknoise | distort 0.6 tube x2" to "25f576ca53aa4159",
    "pinknoise | crush 5" to "b8bf84cbbadf7078",
    "pinknoise | coarse 3" to "e4131bfec2af66ef",
    "pinknoise | onepole 1500" to "1e5a0dbfb7655fa4",
    "pinknoise | analog 2, lpf 1200: one filter" to "2b46442da51532ea",
    "pink | untouched" to "b282af068c92d995",
    "pink | lpf 900 with an envelope" to "af74ca30bcd9f5f1",
    "pink | hpf 300 and lpf 3000" to "4981001557683bae",
    "pink | adsr 0.02 / 0.1 / 0.4 / 0.1" to "32b4bbb3c3403e5a",
    "pink | adsrOff" to "50b6c84fcac1c338",
    "pink | tremolo square" to "19ce8e85c7a3e364",
    "pink | distort 0.6 tube x2" to "25f576ca53aa4159",
    "pink | crush 5" to "b8bf84cbbadf7078",
    "pink | coarse 3" to "e4131bfec2af66ef",
    "pink | onepole 1500" to "1e5a0dbfb7655fa4",
    "pink | analog 2, lpf 1200: one filter" to "2b46442da51532ea",
    "perlinnoise | untouched" to "051f8f1412f4ca67",
    "perlinnoise | lpf 900 with an envelope" to "d510fe2d13e86542",
    "perlinnoise | hpf 300 and lpf 3000" to "1ef92c513a7e1d60",
    "perlinnoise | adsr 0.02 / 0.1 / 0.4 / 0.1" to "6dbc74303757774f",
    "perlinnoise | adsrOff" to "e2c77682b55bb6ef",
    "perlinnoise | tremolo square" to "2461069e722cb1f3",
    "perlinnoise | distort 0.6 tube x2" to "a0bf8b28051238cb",
    "perlinnoise | crush 5" to "dda9e5de82f4ff9d",
    "perlinnoise | coarse 3" to "3d74e260c23f984d",
    "perlinnoise | onepole 1500" to "c1ee49033b1c5518",
    "perlinnoise | analog 2, lpf 1200: one filter" to "40facc761b9705b5",  // diverged from the strip at HEAD: the section 8 draw order
    "perlin | untouched" to "051f8f1412f4ca67",
    "perlin | lpf 900 with an envelope" to "d510fe2d13e86542",
    "perlin | hpf 300 and lpf 3000" to "1ef92c513a7e1d60",
    "perlin | adsr 0.02 / 0.1 / 0.4 / 0.1" to "6dbc74303757774f",
    "perlin | adsrOff" to "e2c77682b55bb6ef",
    "perlin | tremolo square" to "2461069e722cb1f3",
    "perlin | distort 0.6 tube x2" to "a0bf8b28051238cb",
    "perlin | crush 5" to "dda9e5de82f4ff9d",
    "perlin | coarse 3" to "3d74e260c23f984d",
    "perlin | onepole 1500" to "c1ee49033b1c5518",
    "perlin | analog 2, lpf 1200: one filter" to "40facc761b9705b5",  // diverged from the strip at HEAD: the section 8 draw order
    "berlinnoise | untouched" to "084bf568d0361049",
    "berlinnoise | lpf 900 with an envelope" to "806d0d03fa147d56",
    "berlinnoise | hpf 300 and lpf 3000" to "25424c5d2a543c42",
    "berlinnoise | adsr 0.02 / 0.1 / 0.4 / 0.1" to "ad8d3d2387d25308",
    "berlinnoise | adsrOff" to "57e6c7ece2b4bddb",
    "berlinnoise | tremolo square" to "dbb53e9d51548e1c",
    "berlinnoise | distort 0.6 tube x2" to "da79c7d7ba4bdece",
    "berlinnoise | crush 5" to "04a454d767f4928a",
    "berlinnoise | coarse 3" to "dd38e275e640fbc4",
    "berlinnoise | onepole 1500" to "1997206b0540c78e",
    "berlinnoise | analog 2, lpf 1200: one filter" to "783af6355564ecba",  // diverged from the strip at HEAD: the section 8 draw order
    "berlin | untouched" to "084bf568d0361049",
    "berlin | lpf 900 with an envelope" to "806d0d03fa147d56",
    "berlin | hpf 300 and lpf 3000" to "25424c5d2a543c42",
    "berlin | adsr 0.02 / 0.1 / 0.4 / 0.1" to "ad8d3d2387d25308",
    "berlin | adsrOff" to "57e6c7ece2b4bddb",
    "berlin | tremolo square" to "dbb53e9d51548e1c",
    "berlin | distort 0.6 tube x2" to "da79c7d7ba4bdece",
    "berlin | crush 5" to "04a454d767f4928a",
    "berlin | coarse 3" to "dd38e275e640fbc4",
    "berlin | onepole 1500" to "1997206b0540c78e",
    "berlin | analog 2, lpf 1200: one filter" to "783af6355564ecba",  // diverged from the strip at HEAD: the section 8 draw order
    "dust | untouched" to "14bc02d96c31e662",
    "dust | lpf 900 with an envelope" to "6baec1090b2ed62c",
    "dust | hpf 300 and lpf 3000" to "d65c88c76c4b7522",
    "dust | adsr 0.02 / 0.1 / 0.4 / 0.1" to "1c056919cd64e204",
    "dust | adsrOff" to "e93aadc2703c9979",
    "dust | tremolo square" to "b3d8d6f0d54b016f",
    "dust | distort 0.6 tube x2" to "a25d7874c09e2715",
    "dust | crush 5" to "b54e0cf08f4a58e5",
    "dust | coarse 3" to "5815a141e88524f2",
    "dust | onepole 1500" to "f755528cfa3dc4c7",
    "dust | analog 2, lpf 1200: one filter" to "91d27b4249e12a49",
    "crackle | untouched" to "b33d221dd774d76a",
    "crackle | lpf 900 with an envelope" to "8a2a35ed1ee1270a",
    "crackle | hpf 300 and lpf 3000" to "176c168afa2afcc7",
    "crackle | adsr 0.02 / 0.1 / 0.4 / 0.1" to "9f7f4778bfcc323d",
    "crackle | adsrOff" to "d2d1f17babf2f96a",
    "crackle | tremolo square" to "db6a079231d9e563",
    "crackle | distort 0.6 tube x2" to "db736f84bf45c64f",
    "crackle | crush 5" to "1ff9f7034d4c7964",
    "crackle | coarse 3" to "ca9d76eccab4049d",
    "crackle | onepole 1500" to "66c07e0939568748",
    "crackle | analog 2, lpf 1200: one filter" to "f2401f7da408d430",  // diverged from the strip at HEAD: the section 8 draw order
    "pluck | untouched" to "d97ca175b17e19fa",
    "pluck | lpf 900 with an envelope" to "5a9aeb373d5abc2a",
    "pluck | hpf 300 and lpf 3000" to "7efcc8d6314dcb77",
    "pluck | adsr 0.02 / 0.1 / 0.4 / 0.1" to "4d4ba673fb570720",
    "pluck | adsrOff" to "f305a909b970b8ac",
    "pluck | tremolo square" to "df90f2cc816fb05f",
    "pluck | distort 0.6 tube x2" to "5cc5823a270c3c4f",
    "pluck | crush 5" to "475b738344b0fc76",
    "pluck | coarse 3" to "282ef07ed1303fbb",
    "pluck | onepole 1500" to "12f70e8f30e8b8f4",
    "pluck | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "ks | untouched" to "d97ca175b17e19fa",
    "ks | lpf 900 with an envelope" to "5a9aeb373d5abc2a",
    "ks | hpf 300 and lpf 3000" to "7efcc8d6314dcb77",
    "ks | adsr 0.02 / 0.1 / 0.4 / 0.1" to "4d4ba673fb570720",
    "ks | adsrOff" to "f305a909b970b8ac",
    "ks | tremolo square" to "df90f2cc816fb05f",
    "ks | distort 0.6 tube x2" to "5cc5823a270c3c4f",
    "ks | crush 5" to "475b738344b0fc76",
    "ks | coarse 3" to "282ef07ed1303fbb",
    "ks | onepole 1500" to "12f70e8f30e8b8f4",
    "ks | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "string | untouched" to "d97ca175b17e19fa",
    "string | lpf 900 with an envelope" to "5a9aeb373d5abc2a",
    "string | hpf 300 and lpf 3000" to "7efcc8d6314dcb77",
    "string | adsr 0.02 / 0.1 / 0.4 / 0.1" to "4d4ba673fb570720",
    "string | adsrOff" to "f305a909b970b8ac",
    "string | tremolo square" to "df90f2cc816fb05f",
    "string | distort 0.6 tube x2" to "5cc5823a270c3c4f",
    "string | crush 5" to "475b738344b0fc76",
    "string | coarse 3" to "282ef07ed1303fbb",
    "string | onepole 1500" to "12f70e8f30e8b8f4",
    "string | analog 2, lpf 1200: one filter" to "db563b1f9d303874",
    "superpluck | untouched" to "1fb765b8ac225e5d",
    "superpluck | lpf 900 with an envelope" to "afb016a188aa722c",
    "superpluck | hpf 300 and lpf 3000" to "1d43d35bb55f8b6e",
    "superpluck | adsr 0.02 / 0.1 / 0.4 / 0.1" to "c882c74345733cb6",
    "superpluck | adsrOff" to "090543cdd8575e02",
    "superpluck | tremolo square" to "209920a5f59cf67a",
    "superpluck | distort 0.6 tube x2" to "dd17ebc44f64df05",
    "superpluck | crush 5" to "d41190cd0a9ce128",
    "superpluck | coarse 3" to "bfd5dc1b081264c0",
    "superpluck | onepole 1500" to "b2c9fe1784761ea2",
    "superpluck | analog 2, lpf 1200: one filter" to "3fefd5bb04cf3a90",
    "sgpad | untouched" to "06c94960732c7ff5",
    "sgpad | lpf 900 with an envelope" to "5d642b59721bbfd0",
    "sgpad | hpf 300 and lpf 3000" to "1cf27593fdd274c2",
    "sgpad | adsr 0.02 / 0.1 / 0.4 / 0.1" to "ac2d6ad0b0c69c45",
    "sgpad | adsrOff" to "3f78b48ac96c8aa1",
    "sgpad | tremolo square" to "7d39fe4b0a08b1f8",
    "sgpad | distort 0.6 tube x2" to "52633e355356eff8",
    "sgpad | crush 5" to "0ade5a793efc87c0",
    "sgpad | coarse 3" to "a9545b952a4726f2",
    "sgpad | onepole 1500" to "9c62049f1816253c",
    "sgpad | analog 2, lpf 1200: one filter" to "da450286ec2b7af3",
    "sgbell | untouched" to "c6e219205c09ba37",
    "sgbell | lpf 900 with an envelope" to "9f75057f988c11a2",
    "sgbell | hpf 300 and lpf 3000" to "d37b3e86f93758df",
    "sgbell | adsr 0.02 / 0.1 / 0.4 / 0.1" to "c5cea2b1968264c6",
    "sgbell | adsrOff" to "06c8c34b936e9c3c",
    "sgbell | tremolo square" to "2331f06c3e6e1497",
    "sgbell | distort 0.6 tube x2" to "ed7fe0db0c7a0d8c",
    "sgbell | crush 5" to "16b6025f1679226a",
    "sgbell | coarse 3" to "4ef231114a23b9f6",
    "sgbell | onepole 1500" to "76c8a0da92bc7a6f",
    "sgbell | analog 2, lpf 1200: one filter" to "fc7fccabc1942154",
    "sgbuzz | untouched" to "3612c7b2e99b9f9f",
    "sgbuzz | lpf 900 with an envelope" to "20b3c3cab5523a27",
    "sgbuzz | hpf 300 and lpf 3000" to "83a585fec1c94c7d",
    "sgbuzz | adsr 0.02 / 0.1 / 0.4 / 0.1" to "e1ef9c20416b1909",
    "sgbuzz | adsrOff" to "6f4deae5bca6fb56",
    "sgbuzz | tremolo square" to "dbe52e5391df4886",
    "sgbuzz | distort 0.6 tube x2" to "2eae5afa65d4691f",
    "sgbuzz | crush 5" to "3c97f531cecf9d8e",
    "sgbuzz | coarse 3" to "7e50e6b2c7d8eb35",
    "sgbuzz | onepole 1500" to "237f4b1740b13388",
    "sgbuzz | analog 2, lpf 1200: one filter" to "5a637a5d4dc066a2",
    "eqdemo | untouched" to "f58847ba293b4ac3",
    "eqdemo | lpf 900 with an envelope" to "89bb5b3720f0645e",
    "eqdemo | hpf 300 and lpf 3000" to "f4fb4161b8200aa4",
    "eqdemo | adsr 0.02 / 0.1 / 0.4 / 0.1" to "387810dfda9fd206",
    "eqdemo | adsrOff" to "4d3f88dfd36f56ae",
    "eqdemo | tremolo square" to "6540fd84bb1c0af9",
    "eqdemo | distort 0.6 tube x2" to "54ce15aab2045117",
    "eqdemo | crush 5" to "e49bf2133897fc0c",
    "eqdemo | coarse 3" to "6133a364530a4793",
    "eqdemo | onepole 1500" to "d10266fd15ec3888",
    "eqdemo | analog 2, lpf 1200: one filter" to "9fbb80fd1c8e9206",
)
