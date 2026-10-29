/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.routing.ActiveRoute
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.ultra.semanticui.SemanticIconFn

/**
 * The app's pages in groups: the standard column's map, for every page without a column of its own (the maintainer,
 * 2026-10-28: "a standard left side menu when there is nothing that fits a screen. It would show category headlines and
 * links to several pages. Because now we have multiple left side that are inconsistent and do nothing"). A column
 * earns its place by doing work for its page (a song's files, Learn's search and tracks); every other page shows this.
 */
object AppMap {

    /** A page: its [label] and [icon], where it leads ([target]), and which routes count as being on it ([isCurrent]). */
    class Page(
        val label: String,
        val icon: SemanticIconFn,
        val target: Route.Bound,
        val isCurrent: (ActiveRoute) -> Boolean,
    )

    /** A group of pages under its [title]. */
    class Group(val title: String, val pages: List<Page>)

    private fun page(label: String, icon: SemanticIconFn, route: Route.Bound, vararg on: Route) = Page(
        label = label,
        icon = icon,
        target = route,
        isCurrent = { current -> current.route == route.route || current.route in on },
    )

    private fun library(label: String, icon: SemanticIconFn, library: String) = Page(
        label = label,
        icon = icon,
        target = Nav.manualsLibrary(library),
        isCurrent = { current -> current.route == Nav.manualsLibrary && current.matchedRoute["library"] == library },
    )

    val groups: List<Group> = listOf(
        Group(
            title = "Songs",
            pages = listOf(
                page(label = "All songs", icon = { music }, route = Nav.songs()),
                page(label = "New song", icon = { plus }, route = Nav.newSongCode()),
            ),
        ),
        Group(
            title = "Learn",
            pages = listOf(
                page(label = "Tutorials", icon = { graduation_cap }, route = Nav.tutorials()),
                page(label = "Resources", icon = { bookmark }, route = Nav.resources()),
            ),
        ),
        Group(
            title = "Docs",
            pages = listOf(
                page(label = "Overview", icon = { book_open }, route = Nav.manuals()),
                page(label = "Lexikon", icon = { book }, route = Nav.manualsLexikon(), Nav.manualsLexikonEntry),
                library(label = "Sprudel", icon = { wind }, library = "sprudel"),
                library(label = "Stdlib", icon = { cogs }, library = "stdlib"),
                page(label = "KlangScript", icon = { code }, route = Nav.manualsKlangScript()),
            ),
        ),
        Group(
            title = "Sounds",
            pages = listOf(
                page(label = "Samples library", icon = { wave_square }, route = Nav.samplesLibrary()),
                page(label = "MIDI playground", icon = { keyboard }, route = Nav.midiPlayground()),
            ),
        ),
        Group(
            title = "About",
            pages = listOf(
                page(label = "Credits", icon = { bullhorn }, route = Nav.credits()),
            ),
        ),
        Group(
            title = "Dev Tools",
            pages = listOf(
                page(label = "Dev status", icon = { satellite_dish }, route = Nav.devStatus()),
                page(label = "Storage", icon = { database }, route = Nav.devStorage()),
                page(label = "Jingles", icon = { bell }, route = Nav.devJingles()),
                page(label = "UI kitchen sink", icon = { palette }, route = Nav.uiKitchenSink()),
            ),
        ),
    )
}
