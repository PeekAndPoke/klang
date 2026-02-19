package io.peekandpoke.klang

import de.peekandpoke.kraft.kraftApp
import de.peekandpoke.kraft.semanticui.semanticUI
import de.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import io.peekandpoke.klang.strudel.lang.initStrudelLang

val kraft = kraftApp {
    semanticUI()

    routing {
//        usePathStrategy()
        // Mount app routes
        mountNav()
    }
}

fun main() {
    // Initialize Strudel DSL and register documentation
    initStrudelLang()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
