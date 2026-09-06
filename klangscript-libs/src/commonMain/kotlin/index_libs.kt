/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.stdlib.KlangStdLib

/** The KlangScript standard library (`import * from "stdlib"`): Osc, Master, Pipeline, Math, Object, console, value-type extensions. */
val stdlibLib: KlangScriptLibrary = KlangStdLib.create()

/**
 * Create a [KlangScriptEngine] with the standard library registered.
 *
 * This is the factory hosts and tests use. The language-only counterpart is
 * [klangScriptEngine] in `:klangscript`, which registers nothing.
 *
 * @param builder Optional configuration block for additional registrations
 * @return A fully configured engine
 */
fun klangScript(
    builder: KlangScriptEngine.Builder.() -> Unit = {},
): KlangScriptEngine = klangScriptEngine {
    // Always register the standard library first, so a host's registrations can build on it.
    registerLibrary(stdlibLib)
    builder()
}
