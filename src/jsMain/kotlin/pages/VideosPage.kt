/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.pages.videos.Video
import io.peekandpoke.klang.pages.videos.VideoOrigin
import io.peekandpoke.klang.pages.videos.VideoTag
import io.peekandpoke.klang.pages.videos.allVideos
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toggle
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Border
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.Padding
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.border
import kotlinx.css.borderColor
import kotlinx.css.bottom
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.gap
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.left
import kotlinx.css.marginLeft
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.iframe
import kotlinx.html.img
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.VideosPage() = comp {
    VideosPage(it)
}

/**
 * The video shelf: cards for videos worth watching, playing in place.
 *
 * External material stays visibly external: the card credits its author, carries an
 * [VideoOrigin] label, and always offers the way out to the platform it lives on.
 */
class VideosPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    private var searchText: String by value("")

    private var selectedTags: Set<VideoTag> by value(emptySet())

    /** Id of the video whose player is open. One at a time, so no two soundtracks collide. */
    private var playingId: String? by value(null)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleTag(tag: VideoTag) {
        selectedTags = selectedTags.toggle(tag)
    }

    private fun matches(video: Video): Boolean {
        val matchesSearch = searchText.isBlank() ||
                video.title.contains(searchText, ignoreCase = true) ||
                video.description.contains(searchText, ignoreCase = true) ||
                video.author.contains(searchText, ignoreCase = true) ||
                video.tags.any { it.label.contains(searchText, ignoreCase = true) }

        val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in video.tags }

        return matchesSearch && matchesTags
    }

    override fun VDom.render() {
        ui.fluid.container {
            key = "videos-page"

            css { padding = Padding(2.rem) }

            ui.segment {
                ui.header { +"Videos" }
                ui.sub.header { +"Videos worth watching. Everything here is other people's work, credited and linked." }
            }

            ui.segment {
                ui.form {
                    UiInputField(value = searchText, onChange = { searchText = it }) {
                        placeholder("Search videos...")
                        rightClearingIcon()
                        leftLabel {
                            ui.grey.label { icon.search(); +"Search" }
                        }
                    }
                }

                ui.divider()

                div {
                    VideoTag.entries.forEach { tag ->
                        ui.mini.basic.given(tag in selectedTags) { with(laf.styles.goldButton()) }.button {
                            onClick { toggleTag(tag) }
                            +tag.label
                        }
                    }
                }
            }

            val videos = allVideos.filter { matches(it) }

            if (videos.isEmpty()) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.search()
                        if (allVideos.isEmpty()) {
                            +"No videos yet"
                        } else {
                            +"No videos match your filters"
                        }
                    }
                }
            } else {
                ui.three.stackable.cards {
                    videos.forEach { renderVideoCard(it) }
                }
            }
        }
    }

    private fun FlowContent.renderVideoCard(video: Video) {
        ui.card {
            key = "video-${video.id}"

            css { backgroundColor = Color(laf.cardBackground) }

            if (playingId == video.id) {
                renderPlayer(video)
            } else {
                renderThumbnail(video)
            }

            noui.content {
                ui.small.header {
                    css { color = Color(laf.textPrimary) }
                    +video.title
                }

                noui.meta {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        gap = 0.5.rem
                        marginTop = 0.25.rem
                    }

                    renderOriginLabel(video.origin)
                    renderAuthor(video)
                }

                noui.description {
                    css {
                        marginTop = 0.5.rem
                        color = Color(laf.textSecondary)
                    }
                    +video.description
                }
            }

            noui.extra.content {
                css {
                    borderColor = Color(laf.textTertiary)
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.spaceBetween
                    gap = 0.5.rem
                }

                div {
                    video.tags.forEach { tag ->
                        ui.mini.basic.label { +tag.label }
                    }
                }

                a(href = video.source.watchUrl, target = "_blank") {
                    css {
                        // Pushed right whatever the tag row does, and never gold:
                        // the global `a` rule carries !important, so this one has to as well
                        marginLeft = LinearDimension.auto
                        put("color", "${laf.textPrimary} !important")
                    }
                    title = "Watch on ${video.source.platform}"
                    icon.external_alternate()
                    +video.source.platform
                }
            }
        }
    }

    /** The still, with a play overlay. Clicking swaps it for the embedded player. */
    private fun FlowContent.renderThumbnail(video: Video) {
        noui.image {
            css {
                position = Position.relative
                cursor = Cursor.pointer
                // Crops the platform's letterbox bars off a 4:3 still
                put("aspect-ratio", "16 / 9")
                put("overflow", "hidden")
            }
            onClick { playingId = video.id }

            img(src = video.source.thumbnailUrl, alt = video.title) {
                css {
                    width = 100.pct
                    height = 100.pct
                    display = Display.block
                    put("object-fit", "cover")
                }
            }

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    right = 0.px
                    bottom = 0.px
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    put("background-color", "rgba(0,0,0,0.25)")
                }

                icon.huge.play_circle {
                    css { put("color", "white !important") }
                }
            }
        }
    }

    /** The embedded player. Autoplays: it only ever appears right after a click. */
    private fun FlowContent.renderPlayer(video: Video) {
        noui.image {
            css { put("aspect-ratio", "16 / 9") }

            iframe {
                src = video.source.embedUrl(autoplay = true)
                title = video.title
                attributes["allow"] = "accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share"
                attributes["allowfullscreen"] = "true"
                attributes["referrerpolicy"] = "strict-origin-when-cross-origin"
                css {
                    width = 100.pct
                    height = 100.pct
                    display = Display.block
                    border = Border.none
                }
            }
        }
    }

    private fun FlowContent.renderOriginLabel(origin: VideoOrigin) {
        when (origin) {
            VideoOrigin.Klang -> ui.mini.with(laf.styles.goldButton()).label {
                icon.bolt()
                +origin.label
            }

            VideoOrigin.External -> ui.mini.basic.label {
                icon.external_alternate()
                +origin.label
            }
        }
    }

    private fun FlowContent.renderAuthor(video: Video) {
        val authorUrl = video.authorUrl

        if (authorUrl == null) {
            span {
                css { color = Color(laf.textSecondary) }
                +video.author
            }
        } else {
            a(href = authorUrl, target = "_blank") {
                css { color = Color(laf.textSecondary) }
                +video.author
            }
        }
    }
}
