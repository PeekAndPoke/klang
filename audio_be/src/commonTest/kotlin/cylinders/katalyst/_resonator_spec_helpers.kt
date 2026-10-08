/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.ResonatorConfig
import io.peekandpoke.klang.audio_be.filters.ResonatorTable
import io.peekandpoke.klang.audio_bridge.FilterDef

// The resonator stage (`KatalystResonatorEffect`) driven the way its specs wrote it before engine tidy-up step 12 (a),
// from a band carrier (`FilterDef.Body`, `FilterDef.Formant`), so their rows keep their words.

/** A body stage: `KatalystResonatorEffect` of the body kind. */
internal fun bodyStage(sampleRate: Double): KatalystResonatorEffect =
    KatalystResonatorEffect(kind = ResonatorKind.BODY, sampleRate = sampleRate)

/** A vowel stage: `KatalystResonatorEffect` of the vowel kind. */
internal fun vowelStage(sampleRate: Double): KatalystResonatorEffect =
    KatalystResonatorEffect(kind = ResonatorKind.VOWEL, sampleRate = sampleRate)

/**
 * One table per distinct list of rows: equal lists (`==`) get the SAME table, the rule `ResonatorTables` follows for
 * the catalogue, so a spec's equal configs compare as the engine's do.
 */
internal object SpecResonatorTables {
    private val bodies = HashMap<List<FilterDef.Body.Mode>, ResonatorTable>()
    private val vowels = HashMap<List<FilterDef.Formant.Band>, ResonatorTable>()

    fun body(rows: List<FilterDef.Body.Mode>): ResonatorTable = bodies.getOrPut(rows) { ResonatorTable.ofBody(rows) }

    fun vowel(rows: List<FilterDef.Formant.Band>): ResonatorTable = vowels.getOrPut(rows) { ResonatorTable.ofVowel(rows) }
}

/** Configures from a body carrier; null is OFF, and a null floor is UNSET (the stage substitutes its constant). */
internal fun KatalystResonatorEffect.configureBody(def: FilterDef.Body?) {
    configure(
        ResonatorConfig(
            table = def?.let { SpecResonatorTables.body(it.bands) },
            mix = def?.mix ?: Double.NaN,
            floor = def?.floor ?: Double.NaN,
        )
    )
}

/** Configures from a vowel carrier; null is OFF, and a null floor is UNSET (the stage substitutes its constant). */
internal fun KatalystResonatorEffect.configureVowel(def: FilterDef.Formant?) {
    configure(
        ResonatorConfig(
            table = def?.let { SpecResonatorTables.vowel(it.bands) },
            mix = def?.mix ?: Double.NaN,
            floor = def?.floor ?: Double.NaN,
        )
    )
}

/** A table's bands as (freq, q, gain) triples in band order, for a STRUCTURAL compare; null for no table. */
internal fun ResonatorTable?.bandList(): List<Triple<Double, Double, Double>>? =
    this?.let { t -> (0 until t.count).map { Triple(t.freq[it], t.q[it], t.gain[it]) } }

/** The bands a body table built from [rows] carries, as [bandList] gives them; null for no rows. */
internal fun bodyBandList(rows: List<FilterDef.Body.Mode>?): List<Triple<Double, Double, Double>>? =
    rows?.let { ResonatorTable.ofBody(it).bandList() }

/** The bands a vowel table built from [rows] carries, as [bandList] gives them; null for no rows. */
internal fun vowelBandList(rows: List<FilterDef.Formant.Band>?): List<Triple<Double, Double, Double>>? =
    rows?.let { ResonatorTable.ofVowel(it).bandList() }

/**
 * A stage's class name, and for the resonator its kind too (`KatalystResonatorEffect(BODY)`): body and vowel are one
 * class since engine tidy-up step 12 (a), and an order row that read class names alone could no longer tell them
 * apart.
 */
internal fun Any.stageName(): String = when (this) {
    is KatalystResonatorEffect -> "KatalystResonatorEffect($kind)"
    else -> this::class.simpleName ?: "?"
}
