package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.parser.KlangScriptParser

// ── Top-level helpers ─────────────────────────────────────────────────────────

/** Parses KlangScript source → KBProgram and generates code in one step. */
private fun compile(source: String): Pair<KBProgram, CodeGenResult> {
    val ast = KlangScriptParser.parse(source)
    val program = AstToKBlocks.convert(ast)
    return program to program.toCodeGen()
}

/**
 * Collects all [KBCallBlock]s from a program depth-first, including those nested
 * inside [KBNestedChainArg] slots.
 */
private fun KBProgram.allBlocks(): List<KBCallBlock> {
    fun collect(steps: List<KBChainItem>): List<KBCallBlock> {
        val result = mutableListOf<KBCallBlock>()
        for (step in steps) {
            if (step is KBCallBlock) {
                result.add(step)
                for (arg in step.args) {
                    if (arg is KBNestedChainArg) {
                        result.addAll(collect(arg.chain.steps))
                    }
                }
            }
        }
        return result
    }
    return statements.filterIsInstance<KBChainStmt>().flatMap { collect(it.steps) }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

/**
 * Round-trip tests: KlangScript source → KBProgram → generated code → location map.
 *
 * Each test verifies that [CodeGenResult.findAt] returns the correct [KBCallBlock.id],
 * slot index and offset-within-slot for key positions in the generated code.
 *
 * Position notation used in comments:
 *   - Columns are 1-based (col 1 = first character of the line).
 *   - Character offsets in the generated string are 0-based.
 *   - Content offsets within a string slot are 0-based relative to the first character
 *     of the slot value (i.e. the character after the opening quote).
 */
class KBCodeGenTest : StringSpec({

    // ── Single block ──────────────────────────────────────────────────────────

    "single block: generated code round-trips to the original source" {
        val src = """sound("bd hh sd oh")"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "single block: function name position hits block, no slot info" {
        // sound("bd hh sd oh")
        // col 1 = 's'  →  char 0  →  inside block, outside any string content
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 1)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe null
        hit.offsetInSlot shouldBe null
    }

    "single block: opening quote is outside the slot content range" {
        // sound("bd hh sd oh")
        // col 7 = '"'  →  char 6  →  opening quote is NOT included in the content range
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 7)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe null
        hit.offsetInSlot shouldBe null
    }

    "single block: first atom 'bd' maps to slot 0, offset 0" {
        // sound("bd hh sd oh")
        // col 8 = 'b'  →  char 7  →  first char of slot 0 content ("bd hh sd oh")
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 8)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe 0
        hit.offsetInSlot shouldBe 0
    }

    "single block: atom 'hh' maps to slot 0, offset 3" {
        // sound("bd hh sd oh")
        // col 11 = first 'h'  →  char 10  →  content offset 3  (b=0, d=1, ' '=2, h=3)
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 11)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe 0
        hit.offsetInSlot shouldBe 3
    }

    "single block: atom 'sd' maps to slot 0, offset 6" {
        // sound("bd hh sd oh")
        // col 14 = 's'  →  char 13  →  content offset 6  (b=0,d=1,' '=2,h=3,h=4,' '=5,s=6)
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 14)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe 0
        hit.offsetInSlot shouldBe 6
    }

    "single block: closing quote is outside the slot content range" {
        // sound("bd hh sd oh")
        // col 19 = '"'  →  char 18  →  closing quote, content ends at char 17 ('h' of 'oh')
        val (program, result) = compile("""sound("bd hh sd oh")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        val hit = result.findAt(1, 19)
        hit shouldNotBe null
        hit!!.blockId shouldBe soundId
        hit.slotIndex shouldBe null
        hit.offsetInSlot shouldBe null
    }

    "single block: position past closing paren returns null" {
        // sound("bd hh sd oh")  is 20 chars (cols 1–20);  col 21 is past the end
        val (_, result) = compile("""sound("bd hh sd oh")""")
        result.findAt(1, 21) shouldBe null
    }

    // ── Multiple slots in one block ───────────────────────────────────────────

    "multiple slots: each slot's content range is correctly indexed" {
        // note("c3", "e3", "g3")
        // Slot 0 "c3": content at chars 6–7  →  col 7
        // Slot 1 "e3": content at chars 12–13 →  col 13
        // Slot 2 "g3": content at chars 18–19 →  col 19
        val src = """note("c3", "e3", "g3")"""
        val (program, result) = compile(src)
        val noteId = program.allBlocks().first { it.funcName == "note" }.id

        result.code shouldBe src

        result.findAt(1, 7)!!.let { hit ->
            hit.blockId shouldBe noteId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        result.findAt(1, 13)!!.let { hit ->
            hit.blockId shouldBe noteId
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 0
        }

        result.findAt(1, 19)!!.let { hit ->
            hit.blockId shouldBe noteId
            hit.slotIndex shouldBe 2
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── Chained blocks ────────────────────────────────────────────────────────

    "chained blocks: each block's range is disjoint" {
        // sound("bd").gain(0.5)
        // sound block: chars 0–10   (col  1 = 's', col  8 = 'b' in "bd")
        // '.' separator at char 11 (col 12)
        // gain block:  chars 12–20  (col 13 = 'g', col 18 = '0' in 0.5)
        val src = """sound("bd").gain(0.5)"""
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id
        val gainId = program.allBlocks().first { it.funcName == "gain" }.id

        result.code shouldBe src

        // Function names
        result.findAt(1, 1)!!.blockId shouldBe soundId
        result.findAt(1, 13)!!.blockId shouldBe gainId

        // String slot inside sound
        result.findAt(1, 8)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Numeric slot inside gain — tracked as block hit but no content range
        result.findAt(1, 18)!!.let { hit ->
            hit.blockId shouldBe gainId
            hit.slotIndex shouldBe null
        }
    }

    "three chained blocks: all resolve independently" {
        // note("c3").gain(0.5).slow(2)
        // note block:  chars 0–9   (col  1)
        // gain block:  chars 11–19 (col 12)
        // slow block:  chars 21–27 (col 22)
        val src = """note("c3").gain(0.5).slow(2)"""
        val (program, result) = compile(src)
        val noteId = program.allBlocks().first { it.funcName == "note" }.id
        val gainId = program.allBlocks().first { it.funcName == "gain" }.id
        val slowId = program.allBlocks().first { it.funcName == "slow" }.id

        result.code shouldBe src

        result.findAt(1, 1)!!.blockId shouldBe noteId
        result.findAt(1, 12)!!.blockId shouldBe gainId
        result.findAt(1, 22)!!.blockId shouldBe slowId
    }

    // ── Multiple statements ───────────────────────────────────────────────────

    "two statements: blocks on separate lines resolve without cross-contamination" {
        // Line 1: sound("bd")   — chars 0–10
        // '\n' at char 11
        // Line 2: note("c3")    — chars 12–21
        val src = "sound(\"bd\")\nnote(\"c3\")"
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id
        val noteId = program.allBlocks().first { it.funcName == "note" }.id

        result.code shouldBe src

        // Line 1
        result.findAt(1, 1)!!.blockId shouldBe soundId
        result.findAt(1, 8)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 2: col 1 = 'n', col 7 = 'c' in "c3"
        result.findAt(2, 1)!!.blockId shouldBe noteId
        result.findAt(2, 7)!!.let { hit ->
            hit.blockId shouldBe noteId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
    }

    "import statement at top shifts block to line 2" {
        // Line 1: import * from "strudel"   (23 chars, no block ranges)
        // '\n' at char 23
        // Line 2: sound("bd")   — chars 24–34
        val src = "import * from \"strudel\"\nsound(\"bd\")"
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        result.code shouldBe src

        // Import line has no block
        result.findAt(1, 1) shouldBe null
        result.findAt(1, 8) shouldBe null

        // sound block on line 2
        result.findAt(2, 1)!!.blockId shouldBe soundId
        result.findAt(2, 8)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── Nested blocks (KBNestedChainArg) ──────────────────────────────────────

    "nested block: innermost block is returned, not the outer wrapper" {
        // sound(cat("bd", "hh"))
        //   sound block: chars 0–21  (outermost)
        //   cat   block: chars 6–20  (innermost — smaller range wins)
        //   cat slot 0 "bd": content chars 11–12
        //   cat slot 1 "hh": content chars 17–18
        val src = """sound(cat("bd", "hh"))"""
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id
        val catId = program.allBlocks().first { it.funcName == "cat" }.id

        result.code shouldBe src

        // col 1 = 's' — only inside sound, not cat
        result.findAt(1, 1)!!.blockId shouldBe soundId

        // col 7 = 'c' — inside both sound and cat; cat wins (smaller range)
        result.findAt(1, 7)!!.blockId shouldBe catId

        // col 12 = 'b' — inside cat, and inside cat's slot 0 content
        result.findAt(1, 12)!!.let { hit ->
            hit.blockId shouldBe catId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // col 18 = 'h' — inside cat, and inside cat's slot 1 content
        result.findAt(1, 18)!!.let { hit ->
            hit.blockId shouldBe catId
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── Multiline string ──────────────────────────────────────────────────────

    "multiline string: atoms on line 2 are correctly located" {
        // Build program directly so we control the exact newline in the value.
        //
        // KBStringArg("bd\nhh") → multiline → backtick-wrapped in generated code:
        //   sound(`bd          ← line 1: chars 0–9 (incl. '\n' at char 9)
        //   hh`)               ← line 2: chars 10–13
        //
        // Content range: 7..11  ("bd\nhh" = 5 chars, contentStart=7)
        //   line 2, col 1 = char 10 = 'h' → offsetInSlot = 10 - 7 = 3
        //   line 2, col 2 = char 11 = 'h' → offsetInSlot = 4
        //   line 2, col 3 = char 12 = '`' → outside content range

        val block = KBCallBlock(
            id = "test-sound",
            funcName = "sound",
            args = listOf(KBStringArg("bd\nhh")),
            isHead = true,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "test-stmt", steps = listOf(block)))
        )
        val result = program.toCodeGen()

        // Generated code uses backtick wrapping for multiline values
        result.code shouldBe "sound(`bd\nhh`)"

        // Line 1: 'b' at col 8 → first char of content
        result.findAt(1, 8)!!.let { hit ->
            hit.blockId shouldBe "test-sound"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 1: 'd' at col 9
        result.findAt(1, 9)!!.let { hit ->
            hit.blockId shouldBe "test-sound"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 1
        }

        // Line 2: first 'h' at col 1 → offsetInSlot 3 (after "bd\n")
        result.findAt(2, 1)!!.let { hit ->
            hit.blockId shouldBe "test-sound"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 3
        }

        // Line 2: second 'h' at col 2 → offsetInSlot 4
        result.findAt(2, 2)!!.let { hit ->
            hit.blockId shouldBe "test-sound"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 4
        }

        // Line 2: closing backtick at col 3 → outside content range
        result.findAt(2, 3)!!.let { hit ->
            hit.blockId shouldBe "test-sound"
            hit.slotIndex shouldBe null
            hit.offsetInSlot shouldBe null
        }
    }

    // ── Two blocks with identical function names ──────────────────────────────

    "two same-named blocks: positions discriminate by location, not by name" {
        // sound("bd").sound("hh")
        // The helper allBlocks() returns them in source order, so we destructure
        // positionally rather than filtering by name.
        //
        // sound1: chars 0–10  ("sound(\"bd\")", 11 chars)
        // '.'  at char 11
        // sound2: chars 12–22 ("sound(\"hh\")", 11 chars)
        val src = """sound("bd").sound("hh")"""
        val (program, result) = compile(src)
        val (sound1Block, sound2Block) = program.allBlocks()   // positional order

        result.code shouldBe src

        // Positions inside sound1
        result.findAt(1, 1)!!.blockId shouldBe sound1Block.id   // 's' of first sound
        result.findAt(1, 8)!!.let { hit ->                       // 'b' in "bd"
            hit.blockId shouldBe sound1Block.id
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Separator '.' at col 12 is between both blocks → not in either range
        result.findAt(1, 12) shouldBe null

        // Positions inside sound2
        result.findAt(1, 13)!!.blockId shouldBe sound2Block.id  // 's' of second sound
        result.findAt(1, 20)!!.let { hit ->                      // 'h' in "hh"
            hit.blockId shouldBe sound2Block.id
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── VERTICAL pocketLayout ─────────────────────────────────────────────────

    "vertical block: each arg is on its own line, all still resolve correctly" {
        // Build manually — VERTICAL layout puts each arg on its own line:
        //   note(
        //     "c3",
        //     "e3"
        //   )
        //
        // Char layout:
        //   n=0,o=1,t=2,e=3,(=4,\n=5,' '=6,' '=7 → line 1 ends at char 5
        //   line 2: "c3",\n  →  "=8,c=9,3=10,"=11,,=12,\n=13
        //   line 3: "e3"\n  →  ' '=14,' '=15,"=16,e=17,3=18,"=19,\n=20
        //   line 4: )      →  )=21
        //
        // Slot 0 "c3": content 9..10  (line 2, col 4 = 'c'  — 2-space indent + opening quote = 3 chars before content)
        // Slot 1 "e3": content 17..18 (line 3, col 4 = 'e'  — same structure)
        val block = KBCallBlock(
            id = "note-id",
            funcName = "note",
            args = listOf(KBStringArg("c3"), KBStringArg("e3")),
            isHead = true,
            pocketLayout = KBPocketLayout.VERTICAL,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(block)))
        )
        val result = program.toCodeGen()

        result.code shouldBe "note(\n  \"c3\",\n  \"e3\"\n)"

        // Line 1: 'n' at col 1 — function name, no slot
        result.findAt(1, 1)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe null
        }

        // Line 2: 'c' at col 4 — slot 0 content  (2-space indent + '"' = 3 prefix chars)
        result.findAt(2, 4)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 2: '3' at col 5 — slot 0 content, offset 1
        result.findAt(2, 5)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 1
        }

        // Line 3: 'e' at col 4 — slot 1 content  (same indent structure)
        result.findAt(3, 4)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── Vertical stack containing nested chained blocks ───────────────────────

    "vertical stack with nested chains: blocks on different lines all resolve correctly" {
        // Build a VERTICAL stack containing two nested chain args:
        //   stack(
        //     sound("bd").gain(0.5),
        //     note("c3")
        //   )
        //
        // Expected code:  "stack(\n  sound(\"bd\").gain(0.5),\n  note(\"c3\")\n)"
        //
        // Char layout:
        //   Line 1: "stack(\n"              → chars 0-6  (\n at 6)
        //   Line 2: "  sound(\"bd\").gain(0.5),\n"  → chars 7-31 (\n at 31)
        //     chars 7-8:   "  "
        //     chars 9-19:  "sound(\"bd\")"    → sound block 9..19
        //     char  20:    "."
        //     chars 21-29: "gain(0.5)"       → gain block 21..29
        //     chars 30-31: ",\n"
        //   Line 3: "  note(\"c3\")\n"       → chars 32-44 (\n at 44)
        //     chars 32-33: "  "
        //     chars 34-43: "note(\"c3\")"    → note block 34..43
        //   Line 4: ")"                     → char 45
        //   stack block spans entire range: 0..45
        //
        // Content ranges:
        //   sound slot 0 "bd": chars 16..17   → line 2, col 10-11
        //   note  slot 0 "c3": chars 40..41   → line 3, col  9-10

        val soundBlock = KBCallBlock(
            id = "s1", funcName = "sound",
            args = listOf(KBStringArg("bd")), isHead = true,
        )
        val gainBlock = KBCallBlock(
            id = "g1", funcName = "gain",
            args = listOf(KBNumberArg(0.5)), isHead = false,
        )
        val noteBlock = KBCallBlock(
            id = "n1", funcName = "note",
            args = listOf(KBStringArg("c3")), isHead = true,
        )
        val stackBlock = KBCallBlock(
            id = "st", funcName = "stack",
            args = listOf(
                KBNestedChainArg(KBChainStmt(id = "c1", steps = listOf(soundBlock, gainBlock))),
                KBNestedChainArg(KBChainStmt(id = "c2", steps = listOf(noteBlock))),
            ),
            isHead = true,
            pocketLayout = KBPocketLayout.VERTICAL,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(stackBlock)))
        )
        val result = program.toCodeGen()

        result.code shouldBe "stack(\n  sound(\"bd\").gain(0.5),\n  note(\"c3\")\n)"

        // Line 1: 's' of stack at col 1 — only stack matches (outermost)
        result.findAt(1, 1)!!.blockId shouldBe "st"

        // Line 2: 's' of sound at col 3 — sound wins over stack (smaller range)
        result.findAt(2, 3)!!.blockId shouldBe "s1"

        // Line 2: 'b' of "bd" at col 10 — sound block, slot 0, offset 0
        result.findAt(2, 10)!!.let { hit ->
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 2: 'g' of gain at col 15 — gain wins over stack
        result.findAt(2, 15)!!.blockId shouldBe "g1"

        // Line 3: 'n' of note at col 3 — note wins over stack
        result.findAt(3, 3)!!.blockId shouldBe "n1"

        // Line 3: 'c' of "c3" at col 9 — note block, slot 0, offset 0
        result.findAt(3, 9)!!.let { hit ->
            hit.blockId shouldBe "n1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 4: ')' at col 1 — inside stack range only
        result.findAt(4, 1)!!.blockId shouldBe "st"
    }

    "multiline string inside nested chain pushes subsequent block to a later line" {
        // This is the hardest case: sound has a multiline mini-notation string,
        // which means gain ends up on line 3 even though sound started on line 2.
        //
        //   stack(
        //     sound(`bd hh
        //   sd oh`).gain(0.5)
        //   )
        //
        // Expected code: "stack(\n  sound(`bd hh\nsd oh`).gain(0.5)\n)"
        //
        // Char layout:
        //   Line 1: "stack(\n"                     → chars 0-6  (\n at 6)
        //   Line 2: "  sound(`bd hh\n"             → chars 7-21 (\n at 21)
        //     chars  7-8:  "  "
        //     chars  9-13: "sound"
        //     char  14:    "("
        //     char  15:    "`"                       ← backtick, NOT content
        //     chars 16-20: "bd hh"                  ← content starts at 16
        //     char  21:    "\n"                      ← part of content
        //   Line 3: "sd oh`).gain(0.5)\n"          → chars 22-39 (\n at 39)
        //     chars 22-26: "sd oh"                  ← still part of content
        //     char  27:    "`"                       ← closing backtick, not content
        //     char  28:    ")"
        //     char  29:    "."
        //     chars 30-33: "gain"                   ← gain block starts here
        //     char  34:    "("
        //     chars 35-37: "0.5"
        //     char  38:    ")"
        //     char  39:    "\n"
        //   Line 4: ")"                             → char 40
        //
        // Block ranges:
        //   stack: 0..40
        //   sound: 9..28    (sound block: sound(`bd hh\nsd oh`))
        //   gain:  30..38   (gain block: gain(0.5))
        //
        // Content range for sound slot 0: 16..26 ("bd hh\nsd oh", 11 chars)
        //   char 22 = 's' → offsetInSlot = 22-16 = 6  ("bd hh\n" = 6 chars before 's')

        val soundBlock = KBCallBlock(
            id = "s1", funcName = "sound",
            args = listOf(KBStringArg("bd hh\nsd oh")), isHead = true,
        )
        val gainBlock = KBCallBlock(
            id = "g1", funcName = "gain",
            args = listOf(KBNumberArg(0.5)), isHead = false,
        )
        val stackBlock = KBCallBlock(
            id = "st", funcName = "stack",
            args = listOf(
                KBNestedChainArg(KBChainStmt(id = "c1", steps = listOf(soundBlock, gainBlock))),
            ),
            isHead = true,
            pocketLayout = KBPocketLayout.VERTICAL,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(stackBlock)))
        )
        val result = program.toCodeGen()

        result.code shouldBe "stack(\n  sound(`bd hh\nsd oh`).gain(0.5)\n)"

        // Line 2: 's' of sound at col 3 — sound wins over stack
        result.findAt(2, 3)!!.blockId shouldBe "s1"

        // Line 2: 'b' of "bd hh\nsd oh" at col 10 — sound slot 0, offset 0
        result.findAt(2, 10)!!.let { hit ->
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 3: 's' of "sd oh" at col 1 — still inside sound's content range
        result.findAt(3, 1)!!.let { hit ->
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 6  // "bd hh\n" = 6 chars before 's'
        }

        // Line 3: 'g' of gain at col 9 — gain block, which starts after the multiline string
        // This is the critical assertion: gain is on line 3, pushed there by the multiline string.
        result.findAt(3, 9)!!.blockId shouldBe "g1"

        // Line 3: gain is correctly the innermost block (smaller than stack)
        result.findAt(3, 11)!!.let { hit ->
            hit.blockId shouldBe "g1"   // NOT "st"
            hit.slotIndex shouldBe null  // numeric arg, not a string slot
        }
    }

    // ── KBEmptyArg preserves original slot index ─────────────────────────────

    "empty first slot: original slot index is preserved in the content range" {
        // note(KBEmptyArg("pitch"), KBStringArg("c3"))
        // Empty args are filtered out before code gen → generated code: note("c3")
        // BUT the content range must use the ORIGINAL slot index 1, not the
        // filtered index 0.  The UI uses slotIndex to decide which slot to
        // highlight — a wrong index would light up the empty slot instead.
        //
        // Char layout:  note("c3")
        //               n=0,o=1,t=2,e=3,(=4,"=5,c=6,3=7,"=8,)=9
        // Block range:  0..9
        // Content range for SLOT 1 ("c3"):  6..7

        val block = KBCallBlock(
            id = "note-id",
            funcName = "note",
            args = listOf(KBEmptyArg("pitch"), KBStringArg("c3")),
            isHead = true,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(block)))
        )
        val result = program.toCodeGen()

        result.code shouldBe """note("c3")"""

        // 'n' — block hit, no slot
        result.findAt(1, 1)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe null
        }

        // 'c' — slot index 1 (NOT 0 — original slot is preserved)
        result.findAt(1, 7)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 0
        }

        // '3' — slot index 1, offset 1
        result.findAt(1, 8)!!.let { hit ->
            hit.blockId shouldBe "note-id"
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 1
        }
    }

    // ── String-headed chain ───────────────────────────────────────────────────

    "string-headed chain: head text is tracked with the chain id; block offsets unchanged" {
        // "C4".transpose(1).slow(2)
        //
        // KBStringLiteralItem("C4") is now wrapped in trackBlock(chainId) and
        // trackSlotContent(chainId, 0, 1..2).  The chain itself is the top-level
        // KBChainStmt; positions on the head return chainId.
        //
        // Char layout:
        //   "=0, C=1, 4=2, "=3                   ← chain id, slot 0 content = chars 1..2
        //   .=4                                   ← separator, null
        //   t=5..e=13, (=14, 1=15, )=16          ← transpose block 5..16
        //   .=17
        //   s=18..w=21, (=22, 2=23, )=24         ← slow block 18..24

        val (program, result) = compile(""""C4".transpose(1).slow(2)""")
        val chainId = program.statements.filterIsInstance<KBChainStmt>().first().id
        val transposeId = program.allBlocks().first { it.funcName == "transpose" }.id
        val slowId = program.allBlocks().first { it.funcName == "slow" }.id

        result.code shouldBe """"C4".transpose(1).slow(2)"""

        // Opening '"' — inside chainId block range, not in slot content
        result.findAt(1, 1)!!.let { hit ->
            hit.blockId shouldBe chainId
            hit.slotIndex shouldBe null
        }

        // 'C' — inside slot content, offsetInSlot=0
        result.findAt(1, 2)!!.let { hit ->
            hit.blockId shouldBe chainId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // '4' — inside slot content, offsetInSlot=1
        result.findAt(1, 3)!!.let { hit ->
            hit.blockId shouldBe chainId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 1
        }

        // Closing '"' — inside chainId block range, not in slot content
        result.findAt(1, 4)!!.let { hit ->
            hit.blockId shouldBe chainId
            hit.slotIndex shouldBe null
        }

        // '.' separator — not inside any block range
        result.findAt(1, 5) shouldBe null

        // First char of transpose block
        result.findAt(1, 6)!!.blockId shouldBe transposeId   // 't'

        // Numeric arg inside transpose — block hit, no string content
        result.findAt(1, 16)!!.let { hit ->
            hit.blockId shouldBe transposeId
            hit.slotIndex shouldBe null
        }

        // First char of slow block
        result.findAt(1, 19)!!.blockId shouldBe slowId   // 's'
    }

    // ── Three levels of nesting ───────────────────────────────────────────────

    "three levels of nesting: innermost block wins at every position" {
        // sound(cat(seq("bd", "hh"), "oh"))
        //
        // Nesting: sound > cat > seq
        //   sound block: 0..32  (largest)
        //   cat   block: 6..31
        //   seq   block: 10..24 (smallest — wins wherever all three overlap)
        //
        // Content ranges:
        //   seq  slot 0 "bd": chars 15..16   → col 16 = 'b'
        //   seq  slot 1 "hh": chars 21..22   → col 22 = 'h'
        //   cat  slot 1 "oh": chars 28..29   → col 29 = 'o'  (seq ends at 24)

        val (program, result) = compile("""sound(cat(seq("bd", "hh"), "oh"))""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id
        val catId = program.allBlocks().first { it.funcName == "cat" }.id
        val seqId = program.allBlocks().first { it.funcName == "seq" }.id

        result.code shouldBe """sound(cat(seq("bd", "hh"), "oh"))"""

        // 's' of sound — only sound contains char 0
        result.findAt(1, 1)!!.blockId shouldBe soundId

        // 'c' of cat — inside sound AND cat; cat wins (smaller range)
        result.findAt(1, 7)!!.blockId shouldBe catId

        // 's' of seq — inside sound, cat AND seq; seq wins (smallest)
        result.findAt(1, 11)!!.blockId shouldBe seqId

        // 'b' in "bd" — deepest content range: seq slot 0
        result.findAt(1, 16)!!.let { hit ->
            hit.blockId shouldBe seqId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Second 'h' in "hh" — seq slot 1, offset 1
        result.findAt(1, 23)!!.let { hit ->
            hit.blockId shouldBe seqId
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 1
        }

        // 'o' of "oh" — seq has ended; cat wins over sound; cat slot 1
        result.findAt(1, 29)!!.let { hit ->
            hit.blockId shouldBe catId
            hit.slotIndex shouldBe 1
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── Off-by-one regression: last character of a block ─────────────────────

    "last character of block resolves correctly (off-by-one regression)" {
        // sound("bd") — 11 chars (cols 1–11)
        //   block range: 0..10  (inclusive on both ends)
        //   ')' at col 11 = char 10 = LAST character of the block
        //
        // The fix changed `offset >= end` to `offset <= lastInclusive`, where
        // lastInclusive = IntRange.last.  Without the fix the last char was missed.

        val (program, result) = compile("""sound("bd")""")
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        // Second-to-last char ('"' at col 10) — inside block
        result.findAt(1, 10)!!.blockId shouldBe soundId

        // Last char (')' at col 11) — must be inside block, not past it
        result.findAt(1, 11)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe null
        }

        // One past the end (col 12) — outside block
        result.findAt(1, 12) shouldBe null
    }

    // ── Blank line shifts subsequent block to line 3 ─────────────────────────

    "blank line: subsequent block is on line 3, not line 2" {
        // [KBChainStmt(sound), KBBlankLine, KBChainStmt(note)]
        //
        // Generated code: sound("bd")\n\nnote("c3")
        //   Line 1: sound("bd")                     chars 0–10
        //   '\n' (stmt separator before KBBlankLine) char 11
        //   KBBlankLine.appendTo = nothing
        //   '\n' (stmt separator before note)        char 12
        //   Line 3: note("c3")                       chars 13–22
        //   note's "c3" content starts at char 19    → line 3, col 7

        val soundBlock = KBCallBlock(
            id = "s1", funcName = "sound", args = listOf(KBStringArg("bd")), isHead = true,
        )
        val noteBlock = KBCallBlock(
            id = "n1", funcName = "note", args = listOf(KBStringArg("c3")), isHead = true,
        )
        val program = KBProgram(
            statements = listOf(
                KBChainStmt(id = "c1", steps = listOf(soundBlock)),
                KBBlankLine(id = "bl"),
                KBChainStmt(id = "c2", steps = listOf(noteBlock)),
            )
        )
        val result = program.toCodeGen()

        result.code shouldBe "sound(\"bd\")\n\nnote(\"c3\")"

        result.findAt(1, 1)!!.blockId shouldBe "s1"
        result.findAt(2, 1) shouldBe null          // blank line — no block
        result.findAt(3, 1)!!.blockId shouldBe "n1"
        result.findAt(3, 7)!!.let { hit ->         // 'c' in "c3" at line 3, col 7
            hit.blockId shouldBe "n1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
    }

    // ── VERTICAL pocketLayout: affects args only, NOT chain separators ────────

    "vertical block: VERTICAL layout formats args on separate lines but keeps inline chain dot" {
        // sound("bd") is HORIZONTAL; gain(0.5) is VERTICAL.
        // VERTICAL only affects arg rendering inside gain's () — it does NOT force a newline
        // before the chain dot.  The separator is "." (inline) because there is no KBNewlineHint.
        //
        // Generated code: "sound(\"bd\").gain(\n  0.5\n)"
        //
        // Char layout:
        //   Line 1: sound("bd").gain(\n   → sound block 0..10, '.' at 11, gain block 12..24
        //     sound("bd") = chars 0-10
        //     '.'          = char 11  (not inside any block)
        //     gain(         = chars 12-16
        //     '\n'          = char 17
        //   Line 2:   0.5\n              → '  ' at 18-19, '0.5' at 20-22, '\n' at 23
        //   Line 3: )                    → char 24  (closing paren of gain block)
        //
        // gain block range: 12..24
        // "0.5" is a numeric arg — inside gain block but no string content range.

        val soundBlock = KBCallBlock(
            id = "s1", funcName = "sound",
            args = listOf(KBStringArg("bd")), isHead = true,
            pocketLayout = KBPocketLayout.HORIZONTAL,
        )
        val gainBlock = KBCallBlock(
            id = "g1", funcName = "gain",
            args = listOf(KBNumberArg(0.5)), isHead = false,
            pocketLayout = KBPocketLayout.VERTICAL,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(soundBlock, gainBlock)))
        )
        val result = program.toCodeGen()

        result.code shouldBe "sound(\"bd\").gain(\n  0.5\n)"

        // Line 1: sound block
        result.findAt(1, 1)!!.blockId shouldBe "s1"    // 's'
        result.findAt(1, 8)!!.let { hit ->               // 'b' in "bd"
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 1: '.' at col 12 is between blocks — not inside any range
        result.findAt(1, 12) shouldBe null

        // Line 1: gain block starts at col 13 ('g')
        result.findAt(1, 13)!!.blockId shouldBe "g1"

        // Line 2: '0' of 0.5 at col 3 — inside gain block, no string content
        result.findAt(2, 3)!!.let { hit ->
            hit.blockId shouldBe "g1"
            hit.slotIndex shouldBe null
        }

        // Line 3: ')' — last char of gain block
        result.findAt(3, 1)!!.blockId shouldBe "g1"
    }

    // ── Top-level chain: block pushed to line 2 by a multiline string ─────────

    "top-level chain: gain is on line 2 when sound has a multiline string arg" {
        // sound(`bd hh\nsd oh`).gain(0.5)  — top-level chain, no outer wrapper
        //
        // The multiline string in sound causes the chain to span two lines.
        // gain starts immediately after the closing backtick+paren on line 2.
        //
        // Char layout:
        //   Line 1: sound(`bd hh\n      → chars 0–9  ('\n' at 9 is part of content)
        //     sound( = 0-5,  ` = 6,  content starts at 7: b=7,d=8,space=?,h=?,h=?,\n=9
        //     Wait — "bd hh" = 5 chars: b=7,d=8,' '=9... actually:
        //     "bd hh\nsd oh" → b=7,d=8,' '=9,h=10,h=11,'\n'=12... no wait
        //
        // Let me be precise: value = "bd hh\nsd oh" (11 chars)
        //   b=7, d=8, ' '=9, h=10, h=11, '\n'=12, s=13, d=14, ' '=15, o=16, h=17
        //   content range: 7..17
        //   closing ` = 18,  ) = 19
        //   sound block: 0..19
        //   . = 20
        //   gain block: 21..29  (gain(0.5) = 9 chars)
        //
        // Line 1: chars 0–12  (up to and including '\n' at 12)
        // Line 2: chars 13–29
        //   col 1 = char 13 = 's' of "sd oh"  → sound block, content offset 6
        //   col 9 = char 21 = 'g' of gain      → gain block

        val soundBlock = KBCallBlock(
            id = "s1", funcName = "sound",
            args = listOf(KBStringArg("bd hh\nsd oh")), isHead = true,
        )
        val gainBlock = KBCallBlock(
            id = "g1", funcName = "gain",
            args = listOf(KBNumberArg(0.5)), isHead = false,
        )
        val program = KBProgram(
            statements = listOf(KBChainStmt(id = "stmt", steps = listOf(soundBlock, gainBlock)))
        )
        val result = program.toCodeGen()

        result.code shouldBe "sound(`bd hh\nsd oh`).gain(0.5)"

        // Line 1: 'b' is first content char (sound slot 0, offset 0)
        result.findAt(1, 8)!!.let { hit ->
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }

        // Line 2: 's' of "sd oh" — still inside sound's content range
        // offsetInSlot = 6  ("bd hh\n" = 6 chars before 's')
        result.findAt(2, 1)!!.let { hit ->
            hit.blockId shouldBe "s1"
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 6
        }

        // Line 2: '.' between blocks is outside both block ranges → null
        result.findAt(2, 8) shouldBe null

        // Line 2: gain block starts at col 9 ('g')
        result.findAt(2, 9)!!.blockId shouldBe "g1"

        // Line 2: '0' of 0.5 inside gain, no string content
        result.findAt(2, 14)!!.let { hit ->
            hit.blockId shouldBe "g1"
            hit.slotIndex shouldBe null
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    "position completely outside all blocks returns null" {
        // sound("bd")  is 11 chars (cols 1–11); col 12 is past the end
        val (_, result) = compile("""sound("bd")""")
        result.findAt(1, 12) shouldBe null
        result.findAt(2, 1) shouldBe null   // no line 2 exists
    }

    "hit cache: repeated findAt calls for the same position return the same result" {
        val src = """sound("bd hh sd oh")"""
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id

        // Call twice — second call goes through the cache
        val hit1 = result.findAt(1, 8)
        val hit2 = result.findAt(1, 8)

        hit1 shouldNotBe null
        hit2 shouldNotBe null
        hit1!!.blockId shouldBe soundId
        hit2!!.blockId shouldBe soundId
        hit1.slotIndex shouldBe 0
        hit2.slotIndex shouldBe 0
        hit1.offsetInSlot shouldBe 0
        hit2.offsetInSlot shouldBe 0
    }

    // ── Identifier-headed chains (e.g. sine.range(...)) ───────────────────────

    "bare identifier arg: sound('bd hh').pan(sine) round-trips correctly" {
        // sine is a plain KBIdentifierArg — should already work
        val src = """sound("bd hh").pan(sine)"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "identifier-headed chain as arg: round-trips without extra quotes" {
        // Bug: sine.range(0.25, 0.75) was falling back to KBStringArg, wrapping in quotes.
        val src = """sound("bd hh").pan(sine.range(0.25, 0.75))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "identifier-headed chain as arg: findAt resolves sound vs pan vs range" {
        // sound("bd hh").pan(sine.range(0.25, 0.75))
        //
        // sound("bd hh") = 14 chars (0-13), '.' at 14
        // pan(sine.range(0.25, 0.75)) starts at 15:
        //   p=15 a=16 n=17 (=18
        //   s=19 i=20 n=21 e=22 .=23   ← "sine." prefix, NOT tracked in any block
        //   range block 24..40:
        //     r=24 a=25 n=26 g=27 e=28 (=29
        //     0=30 .=31 2=32 5=33 ,=34 ' '=35 0=36 .=37 7=38 5=39 )=40
        //   )=41  ← closing ) of pan
        // pan block: 15..41

        val src = """sound("bd hh").pan(sine.range(0.25, 0.75))"""
        val (program, result) = compile(src)
        val soundId = program.allBlocks().first { it.funcName == "sound" }.id
        val panId = program.allBlocks().first { it.funcName == "pan" }.id
        val rangeId = program.allBlocks().first { it.funcName == "range" }.id

        result.code shouldBe src

        // 's' of sound (col 1 = char 0)
        result.findAt(1, 1)!!.blockId shouldBe soundId

        // '.' between sound and pan (col 15 = char 14) — not in any block range
        result.findAt(1, 15) shouldBe null

        // 'p' of pan (col 16 = char 15)
        result.findAt(1, 16)!!.blockId shouldBe panId

        // 's' of "sine." (col 20 = char 19) — inside pan but before range starts; pan wins
        result.findAt(1, 20)!!.blockId shouldBe panId

        // 'r' of "range" (col 25 = char 24) — inside both pan and range; range wins (smaller)
        result.findAt(1, 25)!!.blockId shouldBe rangeId

        // '0' of first arg 0.25 (col 31 = char 30) — inside range, no string slot
        result.findAt(1, 31)!!.let { hit ->
            hit.blockId shouldBe rangeId
            hit.slotIndex shouldBe null
        }
    }

    "deeper identifier chain: sine.range(0.1, 0.9).slow(2) as arg round-trips correctly" {
        val src = """sound("bd hh").pan(sine.range(0.1, 0.9).slow(2))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "identifier-headed chain at top level round-trips correctly" {
        // sine.range(0.25, 0.75) as a top-level statement
        val src = """sine.range(0.25, 0.75)"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    // ── Arrow function args ────────────────────────────────────────────────────

    "arrow function single param: no extra parentheses in round-trip" {
        // Bug: single-param arrows were emitted as "(x) => ..." instead of "x => ...".
        val src = """sound("bd hh").superimpose(x => x.pan(0.1))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "arrow function multi-param: parentheses are preserved" {
        val src = """sound("bd hh").superimpose((x, y) => x.pan(y))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "arrow function zero params: parentheses are preserved" {
        val src = """sound("bd hh").on("bd", () => note("c3"))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "arrow function with method-chain body: round-trips correctly" {
        val src = """sound("bd hh").superimpose(x => x.gain(0.5).pan(0.1))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    // ── Combined: identifier chain + arrow function ───────────────────────────

    "combined: identifier-headed chain and arrow function in same statement" {
        val src = """sound("bd hh").pan(sine.range(0.25, 0.75)).superimpose(x => x.gain(0.5))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    "nested identifier chain inside stack arg: round-trips correctly" {
        // stack(sound("bd").pan(sine.range(0.1, 0.9)), note("c3"))
        val src = """stack(sound("bd").pan(sine.range(0.1, 0.9)), note("c3"))"""
        val (_, result) = compile(src)
        result.code shouldBe src
    }

    // ── KBNewlineHint: multi-line chain ────────────────────────────────────────

    "newline hint: same-line chain has no KBNewlineHint in steps" {
        val (program, result) = compile("""sound("bd").gain(0.5).slow(2)""")
        val steps = (program.statements.first() as KBChainStmt).steps
        steps.none { it is KBNewlineHint } shouldBe true
        result.code shouldBe """sound("bd").gain(0.5).slow(2)"""
    }

    "newline hint: multi-line chain inserts KBNewlineHint between each cross-line call" {
        // Canonical format — two spaces + dot so generated code matches source exactly.
        val src = "sound(\"bd\")\n  .gain(0.5)\n  .slow(2)"
        val (program, result) = compile(src)

        val steps = (program.statements.first() as KBChainStmt).steps
        // Expected step sequence: CallBlock(sound), KBNewlineHint, CallBlock(gain), KBNewlineHint, CallBlock(slow)
        steps.size shouldBe 5
        steps[0].let { it is KBCallBlock && it.funcName == "sound" } shouldBe true
        steps[1] shouldBe KBNewlineHint
        steps[2].let { it is KBCallBlock && it.funcName == "gain" } shouldBe true
        steps[3] shouldBe KBNewlineHint
        steps[4].let { it is KBCallBlock && it.funcName == "slow" } shouldBe true

        // Generated code must reproduce the same newlines.
        result.code shouldBe src
    }

    "newline hint: round-trip for multi-line chain" {
        roundTrip("sound(\"bd\")\n  .gain(0.5)\n  .slow(2)").shouldRoundTripWithCode()
    }

    "newline hint: only first dot is on a new line — second is inline" {
        val src = "sound(\"bd\")\n  .gain(0.5).slow(2)"
        val (program, result) = compile(src)

        val steps = (program.statements.first() as KBChainStmt).steps
        // Expected: CallBlock(sound), KBNewlineHint, CallBlock(gain), CallBlock(slow)
        steps.size shouldBe 4
        steps[1] shouldBe KBNewlineHint
        steps[3].let { it is KBCallBlock && it.funcName == "slow" } shouldBe true

        result.code shouldBe src
    }

    // ── let / const — source map ──────────────────────────────────────────────

    "let with string value: stmtId is in blockRanges; content hit returns stmtId + slot 0" {
        // let x = "c3"
        // l=1 e=2 t=3 ' '=4 x=5 ' '=6 ==7 ' '=8 "=9 c=10 3=11 "=12
        val src = """let x = "c3""""
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBLetStmt).id

        result.code shouldBe src

        // Opening quote (col 9) — blockId present but no slot info
        result.findAt(1, 9)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe null
        }
        // Content 'c' (col 10) — slot 0, offset 0
        result.findAt(1, 10)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
        // Content '3' (col 11) — slot 0, offset 1
        result.findAt(1, 11)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 1
        }
        // Closing quote (col 12) — blockId present but no slot info
        result.findAt(1, 12)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe null
        }
    }

    "let with number value: stmtId is in blockRanges; hit returns stmtId with no slot info" {
        // let bpm = 120
        // col  1= 'l', col 11= '1'(start of 120), col 13= '0'(last of 120)
        val src = "let bpm = 120"
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBLetStmt).id

        result.code shouldBe src

        // 'l' of 'let' is outside the tracked range (only the value is tracked)
        result.findAt(1, 1) shouldBe null

        // '1' — first digit of 120
        result.findAt(1, 11)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe null
            hit.offsetInSlot shouldBe null
        }
        // '0' — last digit of 120
        result.findAt(1, 13)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe null
        }
    }

    "let with nested chain value: inner block tracked by its own id, stmtId NOT in blockRanges" {
        // let p = sound("bd")
        // col  1= 'l', col 9= 's'(start of sound), col 16= 'b'(content of "bd"), col 17= 'd'
        val src = """let p = sound("bd")"""
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBLetStmt).id
        val soundStmt = program.statements.single() as KBLetStmt
        val soundId = ((soundStmt.value as KBNestedChainArg).chain.steps
            .filterIsInstance<KBCallBlock>().first { it.funcName == "sound" }).id

        result.code shouldBe src

        // stmtId must NOT appear in blockRanges (inner blocks track themselves)
        result.blockRanges.containsKey(stmtId) shouldBe false

        // 's' of 'sound' → soundId, no slot
        result.findAt(1, 9)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe null
        }
        // 'b' in "bd" → soundId, slot 0, offset 0
        result.findAt(1, 16)!!.let { hit ->
            hit.blockId shouldBe soundId
            hit.slotIndex shouldBe 0
            hit.offsetInSlot shouldBe 0
        }
    }

    "const with number value: stmtId is in blockRanges; hit returns stmtId with no slot info" {
        // const bpm = 120
        // col  1= 'c', col 13= '1'(start of 120), col 15= '0'(last of 120)
        val src = "const bpm = 120"
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBConstStmt).id

        result.code shouldBe src

        // 'c' of 'const' is outside the tracked range
        result.findAt(1, 1) shouldBe null

        // '1' of 120
        result.findAt(1, 13)!!.let { hit ->
            hit.blockId shouldBe stmtId
            hit.slotIndex shouldBe null
            hit.offsetInSlot shouldBe null
        }
    }

    "const with nested chain value: inner block tracked by its own id, stmtId NOT in blockRanges" {
        // const kick = sound("bd").gain(0.5)
        val src = """const kick = sound("bd").gain(0.5)"""
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBConstStmt).id
        val constStmt = program.statements.single() as KBConstStmt
        val blocks = (constStmt.value as KBNestedChainArg).chain.steps.filterIsInstance<KBCallBlock>()
        val soundId = blocks.first { it.funcName == "sound" }.id
        val gainId = blocks.first { it.funcName == "gain" }.id

        result.code shouldBe src

        // stmtId must NOT appear in blockRanges
        result.blockRanges.containsKey(stmtId) shouldBe false

        // 's' of 'sound' → soundId
        // "const kick = " is 13 chars → 's' is at col 14
        result.findAt(1, 14)!!.blockId shouldBe soundId

        // 'g' of 'gain' → gainId
        // "const kick = sound(\"bd\")." is 25 chars → 'g' is at col 26
        result.findAt(1, 26)!!.blockId shouldBe gainId
    }

    "let without value: stmtId absent from blockRanges" {
        val src = "let x"
        val (program, result) = compile(src)
        val stmtId = (program.statements.single() as KBLetStmt).id

        result.code shouldBe src
        result.blockRanges.containsKey(stmtId) shouldBe false
        result.findAt(1, 1) shouldBe null
    }
})
