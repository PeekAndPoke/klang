package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm

interface AudioDecoder {
    fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm?
}
