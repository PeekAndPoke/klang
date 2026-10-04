# Contributions ledger

What Klang has contributed that a funding committee could fairly call new, each item labelled by the kind of
contribution it is. Scaffolding, like the rest of this folder: the maintainer writes the words a committee reads.

- Started: 2026-10-04 at `917cb8b3` (`ignitor-katalyst-naming`), from the maintainer's first list (phase pool,
  Ignitor/Katalyst slots, KlangScript, WireCodec) and five read-only sweeps over the repo (audio engine,
  KlangScript and code generation, sprudel, UI and learning, the way we work). Assembled by Claude Opus 5.5.
- Prior art comes from the agents' knowledge and one web search (2026-10-04). It is not a literature review.
  "No equivalent known" means exactly that. Every lead entry has a `[VERIFY]` line naming the search still owed.
- Numbers are quoted from the record named next to them. Checked against the source in this pass: the WireCodec
  table and the phase pool's Selected Mapping credit. The rest are `per <source>`.
- Keep it current: a new entry gets an ID, a date and its evidence; a claim that a search knocks down is moved to
  "Not claimed" with the reason, never deleted.

## Categories

| Category | Meaning | The question a committee asks |
|---|---|---|
| **Novel mechanism** | A mechanism we know no prior form of | "Has nobody done this?" |
| **Domain transfer** | Known in another field, new in audio or live coding | "Where does it come from, and what did it take to make it work here?" |
| **Recombination** | Known parts, combined so the whole does what no part did | "What does the combination do that the parts don't?" |
| **Design model** | A new way to structure or name things, so users or code can do more | "What can a user do now that they could not before?" |
| **Technical optimization** | A known thing made measurably faster or smaller | "By how much, measured how?" |
| **Engineering discipline** | Verification or invariants of unusual rigour | "How do you know it is right?" |
| **Method** | How one human and many AI agents build software | "Could another team adopt it?" |
| **Pedagogical design** | How the platform teaches | "Does it help people learn?" |
| **Integration** | A platform-level whole nobody else has assembled | "Is it really the first?" |

Status: **built** (in the code, with tests) or **planned** (designed, not built). Confidence: how sure we are that
the item is a contribution in its category, not whether it exists.

## Lead entries

### L1. Phase pool for unison oscillators

- **Category:** Domain transfer (Selected Mapping from OFDM radio, 1996) **plus** a novel mechanism (the pool).
  The maintainer's first label was "recombination". The scoring-and-selection core is a transfer from a single
  field, and the blog credits it as such. The parts that look new are the band target, the pool and its refresh.
- **Status:** built, off by default (`SUPERSAW_PHASE_POOL = 0.0`).
- **What:** a supersaw's N voices start at random phases. At low notes that draw fixes the fundamental for the whole
  note, so about one note in five comes out hollow. A phase set's coherence K = |Σ gₙ e^{i2πφₙ}| / Σ gₙ is scored
  in closed form, about 11 sin/cos, before any audio exists, and K does not depend on pitch. At note-on the engine
  keeps the best of M candidates, aiming at a **band**, not the maximum: K→1 is a thin aligned saw, and the band
  is also a timbre control. Accepted sets live in a bounded per-orbit pool:
  - Warm-up is spread across notes, so there is no stall.
  - Entries are served round-robin.
  - About every 10th note draws fresh and evicts a **random** entry. Evicting the worst would pull the pool toward
    the band centre. The result is that the instrument slowly becomes a different individual while staying in band.
  - Two orbits develop different characters.
  - A seed makes offline renders reproducible.
  - Off is bit-identical to the old rng stream.
- **Numbers:** per the blog and the task record:
  - Measured hole rate on 120 repeats of E2: 11 to 21 % of notes at least 6 dB hollow.
  - The Rayleigh model predicted 16 to 21 %.
  - Predicted after selection: about 1 in 2,400.
