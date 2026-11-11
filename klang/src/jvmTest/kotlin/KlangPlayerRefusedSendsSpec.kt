/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * A command the player cannot hand over, its control ring full, is dropped: counted in [KlangDiagnostics.refusedSends]
 * and logged once per burst of refusals, not per message (KLANG-00431 D10, audit L1-11: no sender read the result).
 * JVM only: the log is read off `System.out`.
 */
class KlangPlayerRefusedSendsSpec : StringSpec({

    "a refused send is counted, and the log says so once per burst" {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // A backend that never comes: nothing drains the control ring but this spec
        val player = KlangPlayer(
            options = KlangPlayer.Options(
                samples = Samples(
                    index = Samples.Index(banks = emptyList()),
                    loader = object : AssetLoader {
                        override suspend fun download(uri: String): ByteArray? = null
                    },
                    decoder = NullAudioDecoder(),
                ),
            ),
            backendFactory = { awaitCancellation() },
            scope = scope,
            fetcherDispatcher = Dispatchers.Default,
            backendDispatcher = Dispatchers.Default,
            callbackDispatcher = Dispatchers.Default,
        )
        val captured = ByteArrayOutputStream()
        val console = System.out

        System.setOut(PrintStream(captured, true))

        try {
            val cmd = KlangCommLink.Cmd.Cleanup(playbackId = "p")

            // The ring of 8192 holds 8191
            repeat(8191) { player.sendControl(cmd) }
            player.diagnostics.refusedSends shouldBe 0.0

            // A burst of three refused, a drained slot accepted, a burst of two refused
            repeat(3) { player.sendControl(cmd) }
            player.commLink.backend.control.receive()
            player.sendControl(cmd)
            repeat(2) { player.sendControl(cmd) }

            player.diagnostics.refusedSends shouldBe 5.0
        } finally {
            System.setOut(console)
            player.shutdown()
            scope.cancel()
        }

        captured.toString().lines().count { it.contains("the control ring is full") } shouldBe 2
    }
})
