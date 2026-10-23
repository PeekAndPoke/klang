# docs/tasks — priorities (first pass)

> ## ⚠️ SUPERSEDED 2026-08-31 by [`_v1-scope.md`](_v1-scope.md)
>
> That file is the current, decided V1 line. This one is the draft ranking from before the
> August engine work (block-framing, unified EQ, filter unification, mini-notation tweaks,
> the MIDI playground) and no longer describes reality: several items here have shipped,
> the tutorial rows are stale (the 38 tutorials were wiped in `92f6d54f` and that workstream
> is owned by a separate session), and the Katalyst sequencing question it flags was
> answered by putting Katalyst in V1.
>
> Kept as the reasoning record. Do not plan from it.

> **Draft for review.** Two axes here, kept separate on purpose:
> - **Importance** — the release bar: **MUST** (non-negotiable) · **SHOULD** (strongly wanted, do
>   before nice-to-haves) · **NICE** (opportunistic / polish).
> - **Sequence** — *when* in Q3 (`../history/2026-Q3.md`): Act 1 finish the engine → Act 2 the quarter
>   of tutorials → a lower track. "Sound first" breaks ties.
>
> The two don't always line up — where they diverge it's flagged ⚠️ for us to resolve together.
>
> Status: 🔴 not started · 🟡 in progress · 🟢 mostly done · ⚪ blocked / parked

## Must-have bar (at a glance)

The release-defining set, regardless of when they're sequenced:

- ✅ **Master / loudness stage** (Act 1) — **DONE 2026-08-03.** The point of the whole backend rework.
- ~~**Pipeline DSL finish** (Act 1)~~: closed 2026-09-27: the Pipeline DSL retired in phase 3 step 9; an instrument's
  voice chain is `.classic()` on the Ignitor DSL.
- ~~**Katalyst DSL**~~: DONE 2026-09-20; since 2026-09-28 it is also the master (`master(Katalyst(k => ...))`).
- **Fix the current tutorial set + through-line** (Act 2) — the core of the tutorial quarter.
- **Audio backend audit** (Act 1) — every `audio_be` test was written without a mutation check, so the suite is an
  *unmeasured* net. **SHOULD** now, **MUST** before launch: this is the foundation.
- **Copyright audit** — must-have *before any commercial/dual license*, but ⚪ blocked on external
  counsel and not gating Q3.

---

## Act 1 — Finish & polish the engine

1. ✅ **DONE 2026-08-03** · **Master / loudness stage** — the point of the whole backend rework. Shipped as
   **master-in-pattern**: `master(Master(m => m.gain(2.5).limiter()))` (spelled `Master.of(MasterFx...)` until 2026-09-06)
   rides the voice stream (Ignitor/Pipeline registration playbook), crossfades on swap, works offline, and
   `Master.default()` switches it back off. Plus the reverb/delay **parameter-parity**
   fix (`d78ff3da`) — the same word now means the same thing on the orbit and master buses. Archived: KLANG-00136 (master dsl); loose ends in KLANG-00137 (master dsl followups). (2026-09-28: the Master DSL retired in phase 3 step 12; the
   master is a Katalyst at the output, `master(Katalyst(k => k.gain(2.5).limiter()))`, `master(Katalyst())` for off.)
2. ~~**MUST** · **Pipeline DSL finish**~~: CLOSED 2026-09-27: the Pipeline DSL retired in phase 3 step 9
   (every voice is an Ignitor tree); archived as KLANG-00223 (engine tuning profile). Was:
   *(successor to the archived KLANG-00122 (engine dsl))* Phase 2 wrapper feel-knobs — **Adsr `declickSeconds`/`expK`
   done (2026-07-04, as Ignitor slots)**; filter drift/cutoffOffset/driveScale + analog-drift carriers open.
   Phase 3 engine-identity profiles (`EngineDefault`/`EngineTuning`/`.tune()`, e.g. c64/nes) lean **NICE**.
3. ~~**MUST** · **Katalyst DSL**~~: ✅ DONE 2026-09-20, archived as KLANG-00236 (katalyst dsl). Author per-orbit effect chains from KlangScript, the counterpart to the Ignitor DSL.
4. ~~**SHOULD** · **Resonator swing**~~ — ❌ **WON'T IMPLEMENT** (closed 2026-08-20). Archived with the full
   decision record: KLANG-00147 (resonator swing).
   The design discussion revealed a three-axis model family (source/target/weighting), too diverse to tune by
   ear; and the original "filters feel hard" complaint re-diagnosed as mostly a **mixing** issue, not static
   banks. Taste call: *taste is also what you do not do.* Replacement work: **tune the material tables by ear**
   (the deferred "POC starting points" item in the body-resonator work).
