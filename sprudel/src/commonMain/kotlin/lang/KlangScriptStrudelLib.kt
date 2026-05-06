package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.generated.registerSprudelGenerated
import io.peekandpoke.klang.script.klangScriptLibrary

/**
 * The Sprudel DSL library for KlangScript.
 *
 * All script-visible bindings (functions, properties, type extensions) are emitted by
 * the KSP processor from `@KlangScript.Function` / `@KlangScript.Property` annotations
 * and registered by [registerSprudelGenerated]. Documentation is auto-registered by the
 * same call.
 */
val sprudelLib = klangScriptLibrary("sprudel") {
    registerSprudelGenerated()
}
