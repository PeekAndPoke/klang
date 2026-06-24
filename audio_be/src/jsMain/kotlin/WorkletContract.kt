/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.WorkletContract.requireSchema
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
 * worklet's audio-thread decode (~64 µs/voice → ~0.4 µs). See `docs/tasks/worklet-codec-ksp.md`.
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
