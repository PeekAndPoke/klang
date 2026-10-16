/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

/**
 * Oversampling factor of a voice `shape` or `distort` that names none: 0, off (a per-note cost, opted into per sound).
 * Read by the Kotlin doors (`shape(..., oversample = SHAPE_OVERSAMPLE.ign)`) and the `IgnitorDsl.Shape` node's field;
 * the KlangScript doors bake the same literal (KSP takes literals only). A deliberate asymmetry with the bus stage,
 * whose `DISTORT_OVERSAMPLE` is 2x.
 */
const val SHAPE_OVERSAMPLE: Double = 0.0
