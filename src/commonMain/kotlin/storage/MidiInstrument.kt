/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

/**
 * One instrument authored in the Midi Playground before the one play code (KLANG-00402, D7): both inputs as
 * KlangScript source. Read once, for the play code's first load ([io.peekandpoke.klang.midi.MidiPlayCode.fromOld]).
 *
 * @property ignitorCode Evaluates to an Ignitor; blank = the built-in sound picked on the page.
 * @property mapperCode Evaluates to a pattern mapper (`x => x.reverb(wet = 0.2)`); blank = none.
 */
data class MidiInstrument(
    val name: String,
    val ignitorCode: String,
    val mapperCode: String,
)
