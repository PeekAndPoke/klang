/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.round

/**
 * Vowel formant catalogue: the fixed formant banks behind `vowel(vowel = "<name>")`.
 *
 * A vowel is *pure data*: a list of [FilterDef.Formant.Band] `(freq Hz, db, Q)` triples, one per
 * formant, played as a parallel SVF-bandpass bank blended over the dry source (the source-filter
 * model). The `db` convention is the legacy Q-peak one, documented on [FilterDef.Formant.Band];
 * do not "clean up" a value here without reading it first.
 *
 * The name is `"<voice>:<vowel>"`, and a bare `"<vowel>"` means the soprano register. The four
 * registers (`bass`, `tenor`, `alto`/`countertenor`, `soprano`) are the classic sung-vowel tables;
 * on top of the five Latin vowels they carry the German umlauts and the diphthong nuclei, which is
 * what lets a German lyric be sung rather than transliterated.
 *
 * Lives in `audio_bridge` (Katalyst step 3c, 2026-09-17) so that both readers of a vowel NAME reach
 * it: sprudel's `toVoiceData`, which resolves a voice's vowel, and the backend's `KatalystSlots`,
 * which resolves a declared Katalyst chain's `vowel` stage.
 *
 * **A vowel is also an INDEX** (Katalyst step 5a-2, 2026-09-18): [names] flattens the
 * register-by-vowel table into one closed, ordered list of canonical `"<register>:<vowel>"` names,
 * so a vowel can travel as the number of its position in it and the wire needs no string slot.
 * [indexOf] and [bandsAt] are that one conversion, next to the table, so the pattern doors, the
 * chain builders and the backend resolver can never disagree about which number sings what. Index 0
 * is `none`, the off state on both spellings, and a bare `"a"` still means the soprano register.
 */
object VowelBands {

    /** Helper for band creation. */
    private fun b(freq: Double, db: Double, q: Double) = FilterDef.Formant.Band(freq, db, q)

    /**
     * The voice registers [bandsOf] accepts, in table order. `alto` and `countertenor` are two
     * spellings of one bank and each gets its own index: the catalogue lists what a user may
     * WRITE, and two indices that resolve to equal banks cost nothing.
     */
    private val registers: List<String> = listOf("bass", "tenor", "alto", "countertenor", "soprano")

    /**
     * The vowels [bandsOf] accepts, in table order: the five Latin vowels, the German umlauts in
     * both spellings, and the diphthong nuclei. Every one of them is a name a user may write, so
     * every one of them gets an index, even where two share a bank (`a`, `ei` and `au` do).
     */
    private val vowels: List<String> = listOf(
        "a", "ei", "au", "e", "i", "o", "u",
        "ae", "ä", "oe", "ö", "ue", "ü",
        "eu", "äu",
    )

    /**
     * Every selectable vowel, flat and ordered, in the canonical `"<register>:<vowel>"` spelling.
     * Index 0 is `none` (off); the rest is the full register-by-vowel cross product.
     *
     * **Append only, never reorder**, and that means appending to [registers] or [vowels] rather
     * than inserting: a name's POSITION here is the `vowel.vowel` slot's value, so it is the wire
     * encoding of a vowel and the editor's dropdown order. Reordering repoints every declared chain
     * and every `katp` at a different vowel.
     */
    val names: List<String> = buildList {
        add("none")

        for (register in registers) {
            for (vowel in vowels) {
                add("$register:$vowel")
            }
        }
    }

    /** The index of every name in [names], for [indexOf]. Built once, so no lookup ever scans. */
    private val indexByName: Map<String, Int> = names.withIndex().associate { (i, name) -> name to i }

    /**
     * The bank of every name in [names], by index, built once from [bandsOf].
     *
     * As with the body table, [bandsFor] and [bandsAt] hand back the table's OWN list, the same
     * instance every time, so a consumer deciding whether to rebuild a formant bank short-circuits
     * on identity instead of walking five bands per note.
     */
    private val bandsByIndex: List<List<FilterDef.Formant.Band>?> = names.map { name ->
        val parts = name.split(':')

        if (parts.size > 1) bandsOf(voice = parts[0], vowel = parts[1]) else null
    }

