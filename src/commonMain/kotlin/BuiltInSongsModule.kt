package io.peekandpoke.klang

import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.builder.registerLibrary

/**
 * Register a single [Song] under an explicit klangscript import URI.
 *
 * The URI is the contract — it is permanent once published. Title renames,
 * `Song.id` renames, and Kotlin property renames must never change the URI.
 * To migrate, add a new entry while leaving the old line in place; both will
 * resolve to the same source.
 */
fun KlangScriptExtensionBuilder.registerBuiltInSong(song: Song, importName: String) {
    registerLibrary(importName, song.code)
}

/**
 * Register all [BuiltInSongs] entries as importable klangscript libraries
 * under their canonical Projekt-Klangbuch URIs.
 *
 * Explicit per-song mapping — adding a new song to [BuiltInSongs] does *not*
 * automatically publish it to the canon. A line below is the act of publishing.
 *
 * ```klangscript
 * import { song } from "peekandpoke/der-schmetterling"
 * play(song)
 *
 * // or pull a single named part once the entry exposes one:
 * import { bass } from "peekandpoke/der-schmetterling"
 * ```
 *
 * v0 of the resolver — local-only, derived from the in-memory [BuiltInSongs]
 * object. Forward-compatible: namespaced/versioned identifiers like
 * `peekandpoke/foo@1.0` parse cleanly today; remote resolution and version
 * pinning will plug in alongside.
 */
fun KlangScriptExtensionBuilder.registerBuiltInSongsAsModules() {
    registerBuiltInSong(BuiltInSongs.derSchmetterling, "peekandpoke/der-schmetterling")
    registerBuiltInSong(BuiltInSongs.tetris, "peekandpoke/tetris")
    registerBuiltInSong(BuiltInSongs.tetrisRemix, "peekandpoke/tetris-echo")
    registerBuiltInSong(BuiltInSongs.soundOfTheSea, "peekandpoke/sound-of-the-sea")
    registerBuiltInSong(BuiltInSongs.sakura, "peekandpoke/sakura")
    registerBuiltInSong(BuiltInSongs.aTruthWorthLyingFor, "peekandpoke/a-truth-worth-lying-for")
    registerBuiltInSong(BuiltInSongs.strangerThings, "peekandpoke/stranger-things")
    registerBuiltInSong(BuiltInSongs.irishLamentTechno, "peekandpoke/irish-lament-techno")
    registerBuiltInSong(BuiltInSongs.irishLament, "peekandpoke/irish-lament")
    registerBuiltInSong(BuiltInSongs.finalFantasy7Prelude, "peekandpoke/final-fantasy-7-prelude")
    registerBuiltInSong(BuiltInSongs.smallTownBoy, "peekandpoke/small-town-boy")
    registerBuiltInSong(BuiltInSongs.drunkenSailor, "peekandpoke/drunken-sailor")
}
