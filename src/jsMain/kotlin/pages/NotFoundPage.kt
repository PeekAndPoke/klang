package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.JoinedPageTitle
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Nav
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.NotFoundPage() = comp {
    NotFoundPage(it)
}

class NotFoundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        JoinedPageTitle { listOf("Dragons", "404") }

        div {
            css {
                height = 100.vh
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                backgroundColor = Color.black
                color = Color.white
                textAlign = TextAlign.center
                cursor = Cursor.pointer
            }

            onClick {
                router.navToUri(Nav.dashboard())
            }

            div {
                icon.huge.dragon()
            }

            ui.hidden.divider()

            div {

                div { +"You got lost!" }
                div { +"And you are surrounded by" }
                div {
                    css {
                        padding = Padding(24.px)
                        fontSize = 3.em
                    }
                    +"404"
                }
                div { +"dragons!" }
            }
        }
    }
}
