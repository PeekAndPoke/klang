/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/** What a control does when the routing points it there (KLANG-00402, section 4). */
sealed interface PlayFunction {
    /** A drum sound per pad: bd sd hh oh / cp rim lt cr (D2). */
    data class HitPad(val pad: Int) : PlayFunction

    /** Held notes sound on after their key is up, until the sustain is up. */
    data object Sustain : PlayFunction

    /** Every source's notes up, every voice released. */
    data object Panic : PlayFunction

    /** The previous or next instrument of the loadout: [direction] -1 or +1; an endless control turns by its steps. */
    data class BrowseInstrument(val direction: Int) : PlayFunction

    /** A value the next note reads at its onset: a knob, a fader, the mod wheel (the event names the control). */
    data object Value : PlayFunction

    /** Nothing yet (the light only); later a backing loop or a metronome. */
    data object PlayStop : PlayFunction
}

/** A route's control: on [source] only, or on any source when [source] is null. */
data class ControlRef(val source: SourceId?, val control: ControlId)

/**
 * Which control does what: data, so a control points elsewhere without touching a source (KLANG-00402, section 4).
 * Looked up by (source, control) first, then by control for any source. The keybeds all play the instrument in v1, so
 * notes are not routed here (a split or a key switch would add that).
 */
data class Routing(val routes: Map<ControlRef, PlayFunction>) {

    /** What [control] on [source] does; null for a control no route names. */
    fun functionOf(source: SourceId, control: ControlId): PlayFunction? =
        routes[ControlRef(source = source, control = control)] ?: routes[ControlRef(source = null, control = control)]

    companion object {
        /** The v1 routes, in code (editing them on the page is a later ticket). */
        val defaults = Routing(
            routes = buildMap {
                for (pad in 1..8) {
                    put(ControlRef(source = null, control = ControlId.pad(pad)), PlayFunction.HitPad(pad))
                }

                put(ControlRef(source = null, control = ControlId.Sustain), PlayFunction.Sustain)
                put(ControlRef(source = null, control = ControlId.Panic), PlayFunction.Panic)
                put(ControlRef(source = null, control = ControlId.MainEncoder), PlayFunction.BrowseInstrument(direction = 1))
                put(ControlRef(source = null, control = ControlId.InstrumentPrevious), PlayFunction.BrowseInstrument(direction = -1))
                put(ControlRef(source = null, control = ControlId.InstrumentNext), PlayFunction.BrowseInstrument(direction = 1))
                put(ControlRef(source = null, control = ControlId.ModWheel), PlayFunction.Value)

                for (n in 1..9) {
                    put(ControlRef(source = null, control = ControlId("knob-$n")), PlayFunction.Value)
                    put(ControlRef(source = null, control = ControlId("fader-$n")), PlayFunction.Value)
                }

                put(ControlRef(source = null, control = ControlId("play")), PlayFunction.PlayStop)
                put(ControlRef(source = null, control = ControlId("stop")), PlayFunction.PlayStop)
            },
        )
    }
}
