/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.blocks.ui

/** Identifies a unique active atom by its slot and char range. */
data class KlangBlockAtomKey(val slotIndex: Int?, val atomStart: Int?, val atomEnd: Int?)
