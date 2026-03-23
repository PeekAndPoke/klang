---
name: tutorial-factory
description: "Autonomous tutorial generator — brainstorms a tutorial idea, implements it, self-reviews, and links it into the registry. Designed for /loop."
---

## What This Skill Does

Generates one tutorial per invocation. Reads existing tutorials to avoid duplicates, brainstorms via Six Hats,
implements the winning idea as a Kotlin data file, adds it to the registry, and self-reviews.

**CRITICAL: This skill runs autonomously. Only use tools that do NOT require human acceptance: Read, Write, Edit, Glob,
Grep, Agent. NO Bash commands. NO git commands.**

## Step 1: Check Existing Tutorials

Read the registry file to see what already exists:

```
/opt/dev/peekandpoke/klang/src/jsMain/kotlin/pages/docs/tutorials/TutorialRegistry.kt
```

Also read the generation log to track what has been created:

```
/opt/dev/peekandpoke/klang/src/jsMain/kotlin/pages/docs/tutorials/GENERATION_LOG.md
```

Note all existing tutorial slugs, topics, difficulty levels, and scopes. The goal is DIVERSITY — avoid duplicating
topics and spread across difficulty levels and scopes.

## Step 2: Brainstorm via Six Hats

Run `/six-hats` with this topic:

> "What is the single best NEW tutorial to create next for the Klang live-coding music platform?
> It must teach something concrete using Sprudel pattern language.
> It must NOT overlap with existing tutorials: [list existing slugs/titles here].
> Current difficulty spread: [list counts per difficulty].
> Current topic spread: [list existing tags].
> The tutorial should use ONLY these verified functions: note(), n(), sound(), s(), stack(), cat(), fastcat(),
> slowcat(), silence, fast(), slow(), gain(), pan(), lpf(), hpf(), bandf(), delay(), delaytime(), delayfeedback(), room(),
> rsize(), scale(), transpose(), chord(), adsr(), attack(), decay(), sustain(), release(), legato(), every(),
> superimpose(), jux(), scramble(), shuffle(), pick(), distort(), crush(), tremolo(), vibrato(), phaser(), sine, saw, tri,
> square, brown, pink, supersaw, pure(), range(), bpm(), cps, arrange(), orbit(), spread()."

## Step 3: Pick the Winner

From the Six Hats output, select the tutorial idea that:

1. Fills the biggest gap in difficulty/topic coverage
2. Has the strongest "wow moment" potential (Blue Hat input)
3. Is technically feasible with current functions (Black Hat validation)
4. Has genuine creative appeal (Red Hat + Green Hat input)
5. **SERIES THINKING**: For complex topics (mini-notation, chords, effects, arrangement), prefer creating a tutorial
   that fills a missing difficulty level in an existing topic series. E.g., if there's already a Pro mini-notation
   tutorial, consider a Beginner or Intermediate one on the same topic. Don't cram everything into one tutorial.

## Step 4: Implement the Tutorial

Create a new file at:

```
src/jsMain/kotlin/pages/docs/tutorials/tut_<PascalCaseSlug>.kt
```

Use this template:

```kotlin
package io.peekandpoke.klang.pages.docs.tutorials

val <camelCaseSlug> Tutorial = Tutorial(
    slug = "<kebab-case-slug>",
    title = "<Human Readable Title>",
    description = "<One sentence describing what the learner will achieve>",
    difficulty = TutorialDifficulty.< level >,
    scope = TutorialScope.< scope >,
    tags = listOf("<tag1>", "<tag2>"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "<Brief context — why this matters, what you'll learn>",
        ),
        TutorialSection(
            heading = "<Step heading>",
            text = "<Explanation of the concept>",
            code = "<working sprudel code>",
        ),
        // 2-5 sections with progressive complexity
        TutorialSection(
            heading = "Putting It All Together",
            text = "<Summary of what was learned>",
            code = "<final combined example that sounds good>",
        ),
    ),
)
```

### Code Example Rules

**CRITICAL — every code example must be valid Sprudel that runs in the engine:**

1. Notes use string mini-notation: `note("c3 d3 e3")` not `note(c3, d3, e3)`
2. Samples use names: `sound("bd hh sd hh")`
3. Angle brackets for slow sequences: `note("<c3 e3 g3>")`
4. Square brackets for grouping: `note("[c3 e3] g3")`
5. Stack for layering: `stack(pattern1, pattern2)`
6. Method chaining: `note("c3").sound("sine").gain(0.5)`
7. Scale applied after note numbers: `n("0 2 4 6").scale("C4:major").sound("saw")`
8. ADSR as string: `.adsr("0.01:0.2:0.8:0.1")`
9. Filter values are numbers: `.lpf(800)` not `.lpf("800")`
10. Available waveforms for .sound(): "sine", "saw", "tri", "square", "pulse", "brown", "pink", "supersaw"
11. Available samples for .sound(): "bd", "hh", "sd", "cp", "oh", "ch", "rim"
12. Gain values typically 0.0 to 1.0
13. Available scales: "major", "minor", "dorian", "mixolydian", "pentatonic", "blues", "chromatic"
14. **CHORDS**: `chord()` alone does NOT produce sound. Correct usage:
    `chord("<Am C F G>").voicing().sound("supersaw")`. The chord() takes chord name strings, voicing() turns them into
    notes.
15. **ORBITS**: Effects like delay and reverb are PER-ORBIT. When stacking patterns with different delay/reverb
    settings, EACH layer needs its own `.orbit(N)`. Layers sharing the same effects (or no effects) can share an orbit.
    Dry drums should be on a different orbit than wet synths.
16. Do NOT use the old pattern `n().scale().chord("minor")` — that syntax is wrong. Use `chord("<ChordName>").voicing()`
    instead.
17. **PAN RANGE**: `pan(0.0)` = hard LEFT, `pan(0.5)` = CENTER, `pan(1.0)` = hard RIGHT. The range is 0.0 to 1.0, NOT -1
    to +1.

### Difficulty Guidelines

- **Beginner**: 1-2 functions, immediate result, < 3 code examples. "Paste and play."
- **Intermediate**: 3-5 functions, building on basics, 3-4 code examples. Introduces one new concept.
- **Advanced**: Combines multiple concepts, 4-5 code examples. Pattern design thinking.
- **Pro**: Complex compositions, creative technique, 4-6 code examples. Pushes boundaries.

### Scope Guidelines

- **Quick**: 1-2 minutes to complete, 2-3 sections
- **Standard**: 3-5 minutes, 3-5 sections
- **DeepDive**: 5-10 minutes, 5-7 sections

## Step 5: Register the Tutorial

Edit `TutorialRegistry.kt` to add the new tutorial to `allTutorials`. Add the import and list entry.

## Step 6: Self-Review

Read back the created file and verify:

1. All code examples use only documented functions from the list above
2. Code examples are syntactically valid (method chaining, string arguments, proper parentheses)
3. The tutorial has a clear learning progression
4. The slug is unique
5. Description is one sentence
6. Tags are lowercase single words

Fix any issues found.

## Step 7: Update Generation Log

Edit `GENERATION_LOG.md` to append:

```
| <slug> | <title> | <difficulty> | <scope> | <tags> |
```

## Output

After completion, briefly state:

- Tutorial created: [title]
- Difficulty: [level], Scope: [scope]
- Tags: [list]
- File: [path]
