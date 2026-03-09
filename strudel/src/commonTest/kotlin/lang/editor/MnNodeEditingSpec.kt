package io.peekandpoke.klang.strudel.lang.editor

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern

/**
 * Tests for MnNode tree-editing operations: removeById, replaceById,
 * insertAt, addLayer — exercised directly on the parsed tree.
 */
class MnNodeEditingSpec : StringSpec() {

    private fun parse(text: String): MnPattern = parseMiniNotationMnPattern(text)
    private fun render(p: MnPattern): String = MnRenderer.render(p)
    private fun atoms(p: MnPattern): List<MnNode.Atom> = MnNodeOps.collectAtoms(p)
    private fun rests(p: MnPattern): List<MnNode.Rest> = MnNodeOps.collectRests(p)
    private fun stacks(p: MnPattern): List<MnNode.Stack> = MnNodeOps.collectStacks(p)
    private fun atomByValue(p: MnPattern, value: String): MnNode.Atom? = atoms(p).firstOrNull { it.value == value }

    /** Insert a new atom into the container with [containerId] at [index], return re-rendered text.
     *  Uses [MnNodeOps.groupInsertAt] for Groups to wrap Stacks in sub-Groups. */
    private fun insertChild(p: MnPattern, containerId: Int, index: Int, value: String): String {
        val container = p.findById(containerId) ?: error("Container not found")
        val newAtom = MnNode.Atom(value)
        val newContainer = when (container) {
            is MnPattern -> container.insertAt(index, newAtom)
            is MnNode.Group -> MnNodeOps.groupInsertAt(container, index, newAtom)
            is MnNode.Alternation -> container.insertAt(index, newAtom)
            else -> error("Cannot insert into ${container::class.simpleName}")
        }
        val newRoot = p.replaceById(containerId, newContainer) as? MnPattern ?: error("replaceById failed")
        return render(newRoot)
    }

    /** Remove a node and normalize wrapper groups afterward. */
    private fun removeAndNormalize(p: MnPattern, targetId: Int): String {
        val newRoot = p.removeById(targetId) as? MnPattern ?: error("removeById failed")
        return render(MnNodeOps.normalizeGroups(newRoot))
    }

    /** Stack a new atom onto [nodeId]. Wraps atom in Group+Stack or adds layer to existing Stack. */
    private fun stackOnto(p: MnPattern, nodeId: Int, value: String): String {
        val node = p.findById(nodeId) ?: error("Node not found")
        val newAtom = MnNode.Atom(value)
        val replacement = when (node) {
            is MnNode.Atom -> MnNode.Group(items = listOf(MnNode.Stack(layers = listOf(listOf(node), listOf(newAtom)))))
            is MnNode.Stack -> node.addLayer(newAtom)
            is MnNode.Rest -> newAtom
            else -> error("Cannot stack onto ${node::class.simpleName}")
        }
        val newRoot = p.replaceById(nodeId, replacement) as? MnPattern ?: error("replaceById failed")
        return render(newRoot)
    }

