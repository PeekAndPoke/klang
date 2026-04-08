package io.peekandpoke.klang.pages.docs.tutorials

val patternsInMotionTutorial = Tutorial(
    slug = "patterns-in-motion",
    title = "Patterns in Motion",
    description = "Make your loops come alive with time transformations, conditional changes, and stereo tricks.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.LiveCoding, TutorialTag.Patterns),
    rpm = 35.0,
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "So far your patterns repeat the same way every cycle. That is fine for a loop, but live coding is about patterns that evolve. In this tutorial you will learn to flip patterns, add surprise fills, rotate rhythms, layer harmonies, and spread sound across stereo — turning static loops into living performances.",
        ),
        TutorialSection(
            heading = "Flip It with rev()",
            text = "The simplest trick: play your pattern backwards. rev() reverses the order of events. A melody that goes up now goes down. A drum pattern that leads with a kick now leads with a snare. Hit play, then remove rev() to hear the difference.",
            code = """// Turn the sculpture around — same shape, new perspective
n("0 2 4 7 6 4 2 0").scale("C4:minor")
  .sound("saw").lpf(1000).gain(0.3)
  .rev()""",
        ),
        TutorialSection(
            heading = "Surprise Fills with every()",
            text = "Here is where live coding gets magical. every(n, fn) applies a change only on every Nth cycle. The pattern plays normally most of the time, then BAM — a fill, a speed-up, a reversal. You design the rule, the pattern plays itself.",
            code = """// Chisel a surprise into every 4th cycle — the hats go wild
stack(
  sound("bd sd bd sd").gain(0.7),
  sound("hh hh hh hh")
    .every(4, fast(2)).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Rotating Rhythms with iter()",
            text = "iter(n) rotates where the pattern starts. On cycle 1 it plays normally, cycle 2 starts from the second note, cycle 3 from the third. The same four sounds create four different grooves. Listen for how the kick moves around.",
            code = """// Watch the groove shift — same four sounds, four different angles
sound("bd sd ~ cp").iter(4).gain(0.7)""",
        ),
        TutorialSection(
            heading = "Instant Harmony with off()",
            text = "off(time, fn) takes your pattern, shifts a copy in time, and transforms it. In this example, a copy of the melody is offset by an eighth note and transposed up by 7 scale degrees. One line of code gives you a two-voice harmony that plays itself.",
            code = """// Carve a second voice from the first — offset and transposed
n("0 -3 2 0 4 0 2 4").slow(4)
  .scale("C4:minor").sound("saw")
  .lpf(1000).gain(0.3)
  .off(0.125, x => x.scaleTranspose(7))""",
        ),
        TutorialSection(
            heading = "Stereo Split with jux()",
            text = "jux(fn) is pure stereo magic. It plays the original in one ear and a transformed copy in the other. Put on headphones — you will hear the melody forward on the left and reversed on the right. Two performances from one pattern.",
            code = """// Split the stone in two — forward on the left, reversed on the right
n("0 -3 2 0 4 0 2 4")
  .jux(rev()).slow(4)
  .scale("C4:minor").sound("saw")
  .lpf(800).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It Together",
            text = "Here is the finished piece where every layer has its own motion. The drums rotate with iter(). The hi-hats fill every 4th cycle with alternating crashes. The bass doubles speed every 3rd cycle. The melody has an off() harmony AND a jux() stereo split — forward on the left, reversed on the right. Nothing is static. This is live coding.",
            code = """// The sculpture breathes — every voice moves on its own
stack(
  // Drums — iter rotates the groove each cycle
  sound("bd sd ~ cp").iter(4).gain(1.0),
  // Hi-hats — fills carve in every 4th cycle
  sound("hh hh hh <hh oh cr>")
    .every(4, fast(2)).gain(0.8),
  // Bass — doubles speed every 3rd cycle
  n("0 ~ 0 4").scale("C2:minor").sound("sine")
    .lpf(400).hpf(100).gain(0.6)
    .every(3, fast(2)),
  // Melody — off() harmony + jux() stereo split
  n("0 -3 2 0 4 0 2 4")
    .scale("C4:minor").sound("saw")
    .jux(rev().slow(2)).slow(4)
    .lpf(800).hpf(200)
    .distort(0.5)
    .vibratoMod(0.01)
    .vibrato(perlin.add(5).slow(8))
    .adsr("0.1:0.15:0.3:0.1").gain(0.8)
    .off(0.125, x => x.scaleTranspose(7))
    .postgain(0.1)
).room(0.2).rsize(4)""",
        ),
    ),
)
