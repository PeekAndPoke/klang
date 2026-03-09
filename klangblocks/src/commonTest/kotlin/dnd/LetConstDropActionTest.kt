package io.peekandpoke.klang.blocks.dnd

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.blocks.model.*

class LetConstDropActionTest : StringSpec({

    // ── D1: Row-handle drag — let / const ─────────────────────────────────────

    "D1 MoveRow moves KBLetStmt to a lower index" {
        val c = ctx("sound(\"bd\")\nlet x\ngain(0.5)")
        val letId = c.letStmt("x").id
        c.execute(DropAction.MoveRow(letId, 0))
        c.code() shouldBe "let x\nsound(\"bd\")\ngain(0.5)"
    }

    "D1 MoveRow moves KBLetStmt to a higher index" {
        val c = ctx("let x\nsound(\"bd\")\ngain(0.5)")
        val letId = c.letStmt("x").id
        c.execute(DropAction.MoveRow(letId, 3))
        c.code() shouldBe "sound(\"bd\")\ngain(0.5)\nlet x"
    }

    "D1 MoveRow moves KBConstStmt to a lower index" {
        val c = ctx("sound(\"bd\")\nconst bpm = 120\ngain(0.5)")
        val constId = c.constStmt("bpm").id
        c.execute(DropAction.MoveRow(constId, 0))
        c.code() shouldBe "const bpm = 120\nsound(\"bd\")\ngain(0.5)"
    }

    "D1 MoveRow moves KBConstStmt to a higher index" {
        val c = ctx("const bpm = 120\nsound(\"bd\")\ngain(0.5)")
        val constId = c.constStmt("bpm").id
        c.execute(DropAction.MoveRow(constId, 3))
        c.code() shouldBe "sound(\"bd\")\ngain(0.5)\nconst bpm = 120"
    }

    "D1 MoveRow no-op when let is already at target position" {
        val c = ctx("let x\nsound(\"bd\")")
        val letId = c.letStmt("x").id
        val before = c.code()
        c.execute(DropAction.MoveRow(letId, 0))
        c.code() shouldBe before
    }

    // ── P4: Palette → let/const value slot ────────────────────────────────────

    "P4 CreateBlock to EmptySlot of KBLetStmt sets value as nested chain" {
        val c = ctx("let p\nsound(\"bd\")")
        val letId = c.letStmt("p").id
        c.execute(DropAction.CreateBlock("fast", DropDestination.EmptySlot(letId, 0)))
        c.code() shouldBe "let p = fast()\nsound(\"bd\")"
    }

    "P4 CreateBlock to EmptySlot of KBConstStmt sets value as nested chain" {
        val c = ctx("const bpm = 120")
        val constId = c.constStmt("bpm").id
        c.execute(DropAction.CreateBlock("slow", DropDestination.EmptySlot(constId, 0)))
        c.code() shouldBe "const bpm = slow()"
    }

    // ── A5: Single block → let/const value slot ───────────────────────────────

    "A5 MoveBlocks to EmptySlot of KBLetStmt fills the value" {
        val c = ctx("let p\nsound(\"bd\")")
        val letId = c.letStmt("p").id
        val sound = c.block("sound")
        c.execute(DropAction.MoveBlocks(listOf(sound), DropDestination.EmptySlot(letId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "let p = sound(\"bd\")"
    }

    "A5 MoveBlocks to EmptySlot of KBConstStmt fills the value" {
        val c = ctx("const x = 0\ngain(0.5)")
        val constId = c.constStmt("x").id
        val gain = c.block("gain")
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.EmptySlot(constId, 0)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "const x = gain(0.5)"
    }

    // ── onArgChanged: updating scalar values in let/const ─────────────────────

    "onArgChanged(letId, 0, ...) updates KBLetStmt value" {
        val c = ctx("let x\nsound(\"bd\")")
        val letId = c.letStmt("x").id
        c.onArgChanged(letId, 0, KBNumberArg(120.0))
        c.code() shouldBe "let x = 120\nsound(\"bd\")"
    }

    "onArgChanged(constId, 0, ...) updates KBConstStmt value" {
        val c = ctx("const bpm = 0")
        val constId = c.constStmt("bpm").id
        c.onArgChanged(constId, 0, KBNumberArg(140.0))
        c.code() shouldBe "const bpm = 140"
    }

    // ── Chain operations within let/const nested values ───────────────────────

    "P2 CreateBlock to ChainEnd of chain nested in KBLetStmt value" {
        val c = ctx("let p = sound(\"bd\")")
        val chainId = (c.letStmt("p").value as KBNestedChainArg).chain.id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainEnd(chainId)))
        c.code() shouldBe "let p = sound(\"bd\").gain()"
    }

    "P2 CreateBlock to ChainEnd of chain nested in KBConstStmt value" {
        val c = ctx("const kick = sound(\"bd\")")
        val chainId = (c.constStmt("kick").value as KBNestedChainArg).chain.id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainEnd(chainId)))
        c.code() shouldBe "const kick = sound(\"bd\").gain()"
    }

    "P3 CreateBlock to ChainInsert within KBLetStmt value chain" {
        val c = ctx("let p = sound(\"bd\").slow(2)")
        val chainId = (c.letStmt("p").value as KBNestedChainArg).chain.id
        val slowId = c.block("slow").id
        c.execute(DropAction.CreateBlock("gain", DropDestination.ChainInsert(chainId, slowId)))
        c.code() shouldBe "let p = sound(\"bd\").gain().slow(2)"
    }

    "A3 MoveBlocks to ChainEnd of KBLetStmt value chain" {
        val c = ctx("let p = sound(\"bd\")\ngain(0.5)")
        val chainId = (c.letStmt("p").value as KBNestedChainArg).chain.id
        val gain = c.block("gain")
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.ChainEnd(chainId)))
        c.rowCount() shouldBe 1
        c.code() shouldBe "let p = sound(\"bd\").gain(0.5)"
    }

    "A1 MoveBlocks out of KBLetStmt value chain to RowGap" {
        val c = ctx("let p = sound(\"bd\").gain(0.5)")
        val gain = c.block("gain")
        c.execute(DropAction.MoveBlocks(listOf(gain), DropDestination.RowGap(1)))
        c.rowCount() shouldBe 2
        c.code() shouldBe "let p = sound(\"bd\")\ngain(0.5)"
    }

    // ── onRemoveBlock within let/const values ─────────────────────────────────

    "onRemoveBlock removes block from KBLetStmt nested value" {
        val c = ctx("let p = sound(\"bd\").gain(0.5)")
        val gainId = c.block("gain").id
        c.onRemoveBlock(gainId)
        c.code() shouldBe "let p = sound(\"bd\")"
    }

    "onRemoveBlock collapses KBLetStmt value to empty when last block removed" {
        val c = ctx("let p = sound(\"bd\")")
        val soundId = c.block("sound").id
        c.onRemoveBlock(soundId)
        // Value collapses to KBEmptyArg, generated as empty string → "let p = "
        // which after trimming gives "let p ="... Actually KBEmptyArg.toCode() = "" so
        // the output is "let p = " – check the actual string
        val stmt = c.letStmt("p")
        stmt.value.shouldBeInstanceOf<KBEmptyArg>()
    }

    // ── Undo / redo for let/const mutations ───────────────────────────────────

    "undo reverts P4 drop into let value slot" {
        val c = ctx("let p\nsound(\"bd\")")
        val letId = c.letStmt("p").id
        c.execute(DropAction.CreateBlock("fast", DropDestination.EmptySlot(letId, 0)))
        c.code() shouldBe "let p = fast()\nsound(\"bd\")"
        c.undo()
        c.code() shouldBe "let p\nsound(\"bd\")"
    }

    "undo reverts D1 MoveRow for let stmt" {
        val c = ctx("sound(\"bd\")\nlet x\ngain(0.5)")
        val letId = c.letStmt("x").id
        c.execute(DropAction.MoveRow(letId, 0))
        c.code() shouldBe "let x\nsound(\"bd\")\ngain(0.5)"
        c.undo()
        c.code() shouldBe "sound(\"bd\")\nlet x\ngain(0.5)"
    }
})
