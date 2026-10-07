/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.annotations.KlangScope
import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import kotlinx.css.color
import kotlinx.html.FlowContent
import kotlinx.html.title

/**
 * The badge for where a DSL function takes effect: on the one voice, on the orbit's shared bus,
 * or on the master.
 *
 * One definition for every surface that shows it (the editor's docs popup and the library docs
 * page), so the wording and the colour cannot drift apart.
 *
 * Colour carries the warning, because the bus cases are the ones that surprise people: an orbit
 * setting comes from the newest note that is still held on that orbit (not a release tail), and every other voice on the
 * orbit is along for the ride. The `title` spells that out for whoever hovers it.
 */
fun FlowContent.klangScopeLabel(laf: KlangLookAndFeel, scope: KlangScope) {
    // Not laf.good: that is the "Built-in" origin chip's colour, and the two sit side by side in the
    // popup header, where two identical pills stop carrying any warning at all.
    val background = when (scope) {
        KlangScope.VOICE -> laf.accent
        KlangScope.ORBIT -> laf.gold
        KlangScope.MASTER -> laf.warning
    }

    val hint = when (scope) {
        KlangScope.VOICE ->
            "Per voice: every note carries its own value."

        KlangScope.ORBIT ->
            "Orbit bus: one shared processor per orbit, set by the newest note that is still held on it (a release tail does not count)." +
                    " Every other voice on the orbit is along for the ride." +
                    " Give a pattern its own orbit to give it its own settings."

        KlangScope.MASTER ->
            "Master: the whole playback, after every orbit."
    }

    ui.label {
        css {
            backgroundColor = Color("$background !important")
            color = Color("#222 !important")
        }
        title = hint
        +scope.label
    }
}
