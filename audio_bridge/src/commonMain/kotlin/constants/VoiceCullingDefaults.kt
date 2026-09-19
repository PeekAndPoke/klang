/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Silence culling: a voice whose output has stayed inaudible through its
// release ends itself early instead of rendering its whole scheduled tail.
// The decision is made in `audio_be/voices/Voice.render` from the output peak
// `SendRenderer` measures; only the tunable values live here, so the authoring
// side (`cull(...)`) and the engine cannot disagree about them.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Default silence window in seconds: the release must stay under [VOICE_CULL_FLOOR] for this
 * long before the voice ends. Long enough that a momentary dip never culls, short enough that a
 * two-second tail loses nothing worth rendering. The `cull(seconds)` door overrides it per voice.
 */
const val VOICE_CULL_SECONDS: Double = 0.05

/**
 * Sample magnitude at or below which an orbit's mix buffer counts as silent for its
 * deactivation countdown (`Cylinder.isMixBufferSilent`): `1e-5`, -100 dBFS.
 */
const val ORBIT_SILENCE_FLOOR: Double = 1e-5

/**
 * Output peak below which a voice's block counts as silent for culling. Measured on the voice's
 * own output (post-VCA, times gain), before the solo/mute fade, so a voice that is merely faded
 * out by a solo is not mistaken for a dead one. That output is all a voice puts on its orbit: the
 * mix bus, which the orbit's delay and reverb take their feed from (Katalyst step 5b-2).
 *
 * The same value as [ORBIT_SILENCE_FLOOR], on purpose and by definition rather than by two
 * literals: a culled voice's contribution to the mix bus is then already below what keeps an
 * orbit alive, so one culled voice cannot let its orbit deactivate earlier than its sounding tail
 * would have (and reset the phaser sweep and the compressor follower under the next hit). Some
 * accumulations and gains sit outside that per-voice statement and are accepted as a known class:
 * several sub-floor tails on one orbit whose SUM stays above the floor; a feedback delay whose
 * `TailCeiling` keeps a steady sub-floor feed alive through recirculation; and a stage ahead of the
 * room that amplifies, an orbit `wet` above 1 or a resonant body or vowel, which feeds a culled
 * voice's floor times that factor. All of them remove content at -100 dB and below times such a
 * factor; what can move is the moment a sparse orbit's bus effects reset.
 */
const val VOICE_CULL_FLOOR: Double = ORBIT_SILENCE_FLOOR

/**
 * The wire value `VoiceData.cull` takes for "never cull this voice" (`noCull()`): a negative
 * window never elapses. Any negative value means the same; this is the one the door writes.
 */
const val VOICE_CULL_NEVER: Double = -1.0
