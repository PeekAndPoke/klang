/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.apps.common

import io.peekandpoke.klang.apps.common.builtinsongs.aTruthWorthLyingForSong
import io.peekandpoke.klang.apps.common.builtinsongs.derSchmetterlingAmpPerNoteSong
import io.peekandpoke.klang.apps.common.builtinsongs.derSchmetterlingSong
import io.peekandpoke.klang.apps.common.builtinsongs.drunkenSailorSong
import io.peekandpoke.klang.apps.common.builtinsongs.finalFantasy7PreludeSong
import io.peekandpoke.klang.apps.common.builtinsongs.greensleevesSong
import io.peekandpoke.klang.apps.common.builtinsongs.irishLamentSong
import io.peekandpoke.klang.apps.common.builtinsongs.irishLamentTechnoSong
import io.peekandpoke.klang.apps.common.builtinsongs.kokonSong
import io.peekandpoke.klang.apps.common.builtinsongs.sakuraSong
import io.peekandpoke.klang.apps.common.builtinsongs.sandsturmSong
import io.peekandpoke.klang.apps.common.builtinsongs.smallTownBoySong
import io.peekandpoke.klang.apps.common.builtinsongs.soundOfTheSeaSong
import io.peekandpoke.klang.apps.common.builtinsongs.strangerThingsSong
import io.peekandpoke.klang.apps.common.builtinsongs.tetrisRemixSong
import io.peekandpoke.klang.apps.common.builtinsongs.tetrisSong

/**
 * Registry of all built-in songs.
 *
 * Each song lives in its own file under `builtinsongs/`. This object is the
 * single canonical access point: external callers should always go through
 * [BuiltInSongs.derSchmetterling] etc., never through the per-file vals
 * (which are `internal`).
 */
object BuiltInSongs {

    const val PREFIX = "builtin-song"

    val derSchmetterling: Song = derSchmetterlingSong

    /** Der Schmetterling as it was before its amp moved to the cylinder (KLANG-00469): the reference to compare by ear. */
    val derSchmetterlingAmpPerNote: Song = derSchmetterlingAmpPerNoteSong

    val tetris: Song = tetrisSong
    val tetrisRemix: Song = tetrisRemixSong
    val soundOfTheSea: Song = soundOfTheSeaSong
    val sakura: Song = sakuraSong
    val aTruthWorthLyingFor: Song = aTruthWorthLyingForSong
    val strangerThings: Song = strangerThingsSong
    val irishLamentTechno: Song = irishLamentTechnoSong
    val sandsturm: Song = sandsturmSong
    val irishLament: Song = irishLamentSong
    val finalFantasy7Prelude: Song = finalFantasy7PreludeSong
    val smallTownBoy: Song = smallTownBoySong
    val drunkenSailor: Song = drunkenSailorSong
    val greensleeves: Song = greensleevesSong
    val kokon: Song = kokonSong

    val songs: List<Song> = listOf(
        finalFantasy7Prelude,
        kokon,
        sakura,
        derSchmetterling,
        derSchmetterlingAmpPerNote,
        soundOfTheSea,
        strangerThings,
        tetris,
        tetrisRemix,
        greensleeves,
        aTruthWorthLyingFor,
        irishLamentTechno,
        sandsturm,
        irishLament,
        smallTownBoy,
        drunkenSailor,
    )
}
