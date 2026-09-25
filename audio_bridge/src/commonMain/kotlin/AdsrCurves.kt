/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * The envelope curve catalogue: every shape an envelope stage can take (`adsr(a, d, s, r, e =>
 * e.curves("square", "exp", "cube"))` on the Ignitor, `adsrCurves(...)` on a pattern), as a closed,
 * ordered list whose POSITION the Ignitor envelope nodes carry as a knob (phase 3 step 3c,
 * 2026-09-25, the way [LfoShapes] and [DistortionShapes] do for their shapes since step 3b).
 *
 * The one home of the curve NAMES: both Ignitor doors and sprudel's `adsrCurves` parse through it,
 * and the backend turns a knob value back into an [AdsrCurve] through [curveAt]. The math of each
 * curve lives in the backend (`adsrCurveShape`); this file knows names and positions only.
 *
 * **There is no single fallback curve, and that is the difference to the shape catalogues.** An
 * unknown name and a bad index mean the DEFAULT of the envelope that reads them: the chain's
 * amplitude envelope falls back to [AdsrCurve.Default] (exponential), the filter and pitch
 * envelopes to `MOD_ENV_CURVE`. So every lookup takes the reader's fallback. Nothing here throws:
 * a name is user input.
 */
object AdsrCurves {

    /**
     * The canonical names, in their index order. **Append only, never reorder**: a name's POSITION
     * is the value a curve knob carries, so it is the wire encoding of a curve. The order is
     * [AdsrCurve]'s entry order, and a spec pins that the two agree position by position.
     */
    val names: List<String> = listOf("linear", "square", "cube", "scurve", "invsquare", "exponential")

    /** The alternative spellings, each onto its canonical name. The ones both parsers have always accepted. */
    val aliases: Map<String, String> = mapOf(
        "lin" to "linear",
        "sq" to "square",
        "quad" to "square",
        "quadratic" to "square",
        "cb" to "cube",
        "cubic" to "cube",
        "s" to "scurve",
        "smooth" to "scurve",
        "sigmoid" to "scurve",
        "inv" to "invsquare",
        "isquare" to "invsquare",
        "concave" to "invsquare",
        "exp" to "exponential",
        "expo" to "exponential",
    )

    /** Every name and alias onto its index, built once. */
    private val indexByName: Map<String, Int> = buildCatalogueIndex(names, aliases)

    /**
     * The curve a NAME asks for, or `null` for an unknown name or none at all. Trimmed and
     * case-insensitive, as the two parsers it replaced were.
     */
    fun curveOf(name: String?): AdsrCurve? {
        if (name == null) {
            return null
        }

        val index = indexByName[name.trim().lowercase()] ?: return null

        return AdsrCurve.entries[index]
    }

    /** The INDEX of [curve], the value a curve knob carries for it. */
    fun indexOf(curve: AdsrCurve): Double = curve.ordinal.toDouble()

    /** [curve] as the knob that selects it: a [IgnitorDsl.Constant] of its index. For Kotlin callers building nodes. */
    fun knob(curve: AdsrCurve): IgnitorDsl = IgnitorDsl.Constant(indexOf(curve))

    /** The INDEX a curve [name] asks for, or [fallback]'s index for an unknown name. See [curveOf]. */
    fun indexOf(name: String, fallback: AdsrCurve): Double = indexOf(curveOf(name) ?: fallback)

    /**
     * The curve a knob value selects: `catalogueIndexAt`'s rule (the nearest position, a tie to the
     * even one), and [fallback] for a non-finite value, a negative position or one past the end.
     */
    fun curveAt(index: Double, fallback: AdsrCurve): AdsrCurve =
        AdsrCurve.entries[catalogueIndexAt(index, names.size, fallback.ordinal)]
}
