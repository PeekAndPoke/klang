/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

class SampleCatalogue(
    val sources: List<Source> = emptyList(),
) {
    companion object {
        fun of(vararg coordinates: Source) = SampleCatalogue(coordinates.toList())

        // DRUMS ///////////////////////////////////////////////////////////////////////////////////////////////////////

        val strudelDefaultDrums = Bundle(
            name = "Strudel Default Drums",
            soundsUri = "https://raw.githubusercontent.com/tidalcycles/uzu-drumkit/main/strudel.json"
        )

        val tidalDrumMachine = Bundle(
            name = "Tidal Drum Machine",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json",
            aliasUris = listOf(
                "https://raw.githubusercontent.com/todepond/samples/main/tidal-drum-machines-alias.json"
            ),
        )

        val doughSample = Bundle(
            name = "Dough Samples",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json",
        )

        val vcslSamples = Bundle(
            name = "Vcsl Samples",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/vcsl.json",
        )

        val mridangam = Bundle(
            name = "mridangam",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/mridangam.json",
        )

        // INSTRUMENTS /////////////////////////////////////////////////////////////////////////////////////////////////

        val piano = Bundle(
            name = "Piano",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/piano.json",
        )

        // SOUNDFONTS //////////////////////////////////////////////////////////////////////////////////////////////////

        val gmSoundFont = Soundfont(
            name = "GM - Felix Roos",
            indexUrl = "https://peekandpoke.github.io/klang/felixroos/gm/index.json",
        )

        // MIRROR //////////////////////////////////////////////////////////////////////////////////////////////////////

        /** Base url of the self-hosted sample mirror ... built by `./gradlew runSampleMirror`, uploaded via console/deploy-samples-finzo.sh */
        const val mirrorBase = "https://klang-assets.finzo.de/samples"

        /**
         * The original upstream sources, hot-loaded from raw.githubusercontent.com.
         *
         * Kept as the source of truth for the mirror tool (SampleMirrorMain) and as a manual fallback.
         */
        val origin = SampleCatalogue(
            sources = listOf(
                // drums
                strudelDefaultDrums,
                tidalDrumMachine,
                doughSample,
                vcslSamples,
                mridangam,
                // instruments
                piano,
                // soundfonts
                gmSoundFont,
            ),
        )

        /** One origin bundle and the directory it is mirrored to ... shared by [mirrored] and SampleMirrorMain */
        val mirrorSets: List<MirrorSet> = listOf(
            MirrorSet(source = strudelDefaultDrums, dir = "uzu-drumkit"),
            MirrorSet(source = tidalDrumMachine, dir = "tidal-drum-machines"),
            MirrorSet(source = doughSample, dir = "dirt-samples"),
            MirrorSet(source = vcslSamples, dir = "vcsl"),
            MirrorSet(source = mridangam, dir = "mridangam"),
            MirrorSet(source = piano, dir = "piano"),
        )

        /**
         * All sample sets served from one self-hosted [base] url.
         *
         * Mirrored manifests carry no "_base" — entries resolve relative to the manifest url
         * (see SampleIndexLoader's fallback base). The GM soundfont index is relocatable the same way.
         */
        fun mirrored(base: String): SampleCatalogue {
            val b = base.trimEnd('/')

            return SampleCatalogue(
                sources = mirrorSets.map { set ->
                    set.source.copy(
                        soundsUri = "$b/${set.dir}/index.json",
                        aliasUris = when {
                            set.source.aliasUris.isEmpty() -> emptyList()
                            else -> listOf("$b/${set.dir}/alias.json")
                        },
                    )
                } + gmSoundFont.copy(indexUrl = "$b/felixroos/gm/index.json"),
            )
        }

        // TODO: flip to `mirrored(mirrorBase)` once the mirror is live at [mirrorBase].
        //  Then regenerate the offline fixture: delete klang/cache and run KlangOfflineRendererSampleTest once with network.
        val default = origin
    }

    /** Maps an origin [source] bundle to the [dir] it is mirrored to on the sample mirror */
    data class MirrorSet(
        val source: Bundle,
        val dir: String,
    )

    sealed interface Source {
        /** Name of the bundle ... not used for anything just informal */
        val name: String
    }

    /**
     * A sound bundle
     */
    data class Bundle(
        /** Name of this bundle ... not used for anything just informal */
        override val name: String,
        /**
         * When pitching sound from this bundle, use this pitch as a basis
         *
         * C4 = 261.63 Hz (most sampler / instrument defaults treat samples as “middle C”)
         */
        val defaultPitchHz: Double = 261.63,
        /** Uri where to get the json definition from */
        val soundsUri: String,
        /** Uri where to get the json alias definition from */
        val aliasUris: List<String> = emptyList(),
    ) : Source

    data class Soundfont(
        /** The name of the soundfont bundle */
        override val name: String,
        /** The url to the soundfont index. See [SoundfontIndex] */
        val indexUrl: String,
    ) : Source {
        val baseUrl = indexUrl.substringBeforeLast('/')
    }
}
