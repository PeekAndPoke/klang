package io.peekandpoke.klang.pages.docs.tutorials

val speedUpSlowDownTutorial = Tutorial(
    slug = "speed-up-slow-down",
    title = "Speed Up, Slow Down",
    description = "Use fast() and slow() to change the speed of any pattern without rewriting it.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.GettingStarted),
    sections = listOf(
        TutorialSection(
            heading = "Double Time with fast()",
            text = "The fast() function multiplies the speed of a pattern. fast(2) plays it twice as fast, fast(4) four times. The pattern fits more repetitions into each cycle. Try changing the number — even decimals like fast(1.5) work.",
            code = """// Taste the difference: same pattern, double speed
sound("bd sd bd sd").fast(2)""",
        ),
        TutorialSection(
            heading = "Half Time with slow()",
            text = "The slow() function does the opposite — it stretches a pattern across more cycles. slow(2) means the pattern takes two full cycles to complete. It is like working in clay: same material, more time, finer detail.",
            code = """// Let it breathe: the melody unfolds across two cycles
n("0 2 4 7 6 4 2 0").scale("C3:minor")
  .sound("saw").lpf(800)
  // Smoothed out: stretched across 2 cycles
  .slow(2).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here the hi-hats play at double speed for a busy feel while the melody stretches across two cycles for a relaxed vibe. Same tempo, different speeds — that contrast gives the groove its personality.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  // Quick sizzle: hats at double speed
  sound("hh hh hh hh").fast(2).gain(0.4),
  n("0 2 4 7 6 4 2 0").scale("C3:minor")
    .sound("saw").lpf(700)
    // Long and lazy: melody takes its time
    .slow(2).gain(0.3)
)""",
        ),
    ),
)
