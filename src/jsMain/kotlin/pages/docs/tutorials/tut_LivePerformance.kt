package io.peekandpoke.klang.pages.docs.tutorials

val livePerformanceTutorial = Tutorial(
    slug = "live-performance",
    title = "Music That Plays Itself",
    description = "Design a self-evolving live set using every(), superimpose(), jux(), and slowcat() to build music that performs itself.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.LiveCoding, TutorialTag.Generative),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Everything you have learned so far — beats, melodies, effects, chords, arrangement — comes together here. A live performance is not a static loop. It is a system of rules that generates evolving music. In this tutorial you will design patterns that transform themselves, build tension and release, and create a piece that surprises even you.",
        ),
        TutorialSection(
            heading = "Evolving Drums with every()",
            text = "Start with a basic beat, then add rules that change it periodically. Every 4th cycle the hi-hats double in speed. Every 8th cycle the kick gets a fill. The drum pattern stays recognizable but never quite repeats — it breathes like a human drummer.",
            code = """stack(
  sound("bd ~ bd ~").every(8, fast(2)),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").every(4, fast(2)).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Layered Melodies with superimpose()",
            text = "A single melody becomes an orchestra when you superimpose transformed copies. Here the original melody plays normally while a transposed copy echoes above it. Each layer gets its own pan position for width and its own orbit for independent effects.",
            code = """n("0 2 4 7 6 4 2 0").scale("C3:minor")
  .sound("saw").lpf(800)
  .adsr("0.01:0.1:0.5:0.2").gain(0.3)
  // Voila: superimpose plates the melody with an octave garnish on top
  .superimpose(transpose("12").pan(0.8))
  .pan(0.2).orbit(0)""",
        ),
        TutorialSection(
            heading = "Stereo Movement with jux()",
            text = "The jux() function splits your sound into stereo — the original on one side, a transformed version on the other. Use it on a chord progression to create wide, shifting pads. Combined with slow(), the right channel plays at half speed, creating a canon effect.",
            code = """chord("<Am C F G>").voicing()
  .sound("supersaw").lpf(500)
  .adsr("0.2:0.3:0.7:0.5").gain(0.2)
  // Feel: jux carves stereo space — original left, slowed copy right
  .jux(slow(2))
  .room(0.2).rsize(5.0).orbit(1)""",
        ),
        TutorialSection(
            heading = "Build Tension with slowcat()",
            text = "Use slowcat() to create sections that build over time. Start sparse, add layers, reach a climax. Here the bass alternates between playing and silence — two cycles on, two cycles off — creating a drop effect that happens automatically.",
            code = """slowcat(
  n("0 ~ 0 ~").scale("C2:minor").sound("sine").lpf(200).gain(0.5),
  n("0 ~ 0 ~").scale("C2:minor").sound("sine").lpf(200).gain(0.5),
  silence,
  silence
)""",
        ),
        TutorialSection(
            heading = "Filter Sweeps for Transitions",
            text = "Live performers use filter sweeps to build energy. Use slowcat() to cycle through different filter values — the sound opens up gradually and then closes back down, creating a natural arc of tension and release across four cycles.",
            code = """n("0 2 4 7").scale("C3:minor")
  .sound("saw")
  // Watch: slowcat simmers through filter values, building heat each cycle
  .slowcat(lpf(400), lpf(600), lpf(1000), lpf(600))
  .adsr("0.01:0.1:0.5:0.2").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a complete self-evolving live set. The drums mutate every 4 and 8 cycles. The melody superimposes an octave copy in stereo. The chords spread wide with jux(). The bass drops in and out with slowcat(). Each layer has its own orbit for independent effects. Hit play and let it perform itself.",
            code = """stack(
  sound("bd ~ bd ~").every(8, fast(2)).orbit(0),
  sound("~ sd ~ sd").orbit(0),
  sound("hh hh hh hh")
    .every(4, fast(2)).gain(0.5).orbit(0),
  n("0 2 4 7 6 4 2 0").scale("C3:minor")
    .sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2").gain(0.25)
    .superimpose(transpose("12").pan(0.8))
    .pan(0.2).orbit(1),
  chord("<Am C F G>").voicing()
    .sound("supersaw").lpf(400)
    .adsr("0.2:0.3:0.7:0.5").gain(0.15)
    .jux(slow(2))
    .room(0.2).rsize(5.0).orbit(2),
  slowcat(
    n("0 ~ 0 ~").scale("C2:minor")
      .sound("sine").lpf(200).gain(0.5),
    n("0 ~ 0 ~").scale("C2:minor")
      .sound("sine").lpf(200).gain(0.5),
    silence,
    silence
  ).orbit(3)
)""",
        ),
    ),
)
