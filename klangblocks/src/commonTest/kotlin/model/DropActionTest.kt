package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun ctx(source: String): KBProgramEditingCtx {
    val ast = io.peekandpoke.klang.script.parser.KlangScriptParser.parse(source.trimIndent())
    return KBProgramEditingCtx(initialProgram = AstToKBlocks.convert(ast))
}

private fun KBProgramEditingCtx.code(): String = program.toCode()

private fun KBProgramEditingCtx.rowCount(): Int = program.statements.size

/** Find the first KBCallBlock with the given funcName anywhere in the tree. */
private fun KBProgramEditingCtx.block(funcName: String): KBCallBlock {
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
    for (stmt in program.statements) {
        if (stmt is KBChainStmt) search(stmt.steps)?.let { return it }
    }
    error("No block with funcName=$funcName found in program")
}

/** Find the first KBChainStmt that contains a block with the given funcName. */
private fun KBProgramEditingCtx.chain(funcName: String): KBChainStmt =
    program.statements.filterIsInstance<KBChainStmt>()
        .firstOrNull { chain -> chain.steps.filterIsInstance<KBCallBlock>().any { it.funcName == funcName } }
        ?: error("No chain containing funcName=$funcName found")

/** Collect tail (block and all following KBCallBlocks in its chain). */
private fun KBProgramEditingCtx.tail(funcName: String): List<KBCallBlock> {
    val chain = chain(funcName)
    val allBlocks = chain.steps.filterIsInstance<KBCallBlock>()
    val fromIdx = allBlocks.indexOfFirst { it.funcName == funcName }
    return if (fromIdx >= 0) allBlocks.drop(fromIdx) else emptyList()
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class DropActionTest : StringSpec({

    // ── P: Palette → CreateBlock ──────────────────────────────────────────────

    "P1 CreateBlock to RowGap(0) inserts new chain at top" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        c.execute(DropAction.CreateBlock("gain", DropDestination.RowGap(0)))
        c.code() shouldBe "gain()\nsound(\"bd\")\nfast(2)"
    }

    "P1 CreateBlock to RowGap appends at end" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        c.execute(DropAction.CreateBlock("gain", DropDestination.RowGap(2)))
        c.code() shouldBe "sound(\"bd\")\nfast(2)\ngain()"
    }

    "P1 CreateBlock to RowGap inserts in middle" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        c.execute(DropAction.CreateBlock("gain", DropDestination.RowGap(1)))
        c.code() shouldBe "sound(\"bd\")\ngain()\nfast(2)"
    }

    "P2 CreateBlock to ChainEnd appends block to chain" {
        val c = ctx("sound(\"bd\")")
        val chainId = c.chain("sound").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainEnd(chainId)))
        c.code() shouldBe "sound(\"bd\").gain()"
    }

    "P2 CreateBlock to ChainEnd appends to correct chain when multiple exist" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        val fastChainId = c.chain("fast").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.ChainEnd(fastChainId)))
        c.code() shouldBe "sound(\"bd\")\nfast(2).slow()"
    }

    "P3 CreateBlock to ChainInsert inserts before target block" {
        val c = ctx("sound(\"bd\").fast(2)")
        val chainId = c.chain("sound").id
        val fastId = c.block("fast").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainInsert(chainId, fastId)))
        c.code() shouldBe "sound(\"bd\").gain().fast(2)"
    }

    "P3 CreateBlock to ChainInsert with null insertBeforeBlockId appends to chain" {
        val c = ctx("sound(\"bd\").fast(2)")
        val chainId = c.chain("sound").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainInsert(chainId, null)))
        c.code() shouldBe "sound(\"bd\").fast(2).gain()"
    }

    "P4 CreateBlock to EmptySlot fills the slot" {
        val c = ctx("sound(\"bd\").gain(0.5)")
        val gainId = c.block("gain").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.EmptySlot(gainId, 0)))
        c.code() shouldBe "sound(\"bd\").gain(slow())"
    }

    "P4 CreateBlock to EmptySlot preserves existing string head" {
        val c = ctx("sound(\"bd\").gain(\"C4\")")
        val gainId = c.block("gain").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.EmptySlot(gainId, 0)))
        c.code() shouldBe "sound(\"bd\").gain(\"C4\".slow())"
    }

    // ── A: Single block move ──────────────────────────────────────────────────

    "A1 MoveBlocks single to RowGap extracts block as new chain" {
        val c = ctx("sound(\"bd\").fast(2).slow(4)")
        val fast = c.block("fast")
        c.execute(DropAction.MoveBlocks(listOf(fast), DropDestination.RowGap(0)))
        c.code() shouldBe "fast(2)\nsound(\"bd\").slow(4)"
    }

    "A1 MoveBlocks single: removing last block collapses source chain" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        val sound = c.block("sound")
        c.execute(DropAction.MoveBlocks(listOf(sound), DropDestination.RowGap(2)))
        // "sound" chain collapses, new single-block chain appears at end
        c.rowCount() shouldBe 2
        c.code() shouldBe "fast(2)\nsound(\"bd\")"
    }

    "A2 MoveBlocks single to ChainEnd appends to target" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        val fast = c.block("fast")
        val soundChainId = c.chain("sound").id
        c.execute(DropAction.MoveBlocks(listOf(fast), DropDestination.ChainEnd(soundChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(\"bd\").fast(2)"
    }

    "A3 MoveBlocks single to ChainInsert inserts before target block" {
        val c = ctx("sound(\"bd\").fast(2)\ngain(0.5)")
        val gain = c.block("gain")
        val soundChainId = c.chain("sound").id
        val fastId = c.block("fast").id
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.ChainInsert(soundChainId, fastId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(\"bd\").gain(0.5).fast(2)"
    }

    "A4 MoveBlocks single to EmptySlot places block in slot" {
        // fast(2) has a number arg at slot 0 — no string head preserved
        val c = ctx("fast(2)\ngain(0.5)")
        val gain = c.block("gain")
        val fastId = c.block("fast").id
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.EmptySlot(fastId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "fast(gain(0.5))"
    }

    "A4 MoveBlocks single to EmptySlot preserves existing string as chain head" {
        // sound("bd") has a string arg — buildSlotDropChain preserves it as a string literal head
        val c = ctx("sound(\"bd\")\ngain(0.5)")
        val gain = c.block("gain")
        val soundId = c.block("sound").id
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.EmptySlot(soundId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(\"bd\".gain(0.5))"
    }

    // ── B: Tail / multi-block move ────────────────────────────────────────────

    "B1 MoveBlocks tail to RowGap creates new chain with correct head" {
        val c = ctx("sound(\"bd\").fast(2).slow(4)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        c.execute(DropAction.MoveBlocks(tail, DropDestination.RowGap(0)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "fast(2).slow(4)\nsound(\"bd\")"
    }

    "B2 MoveBlocks tail to ChainEnd appends all blocks and source collapses" {
        // tail("fast") on "fast(2).slow(4)" = [fast, slow] — all blocks, so source collapses
        val c = ctx("fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        val gainChainId = c.chain("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainEnd(gainChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "gain(0.5).fast(2).slow(4)"
    }

    "B2 MoveBlocks tail leaves head block in source chain" {
        // tail("fast") on "sound.fast.slow" = [fast, slow] — sound remains
        val c = ctx("sound(\"bd\").fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        val gainChainId = c.chain("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainEnd(gainChainId)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "sound(\"bd\")\ngain(0.5).fast(2).slow(4)"
    }

    "B3 MoveBlocks tail to ChainInsert inserts all blocks before target" {
        // b().c() is the whole chain, so source collapses after tail removed
        val c = ctx("b().c()\nd().e()")
        val tail = c.tail("b")
        tail.size shouldBe 2
        val dChainId = c.chain("d").id
        val eId = c.block("e").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainInsert(dChainId, eId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "d().b().c().e()"
    }

    "B4 MoveBlocks tail to EmptySlot nests all blocks as chain" {
        // fast(2).slow(4) is the whole chain — source collapses
        val c = ctx("fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        val gainId = c.block("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.EmptySlot(gainId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "gain(fast(2).slow(4))"
    }

    // ── C: MoveRow ────────────────────────────────────────────────────────────

    "C1 MoveRow moves chain row to lower index" {
        val c = ctx("sound(\"bd\")\nfast(2)\nslow(4)")
        val slowId = c.chain("slow").id
        c.execute(DropAction.MoveRow(slowId, 0))
        c.code() shouldBe "slow(4)\nsound(\"bd\")\nfast(2)"
    }

    "C1 MoveRow moves chain row to higher index" {
        val c = ctx("sound(\"bd\")\nfast(2)\nslow(4)")
        val soundId = c.chain("sound").id
        c.execute(DropAction.MoveRow(soundId, 3))
        c.code() shouldBe "fast(2)\nslow(4)\nsound(\"bd\")"
    }

    "C1 MoveRow no-op when moving to same position" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        val soundId = c.chain("sound").id
        val before = c.code()
        c.execute(DropAction.MoveRow(soundId, 0))
        c.code() shouldBe before
    }

    // ── Compound ──────────────────────────────────────────────────────────────

    "Compound executes actions in sequence on updated state" {
        val c = ctx("sound(\"bd\")")
        val chainId = c.chain("sound").id
        c.execute(
            DropAction.Compound(
                listOf(
                    DropAction.CreateBlock("fast", DropDestination.ChainEnd(chainId)),
                    DropAction.CreateBlock("slow", DropDestination.ChainEnd(chainId)),
                )
            )
        )
        c.code() shouldBe "sound(\"bd\").fast().slow()"
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    "execute is undoable" {
        val c = ctx("sound(\"bd\")")
        val chainId = c.chain("sound").id
        c.execute(DropAction.CreateBlock("fast", DropDestination.ChainEnd(chainId)))
        c.code() shouldBe "sound(\"bd\").fast()"
        c.undo()
        c.code() shouldBe "sound(\"bd\")"
    }

    "execute is redoable after undo" {
        val c = ctx("sound(\"bd\")")
        val chainId = c.chain("sound").id
        c.execute(DropAction.CreateBlock("fast", DropDestination.ChainEnd(chainId)))
        c.undo()
        c.redo()
        c.code() shouldBe "sound(\"bd\").fast()"
    }

    // ── Nested chain targets ──────────────────────────────────────────────────

    "CreateBlock to ChainEnd of nested chain appends inside the slot" {
        val c = ctx("sound(\"bd\").gain(fast(2))")
        val fastChainId = (c.block("gain").args[0] as KBNestedChainArg).chain.id
        c.execute(DropAction.CreateBlock("slow", DropDestination.ChainEnd(fastChainId)))
        c.code() shouldBe "sound(\"bd\").gain(fast(2).slow())"
    }

    "MoveBlocks single to ChainEnd of nested chain appends inside" {
        val c = ctx("sound(\"bd\").gain(fast(2))\nslow(4)")
        val fastChainId = (c.block("gain").args[0] as KBNestedChainArg).chain.id
        val slow = c.block("slow")
        c.execute(DropAction.MoveBlocks(listOf(slow), DropDestination.ChainEnd(fastChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(\"bd\").gain(fast(2).slow(4))"
    }
})
