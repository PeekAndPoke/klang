package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.async
import io.peekandpoke.klang.codemirror.ext.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Loaded CodeMirror modules
 */
data class CodeMirrorModules(
    val state: StateModule,
    val view: ViewModule,
    val commands: CommandsModule,
    val language: LanguageModule,
    val langJs: JavaScriptModule,
    val basicSetup: BasicSetupModule,
) {
    companion object {
        val codeMirrorModules: Deferred<CodeMirrorModules> = run {
            async { loadCodeMirror() }
        }

        suspend fun load(): CodeMirrorModules = codeMirrorModules.await()
    }
}

/**
 * Loads all CodeMirror modules asynchronously
 */
private suspend fun loadCodeMirror(): CodeMirrorModules {
    suspend fun <T> load(block: () -> Promise<T>) = block().await()

    val stateLoader = load<StateModule> { js("import('@codemirror/state')") }
    val viewLoader = load<ViewModule> { js("import('@codemirror/view')") }
    val commandsLoader = load<CommandsModule> { js("import('@codemirror/commands')") }
    val languageLoader = load<LanguageModule> { js("import('@codemirror/language')") }
    val langJsLoader = load<JavaScriptModule> { js("import('@codemirror/lang-javascript')") }
    val basicSetupLoader = load<BasicSetupModule> { js("import('codemirror')") }

    return CodeMirrorModules(
        state = stateLoader,
        view = viewLoader,
        commands = commandsLoader,
        language = languageLoader,
        langJs = langJsLoader,
        basicSetup = basicSetupLoader,
    )
}
