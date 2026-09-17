/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Authoring-layer representation of an orbit-chain reference.
 *
 * An event may select its Katalyst either by name (a pre-registered custom chain) or by inlining a
 * [KatalystDsl] chain directly.
 *
 * At the playback to wire boundary, [Dsl] is denormalized to a stable synthetic name (the playback
 * context allocates one via `registerKatalyst`), so the wire-level [VoiceData] still carries
 * `katalyst: String?`.
 *
 * Mirror of [MasterValue] / [PipelineValue] / [SoundValue]. NOT a wire type (no `@WireFormat`, no
 * `@WireName`): it never crosses the worklet boundary, it is denormalized to `katalyst: String?`
 * first. Authoring-layer only.
 */
sealed interface KatalystValue {

    /** Chain referenced by a stable name (a pre-registered custom chain). */
    data class Named(val name: String) : KatalystValue

    /** Chain defined inline as a [KatalystDsl] stage list. */
    data class Dsl(val katalyst: KatalystDsl) : KatalystValue {
        /**
         * The synthetic wire name of [katalyst], computed on first read and kept.
         *
         * Both consumers on the per-event path ask for it: `SprudelVoiceData.toVoiceData`
         * denormalizes to it, and the playback's inline-DSL sweep looks it up. Without this each of
         * them would hash the whole stage list again for every event of every pattern that carries
         * a chain; a pattern hands out one instance for all its events, so once per instance is the
         * right budget.
         *
         * Lazy, not eager: constructing a value must not touch the process-wide identity map, or
         * the map would allocate names for chains nobody ever plays, in construction order rather
         * than in use order.
         *
         * Not part of [equals] or [hashCode] (it is not a constructor property), which is what
         * keeps two structurally equal chains one chain.
         */
        val name: String by lazy { katalyst.uniqueId() }
    }
}
