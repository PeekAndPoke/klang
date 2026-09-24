# Design decisions log: skeleton

Scaffolding only. Telegraph notes, no prose. The maintainer writes the final text.

- Built: 2026-09-24 at `0f1b774f` (`engine-redesign`).
- Markers: `[ME]` maintainer to fill. `[VERIFY]` unsure / conflicting. `[Qn]` = verbatim quote,
  `evidence/maintainer-quotes.md`. `ANS <ts>` = multiple-choice answer, `evidence/maintainer-answers.md`.
  `WP §n` = white paper section. `FED` = `docs/tasks/future/federated-song-sharing.md`. `CLAUDE.md` = rules register.
- Transcript coverage: **2026-08-15 to 2026-09-24 only**. Anything earlier rests on git, DEV-DIARY and docs.
  Every such decision is flagged "no transcript".
- Diary caveat: DEV-DIARY entries v0.3.0 to v0.3.8 (2026-08-13 to 2026-09-07) were written by an agent (commit
  `3d7baebe`, Opus 5 trailer). Authorship of earlier entries `[ME]`.
- "Iterations" counts real reworks named by commit. Keyword counts in `evidence/git-numbers.txt` only size an area.

Template per decision:
- Problem / context · Options (incl. rejected) · Decision + why · Iterations · Evidence · Agent proposal overruled · Status

---

## 0. How the work is done

- **Maintainer role** (evidence: quotes register, answers file)
  - Sets goals and priorities: [P1] goals ranking; CLAUDE.md "Sound first" guideline (2026-07-03).
  - Decides architecture and DSL shape: ch. 3 and 4 below; 72 recorded multiple-choice answers (answers file).
  - Listening verdicts: [P5] checkpoints; [SD1] to [SD7]; `ANS 2026-08-23T22:45` "Build now, I listen after".
  - Inspects diffs before commit when he chooses: [P3].
  - Defines the review regime: [P6] [P7] [P8] [P9]; "prototyping so no need for reviews" [P10].
  - Defines the rules system: [P11]; CLAUDE.md register (commit `0091f7c1`, 2026-09-06).
  - Picks models per job: [A1] [A2] [A3].
  - Refactors by hand in the IDE sometimes: `ANS 2026-09-07T15:58` "I ran it deliberately" (code cleanup);
    2026-08-29 rename `KlangPlaybackController` → `KlangPatternScheduler` "i will do it in the ide"
    (maintainer-messages.txt 2026-08-29T15:20). Songs committed by him: "i commited the song already"
    (2026-08-23T17:29). `[ME]` share of hand-written code? No git signal separates it.
- **Agent role**
  - Implementation, reviews ("/review-loop" skill), measurement, plans in `docs/plans/`, task docs.
  - Agent fleet with model tiers and a single Gradle writer: CLAUDE.md rules "Gradle is a single-writer resource"
    (2026-08-04), "Review rounds 3 and later run on the strongest model tier" (2026-09-05); `.claude/skills/agent-fleet/`.
  - Offers options; maintainer picks (answers file). Where the maintainer deviated from the recommended option:
    ch. 9.
- **Where evidence lives**
  - Git history (2,083 commits); trailers (444 commits); tags (40).
  - DEV-DIARY.MD; `docs/history/2025-Q4.md` .. `2026-Q3.md`; `docs/blog/` (25 posts); `docs/plans/`;
    `docs/tasks/`, `docs/tasks-archive/`; `.claude/skills/`; `.claude/vision/decisions.md` (strategist log).
  - Transcripts (local only): 35 sessions, see `README.md` table.
- **Process iterations** (what changed each time)
  1. 2026-08-02: reviews loop until clean round + mutation checks (CLAUDE.md rule, 2026-08-02).
  2. 2026-08-28: blind first round, later rounds see previous findings [P7]; mutation testing tiered [P8];
     preset pins dropped [P9].
  3. 2026-09-05: rounds 3+ on strongest tier (CLAUDE.md).
  4. 2026-09-06: rules register with hardness [P11]; memory moved into repo [P12] (CLAUDE.md stone 2026-09-05).
  5. 2026-09-18: "world-class" framing for agents, as an experiment [P16] [P17]; effort per round question
     (2026-09-18T08:12).
  6. 2026-09-20: fleet ledger moved to own file (commits `11031109`, `ada4bf6f`).

