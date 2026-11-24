/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.parser

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.applyTweaks
import io.peekandpoke.klang.sprudel.lang.fast
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.silence
import io.peekandpoke.klang.sprudel.lang.slow
import io.peekandpoke.klang.sprudel.lang.stack
import io.peekandpoke.klang.sprudel.pattern.AlternationPattern
import io.peekandpoke.klang.sprudel.pattern.ChoicePattern.Companion.choice
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.DegradePattern
import io.peekandpoke.klang.sprudel.pattern.EuclideanPattern
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern

/**
 * Phase 2: converts an [MnPattern] intermediate AST into a [SprudelPattern].
 *
 * Mirrors the rules of the original [MiniNotationParser] but operates on the
 * pre-built [MnNode] tree rather than inline during parsing.
 *
 * Modifier application order (matches [MnNode.Mods] contract):
 * euclidean → multiplier → divisor → probability → weight
 */
object MnPatternToSprudelPattern {

    /**
     * The `?` marks of one conversion (one notation string), counted as they are met: each gets a random value of its
     * own (the maintainer, 2026-11-24: "every ? should get its own random value"). Before, every `?` read the one signal `rand` at its
     * step's own time, which for every step of a sequence is the cycle's start: `[hh?!8]` and `hh? hh? hh?` dropped
     * all their steps or none. A mark's value is told by the seed, the time and the mark's place in the notation
     * (the copies of a `!` each count), so the same notation under the same seed gives the same events again.
     *
     * The number after a mark is the probability of being played, 0.5 where there is none (the maintainer,
     * 2026-11-24: "it would be better if the number after the ? is the probability of being played defaulting to
     * 0.5"): `a?0.3` plays about three times in ten. It was the share dropped before; `degradeBy(0.3)`, the function,
     * still drops that share.
     */
    private class QuestionMarks {
        private var met = 0

        /** How far two marks' numbers lie apart (an odd number that scatters well: 2^32 over the golden ratio). */
        private val step = -0x61c88647

        /** What the time's hash is spread by: another wide odd number. */
        private val timeStep = -0x7a143595

        /**
         * [pattern] with each of its events played with the [probability] given (0 never, 1 always) and dropped
         * otherwise, by the dice of the next mark.
         */
        fun degrade(pattern: SprudelPattern, probability: Double): SprudelPattern {
            // (the mark's own number, spread wide: added to the time's hash, never multiplied with it. A product
            // is 0 for every mark where the time's hash is 0, which it is in cycle 0)
            val mark = (met++ + 1) * step
            // (the time's hash spread wide too: in the browser a whole cycle's hash is its number, and a small factor
            // would give a seed that much higher the dice of the next cycle)
            val dice = ContinuousPattern { from, _, ctx -> ctx.getSeededRandom(from.hashCode() * timeStep + mark).nextDouble() }

            // kept where the dice (0 up to, not including, 1) is at or above what is left of 1: so `?1` keeps all
            // and `?0` none.
            // NaN-guard: a probability of no number keeps nothing (no dice is at or above it)
            return DegradePattern(source = pattern, randomness = dice, threshold = 1.0 - probability, keepStrictlyAbove = false)
        }
    }

    fun convert(
        pattern: MnPattern,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        if (pattern.items.isEmpty()) return silence
        return layerToPattern(pattern.items, baseLocation, atomFactory, QuestionMarks())
    }

    // ── Layer / sequence ──────────────────────────────────────────────────

    private fun layerToPattern(
        nodes: List<MnNode>,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        // Linebreaks are visual-only — ignore them during pattern conversion.
        // Expand Repeat nodes that carry no mods — they contribute multiple flat sequence items.
        val flat = nodes.filter { it !is MnNode.Linebreak }.flatMap { expandRepeat(it) }
        return when {
            flat.isEmpty() -> silence
            flat.size == 1 -> nodeToPattern(flat[0], baseLocation, atomFactory, marks)
            else -> seq(*flat.map { nodeToPattern(it, baseLocation, atomFactory, marks) }.toTypedArray())
        }
    }

    /** Expands a [MnNode.Repeat] with empty mods into its constituent copies; leaves other nodes as-is. */
    private fun expandRepeat(node: MnNode): List<MnNode> =
        if (node is MnNode.Repeat && node.mods.isEmpty) {
            List(node.count) { node.node }
        } else {
            listOf(node)
        }

    // ── Node → pattern ────────────────────────────────────────────────────

