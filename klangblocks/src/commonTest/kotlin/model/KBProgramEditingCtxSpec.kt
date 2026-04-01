package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KBProgramEditingCtxSpec : StringSpec({

    /** Creates a distinct KBProgram for each index using a unique import statement. */
    fun programAt(i: Int) = KBProgram(
        statements = listOf(KBImportStmt(id = "id$i", libraryName = "lib$i"))
    )

    "undo stack is capped at MAX_UNDO_DEPTH" {
        val ctx = KBProgramEditingCtx(programAt(-1))
        val limit = KBProgramEditingCtx.MAX_UNDO_DEPTH

        // Push limit + 100 updates
        repeat(limit + 100) { i ->
            ctx.update { programAt(i) }
        }

        // Undo should succeed exactly MAX_UNDO_DEPTH times
        var undoCount = 0
        while (ctx.canUndo) {
            ctx.undo()
            undoCount++
        }

        undoCount shouldBe limit
    }

    "undo and redo work correctly" {
        val p0 = programAt(0)
        val p1 = programAt(1)
        val p2 = programAt(2)

        val ctx = KBProgramEditingCtx(p0)
        ctx.update { p1 }
        ctx.update { p2 }

        ctx.program shouldBe p2
        ctx.canUndo shouldBe true

        ctx.undo()
        ctx.program shouldBe p1
        ctx.canRedo shouldBe true

        ctx.redo()
        ctx.program shouldBe p2
    }
})
