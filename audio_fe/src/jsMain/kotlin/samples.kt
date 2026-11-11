/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.SampleIndexLoader
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader

/**
 * The browser's samples: the catalogue's index, and files decoded at [sampleRate], the rate the engine runs at
 * ([BrowserAudioDecoder]; the player's resolved rate), so a file is resampled once, by the browser.
 */
suspend fun Samples.Companion.create(
    sampleRate: Int,
    assetLoader: AssetLoader = AssetLoader.default,
    catalogue: SampleCatalogue = SampleCatalogue.default,
): Samples {
    val indexLoader = SampleIndexLoader(loader = assetLoader)

    val index: Samples.Index = indexLoader.load(catalogue)

    return Samples(
        index = index,
        decoder = BrowserAudioDecoder(sampleRate = sampleRate),
        loader = assetLoader,
    )
}
