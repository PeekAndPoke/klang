package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader

suspend fun Samples.Companion.create(
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: SampleCatalogue = SampleCatalogue.default,
): Samples {
    val indexLoader = SampleIndexLoader(loader = assetLoader)

    val index: Samples.Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        decoder = BrowserAudioDecoder(),
        loader = assetLoader,
    )
}
