package io.peekandpoke.klang.audio_be.worklet

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessagePort

@Suppress("OPT_IN_USAGE")
object WorkletContract {

    private val codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun MessagePort.sendCmd(cmd: KlangCommLink.Cmd) {
        // console.log("Sending message to worklet:", cmd)

        // Json serialize the payload
        val obj = codec.encodeToDynamic(KlangCommLink.Cmd.serializer(), cmd)

        val transfer = when (cmd) {
            is KlangCommLink.Cmd.Sample -> listOfNotNull(cmd.data?.pcm).toTypedArray()
            else -> emptyArray()
        }

        postMessage(obj, transfer)
    }

    fun decodeCmd(msg: MessageEvent): KlangCommLink.Cmd {
        val decoded = codec.decodeFromDynamic(KlangCommLink.Cmd.serializer(), msg.data)

        return decoded
    }

    fun MessagePort.sendFeed(feedback: KlangCommLink.Feedback) {
        // console.log("[WORKLET] Sending feedback to frontend:", feedback)

        val obj = codec.encodeToDynamic(KlangCommLink.Feedback.serializer(), feedback)

        postMessage(obj)
    }

    fun decodeFeed(msg: MessageEvent): KlangCommLink.Feedback {
        val decoded = codec.decodeFromDynamic(KlangCommLink.Feedback.serializer(), msg.data)

        return decoded
    }
}
