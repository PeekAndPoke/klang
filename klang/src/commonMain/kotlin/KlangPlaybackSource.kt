/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

/**
 * Who plays a song: a page's player, an example, Klang Amp. Its [id] stays the same over stop and play, where each
 * playback is new, so what a playback measures goes on where the last one from here stopped (the maintainer,
 * 2026-10-29: "would need an identifier that "survives" stop and play"): one line in the diagnostics' history
 * ([KlangDiagnostics]), not one per play. [name] is what plays, for people to read (a song's title); null for none.
 */
data class KlangPlaybackSource(
    val id: String,
    val name: String?,
)
