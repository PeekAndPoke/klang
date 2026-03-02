package io.peekandpoke.klang.blocks.dnd

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.blocks.model.DropAction
import io.peekandpoke.klang.blocks.model.DropDestination

class ReplaceBlockTest : StringSpec({

    // ── P5: CreateBlock → ReplaceBlock ────────────────────────────────────────

    "P5 CreateBlock to ReplaceBlock replaces middle of chain" {
        val c = ctx("a().b().c()")
        val bId = c.block("b").id
        c.execute(DropAction.CreateBlock("x", DropDestination.ReplaceBlock(bId)))
        c.code() shouldBe "a().x().c()"
    }

    "P5 CreateBlock to ReplaceBlock at chain head" {
        val c = ctx("a().b().c()")
        val aId = c.block("a").id
        c.execute(DropAction.CreateBlock("x", DropDestination.ReplaceBlock(aId)))
        c.code() shouldBe "x().b().c()"
    }

    "P5 CreateBlock to ReplaceBlock single-block chain" {
        val c = ctx("a()\nb()")
        val aId = c.block("a").id
        c.execute(DropAction.CreateBlock("x", DropDestination.ReplaceBlock(aId)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "x()\nb()"
    }

    "P5 deep CreateBlock to ReplaceBlock at level-2 nested block" {
        val c = ctx("outer(inner().tail())")
        val innerId = c.block("inner").id
        c.execute(DropAction.CreateBlock("x", DropDestination.ReplaceBlock(innerId)))
        c.code() shouldBe "outer(x().tail())"
    }

    "P5 deep CreateBlock to ReplaceBlock at level-3 nested block" {
        val c = ctx("outer(mid(inner().tail()))")
        val innerId = c.block("inner").id
        c.execute(DropAction.CreateBlock("x", DropDestination.ReplaceBlock(innerId)))
        c.code() shouldBe "outer(mid(x().tail()))"
    }

    // ── A6: MoveBlocks single → ReplaceBlock ──────────────────────────────────

    "A6 MoveBlocks single to ReplaceBlock replaces target" {
        val c = ctx("a().b().c()\nx()")
        val x = c.block("x")
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ReplaceBlock(bId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "a().x().c()"
    }

    "A6 MoveBlocks single to ReplaceBlock self is no-op" {
        val c = ctx("a().b().c()")
        val b = c.block("b")
        val before = c.code()
        c.execute(DropAction.MoveBlocks(listOf(b), DropDestination.ReplaceBlock(b.id)))
        c.code() shouldBe before
    }

    "A6 deep MoveBlocks from level-1 to ReplaceBlock at level-3" {
        val c = ctx("outer(mid(inner().tail()))\nx()")
        val x = c.block("x")
        val innerId = c.block("inner").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ReplaceBlock(innerId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(x().tail()))"
    }

    "A6 deep MoveBlocks from level-3 to ReplaceBlock at level-1" {
        val c = ctx("a().b().c()\nother(deep(x()))")
        val x = c.block("x")
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ReplaceBlock(bId)))
        // x removed from deep's chain → deep's slot = empty → other(deep()) stays
        c.rowCount() shouldBe 2
        c.code() shouldBe "a().x().c()\nother(deep())"
    }

    "A6 deep MoveBlocks from level-3 to ReplaceBlock at level-3 (different chains)" {
        val c = ctx("outer(mid(inner().leftover()))\nother(deep(x()))")
        val x = c.block("x")
        val innerId = c.block("inner").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ReplaceBlock(innerId)))
        c.code() shouldBe "outer(mid(x().leftover()))\nother(deep())"
    }

    "A6 replace is undoable" {
        val c = ctx("a().b().c()\nx()")
        val x = c.block("x")
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(listOf(x), DropDestination.ReplaceBlock(bId)))
        c.code() shouldBe "a().x().c()"
        c.undo()
        c.code() shouldBe "a().b().c()\nx()"
    }

    // ── B6: MoveBlocks tail → ReplaceBlock ────────────────────────────────────

    "B6 MoveBlocks tail to ReplaceBlock replaces target in-place" {
        val c = ctx("a().b().c()\nd().e().f()")
        val tl = c.tail("d")
        tl.size shouldBe 3
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(tl, DropDestination.ReplaceBlock(bId)))
        // [d,e,f] removed → their chain collapses; b replaced with [d',e',f'] → [a,d',e',f',c]
        c.rowCount() shouldBe 1
        c.code() shouldBe "a().d().e().f().c()"
    }

    "B6 MoveBlocks tail to ReplaceBlock cycle guard: target inside payload is no-op" {
        val c = ctx("a(b().c())")
        val a = c.block("a")
        val bId = c.block("b").id
        val before = c.code()
        // b is nested inside a → moving [a] to replace b would create a cycle
        c.execute(DropAction.MoveBlocks(listOf(a), DropDestination.ReplaceBlock(bId)))
        c.code() shouldBe before
    }

    "B6 deep MoveBlocks tail from level-1 to ReplaceBlock at level-3" {
        val c = ctx("outer(mid(inner().tail()))\na().b()")
        val tl = c.tail("a")
        tl.size shouldBe 2
        val innerId = c.block("inner").id
        c.execute(DropAction.MoveBlocks(tl, DropDestination.ReplaceBlock(innerId)))
        // [a,b] removed → row 2 gone; inner replaced with [a',b'] → [a'(head),b',tail]
        c.rowCount() shouldBe 1
        c.code() shouldBe "outer(mid(a().b().tail()))"
    }

    "B6 deep MoveBlocks tail from level-3 to ReplaceBlock at level-1" {
        val c = ctx("a().b().c()\nouter(mid(x().y()))")
        val tl = c.tail("x")
        tl.size shouldBe 2
        val bId = c.block("b").id
        c.execute(DropAction.MoveBlocks(tl, DropDestination.ReplaceBlock(bId)))
        // [x,y] removed → mid's slot empty → outer(mid()) stays; b replaced by [x',y']
        c.rowCount() shouldBe 2
        c.code() shouldBe "a().x().y().c()\nouter(mid())"
    }
})
