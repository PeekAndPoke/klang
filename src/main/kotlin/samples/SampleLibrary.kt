package io.peekandpoke.samples

class SampleLibrary(
    val banks: List<BankCoordinates> = emptyList(),
) {
    companion object {
        val default = SampleLibrary(
            banks = listOf(
                BankCoordinates(
                    name = "Strudel Default Drums",
                    bankUri = "https://raw.githubusercontent.com/tidalcycles/uzu-drumkit/main/strudel.json"
                ),
                BankCoordinates(
                    name = "Tidal Drum Machine",
                    bankUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json",
                    aliasUris = listOf(
                        "https://raw.githubusercontent.com/todepond/samples/main/tidal-drum-machines-alias.json"
                    ),
                ),
                BankCoordinates(
                    name = "Dough Samples",
                    bankUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json",
                ),
                BankCoordinates(
                    name = "Vcsl",
                    bankUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/vcsl.json",
                ),
                BankCoordinates(
                    name = "mridangam",
                    bankUri = "https://raw.githubusercontent.com/felixroos/dough-samples/main/mridangam.json",
                )
            ),
        )
    }

    class BankCoordinates(
        val name: String,
        val bankUri: String,
        val aliasUris: List<String> = emptyList(),
    )
}
