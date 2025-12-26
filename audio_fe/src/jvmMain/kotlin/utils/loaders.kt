package io.peekandpoke.klang.audio_fe.utils

import io.peekandpoke.klang.audio_fe.cache.DiskUrlCache
import io.peekandpoke.klang.audio_fe.cache.InMemoryUrlCache
import java.nio.file.Path

fun AssetLoader.withDiskCache(dir: Path): AssetLoader {
    return CachingAssetLoader(
        cache = InMemoryUrlCache(
            DiskUrlCache(dir)
        ),
        delegate = this,
    )
}
