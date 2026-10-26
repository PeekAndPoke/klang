/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.Motoer
import io.peekandpoke.klang.comp.PlayerWarehouseStats
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.comp.KlangDrawer
import io.peekandpoke.klang.ui.comp.KlangLookPicker
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.version
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.WorkbenchLayout(
    inner: FlowContent.() -> Unit,
) = comp(
    WorkbenchLayout.Props(inner = inner)
) {
    WorkbenchLayout(it)
}

/**
 * The app's layout (KLANG-00341, KLANG-00334 layout A): the page in the workbench, the Motor bottom left across the
 * pages, the current area's list above it ([SidebarMenu]), the Motor's drawer for every destination and the looks.
 */
class WorkbenchLayout(ctx: Ctx<Props>) : Component<WorkbenchLayout.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val inner: FlowContent.() -> Unit,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val currentRoute by subscribingTo(router.current)

    /** The build, shown in the Motor tool window (the Motor's title, which carried it, went up the strip). */
    private val versionInfo by subscribingTo(version)

    /** What the drawer's search field holds; the search window's step gives it results. */
    private var query: String by value("")

    //  DESTINATIONS  ///////////////////////////////////////////////////////////////////////////////////////////

    private val entries = AppArea.entries
        .filter { it != AppArea.Other }
        .map { KlangDrawer.Entry(id = it.name, label = it.label, sub = it.sub) } +
            listOf(
                KlangDrawer.Entry(id = "community", label = "Community", sub = "With the backend", later = true),
                KlangDrawer.Entry(id = "account", label = "Account", sub = "With the backend", later = true),
            )

    private fun go(id: String) {
        val area = AppArea.entries.firstOrNull { it.name == id } ?: return

        val uri = when (area) {
            AppArea.Songs -> Nav.newSongCode()
            AppArea.Learn -> Nav.tutorials()
            AppArea.Docs -> Nav.manuals()
            AppArea.Samples -> Nav.samplesLibrary()
            AppArea.Midi -> Nav.midiPlayground()
            AppArea.Resources -> Nav.resources()
            AppArea.Credits -> Nav.credits()
            AppArea.DevStatus -> Nav.devStatus()
            AppArea.Other -> return
        }

        router.navToUri(uri)
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val area = AppArea.of(currentRoute.route)

        div {
            key = "workbench-layout"
            css {
                put("width", "100%")
                put("height", "100vh")
            }

            KlangWorkbench(
                column = { SidebarMenu() },
                motor = { menu -> Motoer(menu = menu) },
                content = { props.inner(this) },
                tools = listOf(
                    KlangWorkbench.Tool(id = "motor", title = "Motor", icon = { tachometer_alternate }) {
                        renderBuild()
                        PlayerWarehouseStats()
                    },
                ),
                drawer = { close ->
                    KlangDrawer(
                        title = "Where to",
                        entries = entries,
                        current = area.name.takeIf { area != AppArea.Other },
                        onPick = { id ->
                            close()
                            go(id)
                        },
                        onClose = close,
                        closeOnEsc = true,
                        search = {
                            KlangSearchField(
                                label = "Search songs, docs, samples and actions",
                                value = query,
                                onInput = { query = it },
                                placeholder = "Songs, docs, samples, actions",
                            )
                        },
                        footer = { KlangLookPicker(onChosen = { LookStorage.save(it) }) },
                    )
                },
                mark = "KLANGMOTOR",
            )
        }
    }

    /** The build the Motor runs: version, branch, revision, date; nothing until it has loaded. */
    private fun FlowContent.renderBuild() {
        val info = versionInfo.takeIf { it.isAvailable } ?: return

        div {
            css {
                put("margin", "0 0 10px")
                put("font-family", "monospace")
                put("font-size", "12px")
                put("line-height", "18px")
                put("color", "var(--kl-ink-label)")
            }

            div { +"${info.project} v${info.version}" }
            div { +"branch: ${info.gitBranch}" }
            div { +"rev: ${info.gitDesc}" }
            info.date?.let { date -> div { +"built: $date" } }
        }
    }
}
