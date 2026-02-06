@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.peekandpoke.klang.audio_bridge

/**
 * Platform-optimized buffer for visualization data.
 * JS: Float32Array (zero-copy with Web Audio)
 * JVM: FloatArray
 */
expect class VisualizerBuffer

expect fun createVisualizerBuffer(size: Int): VisualizerBuffer
