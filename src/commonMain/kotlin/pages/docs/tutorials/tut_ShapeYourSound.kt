package io.peekandpoke.klang.pages.docs.tutorials

val shapeYourSoundTutorial = Tutorial(
    slug = "shape-your-sound",
    title = "Shape Your Sound",
    description = "Transform raw waveforms into rich, polished sounds using filters, envelopes, reverb, and effects.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.Effects, TutorialTag.Synthesis),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You can play beats and melodies. Now let's make them sound great. In this tutorial you will shape raw sounds with filters, envelopes, and effects — the tools that turn code into music you actually want to listen to.",
        ),
        TutorialSection(
            heading = "The Raw Block",
            text = "Here is a raw saw wave melody. It works, but it sounds harsh and buzzy — like an unfinished sculpture. Our job is to carve it into something beautiful.",
            code = """// The raw block of stone — listen to how harsh it is
n("0 0 0 7 0 4 0 2").scale("C4:minor")
  .sound("saw").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Soften with a Filter",
            text = "The lpf() function is a low-pass filter — it cuts high frequencies, making the sound warmer and smoother. Lower values mean darker sound. Try changing 1000 to 200 or 2000.",
            code = """// Sand the rough edges — lpf smooths out the harshness
n("0 0 0 7 0 4 0 2").scale("C4:minor")
  .sound("saw").lpf(1000).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Give It Life with adsr()",
            text = "Every note has a shape over time: how fast it fades in (attack), drops to a sustain level (decay and sustain), and fades out (release). The adsr() function controls all four — \"attack:decay:sustain:release\". A slow attack creates a pad feel. A short attack with quick decay creates a pluck. Try changing the values.",
            code = """// Breathe life into the stone — adsr shapes how each note lives and dies
n("0 0 0 7 0 4 0 2").scale("C4:minor")
  .sound("saw").lpf(1000)
  .adsr("0.05:0.2:0.6:0.5").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Add Space with Reverb",
            text = "Music sounds flat without space around it. The room() function adds reverb — like playing in a concert hall instead of a closet. Use rsize() to control how big the room feels. Small values (1-3) are intimate, large values (5-10) are cavernous.",
            code = """// Place the sculpture in a gallery — room adds the space around it
n("0 0 0 7 0 4 0 2").scale("C4:minor")
  .sound("saw").lpf(1000)
  .adsr("0.05:0.2:0.6:0.5")
  .room(0.25).rsize(5).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Add a Beat",
            text = "Let's put drums under the melody. But here is the trick: we put the drums on their own orbit(). Effects like reverb are per orbit — so the drums stay dry and punchy while the melody floats in reverb.",
            code = """// Orbit keeps them apart — drums stay dry, melody stays wet
stack(
  n("0 0 0 7 0 4 0 2").scale("C4:minor")
    .sound("saw").lpf(1000)
    .adsr("0.05:0.2:0.6:0.5")
    .room(0.25).rsize(5)
    .gain(0.3).orbit(1),
  sound("bd sd bd sd").gain(0.7).orbit(0),
  sound("hh hh oh hh").gain(0.3).orbit(0)
)""",
        ),
        TutorialSection(
            heading = "Add a Bass Line",
            text = "A bass line gives the music a foundation. We use a triangle wave — it is softer than a saw. The warmth() filter gently rounds off the top, and adsr gives the bass a tight, punchy feel with a fast attack and short decay.",
            code = """// Carve out the foundation — triangle bass, shaped tight and warm
stack(
  n("0 0 0 7 0 4 0 2").scale("C4:minor")
    .sound("saw").lpf(1000)
    .adsr("0.05:0.2:0.6:0.5")
    .room(0.25).rsize(5)
    .gain(0.3).orbit(1),
  sound("bd sd bd sd").gain(0.7).orbit(0),
  sound("hh hh oh hh").gain(0.3).orbit(0),
  n("<0!8 0!8 -3!8 0!8>*8")
    .scale("C2:minor").sound("tri")
    .warmth(0.5).lpf(400)
    .adsr("0.01:0.1:0.6:0.05")
    .gain(0.5).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Add an Atmosphere",
            text = "A pad fills out the space between melody and bass. We use a supersaw — multiple detuned waves for a thick, lush texture. The adsr has a slow attack so it swells in gently. Tremolo adds a subtle pulsing movement. And pan() places it slightly to the left — leaving room for the melody on the right.",
            code = """// The atmosphere — a lush supersaw pad that breathes and moves
n("<[0@3 1] 2 [5@3 3] 4 [9@3 7] 4 [5@3 6] 4>")
  .scale("C3:minor").sound("supersaw")
  .tremolo(perlin.mul(0.2)).tremdepth(4)
  .lpf(1000).adsr("0.05:0.5:0.8:0.05")
  .gain(0.4).orbit(2).pan(0.4)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the finished piece. Four voices, each shaped with its own character: dry punchy drums, a tight pulsing bass, a breathing supersaw pad panned left, and a filtered melody panned right. The room() on the outer stack gives everything a shared sense of space. Each orbit keeps the effects separated — the drums stay crisp while the synths float in reverb.",
            code = """// The finished sculpture — four voices, shaped and placed
stack(
  // Drums — dry and punchy on orbit 0
  sound("bd sd bd sd").gain(0.7).orbit(0),
  sound("hh hh oh hh").gain(0.3).orbit(0),
  // Bass — tight triangle pulse on orbit 1
  n("<0!8 0!8 -3!8 0!8>*8")
    .scale("C2:minor").sound("tri")
    .warmth(0.5).lpf(400)
    .adsr("0.01:0.1:0.6:0.05")
    .gain(0.5).orbit(1),
  // Pad — lush supersaw, panned left on orbit 2
  n("<[0@3 1] 2 [5@3 3] 4 [9@3 7] 4 [5@3 6] 4>")
    .scale("C3:minor").sound("supersaw")
    .tremolo(perlin.mul(0.2)).tremdepth(4)
    .lpf(1000).adsr("0.05:0.5:0.8:0.05")
    .gain(0.4).orbit(2).pan(0.4),
  // Melody — filtered saw, panned right on orbit 1
  n("0 0 0 7 0 4 0 2")
    .scale("C4:minor").sound("saw")
    .lpf(1000).adsr("0.05:0.2:0.6:0.5")
    .gain(0.2).orbit(1).pan(0.6)
).room(0.25).rsize(5)""",
        ),
    ),
)
