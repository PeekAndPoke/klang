package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader

suspend fun Samples.Companion.create(
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: io.peekandpoke.klang.audio_fe.samples.SampleCatalogue = io.peekandpoke.klang.audio_fe.samples.SampleCatalogue.Companion.default,
): Samples {
    val indexLoader = _root_ide_package_.io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader(
        loader = assetLoader,
    )

    val index: Samples.Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        decoder = BrowserAudioDecoder(),
        loader = assetLoader,
    )
}
