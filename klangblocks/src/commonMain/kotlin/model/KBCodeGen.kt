package io.peekandpoke.klang.blocks.model

fun KBProgram.toCode(): String =
    statements.mapNotNull { it.toCode() }.joinToString("\n")

fun KBStmt.toCode(): String? = when (this) {
    is KBImportStmt -> toCode()
    is KBChainStmt -> toCode()
    is KBLetStmt -> toCode()
    is KBConstStmt -> toCode()
    is KBBlankLine -> ""
}

fun KBImportStmt.toCode(): String {
    val what = when {
        names != null -> "{${names.joinToString(", ")}}"
        alias != null -> "* as $alias"
        else -> "*"
    }
    return "import $what from \"$libraryName\""
}

fun KBChainStmt.toCode(): String? {
    val blocks = steps.filterIsInstance<KBCallBlock>()
    if (blocks.isEmpty()) return null
    return blocks.joinToString(".") { it.toCode() }
}

fun KBCallBlock.toCode(): String {
    val filledArgs = args.filter { it !is KBEmptyArg }
    val argStr = filledArgs.joinToString(", ") { it.toCode() }
    return "$funcName($argStr)"
}

fun KBArgValue.toCode(): String = when (this) {
    is KBEmptyArg -> ""
    is KBStringArg -> "\"$value\""
    is KBNumberArg -> {
        val long = value.toLong()
        if (value == long.toDouble()) long.toString() else value.toString()
    }

    is KBBoolArg -> value.toString()
    is KBIdentifierArg -> name
    is KBNestedChainArg -> chain.toCode() ?: ""
    is KBBinaryArg -> "${left.toCode()} $op ${right.toCode()}"
    is KBUnaryArg -> "$op${operand.toCode()}"
    is KBArrowFunctionArg -> "(${params.joinToString(", ")}) => $bodySource"
}
