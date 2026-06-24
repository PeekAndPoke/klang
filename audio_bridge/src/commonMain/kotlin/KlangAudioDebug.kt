/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Compile-time flag for audio thread debug checks.
 *
 * When false, the Kotlin compiler eliminates all guarded branches entirely — zero overhead.
 * Flip to true during development to surface warnings about unexpected states in audio
 * processing code (e.g., null buffers, unreachable branches).
 */
const val KLANG_AUDIO_DEBUG = false
