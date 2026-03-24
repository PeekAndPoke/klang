package io.peekandpoke.klang.audio_be.orbits.bus

/**
 * A single processing stage in the orbit bus pipeline.
 *
 * The bus signal flows through a chain of BusEffects:
 * **Delay → Reverb → Phaser → Compressor → Ducking**
 *
 * - **Delay** and **Reverb** are send/return effects (read from send buffers, write to mix buffer)
 * - **Phaser** and **Compressor** are insert effects (read/write mix buffer in-place)
 * - **Ducking** is a sidechain effect (reads another orbit's mix buffer as trigger)
 *
 * Each effect checks its own activation state and short-circuits when inactive.
 */
fun interface BusEffect {
    fun process(ctx: BusContext)
}
