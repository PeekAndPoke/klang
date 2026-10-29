/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Whether the window has the user's attention: shown and focused. Only that; what rests because of it is [UiWork]'s
 * (the maintainer, 2026-10-29: "split it .. one stream for the window focus ... then build on top of this one").
 * Followed from the window's focus and the document's visibility, once for the app's life.
 */
object WindowFocus {

    private fun now(): Boolean = document.hasFocus() && document.asDynamic().visibilityState == "visible"

    private val source = StreamSource(now())

    init {
        val update: (Event) -> Unit = { source(now()) }

        window.addEventListener("focus", update)
        window.addEventListener("blur", update)
        document.addEventListener("visibilitychange", update)
    }

    /** True while the window is shown and focused. */
    val focused: Stream<Boolean> = source.distinct()
}
