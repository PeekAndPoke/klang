@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.peekandpoke.klang.audio_bridge

import org.khronos.webgl.Float32Array

actual typealias VisualizerBuffer = Float32Array

actual fun createVisualizerBuffer(size: Int): VisualizerBuffer = Float32Array(size)
