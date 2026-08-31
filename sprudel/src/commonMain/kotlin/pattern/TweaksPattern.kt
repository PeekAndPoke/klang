/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn

/**
 * Binds tweak names to transforms and applies them to the events carrying those names.
 *
 * Tweak names are attached upstream, in mini-notation (`note("c3 e3{swell}")`) or via `.tweak(...)`;
 * this node is where a name finally means something. See `docs/tasks/mini-notation-tweaks.md`.
 *
 * Three semantics this node implements deliberately:
 *
 * - **Written order wins.** Tweaks apply in the order they appear on the event, not in [defs]'
 *   declaration order, and a repeated name applies twice.
 * - **Unbound names pass through untouched**, and are NOT stripped. That is what lets an inner
 *   `tweaks(...)` and an outer one compose without either knowing about the other.
 * - **Applying does not consume the name.** Two nodes binding the same name both apply. Multiplicity
 *   is the author's to control, via `{bend bend}` or a second call.
 *
 * ### Why one node instead of chained filter/stack
 *
 * The obvious shape, `stack(fn(taggedOnly), untagged)`, queries [inner] TWICE, once per branch, so
 * chaining N tweaks costs 2^N queries of the base pattern. This node queries [inner] exactly ONCE
 * and routes per event, which keeps chaining linear. Events with no bound name cost nothing.
 *
 * ### Why the transform sees a pattern, not a voice data
 *
 * A tweak is a [PatternMapperFn], the same transform type as `off` or `superimpose`, so the author
 * writes `x => x.detune(20)` exactly as everywhere else. Each marked event is rebuilt as a one-event
 * pattern, transformed, and squeezed back into its own span (the same mapping [PickSqueezePattern]
 * uses). Voice-data tweaks therefore come out identical to setting the fields directly, and a
 * structural one like `x => x.fast(2)` is well defined as "twice within this note's slot".
 */
internal class TweaksPattern(
    private val inner: SprudelPattern,
    private val defs: Map<String, PatternMapperFn>,
) : SprudelPattern {

    override val weight: Double get() = inner.weight
    override val numSteps: Double? get() = inner.numSteps
    override fun estimateCycleDuration(): Double = inner.estimateCycleDuration()

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val events = inner.queryArcContextual(from, to, ctx)
        if (defs.isEmpty() || events.none { it.hasBoundTweak() }) return events

        val result = mutableListOf<SprudelPatternEvent>()
        for (event in events) {
            if (!event.hasBoundTweak()) {
                result.add(event)
            } else {
                result.addAll(applyTo(event, ctx))
            }
        }
        return result
    }

    private fun SprudelPatternEvent.hasBoundTweak(): Boolean {
        val names = data.tweaks ?: return false
        return names.any { it in defs }
    }

    /**
     * Rebuilds [event] as a one-event pattern spanning a full cycle, runs the composed transform over
     * it, and maps the result back into the event's own [SprudelPatternEvent.whole] span.
     *
     * The result is clipped to the event's original [SprudelPatternEvent.part], so an event that
     * arrived already clipped by the query arc stays clipped. With a voice-data-only transform this
     * round-trips exactly: part and whole come back unchanged.
     */
    private fun applyTo(event: SprudelPatternEvent, ctx: QueryContext): List<SprudelPatternEvent> {
        val duration = event.whole.duration
        if (duration == CycleTime.ZERO) return listOf(event)
        val durCycles = duration.toCycles()

        val transformed = event.data.tweaks.orEmpty()
            .mapNotNull { defs[it] }
            .fold<PatternMapperFn, SprudelPattern>(
                AtomicPattern(data = event.data, sourceLocations = event.sourceLocations),
            ) { pattern, transform -> transform(pattern) }

        // Outer time maps onto the atom's single cycle: whole.begin -> 0, whole.end -> 1.
        val innerFrom = (event.part.begin - event.whole.begin).divBy(durCycles)
        val innerTo = (event.part.end - event.whole.begin).divBy(durCycles)

        return transformed.queryArcContextual(innerFrom, innerTo, ctx).mapNotNull { inner ->
            val part = inner.part.scale(durCycles).shift(event.whole.begin).clipTo(event.part)
                ?: return@mapNotNull null
            inner.copy(
                part = part,
                whole = inner.whole.scale(durCycles).shift(event.whole.begin),
            )
        }
    }
}
