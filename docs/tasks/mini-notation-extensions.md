# Mini-Notation Extensions: Attribute Blocks and MIDI Recording

> **Status (2026-04-13)**: Phase 1 (`{key=value}` parser + AST + renderer + resolver) is DONE.
> `MnNode.Attrs` in `MnNode.kt`, `applyAttrs()` in `MnPatternToSprudelPattern.kt`,
> tests in `MiniNotationAttrsSpec.kt`.
> **Remaining work:** Phase 2 (tutorial docs — tracked in `tutorial-master-plan.md`) and
> Phase 3 (MIDI recording, still future).

## Context

MIDI keyboard integration for Klang requires a new mini-notation feature:
**Attribute block `{key=value}`** — inline per-note properties (legato, velocity, pan, etc.)

This also lays groundwork for MIDI-to-mini-notation recording (quantize keyboard input to patterns).

Detailed implementation plan: `/home/gerk/.claude/plans/snuggly-cuddling-hippo.md`

### Design decision: no `_` tie step

We considered a `_` tie step (monophonic sustain) but dropped it because:

- `@N` (weight) already handles "this note takes N slots"
- `{legato=N}` handles polyphonic sustain (note rings over others)
- `_` created ambiguity in edge cases (`[c d] _` — which note sustains?)
- One less concept to maintain

---

## Attribute Set — Dynamics & Routing (initial scope)

Short keys for frequently used attrs, full names always work as aliases.

| Short  | Full name(s)               | VoiceData field              | Type              | Example                   |
|--------|----------------------------|------------------------------|-------------------|---------------------------|
| `v`    | `vel`, `velocity`          | `velocity`                   | Double            | `{v=0.8}`                 |
| `g`    | `gain`                     | `gain`                       | Double            | `{g=0.5}`                 |
| `l`    | `legato`                   | `legato`                     | Double            | `{l=2}`                   |
| `pan`  | —                          | `pan`                        | Double            | `{pan=-0.5}`              |
| `pg`   | `postgain`                 | `postGain`                   | Double            | `{pg=0.5}`                |
| `adsr` | —                          | attack:decay:sustain:release | Double (compound) | `{adsr=0.01:0.1:0.5:0.3}` |
| `o`    | `orbit`, `cyl`, `cylinder` | `cylinder`                   | Int               | `{o=2}`                   |
| `bank` | —                          | `bank`                       | String            | `{bank=casio}`            |

Separator inside `{}`: **space**. `=` is its own token so `{v = 0.8}` also works.

Phase 2 applies attrs by calling existing DSL functions (`gain()`, `velocity()`, `adsr()`, etc.)
to guarantee semantic consistency.

Future tiers (filters, effects, FM, etc.) can be added incrementally by extending
the resolver table — no parser changes needed.

---

## Overarching Work Plan

### Phase 1: Attribute Block `{key=value}` — DONE (2026-04-13)

Landed in commit `b609e6af` ("mini-notation parser additions").

| Step | What                                                                            | Files                                                 |
|------|---------------------------------------------------------------------------------|-------------------------------------------------------|
| 1a   | AST: add `Attrs` class, add to `Mods`, update `isEmpty`                         | `MnNode.kt`                                           |
| 1b   | Tokenizer: add `{` `}` `=` as tokens, literal break for all three               | `MiniNotationParser.kt`                               |
| 1c   | Parser: `{...}` modifier in `parseStep()`, consume LITERAL EQUALS LITERAL pairs | `MiniNotationParser.kt`                               |
| 1d   | Renderer: append attrs in `renderMods()`                                        | `MnRenderer.kt`                                       |
| 1e   | Tests: parse, round-trip, spaces around `=`                                     | New `MiniNotationAttrsSpec.kt` + `MnRoundTripSpec.kt` |
| 1f   | Phase 2: `applyAttrs()` calling existing DSL functions for 8 attributes         | `MnPatternToSprudelPattern.kt`                        |
| 1g   | Tests: voice data verification for all 8 attrs                                  | `MiniNotationAttrsSpec.kt`                            |
| 1h   | Error tests: unclosed `{`, unexpected `}`                                       | `MiniNotationErrorReportingSpec.kt`                   |

### Phase 2: Documentation — Tutorials (depends on Phase 1)

See "Tutorial Documentation Plan" section below.

**Also includes:**

- Add `MiniNotation` to `TutorialTag` enum
- Tag existing mini-notation tutorials with new tag
- Write 4 new mini-notation tutorials (see below)

### Phase 3: MIDI Recording (future — depends on Phase 1)

Not in scope for immediate implementation. Design notes:

- Web MIDI API receives Note On/Off with timestamps
- Quantize to grid (user-selected resolution: 1/8, 1/16, etc.)
- Serialize: pitch → atom, velocity → `{vel=N}`, held notes → `@N`
- Polyphonic input → stacked layers or per-pitch patterns
- Piano pedal (CC 64) → `{legato=N}` on affected notes

---

## Relationship to Other Open Tasks

### Independent (no dependencies either way)

| Task                                     | Why independent                            |
|------------------------------------------|--------------------------------------------|
| `klangscript-native-object-operators.md` | KlangScript interpreter, not mini-notation |
| `klangscript-intellisense.md`            | Diagnostics/completion, different system   |
| `completion-member-access-bug.md`        | Code editor bug, unrelated                 |
| `code-quality-review.md`                 | General quality items                      |
| `soundfont-looping-investigation.md`     | Audio playback bug                         |
| `audio-pipeline-open-topics.md`          | Future audio features                      |
| `ignitor-dsl-open-items.md`              | Synthesis DSL features                     |

