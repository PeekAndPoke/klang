package io.peekandpoke.klang

import de.peekandpoke.kraft.kraftApp
import de.peekandpoke.kraft.semanticui.semanticUI
import de.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import io.peekandpoke.klang.sprudel.lang.initSprudelDsl
import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.utils.FullscreenController

val kraft = kraftApp {
    semanticUI()

    routing {
        usePathStrategy()
        // Mount app routes
        mountNav()
    }
}

val fs = FullscreenController()

fun main() {
    // Read CSS custom properties and establish the active look-and-feel before render
    KlangTheme.initialize()

    // Initialize Strudel DSL and register documentation
    initSprudelDsl()

    // Register UI tools
    registerSprudelUiTools()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
