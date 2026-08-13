/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Authoring-layer representation of a master-chain reference.
 *
 * An event may select its master either by name (a pre-registered custom master) or by inlining a
 * [MasterDsl] chain directly.
 *
 * At the playback → wire boundary, [Dsl] is denormalized to a stable synthetic name — the playback
 * context allocates one via `registerMaster` — so the wire-level [VoiceData] still carries
 * `master: String?`.
 *
 * Mirror of [PipelineValue] / [SoundValue]. NOT a wire type (no `@WireFormat`, no `@WireName`): it
 * never crosses the worklet boundary — it is denormalized to `master: String?` first.
 * Authoring-layer only.
 */
sealed interface MasterValue {

    /** Master referenced by a stable name (a pre-registered custom chain). */
    data class Named(val name: String) : MasterValue

    /** Master defined inline as a [MasterDsl] stage chain. */
    data class Dsl(val master: MasterDsl) : MasterValue
}
