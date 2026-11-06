/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.REGION_MAX_STAGES

/**
 * Shared context for all [KatalystEffect] stages of one chain run over one bus.
 *
 * Created once per cylinder, once per master bus (`MasterBus`, again when a caller hands it another
 * buffer) and once per `ChainSwap` (the leaving chain's own context). Mutable fields are updated per
 * block before the pipeline runs.
 *
 * Every stage reads and writes [mixBuffer] in place, in list order. The delay and the reverb take
 * their feed from it too (scaled by their `wet`, into a buffer of their own) and add their return
 * back into it (Katalyst step 5b-2: no send buffers any more). The **Duck** reads a sidechain
 * cylinder's mix buffer as a trigger signal.
 */
class KatalystContext(
    /** Number of frames per block */
    val blockFrames: Int,
    /** The cylinder mix: voices sum into this, stages process it in place. */
    val mixBuffer: StereoBuffer,
) {
    /**
     * Sidechain source mix buffer for ducking. Set per block by [io.peekandpoke.klang.audio_be.cylinders.Cylinders]
     * when the sidechain cylinder is resolved. Null if no ducking is configured or the sidechain cylinder doesn't exist.
     */
    var sidechainBuffer: StereoBuffer? = null

    /**
     * The rate buses (KLANG-00214 (oversampling regions) step 3b): index k holds what the voices handed over at k 2x
     * steps above the base rate, `blockFrames shl k` frames; index 0 stays empty, [mixBuffer] is the base-rate bus.
     * A bus is made the first time a voice lands at its rate ([busAt]) and kept for the host's life. The chain brings
     * them to its own rate before its stages run (`KatalystChain.process`).
     */
    val buses: Array<StereoBuffer?> = arrayOfNulls(REGION_MAX_STAGES + 1)

    /** Which [buses] hold this block's audio, one bit per index: set by [busAt], cleared by [clearBuses]. */
    var activeBuses: Int = 0

    /**
     * The bus at [steps] above the base rate, marked as holding this block's audio. Made here the first time it is
     * asked for (a voice landing at that rate, a swap's ramped copy, a chain's steps down ringing out), on the audio
     * thread: at most one per rate per context, kept for its life (the bounded exception a chain build is, see
     * `ChainHost.chainFor`).
     */
    fun busAt(steps: Int): StereoBuffer {
        val bus = buses[steps] ?: StereoBuffer(blockFrames shl steps).also { buses[steps] = it }

        activeBuses = activeBuses or (1 shl steps)

        return bus
    }

    /** Zeroes the buses that held audio (a chain steps them down in place) and marks none. */
    fun clearBuses() {
        val active = activeBuses

        if (active == 0) {
            return
        }

        for (steps in 1..REGION_MAX_STAGES) {
            if (active and (1 shl steps) != 0) {
                buses[steps]?.clear()
            }
        }

        activeBuses = 0
    }
}
