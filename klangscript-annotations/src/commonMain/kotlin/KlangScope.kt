/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.annotations

/**
 * Where a setting takes effect: on the one voice, on the orbit's shared bus, or on the master.
 *
 * This is load-bearing knowledge for anyone writing a song, and it is not guessable from a
 * function's name: `room()` reads like a per-note setting and is mostly not one. The engine's
 * layout (`audio_be`) is the source of truth here:
 *
 * - a voice runs its own strip, Pitch to Ignite to Filter to Send (`Voice.kt`);
 * - each orbit owns ONE shared bus, Body to Vowel to Delay to Reverb to Phaser to Compressor,
 *   processing the orbit's summed mix, plus ducking after all orbits (`Cylinder.kt`);
 * - that bus is configured by ONE voice, the first to sound while it lives ("first-writer-wins",
 *   `Cylinder.kt`): a second voice asking for different bus settings is simply ignored, and the
 *   answer is to route it to its own orbit.
 *
 * Set it with `@scope voice|orbit|orbit-send|master` in the KDoc of the primary declaration.
 *
 * It lives in `klangscript-annotations` because both sides of the docs pipeline need it and that is
 * the only module both can see: the KSP processor reads the tag and emits the constant, `KlangSymbol`
 * (in `klangscript`) carries it, and the editor renders it. One definition, no new module wiring, and
 * it keeps its own module's package rather than splitting `script.types` across two modules.
 */
enum class KlangScope(val tag: String, val label: String) {
    /** Every note carries its own value. Filters, levels, envelopes, distortion, unison. */
    VOICE("voice", "PER VOICE"),

    /**
     * One shared processor per orbit, configured by the orbit's owning voice (first-writer-wins).
     * Every voice on the orbit is processed by it whether it asked for it or not.
     */
    ORBIT("orbit", "ORBIT BUS"),

    /**
     * An orbit bus processor that each voice feeds by its own amount: the processor's character is
     * a bus setting (first-writer-wins), the wet amount is per voice.
     *
     * This is `room` and `delay`, and it is the distinction that trips people up: a dry voice on a
     * wet orbit stays dry, because only voices with a send greater than zero are summed into the
     * orbit's send buffer (`SendRenderer.kt`), while `size` or `time` are the orbit's for everyone.
     */
    ORBIT_SEND("orbit-send", "ORBIT BUS + SEND"),

    /** The whole playback, after every orbit. */
    MASTER("master", "MASTER");

    companion object {
        /** The [KlangScope] for a `@scope` tag value, or null when the value is not one of ours. */
        fun ofTag(tag: String): KlangScope? = entries.firstOrNull { it.tag == tag.trim().lowercase() }
    }
}
