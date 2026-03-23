package io.peekandpoke.klang.pages.docs.tutorials

val spacesAndRestsTutorial = Tutorial(
    slug = "spaces-and-rests",
    title = "Spaces and Rests",
    description = "Learn how spaces divide time equally and the tilde creates silence to shape your rhythms.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf("mini-notation", "rests", "rhythm-basics"),
    sections = listOf(
        TutorialSection(
            heading = "Spaces Divide Time",
            text = "In Sprudel, spaces divide a cycle into equal parts. Two sounds get half each. Four sounds get a quarter each. The more sounds you write, the faster they play. Try adding or removing sounds to hear the difference.",
            code = """sound("bd sd bd sd")""",
        ),
        TutorialSection(
            heading = "Rests with ~",
            text = "The tilde ~ is silence. It takes up time but makes no sound. This is how you create rhythmic gaps. In this pattern the snare only hits on beat 3 — the rests on beats 1, 2, and 4 give it space to breathe.",
            code = """sound("~ ~ sd ~")""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Combine sounds and rests to create grooves with space. The kick hits on beats 1 and 3, the snare on beats 2 and 4, and the hi-hat fills every beat. Rests in each layer leave room for the others — that is how a beat breathes.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").gain(0.5)
)""",
        ),
    ),
)
