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
| Licensing: AGPL v3 with `AUTHORS.MD`; `tones/` stays MIT and never gets the AGPL header. Commercial use waits for copyright-audit task 07 (lawyer).    | 2026-06-24 | `LICENSE`, `docs/tasks/copyright-audit-00-overview.md` |
| Naming: "Klangmotor" / "Motor" with a plain o (the umlaut was retired 2026-08-25, "too much ego"); historic diary and strategist records keep whatever they say; Motörhead keeps its umlaut. | 2026-08-25 | `/code-style` §17 (header)                |

### Rule

| Rule                                                                                                                        | Since      | Detail                                          |
|-----------------------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------------|
| Reviews loop until a clean round; every new test is mutation-checked (mandatory tier for engine/wire/KSP, light elsewhere). | 2026-08-02 | `/review-loop`                                  |
| Gradle is a single-writer resource: one build at a time, the coordinator owns it, workers never fan out.                    | 2026-08-04 | `/agent-fleet`                                  |
| Two doors, one DSL: every surface addition lands in KlangScript stdlib AND Kotlin in the same deliverable, with a door-parity spec. | 2026-08 | `/dsl-design` §3                                |
| Parameter parity: same name, meaning and scale on every surface; conversions in one place; asymmetries recorded with a reason. | 2026-08-02 | `/dsl-design` §4                              |
| One word per concept end to end; a replaced surface is removed, not deprecated.                                              | 2026-08    | `/dsl-design` §5                                |
| Door shape: knobs on a builder behind a `configure` lambda, construction inputs on the door, `configure` last and optional. | 2026-09-05 | `/dsl-design` §2, `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md` |
| Wire types over enums: sealed `@WireName` hierarchies for wire-visible distinctions; enum only for a closed param-less set.  | 2026-08    | `/dsl-design` §7                                |
| KlangScript stdlib follows Kotlin conventions, not JavaScript (naming, argument style, `name = value` named args).           | 2026-05    | `klangscript/MEMORY.md` Design Decisions        |
| Code style: braces always, blank lines around `if`, flat directories, no FQCN, exhaustive `when`, NaN-guard comment, no allocation or exceptions in hot paths, flush IIR state, copyright header. | 2026-04 | `/code-style` |
| No em-dashes in user-facing text (docs, KDoc, UI, tutorials, commits, reports).                                              | 2026-08    | `/code-style` §22                               |
| AST walkers and analysis utilities live in `klangscript`, never in UI modules.                                               | 2026-05    | `/code-style` §18 neighbourhood, `klangscript/CLAUDE.md` |
| Klangbuch exported parts carry no arrangement timing and no scale; both live at song level.                                  | 2026-08-20 | `sprudel/MEMORY.md` Lessons                     |
| Plans live in `docs/plans/`, tasks in `docs/tasks/`, finished tasks in `docs/tasks-archive/<month>/`.                        | 2026-06    | `docs/tasks/_priorities.md`                     |
| Commit completed, reviewed steps on the working branch; leave work uncommitted only when the maintainer asks to inspect first. | 2026-09-06 | this file                                     |

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
| Review rounds 3 and later run on the strongest model tier.                                                                 | 2026-09-05 | `/agent-fleet`                             |
| Whitespace and blank-line findings are not worth a round; codefactor.io fixes formatting.                                  | 2026-07    | `/review-loop` Gotchas                     |
| Scaffolding goes when its job is done: a migration guard, a one-off script or a comparison fixture is removed in the change that finishes the migration, so no future reader wonders why it exists. | 2026-09-06 | this line |

### Guardrail

