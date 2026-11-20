/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.kraftApp
import io.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import kotlinx.browser.document
import kotlinx.browser.window

// Notstrom's page: the band's app alone (NotstromApp). The address's switches (`?seed=7&nostrobe`) are the live act's:
// see Switch in klangviz for the list of them all. Its line of text at the top shows only with `?dev`.

private val kraft = kraftApp {
    addons {
        threeJs()
        browserDetect()
    }
}

fun main() {
    kraft.mount(selector = "#app", engine = PreactVDomEngine()) {
        NotstromApp(query = window.location.search)
    }

    // the page's own "Loading" line, shown while this script came: the app is there now
    document.getElementById("loading")?.remove()
}
