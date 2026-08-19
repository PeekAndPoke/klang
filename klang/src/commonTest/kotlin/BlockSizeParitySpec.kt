/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader

/**
 * Every host must render at the same block size, because block size is a **tone** parameter — it
 * sets the analog-drift update rate (`VoiceFactory.driftUpdateRate = sampleRate / blockFrames`), the
 * filter-smoothing granularity, and the late-voice drop window. The browser worklet is handed 128 by
 * Web Audio and cannot choose, so everything else has to follow it or a render stops sounding like
 * live playback. See `docs/tasks-archive/2026-08/20260807-block-size-parity.md`.
 *
 * The failure this guards is silent: someone bumps one default to make an offline render faster, the
 * whole suite stays green, and WAVs quietly drift away from the browser again. That is exactly how
 * the 512-vs-128 split got in.
 */
class BlockSizeParitySpec : StringSpec({

    val silentPattern = object : KlangPattern {
        override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double) = emptyList<KlangPatternEvent>()
    }

    "the live player defaults to the canonical render quantum" {
        // Reads the ACTUAL default off a constructed Options. Comparing a named constant against
        // RENDER_QUANTUM_FRAMES would be a tautology — it can only fail if someone edits that
        // constant's own initializer, never if they change `blockSize`'s default to a literal,
        // which is precisely the edit that would put the JVM backend back on 512.
        val options = KlangPlayer.Options(
            samples = Samples(
                index = Samples.Index(banks = emptyList()),
                loader = object : AssetLoader {
                    override suspend fun download(uri: String): ByteArray? = null
                },
                decoder = NullAudioDecoder(),
            )
        )

        options.blockSize shouldBe AudioBackendContext.RENDER_QUANTUM_FRAMES
    }

    "the offline renderer actually emits blocks of the canonical render quantum" {
        // Behavioural, not a constant comparison: this is the path that produces WAV files, and a
        // WAV sounding unlike the browser is the whole defect being guarded. `onBlock` reports
        // interleaved stereo, so count == blockFrames * 2.
        val counts = mutableSetOf<Int>()

        KlangOfflineRenderer().render(
            pattern = silentPattern,
            cycles = 1,
            cyclesPerSecond = 4.0,
            tailSec = 0.0,
            onBlock = { _, count -> counts.add(count) },
        )

        counts shouldBe setOf(AudioBackendContext.RENDER_QUANTUM_FRAMES * 2)
    }

    "the canonical render quantum is the Web Audio render quantum" {
        // Pinned as a literal on purpose: the other two assertions only prove the defaults agree
        // with the constant, not that the constant still matches what the browser gives us.
        AudioBackendContext.RENDER_QUANTUM_FRAMES shouldBe 128
    }
})
