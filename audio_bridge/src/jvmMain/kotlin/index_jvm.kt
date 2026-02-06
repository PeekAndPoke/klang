package io.peekandpoke.klang.audio_bridge

actual typealias VisualizerBuffer = FloatArray

actual fun createVisualizerBuffer(size: Int): VisualizerBuffer = FloatArray(size)