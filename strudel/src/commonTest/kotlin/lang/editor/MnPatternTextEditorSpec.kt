package io.peekandpoke.klang.strudel.lang.editor

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern

/**
 * Tests for [MnPatternTextEditor] covering replaceNode, removeNode,
 * and combined tree-level operations (sequential insert, stack operations).
 *
 * The editor uses a tree-based approach:
 * - [MnPatternTextEditor.replaceNode]: replace a node by id
 * - [MnPatternTextEditor.removeNode]: remove a node by id
 * - Domain methods on nodes: [MnPattern.insertAt], [MnNode.Group.insertAt], etc.
 */
class MnPatternTextEditorSpec : StringSpec() {

    private fun editor(text: String) = MnPatternTextEditor(text)

    // ── Test-only query helpers ─────────────────────────────────────────────

    private fun MnPatternTextEditor.atoms(): List<MnNode.Atom> =
        pattern?.let { MnNodeOps.collectAtoms(it) } ?: emptyList()

    private fun MnPatternTextEditor.rests(): List<MnNode.Rest> =
        pattern?.let { MnNodeOps.collectRests(it) } ?: emptyList()

    private fun MnPatternTextEditor.atomByValue(value: String): MnNode.Atom? =
        atoms().firstOrNull { it.value == value }

    private fun MnPatternTextEditor.atomAt(index: Int): MnNode.Atom? =
        atoms().getOrNull(index)

    private fun MnPatternTextEditor.stackNodes(): List<MnNode.Stack> =
        pattern?.let { MnNodeOps.collectStacks(it) } ?: emptyList()

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Insert a new atom into the container with [containerId] at [index]. */
    private fun MnPatternTextEditor.insertChild(containerId: Int, index: Int, value: String): MnPatternTextEditor {
        val p = pattern ?: error("No pattern")
        val container = p.findById(containerId) ?: error("Container not found")
        val newAtom = MnNode.Atom(value)
        val newContainer = when (container) {
            is MnPattern -> container.insertAt(index, newAtom)
            is MnNode.Group -> container.insertAt(index, newAtom)
            is MnNode.Alternation -> container.insertAt(index, newAtom)
            else -> error("Cannot insert into ${container::class.simpleName}")
        }
        return replaceNode(containerId, newContainer)
    }

    /** Stack a new atom onto [nodeId]. Wraps atom in Group+Stack or adds layer to existing Stack. */
    private fun MnPatternTextEditor.stackOnto(nodeId: Int, value: String): MnPatternTextEditor {
        val p = pattern ?: error("No pattern")
        val node = p.findById(nodeId) ?: error("Node not found")
        val newAtom = MnNode.Atom(value)
        return when (node) {
            is MnNode.Atom -> {
                val stack = MnNode.Group(items = listOf(MnNode.Stack(layers = listOf(listOf(node), listOf(newAtom)))))
                replaceNode(nodeId, stack)
            }

            is MnNode.Stack -> replaceNode(nodeId, node.addLayer(newAtom))
            is MnNode.Rest -> replaceNode(nodeId, newAtom)
            else -> error("Cannot stack onto ${node::class.simpleName}")
        }
    }

