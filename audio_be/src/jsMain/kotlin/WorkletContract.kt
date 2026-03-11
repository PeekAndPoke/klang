package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.MessageEvent
import org.w3c.dom.MessagePort

@Suppress("OPT_IN_USAGE")
object WorkletContract {

    const val PROP_TYPE = "type"

    const val PROP_CHUNK_OFFSET = "chunkOffset"
    const val PROP_CLEAR_SCHEDULED = "clearScheduled"
    const val PROP_DATA = "data"
    const val PROP_IS_LAST_CHUNK = "isLastChunk"
    const val PROP_META = "meta"
    const val PROP_NOTE = "note"
    const val PROP_PCM = "pcm"
    const val PROP_PITCH_HZ = "pitchHz"
    const val PROP_PLAYBACK_ID = "playbackId"
    const val PROP_REQ = "req"
    const val PROP_SAMPLE = "sample"
    const val PROP_SAMPLE_RATE = "sampleRate"
    const val PROP_TOTAL_SIZE = "totalSize"
    const val PROP_VOICE = "voice"
    const val PROP_VOICES = "voices"

    private val codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun MessagePort.sendCmd(cmd: KlangCommLink.Cmd) {
        val obj = cmd.encode()

        postMessage(obj)
    }

    fun decodeCmd(msg: MessageEvent): KlangCommLink.Cmd {
        val decoded = decodeCmd(msg.data)

        return decoded
    }

    fun MessagePort.sendFeed(feedback: KlangCommLink.Feedback) {
        // println("Sending feedback: $feedback")

        val obj = codec.encodeToDynamic(KlangCommLink.Feedback.serializer(), feedback)

        postMessage(obj)
    }

    fun decodeFeed(msg: MessageEvent): KlangCommLink.Feedback {
        val decoded = codec.decodeFromDynamic(KlangCommLink.Feedback.serializer(), msg.data)

        return decoded
    }

    private fun KlangCommLink.Cmd.encode(): dynamic {
        return when (this) {
            is KlangCommLink.Cmd.Cleanup -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.Cleanup.SERIAL_NAME
                it[PROP_PLAYBACK_ID] = playbackId
            }

            is KlangCommLink.Cmd.ClearScheduled -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.ClearScheduled.SERIAL_NAME
                it[PROP_PLAYBACK_ID] = playbackId
            }

