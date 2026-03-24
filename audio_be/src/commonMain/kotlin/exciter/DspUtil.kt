package io.peekandpoke.klang.audio_be.exciter

import kotlin.math.abs

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
const val DENORMAL_THRESHOLD = 1e-15

/** Flushes a value to zero if it is below the denormal threshold. */
@Suppress("NOTHING_TO_INLINE")
inline fun flushDenormal(v: Double): Double = if (abs(v) < DENORMAL_THRESHOLD) 0.0 else v
