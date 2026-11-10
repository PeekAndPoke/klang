/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.common.logging.BrowserConsoleListener
import io.peekandpoke.klang.common.logging.KlangLog
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.LogLevel
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.spine.ConsoleLog
import io.peekandpoke.klang.spine.Spine
import io.peekandpoke.klang.spine.spine
import io.peekandpoke.klang.sprudel.ui.registerSprudelUiTools
import io.peekandpoke.klang.storage.KeymapStorage
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.codemirror.KlangEditorKeyBindings
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.klang.ui.feel.KlangBrushedMetal
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.utils.BuildMode
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

/** The app's own Console, made first: it listens before anything logs ([logging]) and is the Spine's ([appSpine]). */
private val consoleLog = ConsoleLog()

/**
 * The log's listeners, before anything logs: the keymap below reports its problems as it is built (KLANG-00407). Its
 * messages go to the browser's console, each at the line that logged it (everything in a development build, `info` and
 * up in a release: D4), and to the app's own Console tab, with the browser's uncaught errors.
 */
@Suppress("unused")
private val logging = run {
    BrowserConsoleListener.least = if (BuildMode.isDevelopment) LogLevel.Debug else LogLevel.Info
    KlangLog.addListener(BrowserConsoleListener)
    consoleLog.install()
    consoleLog.captureUncaught()
}

private val keymapLog = Logger(area = LogArea.App, source = "Keymap")

/**
 * Every key of the app (KLANG-00325): Klang's actions, the user's keys kept in localStorage, hints on a held Ctrl or Alt
 * (Cmd or Option on a Mac; the maintainer, 2026-11-01).
 */
val keysManager = KeysManager(store = KeymapStorage, hintTriggers = listOf("Mod", "Alt")).apply {
    define(KlangKeyBindings)
    // The code editor's keys: the editor runs them through EditorKeyTable, the user's keys included
    define(KlangEditorKeyBindings)

    // A clash or an unreadable key, from the defaults or from the user's keys: say so where the developer looks
    keymap.subscribeToStream { keymap ->
        keymap.problems.forEach { keymapLog.error("A problem in the keymap: $it") }
    }
}

/** The app's one Spine: built here, registered on the app, reached by every component as `spine` (KLANG-00422). */
private val appSpine = Spine.inBrowser(console = consoleLog).also {
    // The samples load while the user looks at the start page, as ever
    it.player.preloadSamples()
}

val kraft = kraftApp {
    semanticUI()

    spine(appSpine)

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
    // The user's own looks and the equipped slots (KLANG-00382), then the look the user chose last time (KLANG-00339)
    LookStorage.restoreShelf()
    // A first visit, nothing kept, wears Sun, gold on the start page (the maintainer, 2026-10-28: "Ok on the golden
    // start page"); not stored, so a later first-visit look reaches everyone who never chose one
    KlangTheme.choose(LookStorage.load() ?: KlangLooks.Sun)
    // The brushed metal's tile for the parts that wear it, painted once (KLANG-00366)
    KlangBrushedMetal.install(seed = appSpine.seeds.metal)
    // The buttons' grain from the same seed: the user's own sheet of metal
    KlangControlStyles.installGrain(seed = appSpine.seeds.metal)

    // Register UI tools
    registerSprudelUiTools()

    // The MIDI devices where the user granted MIDI before, so a KeyLab plays on every page (KLANG-00402, step 4); a
    // first visit is asked nothing
    appSpine.playbacks.live.connectIfGranted()

    kraft.mount(selector = "#spa", engine = PreactVDomEngine()) {
        KlangStudioComponent()
    }
}
