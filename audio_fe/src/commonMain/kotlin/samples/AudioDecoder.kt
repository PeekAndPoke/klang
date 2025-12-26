package io.peekandpoke.klang.audio_fe.samples

interface AudioDecoder {
    fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm?
}
