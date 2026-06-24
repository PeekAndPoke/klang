/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Playground entry point for testing real-time JVM audio playback.
 */
suspend fun main() {
    println("Runtime Name: ${System.getProperty("java.runtime.name")}")
    println("VM Name:      ${System.getProperty("java.vm.name")}")
    println("VM Vendor:    ${System.getProperty("java.vendor")}")
    println("VM Version:   ${System.getProperty("java.vm.version")}")

    val samples = Samples.create(catalogue = SampleCatalogue.default)
    val playerOptions = KlangPlayer.Options(
        samples = samples,
        sampleRate = 48_000,
    )

    val player = klangPlayer(options = playerOptions)

    val scripting = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    val pattern = SprudelPattern.compile(scripting, BuiltInSongs.derSchmetterling.code)!!
//    val pattern = SprudelPattern.compile(scripting, """
//  n(`<[7 4 2 <4 -1 4 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [1 2@3] [2 6@3]>]!4
//      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [-1 -3] 0 <[4 6] [2 3]>] [<7 4> [-5 -6] -7 [-2 <3 -1>]]>/4`)
//      .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.00 0.95!3 0.98 0.95!3".fast(2))
//      .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(9).detune(0.10)
//     .adsr("0.02:0.1:0.5:0.1").adsrCurves("exp:exp:scurve")
//    """.trimIndent())!!

    println("Starting playback...")
    val playback = player.play(pattern)
    playback.start(KlangCyclicPlayback.Options(rpm = 34.0))

    delay(600.seconds)

    playback.stop()
    player.shutdown()

    println("Done")
}
