# Klang Project

Enjoy the ride. Errors happen, no worries, we find them, we fix them. 
We write exceptional software. We are an awesome team and we give our very best.
Der Weg ist das Ziel. Sound first!

## Rules register

Every standing rule of this project, with its hardness. Detail lives in the skill or file named in
the last column; this table is the index. Curated 2026-09-06
(`docs/housekeeping/2026-09-06-memory-and-rules-inventory.md`).

**Hardness levels**

| Level         | Meaning                                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------------------|
| **stone**     | Never deviate. Changing it is a maintainer decision, recorded here with a date.                                |
| **rule**      | The default. Deviate only with a stated reason in the report or the diff.                                      |
| **guideline** | Taste and preference. Apply judgement; deviating needs no justification, but say so when it matters.           |
| **guardrail** | A check against a known failure. Skip it only when you have verified that the failure cannot occur here.       |

**Session instructions are not rules.** What the maintainer says in a session applies to that
session or that phase of work. It becomes a rule only when it lands in this table or in a skill
with a date. Examples that were never meant to last: "stop before commit" (a per-phase request,
superseded 2026-09-06 by "commit completed steps"), "this file is owned by another session",
"do not compile the frontend right now".

### Stone

| Rule                                                                                                                                                   | Since      | Detail                                    |
|--------------------------------------------------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------|
| Complexity is the enemy: before build-time magic or similar weight (cross-module generated sources, processor options, unusual Gradle wiring, clever indirection), STOP and consult. Prefer the plain module boundary, function, data class. | 2026-09-06 | this file                                 |
| Memory lives in the repo: module `MEMORY.md` files, `docs/tasks/`, skills. Never the home-directory auto-memory. No home for a fact? Ask where it belongs. | 2026-09-05 | this file                                 |
| The engine is the horse: a frontend (sprudel, MIDI, a sequencer) never gets DSP of its own; it maps onto existing engine components via the wire.       | 2026-07    | `/dsl-design` §8                          |
| The backend never learns about cycles; only seconds cross the wire.                                                                                     | 2026-07    | `/dsl-design` §8                          |
| Every DSL value is immutable at construction time; runtime mutability is engine-internal (sprudel voice data is the deliberate exception).             | 2026-09-05 | `/dsl-design` §1                          |
| No boxed types anywhere: no `Long`/`ULong`/`Byte`/`Short`/`Char`; `Int` or `Double`.                                                                    | 2026-04    | `/code-style` §5                          |
| The Motor stays raw: no safety clamp on an audio parameter without asking. Coerce user-reachable inputs, never `require()` them.                       | 2026-05    | `/dsl-design` §6, `/code-style` §21       |
| Licensing: AGPL v3 with `AUTHORS.MD`; `tones/` stays MIT and never gets the AGPL header. Commercial use waits for copyright-audit task 07 (lawyer).    | 2026-06-24 | `LICENSE`, `docs/tasks/copyright-audit-07-control-vocabulary-legal-review.md`, `docs/tasks-archive/2026-09/20260927-copyright-audit-00-overview.md` |
| Naming: "Klangmotor" / "Motor" with a plain o (the umlaut was retired 2026-08-25, "too much ego"); historic diary and strategist records keep whatever they say; Motörhead keeps its umlaut. | 2026-08-25 | `/code-style` §17 (header)                |

### Rule

