package io.peekandpoke.klang.pages.docs.tutorials

val everyTrickTutorial = Tutorial(
    slug = "every-trick",
    title = "The every() Trick",
    description = "Stack multiple every() rules on the same pattern to create grooves that surprise you on different cycles.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.LiveCoding),
    sections = listOf(
        TutorialSection(
            heading = "Layer the Rules",
            text = "You already know every() applies a change on every Nth cycle. But you can chain multiple every() calls on the same pattern. Each rule fires on its own cycle count. The result is a pattern that shifts in multiple ways at different intervals — it sounds composed, but you only wrote rules.",
            code = """sound("bd hh sd hh")
  // Watch: two sculptors sharing the same block — each carves on their own beat
  .every(4, fast(2))
  .every(3, crush(4))""",
        ),
        TutorialSection(
            heading = "Melodic Surprises",
            text = "On melodies, stacking every() rules creates phrases that evolve unpredictably. Here the melody reverses every 3 cycles and speeds up every 5. The two rules overlap on cycle 15, creating a rare combined moment — like finding a truffle in your pasta.",
            code = """n("0 2 4 7").scale("C3:minor")
  .sound("saw").lpf(800).gain(0.3)
  // Every 3rd cycle: flip the melody backwards
  .every(3, slow(2))
  // Every 5th cycle: double the speed
  .every(5, fast(2))""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a groove where every layer has its own set of rules. The kick doubles every 8th cycle. The hi-hats get crushed every 3rd. The melody slows down every 4th. Let it play for 20 cycles and notice how the combination of rules creates moments that never quite repeat.",
            code = """stack(
  sound("bd ~ bd ~")
    .every(8, fast(2)).orbit(0),
  sound("~ sd ~ sd").orbit(0),
  sound("hh hh hh hh").gain(0.5)
    // Sprinkle: lo-fi grit every 3rd cycle
    .every(3, crush(6)).orbit(0),
  n("0 2 4 7 6 4 2 0").scale("C3:minor")
    .sound("saw").lpf(700)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3)
    // Stretch: the melody unfolds every 4th cycle
    .every(4, slow(2)).orbit(1)
)""",
        ),
    ),
)
