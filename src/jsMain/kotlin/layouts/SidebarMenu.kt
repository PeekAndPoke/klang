/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.pages.docs.tutorials.LearnTracksColumn
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Border
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.backgroundColor
import kotlinx.css.border
import kotlinx.css.borderBottomLeftRadius
import kotlinx.css.borderBottomRightRadius
import kotlinx.css.borderRadius
import kotlinx.css.borderTopLeftRadius
import kotlinx.css.borderTopRightRadius
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.flexGrow
import kotlinx.css.height
import kotlinx.css.minHeight
import kotlinx.css.overflowY
import kotlinx.css.padding
import kotlinx.css.paddingLeft
import kotlinx.css.paddingTop
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.pointerevents.PointerEvent

@Suppress("FunctionName")
fun Tag.SidebarMenu() = comp {
    SidebarMenu(it)
}

/**
 * The workbench's column (KLANG-00341): the current area's list (the songs, the Learn search and tracks, the doc sections)
 * under the area's name. Areas change through the Motor's drawer (`WorkbenchLayout`).
 */
class SidebarMenu(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private val currentRoute by subscribingTo(router.current)

    /** The area the route belongs to; the drawer changes areas, so the column keeps no state of its own. */
    private val area: AppArea get() = AppArea.of(currentRoute.route)

    //  MENU ITEM HELPER  /////////////////////////////////////////////////////////////////////////////////////

    /**
     * Renders a single menu item with consistent styling.
     * Selected items get accent-blue background with black icon and text.
     */
    private fun DIV.menuItem(
        isSelected: Boolean,
        label: String,
        iconFn: SemanticIconFn? = null,
        onClick: (PointerEvent) -> Unit,
    ) {
        noui.item {
            css {
                border = Border.none
                borderTopLeftRadius = 4.px
                borderBottomLeftRadius = 4.px
                borderTopRightRadius = 0.px
                borderBottomRightRadius = 0.px
                padding = Padding(10.px, 20.px)
                cursor = Cursor.pointer

                if (isSelected) {
                    backgroundColor = Color(laf.accentMuted)
                    color = Color.black
                } else {
                    // Transparent so the column's ground shows through
                    backgroundColor = Color.transparent
                    color = Color.white
                    borderRadius = 0.px
                }
            }
            this.onClick { evt ->
                evt.stopPropagation()
                onClick(evt)
            }

            if (iconFn != null) {
                icon.iconFn().then {
                    if (isSelected) {
                        css { put("color", "black !important") }
                    }
                }
            }

            noui.content {
                css {
                    color = if (isSelected) Color.black else Color.white
                }
                +label
            }
        }
    }

    private fun DIV.menuItemsList(block: DIV.() -> Unit) {
        ui.vertical.relaxed.list {
            key = "menu-list"
            css {
                // Under the area's heading
                paddingTop = 8.px
                paddingLeft = 16.px
                flexGrow = 1.0
                overflowY = Overflow.auto
                minHeight = 0.px
            }
            block()
        }
    }

    //  RENDER  ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "sidebar-menu"
            css {
                color = Color.white
                height = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            div(classes = KlangControlStyles.heading.toString()) {
                key = "area-heading"
                css {
                    padding = Padding(18.px, 20.px, 0.px, 20.px)
                    // The column's headline, a size above the labels under it (the maintainer, 2026-10-27)
                    put("font-size", "13px")
                }
                // The smaller areas share one list, so it is named for all of them
                +when (area) {
                    AppArea.Midi, AppArea.Resources, AppArea.Credits, AppArea.DevStatus, AppArea.Other -> AppArea.Other.label
                    AppArea.Songs, AppArea.Learn, AppArea.Docs, AppArea.Samples -> area.label
                }
            }

            div {
                key = "menu-container-$area"
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    minHeight = 0.px
                    flexGrow = 1.0
                }

                when (area) {
                    AppArea.Songs -> renderSongsMenu()
                    AppArea.Samples -> renderSamplesMenu()
                    // The search and the tracks; a tutorial puts its own column (KLANG-00370)
                    AppArea.Learn -> LearnTracksColumn()
                    AppArea.Docs -> renderDocsMenu()
                    AppArea.Midi, AppArea.Resources, AppArea.Credits, AppArea.DevStatus, AppArea.Other -> renderDefaultMenu()
                }
            }
        }
    }

    private fun DIV.renderDefaultMenu() {
        menuItemsList {
            menuItem(false, "Samples Library", { wave_square }) {
                router.navToUri(Nav.samplesLibrary())
            }
            menuItem(area == AppArea.Midi, "Midi Playground", { keyboard }) {
                router.navToUri(Nav.midiPlayground())
            }
            menuItem(area == AppArea.Resources, "Resources", { bookmark }) {
                router.navToUri(Nav.resources())
            }
            menuItem(area == AppArea.Credits, "Credits", { bullhorn }) {
                router.navToUri(Nav.credits())
            }
            menuItem(area == AppArea.DevStatus, "Dev status", { satellite_dish }) {
                router.navToUri(Nav.devStatus())
            }
            // The page has a full-screen layout of its own, so it never shows as selected here
            menuItem(false, "UI kitchen sink", { palette }) {
                router.navToUri(Nav.uiKitchenSink())
            }
        }
    }

    private fun DIV.renderSongsMenu() {
        val isNewSong = currentRoute.route == Nav.newSongCode
        val isEditSong = currentRoute.route == Nav.editSongCode
        val songId = currentRoute.matchedRoute["id"]

        menuItemsList {
            menuItem(isNewSong, "New Song", { plus }) {
                router.navToUri(Nav.newSongCode())
            }

            BuiltInSongs.songs.forEach { song ->
                val isSelected = isEditSong && song.id == songId
                menuItem(isSelected, song.title, {
                    if (song.icon != null) with(song.icon) else music
                }) {
                    router.navToUri(Nav.editSongCode(song.id))
                }
            }
        }
    }

    private fun DIV.renderSamplesMenu() {
        menuItemsList {
            menuItem(currentRoute.route == Nav.samplesLibrary, "Explore", { music }) {
                router.navToUri(Nav.samplesLibrary())
            }
        }
    }

    private fun DIV.renderDocsMenu() {
        menuItemsList {
            menuItem(currentRoute.route == Nav.manualsLexikon, "Lexikon", { book }) {
                router.navToUri(Nav.manualsLexikon())
            }
            menuItem(currentRoute.route == Nav.manualsLibrary && currentRoute.matchedRoute["library"] == "sprudel", "Sprudel", { wind }) {
                router.navToUri(Nav.manualsLibrary("sprudel"))
            }
            menuItem(currentRoute.route == Nav.manualsLibrary && currentRoute.matchedRoute["library"] == "stdlib", "Stdlib", { cogs }) {
                router.navToUri(Nav.manualsLibrary("stdlib"))
            }
            menuItem(currentRoute.route == Nav.manualsKlangScript, "KlangScript", { code }) {
                router.navToUri(Nav.manualsKlangScript())
            }
        }
    }
}
