package io.peekandpoke.klang.blocks.model

/**
 * The value stored in a single argument slot of a [KBCallBlock].
 *
 * Every slot in a block holds exactly one [KBArgValue]. The subtype determines
 * how the value is displayed in the editor and how it is serialised to code.
 */
sealed class KBArgValue

/** An unfilled slot — the user has not yet provided a value. [paramName] is the slot label shown as a placeholder. */
data class KBEmptyArg(val paramName: String) : KBArgValue()

/** A plain string literal, e.g. `"c3 e3 g3"`. */
data class KBStringArg(val value: String) : KBArgValue()

/** A numeric literal, e.g. `0.5` or `2`. Always stored as Double. */
data class KBNumberArg(val value: Double) : KBArgValue()

/** A boolean literal: `true` or `false`. */
data class KBBoolArg(val value: Boolean) : KBArgValue()

/**
 * A nested function-call chain used as an argument value.
 *
 * Rendered as a row of inline mini-blocks inside the parent slot.
 * Appears when a block is dragged from the canvas or palette into a slot,
 * or when the source code contains a nested call such as `note(cat("c3", "e3"))`.
 */
data class KBNestedChainArg(val chain: KBChainStmt) : KBArgValue()

/**
 * A bare identifier (variable or constant name) used as an argument, e.g. `myPattern`.
 * Produced by the AST converter when a simple identifier appears in argument position.
 */
data class KBIdentifierArg(val name: String) : KBArgValue()

/**
 * A binary expression used as an argument, e.g. `x + 1` or `a == b`.
 * Produced by the AST converter; not directly editable in the blocks UI.
 */
data class KBBinaryArg(
    val left: KBArgValue,
    val op: String,
    val right: KBArgValue,
) : KBArgValue()

/**
 * A unary expression used as an argument, e.g. `-1` or `!flag`.
 * Produced by the AST converter; not directly editable in the blocks UI.
 */
data class KBUnaryArg(
    val op: String,
    val operand: KBArgValue,
) : KBArgValue()

/**
 * An arrow-function literal used as an argument, e.g. `x => x * 2`.
 *
 * The body is kept as raw source text ([bodySource]) because the blocks editor
 * does not yet support deep editing of function bodies.
 */
data class KBArrowFunctionArg(
    val params: List<String>,
    val bodySource: String,
) : KBArgValue()
