/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.routing.Route

/**
 * The areas of the app (KLANG-00341): the workbench's column shows the area's list under its [label], the Motor's
 * drawer offers each area as a destination ([sub] its second line) and marks the one a route belongs to.
 */
enum class AppArea(val label: String, val sub: String) {
    Songs(label = "Songs", sub = "Yours and the built-ins"),
    Learn(label = "Learn", sub = "Tutorials and tracks"),
    Docs(label = "Docs", sub = "Lexikon, Sprudel, Stdlib"),
    Samples(label = "Samples", sub = "The library"),
    Midi(label = "MIDI", sub = "Playground, devices"),
    Credits(label = "Credits", sub = "Who we build on"),

    /** Where Klang stands, what this browser keeps, every part of the UI (the maintainer, 2026-10-28: "Dev Tools"). */
    DevTools(label = "Dev Tools", sub = "Status, storage, the parts"),

    /** A route of no area (the dashboard): the column shows the list of the smaller areas. */
    Other(label = "More", sub = "");

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
