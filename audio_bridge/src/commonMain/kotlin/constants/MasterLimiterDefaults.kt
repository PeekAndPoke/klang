/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Limiter defaults shared by the house safety limiter (`audio_be/MasterStage`)
// and the opt-in authored stage (`MasterStageDsl.Limiter`).
//
// Only the values that are genuinely wire defaults live here. The house
// limiter's own TIMING — `MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS` and
// `HOUSE_LIMITER_ATTACK_SECONDS` — deliberately stays in `audio_be`: no DSL field
// carries it, because the house limiter is not authorable. That split is the
// point, not an oversight; see `MasterDefaultsSyncSpec`.
// ─────────────────────────────────────────────────────────────────────────────

/** Ceiling at -1 dB. */
const val LIMITER_THRESHOLD_DB: Double = -1.0

/** Brickwall ratio. */
const val LIMITER_RATIO: Double = 20.0

/**
 * 2 dB soft knee — 2026-04-30 fix for britzeling on heavily-distorted content.
 * With kneeDb=0 the gain curve had a C¹ kink at the threshold corner; every
 * envelope crossing of -1 dBFS injected high-order harmonics at audio rate.
 * The 2 dB knee makes the corner smooth without changing the brickwall character.
 */
const val LIMITER_KNEE_DB: Double = 2.0

/** Envelope release, shared by both limiters. */
const val LIMITER_RELEASE_SECONDS: Double = 0.1

// ── Authored-limiter defaults (the opt-in `MasterFx.limiter()` stage) ────────────────
// These deliberately DIFFER from the house timing in `MasterStage`, and the difference is
// the point: that stage is per-playback, upstream of the summed mix.

/**
 * **0, on purpose.** An authored master limiter lives on one playback's `MasterBus`, so any
 * lookahead there would delay that playback against every other one — the same desync that
 * keeps lookahead off per-orbit compressors. Latency is opt-in per author, never a default.
 */
const val AUTHORED_LIMITER_LOOKAHEAD_SECONDS: Double = 0.0

/**
 * **The one-pole attack**, not a smoothing length: with no lookahead the authored limiter
 * takes the classic path, where 1 ms is what lets transients keep their punch.
 */
const val AUTHORED_LIMITER_ATTACK_SECONDS: Double = 0.001
