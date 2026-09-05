/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.tones.Tones
import kotlinx.serialization.Serializable

/** Sound font index */
data class SoundfontIndex(
    val name: String,
    val baseUrl: String,
    val entries: Map<String, List<Variant>>,
) {

    @Serializable
    data class Variant(
        /** The name of the variant */
        val name: String,
        /** The file with the sound font content for the variant relative to [SoundfontIndex.baseUrl] */
        val file: String,
        /** Optional information about the original source of the soundfont */
        val source: Source?,
    ) {
        @Serializable
        data class Source(
            val baseUrl: String,
            val file: String,
        )
    }

    @Serializable
    data class SoundData(
        val zones: List<Zone>,
    ) {
        @Serializable
        data class Zone(
            val midi: Int, // 21,
            val originalPitch: Double, // 6000,
            val keyRangeLow: Int, // 0,
            val keyRangeHigh: Int, // 60,
            val loopStart: Int, // 36629,
            val loopEnd: Int, // 182237,
            val coarseTune: Int, // 0,
            val fineTune: Int, // 0,
            val sampleRate: Int, // 22050,
            val ahdsr: Boolean, // false,
            val file: String, // "SUQzBAAAAAAAI1RTU...",
            val anchor: Double, // 6.46648502
        ) {
            /** Effective pitch in cents, applying coarseTune (semitones) and fineTune (cents). */
            fun effectivePitchCents(): Double = originalPitch + coarseTune * 100.0 + fineTune

            /** Effective pitch in Hz, applying coarseTune and fineTune. */
            fun effectivePitchHz(): Double = Tones.midiToFreq(effectivePitchCents() / 100.0)

            /**
             * The sample's playback metadata: its loop, and a VCA envelope that stays out of the way.
             *
             * **A loop is a loop.** SoundFont 2 has no notion of a "fake" loop: whenever
             * `loopEnd > loopStart` the zone loops that region while the note is held, full stop.
             * An earlier version rejected loops shorter than 50 ms as "likely fake, for a percussive
             * sound" — and that discarded **45 % of all loops in the shipped corpus** (2 698 of 5 959
             * zones), because a 1–5 ms loop is a *single-cycle sustain loop*, the standard technique
             * for a sustained tone: a short attack sample ending in one or two perfectly periodic
             * cycles that repeat indefinitely. JCLive's entire accordion is built that way, and under
             * the heuristic it fell back to a percussive envelope and died after half a second.
             * The case the heuristic feared — a 10 ms loop parked in a long-decayed tail, as on the
             * nylon guitar — is harmless when honoured: the sample decays naturally, then loops
             * near-silence until note-off, which is exactly what every SF2 player does.
             *
             * **The sample IS the envelope.** Its own attack is the attack (which is why playback
             * starts at frame 0, see `VoiceFactory`), its loop or its natural decay is the sustain.
             * The synthesized ADSR this used to hand out fought that: the "percussive" shape cut a
             * four-second guitar ring at 0.5 s, and the "sustain" shape's 10 ms attack softened the
             * transient. The `ahdsr` flag is a boolean on every zone of the corpus and never carries
             * a curve, so there is nothing from the font to honour either. What a sampler needs from
             * the VCA is only a short release, so a looping sample can be cut on note-off without a
             * click. A user's `.adsr()` still merges over this and wins wherever it is set.
             */
            fun getSampleMetadata(): SampleMetadata {
                val loop = if (loopEnd > loopStart) {
                    SampleMetadata.LoopRange(
                        startSec = loopStart.toDouble() / sampleRate,
                        endSec = loopEnd.toDouble() / sampleRate,
                    )
                } else {
                    null
                }

                return SampleMetadata(
                    anchor = anchor,
                    loop = loop,
                    adsr = TRANSPARENT_VCA,
                )
            }

            companion object {
                /**
                 * Release for the transparent VCA, in seconds: the shortest cut that is click-free
                 * and still reads as a note ending rather than a splice. A by-ear knob — a bowed
                 * string would take longer, a reed shorter — but samplers converge on this range as
                 * a default, and anything instrument-specific belongs in the user's `.adsr()`.
                 */
                const val SOUNDFONT_RELEASE_SEC: Double = 0.05

                /** Attack 0, decay 0, sustain 1: the VCA passes the sample through untouched until note-off. */
                val TRANSPARENT_VCA: AdsrDef = AdsrDef.Std(
                    attack = 0.0,
                    decay = 0.0,
                    sustain = 1.0,
                    release = SOUNDFONT_RELEASE_SEC,
                )
            }
        }
    }
}
