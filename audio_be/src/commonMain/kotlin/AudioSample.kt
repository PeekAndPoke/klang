/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * Canonical scalar sample type for the DSP hot path.
 *
 * Aliased to [Double] to eliminate per-sample Float↔Double conversions on the JVM
 * (no-ops on JS, real `d2f`/`f2d` ops on JVM). The engine's output is doubles too
 * ([MasterStage.process] writes a clipped [StereoBuffer]). Conversions only happen at the
 * platform output boundaries (to `Float32Array` in the Web Audio worklet, to 16-bit PCM
 * bytes for `SourceDataLine` and the WAV writer through [writePcm16]) and at the
 * sample-asset playback boundary (`MonoSamplePcm.pcm: FloatArray` is widened to
 * [AudioBuffer] when read in [io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor]).
 */
typealias AudioSample = Double

/**
 * Canonical buffer type for the DSP hot path. See [AudioSample].
 */
typealias AudioBuffer = DoubleArray
