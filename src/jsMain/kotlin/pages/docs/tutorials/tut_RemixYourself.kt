package io.peekandpoke.klang.pages.docs.tutorials

val remixYourselfTutorial = Tutorial(
    slug = "remix-yourself",
    title = "Remix Yourself",
    description = "Use scramble, shuffle, and pick to make your patterns surprise you every cycle.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf("randomness", "remix", "generative"),
    sections = listOf(
        TutorialSection(
            heading = "Shuffle the Order",
            text = "Take a pattern you already know and add shuffle() at the end. Every cycle the sounds play in a different order. Hit play a few times — it never repeats the same way twice.",
            code = """sound("bd hh sd cp").shuffle()""",
        ),
        TutorialSection(
            heading = "Scramble for Chaos",
            text = "While shuffle() keeps all the sounds but reorders them, scramble() is wilder — it picks randomly from your sounds, so some may repeat and others may vanish. More unpredictable, more fun.",
            code = """sound("bd hh sd cp oh rim").scramble()""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a groove where the drums shuffle every cycle but the melody stays steady. The contrast between predictable and surprising is what makes generative music addictive. Try moving shuffle() to the melody instead.",
            code = """stack(
  sound("bd hh sd hh").shuffle(),
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").lpf(800).gain(0.3)
)""",
        ),
    ),
)
