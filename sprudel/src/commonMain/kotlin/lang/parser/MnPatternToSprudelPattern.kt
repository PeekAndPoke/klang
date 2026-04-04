package io.peekandpoke.klang.sprudel.lang.parser

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.adsr
import io.peekandpoke.klang.sprudel.lang.bank
import io.peekandpoke.klang.sprudel.lang.degradeBy
import io.peekandpoke.klang.sprudel.lang.fast
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.legato
import io.peekandpoke.klang.sprudel.lang.orbit
import io.peekandpoke.klang.sprudel.lang.pan
import io.peekandpoke.klang.sprudel.lang.postgain
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.silence
import io.peekandpoke.klang.sprudel.lang.slow
import io.peekandpoke.klang.sprudel.lang.stack
import io.peekandpoke.klang.sprudel.lang.velocity
import io.peekandpoke.klang.sprudel.pattern.ChoicePattern.Companion.choice
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

    fun convert(
        pattern: MnPattern,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        if (pattern.items.isEmpty()) return silence
        return layerToPattern(pattern.items, baseLocation, atomFactory)
    }

    // ── Layer / sequence ──────────────────────────────────────────────────

    private fun layerToPattern(
        nodes: List<MnNode>,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        // Linebreaks are visual-only — ignore them during pattern conversion.
        // Expand Repeat nodes that carry no mods — they contribute multiple flat sequence items.
        val flat = nodes.filter { it !is MnNode.Linebreak }.flatMap { expandRepeat(it) }
        return when {
            flat.isEmpty() -> silence
            flat.size == 1 -> nodeToPattern(flat[0], baseLocation, atomFactory)
            else -> seq(*flat.map { nodeToPattern(it, baseLocation, atomFactory) }.toTypedArray())
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
    ): SprudelPattern = when (node) {
        is MnPattern -> layerToPattern(node.items, baseLocation, atomFactory)
        is MnNode.Atom -> atomToPattern(node, baseLocation, atomFactory)
        is MnNode.Group -> groupToPattern(node, baseLocation, atomFactory)
        is MnNode.Alternation -> alternationToPattern(node, baseLocation, atomFactory)
        is MnNode.Choice -> choiceToPattern(node, baseLocation, atomFactory)
        is MnNode.Stack -> stackToPattern(node, baseLocation, atomFactory)
        is MnNode.Repeat -> repeatToPattern(node, baseLocation, atomFactory)
        is MnNode.Rest -> silence
        is MnNode.Linebreak -> silence
    }

    // ── Atom ──────────────────────────────────────────────────────────────

    private fun atomToPattern(
        node: MnNode.Atom,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        val location = node.sourceRange?.toLocation(baseLocation, node.sourceLine, node.sourceColumn)
        val locationChain = location?.let { SourceLocationChain.single(it) }
        val base = atomFactory(node.value, locationChain)
        return applyMods(base, node.mods)
    }

    // ── Group ─────────────────────────────────────────────────────────────

    private fun groupToPattern(
        node: MnNode.Group,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        val base = if (node.items.isEmpty()) {
            silence
        } else {
            layerToPattern(node.items, baseLocation, atomFactory)
        }
        return applyMods(base, node.mods)
    }

    // ── Alternation ───────────────────────────────────────────────────────

    private fun alternationToPattern(
        node: MnNode.Alternation,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        if (node.items.isEmpty()) return silence
        // Expand bare Repeat nodes so <bd!2 sd> still means 3 alternation slots; strip Linebreaks
        val flat = node.items.filter { it !is MnNode.Linebreak }.flatMap { expandRepeat(it) }
        val items = flat.map { nodeToPattern(it, baseLocation, atomFactory) }
        // <a b c> = seq(a, b, c).slow(n) — each item takes one full cycle
        val base = seq(*items.toTypedArray()).slow(items.size.toDouble())
        return applyMods(base, node.mods)
    }

    // ── Repeat ────────────────────────────────────────────────────────────────

    private fun repeatToPattern(
        node: MnNode.Repeat,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        // Repeat with mods: expand copies and apply mods to the resulting sequence
        val copies = List(node.count) { nodeToPattern(node.node, baseLocation, atomFactory) }
        val base = if (copies.size == 1) copies[0] else seq(*copies.toTypedArray())
        return applyMods(base, node.mods)
    }

    // ── Stack ─────────────────────────────────────────────────────────────

    private fun stackToPattern(
        node: MnNode.Stack,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        val base = when {
            node.layers.isEmpty() -> silence
            node.layers.size == 1 -> layerToPattern(node.layers[0], baseLocation, atomFactory)
            else -> {
                val layers = node.layers.map { layerToPattern(it, baseLocation, atomFactory) }
                stack(*layers.toTypedArray())
            }
        }
        return applyMods(base, node.mods)
    }

    // ── Choice ────────────────────────────────────────────────────────────

    private fun choiceToPattern(
        node: MnNode.Choice,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> SprudelPattern,
    ): SprudelPattern {
        if (node.options.isEmpty()) return silence
        val options = node.options.map { nodeToPattern(it, baseLocation, atomFactory) }
        val base = options.drop(1).fold(options[0]) { acc, opt -> acc.choice(opt) }
        return applyMods(base, node.mods)
    }

    // ── Modifier application ──────────────────────────────────────────────

    /**
     * Applies [MnNode.Mods] to a [SprudelPattern] in the canonical order:
     * euclidean → multiplier → divisor → probability → weight → attrs
     */
    private fun applyMods(pattern: SprudelPattern, mods: MnNode.Mods): SprudelPattern {
        if (mods.isEmpty) return pattern

        var result = pattern

        mods.euclidean?.let { e ->
            result = EuclideanPattern.create(inner = result, pulses = e.pulses, steps = e.steps, rotation = e.rotation)
        }
        mods.multiplier?.let { result = result.fast(it) }
        mods.divisor?.let { result = result.slow(it) }
        mods.probability?.let { result = result.degradeBy(it) }
        mods.weight?.let { result = PropertyOverridePattern(result, weightOverride = it) }

        if (!mods.attrs.isEmpty) {
            result = applyAttrs(result, mods.attrs)
        }

        return result
    }

    /**
     * Applies inline `{key=value}` attributes by delegating to the existing DSL functions.
     * This guarantees semantic consistency with the KlangScript DSL.
     */
    private fun applyAttrs(pattern: SprudelPattern, attrs: MnNode.Attrs): SprudelPattern {
        var result = pattern
        for ((key, value) in attrs.entries) {
            result = when (key) {
                "v", "vel", "velocity" -> result.velocity(value)
                "g", "gain" -> result.gain(value)
                "l", "legato" -> result.legato(value)
                "pan" -> result.pan(value)
                "pg", "postgain" -> result.postgain(value)
                "adsr" -> result.adsr(value)
                "o", "orbit", "cyl", "cylinder" -> result.orbit(value)
                "bank" -> result.bank(value)
                else -> result
            }
        }
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