    /**
     * The INDEX of a vowel name, for a `vowel.vowel` slot: the position in [names], or 0.0
     * (`none`, the off state) for an unknown register or vowel.
     *
     * Case-insensitive and register-defaulting, exactly as [bandsFor] has always been: a bare
     * `"a"` is `"soprano:a"`, and a name with more than one colon keeps only the first two parts.
     */
    fun indexOf(vowelValue: String): Double = (indexByName[canonical(vowelValue)] ?: 0).toDouble()

    /**
     * The formant bank at an index, or null when the index names no vowel, which turns the vowel
     * stage OFF. Same index rule as `BodyMaterials.modesAt`: non-finite, negative, past the end or
     * 0 is off, and anything else rounds to the nearest index, a tie to the EVEN one (0.5 is
     * `none`, 1.5 is index 2), which is what `kotlin.math.round` does on both platforms.
     */
    fun bandsAt(index: Double): List<FilterDef.Formant.Band>? {
        // NaN-guard on a value the author can write: a non-finite index was never set.
        if (!index.isFinite()) {
            return null
        }

        val i = round(index).toInt()

        if (i <= 0 || i >= names.size) {
            return null
        }

        return bandsByIndex[i]
    }

    /**
     * Resolves a vowel name to its formant bank. Returns null for an unknown voice register or an
     * unknown vowel, and the formant filter is then skipped (fail soft, never throw on user input).
     *
     * Goes through the index, so the name path and the slot path cannot answer differently.
     */
    fun bandsFor(vowelValue: String): List<FilterDef.Formant.Band>? = bandsAt(indexOf(vowelValue))

    /**
     * The canonical `"<register>:<vowel>"` spelling of a written name: lowercased, and a name with
     * no register gets the soprano one. `"none"` has no register and therefore no canonical form
     * in [names], which is exactly how it resolves to index 0.
     */
    private fun canonical(vowelValue: String): String {
        val parts = vowelValue.lowercase().split(':')

        return if (parts.size > 1) "${parts[0]}:${parts[1]}" else "soprano:${parts[0]}"
    }

    /**
     * The formant table itself, by register and vowel. Private and index-free: it is what fills
     * [bandsByIndex], so it may not ask [bandsAt] anything.
     */
    private fun bandsOf(voice: String, vowel: String): List<FilterDef.Formant.Band>? {
        return when (voice) {
            "bass" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(600.0, 0.0, 60.0),
                    b(1040.0, -7.0, 70.0),
                    b(2250.0, -9.0, 110.0),
                    b(2450.0, -9.0, 120.0),
                    b(2750.0, -20.0, 130.0)
                )

                "e" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(1620.0, -12.0, 70.0),
                    b(2400.0, -9.0, 110.0),
                    b(2800.0, -12.0, 120.0),
                    b(3100.0, -18.0, 130.0)
                )

                "i" -> listOf(
                    b(250.0, 0.0, 60.0),
                    b(1750.0, -30.0, 70.0),
                    b(2600.0, -16.0, 110.0),
                    b(3050.0, -22.0, 120.0),
                    b(3340.0, -28.0, 130.0)
                )

