/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * One block of [buffer], in place, through the door the orbit stage calls ([Phaser.process] with a [PhaserBlock]),
 * with every knob as it STANDS: the breakpoint at [Phaser.center] and [Phaser.sweep], the C4 pair at
 * [Phaser.dryCoeff] and [Phaser.wetCoeff] at both ends of the block. That is the block `KatalystPhaserEffect` hands
 * in once its glides have settled, and the settled reference the specs build their oracles from.
 */
internal fun Phaser.processSettled(buffer: StereoBuffer, frames: Int) {
    val dry = dryCoeff()
    val wet = wetCoeff()
    val block = PhaserBlock()

    block.centerTo = center
    block.sweepTo = sweep
    block.dryFrom = dry
    block.dryTo = dry
    block.wetFrom = wet
    block.wetTo = wet

    process(buffer = buffer, frames = frames, block = block)
}
