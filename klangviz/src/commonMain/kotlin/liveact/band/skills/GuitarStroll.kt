/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

/**
 * The walk with the guitar: a member who plays a strung instrument walks about the front of the stage while they play,
 * slowly, the instrument on its strap (the maintainer, 2026-11-27: "Can we make Sirene walk around slowly with the
 * guitar in 'Noch seltsamere Dinge' ... basically a new 'walk with guitar' skill"). The way is the trumpet player's
 * stroll route, walked at two thirds of her pace; in rounds, standing at their place between them, from twelve
 * seconds into a long phrase, and home again within the silence after the playing (see `planStringStrolls`). The
 * camera chooses no look on them while they stroll (see `Notstrom.seenAt`). On a stroll they look as the trumpet
 * player does on hers: at the one they visit, and they stare at the camera they walk to (the maintainer, 2026-11-27:
 * "add the looks while she strolls"; see `StringStroller.looksAt`).
 *
 * It acts only where the member has a string seat in the song, stands at their place (one who sits stays seated),
 * and the song has no trumpet player, who strolls herself; a song can leave it out (`Score.resting`). The camera
 * then answers the one who strolls as it answers the trumpet player (see `Score.strolls`).
 */
data object GuitarStroll : Skill
