/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.WorkletContract.requireSchema
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.wire.WIRE_SCHEMA_HASH
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.decode_KlangCommLink_Feedback
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Cmd
import io.peekandpoke.klang.audio_bridge.wire.encode_KlangCommLink_Feedback
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessagePort

/**
 * Frontend ⇄ audio-worklet message codec.
 *
 * Commands ([KlangCommLink.Cmd]) and feedback ([KlangCommLink.Feedback]) are encoded/decoded by the
 * KSP-generated "trust the input" wire codec (`:audio-wire-codec-ksp`, generated into `audio_bridge` jsMain).
 * That replaced kotlinx `encodeToDynamic`/`decodeFromDynamic` + hand-built marshalling, which dominated the
 * worklet's audio-thread decode (~64 µs/voice → ~0.4 µs). See KLANG-00103 (worklet codec ksp).
 *
 * Each message carries the generated [WIRE_SCHEMA_HASH] under `v`; [requireSchema] rejects a mismatch so a
 * stale-cached worklet meeting a fresh frontend (different build) fails loudly instead of decoding garbage.
 */
object WorkletContract {
    /**
     * Envelope key carrying the wire-schema hash. A NON-identifier (`#v`) so it can never collide with a
     * data-class field name on the stamped root object — same invariant as the codec's `#t` type-tag.
     */
    private const val PROP_VERSION = "#v"

    fun MessagePort.sendCmd(cmd: KlangCommLink.Cmd) {
        postMessage(stamp(encode_KlangCommLink_Cmd(cmd)))
    }

    /**
     * Sends one whole sample as ONE message whose PCM buffer is TRANSFERRED, not cloned (KLANG-00431 D5, audit L9-09):
     * the worklet receives the buffer as it is and neither allocates nor copies on the audio thread. A transferred
     * buffer is gone on this side (detached, length 0), and the PCM of a `Complete` is the frontend's own cache entry
     * (`Samples` keeps the decoded PCM and hands the same array to every request), so what is transferred is a copy
     * made here, on the main thread, by [copyPcm]. False when that copy could not be allocated: nothing was sent.
     */
    fun MessagePort.sendSample(
        cmd: KlangCommLink.Cmd.Sample.Complete,
        copyPcm: (DoubleArray) -> DoubleArray = { it.copyOf() },
    ): Boolean {
        val pcm = try {
            copyPcm(cmd.sample.pcm)
        } catch (e: Throwable) {
            return false
        }

        val own = cmd.copy(sample = MonoSamplePcm(sampleRate = cmd.sample.sampleRate, pcm = pcm, meta = cmd.sample.meta))

        // On Kotlin/JS a DoubleArray is a Float64Array: its buffer is what moves
        postMessage(stamp(encode_KlangCommLink_Cmd(own)), arrayOf(pcm.asDynamic().buffer))

        return true
    }

    fun decodeCmd(msg: MessageEvent): KlangCommLink.Cmd {
        val data = msg.data
        requireSchema(data)
        return decode_KlangCommLink_Cmd(data)
    }

    fun MessagePort.sendFeed(feedback: KlangCommLink.Feedback) {
        postMessage(stamp(encode_KlangCommLink_Feedback(feedback)))
    }

    fun decodeFeed(msg: MessageEvent): KlangCommLink.Feedback {
        val data = msg.data
        requireSchema(data)
        return decode_KlangCommLink_Feedback(data)
    }

    /** Stamps the wire-schema hash onto the (freshly-built, single-owner) envelope object. */
    private fun stamp(obj: dynamic): dynamic {
        obj[PROP_VERSION] = WIRE_SCHEMA_HASH
        return obj
    }

    private fun requireSchema(data: dynamic) {
        val v: Int? = data[PROP_VERSION]
        if (v != WIRE_SCHEMA_HASH) {
            error(
                "Worklet wire-schema mismatch: message has v=$v, this build expects $WIRE_SCHEMA_HASH. " +
                        "Frontend and audio worklet are different builds (stale cached worklet?) — reload."
            )
        }
    }
}
