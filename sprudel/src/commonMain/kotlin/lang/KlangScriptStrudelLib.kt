package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.generated.registerSprudelGenerated
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.sprudel.lang.docs.registerSprudelDocs

/**
 * The Sprudel DSL library for KlangScript.
 *
 * All script-visible bindings (functions, properties, type extensions) are emitted by
 * the KSP processor from `@KlangScript.Function` / `@KlangScript.Property` annotations
 * and registered by [registerSprudelGenerated].
 */
val sprudelLib = klangScriptLibrary("sprudel") {
    registerSprudelGenerated()
    docs { registerSprudelDocs(this) }
}
