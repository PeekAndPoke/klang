package io.peekandpoke.klang.pages.docs.tutorials

val nestedPatternsTutorial = Tutorial(
    slug = "nested-patterns",
    title = "Nested Patterns",
    description = "Combine square brackets and angle brackets to create complex, evolving rhythms from simple building blocks.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Standard,
    tags = listOf("mini-notation", "nesting", "advanced-patterns"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You know that square brackets group sounds into a beat and angle brackets cycle through options. Now it is time to combine them — nesting brackets inside brackets to create patterns with depth and movement. This is where mini-notation becomes a compositional language.",
        ),
        TutorialSection(
            heading = "Brackets Inside Brackets",
            text = "Each level of nesting doubles the subdivision. \"bd sd\" is two hits per cycle. \"[bd sd] sd\" squeezes two into the first beat. \"[[bd bd] sd] sd\" goes deeper — four quick kicks then a snare in the first half, then a snare in the second half. Try adding another level.",
            code = """sound("[[bd bd] sd] sd")""",
        ),
        TutorialSection(
            heading = "Alternation Inside Groups",
            text = "Put angle brackets inside square brackets to create grouped beats that change every cycle. Here the first beat alternates between a kick and a clap-kick combination, while the rest stays constant. The pattern evolves without you changing anything.",
            code = """sound("[<bd [cp bd]>] hh sd hh")""",
        ),
        TutorialSection(
            heading = "Groups Inside Alternation",
            text = "The reverse works too — put groups inside angle brackets to alternate between entire rhythmic phrases. Each cycle picks one of the grouped options. This creates dramatic shifts between sections with a single line of code.",
            code = """sound("<[bd bd sd bd] [bd sd sd cp]> hh hh hh")""",
        ),
        TutorialSection(
            heading = "Melodic Nesting",
            text = "Nesting is not just for drums. Use it in melodies to create phrases with internal movement. Here the scale pattern has fast runs nested into a slower phrase, creating a melody that breathes between bursts of notes.",
            code = """n("<[0 2 4 7] [7 4 2 0] [0 [2 4] 7 [4 2]] [7 [4 2] 0 [2 4]]>")
  .scale("C4:minor").sound("saw").lpf(800)
  .adsr("0.01:0.1:0.5:0.2").gain(0.3)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a groove that uses nesting at every level. The kick alternates between simple and complex patterns. The hi-hats use nested groups for fast subdivisions. The melody has internal runs that create rhythmic interest. All of this is expressed in pure mini-notation — no fast() or every() needed.",
            code = """stack(
  sound("<bd [bd bd]> <sd [sd sd]> <bd [bd [bd bd]]> sd"),
  sound("[hh hh] [hh hh hh] [hh hh] <[hh oh] hh>").gain(0.5),
  n("<[0 2 4 7] [0 4 7 11] [7 4 2 0] [11 7 4 0]>")
    .scale("C3:minor").sound("saw").lpf(700)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3)
)""",
        ),
    ),
)
