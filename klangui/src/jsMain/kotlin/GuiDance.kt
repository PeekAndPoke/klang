/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlin.js.Date

/**
 * The Gui-dance, a dance through the GUI (KLANG-00350, the first-visit trail of KLANG-00334): story [arcs] of steps,
 * the walkthroughs, one gold "try me" lamp at a time. Only the current step of the current arc is lit ([lit]); using it moves
 * the arc on ([used]), and using anything else counts for nothing. An arc is remembered as done ([Store]) only when
 * its last step is used: a page left in the middle of an arc starts it over on the next visit (the maintainer,
 * 2026-10-26). The arcs run in their order; a step is an id, not a place, so a later arc may light the same thing
 * again.
 *
 * The user may mute the walkthroughs for a while ([mute]): nothing is lit until then, the arcs keep where they are. The
 * mute ends by itself; whoever keeps the clock calls [refresh] then, so the lamp comes back.
 *
 * Plain state, no UI (the epic's item 12): the components only ask whether their step is lit and report when they
 * were used. [now] is the clock, in milliseconds, for the mute. [onArcDone] hears of each arc finished, after it is
 * kept: the app rests the walkthroughs a while there.
 */
class GuiDance(
    val arcs: List<Arc>,
    private val store: Store,
    private val now: () -> Double = { Date.now() },
    private val onArcDone: (Arc) -> Unit = {},
) {
    /** A story arc, a walkthrough: its [id], kept in the [Store] once done, its [title] for the user, its [steps] in order. */
    data class Arc(val id: String, val title: String, val steps: List<String>) {
        init {
            require(steps.isNotEmpty()) { "The arc $id has no steps" }
        }
    }

    /** Where the done arcs and the mute are kept between visits. */
    interface Store {
        /** The ids of the arcs done; empty when nothing is kept, or the storage cannot be read. */
        fun loadDone(): Set<String>

        fun saveDone(done: Set<String>)

        /** Until when the walkthroughs are muted, in milliseconds since the epoch; 0 for not muted. */
        fun loadMutedUntil(): Double

        fun saveMutedUntil(until: Double)
    }

    /** A store that forgets on reload: for tests, and where nothing can be kept. */
    class MemoryStore(
        private var done: Set<String> = emptySet(),
        private var mutedUntil: Double = 0.0,
    ) : Store {
        override fun loadDone(): Set<String> = done

        override fun saveDone(done: Set<String>) {
            this.done = done
        }

        override fun loadMutedUntil(): Double = mutedUntil

        override fun saveMutedUntil(until: Double) {
            mutedUntil = until
        }
    }

    private var done: Set<String> = store.loadDone()

    private var mutedUntil: Double = store.loadMutedUntil()

    /** The step of the current arc that is lit; back to its start on every visit. */
    private var stepIndex = 0

    /** An arc started out of its order ([start]): the current one until it is done; null for the order. */
    private var started: String? = null

    private val litSource = StreamSource(litStep())

    /** The step lit now: the current arc's current step; null while muted, or once every arc is done. */
    val lit: Stream<String?> = litSource

    /** The arc being danced now: one [start]ed, else the first not done in the order; null once every arc is done. */
    fun currentArc(): Arc? =
        started?.let { id -> arcs.firstOrNull { it.id == id && it.id !in done } } ?: arcs.firstOrNull { it.id !in done }

    /** The arcs done, in their order. */
    fun doneArcs(): List<Arc> = arcs.filter { it.id in done }

    /** How many arcs are still to come, the current one with them. */
    fun remaining(): Int = arcs.count { it.id !in done }

    /** Until when the walkthroughs are muted, in milliseconds since the epoch; null when they are not. */
    fun mutedUntil(): Double? = mutedUntil.takeIf { it > now() }

    /**
     * The user used [step]. When it is the lit one, the arc moves on, and after its last step it is kept as done and
     * the next arc lights its first step. Any other step changes nothing, and nothing counts while muted.
     */
    fun used(step: String) {
        if (litSource() != step) {
            return
        }

        val arc = currentArc() ?: return

        stepIndex++

        if (stepIndex == arc.steps.size) {
            done = done + arc.id
            stepIndex = 0
            // A started arc done, the order goes on
            started = null
            store.saveDone(done)
            onArcDone(arc)
        }

        litSource(litStep())
    }

    /**
     * Takes the arc [arcId] back from the done ones, to dance it again (the maintainer, 2026-10-27: "When i click one
     * of the completed tours uncomplete it"). It comes back in its place in the order: an arc before the current one
     * becomes the current one, from its first step.
     */
    fun reopen(arcId: String) {
        if (arcId !in done) {
            return
        }

        val before = currentArc()

        done = done - arcId
        store.saveDone(done)

        if (currentArc() != before) {
            stepIndex = 0
        }

        litSource(litStep())
    }

    /**
     * Marks the arc [arcId] done without dancing it (the maintainer, 2026-10-29: a walkthrough's card links
     * "Complete / Reopen"): the next arc in the order lights up when it was the current one. A done or unknown arc
     * changes nothing.
     */
    fun complete(arcId: String) {
        val arc = arcs.firstOrNull { it.id == arcId } ?: return

        if (arc.id in done) {
            return
        }

        val before = currentArc()

        done = done + arc.id
        store.saveDone(done)

        if (started == arc.id) {
            started = null
        }

        if (currentArc() != before) {
            stepIndex = 0
        }

        litSource(litStep())
    }

    /**
     * Starts the arc [arcId] now, from its first step, done or to come, wherever it stands in the order (the maintainer,
     * 2026-10-29: "add a "start tour" button ... to each tour"). It is the current arc until it is done; then the order
     * goes on. A mute is the caller's to lift. An unknown id changes nothing.
     */
    fun start(arcId: String) {
        val arc = arcs.firstOrNull { it.id == arcId } ?: return

        if (arc.id in done) {
            done = done - arc.id
            store.saveDone(done)
        }

        started = arc.id
        stepIndex = 0

        litSource(litStep())
    }

    /** Mutes the walkthroughs for [forMs] milliseconds from now: nothing is lit until then. */
    fun mute(forMs: Double) {
        mutedUntil = now() + forMs
        store.saveMutedUntil(mutedUntil)
        litSource(litStep())
    }

    /** Ends the mute now. */
    fun unmute() {
        mutedUntil = 0.0
        store.saveMutedUntil(0.0)
        litSource(litStep())
    }

    /** Looks at the clock again: a mute that ran out lights the current step again. */
    fun refresh() {
        litSource(litStep())
    }

    private fun litStep(): String? = if (mutedUntil() != null) null else currentArc()?.steps?.get(stepIndex)
}
