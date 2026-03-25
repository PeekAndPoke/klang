package io.peekandpoke.klang.pages.docs.tutorials

val bassDesignTutorial = Tutorial(
    slug = "bass-design",
    title = "Bass Design",
    description = "Craft different bass sounds from scratch — sub, acid, pluck, and wobble — using waveforms, filters, and envelopes.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Synthesis, TutorialTag.Melody),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Bass is the foundation of any track. But not all bass is the same — a sub bass feels different from an acid line, which feels different from a wobble. In this tutorial you design four classic bass sounds from scratch using the same building blocks: waveforms, filters, and envelopes. Same kitchen, different recipes.",
        ),
        TutorialSection(
            heading = "Sub Bass — Pure and Deep",
            text = "The sub bass is the simplest: a sine wave in a low octave. No filter needed because sine has no harmonics to filter. Keep it clean, keep it low. You feel it more than you hear it. This is the bedrock everything else sits on.",
            code = """// The deepest ingredient: pure sine, felt in the chest
n("0 ~ 0 ~").scale("C2:minor")
  .sound("sine")
  .adsr("0.01:0.1:0.9:0.1")
  .gain(0.5)""",
        ),
        TutorialSection(
            heading = "Acid Bass — Squelchy and Alive",
            text = "The acid bass is a square wave with a tight low-pass filter and short decay. The magic is in the envelope — a fast attack with quick decay creates that classic squelchy, bubbling sound. Lower the filter for more mumble, raise it for more bite.",
            code = """// The acid recipe: square + tight filter + fast envelope
n("0 0 [0 3] 0").scale("C2:minor")
  .sound("square")
  .lpf(400)
  .adsr("0.01:0.15:0.3:0.05")
  .gain(0.45)""",
        ),
        TutorialSection(
            heading = "Pluck Bass — Short and Punchy",
            text = "A pluck bass has almost no sustain — it hits hard and disappears. Use a saw wave with a very short decay and zero sustain. The lpf cuts the brightness. The result snaps like a rubber band — tight, rhythmic, perfect for funk.",
            code = """// Snap! A rubber-band bass: all attack, no sustain
n("0 3 5 0").scale("C2:minor")
  .sound("saw").lpf(600)
  .adsr("0.01:0.12:0.0:0.05")
  .gain(0.45)""",
        ),
        TutorialSection(
            heading = "Dirty Bass — Warm Distortion",
            text = "Take any bass sound and add distort() for warmth and grit. Low distortion values add subtle harmonics that help bass cut through a mix on small speakers. Higher values create aggressive, growling tones. A dash of distortion is like toasting bread — it brings out the flavor.",
            code = """// Toast it: distortion adds warmth and presence
n("0 ~ 0 3").scale("C2:minor")
  .sound("saw").lpf(500)
  .adsr("0.01:0.15:0.7:0.1")
  // A gentle char — just enough grit
  .distort(0.25)
  .gain(0.4)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a track that demonstrates bass as the star. The acid bass drives the verse, the sub anchors below, and a pluck melody dances on top. Each bass type serves a different purpose — together they create a full low-end landscape. Notice the separate orbits keeping the reverbed melody away from the dry bass.",
            code = """stack(
  // Acid bass: the main course
  n("0 0 [0 3] <0 5>").scale("C2:minor")
    .sound("square").lpf(400)
    .adsr("0.01:0.15:0.3:0.05")
    .gain(0.4).orbit(0),
  // Sub layer: felt, not heard
  n("0 ~ 0 ~").scale("C1:minor")
    .sound("sine")
    .adsr("0.01:0.1:0.9:0.1")
    .gain(0.35).orbit(0),
  // Pluck melody on top — snappy and bright
  n("0 4 7 4").scale("C4:minor")
    .sound("saw").lpf(900)
    .adsr("0.01:0.08:0.0:0.05")
    // A whisper of reverb on the melody
    .room(0.15).rsize(3.0)
    .gain(0.25).orbit(1),
  sound("bd ~ bd ~").orbit(2),
  sound("~ sd ~ sd").orbit(2),
  sound("hh hh oh hh").gain(0.4).orbit(2)
)""",
        ),
    ),
)
