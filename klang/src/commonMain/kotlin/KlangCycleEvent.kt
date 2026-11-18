/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.SourceLocationChain

/**
 * One event of a cycle as the playback places it ([KlangCyclicPlayback.cycleEvents], KLANG-00450): what a reader
 * beside the sound (the live act's band) needs to show what sounds.
 */
class KlangCycleEvent(
    /** Its start in seconds from the playback's start, on the playback's tempo map: the seconds `heardSec` counts in. */
    val startSec: Double,
    /** The end of its slot in the pattern, in the same seconds. Not its gate: a `legato` does not move it. */
    val endSec: Double,
    val startCycles: Double,
    val durationCycles: Double,
    /** What the scheduler sends for it. A control-only event (`control == true`) sets state and sounds no note. */
    val data: VoiceData,
    val sourceLocations: SourceLocationChain?,
)
