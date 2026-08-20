/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import kotlin.random.Random

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools

/**
 * Per-playback context. Created when a playback is first seen, destroyed on cleanup.
 *
 * Groups per-playback state that was previously scattered across VoiceScheduler maps.
 * The forked registry allows per-playback oscillator registration (for future KlangScript integration).
 */
class PlaybackCtx(
    val playbackId: String,
    /** Per-playback registry, forked from global. User can register custom oscillators. */
    val ignitorRegistry: IgnitorRegistry,
    /** Per-playback unison start-phase pools (docs/tasks/unison-phase-pool.md §3.3). */
    val phasePools: PhasePools,
    /** Backend-local epoch (seconds since backend start) when this playback first appeared */
    var epoch: Double = 0.0,
    /**
     * Root of the per-playback RNG DERIVATION TREE (seeded-voice-rng): every voice deals
     * its own child stream (`Random(coreRandom.nextInt())`) at creation — EVERY makeVoice
     * call burns exactly one draw, including ones that return null, so async sample-load
     * timing cannot shift later voices' seeds. Draw order BETWEEN voices/nodes never
     * matters, and a render is bit-REPRODUCIBLE for identical pid + NOTE ORDER + block
     * segmentation. The pid is MIXED into the seed (same idiom as the phase-pool seed in
     * VoiceScheduler) — and LIVE pids are AUTO-GENERATED per play ("playback-N"), so live
     * takes vary by design (matching the phase-pool philosophy); the reproducible paths are
     * the CONSTANT pids: the offline renderer ("offline") and the benchmark ("benchmark").
     * Seeds are POSITIONAL: inserting one voice re-seeds every later one — an A/B of two
     * edits stays bit-identical only up to the first difference. Phase pools keep their OWN
     * stream (deliberately voice-shared vocabulary; live seeds from the clock, offline pins
     * seed=1). Int seed — never Long (JS ban). UPGRADE PATH if live-edit-stable drift is
     * ever wanted: derive from FULL voice identity, NOT sourceId alone (chords share
     * sourceId; superimpose copies share sourceId+time+note — identical seeds would
     * collapse them into coherent sums).
     */
    val coreRandom: Random = Random(0x5EED xor playbackId.hashCode()),
)
