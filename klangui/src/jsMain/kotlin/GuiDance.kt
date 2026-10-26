/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The Gui-dance, a dance through the GUI (KLANG-00350, the first-visit trail of KLANG-00334): story [arcs] of steps,
 * one gold "try me" lamp at a time. Only the current step of the current arc is lit ([lit]); using it moves the arc
 * on ([used]), and using anything else counts for nothing. An arc is remembered as done ([Store]) only when its last
 * step is used: a page left in the middle of an arc starts it over on the next visit (the maintainer, 2026-10-26).
 * The arcs run in their order; a step is an id, not a place, so a later arc may light the same thing again.
 *
 * Plain state, no UI (the epic's item 12): the components only ask whether their step is lit and report when they
 * were used.
 */
class GuiDance(
    val arcs: List<Arc>,
    private val store: Store,
) {
    /** A story arc: its [id], kept in the [Store] once done, and its [steps] in order. */
    data class Arc(val id: String, val steps: List<String>) {
        init {
            require(steps.isNotEmpty()) { "The arc $id has no steps" }
        }
    }

    /** Where the done arcs are kept between visits. */
    interface Store {
        /** The ids of the arcs done; empty when nothing is kept, or the storage cannot be read. */
        fun loadDone(): Set<String>

        fun saveDone(done: Set<String>)
    }

    /** A store that forgets on reload: for tests, and where nothing can be kept. */
    class MemoryStore(private var done: Set<String> = emptySet()) : Store {
        override fun loadDone(): Set<String> = done

        override fun saveDone(done: Set<String>) {
            this.done = done
        }
    }

    private var done: Set<String> = store.loadDone()

    /** The step of the current arc that is lit; back to its start on every visit. */
    private var stepIndex = 0

    private val litSource = StreamSource(currentStep())

    /** The step lit now: the current arc's current step, or null once every arc is done. */
    val lit: Stream<String?> = litSource

    /** The arc being danced now, or null once every arc is done. */
    fun currentArc(): Arc? = arcs.firstOrNull { it.id !in done }

    /**
     * The user used [step]. When it is the lit one, the arc moves on, and after its last step it is kept as done and
     * the next arc lights its first step. Any other step changes nothing.
     */
    fun used(step: String) {
        val arc = currentArc() ?: return

        if (arc.steps[stepIndex] != step) {
            return
        }

        stepIndex++

        if (stepIndex == arc.steps.size) {
            done = done + arc.id
            stepIndex = 0
            store.saveDone(done)
        }

        litSource(currentStep())
    }

    private fun currentStep(): String? = currentArc()?.steps?.get(stepIndex)
}