- **Prior art:**
  - Selected Mapping (Bäuml, Fischer, Huber 1996) and Schroeder phases (1970), both credited in the blog.
  - JP-8000 free-running phases.
  - Serum and Vital phase randomisation, and phase reset.
  - No synth known to score and select phase sets.
- **Evidence:**
  - `audio_bridge/src/commonMain/kotlin/constants/OscillatorTuning.kt` (about lines 77 to 213), `IgnitorDsl.kt`
    (the `phasePool` field on the super-* nodes).
  - Specs: `PhasePoolStateSpec`, `PhasePoolSelectionModesSpec`, `PhasePoolDslSeamSpec`, `PhasePoolBypassGoldenSpec`.
  - Records: `docs/tasks-archive/2026-08/20260812-unison-phase-pool.md`, `docs/blog/2026-08-12-the-fundamental-lottery`.
- **Outside Klang:** any unison, supersaw or additive-stack synth (Vital, Surge, game audio). More generally, any
  generator that should be "random, but never a dud".
- **Confidence:** medium-high.
- **Caveat:** the bands are calibrated for 7 to 11 voices. Above 16 voices, selection falls back toward max-K.
- `[VERIFY]` A search of synth patents and DAFx/ICMC papers for "phase selection" and "unison peak factor".

### L2. Slot-addressable instruments and effect chains (Ignitor / Katalyst)

- **Category:** Design model, built by recombination.
- **Status:** built (phase 3 of the signal-flow redesign, 2026-09).
- **What:** an instrument (Ignitor) and a bus effect chain (Katalyst) are typed, serialisable trees whose knobs are
  named slots (`<door>.<param>`). Any pattern can write any slot through `ignp` / `katp`, and control patterns and
  joins work as for any other field. So an author builds an instrument and then plays its knobs from the music,
  live, without the engine knowing that instrument in advance. Supporting rules:
  - **Off means not built:** a stage whose gate knob sits at its off value is never constructed.
  - **Fill rule:** naming a stage fills only the companions the event has not set, and an explicit value is never
    overwritten. This makes partial configuration order-independent.
  - **Compound objects read as well as write:** `adsr(attack = ...)` sets, `adsr.attack` reads, so one setter can
    consume another's value (`.lpf(adsr.attack)`).
- **Prior art:**
  - SuperCollider `NamedControl`, `Pbind` and `NodeProxy.set`.
  - Tidal/Strudel named controls mapped by SuperDirt.
  - Faust and Csound named controls; VST/CLAP parameters.
  - Bitwig and Ableton modulators.
  - Dead-branch elimination at graph build.
- **What is ours:**
  - One slot namespace, owned by a typed tree that crosses a wire.
  - Reached from the same expression language that builds the tree.
  - Works at both voice and bus level, from one pattern.
  - Specified merge semantics.
  - Readers symmetric to writers.
- **Evidence:**
  - Code: `audio_bridge/.../IgnitorDsl.kt`, `IgnitorDslRuntime.kt` (`gatedOff`), `IgnitorDslClassic.kt`,
    `sprudel/.../ParamBag.kt`, `_classic_slot_params.kt`.
  - Docs: `audio/ref/off-values.md`, `docs/plans/signal-flow-redesign.md`,
    `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md`, `/dsl-design` §4.
- **Outside Klang:** any pattern or sequencer engine paired with a graph synth. Game audio parameter systems.
- **Confidence:** medium.
- `[VERIFY]` A side-by-side with SuperCollider `NodeProxy` roles and Faust's UI-parameter addressing.

### L3. One declaration, every surface (KlangScript's KSP pipeline)

