package io.peekandpoke.klang.audio_be

/**
 * Distortion waveshaper shapes. Internal-only enum — the DSL surface stays
 * string-based (sprudel / klangscript) and is mapped via [parseDistortionShape].
 *
 * Dispatch at the audio-rate per-sample loop uses [applyDistortionShape], which
 * is `inline` so each `when` case expands to a literal `ClippingFuncs.foo(x)`
 * call — letting the inline shape functions in `ClippingFunctions.kt` actually
 * inline. Storing a `(Double) -> Double` function reference would defeat that.
 */
internal enum class DistortionShape {
    SOFT, HARD, GENTLE, CUBIC, DIODE, FOLD, CHEBYSHEV, RECTIFY, EXP,
    SOFT_SAT, TUBE, LINEAR_FOLD, ZERO_SQUARE, SINE_SHAPER, ASYM, STOMP_BOX,
}

/**
 * Maps a DSL shape name to the enum. Unknown / null → [DistortionShape.SOFT]
 * (the `tanh` fallback). Case-insensitive.
 */
internal fun parseDistortionShape(shape: String): DistortionShape = when (shape.lowercase()) {
    "hard" -> DistortionShape.HARD
    "gentle" -> DistortionShape.GENTLE
    "cubic" -> DistortionShape.CUBIC
    "diode" -> DistortionShape.DIODE
    "fold" -> DistortionShape.FOLD
    "chebyshev" -> DistortionShape.CHEBYSHEV
    "rectify" -> DistortionShape.RECTIFY
    "exp" -> DistortionShape.EXP
    "softsat", "soft_sat" -> DistortionShape.SOFT_SAT
    "tube" -> DistortionShape.TUBE
    "linearfold", "linear_fold", "lfold" -> DistortionShape.LINEAR_FOLD
    "zerosquare", "zero_square", "square" -> DistortionShape.ZERO_SQUARE
    "sineshaper", "sine_shaper", "sshape" -> DistortionShape.SINE_SHAPER
    "asym" -> DistortionShape.ASYM
    "stompbox", "stomp_box", "stomp" -> DistortionShape.STOMP_BOX
    else -> DistortionShape.SOFT // "soft" + fallback
}

/**
 * Applies the shape to a single sample. `inline` is load-bearing: it expands
 * the `when` at the call site and inlines each `ClippingFuncs.foo(x)`. Holding
 * a `(Double) -> Double` function reference instead would force a virtual
 * Function1 dispatch + Double boxing per sample on Kotlin/JS.
 *
 * The legacy `outputGain` (2.0 for `gentle`, 1.0 for everything else) is baked
 * into the gentle case — no separate field to track.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun applyDistortionShape(shape: DistortionShape, x: Double): Double = when (shape) {
    DistortionShape.SOFT -> ClippingFuncs.fastTanh(x)
    DistortionShape.HARD -> ClippingFuncs.hardClip(x)
    DistortionShape.GENTLE -> ClippingFuncs.softClip(x) * 2.0
    DistortionShape.CUBIC -> ClippingFuncs.cubicClip(x)
    DistortionShape.DIODE -> ClippingFuncs.diodeClip(x)
    DistortionShape.FOLD -> ClippingFuncs.sineFold(x)
    DistortionShape.CHEBYSHEV -> ClippingFuncs.chebyshevT3(x)
    DistortionShape.RECTIFY -> ClippingFuncs.rectify(x)
    DistortionShape.EXP -> ClippingFuncs.expClip(x)
    DistortionShape.SOFT_SAT -> ClippingFuncs.softSat(x)
    DistortionShape.TUBE -> ClippingFuncs.tube(x)
    DistortionShape.LINEAR_FOLD -> ClippingFuncs.linearFold(x)
    DistortionShape.ZERO_SQUARE -> ClippingFuncs.zeroSquare(x)
    DistortionShape.SINE_SHAPER -> ClippingFuncs.sineShaper(x)
    DistortionShape.ASYM -> ClippingFuncs.asym(x)
    DistortionShape.STOMP_BOX -> ClippingFuncs.stompBox(x)
}
