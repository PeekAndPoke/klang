package io.peekandpoke.klang.pages.docs.tutorials

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.routing.urlParam
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
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

    companion object {
        const val PARAM_DIFFICULTY = "difficulty"
    }

    private enum class CompletionFilter { All, Completed, Open }

    private var searchText by value("")
    private var difficultyParam: String by urlParam(name = PARAM_DIFFICULTY, default = "")
    private var selectedDifficulty: TutorialDifficulty?
        get() = TutorialDifficulty.entries.find { it.name.equals(difficultyParam, ignoreCase = true) }
        set(value) {
            difficultyParam = value?.name ?: ""
        }
    private var selectedScope: TutorialScope? by value(null)
    private var selectedTag: String? by value(null)
    private var completionFilter: CompletionFilter by value(CompletionFilter.All)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun filteredTutorials(): List<Tutorial> {
        return allTutorials.filter { tutorial ->
            val matchesSearch = searchText.isBlank() ||
                    tutorial.title.contains(searchText, ignoreCase = true) ||
                    tutorial.description.contains(searchText, ignoreCase = true) ||
                    tutorial.tags.any { it.contains(searchText, ignoreCase = true) }

            val matchesDifficulty = selectedDifficulty == null || tutorial.difficulty == selectedDifficulty
            val matchesScope = selectedScope == null || tutorial.scope == selectedScope
            val matchesTag = selectedTag == null || tutorial.tags.contains(selectedTag)

            val matchesCompletion = when (completionFilter) {
                CompletionFilter.All -> true
                CompletionFilter.Completed -> TutorialStorage.isCompleted(tutorial.slug)
                CompletionFilter.Open -> !TutorialStorage.isCompleted(tutorial.slug)
            }

            matchesSearch && matchesDifficulty && matchesScope && matchesTag && matchesCompletion
        }
    }

    private fun allTags(): List<String> = allTutorials.flatMap { it.tags }.distinct().sorted()

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

                // Difficulty filter
                div {
                    css {
                        display = Display.flex
                        gap = 0.5.rem
                        flexWrap = FlexWrap.wrap
                        marginTop = 1.rem
                        marginBottom = 0.5.rem
                    }

                    ui.mini.givenNot(selectedDifficulty == null) { basic }
                        .given(selectedDifficulty == null) { with(laf.styles.goldButton()) }.button {
                        onClick { selectedDifficulty = null }
                        +"All Levels"
                    }

                    TutorialDifficulty.entries.forEach { diff ->
                        val isSelected = selectedDifficulty == diff
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            onClick { selectedDifficulty = if (isSelected) null else diff }
                            +diff.label
                        }
                    }
                }

                // Scope filter
                div {
                    css {
                        display = Display.flex
                        gap = 0.5.rem
                        flexWrap = FlexWrap.wrap
                        marginBottom = 0.5.rem
                    }

                    ui.mini.givenNot(selectedScope == null) { basic }.given(selectedScope == null) { with(laf.styles.goldButton()) }
                        .button {
                            onClick { selectedScope = null }
                            +"All Scopes"
                        }

                    TutorialScope.entries.forEach { scope ->
                        val isSelected = selectedScope == scope
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            onClick { selectedScope = if (isSelected) null else scope }
                            +scope.label
                        }
                    }
                }

                // Completion filter
                div {
                    css {
                        display = Display.flex
                        gap = 0.5.rem
                        flexWrap = FlexWrap.wrap
                        marginBottom = 0.5.rem
                    }

                    CompletionFilter.entries.forEach { filter ->
                        val isSelected = completionFilter == filter
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            onClick { completionFilter = filter }
                            when (filter) {
                                CompletionFilter.All -> +"All"
                                CompletionFilter.Completed -> {
                                    icon.check(); +"Completed"
                                }

                                CompletionFilter.Open -> {
                                    icon.circle_outline(); +"Open"
                                }
                            }
                        }
                    }
                }

                // Tag filter
                val tags = allTags()
                if (tags.isNotEmpty()) {
                    div {
                        css {
                            display = Display.flex
                            gap = 0.5.rem
                            flexWrap = FlexWrap.wrap
                        }

                        ui.mini.givenNot(selectedTag == null) { basic }.given(selectedTag == null) { with(laf.styles.goldButton()) }
                            .button {
                                onClick { selectedTag = null }
                                +"All Topics"
                            }

                        tags.forEach { tag ->
                            val isSelected = selectedTag == tag
                            ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                                onClick { selectedTag = if (isSelected) null else tag }
                                +tag
                            }
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
                                    ui.mini.basic.label { +tag }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
