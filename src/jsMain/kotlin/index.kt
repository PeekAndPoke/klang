/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.storage.KeymapStorage
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.codemirror.KlangEditorKeyBindings
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.klang.utils.VersionController
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.pixijs.pixiJs
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.kraftApp
import io.peekandpoke.kraft.semanticui.semanticUI
import io.peekandpoke.kraft.vdom.preact.PreactVDomEngine

/** Every key of the app (KLANG-00325): Klang's actions, the user's keys kept in localStorage, hints on a held Ctrl (Cmd on a Mac). */
val keysManager = KeysManager(store = KeymapStorage).apply {
    define(KlangKeyBindings)
    // The code editor's keys: the editor runs them through EditorKeyTable, the user's keys included
    define(KlangEditorKeyBindings)

    // A clash or an unreadable key, from the defaults or from the user's keys: say so where the developer looks
    keymap.subscribeToStream { keymap ->
        keymap.problems.forEach { console.error("Keymap problem:", it.toString()) }
    }
}

val kraft = kraftApp {
    semanticUI()

    keys(keysManager, hint = KlangKeyHint.render, waiting = KlangKeyHint.waiting)

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
    // The look the user chose last time (KLANG-00339)
    LookStorage.load()?.let { KlangTheme.choose(it) }

    // Register UI tools
    registerSprudelUiTools()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
