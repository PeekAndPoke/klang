/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **The built-in `saw` on `classic()`, one voice per slot row: a BASELINE** (signal-flow plan section 12), frozen
 * when the voice strip retired (phase 3 step 9, 2026-09-27).
 *
 * Until step 9 these rows were `ClassicStripParitySpec`, a migration fixture that rendered each row through the old
 * voice strip and through `classic()` and compared them in raw bits. Before the strip was deleted, every row's
 * `classic()` render (the slots written by hand, the BAG column) was fingerprinted at 48 and 44.1 kHz on the tree
 * that still had the strip, where every row but one (the analog draw order, section 8 of the task record) was
 * bit-identical to the strip. Those fingerprints are pinned here ([rawBitsHash]). They are REGENERATED at a
 * listening checkpoint when a change is meant to move a row, never hand-edited, and never derived from the tree
 * under test.
 *
 * Trimmed by the test consolidation (2026-09-28) from 61 rows to 37 (the selection rule: [ClassicVoiceRig]'s
 * `rows`); every kept fingerprint is the one frozen at step 9, untouched.
 *
 * JVM only: the fingerprints are raw bits, and Kotlin/JS math rounds differently (a whole-number double also prints
 * differently in a row's title). The rows, the render and the contract rows that hold on both platforms are
 * [ClassicVoiceRig]'s and `ClassicVoiceContractSpec`'s.
 */
class ClassicVoiceBaselineSpec : StringSpec({

    with(ClassicVoiceRig) {
        "every row has a fingerprint, and every fingerprint a row" {
            val keys = rates.flatMap { rate -> rows.map { "$rate | ${it.title}" } }

            keys.toSet().size shouldBe keys.size
            BASELINE.keys shouldBe keys.toSet()
        }

        for (rate in rates) {
            for (row in rows) {
                "[$rate Hz] ${row.title}" {
                    val hash = classic(row.bag, rate, voice = row.voice).rawBitsHash()

                    println("CLASSIC-BASELINE | \"$rate | ${row.title}\" to \"$hash\",")

                    withClue("the frozen fingerprint") { hash shouldBe BASELINE["$rate | ${row.title}"] }
                }
            }
        }
    }
})

private val BASELINE: Map<String, String> = mapOf(
    "48000 | untouched: the envelope alone, at the voice envelope's defaults" to "4a152e52468f6415",
    "48000 | crush 4" to "e0798c876189d23e",
    "48000 | coarse 3" to "2217c636279cb647",
    "48000 | coarse 7.5" to "6d201a01fef6c46a",
    "48000 | distort 0.5 soft x0" to "b7e74148872bfab1",
    "48000 | distort 0.5 gentle x0" to "b0cee253e28c9c6e",
    "48000 | distort 1.0 hard x0" to "e7a0a3e93d414718",
    "48000 | distort 1.0 fold x0" to "8398fa942f94ca87",
    "48000 | distort 0.5 rectify x0" to "0ad780bfdaf5f6e6",
    "48000 | distort 0.5 tube x4" to "b6f2a55f86ee4ab0",
    "48000 | hpf 400 q 3 passes 2" to "3578e28bb4024444",
    "48000 | bpf 1000 q 2" to "ce5682317e3bcbd4",
    "48000 | notch 1000 q 4" to "a35806bd22578ce4",
    "48000 | lpf 1200 q 3 passes 3" to "6e03261b2634bd4e",
    "48000 | lpf attack only: the slot-layer fill (its law is FilterSlotLayerFillSpec's)" to "9889d9bc43516d59",
    "48000 | lpf pluck env 24 decay 0.2 sustain 0.1" to "0e1e1cc0f4b8aee3",
    "48000 | hpf env -12 decay 0.2 sustain 0.3" to "fe70d209bab6c378",
    "48000 | bpf env 12" to "a285bc02cbdf670d",
    "48000 | notch env 12" to "12d66ae63e8be019",
    "48000 | lpfCurves linear, scurve, invsquare (env 24, every stage curved)" to "cb35ec47c1466fda",
    "48000 | lpf env 24 q 3 passes 3, a step envelope: the sweep forwarded to every stage of the cascade" to "6b3fc5052bcbb5ba",
    "48000 | analog 2, lpf env 24 q 3, a step envelope: the sweep through the saturated branch with the drift" to "f0fd107cb0f4fe1d",
    "48000 | analog 2, hpf env 24 q 3, a step envelope: the sweep through the saturated branch with the drift" to "00c4d43581c8910c",
    "48000 | analog 2, no filter: the oscillator drift alone" to "8a684f01eec74ef2",
    "48000 | analog 2, hpf and lpf: the draw order (section 8)" to "f325acfb5012acea",  // diverged from the strip at HEAD: the section 8 draw order
    "48000 | tremolo depth 0.5 sync 4" to "d9e91547714977e6",
    "48000 | tremolo square, skew 0.3, phase 0.25" to "330b08a87dd4b05b",
    "48000 | adsr 0.005 / 0.2 / 0.5 / 0.2 (whole frame counts at 48 kHz, 220.5 attack frames at 44.1 kHz)" to "5810bc6fc071e108",
    "48000 | adsr at fractional frame counts (one envelope law: fractional attack and decay on both hosts)" to "743d198a7b6dbbb3",
    "48000 | adsrCurves linear, scurve, square" to "16dc7e597051c6f8",
    "48000 | adsr release -0.1: a zero-length release stage, and the voice still lives to its gate (the lifetime floored at 0)" to "12ce01b88d72a343",
    "48000 | adsrOff: the teardown fade, one law on both hosts (step 6)" to "9a19f0ff101386da",
    "48000 | adsrOff with release 0.2: the fade over a longer tail" to "4e29a32327ff56de",
    "48000 | onepole 900 with crush 5: in front of the quantizer" to "b86ea197743f4dea",
    "48000 | vibrato and FM from the voice's pitch pipeline, under lpf env and hpf" to "2bde2ec9d942747a",
    "48000 | coarse, hpf, lpf, tremolo and the envelope together" to "6b80a963fca62b66",
    "48000 | crush and distort in the chain" to "6558b6692867fc2c",
    "44100 | untouched: the envelope alone, at the voice envelope's defaults" to "5082a80592dfcc90",
    "44100 | crush 4" to "85b8965c9f79f8e9",
    "44100 | coarse 3" to "ec184948c1050607",
    "44100 | coarse 7.5" to "a3e6ebbb327eedce",
    "44100 | distort 0.5 soft x0" to "01ee78457880d932",
    "44100 | distort 0.5 gentle x0" to "04c45427889c32a8",
    "44100 | distort 1.0 hard x0" to "8f81dd1300da7b5c",
    "44100 | distort 1.0 fold x0" to "b87a30bf7411ce90",
    "44100 | distort 0.5 rectify x0" to "4d2cdf36b013dce7",
    "44100 | distort 0.5 tube x4" to "c4a96de2880bd3ed",
    "44100 | hpf 400 q 3 passes 2" to "9ce1cc494a681712",
    "44100 | bpf 1000 q 2" to "759ad1dd264b026e",
    "44100 | notch 1000 q 4" to "0985f2d566f0ce68",
    "44100 | lpf 1200 q 3 passes 3" to "61692e7c753d02e1",
    "44100 | lpf attack only: the slot-layer fill (its law is FilterSlotLayerFillSpec's)" to "be6dd0596a715486",
    "44100 | lpf pluck env 24 decay 0.2 sustain 0.1" to "d7b62ff052742447",
    "44100 | hpf env -12 decay 0.2 sustain 0.3" to "efafe56d3a3bc586",
    "44100 | bpf env 12" to "a1bab285b3a21d8a",
    "44100 | notch env 12" to "95b628b648f53c17",
    "44100 | lpfCurves linear, scurve, invsquare (env 24, every stage curved)" to "4354f5ec3725e491",
    "44100 | lpf env 24 q 3 passes 3, a step envelope: the sweep forwarded to every stage of the cascade" to "46fc2d797d74a482",
    "44100 | analog 2, lpf env 24 q 3, a step envelope: the sweep through the saturated branch with the drift" to "6a107adfa4a816dc",
    "44100 | analog 2, hpf env 24 q 3, a step envelope: the sweep through the saturated branch with the drift" to "50ce6e4e5c81eff9",
    "44100 | analog 2, no filter: the oscillator drift alone" to "4921a694bfc181bd",
    "44100 | analog 2, hpf and lpf: the draw order (section 8)" to "c8c42a67f4750378",  // diverged from the strip at HEAD: the section 8 draw order
    "44100 | tremolo depth 0.5 sync 4" to "296e4068d9f0886b",
    "44100 | tremolo square, skew 0.3, phase 0.25" to "5ff510304a9cd59b",
    "44100 | adsr 0.005 / 0.2 / 0.5 / 0.2 (whole frame counts at 48 kHz, 220.5 attack frames at 44.1 kHz)" to "5e6aa4a90443c7f2",
    "44100 | adsr at fractional frame counts (one envelope law: fractional attack and decay on both hosts)" to "f390ab676e34c33c",
    "44100 | adsrCurves linear, scurve, square" to "8d3a2ea8aa86c01d",
    "44100 | adsr release -0.1: a zero-length release stage, and the voice still lives to its gate (the lifetime floored at 0)" to "b54434ec0c9ab4fd",
    "44100 | adsrOff: the teardown fade, one law on both hosts (step 6)" to "9c0c2a9dae78e280",
    "44100 | adsrOff with release 0.2: the fade over a longer tail" to "3d078df852668fe7",
    "44100 | onepole 900 with crush 5: in front of the quantizer" to "94248dd28853a563",
    "44100 | vibrato and FM from the voice's pitch pipeline, under lpf env and hpf" to "0be0cc81b5c68a1c",
    "44100 | coarse, hpf, lpf, tremolo and the envelope together" to "394593c4ea31fecf",
    "44100 | crush and distort in the chain" to "5908044a9ada3df5",
)
