/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/** Runtime ignitor that fills buffer with the voice frequency. Runtime representation of [io.peekandpoke.klang.audio_bridge.IgnitorDsl.Freq]. */
object FreqIgnitor : Ignitor {
    override fun controlRateValueOrNull(freqHz: Double, ctx: IgniteContext): Double = freqHz

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        buffer.fill(freqHz, ctx.offset, ctx.offset + ctx.length)
    }
}
