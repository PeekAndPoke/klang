package io.peekandpoke.klang.pages.docs.tutorials

val pickAndChooseTutorial = Tutorial(
    slug = "pick-and-choose",
    title = "Pick and Choose",
    description = "Use pick() and fastcat() to select from pattern banks and build rapid-fire sequences that change on the fly.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Generative, TutorialTag.Patterns),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Sometimes you want precise control over which sounds play and when. The pick() function lets you select from a bank of options by index, and fastcat() chains patterns together at double speed. Together they give you the tools to build generative sequences that feel curated rather than random.",
        ),
        TutorialSection(
            heading = "Rapid Sequencing with fastcat()",
            text = "You know cat() squeezes patterns into one cycle. fastcat() does the same thing — it plays each pattern back to back, dividing the cycle equally. Think of it as a quick sketch: every shape in rapid succession.",
            code = """// A quick sketch of four drum patterns
fastcat(
  sound("bd hh"),
  sound("sd cp"),
  sound("bd bd"),
  sound("hh oh")
)""",
        ),
        TutorialSection(
            heading = "Select with pick()",
            text = "The pick() function chooses from a list of options based on an index. Give it a number pattern and a list of sounds — it picks the matching one each time. Like a sculptor's toolbelt where the number tells you which chisel to grab next.",
            code = """// Watch: each number picks a different sample
sound("bd hh sd cp").pick(0, 1, 2, 3)""",
        ),
        TutorialSection(
            heading = "Melodic Pattern Banks",
            text = "Pick shines when applied to melodies. Define a set of scale degrees and use pick() to select from them in interesting orders. Combine with angle brackets to change the selection pattern every cycle — the melody reinvents itself.",
            code = """n("<[0 2 4 7] [7 4 2 0] [0 4 7 4] [4 7 0 2]>")
  .scale("C3:minor")
  .sound("saw").lpf(700)
  .adsr("0.01:0.1:0.5:0.2")
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a generative groove that uses fastcat() to rapidly cycle through drum variations while the melody alternates between phrases. The bass holds steady underneath. Every element is curated but the combination creates something that feels alive and unpredictable.",
            code = """stack(
  // Rapid-fire drum variations — a new face every half-cycle
  fastcat(
    sound("bd hh sd hh"),
    sound("bd [hh hh] sd cp"),
    sound("bd hh [sd sd] hh"),
    sound("[bd bd] hh sd oh")
  ).orbit(0),
  n("<[0 2 4 7] [7 4 2 0] [0 4 7 4]>")
    .scale("C3:minor")
    .sound("saw").lpf(700)
    .adsr("0.01:0.1:0.5:0.2")
    // A light glaze of delay on the melody
    .delay(1).delaytime(0.33).delayfeedback(0.2)
    .gain(0.25).orbit(1),
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("sine").lpf(200)
    .gain(0.5).orbit(2)
)""",
        ),
    ),
)
