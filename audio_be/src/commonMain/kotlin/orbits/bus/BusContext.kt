package io.peekandpoke.klang.audio_be.orbits.bus

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * Shared context for all [BusEffect] stages in the orbit bus pipeline.
 *
 * Created once per orbit. Mutable fields are updated per block before the pipeline runs.
 *
 * Effects read/write the shared buffers:
 * - **Send effects** (Delay, Reverb) read from their send buffers and write to [mixBuffer]
 * - **Insert effects** (Phaser, Compressor) read/write [mixBuffer] in-place
 * - **Ducking** reads a sidechain orbit's mix buffer as a trigger signal
 */
class BusContext(
    /** Number of frames per block */
    val blockFrames: Int,
    /** Dry mix buffer — voices sum into this, effects process it in-place */
    val mixBuffer: StereoBuffer,
    /** Delay send buffer — voices write delay sends here */
    val delaySendBuffer: StereoBuffer,
    /** Reverb send buffer — voices write reverb sends here */
    val reverbSendBuffer: StereoBuffer,
) {
    /**
     * Sidechain source mix buffer for ducking. Set per block by [io.peekandpoke.klang.audio_be.orbits.Orbits]
     * when the sidechain orbit is resolved. Null if no ducking is configured or the sidechain orbit doesn't exist.
     */
    var sidechainBuffer: StereoBuffer? = null
}
