package io.peekandpoke.klang.audio_be

/**
 * Resolved distortion shape: clipping function + optional output gain.
 * Shared between the ignitor-based and block-renderer-based distortion implementations.
 *
 * The DC blocker is now applied unconditionally by the runtime distortion path
 * (was previously gated by a per-shape flag). Asymmetric shapes (`diode`,
 * `rectify`) require DC blocking for correctness; symmetric shapes (`soft`,
 * `hard`, etc.) need it as a defensive measure against rail-lock at extreme
 * drive — see `audio/ref/numerical-safety.md`.
 */
internal data class ResolvedShape(
    val fn: (Double) -> Double,
    val outputGain: Double = 1.0,
)

/**
 * Maps a shape name (from the DSL) to the corresponding clipping function and settings.
 * "soft" is the default / fallback.
 */
internal fun resolveDistortionShape(shape: String): ResolvedShape = when (shape.lowercase()) {
    "hard" -> ResolvedShape(fn = ClippingFuncs::hardClip)
    "gentle" -> ResolvedShape(fn = ClippingFuncs::softClip, outputGain = 2.0)
    "cubic" -> ResolvedShape(fn = ClippingFuncs::cubicClip)
    "diode" -> ResolvedShape(fn = ClippingFuncs::diodeClip)
    "fold" -> ResolvedShape(fn = ClippingFuncs::sineFold)
    "chebyshev" -> ResolvedShape(fn = ClippingFuncs::chebyshevT3)
    "rectify" -> ResolvedShape(fn = ClippingFuncs::rectify)
    "exp" -> ResolvedShape(fn = ClippingFuncs::expClip)
    else -> ResolvedShape(fn = ClippingFuncs::fastTanh) // "soft" & fallback
}
