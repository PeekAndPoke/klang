/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Authoring-layer representation of a sound reference.
 *
 * A voice may select its sound either by name (looking up a sample bank or
 * a pre-registered ignitor) or by inlining an [IgnitorDsl] tree directly.
 *
 * At the playback → wire boundary, [Osc] is denormalized to a stable synthetic
 * name — the playback context allocates one via `registerIgnitor` — so the
 * wire-level [VoiceData] still carries `sound: String?` and the BE protocol
 * stays unchanged.
 *
 * NOT a wire type (no `@WireFormat`, no `@WireName`): it never crosses the worklet boundary — it is
 * denormalized to `sound: String?` first. Authoring-layer only.
 */
sealed interface SoundValue {

    /** Sound referenced by a stable name (sample bank entry, pre-registered ignitor, etc.). */
    data class Named(val name: String) : SoundValue

    /** Sound defined inline as an ignitor signal graph. */
    data class Osc(val osc: IgnitorDsl) : SoundValue
}
