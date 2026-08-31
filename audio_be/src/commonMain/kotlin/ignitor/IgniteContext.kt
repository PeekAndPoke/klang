/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import kotlin.random.Random

/**
 * Context for Ignitor rendering. Created ONCE per voice, mutated per block.
 * No reinstantiation in the hot path.
 *
 * RULE: No Long anywhere in audio hot paths. Long is boxed in Kotlin/JS = severe perf degradation.
 * Int frames: max ~2.1B frames = ~12.4 hours at 48kHz — more than enough for any voice.
 */
class IgniteContext(
    // ── Static per voice (set at creation, never changes) ──────────────────────
    /** Audio sample rate in Hz */
    val sampleRate: Int,
    /**
     * Total gate duration in frames (scheduled, before release).
     *
     * Deliberately NOT moved by [io.peekandpoke.klang.audio_be.voices.Voice.releaseGate]: it is
     * the `accelerate` glide base, and retro-shrinking it would jump the glide progress and leap
     * the pitch. On held realtime voices `accelerate` is inert by decision
     * (docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md).
     */
    val voiceDurationFrames: Int,
    // ── Moved by Voice.releaseGate on a realtime note-off — do NOT bake copies ─
    /**
     * Frame (relative to voice start) when gate ends and release begins.
     * `var`: a realtime note-off ([io.peekandpoke.klang.audio_be.voices.Voice.releaseGate])
     * moves the gate earlier.
     */
    var gateEndFrame: Int,
    // ── Static per voice (set at creation, never changes) ──────────────────────
    /** Release duration in frames */
    val releaseFrames: Int,
    /** Shared scratch buffer pool for binary composition operators */
    val scratchBuffers: ScratchBuffers,
    /**
     * THE VOICE'S random stream — derived per voice from the playback's `coreRandom`
     * (`PlaybackCtx`), shared by every draw in this voice's sub-graph (generate-time drift
     * construction reads it here; build-time consumers get the SAME instance via
     * `IgnitorBuildCache.random`). One instance per voice is safe because in-graph draw
     * order is deterministic; deriving per voice is what makes draw order BETWEEN voices
     * irrelevant and playback runs bit-reproducible. Default = the global (test/tool
     * convenience; production always passes the voice's own).
     */
    val random: Random = Random,

    // ── Mutable per block (updated by caller before each generate() call) ──────
    /** Start index in buffer for this block */
    var offset: Int = 0,
    /** Number of samples to generate */
    var length: Int = 0,
    /**
     * Frames since voice start (monotonic, updated once per block), counted AT buffer index
     * [offset] — NOT at index 0. A voice's first `generate` call sees 0. Every consumer adds its
     * own `i - offset` on top (`AdsrIgnitor`, `IgnitorFilters`, `PitchModFactories`); computing it
     * at index 0 made the first block of a mid-block onset run a negative clock, which clamped to
     * silence and then stepped. See `IgniteOnsetOffsetSpec`.
     */
    var voiceElapsedFrames: Int = 0,
    /**
     * Per-sample phase-increment multipliers (1.0 = no change), or null.
     * MUST be at least (offset + length) elements long when non-null.
     *
     * **Set by IgniteRenderer only** (strip-level pipeline bridge from [BlockContext.freqModBuffer]).
     * Ignitor DSL-level pitch mods (vibrato, accelerate, pitchEnvelope, FM) are resolved at
     * build time via [ModApplyingIgnitor] and do NOT use this field.
     */
    var phaseMod: DoubleArray? = null,
) {
    // ── Computed properties (derived from above, no storage) ───────────────────

    /** Pre-computed Double to avoid repeated Int→Double conversion in hot loops */
    val sampleRateD: Double = sampleRate.toDouble()

    /** Pre-computed Double to avoid repeated Int→Double conversion in hot loops */
    val voiceDurationFramesD: Double = voiceDurationFrames.toDouble()

    // Five block-start-only convenience accessors (voiceElapsedSecs, voiceDurationSecs,
    // voiceProgress, isInRelease, releaseProgress) were DELETED here 2026-08-28 with zero callers
    // (block-framing ledger E6). They were shaped exactly like the bug class this file's clock got
    // burned by: a time value that silently means "at ctx.offset" but reads like "now". If a
    // per-sample variant is ever needed, it must take the sample offset explicitly — name it
    // `...At(sampleOffset)` — never a bare property.
}
