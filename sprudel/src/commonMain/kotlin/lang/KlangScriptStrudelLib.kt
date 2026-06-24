/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.generated.registerSprudelGenerated
import io.peekandpoke.klang.script.klangScriptLibrary

/**
 * The Sprudel DSL library for KlangScript.
 *
 * All script-visible bindings (functions, properties, type extensions) are emitted by
 * the KSP processor from `@KlangScript.Function` / `@KlangScript.Constant` annotations
 * and registered by [registerSprudelGenerated]. Documentation is auto-registered by the
 * same call.
 */
val sprudelLib = klangScriptLibrary("sprudel") {
    registerSprudelGenerated()
}
