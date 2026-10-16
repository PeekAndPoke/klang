/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
 * Explicit per-song mapping in [builtInSongModules]: adding a new song to [BuiltInSongs] does *not*
 * automatically publish it to the canon. A line there is the act of publishing.
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
    builtInSongModules.forEach { (importName, song) -> registerBuiltInSong(song = song, importName = importName) }
}

/** The built-in songs published as modules, by import URI. A line below is the act of publishing. */
val builtInSongModules: Map<String, Song> = mapOf(
    "peekandpoke/der-schmetterling" to BuiltInSongs.derSchmetterling,
    "peekandpoke/tetris" to BuiltInSongs.tetris,
    "peekandpoke/tetris-echo" to BuiltInSongs.tetrisRemix,
    "peekandpoke/sound-of-the-sea" to BuiltInSongs.soundOfTheSea,
    "peekandpoke/sakura" to BuiltInSongs.sakura,
    "peekandpoke/a-truth-worth-lying-for" to BuiltInSongs.aTruthWorthLyingFor,
    "peekandpoke/stranger-things" to BuiltInSongs.strangerThings,
    "peekandpoke/irish-lament-techno" to BuiltInSongs.irishLamentTechno,
    "peekandpoke/irish-lament" to BuiltInSongs.irishLament,
    "peekandpoke/final-fantasy-7-prelude" to BuiltInSongs.finalFantasy7Prelude,
    "peekandpoke/small-town-boy" to BuiltInSongs.smallTownBoy,
    "peekandpoke/drunken-sailor" to BuiltInSongs.drunkenSailor,
)
