/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.pages.resources.Resource
import io.peekandpoke.klang.pages.resources.ResourceOrigin
import io.peekandpoke.klang.pages.resources.ResourceSource
import io.peekandpoke.klang.pages.resources.ResourceTag
import io.peekandpoke.klang.pages.resources.allResources
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
fun Tag.ResourcesPage() = comp {
    ResourcesPage(it)
}

/**
 * The resources shelf: cards for videos worth watching and pages worth visiting. Videos play
 * in place, websites open in a new tab.
 *
 * External material stays visibly external: the card credits its author, carries a
 * [ResourceOrigin] label, and always offers the way out to the place it lives.
 */
class ResourcesPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    private var searchText: String by value("")

    private var selectedTags: Set<ResourceTag> by value(emptySet())

    /** Id of the resource whose player is open. One at a time, so no two soundtracks collide. */
    private var playingId: String? by value(null)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleTag(tag: ResourceTag) {
        selectedTags = selectedTags.toggle(tag)
    }

    private fun matches(resource: Resource): Boolean {
        val matchesSearch = searchText.isBlank() ||
                resource.title.contains(searchText, ignoreCase = true) ||
                resource.description.contains(searchText, ignoreCase = true) ||
                resource.author.contains(searchText, ignoreCase = true) ||
                resource.tags.any { it.label.contains(searchText, ignoreCase = true) }

        val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in resource.tags }

        return matchesSearch && matchesTags
    }

    override fun VDom.render() {
        ui.fluid.container {
            key = "resources-page"

            css { padding = Padding(2.rem) }

            ui.segment {
                ui.header { +"Resources" }
                ui.sub.header { +"Videos worth watching, tools worth trying, pages worth reading. Everything here is other people's work, credited and linked." }
            }

            ui.segment {
                ui.form {
                    UiInputField(value = searchText, onChange = { searchText = it }) {
                        placeholder("Search resources...")
                        rightClearingIcon()
                        leftLabel {
                            ui.grey.label { icon.search(); +"Search" }
                        }
                    }
                }

                ui.divider()

                div {
                    ResourceTag.entries.forEach { tag ->
                        ui.mini.basic.given(tag in selectedTags) { with(laf.styles.goldButton()) }.button {
                            onClick { toggleTag(tag) }
                            +tag.label
                        }
                    }
                }
            }

            val resources = allResources.filter { matches(it) }

            if (resources.isEmpty()) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.search()
                        if (allResources.isEmpty()) {
                            +"No resources yet"
                        } else {
                            +"No resources match your filters"
                        }
                    }
                }
            } else {
                ui.three.stackable.cards {
                    resources.forEach { renderResourceCard(it) }
                }
            }
        }
    }

    private fun FlowContent.renderResourceCard(resource: Resource) {
        ui.card {
            key = "resource-${resource.id}"

            css { backgroundColor = Color(laf.cardBackground) }

            when (val source = resource.source) {
                is ResourceSource.Video -> if (playingId == resource.id) {
                    renderPlayer(resource, source)
                } else {
                    renderThumbnail(resource, source)
                }

                is ResourceSource.Website -> renderWebsiteHeader(resource, source)
            }

            noui.content {
                ui.small.header {
                    css { color = Color(laf.textPrimary) }
                    +resource.title
                }

                noui.meta {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        gap = 0.5.rem
                        marginTop = 0.25.rem
                    }

                    renderOriginLabel(resource.origin)
                    renderAuthor(resource)
                }

                noui.description {
                    css {
                        marginTop = 0.5.rem
                        color = Color(laf.textSecondary)
                    }
                    +resource.description
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
                    resource.tags.forEach { tag ->
                        ui.mini.basic.label { +tag.label }
                    }
                }

                a(href = resource.source.url, target = "_blank") {
                    css {
                        // Pushed right whatever the tag row does, and never gold:
                        // the global `a` rule carries !important, so this one has to as well
                        marginLeft = LinearDimension.auto
                        put("color", "${laf.textPrimary} !important")
                    }
                    title = "Open on ${resource.source.platform}"
                    icon.external_alternate()
                    +resource.source.platform
                }
            }
        }
    }

    /** The still, with a play overlay. Clicking swaps it for the embedded player. */
    private fun FlowContent.renderThumbnail(resource: Resource, source: ResourceSource.Video) {
        noui.image {
            css {
                position = Position.relative
                cursor = Cursor.pointer
                // Crops the platform's letterbox bars off a 4:3 still
                put("aspect-ratio", "16 / 9")
                put("overflow", "hidden")
            }
            onClick { playingId = resource.id }

            img(src = source.imageUrl, alt = resource.title) {
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
    private fun FlowContent.renderPlayer(resource: Resource, source: ResourceSource.Video) {
        noui.image {
            css { put("aspect-ratio", "16 / 9") }

            iframe {
                src = source.embedUrl(autoplay = true)
                title = resource.title
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

    /**
     * The card top for a website: its image when it has one, a globe on the card background
     * otherwise. Either way the whole area is the link, opening in a new tab.
     */
    private fun FlowContent.renderWebsiteHeader(resource: Resource, source: ResourceSource.Website) {
        noui.image {
            css {
                put("aspect-ratio", "16 / 9")
                put("overflow", "hidden")
            }

            a(href = source.url, target = "_blank") {
                title = "Open on ${source.platform}"

                css {
                    width = 100.pct
                    height = 100.pct
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    backgroundColor = Color(laf.cardBackground)
                }

                val imageUrl = source.imageUrl

                if (imageUrl == null) {
                    icon.huge.globe {
                        css { put("color", "${laf.textTertiary} !important") }
                    }
                } else {
                    img(src = imageUrl, alt = resource.title) {
                        css {
                            width = 100.pct
                            height = 100.pct
                            display = Display.block
                            put("object-fit", "cover")
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderOriginLabel(origin: ResourceOrigin) {
        when (origin) {
            ResourceOrigin.Klang -> ui.mini.with(laf.styles.goldButton()).label {
                icon.bolt()
                +origin.label
            }

            ResourceOrigin.External -> ui.mini.basic.label {
                icon.external_alternate()
                +origin.label
            }
        }
    }

    private fun FlowContent.renderAuthor(resource: Resource) {
        val authorUrl = resource.authorUrl

        if (authorUrl == null) {
            span {
                css { color = Color(laf.textSecondary) }
                +resource.author
            }
        } else {
            a(href = authorUrl, target = "_blank") {
                css { color = Color(laf.textSecondary) }
                +resource.author
            }
        }
    }
}
