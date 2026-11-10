/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.state.BenchLayout
import io.peekandpoke.klang.ui.comp.KlangWorkbench

/**
 * The bench's layout from the one the Spine keeps ([kept]): the bench (`klangui`) knows nothing of storage, so the app
 * has a class of its own (KLANG-00422). A height never chosen is the bench's own.
 */
fun benchLayoutOf(kept: BenchLayout): KlangWorkbench.Layout = KlangWorkbench.Layout(
    pinned = kept.pinned,
    open = kept.open,
    bottomOpen = kept.bottomOpen,
    bottomPinned = kept.bottomPinned,
    bottomHeight = kept.bottomHeight ?: KlangWorkbench.Layout().bottomHeight,
    bottomTab = kept.bottomTab,
)

/**
 * The layout the Spine keeps from the one the bench reports ([layout]). A height that is the bench's own is not kept
 * as a choice, so a changed default of the bench reaches everyone who never dragged the panel.
 */
fun keptLayoutOf(layout: KlangWorkbench.Layout): BenchLayout = BenchLayout(
    pinned = layout.pinned,
    open = layout.open,
    bottomOpen = layout.bottomOpen,
    bottomPinned = layout.bottomPinned,
    bottomHeight = layout.bottomHeight.takeIf { it != KlangWorkbench.Layout().bottomHeight },
    bottomTab = layout.bottomTab,
)
