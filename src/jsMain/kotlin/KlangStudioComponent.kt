package io.peekandpoke.klang

import io.peekandpoke.klang.comp.RouterWithTransitions
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangStudioComponent() = comp {
    KlangStudioComponent(it)
}

class KlangStudioComponent(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {

        console.info("rendering app ...")

        div(classes = "app") {
            RouterWithTransitions()
        }
    }
}
