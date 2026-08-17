/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.routing.urlParam
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toggle
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Padding
import kotlinx.css.Position
import kotlinx.css.backgroundColor
import kotlinx.css.borderColor
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.fontSize
import kotlinx.css.gap
import kotlinx.css.marginLeft
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.TutorialsListPage() = comp {
    TutorialsListPage(it)
}

class TutorialsListPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    enum class CompletionFilter { All, Completed, Open }

    enum class ViewMode { Tracks, Lessons }

    companion object {
        const val PARAM_DIFFICULTY = "difficulty"
        const val PARAM_SCOPE = "scope"
        const val PARAM_COMPLETION = "completion"
        const val PARAM_VIEW = "view"

        fun viewFromParam(param: String?): ViewMode =
            ViewMode.entries.find { it.name.equals(param, ignoreCase = true) } ?: ViewMode.Tracks

        fun difficultyFromParam(param: String?): TutorialDifficulty? =
            TutorialDifficulty.entries.find { it.name.equals(param, ignoreCase = true) }

        fun scopeFromParam(param: String?): TutorialScope? =
            TutorialScope.entries.find { it.name.equals(param, ignoreCase = true) }

        fun completionFromParam(param: String?): CompletionFilter =
            CompletionFilter.entries.find { it.name.equals(param, ignoreCase = true) } ?: CompletionFilter.All
    }

    private var searchText by value("")

    private var difficultyParam: String by urlParam(name = PARAM_DIFFICULTY, default = "")
    private var selectedDifficulty: TutorialDifficulty?
        get() = difficultyFromParam(difficultyParam)
        set(value) {
            difficultyParam = value?.name ?: ""
        }

    private var scopeParam: String by urlParam(name = PARAM_SCOPE, default = "")
    private var selectedScope: TutorialScope?
        get() = scopeFromParam(scopeParam)
        set(value) {
            scopeParam = value?.name ?: ""
        }

    private var completionParam: String by urlParam(name = PARAM_COMPLETION, default = "")
    private var completionFilter: CompletionFilter
        get() = completionFromParam(completionParam)
        set(value) {
            completionParam = if (value == CompletionFilter.All) "" else value.name
        }

    private var selectedTags: Set<TutorialTag> by value(emptySet())

    private var viewParam: String by urlParam(name = PARAM_VIEW, default = "")
    private var viewMode: ViewMode
        get() = viewFromParam(viewParam)
        set(value) {
            viewParam = if (value == ViewMode.Tracks) "" else value.name
        }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleTag(tag: TutorialTag) {
        selectedTags = selectedTags.toggle(tag)
    }

    private fun matches(tutorial: Tutorial): Boolean {
        val matchesSearch = searchText.isBlank() ||
                tutorial.title.contains(searchText, ignoreCase = true) ||
                tutorial.description.contains(searchText, ignoreCase = true) ||
                tutorial.tags.any { it.label.contains(searchText, ignoreCase = true) }

        val matchesDifficulty = selectedDifficulty == null || tutorial.difficulty == selectedDifficulty
        val matchesScope = selectedScope == null || tutorial.scope == selectedScope
        val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in tutorial.tags }

        val matchesCompletion = when (completionFilter) {
            CompletionFilter.All -> true
            CompletionFilter.Completed -> TutorialStorage.isCompleted(tutorial.slug)
            CompletionFilter.Open -> !TutorialStorage.isCompleted(tutorial.slug)
        }

        return matchesSearch && matchesDifficulty && matchesScope && matchesTags && matchesCompletion
    }

    private fun filteredTutorials(): List<Tutorial> = allTutorials.filter { matches(it) }

    private fun anyFilterActive(): Boolean =
        searchText.isNotBlank() || selectedDifficulty != null || selectedScope != null ||
                selectedTags.isNotEmpty() || completionFilter != CompletionFilter.All

    override fun VDom.render() {
        ui.fluid.container {
            css { padding = Padding(2.rem) }

            ui.segment {
                ui.header { +"Tutorials" }
                ui.sub.header { +"Learn Klang step by step — from first notes to advanced techniques" }

                div {
                    css { marginTop = 1.rem }
                    ViewMode.entries.forEach { mode ->
                        val isSelected = viewMode == mode
                        ui.mini.givenNot(isSelected) { basic }
                            .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                onClick { viewMode = mode }
                                if (mode == ViewMode.Tracks) icon.map_signs() else icon.th_list()
                                +mode.name
                            }
                    }
                }
            }

            // Search and filters
            ui.segment {
                ui.form {
                    UiInputField(value = searchText, onChange = { searchText = it }) {
                        placeholder("Search tutorials...")
                        rightClearingIcon()
                        leftLabel {
                            ui.grey.label { icon.search(); +"Search" }
                        }
                    }
                }

                ui.divider()

                ui.stackable.grid {
                    ui.three.column.row {
                        // Difficulty filter
                        noui.column {
                            ui.mini.givenNot(selectedDifficulty == null) { basic }
                                .given(selectedDifficulty == null) { with(laf.styles.goldButton()) }.button {
                                    onClick { selectedDifficulty = null }
                                    icon.circle()
                                    +"All Levels"
                                }

                            TutorialDifficulty.entries.forEach { diff ->
                                val isSelected = selectedDifficulty == diff
                                ui.mini.givenNot(isSelected) { basic }
                                    .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                        onClick { selectedDifficulty = if (isSelected) null else diff }
                                        diff.renderIcon(this)
                                        +diff.label
                                    }
                            }
                        }

                        // Scope filter
                        noui.column {
                            ui.mini.givenNot(selectedScope == null) { basic }
                                .given(selectedScope == null) { with(laf.styles.goldButton()) }
                                .button {
                                    onClick { selectedScope = null }
                                    icon.circle()
                                    +"All Scopes"
                                }

                            TutorialScope.entries.forEach { scope ->
                                val isSelected = selectedScope == scope
                                ui.mini.givenNot(isSelected) { basic }
                                    .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                        onClick { selectedScope = if (isSelected) null else scope }
                                        scope.renderIcon(this)
                                        +scope.label
                                    }
                            }
                        }

                        // Completion Filter
                        noui.column {
                            CompletionFilter.entries.forEach { filter ->
                                val isSelected = completionFilter == filter
                                ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                                    onClick { completionFilter = filter }
                                    filter.renderIcon(this)
                                    +filter.name
                                }
                            }
                        }
                    }
                }

                ui.divider()

                // Tag filter — selected tags + add button
                div {
                    // Show unselected tags as basic buttons
                    TutorialTag.entries.forEach { tag ->
                        ui.mini.basic.given(tag in selectedTags) { with(laf.styles.goldButton()) }.button {
                            onClick { toggleTag(tag) }
                            +tag.label
                        }
                    }
                }
            }

            if (viewMode == ViewMode.Tracks) {
                renderTracksView()
                return@container
            }

            // Tutorial cards
            val tutorials = filteredTutorials()

            if (tutorials.isEmpty()) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.search()
                        if (allTutorials.isEmpty()) {
                            +"No tutorials yet"
                        } else {
                            +"No tutorials match your filters"
                        }
                    }
                }
            } else {
                ui.three.stackable.cards {
                    tutorials.forEach { tutorial ->
                        val isCompleted = TutorialStorage.isCompleted(tutorial.slug)

                        ui.card {
                            onClick { router.navToUri(Nav.tutorial(tutorial.slug)) }
                            css {
                                cursor = Cursor.pointer
                                backgroundColor = Color(laf.cardBackground)
                                position = Position.relative
                            }

                            // Completed checkmark in top-right
                            if (isCompleted) {
                                span {
                                    css {
                                        position = Position.absolute
                                        top = 8.px
                                        right = 8.px
                                        color = Color(laf.good)
                                        fontSize = 1.2.rem
                                    }
                                    icon.large.check_circle()
                                }
                            }

                            noui.content {
                                ui.small.header {
                                    css { color = Color(laf.textPrimary) }
                                    +tutorial.title
                                }
                                noui.meta {
                                    css {
                                        display = Display.flex
                                        gap = 0.25.rem
                                        marginTop = 0.25.rem
                                    }
                                    difficultyLabel(laf, tutorial.difficulty)
                                    scopeLabel(laf, tutorial.scope)
                                }
                                noui.description {
                                    css {
                                        marginTop = 0.5.rem
                                        color = Color(laf.textSecondary)
                                    }
                                    +tutorial.description
                                }
                            }
                            noui.extra.content {
                                css { borderColor = Color(laf.textTertiary) }
                                tutorial.tags.forEach { tag ->
                                    ui.mini.basic.label { +tag.label }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderTracksView() {
        val filtering = anyFilterActive()
        val visible = allTracks.mapNotNull { track ->
            val matching = track.lessons.filter { matches(it) }
            if (matching.isEmpty()) null else track to matching
        }

        if (visible.isEmpty()) {
            ui.placeholder.segment {
                ui.icon.header {
                    icon.search()
                    +"No lessons match your filters"
                }
            }
            return
        }

        visible.forEach { (track, matching) ->
            val completed = track.lessons.count { TutorialStorage.isCompleted(it.slug) }

            ui.segment {
                ui.medium.header {
                    css { cursor = Cursor.pointer }
                    onClick { router.navToUri(Nav.tutorialTrack(track.slug)) }
                    icon.map_signs()
                    +track.title
                    ui.mini.basic.label {
                        css { marginLeft = 0.75.rem }
                        icon.check_circle()
                        +"$completed of ${track.lessons.size} completed"
                    }
                }
                span {
                    css { color = Color(laf.textSecondary) }
                    +track.description
                }

                if (filtering && matching.size < track.lessons.size) {
                    div {
                        css { marginTop = 0.75.rem }

                        ui.mini.basic.label {
                            icon.search()
                            +"${matching.size} of ${track.lessons.size} match"
                        }
                    }
                }

                div {
                    css {
                        marginTop = 0.75.rem
                        display = Display.flex
                        gap = 0.25.rem
                    }

                    matching.forEach { lesson ->
                        val number = track.lessons.indexOf(lesson) + 1
                        val isCompleted = TutorialStorage.isCompleted(lesson.slug)

                        ui.mini.basic.button {
                            onClick { router.navToUri(Nav.tutorialInTrack(lesson.slug, track.slug)) }
                            if (isCompleted) icon.check_circle()
                            +"$number. ${lesson.title}"
                        }
                    }
                }
            }
        }
    }
}