| Guardrail                                                                                                                  | Since      | Detail                                     |
|----------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------|
| Kotest: one unquoted `--tests` FQCN per Gradle run; treat `No tests found` as a script error in any expect-red runner.      | 2026-07-03 | `/review-loop` Gotchas                     |
| A frontend watcher blocks Gradle only in continuous mode (`-t` / `--continuous`); a plain `jsBrowserDevelopmentRun` does not. | 2026-09-09 | `/review-loop` Gotchas                     |
| Block size is pinned to 128 frames everywhere (it is a tone parameter); never raise it to speed up a render.                | 2026-08    | `audio/MEMORY.md`, `DelayLine` KDoc        |
| Deliberate engine exceptions a reviewer must not "fix": reverb uses `+ ANTI_DENORMAL` (not `flushState`); OnePole HPF cutoff bias is documented, not corrected; BPF stays linear; the master limiter lookahead is master-only. | 2026-05 | `/review-loop` templates, `docs/tasks/audio-backend-audit.md` §7 |
| Script-door defaults must be safe literals; a `Slots.*` default makes KSP emit no thunk and named calls that skip it fail at runtime (the KSP guard catches floatable shapes only). | 2026-09-05 | `/dsl-design` §3 |
| Structural cycle selection (`arrange`, `<...>`) uses exact integer-cycle selection; the N-does-not-divide-T bug class is proven. Guard: `StructuralCycleSelectionSpec`. | 2026-07 | `sprudel/MEMORY.md` |
| Builtin songs are KlangScript inside Kotlin strings: `/` divides, `$` interpolates.                                          | 2026-09    | this line                                  |
| `min`/`max` are clamps on every door: `a.max(b)` is "a, at most b". The Ignitor doors therefore build the opposite-named node (`max` builds `IgnitorDsl.Min`); the nodes and the runtime `Ignitor.min`/`max` primitives keep the mathematical meaning, and `Math.min(a, b)`/`Math.max(a, b)` still select. Do not "correct" the crossing. Guard: `StdLibOscTest`, `StdLibNumberMethodsTest`. | 2026-09-10 | `/dsl-design` §5 |

### Retired, do not restore or cite

`klangblocks` (removed 2026-08-23, never user-visible); the `Motör` spelling; the sub-type method
chain on oscillators (`Osc.supersaw().voices(9)`, gone 2026-09-05); `MasterFx.*` doors; the single
envelope doors `attack()`, `decay()`, `sustain()`, `release()` (gone 2026-09-07, `adsr(attack = ...)`
sets a slot and `adsr.attack` reads it); the per-knob effect doors and their aliases (`roomWet`,
`roomsize`/`rsize`/`sz`/`size`, `roomfade`, `roomlp`, `roomdim`, `delayWet`, `delaytime`, `delayfeedback`/`delayfb`,
`delaycap`/`dcap`, `ph`, `phaserWet`, `phasercenter`/`phc`, `phasersweep`/`phs`, `phaserFloor`, the `tremolo*`/`trem*`
knobs, `dist`, `distos`, `distortshape`/`dshape`, `crushos`, `coarseos`, the `*Oversampling` spellings; gone
2026-09-07: `room`, `delay`, `phaser`, `tremolo`, `distort`, `crush`, `coarse` are objects with named slots,
`room(fade = 0.3)` sets, `room.fade` reads); the filter per-knob doors `lpq`, `lpx`, `lpe`, `lpadsr`, `hpq`, `hpx`, `hpe`,
`hpadsr`, `bpq`, `bpe`, `bpadsr`, `notchf`, `nresonance`/`nres`/`notchq`/`ntq`, `ntf`, `nfadsr`, `nfattack`/`nfa`, `nfdecay`/`nfd`,
`nfsustain`/`nfs`, `nfrelease`/`nfr`, `nfenv`/`nfe` (gone 2026-09-07: `lpf` and `hpf` carry `q`, `passes`, `env` and the envelope
stages as slots, `bpf` and `notch` the same without `passes`); the singular `adsrCurve` on every surface (`adsrCurves(a, d, r)` only); the batch G per-knob doors `vibratoMod`,
`pattack`/`patt`, `pdecay`/`pdec`, `prelease`/`prel`, `pcurve`/`pcrv`, `panchor`/`panc`, `fmenv`/`fmmod`, `fmh`, `fmattack`/`fmatt`,
`fmdecay`/`fmdec`, `fmsustain`/`fmsus`, `duckorbit`, `duckattack`/`duckatt`, `duckdepth`, the pattern-level `voices`, `spread`, `panSpread`
(the ignitor builders keep their own `voices()`/`spread()`),
`vowelWet`, `vowelFloor`, `bodyWet`, `bodyFloor` (gone 2026-09-07: `compressor`, `unison`, `duck`, `vibrato`, `penv`, `fm`,
`vowel`, `body` carry them as slots; `comp`, `uni`, `vib`, `pamt` stay). Guard for all: `LangRetiredDoorsSpec`. The spelling
`@KlangScript.Method(name = "invoke")` for a callable object (replaced 2026-09-07 by `@KlangScript.Invoke`; KSP
refuses the old one). The sprudel `lang/addons/` directory, the package
`io.peekandpoke.klang.sprudel.lang.addons` and the `addon` doc tag (gone 2026-09-07: sprudel is not a Strudel port,
so "what Strudel does not have" named nothing a reader could use; every DSL file is now
`lang_<group>_<subgroup>.kt` in `sprudel/.../lang/`, see `docs/tasks-archive/2026-09/20260907-sprudel-lang-file-reorganisation.md`).
`TutorialScope` and the tutorial field `scope` (renamed 2026-09-08 to `TutorialDepth` / `depth`, with
`scopeLabel`/`scopeColor` and the `?scope=` URL parameter: Quick/Standard/DeepDive is a depth ladder, and
"scope" now means one thing only, WHERE audio runs, see `KlangScope` and the `@scope` KDoc tag).

