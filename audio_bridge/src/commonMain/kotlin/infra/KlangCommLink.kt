package io.peekandpoke.klang.audio_bridge.infra

import io.peekandpoke.klang.audio_bridge.ScheduledVoice

class KlangCommLink(capacity: Int = 8192) {

    /** Sent from the frontend to the backend */
    sealed interface Cmd {
        data class ScheduleVoice(val voice: ScheduledVoice) : Cmd
    }

    /** Send from the backend to the frontend */
    sealed interface Feedback

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
