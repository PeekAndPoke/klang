/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.song.Envelope
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * How bright a played string is, 0 to 1, as its note sounds: the light follows the note's [Envelope] where the band
 * knows it (the maintainer, 2026-11-27: the glass guitars' long notes went dark early, "looking as if no-one is
 * playing"; "Can we somehow tap into the actual duration of the notes"). It rises over the attack, falls toward the
 * sustain over the decay, holds while the note is held (its gate) and falls over the release; each fall exponential, a
 * stage's light down to 5 % where the stage ends, as the engine's envelope falls.
 *
 * It follows the sound's level, so a note that holds sounds lit to its end: Sirene's arpeggio in Noch seltsamere Dinge
 * (half its level held to the next note) stays lit longer than the old fall showed it, not shorter.
 *
 * Without an envelope (a voice whose envelope the band cannot read; the songs are not written for the band: the
 * maintainer, 2026-11-27, "Please do not make the scores more complicated just for the band viz") the light goes by
 * how long the note is held: it falls from the pluck with [RING] seconds to 1/e, but no lower than [HOLD] while the
 * note is held, and from the note's end with [DAMP], as a hand damps it. A long held note so stays lit as long as it is
 * held (the glass guitars), a short one looks as it always did.
 */
object NoteLight {
    /** Without an envelope: how a played string's light falls, in seconds to 1/e, while the note rings. */
    const val RING = 0.25

    /** Without an envelope: the same, once the note has ended, as the hand damps it. */
    const val DAMP = 0.06

    /** Without an envelope: the light of a note still held, at the least: a string that rings. */
    const val HOLD = 0.35

    /** A stage's fall in seconds to 1/e, as a share of the stage's length: a third, so 5 % is left at its end. */
    const val STAGE = 1.0 / 3

    /** The quickest fall, in seconds to 1/e: a light that goes out in one frame would flicker. */
    const val QUICKEST = 0.02

    /**
     * How long after its pluck a note is looked at, at the least, in seconds: the four seconds the string player always
     * looked back (its eyes' glow counts the notes of the last four seconds, and would drop at once without them).
     */
    const val WINDOW = 4.0

    /** How many falls to 1/e after the gate a light is looked at: five leave under 1 %. */
    const val FALLS = 5.0

    /**
     * How long after its pluck a note held [held] seconds with the [envelope] (null: unknown) is looked at, in seconds:
     * its gate and five falls of its release (without an envelope of [DAMP]), never under [WINDOW]. NaN-guard: a held
     * time of no number is none.
     */
    fun lasts(held: Double, envelope: Envelope?): Double {
        val gate = if (held.isFinite() && held > 0.0) held else 0.0
        val fall = if (envelope == null || !envelope.known) DAMP else max(QUICKEST, envelope.release * STAGE)

        return max(WINDOW, gate + FALLS * fall)
    }

    /**
     * For notes plucked at [starts] (sorted) and looked at for [lasts] seconds each: for every note, the latest moment
     * any note up to it is still looked at. A look back from a moment stops at the first note whose reach lies before
     * that moment: nothing before it is lit any more, however long ago a long note was plucked.
     */
    fun reach(starts: DoubleArray, lasts: DoubleArray): DoubleArray {
        val out = DoubleArray(starts.size)
        var most = Double.NEGATIVE_INFINITY

        for (i in starts.indices) {
            most = max(most, starts[i] + lasts[i])
            out[i] = most
        }

        return out
    }

    /**
     * The light [age] seconds after the pluck of a note held [held] seconds, with the [envelope] (null: unknown). An
     * age before the pluck, or of no number, is dark; a [held] of no number, or below 0, is none.
     */
    fun level(age: Double, held: Double, envelope: Envelope?): Double {
        // NaN-guard: no time, or a time before the pluck, is no light
        if (!(age >= 0.0)) {
            return 0.0
        }

        // NaN-guard: a held time of no number is none
        val gate = if (held.isFinite() && held > 0.0) held else 0.0

        if (envelope == null || !envelope.known) {
            // while held, the pluck's fall, no lower than the glow; from the end on, from where it stood, the pluck's
            // fall and the damping together (a short note so falls exactly as it always did)
            val standing = max(HOLD, exp(-min(age, gate) / RING))
            val after = max(0.0, age - gate)

            return standing * exp(-after / RING - after / DAMP)
        }

        val atGate = holding(min(age, gate), envelope)

        if (age <= gate) {
            return atGate
        }

        return atGate * exp(-(age - gate) / max(QUICKEST, envelope.release * STAGE))
    }

    /** The light [a] seconds into a note still held: up over the attack, down toward the sustain over the decay. */
    private fun holding(a: Double, envelope: Envelope): Double {
        val attack = max(0.0, envelope.attack)

        if (a < attack) {
            return a / attack
        }

        val sustain = envelope.sustain.coerceIn(0.0, 1.0)

        return sustain + (1.0 - sustain) * exp(-(a - attack) / max(QUICKEST, envelope.decay * STAGE))
    }
}
