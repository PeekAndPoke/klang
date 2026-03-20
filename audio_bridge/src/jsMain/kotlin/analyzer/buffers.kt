@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.peekandpoke.klang.audio_bridge.analyzer

import org.khronos.webgl.Float32Array

actual typealias AnalyzerBuffer = Float32Array

actual fun createAnalyzerBuffer(size: Int): AnalyzerBuffer = Float32Array(size)
