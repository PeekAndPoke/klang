/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.stdlib.ConsoleLevel
import io.peekandpoke.klang.script.stdlib.KlangStdLib
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * A KlangScript engine as the apps compile songs with: the stdlib, sprudel, and the built-in songs as modules
 * ([registerBuiltInSongsAsModules]).
 *
 * Inline ignitors are denormalized at the playback boundary by the player's ignitor registry; no engine-level wiring
 * is required. If [outputHandler] is provided, console/print output goes there (e.g. REPL capture).
 */
fun createScriptEngine(
    outputHandler: ((ConsoleLevel, List<String>) -> Unit)? = null,
): KlangScriptEngine {
    val stdlib = if (outputHandler != null) {
        KlangStdLib.create(outputHandler = outputHandler)
    } else {
        stdlibLib
    }

    val engineBuilder = KlangScriptEngine.Builder()

    engineBuilder.registerLibrary(stdlib)
    engineBuilder.registerLibrary(sprudelLib)
    engineBuilder.registerBuiltInSongsAsModules()

    return engineBuilder.build()
}
