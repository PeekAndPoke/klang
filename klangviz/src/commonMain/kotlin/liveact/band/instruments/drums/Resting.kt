/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.peekandpoke.klang.viz.liveact.smooth

// The drummer at rest (KLANG-00481; the maintainer, 2026-11-23, of Kokon, where the drums wait two minutes for
// their part: "Wucht really has to relax before he starts playing ... he sits there 'ready' with sticks near the hats
// all the time. We need the meditation pose", the hands resting on the thighs). While his part is silent for long he
// lets the hands sink onto his thighs; before it begins again he takes the sticks up in time.

/** He begins to let go this many seconds after his last hit, and has let go this many seconds later. */
const val REST_AFTER = 3.0
const val REST_SINK = 2.5

/** He is ready again this many seconds before his next hit, and began to take the sticks up this many before that. */
const val REST_READY = 1.2
const val REST_RISE = 2.2

/**
 * How far the drummer rests, 0 (ready, the sticks over the kit) to 1 (the hands on the thighs): [since] his last hit
 * and [until] his next, in seconds (endless where there is none). In a part that keeps going he never rests: a gap
 * has to be longer than [REST_AFTER] and [REST_READY] together before a hand sinks at all.
 */
fun restShare(since: Double, until: Double): Double {
    // NaN-guard: where a time is no number he is ready
    if (since != since || until != until) {
        return 0.0
    }

    return smooth((since - REST_AFTER) / REST_SINK) * smooth((until - REST_READY) / REST_RISE)
}