- **Category:** Recombination.
- **Status:** built (processor since 2026-03-24, `a2e604e3`).
- **What:** one annotated Kotlin function with its KDoc becomes all of the following, so runtime, editor and manual
  cannot drift apart:
  - reflection-free script dispatch (arity overloads, named arguments, baked default thunks)
  - analyzer type signatures, completion and hover docs
  - manual pages whose examples play
  - a scope badge (voice / orbit / master) that shows where a setting acts
  - a **visual parameter editor bound to one argument** (`@param-tool amount SprudelGainEditor, ...`)

  The editor reads the argument's source and writes source back, so code stays the truth. Because the registered
  surface is all a script can reach, the pipeline is also the sandbox boundary. One alignment function
  (`ArgAlignment.positionalTargets`) serves the interpreter and the analyzer, so completion inside a configure
  lambda types `x` exactly as the runtime binds it.
- **Prior art:**
  - OpenAPI and Swagger codegen, Dokka, SWIG.
  - Rust doc-tests.
  - Bret Victor's scrubbable numbers, Light Table, Hydra widgets.
  - The TypeScript language service sharing a checker with the compiler.
- **What is ours:** the visual editor and the playable example are declared in the same comment as the binding, and
  resolved by the same argument binding the type checker uses.
- **Evidence:**
  - Code: `klangscript-ksp/.../KlangScriptProcessor.kt`, `klangscript/src/commonMain/kotlin/runtime/ArgAlignment.kt`,
    `klangscript-ui/.../codemirror/ArgFinder.kt`, `klangui/.../KlangUiTool.kt`,
    `sprudel/src/commonMain/kotlin/lang/lang_dynamics_level.kt:59`.
  - Docs: `docs/blog/2026-08-12-one-annotation-six-artifacts` (draft), `klangscript/ref/intel-analyzer.md`.
- **Outside Klang:** any embedded DSL that has an editor.
- **Confidence:** medium for the param-tool binding, low as a claim for the pipeline as such.

### L4. WireCodec

- **Category:** Technical optimization, with a derived schema hash as engineering discipline.
- **Status:** built (2026-06-07, `096e5e7d`).
- **What:** `@WireFormat` marks the roots of the page-to-worklet protocol. A KSP processor walks the type graph and
  emits per-type encode and decode for JS objects, not bytes:
  - **Trusts its input:** page and worklet are one build, so the decoder skips name matching and unknown-key
    handling.
  - **Fails the build** on a shape it cannot encode, instead of dropping data.
  - **Stamps a structural hash** of the whole schema on every envelope, so a stale cached worklet fails loudly.
    Nobody bumps a version.
- **Numbers:** node, per scheduled voice, checked against the blog table:

  | Operation | kotlinx | generated, 2026-06-07 | generated, 2026-09-16 |
  |---|---:|---:|---:|
  | decode (runs on the audio thread) | 67,001 ns | 385 ns, 174x faster | 562 ns, voice data 72 to 82 fields |
  | encode | 4,904 ns | 478 ns | 848 ns |

  A hand-written proof decoded in 398 ns, so generating the codec costs nothing against writing it by hand.
- **Prior art:** protobuf, FlatBuffers, Cap'n Proto, Avro schema fingerprints, kotlinx.serialization, Moshi codegen.
- **Evidence:**
  - Code: `audio-wire-codec-ksp/src/main/kotlin/WireCodecProcessor.kt`, `WireCodecRoundTripSpec`,
    `IgnitorDslWireCodecSpec`.
  - Records: `docs/benchmarks/2026-09-16_worklet-serialization_nodejs.md`,
    `docs/blog/2026-06-07-sixty-seven-microseconds-to-385-nanoseconds`.
- **Outside Klang:** any Kotlin/JS worker or worklet boundary.
- **Confidence:** high as an optimization, low as an invention.
- **Caveat:** these are node micro-benchmarks, not measurements on the browser audio thread.

### L5. Provenance that rides the values

