package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.filter
import kotlinx.browser.window

/**
 * Subscribes to a stream of highlight signals and manages the highlights.
 */
fun <T : Any> Component<T>.highlights(
    stream: Stream<KlangBlocksHighlightBuffer.HighlightSignal?>,
    filter: (KlangBlocksHighlightBuffer.HighlightSignal?) -> Boolean,
): KlangBlockHighlights {
    val highlights = KlangBlockHighlights(::triggerRedraw)
    stream.filter(filter).subscribe(highlights::handle)

    return highlights
}

/**
 * Manages timed atom highlights for a block or slot.
 *
 * Owns the active-atom set and calls [triggerRedraw] whenever it changes,
 * so the owning Component does not need a reactive delegate for this state.
 */
class KlangBlockHighlights(
    private val triggerRedraw: () -> Unit,
) {
    var activeAtoms: Set<KlangBlockAtomKey> = emptySet()
        private set

    private val timeouts = mutableMapOf<KlangBlockAtomKey, Int>()

    fun handle(signal: KlangBlocksHighlightBuffer.HighlightSignal?) {
        signal ?: return
        val key = KlangBlockAtomKey(signal.slotIndex, signal.atomStart, signal.atomEnd)
        timeouts[key]?.let { window.clearTimeout(it) }
        activeAtoms = activeAtoms + key
        triggerRedraw()
        timeouts[key] = window.setTimeout({
            activeAtoms = activeAtoms - key
            timeouts.remove(key)
            triggerRedraw()
        }, signal.durationMs.toInt())
    }
}
