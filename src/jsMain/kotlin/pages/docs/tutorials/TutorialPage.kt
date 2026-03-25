package io.peekandpoke.klang.pages.docs.tutorials

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.TutorialPage() = comp {
    TutorialPage(it)
}

class TutorialPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private val currentRoute by subscribingTo(router.current)

    private var completedState by value(false)

    private fun currentSlug(): String = currentRoute.matchedRoute["slug"]

    private fun currentTutorial(): Tutorial? = allTutorials.find { it.slug == currentSlug() }

    private fun adjacentTutorials(): Pair<Tutorial?, Tutorial?> {
        val tutorial = currentTutorial() ?: return null to null
        val idx = allTutorials.indexOf(tutorial)
        val prev = if (idx > 0) allTutorials[idx - 1] else null
        val next = if (idx < allTutorials.size - 1) allTutorials[idx + 1] else null
        return prev to next
    }

    init {
        lifecycle {
            onMount {
                completedState = TutorialStorage.isCompleted(currentSlug())
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val tutorial = currentTutorial()

        ui.fluid.container {
            css { padding = Padding(2.rem) }

            if (tutorial == null) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.exclamation_triangle()
                        +"Tutorial not found"
                    }
                    ui.button {
                        onClick { router.navToUri(Nav.tutorials()) }
                        +"Back to Tutorials"
                    }
                }
                return@container
            }

            // Nav
            renderNav()

            // Header
            ui.segment {
                css { position = Position.relative }

                ui.large.header { +tutorial.title }

                if (completedState) {
                    span {
                        css {
                            position = Position.absolute
                            top = 12.px
                            right = 12.px
                            color = Color(laf.good)
                        }
                        icon.large.check_circle()
                    }
                }
                difficultyLabel(laf, tutorial.difficulty)
                scopeLabel(laf, tutorial.scope)
                tutorial.tags.forEach { tag ->
                    ui.mini.basic.label { +tag.label }
                }
                p {
                    css { marginTop = 1.rem }
                    +tutorial.description
                }
            }

            // Sections
            tutorial.sections.forEach { section ->
                ui.segment {
                    if (section.heading != null) {
                        ui.medium.header { +section.heading }
                    }

                    if (section.text.isNotBlank()) {
                        p { +section.text }
                    }

                    if (section.code != null) {
                        PlayableCodeExample(code = section.code)
                    }
                }
            }

            // Completed button
            ui.center.aligned.segment {
                if (completedState) {
                    ui.given(true) { with(laf.styles.goldButton()) }.button {
                        onClick {
                            completedState = TutorialStorage.toggleCompleted(tutorial.slug)
                        }
                        icon.check_circle()
                        +"Completed"
                    }
                } else {
                    ui.basic.button {
                        onClick {
                            completedState = TutorialStorage.toggleCompleted(tutorial.slug)
                        }
                        icon.circle_outline()
                        +"Mark as Completed"
                    }
                }
            }

            renderNav()
        }
    }

    private fun FlowContent.renderNav() {
        // Navigation
        val (prev, next) = adjacentTutorials()

        ui.segment {
            css {
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                alignItems = Align.center
            }

            ui.black.button {
                onClick { router.navToUri(Nav.tutorials()) }
                icon.th_list()
                +"All Tutorials"
            }

            div {
                if (prev != null) {
                    ui.black.button {
                        onClick { router.navToUri(Nav.tutorial(prev.slug)) }
                        icon.arrow_left()
                        +prev.title
                    }
                }

                if (next != null) {
                    ui.black.button {
                        onClick { router.navToUri(Nav.tutorial(next.slug)) }
                        +next.title
                        icon.arrow_right()
                    }
                }
            }
        }
    }
}
