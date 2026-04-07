package io.peekandpoke.klang.pages.docs.tutorials

val drumKitBuilderTutorial = Tutorial(
    slug = "drum-kit-builder",
    title = "Drum Kit Builder",
    description = "Meet every drum sound in the kit and learn to combine them into different groove styles.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.GettingStarted),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You have been using bd, sd, and hh since tutorial one. But the drum kit has more to offer — claps, open hats, closed hats, rimshots. Each one has a role. In this tutorial you will meet every drum sound, learn when to use it, and build different groove styles from the same kit.",
        ),
        TutorialSection(
            heading = "The Kick Family",
            text = "The kick drum (bd) is the heartbeat. It anchors everything. On beats 1 and 3 for a standard rock feel, on every beat for dance music, or syncopated for funk. Try each pattern to feel how the kick placement changes the energy.",
            code = """// Rock steady: 1 and 3
sound("bd ~ bd ~")""",
        ),
        TutorialSection(
            heading = "Snare and Clap",
            text = "The snare (sd) is the backbeat — typically on beats 2 and 4. The clap (cp) is its cousin, brighter and thinner. Layer them for a bigger hit, or use clap alone for a more electronic feel. Try swapping sd for cp in any beat.",
            code = """stack(
  sound("bd ~ bd ~"),
  // Try: swap "sd" for "cp" for a different vibe
  sound("~ sd ~ sd")
)""",
        ),
        TutorialSection(
            heading = "The Hi-Hat Family",
            text = "You have three hi-hats: hh (closed, tight), ch (also closed, even tighter), and oh (open, ringy). Closed hats keep time, open hats add accents. A common trick: use oh on the upbeat to lift the groove.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  // Listen: the open hat on beat 3 lifts the energy
  sound("hh hh oh hh").gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "The Rimshot",
            text = "The rimshot (rim) is a sharp, metallic hit — great for accents, ghost notes, or latin-influenced grooves. It cuts through a mix without taking up much space. Use it sparingly — a little goes a long way, like chili flakes.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  // A sliver of rim for detail
  sound("~ rim ~ rim").gain(0.3),
  sound("hh hh hh hh").gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Style: Straight Beat",
            text = "The classic rock or pop beat: kick on 1 and 3, snare on 2 and 4, hi-hats on every eighth note. This is the bread and butter — simple, effective, works with everything.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("[hh hh] [hh hh] [hh hh] [hh hh]")
    .gain(0.4)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here are three different styles using the same kit. A driving dance beat with claps and open hats, built from every sound you just learned. Each sample has its place — kick for weight, snare for punch, hats for movement, rim for detail, clap for brightness.",
            code = """stack(
  sound("[bd bd] ~ bd ~"),
  sound("~ cp ~ [cp cp]").gain(0.7),
  sound("[hh hh hh hh] [hh hh oh hh]")
    .gain(0.4),
  // The finishing touch: ghost rimshots
  sound("~ [rim ~] ~ [~ rim]").gain(0.8)
).room("0.1:5.0")""",
        ),
    ),
)
