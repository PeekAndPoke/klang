package io.peekandpoke.klang.strudel.lang.editor

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.lang.parser.MnNode

/**
 * Tests for [MnPatternTextEditor] covering removeNode, updateNode,
 * insertBetween, and insertAt across flat, grouped, alternation, and stack patterns.
 *
 * Convention: posToValue maps int → "n<int>" so position 0 → "n0", 3 → "n3", etc.
 */
class MnPatternTextEditorSpec : StringSpec() {

    /** Build an editor from a mini-notation string. posToValue: pos → "n<pos>". */
    private fun editor(text: String) = MnPatternTextEditor(text) { "n$it" }

    init {

        // ── removeNode ────────────────────────────────────────────────────────

        "removeNode: remove only atom leaves empty text" {
            val e = editor("c4").removeNode(editor("c4").atomAt(0)!!)
            e.text shouldBe ""
        }

        "removeNode: remove first atom from sequence" {
            val e = editor("c4 d4 e4")
            val result = e.removeNode(e.atomAt(0)!!)
            result.text shouldBe "d4 e4"
        }

        "removeNode: remove middle atom from sequence" {
            val e = editor("c4 d4 e4")
            val result = e.removeNode(e.atomAt(1)!!)
            result.text shouldBe "c4 e4"
        }

        "removeNode: remove last atom from sequence" {
            val e = editor("c4 d4 e4")
            val result = e.removeNode(e.atomAt(2)!!)
            result.text shouldBe "c4 d4"
        }

        "removeNode: remove atom inside group" {
            val e = editor("c4 [d4 e4] f4")
            val result = e.removeNode(e.atomByValue("d4")!!)
            result.text shouldBe "c4 [e4] f4"
        }

        "removeNode: remove rest from sequence" {
            val e = editor("c4 ~ d4")
            val rest = e.rests().first()
            val result = e.removeNode(rest)
            result.text shouldBe "c4 d4"
        }

        // ── updateNode ────────────────────────────────────────────────────────

        "updateNode: replace atom with different pitch" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, MnNode.Atom("g4"))
            result.text shouldBe "c4 g4 e4"
        }

        "updateNode: replace atom with rest" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, MnNode.Rest(d4.sourceRange))
            result.text shouldBe "c4 ~ e4"
        }

        "updateNode: replace rest with atom" {
            val e = editor("c4 ~ e4")
            val rest = e.rests().first()
            val result = e.updateNode(rest, MnNode.Atom("d4"))
            result.text shouldBe "c4 d4 e4"
        }

        "updateNode: replace atom deep inside group" {
            val e = editor("c4 [d4 e4] f4")
            val e4 = e.atomByValue("e4")!!
            val result = e.updateNode(e4, MnNode.Atom("g4"))
            result.text shouldBe "c4 [d4 g4] f4"
        }

        // ── insertBetween ─────────────────────────────────────────────────────

        "insertBetween: append when both neighbours null (empty pattern)" {
            val e = editor("")
            val result = e.insertBetween(null, null, 5)
            result.text shouldBe "n5"
        }

        "insertBetween: append when both neighbours null (non-empty pattern)" {
            val e = editor("c4 d4")
            val result = e.insertBetween(null, null, 5)
            result.text shouldBe "c4 d4 n5"
        }

        "insertBetween: insert after first atom in sequence" {
            val e = editor("c4 d4 e4")
            val c4 = e.atomByValue("c4")!!
            val d4 = e.atomByValue("d4")!!
            val result = e.insertBetween(c4, d4, 3)
            result.text shouldBe "c4 n3 d4 e4"
        }

        "insertBetween: insert after last atom (left only)" {
            val e = editor("c4 d4")
            val d4 = e.atomByValue("d4")!!
            val result = e.insertBetween(d4, null, 7)
            result.text shouldBe "c4 d4 n7"
        }

        "insertBetween: insert before first atom (right only)" {
            val e = editor("c4 d4")
            val c4 = e.atomByValue("c4")!!
            val result = e.insertBetween(null, c4, 1)
            result.text shouldBe "n1 c4 d4"
        }

        "insertBetween: insert after atom inside group" {
            val e = editor("c4 [d4 e4] f4")
            val d4 = e.atomByValue("d4")!!
            val e4 = e.atomByValue("e4")!!
            val result = e.insertBetween(d4, e4, 2)
            result.text shouldBe "c4 [d4 n2 e4] f4"
        }

        "insertBetween: insert between group and following atom" {
            val e = editor("c4 [d4 e4] f4")
            val e4 = e.atomByValue("e4")!!
            val f4 = e.atomByValue("f4")!!
            val result = e.insertBetween(e4, f4, 4)
            result.text shouldBe "c4 [d4 e4] n4 f4"
        }

        "insertBetween: insert between rest and next atom" {
            val e = editor("c4 ~ d4")
            val rest = e.rests().first()
            val d4 = e.atomByValue("d4")!!
            val result = e.insertBetween(rest, d4, 0)
            result.text shouldBe "c4 ~ n0 d4"
        }

        // ── insertAt ──────────────────────────────────────────────────────────

        "insertAt: wrap single atom in stack" {
            val e = editor("c4")
            val c4 = e.atomByValue("c4")!!
            val result = e.insertAt(c4, 2)
            result.text shouldBe "[c4,n2]"
        }

        "insertAt: wrap middle atom of sequence in stack" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.insertAt(d4, 4)
            result.text shouldBe "c4 [d4,n4] e4"
        }

        "insertAt: extend existing stack with new note" {
            val e = editor("c4 [d4,e4] f4")
            val stack = e.stackNodes().first()
            val result = e.insertAt(stack, 7)
            result.text shouldBe "c4 [d4,e4,n7] f4"
        }

        "insertAt: wrap atom inside group in stack" {
            val e = editor("[c4 d4 e4]")
            val d4 = e.atomByValue("d4")!!
            val result = e.insertAt(d4, 6)
            result.text shouldBe "[c4 [d4,n6] e4]"
        }

        "insertAt: extend stack at start of pattern" {
            val e = editor("[c4,e4] g4")
            val stack = e.stackNodes().first()
            val result = e.insertAt(stack, 9)
            result.text shouldBe "[c4,e4,n9] g4"
        }

        // ── round-trip sanity ─────────────────────────────────────────────────

        "round-trip: pattern is parseable after each operation" {
            var e = editor("c4 d4 e4 f4")

            // insert between c4 and d4
            e = e.insertBetween(e.atomByValue("c4"), e.atomByValue("d4"), 1)
            e.pattern shouldNotBe null
            e.atoms() shouldHaveSize 5

            // remove the newly inserted atom
            val inserted = e.atomByValue("n1")!!
            e = e.removeNode(inserted)
            e.pattern shouldNotBe null
            e.atoms() shouldHaveSize 4

            // stack d4
            e = e.insertAt(e.atomByValue("d4")!!, 3)
            e.pattern shouldNotBe null
            e.stackNodes() shouldHaveSize 1

            // extend the stack
            val stack = e.stackNodes().first()
            e = e.insertAt(stack, 5)
            e.pattern shouldNotBe null

            assertSoftly {
                e.stackNodes() shouldHaveSize 1
                e.stackNodes().first().layers.flatten()
                    .filterIsInstance<MnNode.Atom>()
                    .map { it.value } shouldBe listOf("d4", "n3", "n5")
            }
        }

        "round-trip: alternation pattern survives insertBetween" {
            val e = editor("<c4 e4> g4")
            val g4 = e.atomByValue("g4")!!
            val result = e.insertBetween(g4, null, 8)
            result.text shouldBe "<c4 e4> g4 n8"
            result.pattern shouldNotBe null
        }

        "round-trip: nested group pattern survives insertAt then remove" {
            val e = editor("c4 [d4 [e4 f4]] g4")
            val e4 = e.atomByValue("e4")!!
            val stacked = e.insertAt(e4, 2)
            stacked.text shouldBe "c4 [d4 [[e4,n2] f4]] g4"
            stacked.pattern shouldNotBe null

            val stack = stacked.stackNodes().first()
            val removed = stacked.insertAt(stack, 9)
            removed.text shouldBe "c4 [d4 [[e4,n2,n9] f4]] g4"
        }

        "insertBetween: sequence of 5 insertions builds correct text" {
            var e = editor("")
            repeat(5) { i -> e = e.insertBetween(e.atoms().lastOrNull(), null, i) }
            assertSoftly {
                e.atoms() shouldHaveSize 5
                e.atoms().map { it.value } shouldBe listOf("n0", "n1", "n2", "n3", "n4")
            }
        }

        // ── <a [c d] [e f]> — cross-group boundary insertions ─────────────────

        "insertBetween: before alternation (left of <)" {
            val e = editor("<a [c d] [e f]>")
            val a = e.atomByValue("a")!!
            // Between(null, a) with scan-forward stops at < → inserts before the alternation
            val result = e.insertBetween(null, a, 0)
            result.text shouldBe "n0 <a [c d] [e f]>"
        }

        "insertBetween: between a and [c d]" {
            val e = editor("<a [c d] [e f]>")
            val a = e.atomByValue("a")!!
            val c = e.atomByValue("c")!!
            val result = e.insertBetween(a, c, 0)
            result.text shouldBe "<a n0 [c d] [e f]>"
        }

        "insertBetween: between c and d inside [c d]" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            val d = e.atomByValue("d")!!
            val result = e.insertBetween(c, d, 0)
            result.text shouldBe "<a [c n0 d] [e f]>"
        }

        "insertBetween: between [c d] and [e f] (cross-group boundary)" {
            val e = editor("<a [c d] [e f]>")
            val d = e.atomByValue("d")!!
            val ef = e.atomByValue("e")!!
            val result = e.insertBetween(d, ef, 0)
            result.text shouldBe "<a [c d] n0 [e f]>"
        }

        "insertBetween: between e and f inside [e f]" {
            val e = editor("<a [c d] [e f]>")
            val ef = e.atomByValue("e")!!
            val f = e.atomByValue("f")!!
            val result = e.insertBetween(ef, f, 0)
            result.text shouldBe "<a [c d] [e n0 f]>"
        }

        "insertBetween: after f (last item in alternation)" {
            val e = editor("<a [c d] [e f]>")
            val f = e.atomByValue("f")!!
            val result = e.insertBetween(f, null, 0)
            result.text shouldBe "<a [c d] [e f n0]>"
        }

        "insertAt: stack a inside alternation" {
            val e = editor("<a [c d] [e f]>")
            val a = e.atomByValue("a")!!
            val result = e.insertAt(a, 0)
            result.text shouldBe "<[a,n0] [c d] [e f]>"
        }

        "insertAt: stack c inside nested group" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            val result = e.insertAt(c, 0)
            result.text shouldBe "<a [[c,n0] d] [e f]>"
        }

        "insertAt: stack d inside nested group" {
            val e = editor("<a [c d] [e f]>")
            val d = e.atomByValue("d")!!
            val result = e.insertAt(d, 0)
            result.text shouldBe "<a [c [d,n0]] [e f]>"
        }

        "insertAt: stack e inside second nested group" {
            val e = editor("<a [c d] [e f]>")
            val ef = e.atomByValue("e")!!
            val result = e.insertAt(ef, 0)
            result.text shouldBe "<a [c d] [[e,n0] f]>"
        }

        "insertAt: stack f inside second nested group" {
            val e = editor("<a [c d] [e f]>")
            val f = e.atomByValue("f")!!
            val result = e.insertAt(f, 0)
            result.text shouldBe "<a [c d] [e [f,n0]]>"
        }

        "removeNode: remove a from alternation" {
            val e = editor("<a [c d] [e f]>")
            val a = e.atomByValue("a")!!
            val result = e.removeNode(a)
            result.text shouldBe "<[c d] [e f]>"
        }

        "removeNode: remove c from nested group" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            val result = e.removeNode(c)
            result.text shouldBe "<a [d] [e f]>"
        }

        // ── other nesting situations ───────────────────────────────────────────

        "insertBetween: nested group [a [b c] d] — between c and d (crosses one bracket)" {
            val e = editor("[a [b c] d]")
            val c = e.atomByValue("c")!!
            val d = e.atomByValue("d")!!
            val result = e.insertBetween(c, d, 0)
            result.text shouldBe "[a [b c] n0 d]"
        }

        "insertBetween: group inside alternation <[a b] c> — between b and c" {
            val e = editor("<[a b] c>")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            val result = e.insertBetween(b, c, 0)
            result.text shouldBe "<[a b] n0 c>"
        }

        "insertBetween: alternation inside group [<a b> c] — between b and c" {
            val e = editor("[<a b> c]")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            val result = e.insertBetween(b, c, 0)
            result.text shouldBe "[<a b> n0 c]"
        }

        "insertBetween: doubly-nested [c4 [d4 [e4 f4]] g4] — between f4 and g4 (crosses 2 brackets)" {
            val e = editor("c4 [d4 [e4 f4]] g4")
            val f4 = e.atomByValue("f4")!!
            val g4 = e.atomByValue("g4")!!
            val result = e.insertBetween(f4, g4, 0)
            result.text shouldBe "c4 [d4 [e4 f4]] n0 g4"
        }

        "insertBetween: stack [a,b] c — insert after stack (leftNode is last layer atom)" {
            val e = editor("[a,b] c")
            val b = e.atomByValue("b")!!
            val c = e.atomByValue("c")!!
            val result = e.insertBetween(b, c, 0)
            result.text shouldBe "[a,b] n0 c"
        }

        // ── atoms with modifiers — insertBetween must skip modifier suffix ────

        "insertBetween: skip *N modifier suffix when inserting after left atom" {
            val e = editor("c4 d4*2 e4")
            val d4 = e.atomByValue("d4")!!
            val e4 = e.atomByValue("e4")!!
            val result = e.insertBetween(d4, e4, 0)
            result.text shouldBe "c4 d4*2 n0 e4"
        }

        "insertBetween: skip /N modifier suffix when inserting after left atom" {
            val e = editor("c4 d4/3 e4")
            val d4 = e.atomByValue("d4")!!
            val e4 = e.atomByValue("e4")!!
            val result = e.insertBetween(d4, e4, 0)
            result.text shouldBe "c4 d4/3 n0 e4"
        }

        "insertBetween: append after modifier-carrying last atom" {
            val e = editor("c4 d4*2")
            val d4 = e.atomByValue("d4")!!
            val result = e.insertBetween(d4, null, 0)
            result.text shouldBe "c4 d4*2 n0"
        }

        "insertBetween: insert before atom that follows a modifier-carrying atom" {
            val e = editor("c4*3 d4 e4")
            val c4 = e.atomByValue("c4")!!
            val d4 = e.atomByValue("d4")!!
            val result = e.insertBetween(c4, d4, 0)
            result.text shouldBe "c4*3 n0 d4 e4"
        }

        // ── updateNode: modifier changes ──────────────────────────────────────

        "updateNode: add multiplier *2 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(multiplier = 2.0)))
            result.text shouldBe "c4 d4*2 e4"
        }

        "updateNode: add divisor /3 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(divisor = 3.0)))
            result.text shouldBe "c4 d4/3 e4"
        }

        "updateNode: add weight @2 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(weight = 2.0)))
            result.text shouldBe "c4 d4@2 e4"
        }

        "updateNode: add probability ?0.5 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(probability = 0.5)))
            result.text shouldBe "c4 d4?0.5 e4"
        }

        "updateNode: add euclidean (3,8) to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(
                d4, d4.copy(
                    mods = MnNode.Mods(euclidean = MnNode.Euclidean(3, 8))
                )
            )
            result.text shouldBe "c4 d4(3,8) e4"
        }

        "updateNode: remove modifier from atom" {
            val e = editor("c4 d4*2 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods.None))
            result.text shouldBe "c4 d4 e4"
        }

        "updateNode: change multiplier value on atom" {
            val e = editor("c4 d4*2 e4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(multiplier = 4.0)))
            result.text shouldBe "c4 d4*4 e4"
        }

        "updateNode: add multiplier to atom inside nested group" {
            val e = editor("c4 [d4 e4] f4")
            val d4 = e.atomByValue("d4")!!
            val result = e.updateNode(d4, d4.copy(mods = MnNode.Mods(multiplier = 3.0)))
            result.text shouldBe "c4 [d4*3 e4] f4"
        }

        "updateNode: add multiplier to atom inside alternation" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            val result = e.updateNode(c, c.copy(mods = MnNode.Mods(multiplier = 2.0)))
            result.text shouldBe "<a [c*2 d] [e f]>"
        }

        "insertBetween: modifier-carrying atom cross-group boundary" {
            // d4*2 is last in [c4 d4*2], e4 is first in [e4 f4] — insert between the groups
            val e = editor("[c4 d4*2] [e4 f4]")
            val d4 = e.atomByValue("d4")!!
            val e4 = e.atomByValue("e4")!!
            val result = e.insertBetween(d4, e4, 0)
            result.text shouldBe "[c4 d4*2] n0 [e4 f4]"
        }
    }
}
