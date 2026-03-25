package io.peekandpoke.klang.pages.docs.tutorials

val filterPlaygroundTutorial = Tutorial(
    slug = "filter-playground",
    title = "The Filter Playground",
    description = "Explore all three filter types — low-pass, high-pass, and band-pass — to sculpt any sound into exactly what you want.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.Effects, TutorialTag.Synthesis),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Filters are the salt and pepper of sound design. They do not add anything new — they remove frequencies to reveal the character hiding inside a sound. You already know lpf() from earlier tutorials. Now meet its siblings: hpf() and bandf(). Together these three filters can transform any sound from raw ingredient to finished dish.",
        ),
        TutorialSection(
            heading = "Low-Pass: Keep the Warmth",
            text = "You know this one — lpf() removes everything above a frequency, keeping the warm lows and mids. Low values make a sound dark and muffled, high values let it breathe. Think of it as a blanket: the lower the number, the thicker the blanket.",
            code = """n("0 2 4 7").scale("C3:minor")
  .sound("saw")
  // Cozy: wrap the saw in a warm blanket
  .lpf(400).gain(0.4)""",
        ),
        TutorialSection(
            heading = "High-Pass: Cut the Mud",
            text = "hpf() is the opposite — it removes everything below a frequency, keeping the bright highs. Use it to clean up boomy sounds, create airy textures, or make room for a bass underneath. Think of it as a sieve: the higher the number, the finer the mesh.",
            code = """n("0 2 4 7").scale("C3:minor")
  .sound("saw")
  // Sift away the lows — only the bright shimmer remains
  .hpf(1500).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Band-Pass: Focus the Spotlight",
            text = "bandf() is the most surgical — it keeps only a narrow band of frequencies around the target, removing both highs and lows. It creates a telephone-like, focused sound. Great for creating lo-fi textures or isolating a specific character from a rich waveform.",
            code = """n("0 2 4 7").scale("C3:minor")
  .sound("saw")
  // Spotlight: only the mids survive
  .bandf(800).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Filter Per Layer",
            text = "The real power comes from giving each layer its own filter. Bass gets lpf to stay warm, melody gets hpf to stay bright, pads get bandf for a mid-range bed. Like assigning each ingredient its own cooking method — each one brings out a different flavor.",
            code = """stack(
  // The foundation: warm and deep
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("saw").lpf(200).gain(0.5),
  // The shimmer: bright and airy
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").hpf(2000)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3),
  // The mid-range glue: focused and present
  n("<0 3 5 3>").scale("C3:minor")
    .sound("saw").bandf(600)
    .adsr("0.2:0.3:0.7:0.5").gain(0.25)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track where every layer is sculpted with a different filter. The bass uses lpf for warmth, the melody uses hpf for clarity, the pad sits in a band-pass pocket, and the drums stay unfiltered for punch. Each orbit keeps the effects separate. Three filters, one well-balanced mix.",
            code = """stack(
  // The foundation: warm and deep
  n("<[0 ~ 0 ~] [-3 ~ -3 ~] [2 ~ 2 ~] [3 ~ 3 ~] [0 ~ 0 ~] [3 ~ 3 ~] [-2 ~ -2 ~] [-3 ~ -3 ~]>").scale("C2:minor")
    .sound("saw").lpf(200).gain(0.9).clip(0.9),
  // The shimmer: bright and airy
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").hpf(2200).lpf(2400)
    .adsr("0.01:0.1:0.5:0.2").gain(0.9),
  // The mid-range glue: focused and present
  n("<[0 4 2 3] [0 3 2 1]>").slow(4).scale("C3:minor")
    .sound("saw").bandf(600)
    .adsr("0.1:0.2:0.3:1.0").gain(0.75)
).fast(2).analog(2.0)""",
        ),
    ),
)
