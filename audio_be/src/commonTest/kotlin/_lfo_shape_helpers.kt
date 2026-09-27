/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.LfoShapes

/**
 * A shape NAME to the enum, through the catalogue's index: `lfoShapeAt(LfoShapes.indexOf(name))`. Unknown or
 * null is [LfoShape.SINE]; case-insensitive; the aliases are `LfoShapes`' own.
 *
 * Test-only since phase 3 step 9: the strip's `TremoloRenderer` was its last production caller, and the one
 * production host left, the Ignitor `Tremolo` node, carries the INDEX as a knob and resolves it through
 * [lfoShapeAt]. The specs keep naming shapes, so the name lookup lives here.
 */
internal fun parseLfoShape(shape: String?): LfoShape = lfoShapeAt(LfoShapes.indexOf(shape))
