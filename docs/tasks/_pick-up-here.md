# Pick up here — resume notes (written 2026-08-01)

> Written when the project went on pause (limited time, ~mid-July 2026). This is the "where were we"
> file: repo state, what the last conversations were about, loose ends, and a suggested order of
> action. Companion to [`_priorities.md`](_priorities.md) (the ranked backlog) and the Q3 plan
> ([`../history/2026-Q3.md`](../history/2026-Q3.md)).

## 1. Repo state at pause

- **`main` is clean and everything is merged.** Last feature merge: **PR #64 `ignitor-dsl-surface`
  (2026-07-10)**. After that only song-polish commits ("Songs", Jul 16–25) across the builtin songs (Der Schmetterling,
  Irish Lament Techno, Stranger Things, Tetris, Sakura, A Truth Worth Lying For, FF7 Prelude) + CREDITS page.
- Everything the memory notes flagged as *"uncommitted"* **is now in main** (verified — e.g. the live-update
  `dedupAgainstActive` fix sits in `VoiceScheduler.kt`): live-update double-voice fix,
  `detune→spread` rename, Pipeline DSL Phase B (inline `.pipeline(...)`), the SuperSaw typed osc subtype + static
  supertype inferrer, Adsr `declickSeconds`/`expK` knobs.
- Suite was green (JVM+JS) at merge time. **First step on resume: re-run the full suite** to confirm the baseline
  (remember: no concurrent Gradle builds — KSP cache corruption).
- Only unmerged branch: `code-blocks-with-mutator` — a stale klangblocks MVP from 2026-02. Ignore.

## 2. What the last conversations were about (Jul 4 → Aug 2)

Chronological digest of the final work sessions, so nothing gets lost:

1. **Der Schmetterling CPU deep-dive (Jul 4)** — built the `runSongBenchmark` harness (root module)
    + frozen song fixtures + isolated `EffectBenchmark` cases. Finding: `superimpose` × per-voice effect was the CPU
      sink; **body/vowel then moved to orbit level (Katalyst)** which defused most of it — only `analog` still
      multiplies per voice. Spawned the [`voice-culling.md`](voice-culling.md)
      idea (−80 dB real-output culling). Also probed the ~2.5× JVM-vs-JS gap (accepted for now; native/Wasm backend
      stays parked post-launch).
2. **Phoneme singing / robot voice idea (Jul 5)** — `note("c d e f").sing("klæŋ ˈɔdiˌoʊ …")`, caricature robot singing
   via one built-in `sing` sound + `singer()` register knob. ⚠️ **Both the plan doc and the M1 implementation are LOST**
   (verified 2026-08-01): the plan file
   `docs/tasks/phoneme-singing.md` was never committed, and the branch `singing-like-a-robot` that held the implemented
   M1 grin test no longer exists (deleted unmerged; no phoneme code anywhere in the repo). Only the design summary in
   memory `project_phoneme_singing` survives — recreate plan + M1 from there if the idea stays alive.
3. **Body-filter materials (Jul 4–8)** — **`cedar` + `brass` materials, `BODY_FLOOR` 0.6→0.4, and the
   `SprudelBodyEditorTool` UI (live modal-fingerprint SVG) are implemented and in main** (with Fletcher & Rossing /
   Benade credits in `CREDITS.MD` + CreditsPage). The Jul 8 session also brainstormed further materials (oak, other
   tonewoods, more metals) — exploratory only, no task doc. Materials remain hard-coded tune-by-ear mode tables (memory
   `project_body_resonator`); the longer-term path is [
   `future/ir-to-modal-table-extraction.md`](future/ir-to-modal-table-extraction.md).
4. **"SynthSturm" Sandstorm homage (Jul 8)** — educational demo song shipped; write-up archived at
   `../tasks-archive/2026-07/20260708-synthsturm-sandstorm-homage.md`. Verdict: "cute for educational purposes",
   captures the built-in-synth-demo vibe.
5. **Strategist: "Motör Hits" trademark (Jul 9–13)** — idea to finance the project via mobile apps named "Motör Hits
   #1/#2/…". Strategist verified the Kilmister Trust holds MOTÖRHEAD; app-store takedown is the acute risk →
   **recommendation: name consumer apps "Klang Hits" instead; Motör stays the behind-glass engine brand**. Docs:
   `docs/strategy/brand-trademark-checklist.md` + strategist memories (`project_brand_architecture_klang_motor`,
   `project_motorhead_trademark_risk`). Real trademark attorney clearance needed before spending money on this.
6. **Song polish passes (Jul 16–25)** — by-ear tuning of the builtin songs using the freshly-merged DSL surface. No open
   ends.
7. **Federated song sharing idea (Aug 1, the pause session)** — everyone can run a Klang server; cross-server imports
   (`import {bass} from "klang.art:peekandpoke/super-song@v1.0"`) with immutable song-level version tags,
   content-addressed blobs, tag-time flattening of transitive imports, and pull-based `@latest`. Captured (post-launch,
   sound-first) in
   [`future/federated-song-sharing.md`](future/federated-song-sharing.md).
8. **D6 pivot + review standard (Aug 2)** — two decisions: (a) **master-in-pattern** — the D6 master path was redesigned
   to ride the voice stream (see Step 2.1 below); detailed plan in
   [`master-dsl.md`](master-dsl.md). (b) **New `/review-loop` standard** codified as a skill:
   reviews loop until a clean round (fixes get re-reviewed by fresh agents), and every new test is mutation-checked
   (mutate → red → restore). Applies to all future work, starting with D6.

