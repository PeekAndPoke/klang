package io.peekandpoke.klang.samples

import io.peekandpoke.klang.samples.Samples.Index
import io.peekandpoke.klang.samples.decoders.SimpleAudioDecoder
import io.peekandpoke.klang.utils.AssetLoader
import io.peekandpoke.klang.utils.withDiskCache
import java.nio.file.Path

suspend fun Samples.Companion.create(
    cacheDir: Path = Path.of("./cache"),
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: SampleCatalogue = SampleCatalogue.default,
): Samples {
    val indexLoader = SampleIndexLoader(
        loader = assetLoader.withDiskCache(cacheDir.resolve("index")),
    )

    val index: Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        decoder = SimpleAudioDecoder(),
        loader = assetLoader.withDiskCache(cacheDir.resolve("samples")),
    )
}