---

## 1. KlangScript

### 1.1 Own interpreter instead of running strudel-js
- Problem: strudel-js ran on JVM via GraalVM bridge (DEV-DIARY 2025-12-20, 2025-12-23).
- Options: keep GraalJS bridge / own language. `[ME]` others considered?
- Decision + why: own interpreter, sandbox as security boundary "running a stranger's song must never be able
  to touch the filesystem or the network" (WP §3). `[ME]` own reasons.
- Iterations: 2 parsers. 2026-01-06 first KlangScript, parser on better-parse (`c1c5e4e0`; DEV-DIARY 2026-01-06).
  2026-02-17 parser rewrite (`1ba3ae67`, PR #22 `3745d77e`). KSP registration 2026-03-24 (DEV-DIARY).
- Evidence: blog `docs/blog/2026-03-21-growing-our-own-brain`; `docs/history/2026-Q1.md`.
- Agent proposal overruled: no transcript.
- Status: settled.

### 1.2 JS-shaped syntax, Kotlin-shaped stdlib; Kotlin dialect considered and dropped
- Problem: chains break on narrowed return types [D8].
- Options: move KlangScript to a Kotlin dialect [K1] / configure lambdas in current language [K2].
- Decision + why: lambdas for V1 [K2]; dialect dropped "i think we do not even need to keep this at all" [K3].
- Iterations: 1 (2026-09-05 idea → 2026-09-06 dropped).
- Evidence: [K1] [K2] [K3]; CLAUDE.md rule "KlangScript stdlib follows Kotlin conventions" (2026-05).
- Status: settled.

### 1.3 Named parameters: all or none
- Decision: no mixing of positional and named [K4].
- Iterations: KlangScript steps 1 to 5b on 2026-04-16 (`4d32cfca` .. `756d2c69`); sprudel named params
  2026-04-20 to 2026-04-24 (`aaa3e094` .. `be638de9`); DEV-DIARY 2026-06-29 "named arguments finally work".
- Evidence: git; [K4]. Pre-August reasoning: no transcript.
- Status: settled.

### 1.4 Numbers: extension methods, no power operator, Kotlin precedence
- Options: power operator / `^` as power / number extension methods.
- Decision: power operator won't implement [K6]; `^` stays XOR [K7]; `2.pow(2)` style methods; unary minus
  precedence back to Kotlin [K8].
- Iterations: 2 (precedence first deviated from Kotlin, then reverted, 2026-09-09; handover note `bb526904`).
- Evidence: [K6] [K7] [K8]; `docs/tasks-archive/` `[VERIFY]` exact file for number methods.
- Status: settled.

### 1.5 `min` / `max` are clamps on every door
- Problem: `a.max(3)` read as selection surprised the maintainer [K9].
- Decision: clamp semantics on method doors; `Math.min/max` keep selection (`ANS 2026-09-10T09:54`).
- Evidence: [K9]; CLAUDE.md guardrail 2026-09-10 (guards `StdLibOscTest`, `StdLibNumberMethodsTest`).
- Status: settled.

### 1.6 Callable objects: `@KlangScript.Invoke`
- Decision: dedicated annotation so KSP can reject two invokes (no overloads) [K5].
- Evidence: [K5]; CLAUDE.md "Retired" (old spelling refused by KSP); DEV-DIARY 2026-09-07.
- Status: settled.

### 1.7 Two doors: every DSL usable from Kotlin
- Decision: [K11]; CLAUDE.md rule "Two doors, one DSL" (2026-08).
- Status: settled as rule; parity work open: `docs/tasks/dsl-kotlin-surface-parity.md`.

### 1.8 Open
- Union types for doors accepting lambda or instance [K12]: `docs/tasks/klangscript-union-types.md`.
- Runtime errors vs graceful NaN for live coding [K10]. `[VERIFY]` outcome.

---

## 2. Sprudel vs Strudel

### 2.1 From port to sibling
- Problem: started as Strudel via GraalJS, then ported onto KlangScript (DEV-DIARY 2026-01-07).
- Iterations:
  1. 2026-01-09 GraalJS compatibility tests diff event streams (`2a875ca2`).
  2. 2026-03-21 rename strudel → sprudel (`b0c64728`, PR #43 `b1bcc0e3`) "taking our own direction now".
  3. 2026-08-23 heritage abandoned: compat tests cut to structural functions [S3]; heritage filter aliases
     deleted (`50f97bdb`).
  4. 2026-09-07 `lang/addons` dissolved [S14] (`deb1bf80`); CLAUDE.md "Retired": "sprudel is not a Strudel port".
- Decision + why: [S1] [S2] [S4] [S5].
- Evidence: CREDITS.MD lineage; WP §4.
- Status: settled.

### 2.2 Musical time representation
- Problem: float drift broke event fetching [S8] item 1.
- Iterations (git): `d9d35c7a` PR #1 Rational (2026-01-08) → PR #3 rational-number-2 (`373ee1cd`, 2026-01-13) →
  "back to fixed floating point math" (`a3ee1095`, 2026-01-13) → Rational where useful (`4210d0f6`, 2026-02-05) →
  Rational perf, no `Long` (PR #35 `03738d66`, 2026-03-17) → CycleTime fixed point (`13e3be4b`, 2026-05-29).
- `[VERIFY]` count: maintainer lists 4 steps [S8]; blog subtitle says "five representations in five months"
  (`docs/blog/2026-05-29-the-triplet-that-never-drifted`).
- Evidence: [S8]; DEV-DIARY 2026-01-08, 2026-01-13, 2026-02-04, 2026-03-16, 2026-05-29.
- Agent proposal overruled: no transcript. `[ME]`
- Status: settled (guardrail `StructuralCycleSelectionSpec`, CLAUDE.md).

### 2.3 Compound colon strings dropped
- Options: keep `lpf("2000:1:2")` with detection / drop it [S7].
- Decision: drop, inherited from Strudel's parsing constraints [S7]; compressor leaves the wire as a colon string
  (`ANS 2026-08-23T19:24`).
- Iterations: `8fc7993b` C0.1, `a6b7e89e` C0.2 (2026-08-23).
- Status: settled.

### 2.4 Field accessors and compound objects
- Problem: read a voice value into another setter; wish "make the wind whistle a song" (2026-09-06T17:36).
- Options: context key on query ctx (agent) / event mapper / pattern mapper provider / per-field accessor functions.
- Decision: compounds become objects with named slots, `adsr(attack = ...)` sets, `adsr.attack` reads; no namespace
  pollution [S12] [S13].
- Iterations: pilot on `freq` (`1053f694`, 2026-09-06) → batches E, F, G (`d9b6c8e3`, `0c535555`, `95705370`,
  2026-09-07) → per-knob doors retired (CLAUDE.md "Retired" 2026-09-07).
- Agent proposal overruled: ctx copy per control event rejected for cost [S11].
- Status: settled; string-slot readers parked (`docs/tasks/future/string-slot-readers.md`).

### 2.5 Mini-notation mods
- Decision: parse names only, apply via function, not at parse time [S9].
- Evidence: [S9]; DEV-DIARY 2026-08-31 ("attribute block retires").
- Status: settled.

### 2.6 Mutable voice data at runtime
- Decision: deliberate exception to DSL immutability [S10]; CLAUDE.md stone (2026-09-05).
- Evidence: DEV-DIARY 2026-06-06; blog `2026-06-06-twenty-allocations-per-note`. Pre-Aug reasoning: no transcript.
- Status: settled.

### 2.7 Open
- Unordered DSL "a design flaw, but we will not fix it here" [S6].
- Rests in control patterns mean "leave untouched" [S15]. `[VERIFY]` implemented?
- Any pattern kind wraps any other (future launchpad / sheet / tab frontends) [S16].

---

## 3. Motor / engine

### 3.1 The engine is the horse
- Decision: frontends map onto engine components, no DSP of their own [M1].
- Earliest trace: DEV-DIARY 2025-12-25 "Idea arises: Strudel is just one sound event provider ... we could build
  e.g. a midi-player on top". Realised 2026-08-28/29 as MIDI playground (DEV-DIARY 2026-08-29).
- Evidence: [M1]; CLAUDE.md stone (2026-07); "backend never learns about cycles" (stone, 2026-07).
- Status: settled.

### 3.2 Raw motor
- Decision: no silent safety clamps; coerce user inputs, never `require()` [M2] [M26].
- Iterations: NaN heal "patch not a full solution" → widen helper, rename `flushState()`
  (`ANS 2026-08-31T06:34`, `06:40`); size ceiling kept at 10 despite [D13] (`ANS 2026-09-16T13:17`).
- Evidence: CLAUDE.md stone (2026-05); `docs/history/2026-Q2.md` "Engine stays intentionally raw".
- Status: settled.

### 3.3 Block-framing invariance, no late voices
- Problem: voices floored to block start; clock could run negative (2026-08-28T12:05).
- Decision: structurally correct at any block size [M4] [M5]; drop late voices entirely [M6]; split startup race
  from DSP [M7].
- Iterations: plan `a44c23b0`, harness `d8dfc50d` (2026-08-28); O, D, W batches (DEV-DIARY 2026-08-28);
  tolerance window removed 2026-09-03 (maintainer-messages 2026-09-03T11:35).
- Evidence: `docs/plans/block-framing-invariance.md`; block size pinned 128 (DEV-DIARY 2026-08-08; CLAUDE.md guardrail).
- Status: settled.

### 3.4 Realtime voices (MIDI)
- Options: nullable `startTime` (own first idea) / sealed voice hierarchy / separate wire command.
- Decision: separate command on the wire [M8]; no nullable special case [M9]; autoCleanup deferred
  (`ANS 2026-08-28T20:50`).
- Evidence: DEV-DIARY 2026-08-29; `docs/tasks/midi-keyboard-playground.md`; latency: two backend channels [M27].
- Status: MVP settled; playback / interactive split `[VERIFY]` status.

### 3.5 Resource warehouse
- Problem: first-note allocation spike on Fairphone 4 [PF7].
- Decision: build in isolation then integrate [M13]; never shrink [M12]; explicit warmup, no song [M14]; dirty flag
  [M15]; cylinders join warehouse.
- Iterations: steps 2b, 2d, 2e (`ef33ccd4`, `bcf48e84`, `7ea90627`); 5 review rounds to clean (`08f0be8c`);
  first-run spike v2 parked (`docs/tasks/future/first-run-spike-v2.md`).
- Evidence: `docs/plans/resource-warehouse.md`; blog `2026-09-04-seven-megabytes-of-silence`.
- Status: settled; v2 open.

### 3.6 Nothing allocates without cleanup
- Decision: [M18]; CLAUDE.md rule 2026-09-17.
- Status: rule; debt tracked in `docs/plans/signal-flow-redesign.md` §11.

### 3.7 Effect state machines
- Decision: states pre-allocated, no framework, performance first then readability [M20] [M21] [M22].
- Evidence: `docs/plans/effect-state-machines.md`.
- Status: planned `[VERIFY]`.

### 3.8 Transitions: crossfade, parking slot, morph rejected
- Iterations: 1. crossfade + bank cap 10 (`ANS 2026-09-19T15:11`) → 2. formant MORPH built and heard as
  "laser-shot" [SD7] [M24] → 3. two banks + one parking slot, 20 ms, cap removed [M25] [M26].
- Evidence: CLAUDE.md "Retired" 2026-09-20 (MORPH, `MAX_BANKS`); `docs/plans/knob-glide.md`;
  `docs/tasks/future/transition-times.md`; "security net" [M23].
- Status: settled.

### 3.9 One engine in Zig, later
- Decision: single backend impl when it happens [M16]; ints not strings across the boundary [M17]; readable code
  now to ease the port [P2].
- Evidence: `docs/plans/future/zig-motor-one-engine.md` (`e9a34c16`, 2026-09-15); WP §13 "~10× off a native DAW".
- Status: parked (guideline "Sound first").

### 3.10 Smaller engine decisions (one line each)
- Detune affects musical frequencies only, not LFOs [M10].
- Seeded per-voice RNG for reproduction [M11] (`d3b77cc4`, 2026-08-20).
- Voice envelope switched off explicitly, never inferred (DEV-DIARY 2026-08-28; `ad3c217c`).

---

## 4. The DSLs (Ignitor, Pipeline, Master, Katalyst)

### 4.1 Lineage and names
- Iterations:
  1. 2026-03-27 ExciterDsl (`c6fdfb99`; PR #47 `9c4e7e51`, 2026-04-01).
  2. 2026-04-01 Motor metaphor naming: Ignitor, Cylinder, Injection (PR #48 `c393d058`; `docs/history/2026-Q2.md`).
  3. 2026-06-11 Engine DSL (`6f4b179f`); wire format drops kotlinx (`b7b31bbf`).
  4. 2026-06-29 EngineDsl → PipelineDsl (DEV-DIARY 2026-06-29).
  5. 2026-08-03 Master DSL, master-in-pattern (DEV-DIARY 2026-08-03) `[VERIFY hash]`.
  6. 2026-09-17 Katalyst DSL designed (`412a0409`, `a824ba93`); Pipeline heritage named [D15]; retire Pipeline [D24].
- Status: Katalyst in progress; Pipeline retirement planned (`docs/plans/signal-flow-redesign.md`).

### 4.2 Parameter parity, one word per concept
- Problem: roomSize 10× bug (blog `docs/blog/2026-08-03-the-same-word`); filter surface split [S1].
- Decision: [S2] [D2] [D4] [D14]; CLAUDE.md rules "Parameter parity" (2026-08-02), "One word per concept" (2026-08).
- Iterations: reverb/delay parity 2026-08-03 → C series 2026-08-23 (q 0.707, wet law, semitones [D6]) → `freq`
  everywhere (`5332d52d`, 2026-08-25) → reverb one word (`b3472fbd`), delay (`2edb82c0`), 2026-09-16 → compound
  doors fill defaults per param (CLAUDE.md rule 2026-09-18; 2026-09-18T06:24).
- Agent proposal overruled: q-less one-pole swap "no secret swapping" (`ANS 2026-08-24T07:45`).
- Status: settled as rule, applied per surface.

### 4.3 Configure lambdas, builders, immutability
- Decision: builder type per door [D10]; no backward compat [D9]; immutable, returns new instance [D11] [D12].
- Iterations: plan + `/dsl-design` skill (`e889f388`); builders S2 to S7 (`998a735b`, `7572773a`), 2026-09-06.
- Evidence: CLAUDE.md stone "Every DSL value is immutable" (2026-09-05); `ANS 2026-09-05T20:38`.
- Status: settled.

### 4.4 Door shape
- Decision: musical inputs on the door, `wet` first, knobs on builder behind optional trailing lambda
  (`ANS 2026-09-23T11:01` to `11:22`; [D23]).
- Iterations: 2026-09-05 first shape → 2026-09-23 function-by-function walk (commit `0f1b774f`) → 2026-09-24
  `floor` confirmed over agent's `dryFloor` reason; fm `freq` kept hidden (`ANS 2026-09-24T13:43`).
- Evidence: CLAUDE.md rule (2026-09-05, refined 2026-09-23); `docs/tasks/builtin-instruments.md` §3b.
- Status: settled; implementation in progress.

### 4.5 Signal flow: gain, pregain, velocity, Katalyst ownership
- Problem: three level words inherited from Strudel; bus effects on voices [D15].
- Maintainer's positions in sequence, 2026-09-17 to 2026-09-18 (all maintainer-messages.txt):
  1. 13:38 gain at ignitor output, postgain after ignitor stage.
  2. 13:46 to 13:50 rename ideas pregain / gain / busgain.
  3. 14:24 two stages: minimal voice data, then convenience [D16].
  4. 05:07 (09-18) chaining kat "a design mistake" [D17]; classic by default.
  5. 20:43 pregain + velocity slots; 20:47 rejects agent's "magic" [D18]; 21:25 rejects bare-osc equivalence [D20].
  6. 21:44 velocity folds into gain at the frontend [D21]; 21:49 pregain / gain / busgain order.
- Result: `f359f23e` plan; `df93f9f1` "gain is the one level word, velocity folds at the wire, postgain retires";
  `52b89756` (2026-09-19). CLAUDE.md "Retired" `postgain`, wire `velocity` (2026-09-19).
- Evidence: `docs/plans/signal-flow-redesign.md`; maintainer: "the last discussion was very fruitful!" (2026-09-18T22:28).
- Status: in progress.

### 4.6 Naming decisions
- Klangmotör → Klangmotor [D7]; history untouched (`ANS 2026-08-25T06:55`); CLAUDE.md stone 2026-08-25.
- klangblocks removed [D5] (`aa3437ca`, 2026-08-23), built 2026-02-26 (`35398843`).
- `scope` means where audio runs; tutorial one renamed `depth` (`ANS 2026-09-08T11:01`).
- `eq()` → `equalizer()` in ignitor (`ANS 2026-08-25T17:44`); bit ops `bitShl` / `bitShr` (`17:48`).

### 4.7 Open
- Oversampling regions, with factoring as equal goal [D22]: `docs/tasks/oversampling-regions.md`.
- `.kat()` / `.master()` accepting lambda or instance: `docs/tasks/katalyst-master-configure-doors.md`.

---

## 5. Sound design principles

- **Caricature model**: 2 to 4 acoustic tells, fixed learnable target (CLAUDE.md guideline 2026-07;
  `/klang-music-writing`). No transcript for origin. `[ME]`
- **Fixed reference when judging** [SD3].
- **Built-in songs are a test bench, not releases** [D3].
- **By-ear verdicts that changed code**: round-robin "gargling" → normal distribution [SD1]; saw constants "muffy"
  [SD2]; tracking HPF "strange" [SD4]; morph "laser-shot" [SD7].
- **Warmth quarter** (Q2): drift, supersaw rewrite, passive body (`docs/history/2026-Q2.md`; blog
  `2026-06-30-killing-the-plastic-pipe`). No transcripts.
- **Phase pool** opt-in, numbers in DEV-DIARY 2026-08-12; blog `2026-08-12-the-fundamental-lottery`.
- **Bit identity is not a goal; inaudible margin is** [SD6] [PF13]; blog `2026-09-15-the-promise-is-a-margin`.
- **Tutorials**: equal loudness [SD5]; generated corpus wiped (`92f6d54f`, 2026-08-15) after "ai-slop" [A4].
- **Taste includes not doing**: CLAUDE.md guideline "won't-implement is a first-class outcome" (2026-08-20).

---

## 6. Performance

### 6.1 Fairphone 4 as the fixed target
- Decision: judge on a 2021 phone [PF1] [PF2]; normalise by song complexity [PF11]; no global baseline [PF12].
- Evidence: blogs `2026-08-19-the-phone-that-does-not-get-faster`, `2026-09-16-the-score-so-far`.

### 6.2 Unified EQ + graph optimizer
- Options: agent's D0 data said fused EQ wins ~3 to 5 % song CPU; re-scope suggested (`ANS 2026-08-19T11:05`).
- Decision: continue, EQ needed in all DSL layers anyway (same answer); reusable core [D1]; goals [P1];
  only direct filter neighbours fuse [PF4]; not complete but reliable [PF5].
- Outcome: "Massive 33% reduction in cpu time on this machine" [PF3]; phone runs half the song [PF2].
  `[VERIFY]` reconcile 33 % (maintainer, whole-song observation) with 3 to 5 % (agent projection).
- Iterations: D0 baseline `316cc8d8` → EqCore bake-off `c843dc64`, `302e7f66` → wire node `a4b19970` → optimizer
  `076683ab` → `.eq/.band/.tap` doors `03e995ca` (2026-08-19 to 2026-08-21).
- Evidence: `docs/plans/unified-eq.md`; blog `2026-08-20-eleven-loops-one-pass`.
- Status: settled.

### 6.3 Optimizer rounds and affine folding
- Decision: rounds until fixpoint [PF9]; runtime fusion deferred [PF10]; TDD for the optimizer [P15].
- Evidence: `docs/tasks/future/affine-chain-fusion.md`, `docs/tasks/future/ignitor-optimizer-open-items.md`.

### 6.4 Voice culling
- Decision: per-voice window [PF8]. Commit `2133056a` (2026-09-15); blog `2026-09-15-zombies`.

### 6.5 Earlier wins (no transcripts; git + blog only)
- Worklet codec 67 µs → ~385 ns (blog `2026-06-07-...`) `[VERIFY]` ~400 ns in diary.
- Mutable voice data (blog `2026-06-06-...`). Double arrays (DEV-DIARY 2026-04-29). CycleTime (2.2).
- Body resonator moved to orbit, busiest block cut by three quarters (blog `2026-07-04-the-body-that-played-a-thousand-times`).
- Small wins count: [PF6].

---

## 7. Federation (grant scope)

- **Authorship evidence is thin. Read `gaps.md` G2 first.**
  - FED doc: "Captured 2026-08-01 from a design conversation"; committed by maintainer in `9871556b`
    (2026-08-01, "breadcrumbs", no AI trailer). Conversation transcript: **none found**. `[ME]` where did it happen?
  - WP §11 first person: "This is the one I most want argued with". `[ME]` confirm own words.
  - Maintainer transcript touchpoints: [F1] API in white paper; [F2] needs the online portion; [F3] multi-file songs;
    [S16] pattern interop.
  - Earlier KlangScript import / export PoC `tetris` + `tetris-remix` (`docs/history/2026-Q2.md`) `[VERIFY commit]`.
  - Klangbuch: exported parts carry no timing or scale (CLAUDE.md rule 2026-08-20; `.claude/vision/projekt-klangbuch.md`).

| Decision (FED / WP) | Options noted | Why (source) | Status |
|---------------------|---------------|--------------|--------|
| Registry, not DFS | DFS / registry | "a much better-solved problem" (FED "Reframe") | idea set |
| Immutable tags + content hashes | mutable tags | left-pad, Go (WP §11) | idea set |
| Flatten closure at tag time | resolve per hop | one conversation per import (FED §2) | idea set |
| `@latest` edit-time only; pull + TTL | push / webhooks | songs must not change (FED §3) | idea set |
| 5 endpoints + well-known; URL version + manifest `requires` | larger API | "right the first time" (WP §12) | idea set |
| Mandatory licence from small lattice; tag-time check | per-song free text | permission = mechanics (FED "Licensing") | open: set, NC |
| Vendoring pinned closure on home server | evictable cache | server death survivable, lawful via licence (WP §12) | idea set |
| Operator policy in discovery doc | central moderation | liability dial (WP §12) | idea set |
| No scores, no telemetry | curation-as-citation, PageRank (earlier draft) | honour regress; "surveillance built into the instrument" (FED "No scores"; "The regress that forced this decision (2026-08-01)") | settled as principle |
| Backlinks as lists, never numbers | counters | FED "Evidence without metrics" | idea set |
| Mixtape = song@version + cycle window + overrides | reference whole song | songs are infinite (WP §11) | idea set |
| AI-involvement ladder per asset in manifest | none | EU AI Act Art. 50, C2PA prior art (FED §4) | proposal |
| Launch without NC licences | offer NC | fragments the commons (FED warning) | lean |
| Identity bound to home server for v1 | naming layer | "like Go did" (FED "Open questions") | open |

- Agent proposal overruled: no transcript. `[ME]`
- Status overall: design only; parked behind engine, tutorials, launch (FED header; CLAUDE.md "Sound first").

---

## 8. Open decisions (collected)

- Federation: licence set and NC; identity / migration; private songs; engine-version pinning for reproducible
  songs (**no design found**); whether to build before remix demand exists (WP §11 "Genuinely open").
- KlangScript: union types [K12]; runtime error policy [K10].
- Sprudel: ordered DSL [S6]; control-pattern rests [S15]; pattern-kind interop [S16].
- Engine: effect state machines; playback / interactive channels [M27]; first-run spike v2; transition time per
  effect (`docs/tasks/future/transition-times.md`); voice takeover (maintainer "not 100% sold",
  2026-09-08T13:48; `docs/tasks/voice-takeover.md`).
- DSLs: oversampling regions [D22]; Katalyst completion; Pipeline retirement.
- `[ME]` remaining open V1 items: `docs/tasks/_v1-scope.md`.

---

## 9. Agent proposals I rejected

Only cases with evidence in the transcripts (2026-08-15 onward). "Agent proposed" is inferred from the
maintainer's reply unless an answers-file entry shows the agent's text. `[VERIFY]` each against the session.

| # | Date (UTC) | Agent proposed | Maintainer decided | Evidence |
|---|------------|----------------|--------------------|----------|
| R1 | 2026-08-19 | Re-scope unified EQ: D0 says only ~3 to 5 % song CPU | Continue; EQ needed in all layers | `ANS 2026-08-19T11:05`; outcome [PF3] |
| R2 | 2026-08-24 | Omitted q keeps the one-pole path (implicit swap) | Default 0.707, "no secret swapping" | `ANS 2026-08-24T07:45` |
| R3 | 2026-08-27 | Make the context nullable to probe release tail | Rejected, degrades the rest | [M3] |
| R4 | 2026-08-28 | Preset pin tests | "no value" | [P9] |
| R5 | 2026-08-30 | Add ramp shapes for tremolo | Reuse known shapes only | `ANS 2026-08-30T18:20` |
| R6 | 2026-08-31 | Heal NaN in the master only | General fix, `flushState()` | `ANS 2026-08-31T06:34` |
| R7 | 2026-09-06 | Context key copied per control event | Rejected for cost | [S11] |
| R8 | 2026-09-16 | `LangRetiredDoorsSpec` name guards + a rule for them | Remove all; no rule | `ANS 2026-09-16T14:43`; maintainer-messages 2026-09-16T14:41 |
| R9 | 2026-09-18 | Unplaced knob acts as level at output | "magic", rejected | [D18] |
| R10 | 2026-09-18 | `sound(Osc.saw())` ≡ `sound("saw")` | Rejected | [D20] |
| R11 | 2026-09-19 | Remove pipeline phaser fields piecemeal | Retire PipelineDsl in one go | `ANS 2026-09-19T15:11` |
| R12 | 2026-09-23 | Make `eq()` lambda required `[VERIFY]` recommended option | Keep optional | `ANS 2026-09-23T11:15` |
| R13 | 2026-09-24 | `dryFloor` (KDoc reason) / expose fm `freq` | `floor` stands; fm freq hidden | `ANS 2026-09-24T13:43` |

### 9b. Where the agent corrected the maintainer (for balance)
- RingBuffer `prepend()` idea withdrawn: "it would destroy the semantics and would be a forever bug magnet. Good
  call!" (maintainer-messages 2026-08-31T17:31).
- Warehouse not a true singleton: "you are right, not a \"real\" singleton" (2026-09-03T15:58).
- Classic chain: "OK you are right, things are where they belong already." (2026-09-18T21:03).
- Maintainer corrected a reviewer: "this is not kotlin but klangscript code, therefore not an integer division"
  (2026-09-04T05:26).
- Maintainer's own reversals: bank cap 10 → no cap after listening (3.8); nullable `startTime` → own command (3.4).