    init {

        // ── removeById ────────────────────────────────────────────────────────

        "removeById: remove only atom leaves empty text" {
            val p = parse("c4")
            val result = p.removeById(atoms(p)[0].id) as MnPattern
            render(result) shouldBe ""
        }

        "removeById: remove first atom from sequence" {
            val p = parse("c4 d4 e4")
            val result = p.removeById(atoms(p)[0].id) as MnPattern
            render(result) shouldBe "d4 e4"
        }

        "removeById: remove middle atom from sequence" {
            val p = parse("c4 d4 e4")
            val result = p.removeById(atoms(p)[1].id) as MnPattern
            render(result) shouldBe "c4 e4"
        }

        "removeById: remove last atom from sequence" {
            val p = parse("c4 d4 e4")
            val result = p.removeById(atoms(p)[2].id) as MnPattern
            render(result) shouldBe "c4 d4"
        }

        "removeById: remove atom inside group" {
            val p = parse("c4 [d4 e4] f4")
            val result = p.removeById(atomByValue(p, "d4")!!.id) as MnPattern
            render(result) shouldBe "c4 [e4] f4"
        }

        "removeById: remove rest from sequence" {
            val p = parse("c4 ~ d4")
            val result = p.removeById(rests(p).first().id) as MnPattern
            render(result) shouldBe "c4 d4"
        }

        "removeById: remove a from alternation" {
            val p = parse("<a [c d] [e f]>")
            val result = p.removeById(atomByValue(p, "a")!!.id) as MnPattern
            render(result) shouldBe "<[c d] [e f]>"
        }

        "removeById: remove c from nested group" {
            val p = parse("<a [c d] [e f]>")
            val result = p.removeById(atomByValue(p, "c")!!.id) as MnPattern
            render(result) shouldBe "<a [d] [e f]>"
        }

        // ── replaceById ─────────────────────────────────────────────────────

        "replaceById: replace atom with different pitch" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, MnNode.Atom("g4")) as MnPattern
            render(result) shouldBe "c4 g4 e4"
        }

        "replaceById: replace atom with rest" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, MnNode.Rest(d4.sourceRange)) as MnPattern
            render(result) shouldBe "c4 ~ e4"
        }

        "replaceById: replace rest with atom" {
            val p = parse("c4 ~ e4")
            val rest = rests(p).first()
            val result = p.replaceById(rest.id, MnNode.Atom("d4")) as MnPattern
            render(result) shouldBe "c4 d4 e4"
        }

        "replaceById: replace atom deep inside group" {
            val p = parse("c4 [d4 e4] f4")
            val e4 = atomByValue(p, "e4")!!
            val result = p.replaceById(e4.id, MnNode.Atom("g4")) as MnPattern
            render(result) shouldBe "c4 [d4 g4] f4"
        }

        // ── replaceById: modifier changes ───────────────────────────────────

        "replaceById: add multiplier *2 to atom" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 2.0))) as MnPattern
            render(result) shouldBe "c4 d4*2 e4"
        }

        "replaceById: add divisor /3 to atom" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(divisor = 3.0))) as MnPattern
            render(result) shouldBe "c4 d4/3 e4"
        }

        "replaceById: add weight @2 to atom" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(weight = 2.0))) as MnPattern
            render(result) shouldBe "c4 d4@2 e4"
        }

        "replaceById: add probability ?0.5 to atom" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(probability = 0.5))) as MnPattern
            render(result) shouldBe "c4 d4?0.5 e4"
        }

        "replaceById: add euclidean (3,8) to atom" {
            val p = parse("c4 d4 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(euclidean = MnNode.Euclidean(3, 8)))) as MnPattern
            render(result) shouldBe "c4 d4(3,8) e4"
        }

        "replaceById: remove modifier from atom" {
            val p = parse("c4 d4*2 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods.None)) as MnPattern
            render(result) shouldBe "c4 d4 e4"
        }

        "replaceById: change multiplier value on atom" {
            val p = parse("c4 d4*2 e4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 4.0))) as MnPattern
            render(result) shouldBe "c4 d4*4 e4"
        }

        "replaceById: add multiplier to atom inside nested group" {
            val p = parse("c4 [d4 e4] f4")
            val d4 = atomByValue(p, "d4")!!
            val result = p.replaceById(d4.id, d4.copy(mods = MnNode.Mods(multiplier = 3.0))) as MnPattern
            render(result) shouldBe "c4 [d4*3 e4] f4"
        }

        "replaceById: add multiplier to atom inside alternation" {
            val p = parse("<a [c d] [e f]>")
            val c = atomByValue(p, "c")!!
            val result = p.replaceById(c.id, c.copy(mods = MnNode.Mods(multiplier = 2.0))) as MnPattern
            render(result) shouldBe "<a [c*2 d] [e f]>"
        }

        // ── Sequential insert into flat sequence ────────────────────────────

        "insertAt: append to non-empty sequence" {
            val p = parse("c4 d4")
            insertChild(p, p.id, p.items.size, "n5") shouldBe "c4 d4 n5"
        }

        "insertAt: prepend to sequence" {
            val p = parse("c4 d4")
            insertChild(p, p.id, 0, "n1") shouldBe "n1 c4 d4"
        }

        "insertAt: after first atom in sequence" {
            val p = parse("c4 d4 e4")
            insertChild(p, p.id, 1, "n3") shouldBe "c4 n3 d4 e4"
        }

        "insertAt: after last atom in sequence" {
            val p = parse("c4 d4")
            insertChild(p, p.id, 2, "n7") shouldBe "c4 d4 n7"
        }

        "insertAt: before first atom in sequence" {
            val p = parse("c4 d4")
            insertChild(p, p.id, 0, "n1") shouldBe "n1 c4 d4"
        }

        // ── Sequential insert inside groups ─────────────────────────────────

        "insertAt: between atoms inside group" {
            val p = parse("c4 [d4 e4] f4")
            val group = p.items[1] as MnNode.Group
            insertChild(p, group.id, 1, "n2") shouldBe "c4 [d4 n2 e4] f4"
        }

        "insertAt: at start of group" {
            val p = parse("c4 [d4 e4] f4")
            val group = p.items[1] as MnNode.Group
            insertChild(p, group.id, 0, "n0") shouldBe "c4 [n0 d4 e4] f4"
        }

        "insertAt: at end of group" {
            val p = parse("c4 [d4 e4] f4")
            val group = p.items[1] as MnNode.Group
            insertChild(p, group.id, group.items.size, "n0") shouldBe "c4 [d4 e4 n0] f4"
        }

        "insertAt: between group and following atom (at pattern level)" {
            val p = parse("c4 [d4 e4] f4")
            insertChild(p, p.id, 2, "n4") shouldBe "c4 [d4 e4] n4 f4"
        }

        // ── Sequential insert inside alternation ────────────────────────────

        "insertAt: inside alternation between items" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 1, "n0") shouldBe "<a n0 [c d] [e f]>"
        }

        "insertAt: at start of alternation" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 0, "n0") shouldBe "<n0 a [c d] [e f]>"
        }

        "insertAt: before alternation (at top level)" {
            val p = parse("<a [c d] [e f]>")
            insertChild(p, p.id, 0, "n0") shouldBe "n0 <a [c d] [e f]>"
        }

        "insertAt: between c and d inside group within alternation" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            val grpCD = alt.items[1] as MnNode.Group
            insertChild(p, grpCD.id, 1, "n0") shouldBe "<a [c n0 d] [e f]>"
        }

        "insertAt: between e and f inside second group in alternation" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            val grpEF = alt.items[2] as MnNode.Group
            insertChild(p, grpEF.id, 1, "n0") shouldBe "<a [c d] [e n0 f]>"
        }

        "insertAt: between [c d] and [e f] groups (at alternation level)" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 2, "n0") shouldBe "<a [c d] n0 [e f]>"
        }

        "insertAt: after f (end of alternation)" {
            val p = parse("<a [c d] [e f]>")
            val alt = p.items[0] as MnNode.Alternation
            val grpEF = alt.items[2] as MnNode.Group
            insertChild(p, grpEF.id, grpEF.items.size, "n0") shouldBe "<a [c d] [e f n0]>"
        }

        // ── Cross-group boundary insertions ─────────────────────────────────

        "insertAt: between nested groups — crosses one bracket" {
            val p = parse("[a [b c] d]")
            val outerGroup = p.items[0] as MnNode.Group
            insertChild(p, outerGroup.id, 2, "n0") shouldBe "[a [b c] n0 d]"
        }

        "insertAt: group inside alternation — between b and c" {
            val p = parse("<[a b] c>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 1, "n0") shouldBe "<[a b] n0 c>"
        }

        "insertAt: alternation inside group — between <a b> and c" {
            val p = parse("[<a b> c]")
            val group = p.items[0] as MnNode.Group
            insertChild(p, group.id, 1, "n0") shouldBe "[<a b> n0 c]"
        }

        "insertAt: doubly-nested — between inner group and outer atom" {
            val p = parse("c4 [d4 [e4 f4]] g4")
            insertChild(p, p.id, 2, "n0") shouldBe "c4 [d4 [e4 f4]] n0 g4"
        }

        "insertAt: between rest and next atom" {
            val p = parse("c4 ~ d4")
            insertChild(p, p.id, 2, "n0") shouldBe "c4 ~ n0 d4"
        }

        "insertAt: after stack at top level" {
            val p = parse("[a,b] c")
            insertChild(p, p.id, 1, "n0") shouldBe "[a,b] n0 c"
        }

        "insertAt: before stack at top level" {
            val p = parse("[c4,e4] d4")
            insertChild(p, p.id, 0, "n0") shouldBe "n0 [c4,e4] d4"
        }

        // ── Stack operations (stackOnto) ────────────────────────────────────

        "stackOnto: wrap single atom in stack" {
            val p = parse("c4")
            stackOnto(p, atomByValue(p, "c4")!!.id, "n2") shouldBe "[c4,n2]"
        }

        "stackOnto: wrap middle atom of sequence in stack" {
            val p = parse("c4 d4 e4")
            stackOnto(p, atomByValue(p, "d4")!!.id, "n4") shouldBe "c4 [d4,n4] e4"
        }

        "stackOnto: extend existing stack with new layer" {
            val p = parse("c4 [d4,e4] f4")
            stackOnto(p, stacks(p).first().id, "n7") shouldBe "c4 [d4,e4,n7] f4"
        }

        "stackOnto: wrap atom inside group in stack" {
            val p = parse("[c4 d4 e4]")
            stackOnto(p, atomByValue(p, "d4")!!.id, "n6") shouldBe "[c4 [d4,n6] e4]"
        }

        "stackOnto: extend stack at start of pattern" {
            val p = parse("[c4,e4] g4")
            stackOnto(p, stacks(p).first().id, "n9") shouldBe "[c4,e4,n9] g4"
        }

        "stackOnto: a inside alternation" {
            val p = parse("<a [c d] [e f]>")
            stackOnto(p, atomByValue(p, "a")!!.id, "n0") shouldBe "<[a,n0] [c d] [e f]>"
        }

        "stackOnto: c inside nested group" {
            val p = parse("<a [c d] [e f]>")
            stackOnto(p, atomByValue(p, "c")!!.id, "n0") shouldBe "<a [[c,n0] d] [e f]>"
        }

        "stackOnto: d inside nested group" {
            val p = parse("<a [c d] [e f]>")
            stackOnto(p, atomByValue(p, "d")!!.id, "n0") shouldBe "<a [c [d,n0]] [e f]>"
        }

        "stackOnto: e inside second nested group" {
            val p = parse("<a [c d] [e f]>")
            stackOnto(p, atomByValue(p, "e")!!.id, "n0") shouldBe "<a [c d] [[e,n0] f]>"
        }

        "stackOnto: f inside second nested group" {
            val p = parse("<a [c d] [e f]>")
            stackOnto(p, atomByValue(p, "f")!!.id, "n0") shouldBe "<a [c d] [e [f,n0]]>"
        }

        // ── Round-trip sanity ───────────────────────────────────────────────

        "round-trip: pattern is parseable after each operation" {
            var p = parse("c4 d4 e4 f4")

            // insert between c4 and d4 (at index 1 in pattern)
            var text = insertChild(p, p.id, 1, "n1")
            p = parse(text)
            p shouldNotBe null
            atoms(p) shouldHaveSize 5

            // remove the newly inserted atom
            val inserted = atomByValue(p, "n1")!!
            p = p.removeById(inserted.id) as MnPattern
            atoms(p) shouldHaveSize 4

            // stack d4
            text = stackOnto(p, atomByValue(p, "d4")!!.id, "n3")
            p = parse(text)
            stacks(p) shouldHaveSize 1

            // extend the stack
            text = stackOnto(p, stacks(p).first().id, "n5")
            p = parse(text)

            assertSoftly {
                stacks(p) shouldHaveSize 1
                stacks(p).first().layers.flatten()
                    .filterIsInstance<MnNode.Atom>()
                    .map { it.value } shouldBe listOf("d4", "n3", "n5")
            }
        }

        "round-trip: alternation pattern survives insert" {
            val p = parse("<c4 e4> g4")
            val text = insertChild(p, p.id, p.items.size, "n8")
            text shouldBe "<c4 e4> g4 n8"
            parse(text) shouldNotBe null
        }

        "round-trip: nested group survives stackOnto then extend" {
            var p = parse("c4 [d4 [e4 f4]] g4")
            var text = stackOnto(p, atomByValue(p, "e4")!!.id, "n2")
            text shouldBe "c4 [d4 [[e4,n2] f4]] g4"
            p = parse(text)

            text = stackOnto(p, stacks(p).first().id, "n9")
            text shouldBe "c4 [d4 [[e4,n2,n9] f4]] g4"
        }

        "round-trip: sequence of 5 appends builds correct text" {
            var p = parse("n0")
            repeat(4) { i ->
                val text = insertChild(p, p.id, p.items.size, "n${i + 1}")
                p = parse(text)
            }
            assertSoftly {
                atoms(p) shouldHaveSize 5
                atoms(p).map { it.value } shouldBe listOf("n0", "n1", "n2", "n3", "n4")
            }
        }

        // ── Comprehensive: deeply nested pattern ────────────────────────────
        //
        // Pattern: <[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>

        "deep: insert before alternation (top level)" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            insertChild(p, p.id, 0, "n0") shouldBe "n0 <[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert at start of alternation" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 0, "n0") shouldBe "<n0 [0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside first group before stack" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp0 = alt.items[0] as MnNode.Group
            insertChild(p, grp0.id, 0, "n0") shouldBe "<[n0 [0,7,12]] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: add layer to first stack" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            stackOnto(p, stacks(p)[0].id, "n0") shouldBe "<[0,7,12,n0] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert after first group inside alternation" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 1, "n0") shouldBe "<[0,7,12] n0 [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert between second and third group" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 2, "n0") shouldBe "<[0,7,12] [12,19,24] n0 [[1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside third outer group before first inner" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            insertChild(p, grp2.id, 0, "n0") shouldBe "<[0,7,12] [12,19,24] [n0 [1,8,13] [1,8,13]] [[1,8,13]]>"
        }

        "deep: insert inside first inner group of third" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            val innerGrp0 = grp2.items[0] as MnNode.Group
            insertChild(p, innerGrp0.id, 0, "n0") shouldBe "<[0,7,12] [12,19,24] [[n0 [1,8,13]] [1,8,13]] [[1,8,13]]>"
        }

        "deep: add layer to first inner stack of third group" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            stackOnto(p, stacks(p)[2].id, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13,n0] [1,8,13]] [[1,8,13]]>"
        }

        "deep: between two inner groups of third" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            insertChild(p, grp2.id, 1, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13] n0 [1,8,13]] [[1,8,13]]>"
        }

        "deep: between third and fourth group" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            insertChild(p, alt.id, 3, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] n0 [[1,8,13]]>"
        }

        "deep: add layer to fourth stack" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            stackOnto(p, stacks(p)[4].id, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13,n0]]>"
        }

        "deep: after alternation at top level" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            insertChild(p, p.id, p.items.size, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]> n0"
        }

        // ── Sequential insert after stack inside group ──────────────────────

        "insertAt: after stack inside group" {
            val p = parse("<[0,7,12] [12,19,24]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp0 = alt.items[0] as MnNode.Group
            insertChild(p, grp0.id, 1, "n0") shouldBe "<[[0,7,12] n0] [12,19,24]>"
        }

        "insertAt: after inner group inside outer" {
            val p = parse("<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>")
            val alt = p.items[0] as MnNode.Alternation
            val grp2 = alt.items[2] as MnNode.Group
            insertChild(p, grp2.id, grp2.items.size, "n0") shouldBe "<[0,7,12] [12,19,24] [[1,8,13] [1,8,13] n0] [[1,8,13]]>"
        }

        // ── groupInsertAt: Stack wrapping ───────────────────────────────────

        "groupInsertAt: insert before stack wraps it in sub-group" {
            val p = parse("[0,7,12]")
            val group = p.items[0] as MnNode.Group
            insertChild(p, group.id, 0, "x") shouldBe "[x [0,7,12]]"
        }

        "groupInsertAt: insert after stack wraps it in sub-group" {
            val p = parse("[0,7,12]")
            val group = p.items[0] as MnNode.Group
            insertChild(p, group.id, 1, "x") shouldBe "[[0,7,12] x]"
        }

        "groupInsertAt: insert into group without stacks does not wrap" {
            val p = parse("[a b c]")
            val group = p.items[0] as MnNode.Group
            insertChild(p, group.id, 1, "x") shouldBe "[a x b c]"
        }

        "groupInsertAt: <[0,7,12]> insert before stack inside group" {
            val p = parse("<[0,7,12]>")
            val alt = p.items[0] as MnNode.Alternation
            val group = alt.items[0] as MnNode.Group
            insertChild(p, group.id, 0, "x") shouldBe "<[x [0,7,12]]>"
        }

        // ── normalizeGroups: unwrapping ─────────────────────────────────────

        "normalizeGroups: [[0,7,12]] collapses to [0,7,12]" {
            val p = parse("[[0,7,12]]")
            render(MnNodeOps.normalizeGroups(p)) shouldBe "[0,7,12]"
        }

        "normalizeGroups: [a [0,7,12]] stays unchanged" {
            val p = parse("[a [0,7,12]]")
            render(MnNodeOps.normalizeGroups(p)) shouldBe "[a [0,7,12]]"
        }

        "normalizeGroups: <[[0,7,12]]> collapses inner group" {
            val p = parse("<[[0,7,12]]>")
            render(MnNodeOps.normalizeGroups(p)) shouldBe "<[0,7,12]>"
        }

        "normalizeGroups: deeply nested [[1,8,13]] inside group collapses" {
            val p = parse("[a [[1,8,13]]]")
            render(MnNodeOps.normalizeGroups(p)) shouldBe "[a [1,8,13]]"
        }

        // ── Round-trip: insert then remove restores original ────────────────

        "round-trip: insert before stack then remove restores [0,7,12]" {
            val p = parse("[0,7,12]")
            val group = p.items[0] as MnNode.Group

            // Insert → [x [0,7,12]]
            val inserted = insertChild(p, group.id, 0, "x")
            inserted shouldBe "[x [0,7,12]]"

            // Remove x → normalize → [0,7,12]
            val p2 = parse(inserted)
            val x = atomByValue(p2, "x")!!
            removeAndNormalize(p2, x.id) shouldBe "[0,7,12]"
        }

        "round-trip: insert after stack then remove restores [0,7,12]" {
            val p = parse("[0,7,12]")
            val group = p.items[0] as MnNode.Group

            // Insert → [[0,7,12] x]
            val inserted = insertChild(p, group.id, 1, "x")
            inserted shouldBe "[[0,7,12] x]"

            // Remove x → normalize → [0,7,12]
            val p2 = parse(inserted)
            val x = atomByValue(p2, "x")!!
            removeAndNormalize(p2, x.id) shouldBe "[0,7,12]"
        }

        "round-trip: <[0,7,12]> insert then remove restores original" {
            val p = parse("<[0,7,12]>")
            val alt = p.items[0] as MnNode.Alternation
            val group = alt.items[0] as MnNode.Group

            // Insert → <[x [0,7,12]]>
            val inserted = insertChild(p, group.id, 0, "x")
            inserted shouldBe "<[x [0,7,12]]>"

            // Remove x → normalize → <[0,7,12]>
            val p2 = parse(inserted)
            val x = atomByValue(p2, "x")!!
            removeAndNormalize(p2, x.id) shouldBe "<[0,7,12]>"
        }

        // ── normalizeGroups: Stack normalization ────────────────────────────

        "normalizeGroups: [a,b] remove a → normalize → [b]" {
            val p = parse("[a,b]")
            val a = atomByValue(p, "a")!!
            removeAndNormalize(p, a.id) shouldBe "[b]"
        }

        "normalizeGroups: [a,b] remove b → normalize → [a]" {
            val p = parse("[a,b]")
            val b = atomByValue(p, "b")!!
            removeAndNormalize(p, b.id) shouldBe "[a]"
        }

        "normalizeGroups: [a,b,c] remove a → normalize → [b,c]" {
            val p = parse("[a,b,c]")
            val a = atomByValue(p, "a")!!
            removeAndNormalize(p, a.id) shouldBe "[b,c]"
        }

        "normalizeGroups: c [a,b] d remove a → normalize → c [b] d" {
            val p = parse("c [a,b] d")
            val a = atomByValue(p, "a")!!
            removeAndNormalize(p, a.id) shouldBe "c [b] d"
        }

        // ── StackOnto: adding layer to existing stack ───────────────────────

        "stackOnto: add layer to stack via Stack node" {
            val p = parse("[0,7,12]")
            val stack = stacks(p).first()
            stackOnto(p, stack.id, "n0") shouldBe "[0,7,12,n0]"
        }

        "stackOnto: <[0,7,12]> add layer to stack" {
            val p = parse("<[0,7,12]>")
            val stack = stacks(p).first()
            stackOnto(p, stack.id, "n0") shouldBe "<[0,7,12,n0]>"
        }
    }
}
