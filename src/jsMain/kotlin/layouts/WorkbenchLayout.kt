/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.Motoer
import io.peekandpoke.klang.comp.PlayerWarehouseStats
import io.peekandpoke.klang.comp.WalkthroughsButton
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.pages.docs.tutorials.LearnSearch
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.storage.WorkbenchLayoutStorage
import io.peekandpoke.klang.ui.comp.KlangDrawer
import io.peekandpoke.klang.ui.comp.KlangLookPicker
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.version
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.routing.Static
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

    /** The page's column and its own tool windows (KLANG-00349), when it puts any into the workbench. */
    private val pageColumn by subscribingTo(WorkbenchSlots.column)
    private val pageTools by subscribingTo(WorkbenchSlots.tools)

    private val pageBottom by subscribingTo(WorkbenchSlots.bottom)

    /**
     * The workbench's layout as the last visit left it, read once: its pinned tool windows come back (KLANG-00349). A
     * first visit finds the Quick help open and pinned, where its empty text says what to do (the maintainer,
     * 2026-10-27: "a new user should see the quick help open and pinned", KLANG-00365).
     */
    private val keptLayout = WorkbenchLayoutStorage.load() ?: KlangWorkbench.Layout(bottomOpen = true, bottomPinned = true)

    /** The Gui-dance's lit step: the drawer lights "Songs" when it is (KLANG-00350). */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

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

    /** Where a drawer entry leads: the area's own page; null for no area. */
    private fun landing(area: AppArea): Static? = when (area) {
        AppArea.Songs -> Nav.songs
        AppArea.Learn -> Nav.tutorials
        AppArea.Docs -> Nav.manuals
        AppArea.Samples -> Nav.samplesLibrary
        AppArea.Midi -> Nav.midiPlayground
        AppArea.Resources -> Nav.resources
        AppArea.Credits -> Nav.credits
        AppArea.DevStatus -> Nav.devStatus
        AppArea.Other -> null
    }

    private fun go(id: String) {
        val area = AppArea.entries.firstOrNull { it.name == id } ?: return
        val route = landing(area) ?: return

        router.navToUri(route())
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
                column = {
                    val column = pageColumn

                    if (column != null) {
                        column()
                    } else {
                        SidebarMenu()
                    }
                },
                motor = { menu -> Motoer(menu = menu) },
                content = { props.inner(this) },
                tools = listOf(
                    KlangWorkbench.Tool(id = "motor", title = "Motor", icon = { tachometer_alternate }) {
                        renderBuild()
                        PlayerWarehouseStats()
                    },
                ) + pageTools,
                bottom = pageBottom,
                layout = keptLayout,
                onLayout = { WorkbenchLayoutStorage.save(it) },
                drawer = { close ->
                    KlangDrawer(
                        title = "Where to",
                        entries = entries,
                        // Pressed only on the area's own page: on a song the Songs entry stays up, as it still
                        // leads somewhere, and a pressed-looking key reads as one that cannot be pressed (the
                        // maintainer, 2026-10-26)
                        current = area.name.takeIf { landing(area) == currentRoute.route },
                        onPick = { id ->
                            when (id) {
                                AppArea.Songs.name -> {
                                    KlangGuiDance.dance.used(KlangGuiDance.DRAWER_SONGS)
                                }

                                AppArea.Learn.name -> {
                                    // Led there by the Gui-dance: the overview shows its panels (the next step) only
                                    // with no search left over in the Learn column
                                    if (guiDanceLit == KlangGuiDance.DRAWER_LEARN) {
                                        LearnSearch.text("")
                                    }

                                    KlangGuiDance.dance.used(KlangGuiDance.DRAWER_LEARN)
                                }
                            }

                            close()
                            go(id)
                        },
                        beacon = when (guiDanceLit) {
                            KlangGuiDance.DRAWER_SONGS -> AppArea.Songs.name
                            KlangGuiDance.DRAWER_LEARN -> AppArea.Learn.name
                            else -> null
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
                        footer = {
                            KlangLookPicker(
                                onChosen = { LookStorage.save(it) },
                                // The look editor, an empty slot ready to be filled (KLANG-00382)
                                onEdit = { slot ->
                                    close()
                                    router.navToUri(if (slot == null) Nav.looks() else Nav.looksSlot(slot.toString()))
                                },
                            )
                        },
                    )
                },
                // On every page, at the strip's foot (the maintainer, KLANG-00349)
                keys = {
                    WalkthroughsButton()
                    FullscreenToggleButton(fs = fs)
                },
                mark = "KLANGMOTOR",
                // The mark leads home, to the start screen (the maintainer, 2026-10-27); "/", as `start` renders an empty
                // href, which a browser takes for the page one is on
                markLink = KlangWorkbench.Link(href = router.strategy.render(Nav.startSlash())) { router.navToUri(Nav.startSlash()) },
            ).track(WorkbenchSlots.workbench)
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
