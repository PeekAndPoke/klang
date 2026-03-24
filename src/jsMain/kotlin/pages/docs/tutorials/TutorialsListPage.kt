package io.peekandpoke.klang.pages.docs.tutorials

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.routing.urlParam
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toggle
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
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

    companion object {
        const val PARAM_DIFFICULTY = "difficulty"
        const val PARAM_SCOPE = "scope"
        const val PARAM_COMPLETION = "completion"

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

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleTag(tag: TutorialTag) {
        selectedTags = selectedTags.toggle(tag)
    }

    private fun filteredTutorials(): List<Tutorial> {
        return allTutorials.filter { tutorial ->
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

            matchesSearch && matchesDifficulty && matchesScope && matchesTags && matchesCompletion
        }
    }

    override fun VDom.render() {
        ui.fluid.container {
            css { padding = Padding(2.rem) }

            ui.segment {
                ui.header { +"Tutorials" }
                ui.sub.header { +"Learn Klang step by step — from first notes to advanced techniques" }
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
}
