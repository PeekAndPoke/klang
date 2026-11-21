/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * A song's score: what binds one song to the show, as plain data. The [cast] (who of the band plays which instrument
 * and which of the song's parts), the stage [plot] (where each one stands, and how they walk in), and when two of the
 * band look at each other ([eyeContact], the whole band's timing: see `contactAt`). The live act is built from a show
 * (the song, the seed, the switches) and its score: nothing else in it knows a song.
 *
 * Each song has its own; they lie in the package `scores`, apart from what reads them.
 */
data class Score(val cast: Cast, val plot: StagePlot, val eyeContact: ChanceInSlots)
