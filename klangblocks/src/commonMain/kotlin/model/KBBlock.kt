package io.peekandpoke.klang.blocks.model

// ---- Chain items ------------------------------------------------

sealed class KBChainItem

enum class KBPocketLayout { HORIZONTAL, VERTICAL }

data class KBCallBlock(
    val id: String,
    val funcName: String,
    val args: List<KBArgValue> = emptyList(),
    val isHead: Boolean = true,
    val pocketLayout: KBPocketLayout = KBPocketLayout.HORIZONTAL,
) : KBChainItem()

data object KBNewlineHint : KBChainItem()

// ---- Statements -------------------------------------------------

sealed class KBStmt {
    abstract val id: String
}

data class KBImportStmt(
    override val id: String,
    val libraryName: String,
    val alias: String? = null,
    val names: List<String>? = null,   // null = import *
) : KBStmt()

data class KBLetStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue? = null,
) : KBStmt()

data class KBConstStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue,
) : KBStmt()

data class KBChainStmt(
    override val id: String,
    val steps: List<KBChainItem> = emptyList(),
) : KBStmt()

data class KBBlankLine(
    override val id: String,
) : KBStmt()
