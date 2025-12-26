package io.peekandpoke.klang.audio_bridge.infra

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.ScheduledVoice

class KlangCommLink(capacity: Int = 8192) {

    /** Sent from the frontend to the backend */
    sealed interface Cmd {
        data class ScheduleVoice(val voice: ScheduledVoice) : Cmd
        data class Sample(
            val request: Feedback.RequestSample,
            val sample: MonoSamplePcm?,
        ) : Cmd
    }

    /** Send from the backend to the frontend */
    sealed interface Feedback {
        data class RequestSample(
            /** Name of the requested bank ... null means default sounds */
            val bank: String?,
            /** Name of the requested sound */
            val sound: String?,
            /** Index of the requested variant (if any) */
            val index: Int?,
            /** Note at which the sample would be played. Helps to find the best sample. */
            val note: String?,
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
        val control: KlangEventDispatcher<Cmd> get() = controlBuffer
        val feedback: KlangEventReceiver<Feedback> get() = feedbackBuffer
    }

    /** Endpoint for the backend. */
    inner class BackendEndpoint {
        val control: KlangEventReceiver<Cmd> get() = controlBuffer
        val feedback: KlangEventDispatcher<Feedback> get() = feedbackBuffer
    }
}
