/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * What a declared duck stage is configured with: the source orbit it listens to, and the envelope
 * follower's attack and depth. [KatalystDuckWriter] builds it when the orbit's param state changes,
 * and [KatalystDuckEffect.configure] applies it.
 *
 * Lived under `Voice` (as `Voice.Ducking`) until 2026-10-07 (audit A1.7), though only the chain
 * ever used it. [cylinderId] and [attackSeconds] keep their pre-Katalyst names until the duck's
 * rename (audit A2.7).
 */
class DuckSettings(
    val cylinderId: Int,
    val attackSeconds: Double,
    val depth: Double,
)
