package io.peekandpoke.klang.utils

import io.peekandpoke.klang.cache.DiskUrlCache
import io.peekandpoke.klang.cache.InMemoryUrlCache
import java.nio.file.Path

fun AssetLoader.withDiskCache(dir: Path): AssetLoader {
    return CachingAssetLoader(
        cache = InMemoryUrlCache(
            DiskUrlCache(dir)
        ),
        delegate = this,
    )
}
