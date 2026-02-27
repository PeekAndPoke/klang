package io.peekandpoke.klang.blocks.model

sealed class KBArgValue

data class KBEmptyArg(val paramName: String) : KBArgValue()
data class KBStringArg(val value: String) : KBArgValue()
data class KBNumberArg(val value: Double) : KBArgValue()
data class KBBoolArg(val value: Boolean) : KBArgValue()
data class KBNestedChainArg(val chain: KBChainStmt) : KBArgValue()
data class KBIdentifierArg(val name: String) : KBArgValue()
data class KBBinaryArg(
    val left: KBArgValue,
    val op: String,
    val right: KBArgValue,
) : KBArgValue()

data class KBUnaryArg(
    val op: String,
    val operand: KBArgValue,
) : KBArgValue()

data class KBArrowFunctionArg(
    val params: List<String>,
    val bodySource: String,
) : KBArgValue()
