/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

/**
 * What a [ResonatorBank] installs from, and what a resonator stage (`KatalystResonatorEffect`) is offered: a [table]
 * (for a stage, null is OFF), the [mix] and the [floor].
 *
 * **Mutable and reused, on purpose** (engine tidy-up step 12 (a)). A stage is offered its config on every block, and
 * on V8 a non-integral double handed to a function that is not inlined travels as a heap number, one allocation per
 * value per call. Measured on the first shape, which passed the mix and the floor as arguments: about 40 bytes per
 * block of a steady body, where the code before the step (which passed one carrier object, built per change) took
 * none. So the numbers stay in the fields of one object per holder, and a call hands over the reference. Each holder
 * owns its instance (the slot writer one, the stage one for what it installed and one for what it parked); a receiver
 * reads the values and keeps no reference to another holder's instance.
 */
class ResonatorConfig(
    var table: ResonatorTable? = null,
    var mix: Double = Double.NaN,
    var floor: Double = Double.NaN,
)
