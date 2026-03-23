package io.peekandpoke.klang.pages.docs.tutorials

val yourFirstBeatTutorial = Tutorial(
    slug = "your-first-beat",
    title = "Your First Beat",
    description = "Go from silence to a layered drum groove in under three minutes.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf("drums", "rhythm", "stack", "getting-started"),
    sections = listOf(
        TutorialSection(
            heading = "Hear the Destination",
            text = "Hit play. This is what you will build by the end of this tutorial.",
            code = """stack(
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.6),
  sound("~ ~ cp ~").gain(0.8)
)""",
        ),
        TutorialSection(
            heading = "Start with a Kick",
            text = "Everything begins with a single sound. The sound() function plays a sample — \"bd\" is a bass drum. Hit play and you will hear it repeat every cycle.",
            code = """sound("bd")""",
        ),
        TutorialSection(
            heading = "Build a Pattern",
            text = "Put multiple sounds inside the quotes, separated by spaces. Each one gets an equal slice of the cycle. Try changing the pattern — swap \"sd\" for \"cp\" (clap) or \"rim\" (rimshot).",
            code = """sound("bd sd bd sd")""",
        ),
        TutorialSection(
            heading = "Add a Second Layer",
            text = "Now let's add hi-hats on their own line. The gain() function controls volume — 0.6 means 60%. Try replacing \"oh\" (open hi-hat) with \"ch\" (closed hi-hat) to hear the difference.",
            code = """sound("hh hh oh hh").gain(0.6)""",
        ),
        TutorialSection(
            heading = "Stack It Up",
            text = "The stack() function plays multiple patterns at the same time. This is where it clicks — you are layering sounds like a producer. The \"~\" symbol means silence, so the clap only hits on beat 3.",
            code = """stack(
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.6),
  sound("~ ~ cp ~").gain(0.8)
)""",
        ),
    ),
)
