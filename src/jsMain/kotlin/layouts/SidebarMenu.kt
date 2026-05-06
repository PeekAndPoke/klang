package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.Motoer
import io.peekandpoke.klang.pages.docs.tutorials.TutorialDifficulty
import io.peekandpoke.klang.pages.docs.tutorials.TutorialScope
import io.peekandpoke.klang.pages.docs.tutorials.TutorialsListPage
import io.peekandpoke.klang.pages.docs.tutorials.iconFn
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Border
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.Margin
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.border
import kotlinx.css.borderBottomLeftRadius
import kotlinx.css.borderBottomRightRadius
import kotlinx.css.borderRadius
import kotlinx.css.borderTopLeftRadius
import kotlinx.css.borderTopRightRadius
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.flexGrow
import kotlinx.css.fontSize
import kotlinx.css.gap
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.margin
import kotlinx.css.minHeight
import kotlinx.css.overflowY
import kotlinx.css.padding
import kotlinx.css.paddingLeft
import kotlinx.css.paddingTop
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.pointerevents.PointerEvent

@Suppress("FunctionName")
fun Tag.SidebarMenu() = comp {
    SidebarMenu(it)
}

class SidebarMenu(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private val currentRoute by subscribingTo(router.current)

    private sealed interface State {
        data object Main : State
        data object Songs : State
        data object Samples : State
        data object Tutorials : State
        data object Docs : State
        data object Credits : State
    }

    private fun inferState(): State = when {
        currentRoute.route in listOf(Nav.editSongCode, Nav.newSongCode) -> State.Songs
        currentRoute.route in listOf(Nav.samplesLibrary) -> State.Samples
        currentRoute.route.pattern.startsWith(Nav.tutorialsBase) -> State.Tutorials
        currentRoute.route.pattern.startsWith(Nav.manualsBase) -> State.Docs
        currentRoute.route == Nav.credits -> State.Credits
        else -> State.Main
    }

    private var state: State by value(inferState())

    //  MENU ITEM HELPER  /////////////////////////////////////////////////////////////////////////////////////

    /**
     * Renders a single menu item with consistent styling.
     * Selected items get white background, black text, and red icon.
     */
    private fun DIV.menuItem(
        isSelected: Boolean,
        label: String,
        iconFn: SemanticIconFn? = null,
        onClick: (PointerEvent) -> Unit,
    ) {
        noui.item {
            css {
                border = Border.none
                borderTopLeftRadius = 4.px
                borderBottomLeftRadius = 4.px
                borderTopRightRadius = 0.px
                borderBottomRightRadius = 0.px
                padding = Padding(10.px, 20.px)

                if (isSelected) {
                    backgroundColor = Color.white
                    color = Color(laf.critical)
                } else {
                    backgroundColor = Color(laf.menuBackground)
                    color = Color.white
                    borderRadius = 0.px
                }
            }
            this.onClick { evt ->
                evt.stopPropagation()
                onClick(evt)
            }

            if (iconFn != null) {
                icon.iconFn().then {
                    if (isSelected) {
                        css { put("color", "${laf.critical} !important") }
                    }
                }
            }

            noui.content {
                css {
                    color = if (isSelected) Color.black else Color.white
                }
                +label
            }
        }
    }

    private fun DIV.menuGap() {
        noui.item { css { padding = Padding(8.px) } }
    }

    private fun DIV.menuItemsList(block: DIV.() -> Unit) {
        ui.vertical.relaxed.list {
            key = "menu-list"
            css {
                paddingTop = 16.px
                paddingLeft = 16.px
                flexGrow = 1.0
                overflowY = Overflow.auto
                minHeight = 0.px
            }
            block()
        }
    }

    //  ICON BAR  ////////////////////////////////////////////////////////////////////////////////////////////////

    private data class IconBarEntry(
        val targetState: State,
        val iconFn: SemanticIconFn,
        val title: String,
        val navigate: () -> Unit,
    )

    private fun DIV.renderIconBar() {
        val entries = listOf(
            IconBarEntry(State.Songs, { music }, "Write Songs") {
                state = State.Songs
                router.navToUri(Nav.newSongCode())
            },
            IconBarEntry(State.Tutorials, { graduation_cap }, "Tutorials") {
                state = State.Tutorials
                router.navToUri(Nav.tutorials())
            },
            IconBarEntry(State.Docs, { code }, "Documentation") {
                state = State.Docs
                router.navToUri(Nav.manuals())
            },
            IconBarEntry(State.Main, { ellipsis_horizontal }, "Discover more") {
                state = State.Main
            },
        )

        div {
            key = "icon-bar"
            css {
                display = Display.flex
                justifyContent = JustifyContent.center
                gap = 4.px
                padding = Padding(12.px, 16.px, 0.px, 16.px)
            }

            for (entry in entries) {
                val isSelected = when (entry.targetState) {
                    // "More" is selected only when we're on Main, Samples, or Credits
                    State.Main -> state in listOf(State.Main, State.Samples, State.Credits)
                    else -> state == entry.targetState
                }

                div {
                    key = "icon-bar-${entry.targetState}"
                    css {
                        width = 40.px
                        height = 40.px
                        display = Display.flex
                        alignItems = Align.center
                        justifyContent = JustifyContent.center
                        borderRadius = 8.px
                        cursor = Cursor.pointer

                        if (isSelected) {
                            backgroundColor = Color.white
                        } else {
                            backgroundColor = Color.transparent
                        }
                    }
                    title = entry.title
                    onClick { evt ->
                        evt.stopPropagation()
                        entry.navigate()
                    }

                    icon.(entry.iconFn)().then {
                        css {
                            margin = Margin(0.px)
                            fontSize = 18.px
                            if (isSelected) {
                                put("color", "${laf.critical} !important")
                            } else {
                                put("color", "white !important")
                            }
                        }
                    }
                }
            }
        }
    }

    //  NAV HELPERS  ///////////////////////////////////////////////////////////////////////////////////////////

    private fun navTutorials(vararg params: Pair<String, String>) {
        router.navToUri(Nav.tutorialsWithUpdatedParams(router = router, *params))
    }

    private fun toggleTutorialFilter(paramKey: String, value: String, isActive: Boolean) {
        navTutorials(paramKey to if (isActive) "" else value)
    }

    //  RENDER  ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "sidebar-menu"
            css {
                backgroundColor = Color(laf.menuBackground)
                color = Color.white
                height = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                justifyContent = JustifyContent.spaceBetween
            }

            div {
                key = "menu-top"
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    minHeight = 0.px
                    flexGrow = 1.0
                }

                renderIconBar()

                div {
                    key = "menu-container-${state.hashCode()}"
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        minHeight = 0.px
                        flexGrow = 1.0
                    }
                    when (state) {
                        State.Main, State.Credits -> renderDefaultMenu()
                        State.Songs -> renderSongsMenu()
                        State.Samples -> renderSamplesMenu()
                        State.Tutorials -> renderTutorialsMenu()
                        State.Docs -> renderDocsMenu()
                    }
                }
            }

            div {
                key = "motoer-container"
                Motoer()
            }
        }
    }

    private fun DIV.renderDefaultMenu() {
        menuItemsList {
            menuItem(state == State.Samples, "Samples Library", { wave_square }) {
                state = State.Samples
                router.navToUri(Nav.samplesLibrary())
            }
            menuItem(state == State.Credits, "Credits", { bullhorn }) {
                state = State.Credits
                router.navToUri(Nav.credits())
            }
        }
    }

    private fun DIV.renderSongsMenu() {
        val isNewSong = currentRoute.route == Nav.newSongCode
        val isEditSong = currentRoute.route == Nav.editSongCode
        val songId = currentRoute.matchedRoute["id"]

        menuItemsList {
            menuItem(isNewSong, "New Song", { plus }) {
                router.navToUri(Nav.newSongCode())
            }

            BuiltInSongs.songs.forEach { song ->
                val isSelected = isEditSong && song.id == songId
                menuItem(isSelected, song.title, {
                    if (song.icon != null) with(song.icon) else music
                }) {
                    router.navToUri(Nav.editSongCode(song.id))
                }
            }
        }
    }

    private fun DIV.renderSamplesMenu() {
        menuItemsList {
            menuItem(currentRoute.route == Nav.samplesLibrary, "Explore", { music }) {
                router.navToUri(Nav.samplesLibrary())
            }
        }
    }

    private fun DIV.renderDocsMenu() {
        menuItemsList {
            menuItem(currentRoute.route == Nav.manualsLexikon, "Lexikon", { book }) {
                router.navToUri(Nav.manualsLexikon())
            }
            menuItem(currentRoute.route == Nav.manualsLibrary && currentRoute.matchedRoute["library"] == "sprudel", "Sprudel", { wind }) {
                router.navToUri(Nav.manualsLibrary("sprudel"))
            }
            menuItem(currentRoute.route == Nav.manualsLibrary && currentRoute.matchedRoute["library"] == "stdlib", "Stdlib", { cogs }) {
                router.navToUri(Nav.manualsLibrary("stdlib"))
            }
            menuItem(currentRoute.route == Nav.manualsKlangScript, "KlangScript", { code }) {
                router.navToUri(Nav.manualsKlangScript())
            }
        }
    }

    private fun DIV.renderTutorialsMenu() {
        val activeDifficulty = currentDifficultyFilter()
        val activeScope = currentScopeFilter()
        val activeCompletion = currentCompletionFilter()
        val hasNoFilters = activeDifficulty == null && activeScope == null &&
                activeCompletion == TutorialsListPage.CompletionFilter.All

        menuItemsList {
            menuItem(currentRoute.route == Nav.tutorials && hasNoFilters, "All Tutorials", { list }) {
                router.navToUri(Nav.tutorials())
            }

            menuGap()

            for (diff in TutorialDifficulty.entries) {
                val isSelected = activeDifficulty == diff
                menuItem(isSelected, diff.label, diff.iconFn()) {
                    toggleTutorialFilter(TutorialsListPage.PARAM_DIFFICULTY, diff.name, isSelected)
                }
            }

            menuGap()

            for (scope in TutorialScope.entries) {
                val isSelected = activeScope == scope
                menuItem(isSelected, scope.label, scope.iconFn()) {
                    toggleTutorialFilter(TutorialsListPage.PARAM_SCOPE, scope.name, isSelected)
                }
            }

            menuGap()

            for (filter in listOf(TutorialsListPage.CompletionFilter.Completed, TutorialsListPage.CompletionFilter.Open)) {
                val isSelected = activeCompletion == filter
                menuItem(isSelected, filter.name, filter.iconFn()) {
                    toggleTutorialFilter(TutorialsListPage.PARAM_COMPLETION, filter.name, isSelected)
                }
            }
        }
    }

    private fun currentDifficultyFilter(): TutorialDifficulty? {
        val param = currentRoute.matchedRoute.queryParams[TutorialsListPage.PARAM_DIFFICULTY]
        return TutorialsListPage.difficultyFromParam(param)
    }

    private fun currentScopeFilter(): TutorialScope? {
        val param = currentRoute.matchedRoute.queryParams[TutorialsListPage.PARAM_SCOPE]
        return TutorialsListPage.scopeFromParam(param)
    }

    private fun currentCompletionFilter(): TutorialsListPage.CompletionFilter {
        val param = currentRoute.matchedRoute.queryParams[TutorialsListPage.PARAM_COMPLETION]
        return TutorialsListPage.completionFromParam(param)
    }
}
