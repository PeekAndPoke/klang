/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
