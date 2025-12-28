package io.peekandpoke.klang.audio_bridge.infra

import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KlangCommLink(capacity: Int = 8192) {

    /** Sent from the frontend to the backend */
    @Serializable
    sealed interface Cmd {
        @Serializable
        @SerialName("schedule-voice")
        data class ScheduleVoice(val voice: ScheduledVoice) : Cmd

        @Serializable
        @SerialName("sample")
        sealed interface Sample : Cmd {
            @Serializable
            @SerialName("sample-not-found")
            data class NotFound(
                override val req: SampleRequest,
            ) : Sample

            @Serializable
            @SerialName("sample-complete")
            data class Complete(
                override val req: SampleRequest,
                val note: String?,
                val pitchHz: Double,
                val sampleRate: Int,
                val pcm: FloatArray,
            ) : Sample

//        data class SampleChunk(
//            val req: SampleRequest,
//            val data:
//        ): Cmd {
//
//        }

            val req: SampleRequest
        }
    }

    /** Send from the backend to the frontend */
    @Serializable
    sealed interface Feedback {
        @Serializable
        @SerialName("update-cursor-frame")
        data class UpdateCursorFrame(val frame: Long) : Feedback

        @Serializable
        @SerialName("request-sample")
        data class RequestSample(
            val req: SampleRequest,
        ) : Feedback
    }

    /** Frontend to backend buffer */
    private val controlBuffer = KlangMessageBuffer<Cmd>(capacity)

    /** Backend to frontend buffer */
    private val feedbackBuffer = KlangMessageBuffer<Feedback>(capacity)

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
