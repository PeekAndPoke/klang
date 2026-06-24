/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.peekandpoke.klang.audio_bridge.analyzer

actual typealias AnalyzerBuffer = FloatArray

actual fun createAnalyzerBuffer(size: Int): AnalyzerBuffer = FloatArray(size)
