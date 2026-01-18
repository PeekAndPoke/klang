package io.peekandpoke.klang

import de.peekandpoke.kraft.kraftApp
import de.peekandpoke.kraft.semanticui.semanticUI
import de.peekandpoke.kraft.vdom.preact.PreactVDomEngine

val kraft = kraftApp {
    semanticUI()

    routing {
        usePathStrategy()
        // Mount app routes
        mountNav()
    }
}

fun main() {
    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
