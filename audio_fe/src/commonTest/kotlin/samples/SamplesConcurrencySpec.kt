/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * [Samples]' caches under concurrent requests (audit L9-13, and L9-06's side remark): the preloader asks for every
 * sample of a song at once, on the JVM from several threads. Each sample is downloaded and decoded once, whatever the
 * number of requests that meet inside its load, and every request gets it.
 */
class SamplesConcurrencySpec : StringSpec({

    /** Eight sounds in one bank, each its own file; every note of a sound picks that one file, as a pitched zone does. */
    class Rig {
        private val lock = KlangLock()
        val downloads = mutableMapOf<String, Int>()
        var decodes = 0

        private fun sound(key: String) = object : Samples.SoundProvider {
            override val key: String = key
            override val variantCount: Int = 1
            override val sampleType: Samples.SampleType = Samples.SampleType.PITCHED
            private val file = Samples.Sample.FromUrl(note = null, pitchHz = 440.0, url = "mem://$key")

            override suspend fun provide(request: SampleRequest): Samples.ResolvedSample =
                Samples.ResolvedSample(request = request, sample = file)
        }

        val samples = Samples(
            index = Samples.Index(banks = listOf(Samples.Bank(key = "b", sounds = (0 until 8).map { sound("s$it") }))),
            loader = object : AssetLoader {
                override suspend fun download(uri: String): ByteArray {
                    lock.withLock { downloads[uri] = (downloads[uri] ?: 0) + 1 }
                    // A load that suspends: the other requests for this file arrive while it runs
                    delay(20)

                    return ByteArray(4)
                }
            },
            decoder = object : AudioDecoder {
                override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm {
                    lock.withLock { decodes++ }

                    return MonoSamplePcm(sampleRate = 48000, pcm = DoubleArray(16))
                }
            },
        )
    }

    "64 requests for 8 files at once, on the default dispatcher: each file loaded and decoded once, every request served" {
        val rig = Rig()

        // Eight notes of each of the eight sounds: 64 requests, eight per file
        val requests = (0 until 64).map { i -> SampleRequest(bank = "b", sound = "s${i % 8}", index = 0, note = "n${i / 8}") }

        val loaded = coroutineScope {
            requests.map { req -> async(Dispatchers.Default) { rig.samples.get(req) } }.awaitAll()
        }

        loaded.count { it?.pcm != null } shouldBe 64
        rig.downloads.size shouldBe 8
        rig.downloads.values.toSet() shouldBe setOf(1)
        rig.decodes shouldBe 8
    }
})
