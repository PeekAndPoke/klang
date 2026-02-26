package io.peekandpoke.klang.blockly

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.OnChange
import de.peekandpoke.ultra.html.css
import io.peekandpoke.klang.blockly.BlocklyEditorComp.Props
import io.peekandpoke.klang.blockly.ext.BlocklyOptions
import io.peekandpoke.klang.blockly.ext.WorkspaceSvg
import io.peekandpoke.klang.blockly.ext.inject
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.HTMLElement

/**
 * Kraft component that wraps a Blockly workspace.
 *
 * Usage in a parent component's render:
 * ```kotlin
 * BlocklyEditorComp(
 *     code          = code,
 *     onCodeChanged = { code = it },
 * ).track(blocklyRef)
 * ```
 *
 * The parent can push new code into the editor at any time by calling
 * `blocklyRef { it.setCode(newCode) }`.
 *
 * Life-cycle
 * ----------
 * - **onMount**: register all DSL blocks, inject the workspace, populate from [Props.code].
 * - Block changes trigger [Props.onCodeChanged] (structure-changing events only).
 * - **onUnmount**: dispose the workspace.
 */
@Suppress("FunctionName")
fun Tag.BlocklyEditorComp(
    code: String,
    onCodeChanged: OnChange<String>,
): ComponentRef<BlocklyEditorComp> = comp(
    Props(code = code, onCodeChanged = onCodeChanged)
) {
    BlocklyEditorComp(it)
}

class BlocklyEditorComp(ctx: Ctx<Props>) : Component<Props>(ctx) {

    // ----------------------------------------------------------------
    // Props
    // ----------------------------------------------------------------

    data class Props(
        val code: String,
        val onCodeChanged: OnChange<String>,
    )

    // ----------------------------------------------------------------
    // State / internals
    // ----------------------------------------------------------------

    private val divId = "blockly-workspace-${hashCode()}"

    private var workspace: WorkspaceSvg? by value(null)

    /** Cached last generated code — prevents spurious onCodeChanged callbacks. */
    private var lastGeneratedCode: String = ""

    /**
     * Change listener lambda stored so we can remove it on unmount.
     * Typed as `(dynamic) -> Unit` to match Blockly's JS API.
     */
    private var changeListener: ((dynamic) -> Unit)? = null

    // ----------------------------------------------------------------
    // Life-cycle
    // ----------------------------------------------------------------

    init {
        lifecycle {
            onMount {
                initialize()
            }
            onUnmount {
                teardown()
            }
        }
    }

    // ----------------------------------------------------------------
    // Public API (called via ComponentRef)
    // ----------------------------------------------------------------

    /**
     * Replace the current workspace contents with blocks derived from [code].
     * Safe to call at any time after the component has been mounted.
     */
    fun setCode(code: String) {
        val ws = workspace ?: return
        // Temporarily remove the change listener while we repopulate to avoid
        // triggering onCodeChanged with intermediate / empty states.
        val listener = changeListener
        if (listener != null) ws.removeChangeListener(listener)

        AstToBlockly.populate(ws, code)
        lastGeneratedCode = KlangScriptGenerator.generate(ws)

        if (listener != null) ws.addChangeListener(listener)
    }

    // ----------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------

    private fun initialize() {
        val container = dom?.querySelector("#$divId") as? HTMLElement ?: run {
            console.error("BlocklyEditorComp: container div not found (#$divId)")
            return
        }

        // Register all DSL block definitions (idempotent)
        try {
            BlockDefinitionBuilder.registerBlocks()
        } catch (e: Throwable) {
            console.error("BlocklyEditorComp: failed to register blocks:", e)
        }

        // Build toolbox
        val toolbox = try {
            BlockDefinitionBuilder.buildToolbox()
        } catch (e: Throwable) {
            console.error("BlocklyEditorComp: failed to build toolbox:", e)
            null
        }

        // Inject workspace
        val options = jsObject<BlocklyOptions> {
            this.toolbox = toolbox
            this.trashcan = true
            this.sounds = false
            this.renderer = "geras"
            this.grid = js("""({spacing:20, length:3, colour:"#ccc", snap:true})""")
            this.zoom = js("""({controls:true, wheel:true, startScale:1.0})""")
        }

        val ws = try {
            inject(container, options)
        } catch (e: Throwable) {
            console.error("BlocklyEditorComp: Blockly.inject failed:", e)
            return
        }

        workspace = ws

        // Populate from initial code
        if (props.code.isNotBlank()) {
            AstToBlockly.populate(ws, props.code)
        }

        // Snapshot the initial state
        lastGeneratedCode = KlangScriptGenerator.generate(ws)

        // Register change listener
        val listener: (dynamic) -> Unit = { event ->
            onWorkspaceChanged(ws, event)
        }
        changeListener = listener
        ws.addChangeListener(listener)
    }

    private fun teardown() {
        val ws = workspace ?: return
        changeListener?.let { ws.removeChangeListener(it) }
        changeListener = null
        ws.dispose()
        workspace = null
    }

    /**
     * Called on every Blockly workspace event.
     * Only regenerates code for events that change the block structure.
     */
    private fun onWorkspaceChanged(ws: WorkspaceSvg, event: dynamic) {
        val eventType = event.type?.toString() ?: return
        if (eventType !in BlocklyEventTypes.STRUCTURAL) return

        val newCode = KlangScriptGenerator.generate(ws)
        if (newCode != lastGeneratedCode) {
            lastGeneratedCode = newCode
            props.onCodeChanged(newCode)
        }
    }

    // ----------------------------------------------------------------
    // Render
    // ----------------------------------------------------------------

    override fun VDom.render() {
        div("blockly-editor-container") {
            css {
                width = 100.pct
                height = 500.px
                position = Position.relative
            }
            div {
                id = divId
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    right = 0.px
                    bottom = 0.px
                }
            }
        }
    }
}
