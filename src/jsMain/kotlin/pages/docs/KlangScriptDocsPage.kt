package io.peekandpoke.klang.pages.docs

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Padding
import kotlinx.css.padding
import kotlinx.css.rem
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.KlangScriptDocsPage() = comp {
    KlangScriptDocsPage(it)
}

class KlangScriptDocsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            ui.segment {
                ui.header { +"KlangScript Docs" }
            }

            ui.message {
                icon.hammer()
                +"Coming soon..."
            }
        }
    }
}