5. ~~**SHOULD** · **Resource warehouse pool**~~: ✅ DONE 2026-09-04 as the resource warehouse, archived as KLANG-00230 (resource warehouse). Was: 🔴
   Self-balancing pool for expensive per-engine resources (~7.68 MB delay rings, cylinders); kills the
   audible first-note alloc spike (the "Der Schmetterling" stutter). Q3 schedules it **last**. (Audible
   quality — arguably a MUST; parked-last by Q3.)
6. **SHOULD** (→ MUST before launch) · **Audio backend audit** — KLANG-00224 (audio backend audit) 🔴 *(planned 2026-08-04, not started; closed 2026-09-27)*
   Every `audio_be` test was written **without a mutation check** and every file was built with a **single review
   round** — the Master DSL then needed 5 rounds, each finding defects in the previous round's fixes, and turned up 3
   toothless guards. So the green suite is an *unmeasured* net. Verify-the-net → map-the-holes → `/review-loop` →
   fix+guard, per subsystem, by hand (automation rejected with evidence: the DSP hot path is fully inlined). **Pilot
   `voices/` first, then re-decide.**
   Ledger in `docs/audio-audit/`. Multi-session; designed to survive interruption.
7. ✅ **DONE 2026-08-12** · **Unison phase pool** — banded start-phase selection + per-orbit pools kill the
   super-oscillator "fundamental lottery" (onset holes 16 % → 0, never-rings 6/120 → 0, texture untouched).
   OFF by default; per-song opt-in via one combined `.phasePool(...)` call (per-family band defaults kept by
   user by-ear decision). 7 commits (`6c95de65`…`9a0cdb37`), 10+ review rounds, ~65 findings fixed, every new
   test mutation-checked. Archived: KLANG-00144 (unison phase pool).
   Future (parked): voices-aware bands, sticky/contextual selection, persistence.

## Act 2 — The quarter of tutorials

> Q3 mandate: **fix the current set first → build a stringent through-line → then expand.**

8. **MUST** · **Fix the current tutorial set + through-line** — KLANG-00131 (tutorial fix and through line) 🔴
   The 38 shipped tutorials have ugly-sounding examples and miss the *core* of what they teach; the
   basics → crafted-sound path isn't coherent. Lean on the music-writing/recording skills + tutorial
   factory (sound first). → wants its own task doc.
9. **SHOULD** · **Mini-notation attribute-blocks tutorial (+ Phase 2 series)** — KLANG-00221 (mini notation extensions) 🟡 *(archived 2026-09-27; the attribute block was removed 2026-08-30)*
   Phase 1 (`{key=value}` engine feature) shipped, so this tutorial is a clean, high-value fit. Phase 3
   (MIDI → mini-notation recording) stays future/NICE.
10. **NICE** · **Tutorial expansion backlog** — KLANG-00086 (tutorial master plan) 🟡
   The recast "expand" backlog (euclid, struct/mask, swing, sometimes-family, orbits, pattern-math,
   off/echo/stut, FM/super-osc masterclasses, "Make a…" showcases). Rises to **SHOULD** after the
   fix-first pass lands.

## Supporting / enabling (pull in as they unblock the acts above)

11. **SHOULD** · **Intellisense: wire the linter stub** — KLANG-00064 (klangscript intellisense)
   🟡 — *cheap win*
   The CodeMirror `linterSource` is a `[]` stub; wiring it to `AnalyzedAst.diagnostics` immediately
   surfaces the already-built named-arg checker. Low effort, improves tutorial-authoring UX. (Full
   analyzer tiers + web worker are NICE.)
12. **SHOULD** · **`snd*` sound-function surface redesign** — KLANG-00101 (sprudel sound function surface) 🔴
   Real DSL debt (per-param patternable sound selection); unblocked now that named args exist.
13. **NICE** · **Sprudel editor tools backlog** — KLANG-00090 (sprudel ui tools) 🟡
    ~16 param editors still unwired; aids the tutorial quarter.
    Since 2026-09-07 the tools also resolve a named argument by position, so `room(size = 4)`
    opens the wet editor: KLANG-00231 (editor tools named arguments),
    to be folded into the rework. (Fixed 2026-09-26, `ffa490e4`.)
14. **NICE** · **Named-args docs polish** — KLANG-00130 (klangscript named args docs polish) 🔴
    Usage-styles panel, KDoc conventions + `@sample` sweep. Small remainder of a done feature.

## Lower / opportunistic (Q3 "likely, lower priority")

