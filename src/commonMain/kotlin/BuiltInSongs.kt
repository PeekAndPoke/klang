package io.peekandpoke.klang

object BuiltInSongs {

    const val PREFIX = "builtin-song"

    private val _song = mutableListOf<Song>()
    val songs get() = _song.toList()

    private fun add(song: Song): Song = song.also {
        _song.add(song)
    }

    val tetris = add(
        Song(
            id = "$PREFIX-0001",
            title = "Synthris",
            cps = 0.65,
            code = TestTextPatterns.tetris,
            icon = "gamepad",
        )
    )

    val smallTownBoy = add(
        Song(
            id = "$PREFIX-0002",
            title = "Synthtown Boy",
            cps = 0.58,
            code = TestTextPatterns.smallTownBoy,
            icon = "record vinyl",
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "$PREFIX-0003",
            title = "A Truth Worth Synthing For",
            cps = 0.41,
            code = TestTextPatterns.aTruthWorthFightingFor,
            icon = "guitar",
        )
    )

    val strangerThings = add(
        Song(
            id = "$PREFIX-0004",
            title = "Stranger Synths",
            cps = 0.57,
            code = TestTextPatterns.strangerThingsNetflix,
            icon = "music",
        )
    )
}
