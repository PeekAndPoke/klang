/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Authoring-layer representation of a voice-pipeline reference.
 *
 * A voice may select its pipeline either by name (a built-in like `"modern"`/`"pedal"`, or a
 * pre-registered custom pipeline) or by inlining a [PipelineDsl] tree directly.
 *
 * At the playback → wire boundary, [Dsl] is denormalized to a stable synthetic name — the playback
 * context allocates one via `registerPipeline` — so the wire-level [VoiceData] still carries
 * `pipeline: String?` and the BE protocol stays unchanged.
 *
 * Mirror of [SoundValue]. NOT a wire type (no `@WireFormat`, no `@WireName`): it never crosses the
 * worklet boundary — it is denormalized to `pipeline: String?` first. Authoring-layer only.
 */
sealed interface PipelineValue {

    /** Pipeline referenced by a stable name (built-in `modern`/`pedal`, or a pre-registered custom). */
    data class Named(val name: String) : PipelineValue

    /** Pipeline defined inline as a [PipelineDsl] stage chain. */
    data class Dsl(val pipeline: PipelineDsl) : PipelineValue
}