| Rule                                                                                                                        | Since      | Detail                                          |
|-----------------------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------------|
| Reviews loop until a clean round; every new test is mutation-checked (mandatory tier for engine/wire/KSP, light elsewhere). | 2026-08-02 | `/review-loop`                                  |
| Gradle is a single-writer resource: one build at a time, always through `console/with-build-lock.sh`; the coordinator owns it, reviewers may build under the lock (a doubt tested by a mutation stays inside one lock call and is restored), other workers never fan out. | 2026-08-04, reviewers 2026-10-04 | `/agent-fleet` |
| Published writing (whitepaper, blog, README, release notes, site copy) follows the public voice: friendly, calm and humble, inviting, human, "we", no hype, cautious with claims. Tutorials and in-app text have their own style. | 2026-09-28 | `/public-voice` |
| Two doors, one DSL: every surface addition lands in KlangScript stdlib AND Kotlin in the same deliverable, with a door-parity spec. | 2026-08 | `/dsl-design` §3                                |
| Parameter parity: same name, meaning and scale on every surface; conversions in one place; asymmetries recorded with a reason. | 2026-08-02 | `/dsl-design` §4                              |
| One word per concept end to end; a replaced surface is removed, not deprecated.                                              | 2026-08    | `/dsl-design` §5                                |
| Door shape: musical inputs on the door (`wet` always first), secondary knobs on a builder behind a `configure` lambda that is last and always optional, dynamics stages flat, one shape per concept across the DSLs. | 2026-09-05, refined 2026-09-23 | `/dsl-design` §2, `.claude/skills/dsl-design/door-shapes.md` |
| Wire types over enums: sealed `@WireName` hierarchies for wire-visible distinctions; enum only for a closed param-less set.  | 2026-08    | `/dsl-design` §7                                |
| KlangScript stdlib follows Kotlin conventions, not JavaScript (naming, argument style, `name = value` named args).           | 2026-05    | `klangscript/MEMORY.md` Design Decisions        |
| Code style: braces always, blank lines around `if`, flat directories, no FQCN, exhaustive `when`, NaN-guard comment, named arguments where they could be swapped (2026-10-07), no allocation or exceptions in hot paths, flush IIR state, copyright header. | 2026-04 | `/code-style` |
| No em-dashes in user-facing text (docs, KDoc, UI, tutorials, commits, reports).                                              | 2026-08    | `/code-style` §22                               |
| AST walkers and analysis utilities live in `klangscript`, never in UI modules.                                               | 2026-05    | `/code-style` §18 neighbourhood, `klangscript/CLAUDE.md` |
| Klangbuch exported parts carry no arrangement timing and no scale; both live at song level.                                  | 2026-08-20 | `sprudel/MEMORY.md` Lessons                     |
| Plans live in `docs/plans/`, tasks in `docs/tasks/`, finished tasks in `docs/tasks-archive/<month>/`.                        | 2026-06    | `docs/tasks/_priorities.md`                     |
| Commit completed, reviewed steps on the working branch; leave work uncommitted only when the maintainer asks to inspect first. | 2026-09-06 | this file                                     |
| Every commit an AI agent wrote or co-wrote carries a `Co-Authored-By:` trailer naming the model (e.g. `Claude Opus 5.5 (1M context) <noreply@anthropic.com>`); a commit the maintainer wrote alone carries none. The trailer is the provenance record funding applications rely on, so it is never dropped, not even for a one-line fix. Commits before 2026-08 are inconsistent: `docs/funding/gaps.md` G4. | 2026-09-24 | this file |
| Compound doors fill per param at the door: a call that names a stage writes every companion it left out and the event has not set, from the constant in `audio_bridge/constants/`; an explicit value is never overwritten. A stage with a name knob is named only by that knob, and a tail-only call never invents it; a stage without one is named by any of its knobs. Which door is which: the two closed lists in `/dsl-design` §4, the one home of this rule's text, never copied. | 2026-09-18 | `/dsl-design` §4, checklist 11 and 12 |
| Nothing in the backend or the frontend allocates without a way to clean it up: per-playback state lives in a storage the playback owns or in a per-playbackId registry that is freed when the playback dies; process-wide maps that grow per edit are debt (tracked in `docs/plans/signal-flow-redesign.md` §11). | 2026-09-17 | this file |
| Script-door defaults are plain literals (number, string, boolean, null); the KSP build refuses any other default (e.g. a `Slots.*` leaf) with an error naming the door and parameter. Bake the literal on the door, resolve the real default in the body. | 2026-09-05, enforced 2026-10-06 | `/dsl-design` §3 |

### Guideline

