package io.peekandpoke.klang.ui

import de.peekandpoke.ultra.common.TypedAttributes
import de.peekandpoke.ultra.common.TypedKey
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.streams.Stream
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.html.FlowContent

/**
 * Context passed to a [KlangUiTool] when it is opened.
 *
 * @param symbol       The function whose param triggered the tool.
 * @param paramName    The name of the param that has this tool attached.
 * @param currentValue Raw source text of the argument at the cursor (null if no arg is present yet).
 * @param onCommit     Call with the replacement source text to commit the edit back to the editor.
 * @param onCancel     Call to dismiss without making any change.
 * @param attrs        Extensible typed attributes for passing module-specific data (e.g. playback signals).
 */
data class KlangUiToolContext(
    val symbol: KlangSymbol,
    val paramName: String,
    val currentValue: String?,
    val onCommit: (String) -> Unit,
    val onCancel: () -> Unit,
    val attrs: TypedAttributes = TypedAttributes.empty,
) {
    companion object {
        /** Typed key for the stream of scheduled voice batches (raw timing + source locations). */
        val PlaybackVoices = TypedKey<Stream<List<PlaybackVoice>>>("PlaybackVoices")

        /** Typed key for the base source location of the edited string literal (opening-quote position). */
        val BaseSourceLocation = TypedKey<SourceLocation>("BaseSourceLocation")
    }
}

/**
 * A UI tool that can edit a function argument interactively.
 *
 * Implement this interface and register it with [KlangUiToolRegistry] under the name
 * declared in a `@param-tool` KDoc tag.
 */
fun interface KlangUiTool {
    val title: String? get() = null

    val iconFn: SemanticIconFn get() = { wrench }

    fun FlowContent.render(ctx: KlangUiToolContext)
}

/**
 * A [KlangUiTool] that can also render its editing content inline, without
 * Cancel / Reset / Update buttons.
 *
 * When rendered embedded, [FlowContent.renderEmbedded] is called instead of [FlowContent.render].
 * The tool must call [KlangUiToolContext.onCommit] on every live change so that the host
 * (e.g. the mini-notation editor) stays in sync automatically.
 */
interface KlangUiToolEmbeddable : KlangUiTool {
    fun FlowContent.renderEmbedded(ctx: KlangUiToolContext)
}

/**
 * Global registry mapping tool names (as declared in `@param-tool` KDoc tags) to [KlangUiTool] implementations.
 *
 * Register tools at application startup:
 * ```kotlin
 * KlangUiToolRegistry.register("StrudelAdsrEditor", StrudelAdsrEditor())
 * ```
 */
object KlangUiToolRegistry {
    private val tools = mutableMapOf<String, KlangUiTool>()

    fun register(name: String, tool: KlangUiTool) {
        tools[name] = tool
    }

    fun get(name: String): KlangUiTool? = tools[name]

    /** Returns all tool names registered for the given list of names (preserving order, skipping unknowns). */
    fun resolve(names: List<String>): List<Pair<String, KlangUiTool>> =
        names.mapNotNull { name -> tools[name]?.let { name to it } }
}
