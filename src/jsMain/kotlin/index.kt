package io.peekandpoke.klang

import de.peekandpoke.kraft.kraftApp
import de.peekandpoke.kraft.semanticui.semanticUI
import de.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import io.peekandpoke.klang.codetools.StrudelAdsrEditorTool
import io.peekandpoke.klang.codetools.StrudelScaleEditorTool
import io.peekandpoke.klang.strudel.lang.initStrudelLang
import io.peekandpoke.klang.ui.KlangUiToolRegistry
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
    KlangUiToolRegistry.register("StrudelAdsrEditor", StrudelAdsrEditorTool)
    KlangUiToolRegistry.register("StrudelScaleEditor", StrudelScaleEditorTool)

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
