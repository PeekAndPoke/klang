package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * A single processing stage in the orbit bus pipeline.
 *
 * The bus signal flows through a chain of KatalystEffects:
 * **Delay → Reverb → Phaser → Compressor → Ducking**
 *
 * - **Delay** and **Reverb** are send/return effects (read from send buffers, write to mix buffer)
 * - **Phaser** and **Compressor** are insert effects (read/write mix buffer in-place)
 * - **Ducking** is a sidechain effect (reads another cylinder's mix buffer as trigger)
 *
 * Each effect checks its own activation state and short-circuits when inactive.
 */
fun interface KatalystEffect {
    fun process(ctx: KatalystContext)
}
