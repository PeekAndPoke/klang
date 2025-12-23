package io.peekandpoke.samples

class SampleCatalogue(
    val coordinates: List<Bundle> = emptyList(),
) {
    companion object {
        fun of(vararg coordinates: Bundle) = SampleCatalogue(coordinates.toList())

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

        val piano = Bundle(
            name = "Piano",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/piano.json",
        )

        val default = SampleCatalogue(
            coordinates = listOf(
                strudelDefaultDrums,
                tidalDrumMachine,
                doughSample,
                vcslSamples,
                mridangam,
            ),
        )
    }

    /**
     * A sound bundle
     */
    class Bundle(
        /** Name of this bundle ... not used for anything just informal */
        val name: String,
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
    )
}
