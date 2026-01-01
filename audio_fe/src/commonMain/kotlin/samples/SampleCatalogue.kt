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

        val default = SampleCatalogue(
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
    }

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
