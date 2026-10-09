/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Silence: the engine's one silence floor, and silence culling, where a voice
// whose output has stayed inaudible through its release ends itself early
// instead of rendering its whole scheduled tail. The culling decision is made in
// `audio_be/voices/Voice.render` from the output peak `SendRenderer` measures;
// only the tunable values live here, so the authoring side (`cull(...)`) and the
// engine cannot disagree about them.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Default silence window in seconds: the release must stay under [VOICE_CULL_FLOOR] for this
 * long before the voice ends. Long enough that a momentary dip never culls, short enough that a
 * two-second tail loses nothing worth rendering. The `cull(seconds)` door overrides it per voice.
 */
const val VOICE_CULL_SECONDS: Double = 0.05

/**
 * The engine's silence floor, `1e-5`, -100 dBFS. ONE value for every "is this still sounding"
 * question on an orbit (audit B2.6, 2026-10-08; five declarations of it before):
 * - an orbit's mix buffer, for its deactivation countdown (`Cylinder.isMixBufferSilent`), and the
 *   lookahead compressor's ring (`KatalystCompressorEffect`);
 * - a culled voice's output ([VOICE_CULL_FLOOR]);
 * - the delay and reverb drains: `TailCeiling.hasTail`, the closed-form "samples until silent" of
 *   `DelayLine` and `Reverb`, and their `hasTail` scans.
 *
 * The comparison is not the same everywhere: every site counts a magnitude AT OR BELOW the floor as
 * silent (above it is "still sounding"), except the voice cull, which counts a peak strictly BELOW
 * it as silent (`voiceOutputPeak < VOICE_CULL_FLOOR` in `Voice.render`).
 *
 * NOT the master's tail poll (`MasterBus.TAIL_SILENCE_THRESHOLD`, 1e-4): whether the output may
 * use another floor is an open maintainer decision (D9 of
 * `docs/audio-audit/2026-10-07-engine-tidy-audit.md`).
 */
const val SILENCE_FLOOR: Double = 1e-5

/**
 * Output peak below which a voice's block counts as silent for culling. Measured on the voice's
 * own output (post-VCA, times gain), before the solo/mute fade, so a voice that is merely faded
 * out by a solo is not mistaken for a dead one. That output is all a voice puts on its orbit: the
 * mix bus, which the orbit's delay and reverb take their feed from (Katalyst step 5b-2).
 *
 * The same value as [SILENCE_FLOOR], on purpose and by definition rather than by two
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
const val VOICE_CULL_FLOOR: Double = SILENCE_FLOOR

/**
 * The wire value `VoiceData.cull` takes for "never cull this voice" (`noCull()`): a negative
 * window never elapses. Any negative value means the same; this is the one the door writes.
 */
const val VOICE_CULL_NEVER: Double = -1.0
