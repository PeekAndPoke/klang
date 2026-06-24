/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.klang.utils.VersionController
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.pixijs.pixiJs
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.kraftApp
import io.peekandpoke.kraft.semanticui.semanticUI
import io.peekandpoke.kraft.vdom.preact.PreactVDomEngine

val kraft = kraftApp {
    semanticUI()

    addons {
        browserDetect()
        marked()
        pixiJs(lazy = true)
        threeJs(lazy = true)
    }

    routing {
        usePathStrategy()
        // Mount app routes
        mountNav()
    }
}

val fs = FullscreenController()

// Build metadata (project + git), published as a stream; see VersionController.
val version = VersionController()

fun main() {
    // Read CSS custom properties and establish the active look-and-feel before render
    KlangTheme.initialize()

    // Register UI tools
    registerSprudelUiTools()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
