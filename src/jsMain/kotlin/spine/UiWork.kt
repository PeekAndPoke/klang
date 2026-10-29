/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.map

/**
 * When the work that only shows things rests: the scopes, the spectrums, the gauges' needles, the Motor's graphs, the
 * lamp over the plate, the voices' rain. The music plays on. Built on [WindowFocus] (the maintainer, 2026-10-29: "The
 * spine should also react to the window losing focus and pause all ui work and resume when the window/document is
 * focused again"): a window left for another app gets animation frames from the browser and draws for no one. The
 * default pause of every part that has one; a part under a visual rests for that too (the workbench's Motor).
 */
object UiWork {

    /** True while showing things is pointless: the window has no attention. */
    val resting: Stream<Boolean> = WindowFocus.focused.map { !it }
}
