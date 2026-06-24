/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

data class Song(
    val id: String,
    val title: String,
    val rpm: Double,
    val code: String,
    val icon: String? = null,
)
