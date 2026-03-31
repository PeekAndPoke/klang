package io.peekandpoke.klang.audio_be

/**
 * Resolved distortion shape: clipping function + optional output gain and DC block flag.
 * Shared between the exciter-based and block-renderer-based distortion implementations.
 */
internal data class ResolvedShape(
    val fn: (Double) -> Double,
    val outputGain: Double = 1.0,
    val needsDcBlock: Boolean = false,
)

/**
 * Maps a shape name (from the DSL) to the corresponding clipping function and settings.
 * "soft" is the default / fallback.
 */
internal fun resolveDistortionShape(shape: String): ResolvedShape = when (shape.lowercase()) {
    "hard" -> ResolvedShape(fn = ClippingFuncs::hardClip)
    "gentle" -> ResolvedShape(fn = ClippingFuncs::softClip, outputGain = 2.0)
    "cubic" -> ResolvedShape(fn = ClippingFuncs::cubicClip)
    "diode" -> ResolvedShape(fn = ClippingFuncs::diodeClip, needsDcBlock = true)
    "fold" -> ResolvedShape(fn = ClippingFuncs::sineFold)
    "chebyshev" -> ResolvedShape(fn = ClippingFuncs::chebyshevT3)
    "rectify" -> ResolvedShape(fn = ClippingFuncs::rectify, needsDcBlock = true)
    "exp" -> ResolvedShape(fn = ClippingFuncs::expClip)
    else -> ResolvedShape(fn = ClippingFuncs::fastTanh) // "soft" & fallback
}
