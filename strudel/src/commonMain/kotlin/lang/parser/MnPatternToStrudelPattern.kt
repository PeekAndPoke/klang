package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.pattern.ChoicePattern.Companion.choice
import io.peekandpoke.klang.strudel.pattern.EuclideanPattern
import io.peekandpoke.klang.strudel.pattern.PropertyOverridePattern

/**
 * Phase 2: converts an [MnPattern] intermediate AST into a [StrudelPattern].
 *
 * Mirrors the rules of the original [MiniNotationParser] but operates on the
 * pre-built [MnNode] tree rather than inline during parsing.
 *
 * Modifier application order (matches [MnNode.Mods] contract):
 * euclidean → multiplier → divisor → probability → weight
 */
object MnPatternToStrudelPattern {

    fun convert(
        pattern: MnPattern,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
        if (pattern.items.isEmpty()) return silence
        return layerToPattern(pattern.items, baseLocation, atomFactory)
    }

    // ── Layer / sequence ──────────────────────────────────────────────────

    private fun layerToPattern(
        nodes: List<MnNode>,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
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
        if (node is MnNode.Repeat && node.mods.isEmpty) List(node.count) { node.node }
        else listOf(node)

    // ── Node → pattern ────────────────────────────────────────────────────

    private fun nodeToPattern(
        node: MnNode,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern = when (node) {
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
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
        val location = node.sourceRange?.toLocation(baseLocation, node.sourceLine, node.sourceColumn)
        val locationChain = location?.let { SourceLocationChain.single(it) }
        val base = atomFactory(node.value, locationChain)
        return applyMods(base, node.mods)
    }

    // ── Group ─────────────────────────────────────────────────────────────

    private fun groupToPattern(
        node: MnNode.Group,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
        val base = if (node.items.isEmpty()) silence
        else layerToPattern(node.items, baseLocation, atomFactory)
        return applyMods(base, node.mods)
    }

    // ── Alternation ───────────────────────────────────────────────────────

    private fun alternationToPattern(
        node: MnNode.Alternation,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
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
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
        // Repeat with mods: expand copies and apply mods to the resulting sequence
        val copies = List(node.count) { nodeToPattern(node.node, baseLocation, atomFactory) }
        val base = if (copies.size == 1) copies[0] else seq(*copies.toTypedArray())
        return applyMods(base, node.mods)
    }

    // ── Stack ─────────────────────────────────────────────────────────────

    private fun stackToPattern(
        node: MnNode.Stack,
        baseLocation: SourceLocation?,
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
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
        atomFactory: (String, SourceLocationChain?) -> StrudelPattern,
    ): StrudelPattern {
        if (node.options.isEmpty()) return silence
        val options = node.options.map { nodeToPattern(it, baseLocation, atomFactory) }
        val base = options.drop(1).fold(options[0]) { acc, opt -> acc.choice(opt) }
        return applyMods(base, node.mods)
    }

    // ── Modifier application ──────────────────────────────────────────────

    /**
     * Applies [MnNode.Mods] to a [StrudelPattern] in the canonical order:
     * euclidean → multiplier → divisor → probability → weight
     */
    private fun applyMods(pattern: StrudelPattern, mods: MnNode.Mods): StrudelPattern {
        if (mods.isEmpty) return pattern

        var result = pattern

        mods.euclidean?.let { e ->
            result = EuclideanPattern.create(inner = result, pulses = e.pulses, steps = e.steps, rotation = e.rotation)
        }
        mods.multiplier?.let { result = result.fast(it) }
        mods.divisor?.let { result = result.slow(it) }
        mods.probability?.let { result = result.degradeBy(it) }
        mods.weight?.let { result = PropertyOverridePattern(result, weightOverride = it) }

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
