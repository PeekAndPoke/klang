/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Defaults of the two send effects, delay and reverb, on EVERY surface: the
// master stages (`MasterStageDsl.Delay` / `.Reverb` and their builders), a
// sprudel `delay(...)` / `reverb(...)` call (the compound doors fill their
// companions from here, see `/dsl-design` §4),
// the engine's fill for any other producer (`VoiceFactory`), the fallback for a
// non-finite value on both buses, and the sprudel editor tools. One edit here
// retunes all of them; they cannot drift apart.
//
// Musical, not neutral (maintainer, 2026-09-16): an unset slot means a usable
// sound. A voice that never touches an effect still sends nothing to it.
// `docs/tasks-archive/2026-09/20260916-delay-names-and-send-defaults.md`.
// ─────────────────────────────────────────────────────────────────────────────

/** Delay send, 0..1. */
const val DELAY_WET: Double = 0.25

/** Delay time in seconds. */
const val DELAY_TIME_SECONDS: Double = 0.25

/** Delay feedback; at or above 1.0 the delay self-oscillates, bounded by the cap. */
const val DELAY_FEEDBACK: Double = 0.3

/** Level the recirculating delay signal saturates toward. 1.0 is the engine's plain soft cap. */
const val DELAY_CAP: Double = 1.0

/** Reverb send, 0..1. */
const val REVERB_WET: Double = 0.25

/**
 * Reverb size on the authored ~0..10 scale. `5.0 / 10 == 0.5`, the Freeverb default, about a
 * 1.4 s tail.
 */
const val REVERB_SIZE: Double = 5.0
