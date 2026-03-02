package io.peekandpoke.klang.blocks.dnd

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.blocks.model.DropAction
import io.peekandpoke.klang.blocks.model.DropDestination
import io.peekandpoke.klang.blocks.model.KBNestedChainArg

class DropActionTest : StringSpec({

    // ── P1: Palette → row gap ─────────────────────────────────────────────────

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

    // ── P2: Palette → chain append ────────────────────────────────────────────

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

    "P2 deep CreateBlock to ChainEnd of level-3 nested chain" {
        val c = ctx("outer(mid(inner()))\nx()")
        val innerChainId = c.chain("inner").id
        c.execute(DropAction.CreateBlock("y", DropDestination.ChainEnd(innerChainId)))
        c.code() shouldBe "outer(mid(inner().y()))\nx()"
    }

    // ── P3: Palette → chain insert ────────────────────────────────────────────

    "P3 CreateBlock to ChainInsert inserts before target block" {
        val c = ctx("sound(\"bd\").fast(2)")
        val chainId = c.chain("sound").id
        val fastId = c.block("fast").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainInsert(chainId, fastId)))
        c.code() shouldBe "sound(\"bd\").gain().fast(2)"
    }

    "P3 CreateBlock to ChainInsert with null insertBeforeBlockId appends" {
        val c = ctx("sound(\"bd\").fast(2)")
        val chainId = c.chain("sound").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainInsert(chainId, null)))
        c.code() shouldBe "sound(\"bd\").fast(2).gain()"
    }

    "P3 deep CreateBlock to ChainInsert in level-3 nested chain" {
        val c = ctx("outer(mid(inner().tail()))")
        val innerChainId = c.chain("inner").id
        val tailId = c.block("tail").id
        c.execute(DropAction.CreateBlock("y", DropDestination.ChainInsert(innerChainId, tailId)))
        c.code() shouldBe "outer(mid(inner().y().tail()))"
    }

    // ── P4: Palette → empty slot ──────────────────────────────────────────────

    "P4 CreateBlock to EmptySlot fills the slot" {
        val c = ctx("sound(\"bd\").gain(0.5)")
        val gainId = c.block("gain").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.EmptySlot(gainId, 0)))
        c.code() shouldBe "sound(\"bd\").gain(slow())"
    }

    "P4 CreateBlock to EmptySlot discards existing string arg" {
        val c = ctx("sound(\"bd\").gain(\"C4\")")
        val gainId = c.block("gain").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.EmptySlot(gainId, 0)))
        c.code() shouldBe "sound(\"bd\").gain(slow())"
    }

    "P4 deep CreateBlock to EmptySlot of level-3 block" {
        val c = ctx("outer(mid(inner()))")
        val innerId = c.block("inner").id
        c.execute(DropAction.CreateBlock("y", DropDestination.EmptySlot(innerId, 0)))
        c.code() shouldBe "outer(mid(inner(y())))"
    }

    // ── A1: Single block → row gap ────────────────────────────────────────────

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
        c.rowCount() shouldBe 2
        c.code() shouldBe "fast(2)\nsound(\"bd\")"
    }

    "A1 deep MoveBlocks from level-3 to RowGap creates new row" {
        val c = ctx("outer(mid(inner().tail()))\nother()")
        val tail = c.block("tail")
        c.execute(DropAction.MoveBlocks(listOf(tail), DropDestination.RowGap(2)))
        c.code() shouldBe "outer(mid(inner()))\nother()\ntail()"
    }

    // ── A2/A3: Single block → chain end / insert ──────────────────────────────

    "A2 MoveBlocks single to ChainEnd appends to target" {
        val c = ctx("sound(\"bd\")\nfast(2)")
        val fast = c.block("fast")
        val soundChainId = c.chain("sound").id
        c.execute(DropAction.MoveBlocks(listOf(fast), DropDestination.ChainEnd(soundChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(\"bd\").fast(2)"
    }

    "A2 deep MoveBlocks from level-1 to level-3 ChainEnd" {
        val c = ctx("outer(mid(inner()))\nx()")
        val x = c.block("x")
        val innerChainId = c.chain("inner").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ChainEnd(innerChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(inner().x()))"
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

    "A3 deep MoveBlocks from level-1 to level-3 ChainInsert" {
        val c = ctx("outer(mid(inner().tail()))\nx()")
        val x = c.block("x")
        val innerChainId = c.chain("inner").id
        val tailId = c.block("tail").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ChainInsert(innerChainId, tailId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(inner().x().tail()))"
    }

    // ── A4/A5: Single block → empty slot ──────────────────────────────────────

    "A4 MoveBlocks single to EmptySlot places block in slot" {
        val c = ctx("fast(2)\ngain(0.5)")
        val gain = c.block("gain")
        val fastId = c.block("fast").id
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.EmptySlot(fastId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "fast(gain(0.5))"
    }

    "A4 MoveBlocks single to EmptySlot discards existing string arg" {
        val c = ctx("sound(\"bd\")\ngain(0.5)")
        val gain = c.block("gain")
        val soundId = c.block("sound").id
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.EmptySlot(soundId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "sound(gain(0.5))"
    }

    "A4 deep MoveBlocks from level-1 to EmptySlot of level-3 block" {
        val c = ctx("outer(mid(inner()))\nx()")
        val x = c.block("x")
        val innerId = c.block("inner").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.EmptySlot(innerId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(inner(x())))"
    }

    "A4 deep MoveBlocks from level-3 to different level-3 ChainEnd" {
        val c = ctx("outer(mid(inner().leftover()))\nother(deep(x()))")
        val inner = c.block("inner")
        val xChainId = c.chain("x").id
        c.execute(DropAction.MoveBlocks(listOf(inner), DropDestination.ChainEnd(xChainId)))
        c.code() shouldBe "outer(mid(leftover()))\nother(deep(x().inner()))"
    }

    "A4 self ChainInsert drag block before itself is a no-op" {
        val c = ctx("a().b().c()")
        val b = c.block("b")
        val chainId = c.chain("b").id
        c.execute(DropAction.MoveBlocks(listOf(b), DropDestination.ChainInsert(chainId, b.id)))
        // insertBeforeBlockId == b.id which is in the payload → early exit, chain unchanged
        c.code() shouldBe "a().b().c()"
    }

    // ── B1: Tail → row gap ────────────────────────────────────────────────────

    "B1 MoveBlocks tail to RowGap creates new chain with correct head" {
        val c = ctx("sound(\"bd\").fast(2).slow(4)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        c.execute(DropAction.MoveBlocks(tail, DropDestination.RowGap(0)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "fast(2).slow(4)\nsound(\"bd\")"
    }

    "B1 deep MoveBlocks tail from level-3 to RowGap" {
        val c = ctx("outer(mid(inner().t1().t2()))")
        val tl = c.tail("inner")
        tl.size shouldBe 3
        c.execute(DropAction.MoveBlocks(tl, DropDestination.RowGap(1)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "outer(mid())\ninner().t1().t2()"
    }

    // ── B2/B3: Tail → chain end / insert ──────────────────────────────────────

    "B2 MoveBlocks tail to ChainEnd appends all blocks and source collapses" {
        val c = ctx("fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        val gainChainId = c.chain("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainEnd(gainChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "gain(0.5).fast(2).slow(4)"
    }

    "B2 MoveBlocks tail leaves head block in source chain" {
        val c = ctx("sound(\"bd\").fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        val gainChainId = c.chain("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainEnd(gainChainId)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "sound(\"bd\")\ngain(0.5).fast(2).slow(4)"
    }

    "B2 deep MoveBlocks tail from level-1 to level-3 ChainEnd" {
        val c = ctx("outer(mid(inner()))\nx().y()")
        val tl = c.tail("x")
        tl.size shouldBe 2
        val innerChainId = c.chain("inner").id
        c.execute(DropAction.MoveBlocks(tl, DropDestination.ChainEnd(innerChainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(inner().x().y()))"
    }

    "B3 MoveBlocks tail to ChainInsert inserts all blocks before target" {
        val c = ctx("b().c()\nd().e()")
        val tail = c.tail("b")
        tail.size shouldBe 2
        val dChainId = c.chain("d").id
        val eId = c.block("e").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.ChainInsert(dChainId, eId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "d().b().c().e()"
    }

    // ── B4/B5: Tail → empty slot ──────────────────────────────────────────────

    "B4 MoveBlocks tail to EmptySlot nests all blocks as chain" {
        val c = ctx("fast(2).slow(4)\ngain(0.5)")
        val tail = c.tail("fast")
        tail.size shouldBe 2
        val gainId = c.block("gain").id
        c.execute(DropAction.MoveBlocks(tail, DropDestination.EmptySlot(gainId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "gain(fast(2).slow(4))"
    }

    "B4 self ChainInsert drag tail before itself is a no-op" {
        val c = ctx("a().b().c()")
        val tl = c.tail("b")
        tl.size shouldBe 2
        val chainId = c.chain("b").id
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(tl, DropDestination.ChainInsert(chainId, bId)))
        // insertBeforeBlockId == b.id which is in the payload → early exit, chain unchanged
        c.code() shouldBe "a().b().c()"
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

    // ── Nested chain targets (regression) ────────────────────────────────────

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
