package io.peekandpoke.samples

class SampleCatalogue(
    val defaultBank: String,
    val banks: List<BankCoordinates> = emptyList(),
) {
    companion object {
        val strudelDefaultDrums = BankCoordinates(
            name = "Strudel Default Drums",
            soundsUri = "https://raw.githubusercontent.com/tidalcycles/uzu-drumkit/main/strudel.json"
        )

        val tidalDrumMachine = BankCoordinates(
            name = "Tidal Drum Machine",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json",
            aliasUris = listOf(
                "https://raw.githubusercontent.com/todepond/samples/main/tidal-drum-machines-alias.json"
            ),
        )

        val doughSample = BankCoordinates(
            name = "Dough Samples",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json",
        )

        val vcslSamples = BankCoordinates(
            name = "Vcsl Samples",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/vcsl.json",
        )

        val mridangam = BankCoordinates(
            name = "mridangam",
            soundsUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/mridangam.json",
        )

        val default = SampleCatalogue(
            defaultBank = "RolandTR909",
            banks = listOf(
                strudelDefaultDrums,
                tidalDrumMachine,
                doughSample,
                vcslSamples,
                mridangam,
            ),
        )
    }

    class BankCoordinates(
        val name: String,
        val soundsUri: String,
        val aliasUris: List<String> = emptyList(),
    )
}
