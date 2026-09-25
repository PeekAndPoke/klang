/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.DistortionShapes

/**
 * Distortion waveshaper shapes. Internal-only enum: the DSL surface names a shape and the
 * Ignitor nodes carry its index in `DistortionShapes`; both map here via [parseDistortionShape]
 * and [distortionShapeAt]. **The entry order is the catalogue's**, append only.
 *
 * Dispatch at the audio-rate per-sample loop uses [applyDistortionShape], which
 * is `inline` so each `when` case expands to a literal `ShapingFuncs.foo(x)`
 * call — letting the inline shape functions in `ShapingFuncs.kt` actually
 * inline. Storing a `(Double) -> Double` function reference would defeat that.
 */
internal enum class DistortionShape {
    SOFT, HARD, GENTLE, CUBIC, DIODE, FOLD, CHEBYSHEV, RECTIFY, EXP,
    SOFT_SAT, TUBE, LINEAR_FOLD, ZERO_SQUARE, SINE_SHAPER, ASYM, STOMP_BOX,
}

/**
 * Maps a DSL shape name to the enum. Unknown → [DistortionShape.SOFT] (the `tanh` fallback).
 * Case-insensitive.
 *
 * The names and aliases live in ONE table, `DistortionShapes` in `audio_bridge` (phase 3 step 3b,
 * 2026-09-25), and this goes through its INDEX, so the strip (which reads a name off the voice)
 * and the Ignitor `Shape`/`Distort` nodes (which carry the index as a knob) cannot disagree.
 */
internal fun parseDistortionShape(shape: String): DistortionShape = distortionShapeAt(DistortionShapes.indexOf(shape))

/**
 * The shape an index knob selects: `DistortionShapes.indexAt`'s rule (nearest position; non-finite,
 * negative or past the end is [DistortionShape.SOFT]). The enum's order IS the catalogue's, pinned
 * by `ShapeCatalogueSpec`.
 */
internal fun distortionShapeAt(index: Double): DistortionShape = DistortionShape.entries[DistortionShapes.indexAt(index)]

/**
 * Applies the shape to a single sample. `inline` is load-bearing: it expands
 * the `when` at the call site and inlines each `ShapingFuncs.foo(x)`. Holding
 * a `(Double) -> Double` function reference instead would force a virtual
 * Function1 dispatch + Double boxing per sample on Kotlin/JS.
 *
 * The legacy `outputGain` (2.0 for `gentle`, 1.0 for everything else) is baked
 * into the gentle case — no separate field to track.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun applyDistortionShape(shape: DistortionShape, x: Double): Double = when (shape) {
    DistortionShape.SOFT -> ShapingFuncs.fastTanh(x)
    DistortionShape.HARD -> ShapingFuncs.hardClip(x)
    DistortionShape.GENTLE -> ShapingFuncs.softClip(x) * 2.0
    DistortionShape.CUBIC -> ShapingFuncs.cubicClip(x)
    DistortionShape.DIODE -> ShapingFuncs.diodeClip(x)
    DistortionShape.FOLD -> ShapingFuncs.sineFold(x)
    DistortionShape.CHEBYSHEV -> ShapingFuncs.chebyshevT3(x)
    DistortionShape.RECTIFY -> ShapingFuncs.rectify(x)
    DistortionShape.EXP -> ShapingFuncs.expClip(x)
    DistortionShape.SOFT_SAT -> ShapingFuncs.softSat(x)
    DistortionShape.TUBE -> ShapingFuncs.tube(x)
    DistortionShape.LINEAR_FOLD -> ShapingFuncs.linearFold(x)
    DistortionShape.ZERO_SQUARE -> ShapingFuncs.zeroSquare(x)
    DistortionShape.SINE_SHAPER -> ShapingFuncs.sineShaper(x)
    DistortionShape.ASYM -> ShapingFuncs.asym(x)
    DistortionShape.STOMP_BOX -> ShapingFuncs.stompBox(x)
}
