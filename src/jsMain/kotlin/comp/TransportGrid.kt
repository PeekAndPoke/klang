/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.ultra.html.css
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.div

/** A column of [transportGrid]: room for the widest word Play shows ("Loading" with its spinner, "Update" 96px). */
const val TRANSPORT_COLUMN_PX = 112

/** The gap between the columns of [transportGrid]. */
const val TRANSPORT_GAP_PX = 12

/**
 * The transport's controls on a grid of equal columns (the maintainer, 2026-11-02: "The element in the top bar must be
 * on a grid of equal width. The buttons must no change size when the text changes 'Play' / 'Update'"), the song page's
 * and the code examples' alike. A button fills its column, so Play keeps its size as its word changes; a control drawn
 * in [transportCentred] sits in the middle of its column, one in [transportSpanTwo] spans two (the RPM knob and its
 * display). As many columns as fit, from the left: where the room is narrow (a code example in a narrow docs column) the
 * controls go on in the next row, whole columns at a time. Never narrower than two columns, so the two-column control
 * always fits: in a flex row it takes the room left, and what stands beside it wraps first.
 */
fun FlowContent.transportGrid(content: DIV.() -> Unit) {
    div {
        css {
            put("display", "grid")
            put("grid-template-columns", "repeat(auto-fill, ${TRANSPORT_COLUMN_PX}px)")
            put("justify-content", "start")
            put("align-items", "center")
            put("gap", "${TRANSPORT_GAP_PX}px")
            put("flex", "1 1 0")
            put("min-width", "${2 * TRANSPORT_COLUMN_PX + TRANSPORT_GAP_PX}px")
        }

        content()
    }
}

/** A control in the middle of its column of a [transportGrid]: the cycle's display. */
fun DIV.transportCentred(content: DIV.() -> Unit) {
    div {
        css { put("justify-self", "center") }
        content()
    }
}

/** A control across two columns of a [transportGrid], at their start: the RPM knob and its display. */
fun DIV.transportSpanTwo(content: DIV.() -> Unit) {
    div {
        css {
            put("grid-column", "span 2")
            put("justify-self", "start")
        }
        content()
    }
}
