/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.comp.RouterWithTransitions
import io.peekandpoke.klang.comp.ScreenAwake
import io.peekandpoke.klang.visuals.VisualsScreen
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div

private val log = Logger(area = LogArea.App, source = "App")

@Suppress("FunctionName")
fun Tag.KlangStudioComponent() = comp {
    KlangStudioComponent(it)
}

class KlangStudioComponent(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    init {
        lifecycle {
            // Not in the render: a render logs nothing (see Logger)
            onMount { log.info("The app is on the page") }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = "app") {
            RouterWithTransitions()
            // A visual over the whole window, from the bottom panel's Visuals tab
            VisualsScreen()
            // The screen stays awake while something plays
            ScreenAwake()
        }
    }
}
