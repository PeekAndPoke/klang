/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * A pause that never comes: the default for a part that draws what plays (the scope, the spectrum, the gauges) where
 * nothing covers it. The workbench hands its parts `VisualsScreen.open` instead, so they rest while a visual covers the
 * page (the maintainer, 2026-10-29: "handle the cutoff for all visual things ... in one place").
 */
val NeverPaused: Stream<Boolean> = StreamSource(false)
