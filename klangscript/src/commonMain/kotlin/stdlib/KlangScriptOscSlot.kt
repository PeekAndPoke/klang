/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Canonical open parameter slots for sprudel-compatible custom sounds.
 *
 * Each slot is the same `IgnitorDsl.Param(name, default)` singleton that built-in
 * sounds use, exposed for custom sounds that want to opt in to sprudel
 * modulation (`.analog()`, `.voices()`, `.detune()`, etc).
 *
 * ```KlangScript(Executable)
 * let pad = Osc.sine().analog(OscSlot.analog).lowpass(2000)
 * note("c").sound(pad)
 * ```
 *
 * Also accessible via `Osc.slot.analog` (member-property chain through the Osc
 * namespace).
 *
 * Without opting in, custom sounds ignore sprudel modulation (the data-class
 * defaults are sealed `Constant(0.0)`). Opting in wires the named slot to
 * `oscParams[name]` lookup at voice-trigger time.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("OscSlot")
object KlangScriptOscSlot {
    override fun toString(): String = "[OscSlot object]"

    /** Open `analog` slot (default 0.0). Mirrors sprudel `.analog(x)`. */
    @KlangScript.Property
    val analog: IgnitorDsl = IgnitorDsl.Slots.analog

    /** Open `voices` slot (default 8.0). Used by super-oscillators. */
    @KlangScript.Property
    val voices: IgnitorDsl = IgnitorDsl.Slots.voices

    /** Open `detune` slot (default 0.2). Used by super-oscillators. */
    @KlangScript.Property
    val detune: IgnitorDsl = IgnitorDsl.Slots.detune

    /** Open `duty` slot (default 0.5). Used by pulze. */
    @KlangScript.Property
    val duty: IgnitorDsl = IgnitorDsl.Slots.duty

    /** Open `density` slot (default 0.2). Used by dust / crackle. */
    @KlangScript.Property
    val density: IgnitorDsl = IgnitorDsl.Slots.density

    /** Open `decay` slot (default 0.996). Used by pluck. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.decay

    /** Open `brightness` slot (default 0.5). Used by pluck. */
    @KlangScript.Property
    val brightness: IgnitorDsl = IgnitorDsl.Slots.brightness

    /** Open `pickPosition` slot (default 0.5). Used by pluck. */
    @KlangScript.Property
    val pickPosition: IgnitorDsl = IgnitorDsl.Slots.pickPosition

    /** Open `stiffness` slot (default 0.0). Used by pluck. */
    @KlangScript.Property
    val stiffness: IgnitorDsl = IgnitorDsl.Slots.stiffness

    /** Open `rate` slot (default 1.0). Used by perlin / berlin noise. */
    @KlangScript.Property
    val rate: IgnitorDsl = IgnitorDsl.Slots.rate
}
