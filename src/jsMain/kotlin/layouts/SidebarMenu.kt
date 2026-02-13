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

@Suppress("FunctionName")
fun Tag.SidebarMenu() = comp {
    SidebarMenu(it)
}

class SidebarMenu(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val currentRoute by subscribingTo(router.current)

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
                key = "menu-container"

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

                    val iNewSong = currentRoute.route == Nav.newSongCode
                    val isEditSong = currentRoute.route == Nav.editSongCode
                    val songId = currentRoute.matchedRoute["id"]

                    fun DIV.itemCss(isSelected: Boolean) {
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

                    fun DIV.itemContentCss(isSelected: Boolean) {
                        css {
                            if (isSelected) {
                                color = Color.black
                            } else {
                                color = Color.white
                            }
                        }
                    }

                    ui.item {
                        itemCss(iNewSong)
                        onClick { router.navToUri(Nav.newSongCode()) }
                        icon.plus()
                        noui.content {
                            itemContentCss(iNewSong)
                            +"New Song"
                        }
                    }

                    BuiltInSongs.songs.forEach { song ->
                        ui.item {
                            val isSelected = isEditSong && song.id == songId
                            itemCss(isSelected)
                            onClick { router.navToUri(Nav.editSongCode(song.id)) }
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

            div {
                key = "motoer-container"

                Motoer()
            }
        }
    }
}