15. ~~**SHOULD** · **Soundfont looping bug**~~ — ✅ DONE 2026-09-03, confirmed by ear; archived
    KLANG-00171 (soundfont looping investigation).
    Three stacked defects; in the browser no soundfont had ever looped (the worklet dropped every sample's metadata).
    The only user-visible item on that list; blocks round-trip drops loop/break/continue.
17. **NICE** · **Filter-envelope curve config**: DONE 2026-09-25 (phase 3 step 5b c2: `lpfCurves`, `hpfCurves`,
    `bpfCurves`, `notchCurves`), archived as
    KLANG-00215 (filter envelope configuration).
18. **NICE** · **Constant-control fast-path** — KLANG-00104 (optimize constant control fast path) 🔴
    Optional sprudel perf; modest (~7% of query frame) after the VoiceData grouping. Measure-first.
19. **NICE** · **JS bundle §1 (KSP-registration de-bloat)** — KLANG-00106 (reduce js bundle size) 🔴
    ~1.2–1.6 MB win, no UX tradeoff; but "hard performance" is deliberately parked late-game.
20. **NICE** · **Sprudel test-coverage sweep** — KLANG-00127 (sprudel test coverage and review) 🟡 — *ongoing, user-paced*
    Form-(d) chained-mapper cases; done opportunistically as files are touched.
21. **NICE** · **Voice culling** — KLANG-00199 (voice culling) ✅ DONE 2026-09-15 (release-phase only, `cull(seconds)` / `noCull()`; no `cullAfter`)
    Sound-preserving, all-platform CPU win: terminate voices whose *real output* has decayed below −80 dB
    (ignitor-agnostic — no ADSR inference; gated by a per-voice `cullAfter` life-fraction). Reclaims wasted
    silent-tail rendering on dense `sustain=0` sections (Der Schmetterling). Complements the orbit-body move;
    measure the win via `runSongBenchmark`.
22. **Phase 3 follow-ups** (opened 2026-09-28 when the phase 3 record was archived as
    KLANG-00237 (builtin instruments)):
    - **By ear, DONE 2026-10-17:** KLANG-00318 (chain swap request during drain)
      (step 12 decision (g)): a waiting edit now cuts the drain and lands in about 0.2 s. The phase 3 end checkpoint is done (2026-10-02,
      archived (KLANG-00264)).
    - **NICE, deferred by the maintainer:** KLANG-00244 (editor voice door diagnostics)
      (step 11: warn on doors an instrument does not hear, a string in a wet slot).
    - **DONE 2026-10-03, a naming decision:** KLANG-00268 (tremolo rate naming parity): sprudel's `sync` is
      now `rate`, as on the Ignitor door; the order stays `tremolo(depth, rate, shape)`.
    - **NICE, parked:** `svf-resonator-class-collapse` (dead sweep code in the resonators' SVF classes) is DONE by
      engine tidy-up step 12 (a), 2026-10-08 (KLANG-00282 (svf resonator class collapse)).
      **Promoted to V1, high priority, 2026-10-07:**
      KLANG-00245 (pitch pipeline into the tree) (vibrato, accelerate, `penv`, `fm`
      into the tree; `_v1-scope.md` Layer 2). Steps 1 to 3b done 2026-10-09 (v0.6.1), steps 4 (`fm`) and 5 (the strip's
      shell, `voices/strip/` dissolved) 2026-10-10: left are the listening round before the branch merges, then the
      composition block (section 7).
    - Existing homes: KLANG-00165 (envelope shape followups) sections 4 to 6 (FM
      curves, a per-curve bend, the in-block corner), KLANG-00243 (one chain host),
      KLANG-00235 (onepole highpass door),
      KLANG-00214 (oversampling regions) (the D7 stopgap, crush and coarse oversampling, the
      distort cap), KLANG-00217 (stored lambda type inference).
23. **Engine pass 1 follow-ups** (opened 2026-10-09 when the engine tidy-up was archived as
    KLANG-00286 (engine tidy up), v0.6.0):
    KLANG-00290 (engine follow ups). **NICE** for the behaviour-neutral items (the V8 residues on the
    stages and oscillators, the JVM box per block-constant read, two weak specs); the audit's later shape and sound
    steps (the duck's attack, the vowel word, "cylinder" as the one word, `legato` on the wire) and its open decisions
    are the maintainer's call. The current engine order is in [`_v1-scope.md`](_v1-scope.md).

- **NICE** · **Blog series: the phone that does not get faster** (KLANG-00206 (blog optimization series)): P16's fact check, then publishing. Not urgent (maintainer, 2026-10-14, C15).

## Blocked / off-plan / parked

- **Maintainer decisions, all answered** (raised in the topic walk of 2026-10-02/04, answered 2026-10-06/07): (1) Sonnet 5.5 as a third,
  blind reviewer in round 1, as a trial; (2) `docs/blog/2026-01-20-the-position-that-survived/position-pipeline.png`,
  unreferenced since its interactive figure replaced it: keep as history or delete; (3) `console/deploy-finzo.sh`
  copies with `scp -r` and never deletes on the server, so a removed page or post stays online: add a delete step or
  accept it. Answered 2026-10-06: the triangle's name across the DSLs is `tri` (`Ign.tri`, like `supertri` and
  sprudel's `tri`; the sound and LFO names stay),
  KLANG-00273 (oscillator names across dsls);
  sprudel sounds get no `phase` slot,
  KLANG-00272 (oscillator phase knob), "Recorded
  asymmetries". Done 2026-10-07: KLANG-00278 (shared modulator memo rate), a stateful
  modulator shared by oscillators at different pitches ran at double rate (pre-existing, `duty` and `phase`); open from that batch: KLANG-00309 (bugfix non finite pitch strip and signals), and the audit leftovers §2 (worklet tests) and §4 (cut groups, by ear) in KLANG-00308 (audit audio backend leftovers).
  Answered 2026-10-07: (4) the saw's node is `IgnitorDsl.Saw` (wire name `saw`, factory `Ignitors.saw`), like its
  door `Ign.saw`; sprudel's `sndTriangle()` is `sndTri()` (it still sets the sound `triangle`); the sound names and
  the LFO shape `sawtooth` stay,
  KLANG-00273 (oscillator names across dsls),
  decisions 3 and 4. Also answered 2026-10-07: (1) no Sonnet reviewer trial; (2) `position-pipeline.png` deleted
  (source and built copy); (3) the deploy is versioned, `klang.finzo.de/versions/v<version>-<git hash>` with a `current`
  symlink, the newest 10 releases kept with their source maps (`console/deploy-finzo.sh`); (5) the repo-scanning guard
  `RetiredIgnitorNamesSpec` goes (it guarded what the compiler and the song smoke test already catch); the retired
  names moved from `CLAUDE.md` to `docs/retired-names.md`. Done 2026-10-07: KLANG-00275 (callable object docs),
  a callable object's docs show its object form and its callable form.
- **MUST (for commercialization) · ⚪ BLOCKED** — **Copyright audit** — KLANG-00222 (copyright audit 00 overview) + 
  `-07` (KLANG-00118)
  Code work done & archived; the one open item awaits external IP counsel. Gates a non-AGPL license, not Q3.
- **DONE 2026-09-07** — **Sprudel field accessors** — KLANG-00184 (sprudel field accessors) —
  every numeric field is an accessor, every compound door an object with slot children. Follow-up:
  **`snd*` sound doors as compound objects** — KLANG-00189 (sprudel sound doors compound), NICE.
- **NICE** — **Native-object operators** — KLANG-00056 (klangscript native object operators) — prerequisite for field accessors;
  readability win.
- **NICE** — **Ignitor DSL backlog** — KLANG-00219 (ignitor dsl open items) — attributes /
  splitAndJoin / convolution (convolution = long-term guitar/body realism). *(Retired 2026-09-27; see its banner.)*
- **NICE · ⚪ parked** — **IR → modal-table extraction** — KLANG-00121 (ir to modal table extraction) — real `body()` materials from
  recorded IRs.
- **NICE · ⚪ far-future** — **High-performance native/Wasm backend** — KLANG-00129 (high performance audio backend) — closing the gap to native
  DAWs; explicitly parked until *after* launch ("sound first"; hard performance is late-game).
- **NICE · ⚪ far-future** — **Federated song sharing** — KLANG-00135 (federated song sharing) — multi-server imports
  (`klang.art:user/song@v1.0`), immutable version tags + content addressing, per-server blob storage; captured
  2026-08-01, post-launch by design.

---

*Stubbed 2026-07-04: KLANG-00236 (katalyst dsl), `resource-warehouse-pool.md`, KLANG-00131 (tutorial fix and through line) (then items
3–5).*
*Archived in the 2026-07-04 cleanup: `engine-dsl` (design record), `klang-blocks-take-1`,
`wireformat-enhancements`, `sprudel-dsl-named-args`, `strudel-dsl-documentation`,
`documentation-implementation-status`, `body-vowel-to-orbit-katalyst`. Split: `klangscript-named-arguments`.*
