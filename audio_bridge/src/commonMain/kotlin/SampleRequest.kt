/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Identifies one sample — and, by convention, a **system-wide unique key**: the same
 * `[bank, sound, index, note]` must always resolve to the same PCM, regardless of which playback
 * requests it. That invariant is what makes the shared `SampleStore` (one cache for the backend's
 * lifetime) safe — two playbacks asking for the same key get the same sound, never a clash.
 *
 * Unlike inline oscillators (keyed by content hash → clash-proof by construction), samples are keyed
 * by NAME, so the invariant holds by convention only. Any future custom/uploaded sample must use a
 * globally-unique, content-derived key (namespace or hash), never a bare reusable name — otherwise it
 * collides in the shared store (first load wins; others silently get the wrong sound).
 */
@WireFormat
data class SampleRequest(
    /** Name of the requested bank ... null means default sounds */
    val bank: String?,
    /** Name of the requested sound */
    val sound: String?,
    /** Index of the requested variant (if any) */
    val index: Int?,
    /** Note at which the sample would be played. Helps to find the best sample. */
    val note: String?,
)