## 3. Loose ends (small but easy to lose)

- **[`_priorities.md`](_priorities.md) is still a draft.** The joint review of the ordering never happened. The one
  flagged decision: **Katalyst DSL — Act 1 (with the other engine-authoring DSLs)
  or the lower track?** It's marked MUST but Q3 sequenced it low. Decide on resume.
- **Phoneme singing: plan doc + M1 implementation lost** (see §2.2) — recreate from memory
  `project_phoneme_singing` if pursued.
- **Engine tuning Part B wrinkle undecided** — the Phase-2 character knobs are plain `Double`s, but
  `EngineDefault` resolution needs nodes; settle the resolution path *before* flipping defaults ([
  `engine-tuning-profile.md`](engine-tuning-profile.md) §"The known wrinkle").
- **Trademark/legal** — "Klang Hits" naming decision + attorney clearance (if the app idea stays alive); the copyright
  audit (`copyright-audit-07`) remains ⚪ blocked on external IP counsel.
- Memory notes saying "uncommitted" are stale — corrected 2026-08-01; if one slips through, trust
  `main`.

## 4. Suggested order of action on resume

**Step 0 — warm-up (half a session):** run the full JVM+JS suite; skim `_priorities.md` + this file; play a song or two
to get the ears back.

**Step 1 — decide the two open questions** (cheap, unblocks everything):

1. Katalyst DSL: Act 1 or later? (If Act 1: after D6, since the orbit bus feeds the master stage.)
2. Engine tuning Part B `Double`-vs-node resolution path.

**Step 2 — Act 1, finish the engine** (the Q3 "sound first" core, in order):

1. **D6 master path** — [`per-playback-engine.md`](per-playback-engine.md) §H, **REVISED 2026-08-02:
   master-in-pattern** — `master(Master().gain().limiter())` rides the voice stream via the Ignitor/Pipeline
   registration playbook (rest-carrier event once per cycle;
   `"~".slow(8).master(...)` makes it patternable → fades/endings; no `Song.master`, no
   `Cmd.SetMaster`, no settings UI). **Detailed plan: [`master-dsl.md`](master-dsl.md).** *The*
   next feature; the reason the whole multi-engine foundation was built. Then richer master chain / crossfade /
   metering.
2. **Engine tuning profile Phase 3** — [`engine-tuning-profile.md`](engine-tuning-profile.md)
   Part B (`EngineDefault` sentinel + `EngineTuning` profile, e.g. future c64/nes identities); Part A #3 (analog-drift
   carrier params) folds into it. Parts A #1/#2 are done.
3. **Katalyst DSL** — [`katalyst-dsl.md`](katalyst-dsl.md) (if confirmed Act 1): author the per-orbit chain that today
   is hardcoded at `Cylinder.kt:68`.
4. **Resource warehouse pool** — [`resource-warehouse-pool.md`](resource-warehouse-pool.md):
   kills the first-note alloc spike; Q3 deliberately schedules it last of the backend work.

**Step 3 — Act 2, the quarter of tutorials:**

1. **Fix-first pass + through-line** — [`tutorial-fix-and-through-line.md`](tutorial-fix-and-through-line.md)
   (MUST): audit the 38 shipped tutorials, make examples musical, build the plain-saw → crafted-sound spine. Workflow:
   Claude frames → user polishes the jingle → Claude works backwards.
2. **Mini-notation attribute-blocks tutorial** — [`mini-notation-extensions.md`](mini-notation-extensions.md)
   (feature shipped; clean first new tutorial).
3. Then the expansion backlog — [`tutorial-master-plan.md`](tutorial-master-plan.md).

**Opportunistic cheap wins** (slot in when touching the area anyway):

- Wire the CodeMirror linter stub to `AnalyzedAst.diagnostics`
  ([`klangscript-intellisense.md`](klangscript-intellisense.md)) — low effort, real UX win.
- `snd*` sound-function surface redesign ([`sprudel-sound-function-surface.md`](sprudel-sound-function-surface.md))
  — unblocked by named args.
- Voice culling ([`voice-culling.md`](voice-culling.md)) — measure via `runSongBenchmark` first.
- Form- (d) chained-mapper test sweep — ongoing, file-by-file as touched.

**Fun-sized / morale items** (when a light session is wanted): phoneme singing M1 grin-test (re-doing it — plan + code
lost, see §3), new body materials by ear, another homage song.

## 5. Where everything lives

- Ranked backlog: [`docs/tasks/_priorities.md`](_priorities.md) · Quarter plan: `docs/history/2026-Q3.md`
- Task docs: `docs/tasks/*.md` (open) · `docs/tasks/future/` (parked) · `docs/tasks-archive/` (done)
- Session memory: `~/.claude/projects/-opt-dev-peekandpoke-klang/memory/MEMORY.md`
- Strategist output: `.claude/vision/` + `docs/strategy/` + `.claude/agent-memory/music-platform-strategist/`
- Skills (context loaders): see `CLAUDE.md` — `/sprudel-dev-knowhow`, `/klangscript-knowhow`,
  `/klangaudio-knowhow`, `/klang-music-writing`, `/klang-music-recording`, …
