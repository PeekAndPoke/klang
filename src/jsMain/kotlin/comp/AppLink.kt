/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.kraft.routing.Router
import io.peekandpoke.ultra.html.onClick
import kotlinx.html.A
import kotlinx.html.FlowContent
import kotlinx.html.a

/** A link to a place in the app: a real link (a new tab with a key held), a plain click stays in the app. */
fun FlowContent.appLink(router: Router, to: Route.Bound, block: A.() -> Unit) {
    // The colour from a class (klang.css): a caller's own css {} would replace an inline one
    a(href = router.strategy.render(to), classes = "kl-app-link") {
        onClick { event ->
            val mouse = event.asDynamic()

            if (mouse.ctrlKey != true && mouse.metaKey != true && mouse.shiftKey != true && mouse.altKey != true && mouse.button == 0) {
                event.preventDefault()
                router.navToUri(to)
            }
        }

        block()
    }
}
