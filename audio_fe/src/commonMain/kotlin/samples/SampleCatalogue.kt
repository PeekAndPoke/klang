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

        /**
         * Base url of the self-hosted sample mirror.
         *
         * Built by `./gradlew runSampleMirror` (see SampleMirrorMain for the upstream sources),
         * uploaded via console/deploy-samples-finzo.sh.
         */
        const val MIRROR_BASE = "https://klang-assets.finzo.de/samples"

        /**
         * The sample sets on the mirror host. Each lives at `<base>/<dir>/index.json`.
         *
         * Mirrored manifests carry no "_base" — entries resolve relative to the manifest url
         * (see SampleIndexLoader's fallback base). The GM soundfont index is relocatable the
         * same way and always lives at `<base>/felixroos/gm/index.json`.
         */
        val mirrorSets: List<MirrorSet> = listOf(
            // drums
            MirrorSet(name = "Strudel Default Drums", dir = "uzu-drumkit"),
            MirrorSet(name = "Tidal Drum Machine", dir = "tidal-drum-machines", hasAlias = true),
            MirrorSet(name = "Dirt Samples", dir = "dirt-samples"),
            MirrorSet(name = "Vcsl Samples", dir = "vcsl"),
            MirrorSet(name = "mridangam", dir = "mridangam"),
            // instruments
            MirrorSet(name = "Piano", dir = "piano"),
        )

        /** All sample sets served from one self-hosted [base] url */
        fun mirrored(base: String): SampleCatalogue {
            val b = base.trimEnd('/')

            return SampleCatalogue(
                sources = mirrorSets.map { set ->
                    Bundle(
                        name = set.name,
                        soundsUri = "$b/${set.dir}/index.json",
                        aliasUris = when {
                            set.hasAlias -> listOf("$b/${set.dir}/alias.json")
                            else -> emptyList()
                        },
                    )
                } + Soundfont(
                    name = "GM - Felix Roos",
                    indexUrl = "$b/felixroos/gm/index.json",
                ),
            )
        }

        val default = mirrored(MIRROR_BASE)
    }

    /** One sample set on the mirror host */
    data class MirrorSet(
        /** Name of the set ... not used for anything just informal */
        val name: String,
        /** Directory of the set on the mirror host */
        val dir: String,
        /** Whether the set has an `alias.json` next to its `index.json` */
        val hasAlias: Boolean = false,
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