- **Category:** Novel mechanism (modest), or a domain transfer from provenance tracking.
- **Status:** built (2026-01-20 to 01-23; WebGL highlight 2026-06).
- **What:**
  - Every KlangScript literal carries its source span, and equality ignores it.
  - Function calls receive per-argument spans.
  - Pattern events carry a `SourceLocationChain` that transformations extend and never overwrite.
  - The scheduler notifies the editor about 25 ms ahead of the sound.

  So in `let feel = 1.0; gain(feel)` the `1.0` lights up when the note plays, through a variable and a function
  call. Highlights are pooled WebGL quads, not one DOM mark per event.
- **Prior art:** Strudel highlights mini-notation atoms through parser offsets; source maps.
- **What is ours:** provenance as a property of runtime values, across variables and arguments, not of syntax.
- **Honest limit:** arithmetic cuts the thread (`feel * 2` has no birthplace). Transforms applied per query
  (`sometimesBy`) are not captured.
- **Evidence:** `sprudel/src/commonMain/kotlin/SprudelPatternEvent.kt`, `CodeMirrorHighlightBuffer.kt`,
  `docs/blog/2026-01-20-the-position-that-survived`.
- **Confidence:** medium.

### L6. CycleTime: musical time as exact integer ticks in a Double

- **Category:** Technical optimization, with a domain transfer (DAW tick grids into a pattern algebra).
- **Status:** built (2026-05-29, `13e3be4b`).
- **What:**
  - 110,100,480 ticks per cycle (2^20 · 3 · 5 · 7), so thirds, fifths, sevenths and twenty halvings are exact.
  - Held in a Double as an integer, so it is fast and safe on Kotlin/JS without `Long` or rationals.
  - `+`, `-` and `times(Int)` are bit-exact, with no gcd.
  - Exact to about 80 million cycles.
  - It took five representations in five months to get here.
  - The guard `StructuralCycleSelectionSpec` covers the one bug class a fixed grid introduces: `slow(N)` when N
    does not divide the tick count.
- **Numbers:** per the blog, node, per operation (no whole-query number exists):
  - `plus`: 92 ns (BigInt rational) to 5.7 ns.
  - step map: 186 ns to 5.4 ns.
- **Prior art:** Tidal/Strudel exact rationals; MIDI PPQN and DAW tick bases.
- **Evidence:** `common/src/commonMain/kotlin/math/CycleTime.kt`, `docs/blog/2026-05-29-the-triplet-that-never-drifted`.
- **Outside Klang:** Strudel and any JS pattern engine.
- **Confidence:** medium.

### L7. The whole platform on Kotlin Multiplatform

- **Category:** Integration.
- **Status:** built (first commit 2025-12-20).
- **The claim we can defend:** to our knowledge, Klang is the most complete music environment on Kotlin
  Multiplatform. One Kotlin engine runs both in a browser AudioWorklet and on the JVM. Around it:
  - its own sandboxed language
  - a pattern engine
  - instrument and effect design
  - offline rendering
  - an editor with visual parameter tools
  - a tutorial curriculum
