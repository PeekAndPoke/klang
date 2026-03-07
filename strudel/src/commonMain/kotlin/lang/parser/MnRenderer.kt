package io.peekandpoke.klang.strudel.lang.parser

/**
 * Serialises an [MnPattern] back to a canonical mini-notation string.
 *
 * Round-trip invariant:
 * ```
 * MiniNotationParser(render(pattern)).parse() == pattern
 * ```
 * (modulo insignificant whitespace)
 */
object MnRenderer {

    fun render(pattern: MnPattern): String = renderLayer(pattern.items)

    // ── Layer ─────────────────────────────────────────────────────────────

    private fun renderLayer(nodes: List<MnNode>): String = buildString {
        var needSpace = false
        for (node in nodes) {
            if (node is MnNode.Linebreak) {
                append("\n")
                needSpace = false
            } else {
                if (needSpace) append(" ")
                append(renderNode(node))
                needSpace = true
            }
        }
    }

    // ── Node ──────────────────────────────────────────────────────────────

    fun renderNode(node: MnNode): String = when (node) {
        is MnNode.Atom -> node.value + renderMods(node.mods)

        is MnNode.Group -> {
            val inner = renderLayer(node.items)
            "[${inner}]${renderMods(node.mods)}"
        }

        is MnNode.Alternation -> {
            val inner = node.items.joinToString(" ") { renderNode(it) }
            "<${inner}>${renderMods(node.mods)}"
        }

        is MnNode.Choice -> {
            // Choices may not have mods themselves (mods sit on the individual options).
            // If the Choice has mods we wrap it in a group to apply them.
            val inner = node.options.joinToString(" | ") { renderNode(it) }
            if (node.mods.isEmpty) inner else "[${inner}]${renderMods(node.mods)}"
        }

        is MnNode.Stack -> {
            // Stack never owns brackets — the surrounding context (Group or MnPattern) provides them.
            // Layers are joined with ", "; each layer is a space-separated sequence.
            node.layers.joinToString(", ") { layer -> renderLayer(layer) } + renderMods(node.mods)
        }

        is MnNode.Repeat -> renderNode(node.node) + "!${node.count}" + renderMods(node.mods)

        is MnNode.Rest -> "~"

        is MnNode.Linebreak -> "\n"
    }

    // ── Mods ──────────────────────────────────────────────────────────────

    private fun renderMods(mods: MnNode.Mods): String {
        if (mods.isEmpty) return ""
        return buildString {
            // Order matches phase-2 application: euclidean → multiplier → divisor → probability → weight
            mods.euclidean?.let { e ->
                append(
                    if (e.rotation != 0) "(${e.pulses},${e.steps},${e.rotation})"
                    else "(${e.pulses},${e.steps})"
                )
            }
            mods.multiplier?.let { append("*${renderNumber(it)}") }
            mods.divisor?.let { append("/${renderNumber(it)}") }
            mods.probability?.let { append("?${renderNumber(it)}") }
            mods.weight?.let { append("@${renderNumber(it)}") }
        }
    }

    // ── Number formatting ─────────────────────────────────────────────────

    /**
     * Renders a [Double] without a trailing `.0` when the value is a whole number.
     * E.g. `2.0` → `"2"`, `0.5` → `"0.5"`.
     */
    private fun renderNumber(d: Double): String =
        if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
}
