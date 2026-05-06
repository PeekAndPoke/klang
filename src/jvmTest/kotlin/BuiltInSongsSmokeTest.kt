package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlib.KlangScriptOsc
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Smoke test for BuiltInSongs that have been refactored to use the new
 * `export name = expr` form (and for the cross-song remix that imports
 * named parts from another built-in entry).
 *
 * Confirms each song compiles to a non-null SprudelPattern under the same
 * engine setup the user-facing app uses (stdlib + sprudel + builtin songs).
 */
class BuiltInSongsSmokeTest : StringSpec({

    // Engine matches the Cli's `compilePattern` setup: sprudel registered,
    // built-in songs registered as modules, and a no-op Osc registrar so
    // songs that call `Osc.register(...)` (Sakura, Irish Lament, ...) compile
    // without an attached audio player.
    fun engine() = klangScript {
        attrs[KlangScriptOsc.REGISTRAR_KEY] = { name: String, _: IgnitorDsl -> name }
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    "tetris (rewritten with export declarations) compiles to a SprudelPattern" {
        val pattern = SprudelPattern.compile(engine(), BuiltInSongs.tetris.code)
        pattern.shouldNotBeNull()
    }

    "tetris-remix (imports leadPattern + bassPattern from peekandpoke/tetris) compiles to a SprudelPattern" {
        val pattern = SprudelPattern.compile(engine(), BuiltInSongs.tetrisRemix.code)
        pattern.shouldNotBeNull()
    }

    "every BuiltInSongs entry compiles to a SprudelPattern" {
        val songs = BuiltInSongs.songs
        for (song in songs) {
            val pattern = SprudelPattern.compile(engine(), song.code)
            withClue(song) { pattern.shouldNotBeNull() }
        }
    }
})

private inline fun <T> withClue(clue: Any, block: () -> T): T = io.kotest.assertions.withClue(clue, block)
