/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Registry for resolving and decoding samples.
 *
 * Threading:
 * - safe to call from scheduler thread to prefetch
 * - DO NOT call from the audio render loop unless you know it's already loaded (see getIfLoaded)
 */
class Samples(
    val index: Index,
    private val loader: AssetLoader,
    private val decoder: AudioDecoder,
) {
    companion object {
        // used for platform specific extension functions
    }

    enum class SampleType {
        /** Single sample - only one recording */
        SINGLE,

        /** Multiple recordings at different pitches - DSL picks by note */
        PITCHED,

        /** Multiple different versions - user selects variant */
        VARIANTS
    }

    interface SoundProvider {
        val key: String
        val variantCount: Int
        val sampleType: SampleType
        val noteRange: String? get() = null  // Override in implementations that support it

        suspend fun provide(request: SampleRequest): ResolvedSample?
    }

    /** A [sample] that was resolved from the [request]. */
    data class ResolvedSample(
        val request: SampleRequest,
        val sample: Sample,
    )

    /** A [sample] that was resolved from the [request] including the [pcm] audio data. */
    data class LoadedSample(
        val request: SampleRequest,
        val sample: Sample,
        val pcm: MonoSamplePcm?,
    )

    data class Index(
        val banks: List<Bank>,
        val aliases: Map<String, String> = emptyMap(),
    ) {
        val banksByKey: Map<String, Bank> = banks.associateBy { it.key }

        suspend fun resolve(request: SampleRequest): ResolvedSample? {
            // Resolve the sample provider (taking aliases into account)
            // Note there is a special bank with an empty name ""
            val resolvedBank = resolveBank(request.bank ?: "") ?: return null
            // Get the sound provider
            val sound = resolvedBank.getSound(request.sound ?: "") ?: return null
            // Try to provide the sound
            return sound.provide(request)
        }

        private fun resolveBank(key: String, visitedAliases: List<String> = emptyList()): Bank? {
            if (key in visitedAliases) {
                println("[Samples] Bank '$key' has an alias loop: ${visitedAliases.joinToString(" -> ")}.")
                return null
            }

            // Do we have a bank with this key?
            return when (val bank = banksByKey[key]) {
                // No? Check aliases
                null -> aliases[key]?.let { resolveBank(it, visitedAliases + key) }
                // Else try to load
                else -> bank
            }
        }
    }

    data class Bank(
        val key: String,
        val sounds: List<SoundProvider> = emptyList(),
    ) {
        val soundsByKey: Map<String, SoundProvider> = sounds.associateBy { it.key }

        fun getSound(key: String): SoundProvider? {
            return soundsByKey[key]
        }

        fun plusSound(sound: SoundProvider) = copy(
            sounds = sounds.plus(sound).distinctBy { it.key }
        )

        fun plusSounds(sounds: List<SoundProvider>) = copy(
            sounds = this.sounds.plus(sounds).distinctBy { it.key }
        )
    }

    sealed interface Sample {
        class FromUrl(
            override val note: String?,
            override val pitchHz: Double,
            val url: String,
        ) : Sample {
            override suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm? {
                println("Loading sample $url")

                return loader.download(url)?.let {
                    try {
                        decoder.decodeMonoFloatPcm(it)
                    } catch (e: Exception) {
                        println("Failed to decode sample $url: ${e.stackTraceToString()}")
                        null
                    }
                }
            }
        }

        class FromBytes(
            override val note: String?,
            override val pitchHz: Double,
            val sampleRate: Int,
            val bytes: ByteArray,
            val meta: SampleMetadata,
        ) : Sample {
            private var _pcm: MonoSamplePcm? = null

            override suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm? {
                return _pcm ?: getPcmInternal(decoder)?.also { _pcm = it }
            }

            private suspend fun getPcmInternal(decoder: AudioDecoder): MonoSamplePcm? {
                return decoder.decodeMonoFloatPcm(bytes)?.withMetadata(meta)
            }
        }

        /** Optional informal note name ... not used for anything */
        val note: String?

        /** The pitch of this sound in Hz. Use for playing this sound at different tones */
        val pitchHz: Double

        /** The pcm audio data */
        suspend fun getPcm(loader: AssetLoader, decoder: AudioDecoder): MonoSamplePcm?
    }

    /**
     * Guards the two caches: on the JVM several requests run on several threads at once (the preloader starts one
     * coroutine per request on the default pool), and on both platforms two requests for one sample meet inside a
     * suspending load. Each cache holds one [CompletableDeferred] per key, put under the lock by the first caller,
     * who loads it outside the lock; every later caller awaits that one load (audit L9-13; L9-06: one sample decoded
     * once, not once per concurrent request).
     */
    private val lock = KlangLock()

    private val resolveCache = mutableMapOf<SampleRequest, CompletableDeferred<ResolvedSample?>>()

    // Keyed by Sample instance identity — avoids duplicate decoding when multiple requests
    // resolve to the same underlying audio (e.g., pitched samples where C4 and D4 both pick C4.mp3)
    private val pcmCache = mutableMapOf<Sample, CompletableDeferred<MonoSamplePcm?>>()

    /**
     * Resolve a sample by the given [request].
     */
    suspend fun resolve(request: SampleRequest): ResolvedSample? {
        return once(cache = resolveCache, key = request) { index.resolve(request) }
    }

    /**
     * Resolves a sample by the given [request] and loads its PCM audio data.
     */
    suspend fun get(request: SampleRequest): LoadedSample? {
        val resolved = resolve(request) ?: return null

        val pcm = once(cache = pcmCache, key = resolved.sample) {
            resolved.sample.getPcm(loader, decoder)
        }

        return LoadedSample(
            request = resolved.request,
            sample = resolved.sample,
            pcm = pcm,
        )
    }

    /**
     * The value of [key] in [cache], loaded by [load] once: the first caller loads, the others await it. A load that
     * throws or gives null is not kept: its caller gets the throw (or the null), a waiting caller gets it too, and the
     * next caller loads again. A waiting caller whose loader was cancelled (its coroutine, not this one) loads again
     * itself.
     */
    private suspend fun <K : Any, V> once(
        cache: MutableMap<K, CompletableDeferred<V>>,
        key: K,
        load: suspend () -> V,
    ): V {
        while (true) {
            var mine = false
            val entry = lock.withLock {
                cache[key] ?: CompletableDeferred<V>().also {
                    cache[key] = it
                    mine = true
                }
            }

            if (mine) {
                try {
                    val value = load()

                    // A null (not found, a download that failed) is not kept, as before: the next caller tries again
                    if (value == null) {
                        lock.withLock { cache.remove(key) }
                    }

                    entry.complete(value)

                    return value
                } catch (t: Throwable) {
                    lock.withLock { cache.remove(key) }
                    entry.completeExceptionally(t)

                    throw t
                }
            }

            try {
                return entry.await()
            } catch (e: CancellationException) {
                // Either this coroutine was cancelled (rethrown here), or the one loading was: then load again
                currentCoroutineContext().ensureActive()
            }
        }
    }
}
