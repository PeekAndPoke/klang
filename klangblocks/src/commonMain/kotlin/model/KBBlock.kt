package io.peekandpoke.klang.blocks.model

// ---- Chain items ------------------------------------------------

/**
 * One item inside a [KBChainStmt]'s step list.
 *
 * A chain is mostly a sequence of [KBCallBlock]s joined by `.` in the generated
 * code. [KBNewlineHint] can appear between blocks to record where the original
 * source had a line break.
 */
sealed class KBChainItem

/**
 * Controls how a [KBCallBlock]'s argument slots are laid out in the editor.
 *
 * - [HORIZONTAL] — slots are arranged in a single row (default).
 * - [VERTICAL] — slots are stacked column-wise. In the generated code each
 *   argument is placed on its own line.
 */
enum class KBPocketLayout { HORIZONTAL, VERTICAL }

/**
 * A single function-call block, the primary building block of the editor.
 *
 * Represents one call in a method chain, e.g. `note("c3")` or `.gain(0.5)`.
 * Chains of these are stored in [KBChainStmt.steps] and rendered as connected
 * coloured tiles on the canvas.
 *
 * @param id          Stable unique ID used to identify the block across edits (e.g. for undo/redo).
 * @param funcName    The function name, e.g. `"note"` or `"gain"`.
 * @param args        Argument values parallel to the function's parameter list.
 * @param isHead      True when this block is the first call in its chain (no leading `.`).
 * @param pocketLayout Whether argument slots are shown in a row or stacked vertically.
 */
data class KBCallBlock(
    val id: String,
    val funcName: String,
    val args: List<KBArgValue> = emptyList(),
    val isHead: Boolean = true,
    val pocketLayout: KBPocketLayout = KBPocketLayout.HORIZONTAL,
) : KBChainItem()

/**
 * A hint that the original source code had a newline at this position in the chain.
 * Not rendered as a visible block; used to preserve formatting intent during round-trips.
 */
data object KBNewlineHint : KBChainItem()

/**
 * A string literal at the head of a nested chain, acting as the receiver for the first call.
 * e.g. `"C4".transpose(1)` is modelled as `[KBStringLiteralItem("C4"), KBCallBlock("transpose")]`.
 * Only valid as the **first** item in a [KBChainStmt] that lives inside a [KBNestedChainArg].
 */
data class KBStringLiteralItem(val value: String) : KBChainItem()

/**
 * A bare identifier at the head of a nested chain, acting as the receiver for the first call.
 * e.g. `sine.range(0.25, 0.75)` is modelled as `[KBIdentifierItem("sine"), KBCallBlock("range")]`.
 * Only valid as the **first** item in a [KBChainStmt] that lives inside a [KBNestedChainArg].
 */
data class KBIdentifierItem(val name: String) : KBChainItem()

// ---- Statements -------------------------------------------------

/**
 * One top-level statement in a [KBProgram].
 *
 * Each statement maps to a single line (or logical unit) on the editor canvas
 * and to a single statement in the generated code.
 */
sealed class KBStmt {
    abstract val id: String
}

/**
 * An `import` statement, e.g. `import * from "strudel"` or `import {note} from "strudel"`.
 *
 * @param libraryName The module specifier string (the part after `from`).
 * @param alias       Namespace alias used with `import * as alias from …`.
 * @param names       Explicit named imports; `null` means `import *`.
 */
data class KBImportStmt(
    override val id: String,
    val libraryName: String,
    val alias: String? = null,
    val names: List<String>? = null,
) : KBStmt()

/**
 * A `let` variable declaration, e.g. `let x = note("c3")`.
 * [value] is null when the variable is declared without an initialiser.
 */
data class KBLetStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue? = null,
) : KBStmt()

/**
 * A `const` variable declaration, e.g. `const bpm = 120`.
 * Always has an initialiser value.
 */
data class KBConstStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue,
) : KBStmt()

/**
 * An `export` declaration, e.g. `export bass = note("c3 e3 g3")`.
 *
 * Combined immutable binding + export marker. Always has an initialiser value;
 * intended for top-level use in a library/module file. Mirrors [KBConstStmt]
 * structurally — emits `export <name> = <value>` instead of `const <name> = <value>`.
 */
data class KBExportStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue,
) : KBStmt()

/**
 * A method-call chain statement — the most common statement type.
 *
 * Represents a chain of function calls joined by `.`, e.g.
 * `note("c3 e3").gain(0.5).slow(2)`. Each call is one [KBCallBlock] in [steps].
 * Rendered as a horizontal row of coloured tiles on the canvas.
 */
data class KBChainStmt(
    override val id: String,
    val steps: List<KBChainItem> = emptyList(),
) : KBStmt()

/**
 * A fallback statement for expressions that are not call chains and not assignments,
 * e.g. postfix `x++`, `x--`, or any bare expression used as a statement.
 *
 * The expression is stored as a [KBArgValue] and emitted verbatim in code gen.
 * Not directly editable in the blocks UI — used only to preserve round-trip correctness.
 */
data class KBExprStmt(
    override val id: String,
    val expr: KBArgValue,
) : KBStmt()

/**
 * A variable (re-)assignment statement, e.g. `x = 5` or `x = x + 1`.
 *
 * [target] is the raw source string of the left-hand side (e.g. `"x"`, `"arr[0]"`, `"obj.prop"`).
 * It is emitted verbatim in the generated code — no quoting is applied.
 * [value] is the structured right-hand side value.
 *
 * Compound assignments (`x += 1`) are desugared by the parser to `x = x + 1`,
 * so they appear here as `KBAssignStmt("x", KBBinaryArg(...))`.
 */
data class KBAssignStmt(
    override val id: String,
    val target: String,
    val value: KBArgValue,
) : KBStmt()

/**
 * A blank line in the program.
 *
 * Carries no code semantics; exists purely to preserve vertical whitespace
 * between statements as the user arranged them. Renders as an empty row on
 * the canvas and as an empty line in the generated code.
 */
data class KBBlankLine(
    override val id: String,
) : KBStmt()
