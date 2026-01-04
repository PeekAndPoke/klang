package io.peekandpoke.klang.strudel.lang.parser

/**
 * Represents the structure of Strudel code.
 */
sealed class AstNode

/**
 * A literal value (String or Number).
 */
data class LiteralNode(val value: Any) : AstNode()

/**
 * A function call, e.g. `note("c3")` or `stack(...)`.
 */
data class FunCallNode(val name: String, val args: List<AstNode>) : AstNode()

/**
 * A method chain, e.g. `note("c3").fast(2)`.
 * [receiver] is `note("c3")`, [methodCall] is `fast(2)`.
 */
data class ChainNode(val receiver: AstNode, val methodCall: FunCallNode) : AstNode()
