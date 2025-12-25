package io.peekandpoke.klang.samples

interface AudioDecoder {
    fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm?
}
