package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_fe.decoders.SimpleAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.audio_fe.utils.withDiskCache
import java.nio.file.Path

suspend fun Samples.Companion.create(
    cacheDir: Path = Path.of("cache"),
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: SampleCatalogue = SampleCatalogue.Companion.default,
): Samples {
    val indexLoader = SampleIndexLoader(
        loader = assetLoader.withDiskCache(cacheDir.resolve("index")),
    )

    val index: Samples.Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        decoder = SimpleAudioDecoder(),
        loader = assetLoader.withDiskCache(cacheDir.resolve("samples")),
    )
}
