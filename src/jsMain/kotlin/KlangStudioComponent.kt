/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.comp.RouterWithTransitions
import io.peekandpoke.klang.visuals.VisualsScreen
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
            // A visual over the whole window, from the bottom panel's Visuals tab
            VisualsScreen()
        }
    }
}
