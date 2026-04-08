package io.peekandpoke.klang.pages.docs.tutorials

val polyrhythmicWorkshopTutorial = Tutorial(
    slug = "polyrhythmic-workshop",
    title = "Polyrhythmic Workshop",
    description = "Layer competing rhythms using subdivisions, spread, and pick to build grooves that phase and evolve.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.Patterns),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Polyrhythms are what make music feel alive — two or more rhythmic patterns running at different speeds, creating shifting accents and unexpected grooves. In this tutorial you will layer competing time feels using mini-notation subdivisions, spread values across events, and pick from pattern banks to build rhythms that never quite repeat the same way.",
        ),
        TutorialSection(
            heading = "Subdivide with Square Brackets",
            text = "Square brackets let you fit multiple sounds into one beat. While \"bd sd\" plays two sounds per cycle, \"[bd bd] sd\" squeezes two kicks into the first beat. This is how you create faster rhythmic subdivisions without using fast(). Try \"[bd bd bd] sd\" for triplet kicks.",
            code = """sound("[bd bd] sd [hh hh hh] sd")""",
        ),
        TutorialSection(
            heading = "Three Against Four",
            text = "The classic polyrhythm: three beats against four. Stack a pattern with three hits per cycle against one with four. Your ear will hear them drift in and out of alignment — that tension is the magic of polyrhythm.",
            code = """stack(
  sound("[bd bd bd]").gain(0.8),
  sound("[rim rim rim rim]").gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Spread Values Across Events",
            text = "The spread() function distributes a range of values across events in your pattern. Here it fans gain values from soft to loud across four hi-hats, creating a dynamic accent pattern that cycles. Each hit gets a different volume, making the rhythm feel human.",
            code = """sound("hh hh hh hh")
  // Watch: spread distributes the values — each hit gets its own weight
  .gain(spread(0.3, 0.5, 0.7, 1.0))""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full polyrhythmic groove. The kick plays three against the hi-hat's four. The snare accents shift with spread. A melody floats above in pentatonic, untouched by the rhythmic chaos below. The layers phase against each other, creating a groove that evolves on its own — this is generative rhythm.",
            code = """stack(
  sound("[bd bd bd]").gain(0.8),
  sound("[hh hh hh hh]")
    .gain(spread(0.3, 0.5, 0.7, 1.0)),
  sound("~ sd ~ [sd sd]").gain(0.7),
  n("0 4 7 ~").scale("C3:pentatonic")
    .sound("saw").lpf(600)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3)
    .jux(slow(2))
)""",
        ),
    ),
)
