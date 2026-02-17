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
                it.type = KlangCommLink.Cmd.Cleanup.SERIAL_NAME
                it[KlangCommLink.Cmd::playbackId.name] = playbackId
            }

            is KlangCommLink.Cmd.ClearScheduled -> jsObject {
                it.type = KlangCommLink.Cmd.ClearScheduled.SERIAL_NAME
                it[KlangCommLink.Cmd::playbackId.name] = playbackId
            }

            is KlangCommLink.Cmd.ReplaceVoices -> jsObject {
                it.type = KlangCommLink.Cmd.ReplaceVoices.SERIAL_NAME
                it[KlangCommLink.Cmd::playbackId.name] = playbackId
                it[KlangCommLink.Cmd.ReplaceVoices::voices.name] = voices.map { v -> v.encode() }.toTypedArray()
            }

            is KlangCommLink.Cmd.ScheduleVoice -> jsObject {
                it.type = KlangCommLink.Cmd.ScheduleVoice.SERIAL_NAME
                it[KlangCommLink.Cmd::playbackId.name] = playbackId
                it[KlangCommLink.Cmd.ScheduleVoice::voice.name] = voice.encode()
                it[KlangCommLink.Cmd.ScheduleVoice::clearScheduled.name] = clearScheduled
            }

            is KlangCommLink.Cmd.Sample.NotFound -> jsObject {
                it.type = KlangCommLink.Cmd.Sample.NotFound.SERIAL_NAME
                it[KlangCommLink.Cmd.Sample.NotFound::req.name] = req.encode()
            }

            is KlangCommLink.Cmd.Sample.Complete -> jsObject {
                it.type = KlangCommLink.Cmd.Sample.Complete.SERIAL_NAME
                it[KlangCommLink.Cmd.Sample.Complete::req.name] = req.encode()
                it[KlangCommLink.Cmd.Sample.Complete::note.name] = note
                it[KlangCommLink.Cmd.Sample.Complete::pitchHz.name] = pitchHz
                it[KlangCommLink.Cmd.Sample.Complete::sample.name] = sample.encode()
            }

            is KlangCommLink.Cmd.Sample.Chunk -> jsObject {
                it.type = KlangCommLink.Cmd.Sample.Chunk.SERIAL_NAME
                it[KlangCommLink.Cmd.Sample.Chunk::req.name] = req.encode()
                it[KlangCommLink.Cmd.Sample.Chunk::note.name] = note
                it[KlangCommLink.Cmd.Sample.Chunk::pitchHz.name] = pitchHz
                it[KlangCommLink.Cmd.Sample.Chunk::sampleRate.name] = sampleRate
                it[KlangCommLink.Cmd.Sample.Chunk::meta.name] = meta.encode()
                it[KlangCommLink.Cmd.Sample.Chunk::totalSize.name] = totalSize
                it[KlangCommLink.Cmd.Sample.Chunk::isLastChunk.name] = isLastChunk
                it[KlangCommLink.Cmd.Sample.Chunk::chunkOffset.name] = chunkOffset
                it[KlangCommLink.Cmd.Sample.Chunk::data.name] = data
            }
        }
    }

    private fun decodeCmd(msg: dynamic): KlangCommLink.Cmd {
        return when (val type = msg.type) {
            KlangCommLink.Cmd.Cleanup.SERIAL_NAME -> KlangCommLink.Cmd.Cleanup(
                playbackId = msg[KlangCommLink.Cmd::playbackId.name],
            )

            KlangCommLink.Cmd.ClearScheduled.SERIAL_NAME -> KlangCommLink.Cmd.ClearScheduled(
                playbackId = msg[KlangCommLink.Cmd::playbackId.name],
            )

            KlangCommLink.Cmd.ReplaceVoices.SERIAL_NAME -> KlangCommLink.Cmd.ReplaceVoices(
                playbackId = msg[KlangCommLink.Cmd::playbackId.name],
                voices = (msg[KlangCommLink.Cmd.ReplaceVoices::voices.name] as Array<*>).map {
                    decodeScheduledVoice(it!!)
                }
            )

            KlangCommLink.Cmd.ScheduleVoice.SERIAL_NAME -> KlangCommLink.Cmd.ScheduleVoice(
                playbackId = msg[KlangCommLink.Cmd::playbackId.name],
                voice = decodeScheduledVoice(msg[KlangCommLink.Cmd.ScheduleVoice::voice.name]),
                clearScheduled = msg[KlangCommLink.Cmd.ScheduleVoice::clearScheduled.name] as? Boolean ?: false
            )

            KlangCommLink.Cmd.Sample.NotFound.SERIAL_NAME -> KlangCommLink.Cmd.Sample.NotFound(
                req = decodeSampleRequest(msg[KlangCommLink.Cmd.Sample.NotFound::req.name])
            )

            KlangCommLink.Cmd.Sample.Complete.SERIAL_NAME -> KlangCommLink.Cmd.Sample.Complete(
                req = decodeSampleRequest(msg[KlangCommLink.Cmd.Sample.Complete::req.name]),
                note = msg[KlangCommLink.Cmd.Sample.Complete::note.name],
                pitchHz = msg[KlangCommLink.Cmd.Sample.Complete::pitchHz.name],
                sample = decodeMonoSamplePcm(msg[KlangCommLink.Cmd.Sample.Complete::sample.name])
            )

            KlangCommLink.Cmd.Sample.Chunk.SERIAL_NAME -> KlangCommLink.Cmd.Sample.Chunk(
                req = decodeSampleRequest(msg[KlangCommLink.Cmd.Sample.Chunk::req.name]),
                note = msg[KlangCommLink.Cmd.Sample.Chunk::note.name],
                pitchHz = msg[KlangCommLink.Cmd.Sample.Chunk::pitchHz.name],
                sampleRate = msg[KlangCommLink.Cmd.Sample.Chunk::sampleRate.name],
                meta = decodeSampleMetadata(msg[KlangCommLink.Cmd.Sample.Chunk::meta.name]),
                totalSize = msg[KlangCommLink.Cmd.Sample.Chunk::totalSize.name],
                isLastChunk = msg[KlangCommLink.Cmd.Sample.Chunk::isLastChunk.name],
                chunkOffset = msg[KlangCommLink.Cmd.Sample.Chunk::chunkOffset.name],
                data = msg[KlangCommLink.Cmd.Sample.Chunk::data.name],
            )

            else -> error("Unknown cmd type: $type")
        }
    }

    private fun MonoSamplePcm.encode(): dynamic = jsObject {
        it[MonoSamplePcm::sampleRate.name] = sampleRate
        it[MonoSamplePcm::pcm.name] = pcm
        it[MonoSamplePcm::meta.name] = meta.encode()
    }

    private fun decodeMonoSamplePcm(obj: dynamic): MonoSamplePcm {
        return MonoSamplePcm(
            sampleRate = obj[MonoSamplePcm::sampleRate.name],
            pcm = obj[MonoSamplePcm::pcm.name],
            meta = decodeSampleMetadata(obj[MonoSamplePcm::meta.name])
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
