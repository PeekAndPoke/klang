/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * Runtime exciter that fills a buffer with a fixed [value].
 *
 * This is the runtime representation of [IgnitorDsl.Constant] — a sealed,
 * non-overridable scalar in the signal graph. Also serves as the default
 * value for factory parameters in [Ignitors] when no explicit ignitor is
 * supplied (the normal DSL path always supplies one explicitly, but the
 * default is needed for direct Kotlin callers).
 *
 * For overridable parameter slots (sprudel oscParam compatibility) see
 * [ParamIgnitor], which carries a name used for `oscParams[name]` lookup
 * at build time (in `IgnitorDslRuntime.buildIgnitor`).
 */
class ConstantIgnitor(val value: Double) : Ignitor {

    private val valueF = value

    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double = valueF

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(valueF, ctx.offset, ctx.offset + ctx.length)
    }
}
