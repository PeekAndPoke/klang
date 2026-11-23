/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.demo

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.meet.MeetLayout
import io.peekandpoke.klang.viz.liveact.meet.MeetTheBand
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.window
import kotlinx.html.Tag
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * The demo's page for "Meet the Band" (`?meet`, `?meet=rune`): the scene alone, [first] at the front at the start.
 * The left and right arrow keys turn the carousel to the member before or after ([MeetLayout.ORDER]): on this page
 * the keys are the demo's, not the scene's. The figures are fetched from [models] in the release [version].
 */
@Suppress("FunctionName")
fun Tag.MeetPage(first: Player, models: String, version: String) = comp(
    MeetPage.Props(first = first, models = models, version = version)
) {
    MeetPage(it)
}

/** The demo's page for "Meet the Band" as a component: it holds who is chosen, and listens for the arrow keys. */
class MeetPage(ctx: Ctx<Props>) : Component<MeetPage.Props>(ctx) {

    data class Props(val first: Player, val models: String, val version: String)

    /** Who stands at the front, or is on the way there. */
    private var selected: Player by value(props.first)

    private val onKey: (Event) -> Unit = { event ->
        // a key held with Ctrl, Alt or Meta is the browser's
        val key = (event as? KeyboardEvent)?.takeUnless { it.ctrlKey || it.metaKey || it.altKey }?.key

        when (key) {
            "ArrowRight" -> selected = MeetLayout.next(selected)
            "ArrowLeft" -> selected = MeetLayout.previous(selected)
            else -> Unit
        }
    }

    init {
        lifecycle {
            onMount {
                window.addEventListener("keydown", onKey)
            }

            onUnmount {
                window.removeEventListener("keydown", onKey)
            }
        }
    }

    override fun VDom.render() {
        MeetTheBand(models = props.models, version = props.version, selected = selected)
    }
}
