/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * The workbench's layout as it is stored (KLANG-00422, first kept with KLANG-00349): the tool windows [pinned], the
 * pinned one [open], and the bottom panel: whether it is docked ([bottomPinned]) and open docked ([bottomOpen]), its
 * height in pixels ([bottomHeight], null for the bench's own) and the tab it shows ([bottomTab], null for the
 * first). The app's own class: the bench (`KlangWorkbench.Layout`, in `klangui`) knows nothing of storage.
 *
 * A first visit finds the bottom panel open and docked, where the Quick help's empty text says what to do (the
 * maintainer, 2026-10-27: "a new user should see the quick help open and pinned", KLANG-00365).
 */
@Serializable
data class BenchLayout(
    val pinned: Set<String> = emptySet(),
    val open: String? = null,
    val bottomOpen: Boolean = true,
    val bottomPinned: Boolean = true,
    val bottomHeight: Double? = null,
    val bottomTab: String? = null,
) {
    /** A height that is no number (NaN, infinite) is the bench's own: JSON holds no such number. */
    fun inRange(): BenchLayout = if (bottomHeight != null && !bottomHeight.isFinite()) copy(bottomHeight = null) else this
}
