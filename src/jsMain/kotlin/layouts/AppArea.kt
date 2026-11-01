/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.ultra.semanticui.SemanticIconFn

/**
 * The areas of the app (KLANG-00341): the workbench's column shows the area's list under its [label], the Motor's
 * menu offers each area as a destination ([sub] its second line, [icon] the first page's icon in the column's list)
 * and marks the one a route belongs to.
 */
enum class AppArea(val label: String, val sub: String, val icon: SemanticIconFn) {
    Songs(label = "Songs", sub = "Yours and the built-ins", icon = { music }),
    Learn(label = "Learn", sub = "Tutorials and tracks", icon = { graduation_cap }),
    Docs(label = "Docs", sub = "Lexikon, Sprudel, Stdlib", icon = { book_open }),
    Samples(label = "Samples", sub = "The library", icon = { wave_square }),
    Midi(label = "MIDI", sub = "Playground, devices", icon = { keyboard }),
    Credits(label = "Credits", sub = "Who we build on", icon = { bullhorn }),

    /** Where Klang stands, what this browser keeps, every part of the UI (the maintainer, 2026-10-28: "Dev Tools"). */
    DevTools(label = "Dev Tools", sub = "Status, storage, the parts", icon = { satellite_dish }),

    /** A route of no area (the dashboard): the column shows the list of the smaller areas. */
    Other(label = "More", sub = "", icon = { ellipsis_horizontal });

    companion object {
        /** The area [route] belongs to; tutorials before docs, their paths share the manuals' base. */
        fun of(route: Route): AppArea = when {
            route == Nav.songs || route == Nav.newSongCode || route == Nav.editSongCode -> Songs
            route.pattern.startsWith(Nav.tutorialsBase) -> Learn
            // Learning resources: Learn's (the maintainer, 2026-10-28)
            route == Nav.resources -> Learn
            route.pattern.startsWith(Nav.manualsBase) -> Docs
            route == Nav.samplesLibrary -> Samples
            route == Nav.midiPlayground -> Midi
            route == Nav.credits -> Credits
            route.pattern == Nav.devToolsBase || route.pattern.startsWith("${Nav.devToolsBase}/") -> DevTools
            else -> Other
        }
    }
}