                "o" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(750.0, -11.0, 70.0),
                    b(2400.0, -21.0, 110.0),
                    b(2600.0, -20.0, 120.0),
                    b(2900.0, -40.0, 130.0)
                )

                "u" -> listOf(
                    b(350.0, 0.0, 60.0),
                    b(600.0, -20.0, 70.0),
                    b(2400.0, -32.0, 110.0),
                    b(2675.0, -28.0, 120.0),
                    b(2950.0, -36.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(600.0, 0.0, 60.0),
                    b(1400.0, -10.0, 70.0),
                    b(2200.0, -12.0, 110.0),
                    b(2450.0, -12.0, 120.0),
                    b(2750.0, -22.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(1300.0, -14.0, 70.0),
                    b(2000.0, -12.0, 110.0),
                    b(2400.0, -14.0, 120.0),
                    b(3100.0, -20.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(250.0, 0.0, 60.0),
                    b(1400.0, -28.0, 70.0),
                    b(2100.0, -18.0, 110.0),
                    b(3050.0, -24.0, 120.0),
                    b(3340.0, -30.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(500.0, 0.0, 60.0),
                    b(900.0, -10.0, 70.0),
                    b(2300.0, -15.0, 110.0),
                    b(2500.0, -20.0, 120.0),
                    b(2800.0, -30.0, 130.0)
                )

                else -> null
            }

            "tenor" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(650.0, 0.0, 70.0),
                    b(1080.0, -6.0, 80.0),
                    b(2650.0, -7.0, 110.0),
                    b(2900.0, -8.0, 120.0),
                    b(3250.0, -22.0, 130.0)
                )

                "e" -> listOf(
                    b(400.0, 0.0, 70.0),
                    b(1700.0, -14.0, 80.0),
                    b(2600.0, -12.0, 110.0),
                    b(3200.0, -14.0, 120.0),
                    b(3580.0, -20.0, 130.0)
                )

                "i" -> listOf(
                    b(290.0, 0.0, 70.0),
                    b(1870.0, -15.0, 80.0),
                    b(2800.0, -18.0, 110.0),
                    b(3250.0, -20.0, 120.0),
                    b(3540.0, -30.0, 130.0)
                )

                "o" -> listOf(
                    b(450.0, 0.0, 70.0),
                    b(800.0, -11.0, 80.0),
                    b(2830.0, -22.0, 110.0),
                    b(3500.0, -22.0, 120.0),
                    b(3800.0, -50.0, 130.0)
                )

                "u" -> listOf(
                    b(350.0, 0.0, 70.0),
                    b(600.0, -20.0, 80.0),
                    b(2700.0, -17.0, 110.0),
                    b(2900.0, -14.0, 120.0),
                    b(3300.0, -26.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(650.0, 0.0, 70.0),
                    b(1500.0, -8.0, 80.0),
                    b(2650.0, -10.0, 110.0),
                    b(2900.0, -12.0, 120.0),
                    b(3250.0, -24.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(400.0, 0.0, 70.0),
                    b(1400.0, -16.0, 80.0),
                    b(2200.0, -14.0, 110.0),
                    b(3200.0, -16.0, 120.0),
                    b(3580.0, -22.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(290.0, 0.0, 70.0),
                    b(1500.0, -18.0, 80.0),
                    b(2300.0, -20.0, 110.0),
                    b(3250.0, -22.0, 120.0),
                    b(3540.0, -32.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(550.0, 0.0, 70.0),
                    b(950.0, -10.0, 80.0),
                    b(2750.0, -15.0, 110.0),
                    b(2950.0, -20.0, 120.0),
                    b(3300.0, -30.0, 130.0)
                )

                else -> null
            }

            "alto", "countertenor" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(660.0, 0.0, 70.0),
                    b(1120.0, -6.0, 80.0),
                    b(2750.0, -23.0, 110.0),
                    b(3000.0, -24.0, 120.0),
                    b(3350.0, -38.0, 130.0)
                )

                "e" -> listOf(
                    b(440.0, 0.0, 70.0),
                    b(1800.0, -14.0, 80.0),
                    b(2700.0, -18.0, 110.0),
                    b(3000.0, -20.0, 120.0),
                    b(3300.0, -20.0, 130.0)
                )

                "i" -> listOf(
                    b(270.0, 0.0, 70.0),
                    b(1850.0, -20.0, 80.0),
                    b(2900.0, -24.0, 110.0),
                    b(3350.0, -26.0, 120.0),
                    b(3590.0, -36.0, 130.0)
                )

                "o" -> listOf(
                    b(430.0, 0.0, 70.0),
                    b(820.0, -10.0, 80.0),
                    b(2700.0, -26.0, 110.0),
                    b(3000.0, -22.0, 120.0),
                    b(3300.0, -34.0, 130.0)
                )

                "u" -> listOf(
                    b(370.0, 0.0, 70.0),
                    b(630.0, -20.0, 80.0),
                    b(2750.0, -23.0, 110.0),
                    b(3000.0, -24.0, 120.0),
                    b(3400.0, -34.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(660.0, 0.0, 70.0),
                    b(1500.0, -8.0, 80.0),
                    b(2750.0, -25.0, 110.0),
                    b(3000.0, -26.0, 120.0),
                    b(3350.0, -40.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(440.0, 0.0, 70.0),
                    b(1400.0, -16.0, 80.0),
                    b(2300.0, -20.0, 110.0),
                    b(3000.0, -22.0, 120.0),
                    b(3300.0, -22.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(270.0, 0.0, 70.0),
                    b(1500.0, -22.0, 80.0),
                    b(2400.0, -26.0, 110.0),
                    b(3350.0, -28.0, 120.0),
                    b(3590.0, -38.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(550.0, 0.0, 70.0),
                    b(970.0, -10.0, 80.0),
                    b(2750.0, -15.0, 110.0),
                    b(3000.0, -20.0, 120.0),
                    b(3350.0, -30.0, 130.0)
                )

                else -> null
            }

            "soprano" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(800.0, 0.0, 80.0),
                    b(1150.0, -6.0, 90.0),
                    b(2900.0, -32.0, 120.0),
                    b(3900.0, -20.0, 130.0),
                    b(4950.0, -50.0, 140.0)
                )

                "e" -> listOf(
                    b(350.0, 0.0, 80.0),
                    b(2000.0, -20.0, 90.0),
                    b(2800.0, -15.0, 120.0),
                    b(3600.0, -40.0, 130.0),
                    b(4950.0, -56.0, 140.0)
                )

                "i" -> listOf(
                    b(270.0, 0.0, 80.0),
                    b(2140.0, -12.0, 90.0),
                    b(3050.0, -26.0, 120.0),
                    b(4000.0, -26.0, 130.0),
                    b(4950.0, -44.0, 140.0)
                )

                "o" -> listOf(
                    b(450.0, 0.0, 80.0),
                    b(800.0, -11.0, 90.0),
                    b(2830.0, -22.0, 120.0),
                    b(3800.0, -22.0, 130.0),
                    b(4950.0, -50.0, 140.0)
                )

                "u" -> listOf(
                    b(325.0, 0.0, 80.0),
                    b(700.0, -16.0, 90.0),
                    b(2700.0, -35.0, 120.0),
                    b(3800.0, -40.0, 130.0),
                    b(4950.0, -60.0, 140.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(700.0, 0.0, 80.0),
                    b(1800.0, -10.0, 90.0),
                    b(2800.0, -20.0, 120.0),
                    b(3900.0, -25.0, 130.0),
                    b(4950.0, -52.0, 140.0)
                )

                "oe", "ö" -> listOf(
                    b(450.0, 0.0, 80.0),
                    b(1500.0, -22.0, 90.0),
                    b(2500.0, -18.0, 120.0),
                    b(3600.0, -42.0, 130.0),
                    b(4950.0, -58.0, 140.0)
                )

                "ue", "ü" -> listOf(
                    b(350.0, 0.0, 80.0),
                    b(1700.0, -20.0, 90.0),
                    b(2500.0, -28.0, 120.0),
                    b(4000.0, -30.0, 130.0),
                    b(4950.0, -46.0, 140.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(600.0, 0.0, 80.0),
                    b(950.0, -10.0, 90.0),
                    b(2850.0, -15.0, 120.0),
                    b(3850.0, -20.0, 130.0),
                    b(4950.0, -30.0, 140.0)
                )

                else -> null
            }

            else -> null
        }
    }
}
