package io.peekandpoke.klang.audio_bridge.infra

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class KlangCommLink(capacity: Int = 8192) {

    companion object {
        /** Playback ID for system-wide messages (not tied to a specific playback) */
        const val SYSTEM_PLAYBACK_ID = "--SYSTEM--"
    }

    /** Sent from the frontend to the backend */
    sealed interface Cmd {
        val playbackId: String

        data class Cleanup(
            override val playbackId: String,
        ) : Cmd {
            companion object {
                const val SERIAL_NAME = "cleanup"
            }
        }

        data class ClearScheduled(
            override val playbackId: String,
        ) : Cmd {
            companion object {
                const val SERIAL_NAME = "clear-scheduled"
            }
        }

        data class ReplaceVoices(
            override val playbackId: String,
            val voices: List<ScheduledVoice>,
        ) : Cmd {
            companion object {
                const val SERIAL_NAME = "replace-voices"
            }
        }

        data class ScheduleVoice(
            override val playbackId: String,
            val voice: ScheduledVoice,
            /** If true, clears all scheduled voices for this playback before scheduling this one */
            val clearScheduled: Boolean = false,
        ) : Cmd {
            companion object {
                const val SERIAL_NAME = "schedule-voice"
            }
        }

        sealed interface Sample : Cmd {
            data class NotFound(
                override val req: SampleRequest,
            ) : Sample {
                companion object {
                    const val SERIAL_NAME = "sample-not-found"
                }

                override val playbackId: String = SYSTEM_PLAYBACK_ID
            }

            data class Complete(
                override val req: SampleRequest,
                val note: String?,
                val pitchHz: Double,
                val sample: MonoSamplePcm,
            ) : Sample {
                companion object {
                    const val SERIAL_NAME = "sample-complete"
                }

                override val playbackId: String = SYSTEM_PLAYBACK_ID

                fun toChunks(chunkSizeBytes: Int = 16 * 1024): List<Chunk> {
                    val numChunks = (sample.pcm.size / chunkSizeBytes) + 1

                    return (0 until numChunks).map { i ->
                        val startByte = i * chunkSizeBytes
                        val endByte = minOf(sample.pcm.size, (i + 1) * chunkSizeBytes)

                        Chunk(
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

            @Suppress("ArrayInDataClass")
            data class Chunk(
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

                override val playbackId: String = SYSTEM_PLAYBACK_ID
            }

            val req: SampleRequest
        }
    }

    /** Sent from the backend to the frontend */
    @Serializable
    sealed interface Feedback {
        val playbackId: String

        @Serializable
        @SerialName("request-sample")
        data class RequestSample(
            override val playbackId: String,
            val req: SampleRequest,
        ) : Feedback

        @Serializable
        @SerialName("sample-received")
        data class SampleReceived(
            override val playbackId: String,
            val req: SampleRequest,
        ) : Feedback

        @Serializable
        @SerialName("playback-latency")
        data class PlaybackLatency(
            override val playbackId: String,
            /**
             * The backend's KlangTime.internalMsNow() at the moment the playback epoch was recorded.
             * Both frontend and backend KlangTime are seeded from Date.now(), so the frontend
             * can compute transport latency as: backendTimestampMs - frontendStartTimeMs
             */
            val backendTimestampMs: Double,
        ) : Feedback

        @Serializable
        @SerialName("diagnostics")
        data class Diagnostics(
            override val playbackId: String,
            /** Backend sample rate in Hz (e.g. 44100, 48000) */
            val sampleRate: Int,
            /**
             * Rendering headroom as a ratio (not time-based).
             * - 1.0 = idle (all time available)
             * - 0.0 = full load (using all available time)
             * - <0.0 = overload (glitching, taking longer than block duration)
             * Calculated as: 1.0 - (renderDuration / blockDuration)
             */
            val renderHeadroom: Double,
            /**
             * Number of voices currently active (rendering audio)
             */
            val activeVoiceCount: Int,
            /**
             * State of all allocated orbits (mixing channels)
             */
            val orbits: List<OrbitState>,
            /**
             * Backend KlangTime.internalMsNow() at the moment this message was sent.
             * Used by the frontend to continuously correct clock drift via EMA.
             */
            val backendNowMs: Double,
            /** AudioContext.baseLatency in ms (browser's audio processing pipeline). 0.0 on JVM. */
            val baseLatencyMs: Double = 0.0,
            /** AudioContext.outputLatency in ms (hardware/device latency, e.g. Bluetooth). 0.0 on JVM. */
            val outputDeviceLatencyMs: Double = 0.0,
            /** Total output latency in ms (baseLatencyMs + outputDeviceLatencyMs). */
            val outputLatencyMs: Double = 0.0,
        ) : Feedback {
            @Serializable
            data class OrbitState(
                /** Orbit ID (0-15 typically) */
                val id: Int,
                /** Whether this orbit is currently active (processing audio or effect tails) */
                val active: Boolean,
            )
        }
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
