/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.key

/**
 * Represents a chord in a key with its roles.
 */
data class KeyChord(
    /** The name of the chord */
    val name: String,
    /** The harmonic roles of the chord in the key */
    val roles: List<String>,
)
