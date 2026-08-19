/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Test helper: forces the non-scalar path. Delegates rendering but inherits the `null` default
 * of [Ignitor.controlRateValueOrNull] and the `false` default of [Ignitor.isBlockConstant] —
 * the reference behavior a foldable node had before the constant-fold work. Parity specs wrap
 * one operand in this to keep the SCRATCH path as the oracle the fold path is compared against.
 *
 * Only the outermost node is opacified: a composite inner still folds internally, which is fine —
 * inner folds are independently anchored by the both-constant cases.
 */
internal class OpaqueIgnitor(private val inner: Ignitor) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) =
        inner.generate(buffer, freqHz, ctx)
}

/**
 * Test helper: fully transparent delegate (folds exactly like [inner] — flag and scalar pass
 * through) that COUNTS [generate] calls. Lets a spec prove a fold actually SKIPPED a render
 * (`generateCalls == 0`) instead of merely asserting value equality that would hold either way.
 */
internal class RenderCountProbe(private val inner: Ignitor) : Ignitor {
    var generateCalls: Int = 0
        private set

    override val isBlockConstant: Boolean get() = inner.isBlockConstant

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double? =
        inner.controlRateValueOrNull(freqHz, ctx)

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        generateCalls++
        inner.generate(buffer, freqHz, ctx)
    }
}
