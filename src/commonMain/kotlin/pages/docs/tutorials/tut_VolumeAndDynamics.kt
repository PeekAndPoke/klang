package io.peekandpoke.klang.pages.docs.tutorials

val volumeAndDynamicsTutorial = Tutorial(
    slug = "volume-and-dynamics",
    title = "Volume and Dynamics",
    description = "Use gain() creatively to add accents, build energy, and make patterns feel alive.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Mixing, TutorialTag.Rhythm),
    sections = listOf(
        TutorialSection(
            heading = "More Than Just Loud and Quiet",
            text = "You have been using gain() to balance layers. But gain is also a creative tool. Different volumes on different hits create accents — the difference between a mechanical loop and a groove that breathes. Think of it as seasoning: the right amount in the right place makes everything better.",
            code = """// Without accents: flat and lifeless
sound("hh hh hh hh").gain(0.5)""",
        ),
        TutorialSection(
            heading = "Create Accents",
            text = "Use spread() to fan different gain values across hits. The first hi-hat is quiet, the second louder, the third loudest, the fourth medium. This creates a human-feeling accent pattern — like a drummer naturally hitting some beats harder than others.",
            code = """// Watch: spread seasons each hit differently
sound("hh hh hh hh")
  .gain(spread(0.2, 0.4, 0.7, 0.5))""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a groove with dynamic hi-hats over a steady kick and snare. The hi-hats use spread() for natural accents. The melody alternates between loud and soft cycles with angle brackets in the gain. The contrast between loud and quiet is what makes music feel alive.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  // The secret sauce: accented hi-hats
  sound("[hh hh hh hh] [hh hh hh hh]")
    .gain(spread(0.2, 0.5, 0.3, 0.7)),
  n("0 2 4 7").scale("C3:minor")
    .sound("saw").lpf(700)
    .adsr("0.01:0.1:0.5:0.2")
    .gain(0.3)
)""",
        ),
    ),
)
