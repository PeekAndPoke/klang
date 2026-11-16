/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Universal pattern interface for composable music patterns.
 *
 * Every pattern source (Strudel, sequencer, MIDI, generative, etc.) implements this interface.
 * Patterns can be composed across implementations — a Strudel pattern stacked with a sequencer
 * pattern goes through the same scheduling and playback infrastructure.
 *
 * queries patterns for events in cycle ranges and converts them to [ScheduledVoice] instances.
 */
interface KlangPattern {
    /**
     * Query events in the given cycle range.
     *
     * Contract:
     * - Returns only events that should be played (onset filtering is the implementor's responsibility)
     * - Events outside [fromCycles, toCycles) may be excluded
     * - [cps] (cycles per second) is provided for implementations that need tempo context
     * - Events should be returned in start-time order
     * - Any window may be asked, and an implementation answers any. To get the events that are heard, ask one
     *   whole cycle at a time (the maintainer, 2026-11-16, KLANG-00450): the live scheduler asks every one of its
     *   windows that way and cuts an edge that is not on a cycle afterwards, by each event's start. Asked so, the
     *   same cycle gives the same events every time, as long as the pattern reads nothing that moves between two
     *   askings (the wall clock: sprudel's `timeOfDay` and its siblings; the scheduler keeps a cycle's answer for
     *   a while, so there it holds even then). Another window, one that cuts a cycle or spans several, may give
     *   other events for the same cycle: a continuous amount (a `late` by a noise) is read once per asking, from
     *   the window's start.
     */
    fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent>
}
