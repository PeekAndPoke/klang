/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangPathColumn() = comp { KlangPathColumn(it) }

/** The Learn overview's column: The Klang Path's lessons with their lamps, then the other tracks with how far. */
class KlangPathColumn(ctx: NoProps) : PureComponent(ctx) {

    private val done by subscribingTo(TutorialStorage.done)

    override fun VDom.render() {
        val progress = TutorialProgress(done = done)
        val path = theKlangPathTrack

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "14px")
                put("padding", "18px 16px")
                put("color", "var(--kl-ink-text)")
            }

            appLink(router, Nav.tutorialTrack(path.slug)) {
                learnLabel("${path.title}, ${progress.doneIn(path)} of ${path.lessons.size} done")
            }

            lessonList(router = router, track = path, progress = progress, current = null)

            learnLabel("Other tracks")

            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "8px")
                    put("font-size", "13px")
                }

                for (track in allTracks.filter { it != path }) {
                    appLink(router, Nav.tutorialTrack(track.slug)) {
                        attributes["data-track"] = track.slug
                        css { put("display", "flex") }

                        span {
                            css { put("flex", "1") }
                            +track.title
                        }
                        span {
                            css {
                                put("font-size", "12px")
                                put("color", "var(--kl-ink-label)")
                            }

                            +"${progress.doneIn(track)} of ${track.lessons.size}"
                        }
                    }
                }
            }
        }
    }
}