### Loosely related (shared code areas but no blocking dependency)

| Task                          | Relationship                                                                                                                                               |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `strudel-ui-tools.md`         | Proposes a **legato editor tool** (Tier 1). Once `{legato=N}` exists in mini-notation, the editor tool could generate/read it. Not blocking.               |
| `klang-blocks-take-1.md`      | Block editor converts AST ↔ blocks. `Attrs` on `Mods` will eventually need block representations. Not blocking — blocks can ignore unknown mods initially. |
| `klang-pattern-extraction.md` | Extracts playback to generic `KlangPattern`. Mini-notation changes are upstream (parser level) and don't conflict. Could be done in parallel.              |

### Sequencing recommendation

```
  ┌──────────────────────────┐
  │  Phase 1: Attrs {k=v}   │
  │  (no blockers)           │
  └────────────┬─────────────┘
               │
       ┌───────┴───────┐
       │               │
  ┌────▼─────────┐ ┌───▼──────────┐
  │  Phase 2:    │ │  Phase 3:    │
  │  Tutorials   │ │  MIDI        │
  │              │ │  Recording   │
  └──────────────┘ │  (future)    │
                   └──────────────┘
```

All other open tasks (`audio-pipeline`, `ignitor-dsl`, `klangscript-*`, `code-quality`, etc.) can proceed independently
in any order.

---

## Tutorial Documentation Plan

### Prerequisites

- Add `MiniNotation("Mini-Notation")` to `TutorialTag` enum in `TutorialModel.kt`
- Add `MiniNotation` tag to existing tutorials: `spacesAndRests`, `groupAndAlternate`, `nestedPatterns`,
  `miniNotationMastery`

### Mini-Notation Series (existing + new)

| # | Tutorial                 | Difficulty   | Status          | Covers         |
|---|--------------------------|--------------|-----------------|----------------|
| 1 | Spaces and Rests         | Beginner     | exists, add tag | space, `~`     |
| 2 | Group and Alternate      | Intermediate | exists, add tag | `[]`, `<>`     |
| 3 | **Speed and Weight**     | Beginner     | **new**         | `*`, `/`, `@`  |
| 4 | **Chance and Choice**    | Intermediate | **new**         | `?`, `\|`      |
| 5 | **Repeat and Euclidean** | Intermediate | **new**         | `!`, `(p,s,r)` |
| 6 | Nested Patterns          | Advanced     | exists, add tag | deep nesting   |
| 7 | **Attribute Blocks**     | Intermediate | **new**         | `{key=value}`  |
| 8 | Mini-Notation Mastery    | Pro          | exists, add tag | all combined   |

All tutorials tagged `[MiniNotation, Patterns]`.

### New Tutorial 3: "Speed and Weight" (Beginner)

**File:** `tut_SpeedAndWeight.kt`

**Sections:**

1. **Multiply for speed** — `sound("bd*2 sd")` plays kick twice as fast
2. **Divide to slow down** — `sound("bd hh/2")` hi-hat spans two beats
3. **Weight for proportion** — `sound("bd@3 sd")` kick gets 3/4 of the cycle
4. **Combining them** — build a groove with unequal beat lengths

### New Tutorial 4: "Chance and Choice" (Intermediate)

**File:** `tut_ChanceAndChoice.kt`

**Sections:**

1. **Random choice with |** — `sound("bd | sd")` picks one per event
2. **Probability with ?** — `sound("hh hh? hh hh?0.3")` random drops
3. **Combining for variation** — evolving patterns that never repeat exactly

### New Tutorial 5: "Repeat and Euclidean" (Intermediate)

**File:** `tut_RepeatAndEuclidean.kt`

**Sections:**

1. **Bang for repeats** — `sound("bd!4")` = `sound("bd bd bd bd")`
2. **Euclidean rhythms** — `sound("bd(3,8)")` distributes 3 hits across 8 slots
3. **Rotation** — `sound("bd(3,8,2)")` rotates the pattern
4. **World rhythms** — famous patterns: `(3,8)` tresillo, `(5,8)` cinquillo

### New Tutorial 7: "Attribute Blocks" (Intermediate)

**File:** `tut_AttributeBlocks.kt`

**Sections:**

1. **Why attribute blocks?** — Inline per-note control with `{key=value}`
2. **Velocity accents** — `sound("bd{v=1} hh{v=0.3} sd{v=0.8} hh{v=0.3}")`
3. **Panning** — `sound("bd{pan=-0.5} sd{pan=0.5}")` — spatial placement
4. **Legato / let ring** — `n("e2{l=4} b3 e3 d4")` — polyphonic sustain
5. **Bank switching** — `sound("bd bd{bank=casio} sd bd{bank=MPC60}")`
6. **ADSR shaping** — `n("c4{adsr=0.5:0.2:0.7:0.3} e4{adsr=0.01:0.1:0.5:0.1}")`
7. **Combining attributes** — `n("c4{v=0.8 pan=-0.3 l=2}")`
8. **Attributes on groups** — `n("[c4 e4 g4]{l=2 v=0.6}")`
