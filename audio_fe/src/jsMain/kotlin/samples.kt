package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.utils.AssetLoader


suspend fun io.peekandpoke.klang.audio_fe.samples.Samples.Companion.create(
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: io.peekandpoke.klang.audio_fe.samples.SampleCatalogue = _root_ide_package_.io.peekandpoke.klang.audio_fe.samples.SampleCatalogue.Companion.default,
): io.peekandpoke.klang.audio_fe.samples.Samples {
    val indexLoader = _root_ide_package_.io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader(
        loader = assetLoader,
    )

    val index: io.peekandpoke.klang.audio_fe.samples.Samples.Index = indexLoader.load(catalogue)

    return _root_ide_package_.io.peekandpoke.klang.audio_fe.samples.Samples(
        index = index,
        decoder = NullAudioDecoder(),
        loader = assetLoader,
    )
}

// TODO: ...
class NullAudioDecoder : io.peekandpoke.klang.audio_fe.samples.AudioDecoder {
    override fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return null
    }
}