    private fun nodeToPattern(
        node: MnNode,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern = when (node) {
        is MnPattern -> layerToPattern(node.items, baseLocation, atomFactory, marks)
        is MnNode.Atom -> atomToPattern(node, baseLocation, atomFactory, marks)
        is MnNode.Group -> groupToPattern(node, baseLocation, atomFactory, marks)
        is MnNode.Alternation -> alternationToPattern(node, baseLocation, atomFactory, marks)
        is MnNode.Choice -> choiceToPattern(node, baseLocation, atomFactory, marks)
        is MnNode.Stack -> stackToPattern(node, baseLocation, atomFactory, marks)
        is MnNode.Repeat -> repeatToPattern(node, baseLocation, atomFactory, marks)
        // Rests keep their mods — `~@3` is a weighted rest, standard notation for timing gaps.
        is MnNode.Rest -> applyMods(silence, node.mods, marks)
        is MnNode.Linebreak -> silence
    }

    // ── Atom ──────────────────────────────────────────────────────────────

    private fun atomToPattern(
        node: MnNode.Atom,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        val location = node.sourceRange?.toLocation(baseLocation, node.sourceLine, node.sourceColumn)
        val locationChain = location?.let { SourceLocationChain.single(it) }
        val base = atomFactory(node.value, locationChain)
        return applyMods(base, node.mods, marks)
    }

    // ── Group ─────────────────────────────────────────────────────────────

    private fun groupToPattern(
        node: MnNode.Group,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        val base = if (node.items.isEmpty()) {
            silence
        } else {
            layerToPattern(node.items, baseLocation, atomFactory, marks)
        }
        return applyMods(base, node.mods, marks)
    }

    // ── Alternation ───────────────────────────────────────────────────────

    private fun alternationToPattern(
        node: MnNode.Alternation,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        if (node.items.isEmpty()) return silence
        // Expand bare Repeat nodes so <bd!2 sd> still means 3 alternation slots; strip Linebreaks
        val flat = node.items.filter { it !is MnNode.Linebreak }.flatMap { expandRepeat(it) }
        val items = flat.map { nodeToPattern(it, baseLocation, atomFactory, marks) }
        // <a b c>: each item takes one full cycle, cycling every n cycles. Implemented with exact
        // integer-cycle selection (AlternationPattern) rather than the old `seq(items).slow(n)`, which
        // rounded cycle boundaries and dropped/misselected items whenever n did not divide CycleTime.T.
        val base = AlternationPattern(items)
        return applyMods(base, node.mods, marks)
    }

    // ── Repeat ────────────────────────────────────────────────────────────────

    private fun repeatToPattern(
        node: MnNode.Repeat,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        // Repeat with mods: expand copies and apply mods to the resulting sequence
        val copies = List(node.count) { nodeToPattern(node.node, baseLocation, atomFactory, marks) }
        val base = if (copies.size == 1) copies[0] else seq(*copies.toTypedArray())
        return applyMods(base, node.mods, marks)
    }

    // ── Stack ─────────────────────────────────────────────────────────────

    private fun stackToPattern(
        node: MnNode.Stack,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        val base = when {
            node.layers.isEmpty() -> silence
            node.layers.size == 1 -> layerToPattern(node.layers[0], baseLocation, atomFactory, marks)
            else -> {
                val layers = node.layers.map { layerToPattern(it, baseLocation, atomFactory, marks) }
                stack(*layers.toTypedArray())
            }
        }
        return applyMods(base, node.mods, marks)
    }

    // ── Choice ────────────────────────────────────────────────────────────

    private fun choiceToPattern(
        node: MnNode.Choice,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
        marks: QuestionMarks,
    ): SprudelPattern {
        if (node.options.isEmpty()) return silence
        val options = node.options.map { nodeToPattern(it, baseLocation, atomFactory, marks) }
        val base = options.drop(1).fold(options[0]) { acc, opt -> acc.choice(opt) }
        return applyMods(base, node.mods, marks)
    }

    // ── Modifier application ──────────────────────────────────────────────

    /**
     * Applies [MnNode.Mods] to a [SprudelPattern] in the canonical order:
     * euclidean → multiplier → divisor → probability → weight → tweaks
     */
    private fun applyMods(pattern: SprudelPattern, mods: MnNode.Mods, marks: QuestionMarks): SprudelPattern {
        if (mods.isEmpty) return pattern

        var result = pattern

        mods.euclidean?.let { e ->
            result = EuclideanPattern.create(inner = result, pulses = e.pulses, steps = e.steps, rotation = e.rotation)
        }
        mods.multiplier?.let { result = result.fast(it) }
        mods.divisor?.let { result = result.slow(it) }
        mods.probability?.let { result = marks.degrade(pattern = result, probability = it) }
        mods.weight?.let { result = PropertyOverridePattern(result, weightOverride = it) }

        // Only the NAMES are attached here. The transform each one refers to is bound later by
        // `tweaks(…)`, so an unbound name rides along inert rather than failing.
        result = applyTweaks(result, mods.tweaks)

        return result
    }

    // ── Source location helper ────────────────────────────────────────────

    /**
     * Converts a character-offset [IntRange] within a mini-notation string into a [SourceLocation].
     *
     * [line] and [col] are the 1-based line/column within the mini-notation string, as tracked
     * by the tokeniser. When [line] == 1 the column is offset by [base].startColumn; for
     * subsequent lines the column is used directly.
     */
    private fun IntRange.toLocation(base: SourceLocation?, line: Int, col: Int?): SourceLocation? {
        if (base == null) return null
        val start = first           // 0-based char offset
        val end = last + 1          // exclusive
        val column = col ?: (start + 1) // 1-based column within its line
        val absoluteStartLine = base.startLine + line - 1
        val absoluteStartColumn = if (line == 1) base.startColumn + column else column
        val absoluteEndLine = absoluteStartLine
        val absoluteEndColumn = absoluteStartColumn + (end - start)
        return SourceLocation(
            source = base.source,
            startLine = absoluteStartLine,
            startColumn = absoluteStartColumn,
            endLine = absoluteEndLine,
            endColumn = absoluteEndColumn,
        )
    }
}
