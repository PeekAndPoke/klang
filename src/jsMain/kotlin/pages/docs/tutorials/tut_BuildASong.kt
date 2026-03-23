package io.peekandpoke.klang.pages.docs.tutorials

val buildASongTutorial = Tutorial(
    slug = "build-a-song",
    title = "Build a Song",
    description = "Graduate from loops to songs by sequencing sections with cat, slowcat, and silence.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf("arrangement", "song-structure", "sequencing"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Everything so far has been loops — patterns that repeat forever. Songs have structure: a verse, a chorus, a breakdown, a drop. In this tutorial you will learn to chain sections together and add silence for dramatic pauses, turning loops into actual songs.",
        ),
        TutorialSection(
            heading = "Sequence with cat()",
            text = "The cat() function plays patterns one after another within a single cycle. Each pattern gets an equal share of time. Here two different drum patterns alternate — a verse groove and a fill. They play back to back in one cycle.",
            code = """cat(
  sound("bd hh sd hh"),
  sound("bd bd sd cp")
)""",
        ),
        TutorialSection(
            heading = "Slow It Down with slowcat()",
            text = "While cat() squeezes everything into one cycle, slowcat() gives each pattern a full cycle of its own. This is how you build sections — each pattern plays for a whole measure before the next one starts. Listen to how the mood shifts every cycle.",
            code = """slowcat(
  n("0 2 4 7").scale("C3:minor").sound("saw").lpf(800).gain(0.3),
  n("0 3 5 7").scale("C3:minor").sound("saw").lpf(600).gain(0.3),
  n("0 2 4 7").scale("C3:minor").sound("saw").lpf(800).gain(0.3),
  n("5 4 2 0").scale("C3:minor").sound("saw").lpf(1000).gain(0.3)
)""",
        ),
        TutorialSection(
            heading = "Add Breathing Room with silence",
            text = "Music needs space. The silence keyword creates an empty pattern — no sound at all. Used inside slowcat(), it adds a rest between sections. This creates tension and makes the return of sound feel powerful.",
            code = """slowcat(
  sound("bd hh sd hh"),
  sound("bd hh sd hh"),
  silence,
  sound("bd bd sd cp")
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a mini song with four sections: an intro with just melody, a verse that adds drums, a breakdown with silence, and a drop where everything comes back louder. Each section gets one full cycle. This is arrangement — you are composing a piece with a beginning, middle, and end.",
            code = """stack(
  slowcat(
    n("0 2 4 7").scale("C3:minor").sound("saw").lpf(600).gain(0.3),
    n("0 2 4 7").scale("C3:minor").sound("saw").lpf(800).gain(0.3),
    silence,
    n("0 2 4 7").scale("C3:minor").sound("supersaw").lpf(1000).gain(0.25)
  ),
  slowcat(
    silence,
    sound("bd hh sd hh"),
    silence,
    sound("bd hh sd [cp cp]").gain(0.8)
  )
)""",
        ),
    ),
)
