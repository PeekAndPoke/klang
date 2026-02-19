package io.peekandpoke.klang.layouts

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.Motoer
import kotlinx.css.*
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.pointerevents.PointerEvent

@Suppress("FunctionName")
fun Tag.SidebarMenu() = comp {
    SidebarMenu(it)
}

class SidebarMenu(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val currentRoute by subscribingTo(router.current)

    private sealed interface State {
        data object Main : State
        data object Songs : State
        data object Samples : State
        data object Docs : State
    }

    private fun inferState(): State = when (currentRoute.route) {
        Nav.editSongCode, Nav.newSongCode -> State.Songs
        Nav.samplesLibrary -> State.Samples
        Nav.strudelDocs -> State.Docs
        else -> State.Main
    }

    private var state: State by value(inferState())

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "sidebar-menu"
            css {
                backgroundColor = Color.black
                color = Color.white
                height = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                justifyContent = JustifyContent.spaceBetween
            }

            div {
                key = "menu-container-${state.hashCode()}"

                when (state) {
                    State.Main -> renderDefaultMenu()
                    State.Songs -> renderSongsMenu()
                    State.Samples -> renderSamplesMenu()
                    State.Docs -> renderDocsMenu()
                }
            }

            div {
                key = "motoer-container"

                Motoer()
            }
        }
    }

    private fun DIV.onItemClick(block: (evt: PointerEvent) -> Unit) {
        onClick { evt ->
            evt.stopPropagation()
            block(evt)
        }
    }

    private fun DIV.itemCss(isSelected: Boolean) {
        css {
            border = Border.none

            borderTopLeftRadius = 4.px
            borderBottomLeftRadius = 4.px
            borderTopRightRadius = 0.px
            borderBottomRightRadius = 0.px

            padding = Padding(10.px, 20.px)

            if (isSelected) {
                backgroundColor = Color.white
                color = Color.black
            } else {
                backgroundColor = Color.black
                color = Color.white

                borderRadius = 0.px
            }
        }
    }

    private fun DIV.menuItemsList(block: DIV.() -> Unit) {
        ui.vertical.relaxed.list {
            key = "menu-list"

            // Allow the list to grow and scroll if needed
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

    private fun DIV.itemContentCss(isSelected: Boolean) {
        css {
            if (isSelected) {
                color = Color.black
            } else {
                color = Color.white
            }
        }
    }

    private fun DIV.renderCategory(name: String) {
        ui.big.basic.segment {
            css {
                paddingBottom = 0.px
            }
            ui.large.item {
                onItemClick { state = State.Main }
                icon.angle_left()
                +name
            }
        }
    }

    private fun DIV.renderDefaultMenu() {
        menuItemsList {
            noui.item {
                itemCss(false)
                onItemClick {
                    state = State.Songs
                    router.navToUri(Nav.newSongCode())
                }
                +"Songs"
            }
            noui.item {
                itemCss(false)
                onItemClick {
                    state = State.Samples
                    router.navToUri(Nav.samplesLibrary())
                }
                +"Sound Samples Library"
            }
            noui.item {
                itemCss(false)
                onItemClick {
                    state = State.Docs
                    router.navToUri(Nav.strudelDocs())
                }
                +"Strudel DSL Documentation"
            }
        }
    }

    private fun DIV.renderSongsMenu() {
        val iNewSong = currentRoute.route == Nav.newSongCode
        val isEditSong = currentRoute.route == Nav.editSongCode
        val songId = currentRoute.matchedRoute["id"]

        renderCategory("Songs")

        menuItemsList {
            noui.item {
                itemCss(iNewSong)
                onItemClick { router.navToUri(Nav.newSongCode()) }
                icon.plus()
                noui.content {
                    itemContentCss(iNewSong)
                    +"New Song"
                }
            }

            BuiltInSongs.songs.forEach { song ->
                noui.item {
                    val isSelected = isEditSong && song.id == songId
                    itemCss(isSelected)
                    onItemClick { router.navToUri(Nav.editSongCode(song.id)) }
                    if (song.icon != null) {
                        icon.with(song.icon).render()
                    } else {
                        icon.music()
                    }
                    noui.content {
                        itemContentCss(isSelected)
                        +song.title
                    }
                }
            }
        }
    }

    private fun DIV.renderSamplesMenu() {
        renderCategory("Sound Samples Library")

        menuItemsList {
            noui.item {
                val isSelected = currentRoute.route == Nav.samplesLibrary
                itemCss(isSelected)
                onItemClick { router.navToUri(Nav.samplesLibrary()) }
                icon.music()
                noui.content {
                    itemContentCss(isSelected)
                    +"Explore"
                }
            }
        }
    }

    private fun DIV.renderDocsMenu() {
        renderCategory("Strudel DSL Documentation")

        menuItemsList {
            noui.item {
                val isSelected = currentRoute.route == Nav.strudelDocs
                itemCss(isSelected)
                onItemClick { router.navToUri(Nav.strudelDocs()) }
                icon.book()
                noui.content {
                    itemContentCss(isSelected)
                    +"Functions"
                }
            }
        }
    }
}
