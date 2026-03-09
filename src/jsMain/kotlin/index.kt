package io.peekandpoke.klang

import de.peekandpoke.kraft.kraftApp
import de.peekandpoke.kraft.semanticui.semanticUI
import de.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import io.peekandpoke.klang.strudel.lang.initStrudelLang
import io.peekandpoke.klang.strudel.ui.registerStrudelUiTools
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
    // Initialize Strudel DSL and register documentation
    initStrudelLang()

    // Register UI tools
    registerStrudelUiTools()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
