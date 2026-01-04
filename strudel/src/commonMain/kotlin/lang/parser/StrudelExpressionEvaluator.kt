package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.strudel.lang.StrudelRegistry
import io.peekandpoke.klang.strudel.lang.registerStandardFunctions

class StrudelExpressionEvaluator {

    init {
        // Crucial: This triggers the static initialization of lang.kt
        // and registers the manual functions defined above.
        registerStandardFunctions()
    }

    fun evaluate(node: AstNode): Any {
        return when (node) {
            is LiteralNode -> node.value

            is FunCallNode -> {
                val args = node.args.map { evaluate(it) }
                val handler = StrudelRegistry.functions[node.name]
                    ?: error("Unknown function: '${node.name}'")

                handler(args)
            }

            is ChainNode -> {
                val receiver = evaluate(node.receiver)
                val methodName = node.methodCall.name
                val args = node.methodCall.args.map { evaluate(it) }

                val handler = StrudelRegistry.methods[methodName]
                    ?: error("Unknown method: '$methodName'")

                handler(receiver, args)
            }

            is ListNode -> {
                node.elements.map { evaluate(it) }
            }
        }
    }
}
