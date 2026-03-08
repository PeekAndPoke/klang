package io.peekandpoke.klang.strudel.lang.editor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.BracketType
import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.InsertTarget
import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.LayoutItem
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern

/**
 * Tests for [NoteStaffLayout] — the platform-independent layout engine.
 *
 * These tests cover:
 * 1. [NoteStaffLayout.buildLayoutItems] — correct LayoutItem list for a given pattern.
 * 2. [NoteStaffLayout.buildInsertTargets] — correct InsertTargets (which insertBetween/At calls fire).
 * 3. Integration: given a pattern → build layout → get targets → execute insertBetween → verify text.
 *
 * Architecture note:
 * In mini-notation, `[a,b]` is a GROUP containing a STACK (MnNode.Group wraps MnNode.Stack).
 * The GROUP produces bracket marks; the STACK produces a LayoutItem.Stack (no extra marks).
 * So `[a,b] c` → [BracketMark(open), Stack([a,b]), BracketMark(close), Note(c)] — 4 items.
 */
class NoteStaffLayoutSpec : StringSpec() {

    private fun parse(pattern: String) = parseMiniNotationMnPattern(pattern)!!
    private fun editor(text: String) = MnPatternTextEditor(text) { "n$it" }

    private fun layout(pattern: String) = NoteStaffLayout.buildLayoutItems(parse(pattern))
    private fun targets(pattern: String) = NoteStaffLayout.buildInsertTargets(layout(pattern))
    private fun pushTargets(pattern: String) = targets(pattern).filter { it.isPush }.map { it.target }