## Available Agent

| Agent                       | Trigger                                                                                                       | Description                                                                                                                                                         |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `music-platform-strategist` | **Explicit only**: say "music-platform-strategist" or "talk to the strategist" or "platform strategy session" | Strategic product advisor. NOT a coder. Reasons about user value, platform surface, and launch readiness. Has persistent memory. Saves output to `.claude/vision/`. |

## Available Skills

Use `/skill-name` or describe what you need in natural language to invoke a skill.

| Skill                    | Trigger                                                                                        | Description                                                                                               |
|--------------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `/skill-builder`         | "create a skill", "build a skill", "audit a skill"                                             | Guide skill creation, optimization, and auditing following Claude Code best practices                     |
| `/sprudel-dev-knowhow`   | "work on sprudel", "implement sprudel feature", "sprudel tests"                                | Load sprudel module architecture, critical rules, current status, and feature checklist                   |
| `/klangscript-knowhow`   | "work on klangscript", "add language feature", "klangscript parser", "klangscript interpreter" | Load klangscript context incrementally (dispatcher + targeted ref files)                                  |
| `/klangaudio-knowhow`    | "work on audio", "audio engine", "voice synthesis", "effects", "sample loading", "orbits"      | Load audio subsystem context (audio_bridge / audio_be / audio_fe / audio_jsworklet)                       |
| `/code-style`            | "apply code style", "check code style", "clean up code style", "follow code conventions"       | Project code style rules (curly braces, formatting, etc.)                                                 |
| `/dsl-design`            | "design a DSL", "add a DSL door/knob", "review a DSL change", "builder", "configure lambda", "DSL immutability", "parameter parity" | Design principles for every Klang DSL: construction-time immutability, builder/configure-lambda door shape, two doors, parity, one word per concept, coerce vs raw, wire types, review checklist |
| `/review-loop`           | "review this change", "code review", "review loop", "mutation check", "apply review findings"  | Review standard: reviews loop until a clean round (fixes get re-reviewed); new tests are mutation-checked |
| `/ultra-libs-knowhow`    | "ultra libs", "ultra.html", "ultra events", "io.peekandpoke.ultra"                             | Source reference for all `io.peekandpoke.ultra.*` modules (html, streams, common, etc.)                   |
| `/kraft-knowhow`         | "kraft", "kraft component", "kraft forms", "kraft routing", "io.peekandpoke.kraft"             | Source reference for the Kraft UI framework (components, VDom, forms, routing, etc.)                      |
| `/klang-music-writing`   | "write music", "compose", "make a beat", "create an instrument", "sound design"                | LLM-ready reference for writing sprudel patterns and designing ignitor instruments                        |
| `/klang-music-recording` | "record audio", "render to wav", "export wav", "offline render", "record.sh"                   | Offline WAV rendering pipeline: CLI commands, KlangOfflineRenderer, WavFileWriter                         |
| `/six-hats`              | "six hats", "thinking hats", "multi-perspective analysis"                                      | Run Six Thinking Hats method: 5 parallel agents (Red/Black/Yellow/Green/Blue) + synthesis                 |
| `/agent-fleet`           | "fan out agents", "launch agents in parallel", "spawn sub-agents", before any multi-agent run  | Model/effort tiers per sub-agent + fan-out safety (coordinator owns Gradle; workers never fan out)        |
