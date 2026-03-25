package io.peekandpoke.klang.pages.docs.tutorials

val shapeYourSoundTutorial = Tutorial(
    slug = "shape-your-sound",
    title = "Shape Your Sound",
    description = "Transform raw waveforms into rich, polished sounds using filters, envelopes, reverb, and delay.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Effects, TutorialTag.Synthesis),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You can play beats and melodies. Now let's make them sound great. In this tutorial you will learn to shape raw sounds with filters, envelopes, and effects — the tools that turn code into music you want to listen to.",
        ),
        TutorialSection(
            heading = "Soften with a Filter",
            text = "A raw saw wave can sound harsh. The lpf() function is a low-pass filter — it removes high frequencies, making the sound warmer and smoother. Lower values mean darker sound. Try changing 800 to 200 or 2000 to hear the difference.",
            code = """n("0 2 4 6 7 6 4 2").scale("C3:minor")
  .sound("saw")
  // Feel: lpf is your fine sieve — it strains out the harsh highs
  .lpf(800).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Control the Shape with adsr()",
            text = "Every note has a shape over time: how fast it fades in (attack), drops to a sustain level (decay and sustain), and fades out (release). The adsr() function controls all four as a string — \"attack:decay:sustain:release\" in seconds. A slow attack creates a pad feel. A short attack with quick decay creates a pluck.",
            code = """n("0 2 4 6 7 6 4 2").scale("C3:minor")
  .sound("saw").lpf(800)
  // Taste this: attack, decay, sustain, release — the recipe for every note's texture
  .adsr("0.3:0.2:0.6:0.5").gain(0.4)""",
        ),
        TutorialSection(
            heading = "Add Space with Reverb",
            text = "Music sounds flat without space around it. The room() function adds reverb — like playing in a concert hall. Use rsize() to control how big the room feels. Small values (1-3) are intimate, large values (5-10) are cavernous.",
            code = """n("0 2 4 6 7 6 4 2").scale("C3:minor")
  .sound("saw").lpf(800)
  .adsr("0.3:0.2:0.6:0.5")
  // Chef's kiss: a pinch of reverb for warmth, rsize sets the dining room
  .room(0.3).rsize(5.0).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Echo with Delay",
            text = "Delay repeats your sound after a short time, creating rhythmic echoes. Use delay(1) to turn it on, delaytime() to set the echo interval, and delayfeedback() to control how many times it repeats. Higher feedback means more echoes.",
            code = """n("0 ~ 4 ~").scale("C3:minor")
  .sound("saw").lpf(600)
  .adsr("0.01:0.1:0.3:0.2")
  // Now fold in some delay — rhythmic echoes that keep on giving
  .delay(1).delaytime(0.33).delayfeedback(0.4)
  .gain(0.4)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track using everything you learned. The melody is filtered and shaped with adsr, sitting in a reverb space. The bass gets its own envelope. The drums stay dry on a separate orbit so the reverb does not bleed into them.",
            code = """stack(
  n("0 2 4 7 6 4 2 0").scale("C3:minor")
    .sound("saw").lpf(600)
    .adsr("0.3:0.2:0.6:0.5")
    .room(0.2).rsize(4.0)
    .gain(0.3).orbit(0),
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("square").lpf(400)
    .adsr("0.01:0.3:0.8:0.1")
    .gain(0.4).orbit(1),
  sound("bd sd bd sd").orbit(2),
  sound("hh hh oh hh").gain(0.5).orbit(2)
)""",
        ),
    ),
)