| Guideline                                                                                                                  | Since      | Detail                                     |
|----------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------|
| Sound first: engine work-streams, then the tutorial quarter, then launch, then hard performance work. Do not push launch planning or native/Wasm backends before then. | 2026-07-03 | `docs/tasks/_priorities.md`, `docs/tasks/future/high-performance-audio-backend.md` |
| Taste is also what you do not do: won't-implement is a first-class outcome, even for a designed feature; check upstream (mixing, levels) before animating a component. | 2026-08-20 | `/dsl-design` §9 |
| Caricature sound model: 2 to 4 real acoustic tells, sparse fill, tuned by ear, a fixed learnable target (no randomised or adaptive parameters). | 2026-07 | `/klang-music-writing` |
| Design for adults that kids also enjoy, never the reverse (Pixar, not PBS Kids).                                          | 2026-07    | this line                                  |
| "What we built": credit the collaboration in reports and docs.                                                            | 2026-07    | this line                                  |
| Tutorial craft (per-orbit effects, `chord().voicing()`, pan 0 to 1, lpf harsh waves, sculptor comments, series across levels, maintainer writes the jingle). | 2026-07 | `docs/tasks/tutorial-curriculum.md` appendix |
| Klang UI conventions: `.with()` for custom classes, `.render()` on stored icon functions, RoundGauge proportions.          | 2026-05    | `/kraft-knowhow`                           |
| Review rounds 3 and later run on the strongest model tier (`fable`) at effort xhigh, never max.                            | 2026-09-05, capped 2026-09-28 | `/agent-fleet`                             |
| Whitespace and blank-line findings are not worth a round; codefactor.io fixes formatting.                                  | 2026-07    | `/review-loop` Gotchas                     |
| Module `MEMORY.md` files state what is true NOW and stay short (restructured 2026-09-29 after `audio/MEMORY.md` reached 3,000 lines): a change updates its section in place and adds one History line; the narrative lives in the task record, the full dated record in `<module>/ref/memory-history.md`. | 2026-09-29 | the knowhow skills |
| Scaffolding goes when its job is done: a migration guard, a one-off script or a comparison fixture is removed in the change that finishes the migration, so no future reader wonders why it exists. | 2026-09-06 | this line |

### Guardrail

| Guardrail                                                                                                                  | Since      | Detail                                     |
|----------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------|
| Block size is pinned to 128 frames everywhere (it is a tone parameter); never raise it to speed up a render.                | 2026-08    | `audio/MEMORY.md`, `DelayLine` KDoc        |
| Deliberate engine exceptions a reviewer must not "fix" (reverb `+ ANTI_DENORMAL`, the OnePole HPF bias, linear bandpass, the house limiter's fixed lookahead, authored lookahead uncompensated): the list and the reasons are in `audio-constraints.md`. | 2026-05, narrowed 2026-09-27 | `.claude/skills/review-loop/audio-constraints.md` |
| Structural cycle selection (`arrange`, `<...>`) uses exact integer-cycle selection; the N-does-not-divide-T bug class is proven. Guard: `StructuralCycleSelectionSpec`. | 2026-07 | `sprudel/MEMORY.md` Lessons |
| Builtin songs are KlangScript inside Kotlin strings: `/` divides, `$` interpolates.                                          | 2026-09    | this line                                  |
| `min`/`max` are clamps on every door: `a.max(b)` is "a, at most b". The Ignitor doors therefore build the opposite-named node (`max` builds `IgnitorDsl.Min`); the nodes and the runtime `Ignitor.min`/`max` primitives keep the mathematical meaning, and `Math.min(a, b)`/`Math.max(a, b)` still select. Do not "correct" the crossing. Guard: `StdLibIgnitorTest`, `StdLibNumberMethodsTest`. | 2026-09-10 | `/dsl-design` §5 |

### Retired names

A retired name is never restored and never cited as current. The full list, newest first, with what replaced each
name and where it was decided, is `docs/retired-names.md`; read it when an old name turns up (for example when
porting an old song). History keeps its words.

## Agents and skills

The project skills live in `.claude/skills/` (each `SKILL.md` says when to use it) and the agents in
`.claude/agents/`; the harness lists both, with their triggers, to every session. Use `/skill-name` or describe
the task. The `music-platform-strategist` agent runs only when asked for by name ("talk to the strategist").
