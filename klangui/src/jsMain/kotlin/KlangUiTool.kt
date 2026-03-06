package io.peekandpoke.klang.ui

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
 */
data class KlangUiToolContext(
    val symbol: KlangSymbol,
    val paramName: String,
    val currentValue: String?,
    val onCommit: (String) -> Unit,
    val onCancel: () -> Unit,
)

/**
 * A UI tool that can edit a function argument interactively.
 *
 * Implement this interface and register it with [KlangUiToolRegistry] under the name
 * declared in a `@param-tool` KDoc tag.
 */
fun interface KlangUiTool {
    fun FlowContent.render(ctx: KlangUiToolContext)
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