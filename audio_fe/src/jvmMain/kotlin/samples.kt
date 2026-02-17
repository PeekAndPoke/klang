package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_fe.decoders.JvmAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.audio_fe.utils.withDiskCache
import java.nio.file.Path

suspend fun Samples.Companion.create(
    cacheDir: Path = Path.of("cache"),
    loader: AssetLoader = AssetLoader.default,
    decoder: AudioDecoder = JvmAudioDecoder(),
    catalogue: SampleCatalogue = SampleCatalogue.default,
): Samples {
    val indexLoader = SampleIndexLoader(
        loader = loader.withDiskCache(cacheDir.resolve("index")),
    )

    val index: Samples.Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        loader = loader.withDiskCache(cacheDir.resolve("samples")),
        decoder = decoder,
    )
}
