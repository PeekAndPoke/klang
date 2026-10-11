/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * What a declared duck stage is configured with: the source orbit it listens to, and the envelope
 * follower's attack and depth. [KatalystDuckEffect.configure] applies it.
 *
 * **Mutable and reused, on purpose** (katalyst knob expressions step 2, 2026-10-11): [KatalystDuckWriter]
 * owns one and refills it in place at every resolve. It built a new one per resolve before, which a
 * knob resolved every block would turn into an allocation per block. The stage reads the values and
 * keeps no reference.
 *
 * Lived under `Voice` (as `Voice.Ducking`) until 2026-10-07 (audit A1.7), though only the chain
 * ever used it. [cylinderId] and [attackSeconds] keep their pre-Katalyst names until the duck's
 * rename (audit A2.7).
 */
class DuckSettings(
    var cylinderId: Int = 0,
    var attackSeconds: Double = Double.NaN,
    var depth: Double = Double.NaN,
)
