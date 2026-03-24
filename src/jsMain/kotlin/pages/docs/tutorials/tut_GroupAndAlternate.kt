package io.peekandpoke.klang.pages.docs.tutorials

val groupAndAlternateTutorial = Tutorial(
    slug = "group-and-alternate",
    title = "Group and Alternate",
    description = "Use square brackets to pack sounds into beats and angle brackets to cycle through variations.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Patterns),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You already know that spaces divide a cycle into equal parts and ~ creates silence. Now it is time to learn the two most powerful mini-notation tools: square brackets for grouping multiple sounds into a single beat, and angle brackets for cycling through variations across cycles.",
        ),
        TutorialSection(
            heading = "Group with Square Brackets",
            text = "Square brackets squeeze multiple sounds into the time of one. Without brackets, \"bd sd bd sd\" plays four equal hits. With brackets, \"[bd bd] sd bd sd\" plays two kicks in the time of one beat. The rest of the pattern stays the same. Try putting brackets around different parts.",
            code = """sound("[bd bd] sd bd sd")""",
        ),
        TutorialSection(
            heading = "Fast Hi-Hats",
            text = "Grouping is how you make fast hi-hat patterns. Put four hi-hats in brackets and they subdivide into sixteenth notes while the kick and snare stay on quarter notes. This is the foundation of most electronic drum patterns.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("[hh hh hh hh] [hh hh hh hh] [hh hh oh hh] [hh hh hh hh]")
    .gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Alternate with Angle Brackets",
            text = "Angle brackets cycle through their contents — one item per cycle. On cycle 1 you hear the first option, cycle 2 the second, and so on before looping back. This creates variation without writing separate patterns. Listen for four cycles to hear all the changes.",
            code = """sound("bd <sd cp> bd <sd oh>")""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Combine grouping and alternation for patterns that are both rhythmically dense and evolving. The hi-hats use brackets for speed. The kick alternates between simple and syncopated. The melody cycles through different phrases each measure. Minimal code, maximum variation.",
            code = """stack(
  sound("<bd [bd bd]> sd <bd [bd bd bd]> sd"),
  sound("[hh hh] [hh hh] [hh hh oh] [hh hh]").gain(0.5),
  n("<[0 2 4 7] [7 4 2 0]>").scale("C4:minor")
    .sound("saw").lpf(800).gain(0.3)
)""",
        ),
    ),
)
