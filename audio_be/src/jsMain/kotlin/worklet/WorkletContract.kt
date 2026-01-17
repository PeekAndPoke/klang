package io.peekandpoke.klang.audio_be.worklet

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink.Cmd
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

    fun MessagePort.sendCmd(cmd: Cmd) {
        val obj = cmd.encode()

        postMessage(obj)
    }

    fun decodeCmd(msg: MessageEvent): Cmd {
        val decoded = decodeCmd(msg.data)

        return decoded
    }

    fun MessagePort.sendFeed(feedback: KlangCommLink.Feedback) {
        val obj = codec.encodeToDynamic(KlangCommLink.Feedback.serializer(), feedback)

        postMessage(obj)
    }

    fun decodeFeed(msg: MessageEvent): KlangCommLink.Feedback {
        val decoded = codec.decodeFromDynamic(KlangCommLink.Feedback.serializer(), msg.data)

        return decoded
    }

    private fun Cmd.encode(): dynamic {
        return when (this) {
            is Cmd.ScheduleVoice -> jsObject {
                it.type = Cmd.ScheduleVoice.SERIAL_NAME
                it[Cmd::playbackId.name] = playbackId
                it[Cmd.ScheduleVoice::voice.name] = voice.encode()
            }

            is Cmd.Sample.NotFound -> jsObject {
                it.type = Cmd.Sample.NotFound.SERIAL_NAME
                it[Cmd::playbackId.name] = playbackId
                it[Cmd.Sample.NotFound::req.name] = req.encode()
            }

            is Cmd.Sample.Complete -> jsObject {
                it.type = Cmd.Sample.Complete.SERIAL_NAME
                it[Cmd::playbackId.name] = playbackId
                it[Cmd.Sample.Complete::req.name] = req.encode()
                it[Cmd.Sample.Complete::note.name] = note
                it[Cmd.Sample.Complete::pitchHz.name] = pitchHz
                it[Cmd.Sample.Complete::sample.name] = sample.encode()
            }

            is Cmd.Sample.Chunk -> jsObject {
                it.type = Cmd.Sample.Chunk.SERIAL_NAME
                it[Cmd::playbackId.name] = playbackId
                it[Cmd.Sample.Chunk::req.name] = req.encode()
                it[Cmd.Sample.Chunk::note.name] = note
                it[Cmd.Sample.Chunk::pitchHz.name] = pitchHz
                it[Cmd.Sample.Chunk::sampleRate.name] = sampleRate
                it[Cmd.Sample.Chunk::meta.name] = meta.encode()
                it[Cmd.Sample.Chunk::totalSize.name] = totalSize
                it[Cmd.Sample.Chunk::isLastChunk.name] = isLastChunk
                it[Cmd.Sample.Chunk::chunkOffset.name] = chunkOffset
                it[Cmd.Sample.Chunk::data.name] = data
            }
        }
    }

    private fun decodeCmd(msg: dynamic): Cmd {
        return when (val type = msg.type) {
            Cmd.ScheduleVoice.SERIAL_NAME -> Cmd.ScheduleVoice(
                playbackId = msg[Cmd::playbackId.name],
                voice = decodeScheduledVoice(msg[Cmd.ScheduleVoice::voice.name])
            )

            Cmd.Sample.NotFound.SERIAL_NAME -> Cmd.Sample.NotFound(
                playbackId = msg[Cmd::playbackId.name],
                req = decodeSampleRequest(msg[Cmd.Sample.NotFound::req.name])
            )

            Cmd.Sample.Complete.SERIAL_NAME -> Cmd.Sample.Complete(
                playbackId = msg[Cmd::playbackId.name],
                req = decodeSampleRequest(msg[Cmd.Sample.Complete::req.name]),
                note = msg[Cmd.Sample.Complete::note.name],
                pitchHz = msg[Cmd.Sample.Complete::pitchHz.name],
                sample = decodeMonoSamplePcm(msg[Cmd.Sample.Complete::sample.name])
            )

            Cmd.Sample.Chunk.SERIAL_NAME -> Cmd.Sample.Chunk(
                playbackId = msg[Cmd::playbackId.name],
                req = decodeSampleRequest(msg[Cmd.Sample.Chunk::req.name]),
                note = msg[Cmd.Sample.Chunk::note.name],
                pitchHz = msg[Cmd.Sample.Chunk::pitchHz.name],
                sampleRate = msg[Cmd.Sample.Chunk::sampleRate.name],
                meta = decodeSampleMetadata(msg[Cmd.Sample.Chunk::meta.name]),
                totalSize = msg[Cmd.Sample.Chunk::totalSize.name],
                isLastChunk = msg[Cmd.Sample.Chunk::isLastChunk.name],
                chunkOffset = msg[Cmd.Sample.Chunk::chunkOffset.name],
                data = msg[Cmd.Sample.Chunk::data.name],
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
        return codec.encodeToDynamic(SampleMetadata.serializer(), this)
    }

    private fun decodeSampleMetadata(obj: dynamic): SampleMetadata {
        return codec.decodeFromDynamic(SampleMetadata.serializer(), obj)
    }

    fun jsObject(): dynamic = js("({})")

    fun <T> jsObject(block: (T) -> Unit): T {
        val obj = jsObject() as T
        block(obj)
        return obj
    }
}