    init {

        // ── removeNode ────────────────────────────────────────────────────────

        "removeNode: remove only atom leaves empty text" {
            val e = editor("c4")
            e.removeNode(e.atomAt(0)!!.id).text shouldBe ""
        }

        "removeNode: remove first atom from sequence" {
            val e = editor("c4 d4 e4")
            e.removeNode(e.atomAt(0)!!.id).text shouldBe "d4 e4"
        }

        "removeNode: remove middle atom from sequence" {
            val e = editor("c4 d4 e4")
            e.removeNode(e.atomAt(1)!!.id).text shouldBe "c4 e4"
        }

        "removeNode: remove last atom from sequence" {
            val e = editor("c4 d4 e4")
            e.removeNode(e.atomAt(2)!!.id).text shouldBe "c4 d4"
        }

        "removeNode: remove atom inside group" {
            val e = editor("c4 [d4 e4] f4")
            e.removeNode(e.atomByValue("d4")!!.id).text shouldBe "c4 [e4] f4"
        }

        "removeNode: remove rest from sequence" {
            val e = editor("c4 ~ d4")
            e.removeNode(e.rests().first().id).text shouldBe "c4 d4"
        }

        "removeNode: remove a from alternation" {
            val e = editor("<a [c d] [e f]>")
            e.removeNode(e.atomByValue("a")!!.id).text shouldBe "<[c d] [e f]>"
        }

        "removeNode: remove c from nested group" {
            val e = editor("<a [c d] [e f]>")
            e.removeNode(e.atomByValue("c")!!.id).text shouldBe "<a [d] [e f]>"
        }

        // ── replaceNode ─────────────────────────────────────────────────────

        "replaceNode: replace atom with different pitch" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, MnNode.Atom("g4")).text shouldBe "c4 g4 e4"
        }

        "replaceNode: replace atom with rest" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, MnNode.Rest(d4.sourceRange)).text shouldBe "c4 ~ e4"
        }

        "replaceNode: replace rest with atom" {
            val e = editor("c4 ~ e4")
            val rest = e.rests().first()
            e.replaceNode(rest.id, MnNode.Atom("d4")).text shouldBe "c4 d4 e4"
        }

        "replaceNode: replace atom deep inside group" {
            val e = editor("c4 [d4 e4] f4")
            val e4 = e.atomByValue("e4")!!
            e.replaceNode(e4.id, MnNode.Atom("g4")).text shouldBe "c4 [d4 g4] f4"
        }

        // ── replaceNode: modifier changes ───────────────────────────────────

        "replaceNode: add multiplier *2 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 2.0))).text shouldBe "c4 d4*2 e4"
        }

        "replaceNode: add divisor /3 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(divisor = 3.0))).text shouldBe "c4 d4/3 e4"
        }

        "replaceNode: add weight @2 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(weight = 2.0))).text shouldBe "c4 d4@2 e4"
        }

        "replaceNode: add probability ?0.5 to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(probability = 0.5))).text shouldBe "c4 d4?0.5 e4"
        }

        "replaceNode: add euclidean (3,8) to atom" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(euclidean = MnNode.Euclidean(3, 8)))).text shouldBe "c4 d4(3,8) e4"
        }

        "replaceNode: remove modifier from atom" {
            val e = editor("c4 d4*2 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods.None)).text shouldBe "c4 d4 e4"
        }

        "replaceNode: change multiplier value on atom" {
            val e = editor("c4 d4*2 e4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 4.0))).text shouldBe "c4 d4*4 e4"
        }

        "replaceNode: add multiplier to atom inside nested group" {
            val e = editor("c4 [d4 e4] f4")
            val d4 = e.atomByValue("d4")!!
            e.replaceNode(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 3.0))).text shouldBe "c4 [d4*3 e4] f4"
        }

        "replaceNode: add multiplier to atom inside alternation" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            e.replaceNode(c.id, c.copy(mods = MnNode.Mods(multiplier = 2.0))).text shouldBe "<a [c*2 d] [e f]>"
        }

        // ── Sequential insert into flat sequence ────────────────────────────

        "insert: append to non-empty sequence" {
            val e = editor("c4 d4")
            val p = e.pattern!!
            e.insertChild(p.id, p.items.size, "n5").text shouldBe "c4 d4 n5"
        }

        "insert: prepend to sequence" {
            val e = editor("c4 d4")
            val p = e.pattern!!
            e.insertChild(p.id, 0, "n1").text shouldBe "n1 c4 d4"
        }

        "insert: after first atom in sequence" {
            val e = editor("c4 d4 e4")
            val p = e.pattern!!
            e.insertChild(p.id, 1, "n3").text shouldBe "c4 n3 d4 e4"
        }

        "insert: after last atom in sequence" {
            val e = editor("c4 d4")
            val p = e.pattern!!
            e.insertChild(p.id, 2, "n7").text shouldBe "c4 d4 n7"
        }

        "insert: before first atom in sequence" {
            val e = editor("c4 d4")
            val p = e.pattern!!
            e.insertChild(p.id, 0, "n1").text shouldBe "n1 c4 d4"
        }

        // ── Sequential insert inside groups ─────────────────────────────────

        "insert: between atoms inside group" {
            val e = editor("c4 [d4 e4] f4")
            val group = e.pattern!!.items[1] as MnNode.Group
            e.insertChild(group.id, 1, "n2").text shouldBe "c4 [d4 n2 e4] f4"
        }

        "insert: at start of group" {
            val e = editor("c4 [d4 e4] f4")
            val group = e.pattern!!.items[1] as MnNode.Group
            e.insertChild(group.id, 0, "n0").text shouldBe "c4 [n0 d4 e4] f4"
        }

        "insert: at end of group" {
            val e = editor("c4 [d4 e4] f4")
            val group = e.pattern!!.items[1] as MnNode.Group
            e.insertChild(group.id, group.items.size, "n0").text shouldBe "c4 [d4 e4 n0] f4"
        }

        "insert: between group and following atom (at pattern level)" {
            val e = editor("c4 [d4 e4] f4")
            val p = e.pattern!!
            // Group is at index 1, f4 at index 2 → insert at index 2
            e.insertChild(p.id, 2, "n4").text shouldBe "c4 [d4 e4] n4 f4"
        }

        // ── Sequential insert inside alternation ────────────────────────────

        "insert: inside alternation between items" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            // a is at index 0, [c d] at index 1 → insert at index 1
            e.insertChild(alt.id, 1, "n0").text shouldBe "<a n0 [c d] [e f]>"
        }

        "insert: at start of alternation" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            e.insertChild(alt.id, 0, "n0").text shouldBe "<n0 a [c d] [e f]>"
        }

        "insert: before alternation (at top level)" {
            val e = editor("<a [c d] [e f]>")
            val p = e.pattern!!
            e.insertChild(p.id, 0, "n0").text shouldBe "n0 <a [c d] [e f]>"
        }

        "insert: between c and d inside group within alternation" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grpCD = alt.items[1] as MnNode.Group
            e.insertChild(grpCD.id, 1, "n0").text shouldBe "<a [c n0 d] [e f]>"
        }

        "insert: between e and f inside second group in alternation" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grpEF = alt.items[2] as MnNode.Group
            e.insertChild(grpEF.id, 1, "n0").text shouldBe "<a [c d] [e n0 f]>"
        }

        "insert: between [c d] and [e f] groups (at alternation level)" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            // [c d] at index 1, [e f] at index 2 → insert at index 2
            e.insertChild(alt.id, 2, "n0").text shouldBe "<a [c d] n0 [e f]>"
        }

        "insert: after f (end of alternation)" {
            val e = editor("<a [c d] [e f]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grpEF = alt.items[2] as MnNode.Group
            e.insertChild(grpEF.id, grpEF.items.size, "n0").text shouldBe "<a [c d] [e f n0]>"
        }

        // ── Cross-group boundary insertions ─────────────────────────────────

        "insert: between nested groups — crosses one bracket" {
            val e = editor("[a [b c] d]")
            val outerGroup = e.pattern!!.items[0] as MnNode.Group
            // [b c] is at index 1, d at index 2 → insert at index 2
            e.insertChild(outerGroup.id, 2, "n0").text shouldBe "[a [b c] n0 d]"
        }

        "insert: group inside alternation — between b and c" {
            val e = editor("<[a b] c>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            // [a b] at index 0, c at index 1 → insert at index 1
            e.insertChild(alt.id, 1, "n0").text shouldBe "<[a b] n0 c>"
        }

        "insert: alternation inside group — between <a b> and c" {
            val e = editor("[<a b> c]")
            val group = e.pattern!!.items[0] as MnNode.Group
            // <a b> at index 0, c at index 1 → insert at index 1
            e.insertChild(group.id, 1, "n0").text shouldBe "[<a b> n0 c]"
        }

        "insert: doubly-nested — between inner group and outer atom" {
            val e = editor("c4 [d4 [e4 f4]] g4")
            val p = e.pattern!!
            // [d4 [e4 f4]] is at index 1, g4 at index 2 → insert at index 2
            e.insertChild(p.id, 2, "n0").text shouldBe "c4 [d4 [e4 f4]] n0 g4"
        }

        "insert: between rest and next atom" {
            val e = editor("c4 ~ d4")
            val p = e.pattern!!
            // ~ at index 1, d4 at index 2 → insert at index 2
            e.insertChild(p.id, 2, "n0").text shouldBe "c4 ~ n0 d4"
        }

        "insert: after stack at top level" {
            val e = editor("[a,b] c")
            val p = e.pattern!!
            // [a,b] at index 0, c at index 1 → insert at index 1
            e.insertChild(p.id, 1, "n0").text shouldBe "[a,b] n0 c"
        }

        "insert: before stack at top level" {
            val e = editor("[c4,e4] d4")
            val p = e.pattern!!
            e.insertChild(p.id, 0, "n0").text shouldBe "n0 [c4,e4] d4"
        }

        // ── Stack operations (stackOnto) ────────────────────────────────────

        "stackOnto: wrap single atom in stack" {
            val e = editor("c4")
            val c4 = e.atomByValue("c4")!!
            e.stackOnto(c4.id, "n2").text shouldBe "[c4,n2]"
        }

        "stackOnto: wrap middle atom of sequence in stack" {
            val e = editor("c4 d4 e4")
            val d4 = e.atomByValue("d4")!!
            e.stackOnto(d4.id, "n4").text shouldBe "c4 [d4,n4] e4"
        }

        "stackOnto: extend existing stack with new layer" {
            val e = editor("c4 [d4,e4] f4")
            val stack = e.stackNodes().first()
            e.stackOnto(stack.id, "n7").text shouldBe "c4 [d4,e4,n7] f4"
        }

        "stackOnto: wrap atom inside group in stack" {
            val e = editor("[c4 d4 e4]")
            val d4 = e.atomByValue("d4")!!
            e.stackOnto(d4.id, "n6").text shouldBe "[c4 [d4,n6] e4]"
        }

        "stackOnto: extend stack at start of pattern" {
            val e = editor("[c4,e4] g4")
            val stack = e.stackNodes().first()
            e.stackOnto(stack.id, "n9").text shouldBe "[c4,e4,n9] g4"
        }

        "stackOnto: a inside alternation" {
            val e = editor("<a [c d] [e f]>")
            val a = e.atomByValue("a")!!
            e.stackOnto(a.id, "n0").text shouldBe "<[a,n0] [c d] [e f]>"
        }

        "stackOnto: c inside nested group" {
            val e = editor("<a [c d] [e f]>")
            val c = e.atomByValue("c")!!
            e.stackOnto(c.id, "n0").text shouldBe "<a [[c,n0] d] [e f]>"
        }

        "stackOnto: d inside nested group" {
            val e = editor("<a [c d] [e f]>")
            val d = e.atomByValue("d")!!
            e.stackOnto(d.id, "n0").text shouldBe "<a [c [d,n0]] [e f]>"
        }

        "stackOnto: e inside second nested group" {
            val e = editor("<a [c d] [e f]>")
            val ef = e.atomByValue("e")!!
            e.stackOnto(ef.id, "n0").text shouldBe "<a [c d] [[e,n0] f]>"
        }

        "stackOnto: f inside second nested group" {
            val e = editor("<a [c d] [e f]>")
            val f = e.atomByValue("f")!!
            e.stackOnto(f.id, "n0").text shouldBe "<a [c d] [e [f,n0]]>"
        }

        // ── Round-trip sanity ───────────────────────────────────────────────

        "round-trip: pattern is parseable after each operation" {
            var e = editor("c4 d4 e4 f4")

            // insert between c4 and d4 (at index 1 in pattern)
            val p1 = e.pattern!!
            e = e.insertChild(p1.id, 1, "n1")
            e.pattern shouldNotBe null
            e.atoms() shouldHaveSize 5

            // remove the newly inserted atom
            val inserted = e.atomByValue("n1")!!
            e = e.removeNode(inserted.id)
            e.pattern shouldNotBe null
            e.atoms() shouldHaveSize 4

            // stack d4
            e = e.stackOnto(e.atomByValue("d4")!!.id, "n3")
            e.pattern shouldNotBe null
            e.stackNodes() shouldHaveSize 1

            // extend the stack
            val stack = e.stackNodes().first()
            e = e.stackOnto(stack.id, "n5")
            e.pattern shouldNotBe null

            assertSoftly {
                e.stackNodes() shouldHaveSize 1
                e.stackNodes().first().layers.flatten()
                    .filterIsInstance<MnNode.Atom>()
                    .map { it.value } shouldBe listOf("d4", "n3", "n5")
            }
        }

        "round-trip: alternation pattern survives insert" {
            val e = editor("<c4 e4> g4")
            val p = e.pattern!!
            val result = e.insertChild(p.id, p.items.size, "n8")
            result.text shouldBe "<c4 e4> g4 n8"
            result.pattern shouldNotBe null
        }

        "round-trip: nested group survives stackOnto then extend" {
            val e = editor("c4 [d4 [e4 f4]] g4")
            val e4 = e.atomByValue("e4")!!
            val stacked = e.stackOnto(e4.id, "n2")
            stacked.text shouldBe "c4 [d4 [[e4,n2] f4]] g4"
            stacked.pattern shouldNotBe null

            val stack = stacked.stackNodes().first()
            val extended = stacked.stackOnto(stack.id, "n9")
            extended.text shouldBe "c4 [d4 [[e4,n2,n9] f4]] g4"
        }

        "round-trip: sequence of 5 appends builds correct text" {
            var e = editor("n0")
            repeat(4) { i ->
                val p = e.pattern!!
                e = e.insertChild(p.id, p.items.size, "n${i + 1}")
            }
            assertSoftly {
                e.atoms() shouldHaveSize 5
                e.atoms().map { it.value } shouldBe listOf("n0", "n1", "n2", "n3", "n4")
            }
        }

        // ── Comprehensive: deeply nested pattern ────────────────────────────
        //
        // Pattern: <[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>
        //
        // AST structure:
        //   Alternation
        //     Group(Stack([0],[7],[12]))                         — [0,7,12]
        //     Group(Stack([12],[19],[24]))                       — [12,19,24]
        //     Group(Group(Stack([1],[8],[13])), Group(Stack(…))) — [[1,8,13] [1,8,13]]
        //     Group(Group(Stack([1],[8],[13])))                   — [[1,8,13]]

        "deep: insert before alternation (top level)" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val p = e.pattern!!
            e.insertChild(p.id, 0, "n0")
                .text shouldBe "n0 <[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert at start of alternation" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            e.insertChild(alt.id, 0, "n0")
                .text shouldBe "<n0 [0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside first group before stack" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp0 = alt.items[0] as MnNode.Group
            e.insertChild(grp0.id, 0, "n0")
                .text shouldBe "<[n0 0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: add layer to first stack" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            e.stackOnto(e.stackNodes()[0].id, "n0")
                .text shouldBe "<[0,7,12,n0] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert after first group inside alternation" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            e.insertChild(alt.id, 1, "n0")
                .text shouldBe "<[0,7,12] n0 [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert between second and third group" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            e.insertChild(alt.id, 2, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] n0 [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside third outer group before first inner" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            e.insertChild(grp2.id, 0, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [n0 [1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside first inner group of third" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            val innerGrp0 = grp2.items[0] as MnNode.Group
            e.insertChild(innerGrp0.id, 0, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[n0 1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: add layer to first inner stack of third group" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            e.stackOnto(e.stackNodes()[2].id, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13,n0] [1,8,13]] [[1,8,13]]>"
        }

        "deep: between two inner groups of third" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            e.insertChild(grp2.id, 1, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13] n0 [1,8,13]] [[1,8,13]]>"
        }

        "deep: between third and fourth group" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            e.insertChild(alt.id, 3, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] n0 [[1,8,13]]>"
        }

        "deep: add layer to fourth stack" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            e.stackOnto(e.stackNodes()[4].id, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13,n0]]>"
        }

        "deep: after alternation at top level" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val p = e.pattern!!
            e.insertChild(p.id, p.items.size, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]> n0"
        }

        // ── Sequential insert after stack inside group ──────────────────────

        "insert: after stack inside group" {
            val e = editor("<[0,7,12] [12,19,24]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp0 = alt.items[0] as MnNode.Group
            // Stack is at index 0 → insert at index 1 (after stack, still inside group)
            e.insertChild(grp0.id, 1, "n0")
                .text shouldBe "<[0,7,12 n0] [12,19,24]>"
        }

        "insert: after inner group inside outer" {
            val e = editor("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = e.pattern!!.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            // Insert at end of outer group (after both inner groups)
            e.insertChild(grp2.id, grp2.items.size, "n0")
                .text shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13] n0] [[1,8,13]]>"
        }
    }
}
