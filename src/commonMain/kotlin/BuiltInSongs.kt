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
            title = "Tetris",
            cps = 0.63,
            code = TestTextPatterns.tetris,
        )
    )

    val smallTownBoy = add(
        Song(
            id = "builtin-song-0000002",
            title = "Smalltown Boy",
            cps = 0.58,
            code = TestTextPatterns.smallTownBoy,
        )
    )

    val strangerThings = add(
        Song(
            id = "builtin-song-0000003",
            title = "Stranger Things",
            cps = 0.60,
            code = TestTextPatterns.strangerThingsNetflix,
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "builtin-song-0000004",
            title = "A Truth Worth Lying For",
            cps = 0.4,
            code = TestTextPatterns.aTruthWorthFightingFor,
        )
    )
}
