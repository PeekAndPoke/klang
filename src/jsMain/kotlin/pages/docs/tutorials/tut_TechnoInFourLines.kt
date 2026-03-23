package io.peekandpoke.klang.pages.docs.tutorials

val technoInFourLinesTutorial = Tutorial(
    slug = "techno-in-four-lines",
    title = "Techno in Four Lines",
    description = "Build a driving techno groove with just four lines of code using fast kicks, filtered hats, and acid bass.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Quick,
    tags = listOf("techno", "genre", "minimal", "acid"),
    sections = listOf(
        TutorialSection(
            heading = "The Recipe",
            text = "Techno is about relentless forward motion from minimal ingredients. A four-on-the-floor kick, sizzling hi-hats, a rumbling bass, and one hypnotic element on top. Four lines. No filler. Every sound earns its place.",
            code = """stack(
  // The heartbeat: four kicks, no mercy
  sound("[bd bd bd bd]").gain(0.9),
  // Sizzle: fast hats with the lows cut away
  sound("[hh hh hh hh hh hh hh hh]")
    .hpf(4000).gain(0.35),
  // The growl: acid bass through a tight filter
  n("<0 0 [0 3] 0>").scale("C2:minor")
    .sound("square").lpf(300)
    .adsr("0.01:0.1:0.8:0.05")
    .distort(0.3).gain(0.45),
  // Garnish: a single hypnotic note, delayed
  n("0 ~ ~ ~").scale("C4:minor")
    .sound("saw").lpf(600)
    .delay(1).delaytime(0.166)
    .delayfeedback(0.5)
    .gain(0.2).orbit(1)
)""",
        ),
    ),
)
