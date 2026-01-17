package io.peekandpoke.klang.audio_bridge.infra

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KlangCommLink(capacity: Int = 8192) {

    /** Sent from the frontend to the backend */
    sealed interface Cmd {
        val playbackId: String

        data class ScheduleVoice(
            override val playbackId: String,
            val voice: ScheduledVoice,
        ) : Cmd {
            companion object {
                const val SERIAL_NAME = "schedule-voice"
            }
        }

        sealed interface Sample : Cmd {
            data class NotFound(
                override val playbackId: String,
                override val req: SampleRequest,
            ) : Sample {
                companion object {
                    const val SERIAL_NAME = "sample-not-found"
                }
            }

            data class Complete(
                override val playbackId: String,
                override val req: SampleRequest,
                val note: String?,
                val pitchHz: Double,
                val sample: MonoSamplePcm,
            ) : Sample {
                companion object {
                    const val SERIAL_NAME = "sample-complete"
                }

                fun toChunks(chunkSizeBytes: Int = 16 * 1024): List<Chunk> {
                    val numChunks = (sample.pcm.size / chunkSizeBytes) + 1

                    return (0 until numChunks).map { i ->
                        val startByte = i * chunkSizeBytes
                        val endByte = minOf(sample.pcm.size, (i + 1) * chunkSizeBytes)

                        Chunk(
                            playbackId = playbackId,
                            req = req,
                            note = note,
                            pitchHz = pitchHz,
                            sampleRate = sample.sampleRate,
                            meta = sample.meta,
                            totalSize = sample.pcm.size,
                            isLastChunk = i == numChunks - 1,
                            chunkOffset = i * chunkSizeBytes,
                            data = sample.pcm.copyOfRange(startByte, endByte),
                        )
                    }
                }
            }

            data class Chunk(
                override val playbackId: String,
                override val req: SampleRequest,
                val note: String?,
                val pitchHz: Double,
                val sampleRate: Int,
                val meta: SampleMetadata,
                val totalSize: Int,
                val isLastChunk: Boolean,
                val chunkOffset: Int,
                val data: FloatArray,
            ) : Sample {
                companion object {
                    const val SERIAL_NAME = "sample-chunk"
                }
            }

            val req: SampleRequest
        }
    }

    /** Send from the backend to the frontend */
    @Serializable
    sealed interface Feedback {
        val playbackId: String

        @Serializable
        @SerialName("update-cursor-frame")
        data class UpdateCursorFrame(
            override val playbackId: String,
            val frame: Long,
        ) : Feedback

        @Serializable
        @SerialName("request-sample")
        data class RequestSample(
            override val playbackId: String,
            val req: SampleRequest,
        ) : Feedback
    }

    /** Frontend to backend buffer */
    private val controlBuffer = KlangRingBuffer<Cmd>(capacity)

    /** Backend to frontend buffer */
    private val feedbackBuffer = KlangRingBuffer<Feedback>(capacity)

    /** The interface for the Audio Frontend (UI/Main Thread). */
    val frontend = FrontendEndpoint()

    /** The interface for the Audio Backend (DSP/Audio Thread). */
    val backend = BackendEndpoint()

    /**
     * Clears the buffers.
     */
    fun clear() {
        controlBuffer.clear()
        feedbackBuffer.clear()
    }

    /** Endpoint for the frontend. */
    inner class FrontendEndpoint {
        val control: KlangMessageSender<Cmd> get() = controlBuffer
        val feedback: KlangMessageReceiver<Feedback> get() = feedbackBuffer
    }

    /** Endpoint for the backend. */
    inner class BackendEndpoint {
        val control: KlangMessageReceiver<Cmd> get() = controlBuffer
        val feedback: KlangMessageSender<Feedback> get() = feedbackBuffer
    }
}