            is KlangCommLink.Cmd.ReplaceVoices -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.ReplaceVoices.SERIAL_NAME
                it[PROP_PLAYBACK_ID] = playbackId
                it[PROP_VOICES] = voices.map { v -> v.encode() }.toTypedArray()
            }

            is KlangCommLink.Cmd.ScheduleVoice -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.ScheduleVoice.SERIAL_NAME
                it[PROP_PLAYBACK_ID] = playbackId
                it[PROP_VOICE] = voice.encode()
                it[PROP_CLEAR_SCHEDULED] = clearScheduled
            }

            is KlangCommLink.Cmd.Sample.NotFound -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.Sample.NotFound.SERIAL_NAME
                it[PROP_REQ] = req.encode()
            }

            is KlangCommLink.Cmd.Sample.Complete -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.Sample.Complete.SERIAL_NAME
                it[PROP_REQ] = req.encode()
                it[PROP_NOTE] = note
                it[PROP_PITCH_HZ] = pitchHz
                it[PROP_SAMPLE] = sample.encode()
            }

            is KlangCommLink.Cmd.Sample.Chunk -> jsObject {
                it[PROP_TYPE] = KlangCommLink.Cmd.Sample.Chunk.SERIAL_NAME
                it[PROP_REQ] = req.encode()
                it[PROP_NOTE] = note
                it[PROP_PITCH_HZ] = pitchHz
                it[PROP_SAMPLE_RATE] = sampleRate
                it[PROP_META] = meta.encode()
                it[PROP_TOTAL_SIZE] = totalSize
                it[PROP_IS_LAST_CHUNK] = isLastChunk
                it[PROP_CHUNK_OFFSET] = chunkOffset
                it[PROP_DATA] = data
            }
        }
    }

    private fun decodeCmd(msg: dynamic): KlangCommLink.Cmd {
        return when (val type = msg[PROP_TYPE]) {
            KlangCommLink.Cmd.Cleanup.SERIAL_NAME -> KlangCommLink.Cmd.Cleanup(
                playbackId = msg[PROP_PLAYBACK_ID],
            )

            KlangCommLink.Cmd.ClearScheduled.SERIAL_NAME -> KlangCommLink.Cmd.ClearScheduled(
                playbackId = msg[PROP_PLAYBACK_ID],
            )

            KlangCommLink.Cmd.ReplaceVoices.SERIAL_NAME -> KlangCommLink.Cmd.ReplaceVoices(
                playbackId = msg[PROP_PLAYBACK_ID],
                voices = (msg[PROP_VOICES] as Array<*>).map { decodeScheduledVoice(it!!) }
            )

            KlangCommLink.Cmd.ScheduleVoice.SERIAL_NAME -> KlangCommLink.Cmd.ScheduleVoice(
                playbackId = msg[PROP_PLAYBACK_ID],
                voice = decodeScheduledVoice(msg[PROP_VOICE]),
                clearScheduled = msg[PROP_CLEAR_SCHEDULED] as? Boolean ?: false
            )

            KlangCommLink.Cmd.Sample.NotFound.SERIAL_NAME -> KlangCommLink.Cmd.Sample.NotFound(
                req = decodeSampleRequest(msg[PROP_REQ])
            )

            KlangCommLink.Cmd.Sample.Complete.SERIAL_NAME -> KlangCommLink.Cmd.Sample.Complete(
                req = decodeSampleRequest(msg[PROP_REQ]),
                note = msg[PROP_NOTE],
                pitchHz = msg[PROP_PITCH_HZ],
                sample = decodeMonoSamplePcm(msg[PROP_SAMPLE])
            )

            KlangCommLink.Cmd.Sample.Chunk.SERIAL_NAME -> KlangCommLink.Cmd.Sample.Chunk(
                req = decodeSampleRequest(msg[PROP_REQ]),
                note = msg[PROP_NOTE],
                pitchHz = msg[PROP_PITCH_HZ],
                sampleRate = msg[PROP_SAMPLE_RATE],
                meta = decodeSampleMetadata(msg[PROP_META]),
                totalSize = msg[PROP_TOTAL_SIZE],
                isLastChunk = msg[PROP_IS_LAST_CHUNK],
                chunkOffset = msg[PROP_CHUNK_OFFSET],
                data = msg[PROP_DATA],
            )

            else -> error("Unknown cmd type: $type")
        }
    }

    private fun MonoSamplePcm.encode(): dynamic = jsObject {
        it[PROP_SAMPLE_RATE] = sampleRate
        it[PROP_PCM] = pcm
        it[PROP_META] = meta.encode()
    }

    private fun decodeMonoSamplePcm(obj: dynamic): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = obj[PROP_SAMPLE_RATE],
            pcm = obj[PROP_PCM],
            meta = decodeSampleMetadata(obj[PROP_META])
        )
    }

    private fun ScheduledVoice.encode(): dynamic {
        return codec.encodeToDynamic(ScheduledVoice.serializer(), this)
    }

    private fun decodeScheduledVoice(obj: dynamic): ScheduledVoice {
        return codec.decodeFromDynamic(ScheduledVoice.serializer(), obj)
    }

    private fun SampleRequest.encode(): dynamic {
        return codec.encodeToDynamic(SampleRequest.serializer(), this)
    }

    private fun decodeSampleRequest(obj: dynamic): SampleRequest {
        return codec.decodeFromDynamic(SampleRequest.serializer(), obj)
    }

    private fun SampleMetadata.encode(): dynamic {
        return codec.encodeToDynamic(SampleMetadata.Companion.serializer(), this)
    }

    private fun decodeSampleMetadata(obj: dynamic): SampleMetadata {
        return codec.decodeFromDynamic(SampleMetadata.Companion.serializer(), obj)
    }

    fun jsObject(): dynamic = js("({})")

    fun <T> jsObject(block: (T) -> Unit): T {
        val obj = jsObject() as T
        block(obj)
        return obj
    }
}