- **The claim we cannot defend:** "the first Kotlin Multiplatform synth". Prior art found 2026-10-04. Dates are
  repository creation dates from the GitHub API. Klang's first commit is 2025-12-20, and its first AudioWorklet
  commit is 2025-12-25 (`6a0d1c54`).
  - **Earlier than Klang:**
    - [fsynth](https://github.com/fsynthlib/fsynth) (2018): KMP, waveform synthesis in Kotlin, a song DSL and a
      web player.
    - [PatchCore](https://github.com/SillyDevices/PatchCore) (2025-06): a KMP modular-synth library with a C++ core.
    - [KSyn](https://github.com/philburk/ksyn) (2025-11-29): Phil Burk's KMP port of JSyn.
    - [Sonicgraph](https://github.com/DataInfraNerd/Sonicgraph) (2025-12-05): a single-commit KMP demo.
    - [PunKt](https://github.com/pjagielski/punkt) (2020): Kotlin live coding, JVM only, sound from SuperCollider.
  - **Later than Klang:**
    - [Orphic-FM](https://github.com/balch/orphic-fm-app) (2025-12-31): C++ engine, Tidal live coding.
    - DaKapo / "Livecoding soundscapes", FOSDEM, 2026-02-01.
    - [Klarinet](https://github.com/vectencia/Klarinet) (2026-04).
    - [afterfade-dsp](https://github.com/874wokiite/afterfade-dsp) (2026-09).
    - [Ambient / kmp-procedural-audio](https://github.com/hyshu/kmp-procedural-audio) (2026-09-29; the blog post is
      2026-09-30).
- **What none of them has together:** pure-Kotlin DSP running in both the AudioWorklet and on the JVM, plus their
  own language, pattern engine, instrument design and editor. Orphic-FM comes closest as a whole product, but its
  DSP is C++ and its live coding is Tidal.
- **Confidence:** medium for the narrow claim.
- `[VERIFY]` One more search for KMP DAWs and sequencers before an application cites this.

### L8. A curriculum the build can check

- **Category:** Pedagogical design, enforced by tests.
- **Status:** built (tracks 2026-08-17).
- **What:** lessons are Kotlin data. `TutorialCurriculumSpec` fails when:
  - A lesson uses a function that its track has not taught yet and that is not declared as a preview. The check
    runs per track, through `buildsOn` prerequisites.
  - A commented A/B alternative no longer compiles.
  - A diagram's values differ from the code next to it.
  - A cross-reference says "last lesson" instead of naming it.

  The problem it answers was real: an AI-generated corpus of 38 lessons taught out of order and was wiped on
  2026-08-15 (`92f6d54f`).
- **Prior art:** doctests, mdBook example tests, nbval; concept graphs (Khan Academy); graded readers.
- **What is ours:** prerequisite order applied to the identifiers of a live DSL, per track, on every build.
- **Evidence:** `src/jvmTest/kotlin/TutorialCurriculumSpec.kt`, `src/commonMain/kotlin/pages/docs/tutorials/TutorialModel.kt`,
  `docs/tasks/tutorial-curriculum.md`.
- **Outside Klang:** any API or DSL with a tutorial.
- **Confidence:** medium.

### L9. Agent instructions get postmortems (escape ledger)

- **Category:** Method; a domain transfer of blameless postmortems to the instructions agents work from.
- **Status:** in use since 2026-09-18.
- **What:** each CRITICAL or MAJOR a review finds counts as a defect an earlier stage let through. It is classified
  (brief, checklist, test, design, tooling, recurrence) and closed by changing one artefact. A row that restates
  the problem does not count as closed. If a class that already has a rule escapes again, the rule's **text** is
  declared failed and rewritten. Example: one rule text escaped three times because a dozen copies could not be
  corrected together, which produced the convention "one home for a rule's text, never copied" (`/dsl-design` §4).
- **Prior art:** blameless postmortems, five whys, escaped-defect analysis, poka-yoke.
- **What is ours:** the thing being repaired is the prose that steers agents, and recurrence is read as that prose
  failing.
- **Evidence:** `.claude/skills/review-loop/escape-ledger.md`, `.claude/skills/review-loop/SKILL.md` Standard 3.
- **Outside Klang:** any repo that has CLAUDE.md or AGENTS.md.
- **Confidence:** medium.

### L10. The operating regime for one human and many agents

- **Category:** Method, by recombination.
- **Status:** in use (dates per piece).
- **What:**
  - **Rules register with hardness levels** (stone, rule, guideline, guardrail; 2026-09-06). Its key line is
    "session instructions are not rules": a request is scoped to its session until it lands in the register with
    a date. It was born from a scored clean-up of 89 stale agent memories.
  - **A Retired register,** guarded by specs (`RetiredIgnitorNamesSpec`), so agents do not bring back dead names.
  - **Review protocol:**
    - Reviews loop until a clean round, because a fix is itself an unreviewed change.
    - Round 1 reviewers see no history.
    - Later rounds review first, then reconcile with earlier findings in the same agent. A reviewer keeps a finding
      only by naming what is factually wrong with its rejection.
    - The model tier rises per failed round.
  - **Mutation checks by agents under a lock:** every new test must be seen red. The critical section is
    mutate, build, restore, under one `flock`, because a build racing an edit gives a verdict about code nobody
    chose.
  - **Defect-density ledger:** tests a prompt hypothesis (the "world-class" opening line). It is labelled
    "a theory, not a proven rule".
- **Prior art:** RFC 2119, ADRs, AGENTS.md, Cursor rules; Fagan inspections, blind peer review, LLM debate; PIT and
  Stryker; ArchUnit fitness functions.
- **What is ours:** the pieces fitted together, dated, and corrected from evidence.
- **Evidence:** `CLAUDE.md`, `.claude/skills/review-loop/`, `.claude/skills/agent-fleet/` (both ledgers),
  `console/with-build-lock.sh`, `docs/housekeeping/2026-09-06-memory-and-rules-inventory.md`.
- **Outside Klang:** high. It is the easiest item to share, because it is text.
- **Confidence:** medium as a method. Low for any claim about its effect: the sample is about 20 uncontrolled rows,
  and the commit-message convention appears in about 49 of 2,321 commits.

### L11. Checked provenance of human authorship in AI-assisted work

- **Category:** Engineering discipline, as tooling.
- **Status:** built (2026-09-24).
- **What:**
  - The maintainer's typed words are rebuilt from transcripts, with tool output and agent traffic excluded.
  - 125 quotes are checked verbatim by script.
  - Every number comes from one script.
  - A gaps file lists where the record is thin.
  - A chapter lists agent proposals the maintainer rejected.
  - `Co-Authored-By` trailers are mandatory since 2026-09-24.

  It answers the question "how much of this is generated?" with evidence, not assurance.
- **Prior art:** commit trailers, Linux `Assisted-by`, reproducible-research artefact evaluation.
- **Evidence:** this folder (`README.md`, `scripts/`, `evidence/`, `gaps.md`).
- **Confidence:** medium.

## Supporting engineering

These are good work, described as such and not claimed as inventions. They answer "how do you know it is right?"
and "is it fast enough on a phone?".

| ID | Item | Category | Number or guard | Source |
|---|---|---|---|---|
| S1 | Optimizer promise as a margin: rewrites stay within 1e-12 of the block's loudest sample, fuzz harness built before the rules | Engineering discipline | `OPTIMIZER_PARITY` | `IgnitorDslOptimizer.kt`, blog `2026-09-15-the-promise-is-a-margin` |
| S2 | Body and vowel moved from every voice to the bus, configured by lease, crossfaded on rebuild | Technical optimization | median cost down a fifth, busiest block down three quarters (per blog) | blog `2026-07-04-the-body-that-played-a-thousand-times` |
| S3 | Zombie voices: silent voices stop rendering but keep ownership, found by a null-diff 28 dB off | Technical optimization | drums 53 % cheaper, song 17 to 19 % (per blog) | blog `2026-09-15-zombies` |
| S4 | One mutable voice record per event, cloned once at the leaf | Technical optimization | 20 allocations per note to 1, clone about 17x faster (per blog) | blog `2026-06-06-twenty-allocations-per-note` |
| S5 | Effect lifecycles as pre-allocated state machines; whole-chain swap under live audio, never a hard cut | Engineering discipline | transition tables in KDoc | `ChainSwap.kt`, `docs/plans/effect-state-machines.md` |
| S6 | Block-framing invariance as a testable property ("output depends on when a note starts, never on how time is chopped") | Engineering discipline | `IgniteOnsetOffsetSpec` | `docs/plans/block-framing-invariance.md` |
| S7 | Warm-up runs every node kind in silence; an exhaustive `when` makes a new kind fail to compile until decided | Engineering discipline | `WarmupVocabularySpec` | blog `2026-09-04-seven-megabytes-of-silence` |
| S8 | Minimax sine, bit-identical on JVM and JS; a fourth polynomial measured and not built | Technical optimization | about 4x per call on V8 (per blog) | blog `2026-09-15-eleven-digits-of-sine` |
| S9 | Two-timescale drift (Ornstein-Uhlenbeck), block-stepped and ramped, shared-to-per-voice spread with exact ends | Recombination | "cost nothing measurable" (per blog) | blogs `killing-the-plastic-pipe`, `drift-for-free` |
| S10 | Verification protocol: engagement controls (a deliberate mutant must move the predicted rows), oracle in the test, raw-double renders | Engineering discipline | 18 songs hashed over 256 cycles per rename step | `audio/ref/verification.md` |
| S11 | Door parity: every DSL addition ships in KlangScript and Kotlin with a spec comparing both against the bare data class | Engineering discipline | `KlangScript*DoorParitySpec` | blog `2026-08-03-the-same-word` |
| S12 | The old Strudel-JS demoted to a differential-testing oracle with graded verdicts during the language switch | Domain transfer | `JsCompatTests` | blog `2026-03-21-growing-our-own-brain` |
| S13 | Caricature sound model: 2 to 4 real acoustic tells, a fixed learnable target, no randomised parameters | Design model | rule since 2026-07 | `CLAUDE.md`, `/klang-music-writing` |
| S14 | Benchmarking on a fixed 2021 phone, ratios against an untouched control row, every speed claim paired with its sound cost | Engineering discipline | | blog `2026-08-19-the-phone-that-does-not-get-faster`, `audio/ref/performance.md` |

## Planned, not built

| ID | Item | Category | Where |
|---|---|---|---|
| P1 | Render QA gate for ear training: every lesson's A/B pair level-matched within about 1 dB and audibly different, checked in the docs build | Domain transfer (psychoacoustic method into CI) | `docs/tasks/tutorial-curriculum.md` |
| P2 | "Crowded, not just loud": which source owns how much of a frequency band, pointing back to the line of code | Design model | `docs/plans/realtime-analysis.md` |
| P3 | Federated song registry: a licence lattice that gates imports, credits computed from the pinned closure, citations as cycle windows, no scores | Recombination | `docs/tasks/future/federated-song-sharing.md`, whitepaper part II |

## Not claimed

Recorded so nobody claims them by accident.

- The pattern algebra (cycles, joins, mini-notation, `fast`/`slow`, stack): Tidal and Strudel, credited in `CREDITS.MD`.
- Seconds-only wire: the SuperCollider and SuperDirt architecture. In Klang it is a rule, not an invention.
- The master limiter: the Signalsmith construction (Luff 2022), credited in the blog.
- The reverb (Freeverb-style), the SVF with diode-style damping, the polyphase oversampler: known designs.
- KlangScript's sandbox as a security claim: it is a capability sandbox (a script reaches only what was registered)
  with a call-depth cap, and **no** time or instruction limit. Do not call it safe against a hostile infinite loop.
- The tutorial depth ladder, the scope badge on its own, and "did you mean" diagnostics: ordinary.
- Callable objects (`@KlangScript.Invoke`), `min`/`max` as clamps: careful API decisions, not inventions.
- Lazy delay rings, append-only catalogue indices, choke groups, silence culling: standard systems practice.

## Open checks

- L1: a search of patents and DAFx/ICMC papers for analytic phase selection in unison synthesis.
- L2: a written comparison with SuperCollider `NodeProxy` and Faust parameter addressing.
- L7: one more search for KMP DAWs and sequencers (Ambient and KSyn dates settled 2026-10-04).
- The numbers marked "per blog" in S2 to S9: re-check against their task records before they go into an
  application.
