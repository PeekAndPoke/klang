/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.TutorialColumn() = comp { TutorialColumn(it) }

/**
 * A tutorial's column (the canvas's board "Learn: a tutorial"): back to Learn, the track's lessons with their lamps,
 * the one read marked. No "On this page": the maintainer, 2026-10-27, "the "On this page" part is not even needed".
 */
class TutorialColumn(ctx: NoProps) : PureComponent(ctx) {

    private val currentRoute by subscribingTo(router.current)

    private val done by subscribingTo(TutorialStorage.done)

    override fun VDom.render() {
        val tutorial = allTutorials.find { it.slug == currentRoute.matchedRoute["slug"] }
        val track = tutorial?.let { tutorialTrack(currentRoute.matchedRoute.queryParams[TutorialPage.PARAM_TRACK], it) }
        val progress = TutorialProgress(done = done)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
                // On the area headline's left edge, 20px in as the other areas' menus
                put("padding", "18px 20px")
                put("color", "var(--kl-ink-text)")
            }

            backLink(router = router, label = "Learn", to = Nav.tutorials())

            if (tutorial == null || track == null) {
                return@div
            }

            learnLabel("${track.title}, ${track.lessons.indexOf(tutorial) + 1} of ${track.lessons.size}")

            lessonList(router = router, track = track, progress = progress, current = tutorial)
        }
    }
}