    init {

        // ── buildLayoutItems ──────────────────────────────────────────────────

        "layout: flat sequence a b c → three Notes, no brackets" {
            val items = layout("a b c")
            items.filterIsInstance<LayoutItem.Note>() shouldHaveSize 3
            items.filterIsInstance<LayoutItem.BracketMark>() shouldHaveSize 0
        }

        "layout: group [a b] c → bracket marks wrap the two notes" {
            val items = layout("[a b] c")
            items shouldHaveSize 5
            items[0] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = true)
            (items[1] as LayoutItem.Note).let { (it.node as MnNode.Atom).value shouldBe "a" }
            (items[2] as LayoutItem.Note).let { (it.node as MnNode.Atom).value shouldBe "b" }
            items[3] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = false)
            (items[4] as LayoutItem.Note).let { (it.node as MnNode.Atom).value shouldBe "c" }
        }

        "layout: alternation <a b> → alternation bracket marks wrap two notes" {
            val items = layout("<a b>")
            items shouldHaveSize 4
            items[0] shouldBe LayoutItem.BracketMark(BracketType.Alternation, isOpen = true)
            items[3] shouldBe LayoutItem.BracketMark(BracketType.Alternation, isOpen = false)
        }

        "layout: stack [a,b] → Group wraps Stack — bracket marks plus a single Stack item" {
            // [a,b] is parsed as MnNode.Group containing MnNode.Stack(layers=[[a],[b]])
            // The Group produces open/close bracket marks; Stack produces LayoutItem.Stack
            val items = layout("[a,b]")
            items shouldHaveSize 3
            items[0] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = true)
            (items[1] as LayoutItem.Stack).items.map { (it as MnNode.Atom).value } shouldBe listOf("a", "b")
            items[2] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = false)
        }

        "layout: [a,b] c → open-bracket, Stack, close-bracket, Note" {
            val items = layout("[a,b] c")
            items shouldHaveSize 4
            items[0] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = true)
            (items[1] as LayoutItem.Stack).items.map { (it as MnNode.Atom).value } shouldBe listOf("a", "b")
            items[2] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = false)
            (items[3] as LayoutItem.Note).let { (it.node as MnNode.Atom).value shouldBe "c" }
        }

        "layout: <[a,b] [c,d]> → alt-open, grp-open, Stack, grp-close, grp-open, Stack, grp-close, alt-close" {
            val items = layout("<[a,b] [c,d]>")
            items shouldHaveSize 8
            items[0] shouldBe LayoutItem.BracketMark(BracketType.Alternation, isOpen = true)
            items[1] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = true)
            (items[2] as LayoutItem.Stack).items.map { (it as MnNode.Atom).value } shouldBe listOf("a", "b")
            items[3] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = false)
            items[4] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = true)
            (items[5] as LayoutItem.Stack).items.map { (it as MnNode.Atom).value } shouldBe listOf("c", "d")
            items[6] shouldBe LayoutItem.BracketMark(BracketType.Group, isOpen = false)
            items[7] shouldBe LayoutItem.BracketMark(BracketType.Alternation, isOpen = false)
        }

        // ── buildInsertTargets — key push slots ───────────────────────────────
        // Rather than testing exact indices, we verify the PRESENCE of the critical targets.

        "targets: [a,b] c — right-push of Stack uses c as rightNode (so Phase 2 exits ])" {
            // The Stack is at layout idx=1; right-push = Between(b, firstNodeAfter[2]=c, 0).
            // Between(b, c, 0) → insertBetween skips ] and space → lands BETWEEN stack and c.
            val e = editor("[a,b] c")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            val hasBetweenBC = pushTargets("[a,b] c").any { it == InsertTarget.Between(b, c, 0) }
            hasBetweenBC shouldBe true
        }

        "targets: <[a,b] [c,d]> — right-push of Stack[a,b] uses c as rightNode" {
            val e = editor("<[a,b] [c,d]>")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            val hasBetweenBC = pushTargets("<[a,b] [c,d]>").any { it == InsertTarget.Between(b, c, 0) }
            hasBetweenBC shouldBe true
        }

        "targets: <a [c d] [e f]> — right-push of Note(d) targets e as rightNode" {
            val e = editor("<a [c d] [e f]>")
            val d = e.atomByValue("d")!!
            val ef = e.atomByValue("e")!!
            val hasBetweenDE = pushTargets("<a [c d] [e f]>").any { it == InsertTarget.Between(d, ef, 0) }
            hasBetweenDE shouldBe true
        }

        "targets: [a,b] c — opening brackets produce skip>0 push slots with null leftNode" {
            val e = editor("[a,b] c")
            val a = e.atomByValue("a")!!
            // Some push slots before the first real node use skip>0 (inside the brackets)
            val insidePush = pushTargets("[a,b] c")
                .filterIsInstance<InsertTarget.Between>()
                .filter { it.leftNode == null && it.skipOpeningBrackets > 0 }
            insidePush.isNotEmpty() shouldBe true
        }

        // ── Integration: layout → targets → insertBetween ────────────────────

        "integration: [c4,e4] d4 — right-push of Stack → note lands between stack and d4" {
            val e = editor("[c4,e4] d4")
            val e4 = e.atomByValue("e4")!!
            val d4 = e.atomByValue("d4")!!
            e.insertBetween(e4, d4, 0).text shouldBe "[c4,e4] n0 d4"
        }

        "integration: [c4,e4] d4 — left-push before the [ with skip=0 → note before stack" {
            val e = editor("[c4,e4] d4")
            val c4 = e.atomByValue("c4")!!
            e.insertBetween(null, c4, 0).text shouldBe "n0 [c4,e4] d4"
        }

        "integration: [c4,e4] d4 — push inside [ with skip=1 → note inside the group" {
            val e = editor("[c4,e4] d4")
            val c4 = e.atomByValue("c4")!!
            e.insertBetween(null, c4, 0, skipOpeningBrackets = 1).text shouldBe "[n0 c4,e4] d4"
        }

        "integration: <[a,b] [c,d]> — right-push of Stack[a,b] → note between the two stacks" {
            val e = editor("<[a,b] [c,d]>")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            e.insertBetween(b, c, 0).text shouldBe "<[a,b] n0 [c,d]>"
        }

        "integration: <[a,b] [c,d]> — left-push at < with skip=1 → note inside alternation" {
            val e = editor("<[a,b] [c,d]>")
            val a = e.atomByValue("a")!!
            e.insertBetween(null, a, 0, skipOpeningBrackets = 1).text shouldBe "<n0 [a,b] [c,d]>"
        }

        "integration: <a [c d] [e f]> — right-push of Note(d) → note between groups" {
            val e = editor("<a [c d] [e f]>")
            val d = e.atomByValue("d")!!
            val ef = e.atomByValue("e")!!
            e.insertBetween(d, ef, 0).text shouldBe "<a [c d] n0 [e f]>"
        }
    }
}
