package io.peekandpoke.klang.pages.docs.tutorials

val miniNotationMasteryTutorial = Tutorial(
    slug = "mini-notation-mastery",
    title = "Mini-Notation Mastery",
    description = "Unlock the full power of mini-notation with nested grouping, alternation, and rest patterns.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Patterns),
    sections = listOf(
        TutorialSection(
            heading = "Nest for Density",
            text = "Square brackets group sounds into a single beat. But you can nest them — brackets inside brackets create increasingly dense subdivisions. Each level doubles the speed. This single line creates a drum fill that accelerates from quarter notes to thirty-second notes.",
            code = """sound("bd [bd sd] [bd sd bd sd] [bd sd bd sd bd sd bd sd]")""",
        ),
        TutorialSection(
            heading = "Alternate with Angle Brackets",
            text = "Angle brackets cycle through their contents one per cycle. Nest them inside patterns to create variations that evolve over time. Here the third beat alternates between a clap and open hi-hat every cycle, while the kick pattern stays constant. Minimal code, maximum variation.",
            code = """sound("bd hh <cp oh> hh")""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Combine nesting, alternation, and rests to write an entire evolving groove in a few lines. The kick alternates between simple and syncopated. The hi-hats nest for density. The melody cycles through different phrases each measure. This is the Pro way to write — maximum expression, minimum code.",
            code = """stack(
  sound("<bd [bd bd]> hh <sd [sd sd]> [hh oh]"),
  sound("[hh hh] [hh hh hh] [hh hh] hh").gain(0.4),
  n("<[0 2 4 7] [7 4 2 0] [0 4 7 11] [11 7 4 0]>")
    .scale("C3:minor").sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3)
)""",
        ),
    ),
)
