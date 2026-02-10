package io.peekandpoke.klang

object BuiltInSongs {

    private val _song = mutableListOf<Song>()
    val songs get() = _song.toList()

    private fun add(song: Song): Song = song.also {
        _song.add(song)
    }

    val tetris = add(
        Song(
            id = "builtin-song-0000001",
            title = "Synthris",
            cps = 0.65,
            code = TestTextPatterns.tetris,
        )
    )

    val smallTownBoy = add(
        Song(
            id = "builtin-song-0000002",
            title = "Synthtown Boy",
            cps = 0.58,
            code = TestTextPatterns.smallTownBoy,
        )
    )

    val strangerThings = add(
        Song(
            id = "builtin-song-0000003",
            title = "Stranger Synths",
            cps = 0.57,
            code = TestTextPatterns.strangerThingsNetflix,
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "builtin-song-0000004",
            title = "A Truth Worth Synthing For",
            cps = 0.42,
            code = TestTextPatterns.aTruthWorthFightingFor,
        )
    )
}
