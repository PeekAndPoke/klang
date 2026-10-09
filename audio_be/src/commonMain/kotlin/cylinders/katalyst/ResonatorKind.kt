/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * Which resonator a [KatalystResonatorEffect] is: `body(...)` or `vowel(...)`. The kind picks the catalogue its tables
 * come from ([ResonatorTables]) and the wet and floor an unset knob stands for; nothing else differs between the two.
 */
enum class ResonatorKind { BODY, VOWEL }
