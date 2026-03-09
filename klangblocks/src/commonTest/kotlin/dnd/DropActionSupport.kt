package io.peekandpoke.klang.blocks.dnd

import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.parser.KlangScriptParser

internal fun ctx(source: String): KBProgramEditingCtx {
    val ast = KlangScriptParser.parse(source.trimIndent())
    return KBProgramEditingCtx(initialProgram = AstToKBlocks.convert(ast))
}

internal fun KBProgramEditingCtx.code(): String = program.toCode()

internal fun KBProgramEditingCtx.rowCount(): Int = program.statements.size

/** Find the first KBCallBlock with the given funcName anywhere in the tree. */
internal fun KBProgramEditingCtx.block(funcName: String): KBCallBlock {
    fun search(steps: List<KBChainItem>): KBCallBlock? {
        for (step in steps) {
            if (step is KBCallBlock) {
                if (step.funcName == funcName) return step
                for (arg in step.args) {
                    if (arg is KBNestedChainArg) search(arg.chain.steps)?.let { return it }
                }
            }
        }
        return null
    }
    fun searchValue(value: KBArgValue?): KBCallBlock? =
        if (value is KBNestedChainArg) search(value.chain.steps) else null

    for (stmt in program.statements) {
        when (stmt) {
            is KBChainStmt -> search(stmt.steps)?.let { return it }
            is KBLetStmt -> searchValue(stmt.value)?.let { return it }
            is KBConstStmt -> searchValue(stmt.value)?.let { return it }
            else -> {}
        }
    }
    error("No block with funcName=$funcName found in program")
}

/**
 * Find the KBChainStmt (at any nesting depth) that directly contains a block
 * with the given funcName.
 */
internal fun KBProgramEditingCtx.chain(funcName: String): KBChainStmt {
    fun searchChain(chain: KBChainStmt): KBChainStmt? {
        if (chain.steps.filterIsInstance<KBCallBlock>().any { it.funcName == funcName }) return chain
        for (item in chain.steps) {
            if (item is KBCallBlock) {
                for (arg in item.args) {
                    if (arg is KBNestedChainArg) searchChain(arg.chain)?.let { return it }
                }
            }
        }
        return null
    }
    for (stmt in program.statements) {
        when (stmt) {
            is KBChainStmt -> searchChain(stmt)?.let { return it }
            is KBLetStmt -> (stmt.value as? KBNestedChainArg)?.let { searchChain(it.chain) }?.let { return it }
            is KBConstStmt -> (stmt.value as? KBNestedChainArg)?.let { searchChain(it.chain) }?.let { return it }
            else -> {}
        }
    }
    error("No chain containing funcName=$funcName found at any depth")
}

/** Collect tail (block and all following KBCallBlocks in its direct containing chain). */
internal fun KBProgramEditingCtx.tail(funcName: String): List<KBCallBlock> {
    val c = chain(funcName)
    val allBlocks = c.steps.filterIsInstance<KBCallBlock>()
    val fromIdx = allBlocks.indexOfFirst { it.funcName == funcName }
    return if (fromIdx >= 0) allBlocks.drop(fromIdx) else emptyList()
}

/** Find the first [KBLetStmt] with the given variable name. */
internal fun KBProgramEditingCtx.letStmt(name: String): KBLetStmt =
    program.statements.filterIsInstance<KBLetStmt>().first { it.name == name }

/** Find the first [KBConstStmt] with the given variable name. */
internal fun KBProgramEditingCtx.constStmt(name: String): KBConstStmt =
    program.statements.filterIsInstance<KBConstStmt>().first { it.name == name }
