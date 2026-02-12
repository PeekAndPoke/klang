package io.peekandpoke.klang.layouts

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.MenuLayout(
    inner: FlowContent.() -> Unit,
) = comp(
    MenuLayout.Props(inner = inner)
) {
    MenuLayout(it)
}

class MenuLayout(ctx: Ctx<Props>) : Component<MenuLayout.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val inner: FlowContent.() -> Unit,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////


    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "menu-layout"
            css {
                width = 100.pct
                height = 100.vh
                maxHeight = 100.vh
                display = Display.flex
                overflow = Overflow.hidden
            }

            div {
                css {
                    width = 340.px
                    // Prevent the menu from shrinking on smaller screens
                    minWidth = 340.px
                    flexShrink = 0.0

                    height = 100.pct
                    overflowY = Overflow.hidden
                }

                SidebarMenu()
            }

            div {
                css {
                    flexGrow = 1.0
                    height = 100.pct
                    overflowY = Overflow.auto
                }
                props.inner(this)
            }
        }
    }
}
