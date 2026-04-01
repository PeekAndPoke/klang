package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.builder.registerVarargFunction
import io.peekandpoke.klang.script.generated.registerStdlibGenerated
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.script.stdlib.KlangStdLib.defaultOutputHandler
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangType

/**
 * KlangScript Standard Library
 *
 * Most registrations are generated from @KlangScript annotations (see stdlib/ files).
 * Console and print remain manual because they need the injected outputHandler.
 *
 * **Usage**:
 * ```kotlin
 * val engine = klangScript {
 *     registerLibrary(KlangStdLib.create())
 * }
 * ```
 */
object KlangStdLib {

    /** Default output handler -- routes to platform-specific console. */
    val defaultOutputHandler: (ConsoleLevel, List<String>) -> Unit = ::platformConsoleOutput

    /**
     * Create the standard library.
     *
     * @param outputHandler Handler for print() and console output.
     *   Receives the [ConsoleLevel] and the list of stringified arguments.
     *   Defaults to [defaultOutputHandler] (platform console). Override to capture output in a REPL or tests.
     * @return A KlangScriptLibrary instance containing all standard functions
     */
    fun create(
        outputHandler: (ConsoleLevel, List<String>) -> Unit = defaultOutputHandler,
    ): KlangScriptLibrary {
        return klangScriptLibrary("stdlib") {
            source(
                """
                export {
                    console,
                    Math,
                    Object,
                    Osc
                }
                """.trimIndent()
            )

            // Register all annotation-generated symbols:
            // Math, Object, String/Array/Number/Boolean extensions
            registerStdlibGenerated()

            // Console object -- registered manually because output goes through the injected outputHandler.
            // Methods match the JavaScript console API: log, warn, error, info, debug.
            registerObject("console", KlangScriptConsole) {
                registerVarargMethod("log") { args: List<Any?> ->
                    outputHandler(ConsoleLevel.LOG, args.map { it?.toString() ?: "null" })
                }
                registerVarargMethod("warn") { args: List<Any?> ->
                    outputHandler(ConsoleLevel.WARN, args.map { it?.toString() ?: "null" })
                }
                registerVarargMethod("error") { args: List<Any?> ->
                    outputHandler(ConsoleLevel.ERROR, args.map { it?.toString() ?: "null" })
                }
                registerVarargMethod("info") { args: List<Any?> ->
                    outputHandler(ConsoleLevel.INFO, args.map { it?.toString() ?: "null" })
                }
                registerVarargMethod("debug") { args: List<Any?> ->
                    outputHandler(ConsoleLevel.DEBUG, args.map { it?.toString() ?: "null" })
                }
            }

            // print() -- uses the captured outputHandler at LOG level
            registerVarargFunction("print") { args: List<Any?> ->
                outputHandler(ConsoleLevel.LOG, args.map { it?.toString() ?: "null" })
            }

            // Osc.register() -- registered manually because it needs engine access to read
            // the IgnitorRegistrar from engine.attrs at call time.
            registerExtensionMethodWithEngine(
                receiver = KlangScriptOsc::class,
                name = "register",
                docs = KlangCallable(
                    library = "stdlib",
                    name = "register",
                    receiver = KlangType(simpleName = "Osc"),
                    params = listOf(
                        KlangParam(
                            name = "name", type = KlangType(simpleName = "String"),
                            description = "Name for the sound, used with .sound()"
                        ),
                        KlangParam(
                            name = "dsl", type = KlangType(simpleName = "IgnitorDsl"),
                            description = "The IgnitorDsl signal graph to register"
                        ),
                    ),
                    returnType = KlangType(simpleName = "String"),
                    description = "Registers an IgnitorDsl signal graph as a named sound for use with .sound().",
                ),
            ) { _, args, _, engine ->
                val registrar = engine.attrs[KlangScriptOsc.REGISTRAR_KEY]
                    ?: error("Osc.register() requires an audio player. No player is connected.")
                val name = (args[0] as StringValue).value

                @Suppress("UNCHECKED_CAST")
                val dsl = (args[1] as NativeObjectValue<IgnitorDsl>).value
                StringValue(registrar(name, dsl))
            }
        }
    }
}
