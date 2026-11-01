/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Which control does what (KLANG-00402, section 4): the lookup order, and the v1 defaults' table. */
class RoutingSpec : StringSpec({

    val keylab = SourceId("keylab")

    "a route for the source wins over one for any source; a control no route names does nothing" {
        val routing = Routing(
            routes = mapOf(
                ControlRef(source = null, control = ControlId("play")) to PlayFunction.PlayStop,
                ControlRef(source = keylab, control = ControlId("play")) to PlayFunction.Panic,
            ),
        )

        routing.functionOf(source = keylab, control = ControlId("play")) shouldBe PlayFunction.Panic
        routing.functionOf(source = SourceId.Laptop, control = ControlId("play")) shouldBe PlayFunction.PlayStop
        routing.functionOf(source = keylab, control = ControlId("record")) shouldBe null
    }

    "by default every pad, knob, fader and wheel the KeyLab's drawing has does something, and its Play and Stop" {
        // The pitch bend is the instrument's own (it bends the notes), not a routed function
        val routed = KeyLabEssential49Mk3.controls.filter {
            it is PanelControl.Pad || it is PanelControl.Knob || it is PanelControl.Fader || it is PanelControl.Wheel
        }.map { it.id }.filterNot { it == ControlId.PitchBend.id } + listOf("play", "stop")

        val missing = routed.filter { Routing.defaults.functionOf(source = keylab, control = ControlId(it)) == null }

        missing shouldBe emptyList()
        // Not a walk over nothing: 8 pads, 9 knobs and the main encoder (drawn as a knob), 9 faders, the mod wheel, 2 buttons
        routed.size shouldBe 8 + 10 + 9 + 1 + 2
    }
})
