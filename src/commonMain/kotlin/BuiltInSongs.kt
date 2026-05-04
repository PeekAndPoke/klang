@file:Suppress("unused")

package io.peekandpoke.klang

import io.peekandpoke.klang.builtinsongs.aTruthWorthLyingForSong
import io.peekandpoke.klang.builtinsongs.derSchmetterlingSong
import io.peekandpoke.klang.builtinsongs.drunkenSailorSong
import io.peekandpoke.klang.builtinsongs.finalFantasy7PreludeSong
import io.peekandpoke.klang.builtinsongs.irishLamentSong
import io.peekandpoke.klang.builtinsongs.irishLamentTechnoSong
import io.peekandpoke.klang.builtinsongs.sakuraSong
import io.peekandpoke.klang.builtinsongs.smallTownBoySong
import io.peekandpoke.klang.builtinsongs.soundOfTheSeaSong
import io.peekandpoke.klang.builtinsongs.strangerThingsSong
import io.peekandpoke.klang.builtinsongs.tetrisSong

/**
 * Registry of all built-in songs.
 *
 * Each song lives in its own file under `builtinsongs/`. This object is the
 * single canonical access point — external callers should always go through
 * [BuiltInSongs.derSchmetterling] etc., never through the per-file vals
 * (which are `internal`).
 */
object BuiltInSongs {

    const val PREFIX = "builtin-song"

    val derSchmetterling: Song = derSchmetterlingSong
    val tetris: Song = tetrisSong
    val soundOfTheSea: Song = soundOfTheSeaSong
    val sakura: Song = sakuraSong
    val aTruthWorthLyingFor: Song = aTruthWorthLyingForSong
    val strangerThings: Song = strangerThingsSong
    val irishLamentTechno: Song = irishLamentTechnoSong
    val irishLament: Song = irishLamentSong
    val finalFantasy7Prelude: Song = finalFantasy7PreludeSong
    val smallTownBoy: Song = smallTownBoySong
    val drunkenSailor: Song = drunkenSailorSong

    val songs: List<Song> = listOf(
        derSchmetterling,
        tetris,
        soundOfTheSea,
        sakura,
        aTruthWorthLyingFor,
        strangerThings,
        irishLamentTechno,
        irishLament,
        finalFantasy7Prelude,
        smallTownBoy,
        drunkenSailor,
    )
}
