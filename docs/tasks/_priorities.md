# docs/tasks — priorities (first pass)

> **Draft for review.** Two axes here, kept separate on purpose:
> - **Importance** — the release bar: **MUST** (non-negotiable) · **SHOULD** (strongly wanted, do
    > before nice-to-haves) · **NICE** (opportunistic / polish).
> - **Sequence** — *when* in Q3 (`../history/2026-Q3.md`): Act 1 finish the engine → Act 2 the quarter
    > of tutorials → a lower track. "Sound first" breaks ties.
>
> The two don't always line up — where they diverge it's flagged ⚠️ for us to resolve together.
>
> Status: 🔴 not started · 🟡 in progress · 🟢 mostly done · ⚪ blocked / parked

## Must-have bar (at a glance)

The release-defining set, regardless of when they're sequenced:

- **Master / loudness stage** (Act 1) — the point of the whole backend rework.
- **Pipeline DSL finish** (Act 1) — nail the engine's authoring surface.
- **Katalyst DSL** — per-orbit effect authoring, counterpart to the Ignitor/Pipeline DSLs.
  ⚠️ Q3 sequences Katalyzers in the *lower* track, but it's flagged **must-have** — it likely belongs
  up with the Act-1 engine-authoring DSLs. **Ordering to confirm.**
- **Fix the current tutorial set + through-line** (Act 2) — the core of the tutorial quarter.
- **Copyright audit** — must-have *before any commercial/dual license*, but ⚪ blocked on external
  counsel and not gating Q3.

---

## Act 1 — Finish & polish the engine

1. **MUST** · **Master / loudness stage** — [`per-playback-engine.md`](per-playback-engine.md) (§H / D6) 🟡
   Multi-engine foundation shipped in Q2; **D6** (`MasterDsl` → `Cmd.SetMaster` → `Song.master` →
   per-engine output gain) is *the* next feature. Then richer master chain (glue/eq/drive/ceiling),
   crossfade, metering. **Highest priority.**
2. **MUST** · **Pipeline DSL finish** — [`engine-tuning-profile.md`](engine-tuning-profile.md) 🟡
   *(successor to the archived `engine-dsl.md`)* Phase 2 wrapper feel-knobs — **Adsr `declickSeconds`/`expK`
   done (2026-07-04, as oscParam slots)**; filter drift/cutoffOffset/driveScale + analog-drift carriers open.
   Phase 3 engine-identity profiles (`EngineDefault`/`EngineTuning`/`.tune()`, e.g. c64/nes) lean **NICE**.
3. **MUST** · **Katalyst DSL** — [`katalyst-dsl.md`](katalyst-dsl.md) 🔴 *(effects shipped; authoring surface not)*
   Author per-orbit effect chains from KlangScript — the counterpart to the Ignitor/Pipeline DSLs.
   ⚠️ Q3 lists Katalyzers in the *lower* track; you've flagged it must-have. Reads as an Act-1
   engine-authoring item. → wants its own task doc.
4. **SHOULD** · **Resource warehouse pool** — [`resource-warehouse-pool.md`](resource-warehouse-pool.md) 🔴
   Self-balancing pool for expensive per-engine resources (~7.68 MB delay rings, cylinders); kills the
   audible first-note alloc spike (the "Der Schmetterling" stutter). Q3 schedules it **last**. (Audible
   quality — arguably a MUST; parked-last by Q3.)

## Act 2 — The quarter of tutorials

> Q3 mandate: **fix the current set first → build a stringent through-line → then expand.**

5. **MUST** · **Fix the current tutorial set + through-line** — [
   `tutorial-fix-and-through-line.md`](tutorial-fix-and-through-line.md) 🔴
   The 38 shipped tutorials have ugly-sounding examples and miss the *core* of what they teach; the
   basics → crafted-sound path isn't coherent. Lean on the music-writing/recording skills + tutorial
   factory (sound first). → wants its own task doc.
6. **SHOULD** · **Mini-notation attribute-blocks tutorial (+ Phase 2 series)** — [
   `mini-notation-extensions.md`](mini-notation-extensions.md) 🟡
   Phase 1 (`{key=value}` engine feature) shipped, so this tutorial is a clean, high-value fit. Phase 3
   (MIDI → mini-notation recording) stays future/NICE.
7. **NICE** · **Tutorial expansion backlog** — [`tutorial-master-plan.md`](tutorial-master-plan.md) 🟡
   The recast "expand" backlog (euclid, struct/mask, swing, sometimes-family, orbits, pattern-math,
   off/echo/stut, FM/super-osc masterclasses, "Make a…" showcases). Rises to **SHOULD** after the
   fix-first pass lands.

## Supporting / enabling (pull in as they unblock the acts above)

8. **SHOULD** · **Intellisense: wire the linter stub** — [`klangscript-intellisense.md`](klangscript-intellisense.md)
   🟡 — *cheap win*
   The CodeMirror `linterSource` is a `[]` stub; wiring it to `AnalyzedAst.diagnostics` immediately
   surfaces the already-built named-arg checker. Low effort, improves tutorial-authoring UX. (Full
   analyzer tiers + web worker are NICE.)
9. **SHOULD** · **`snd*` sound-function surface redesign** — [
   `sprudel-sound-function-surface.md`](sprudel-sound-function-surface.md) 🔴
   Real DSL debt (per-param patternable sound selection); unblocked now that named args exist.
10. **NICE** · **Sprudel editor tools backlog** — [`sprudel-ui-tools.md`](sprudel-ui-tools.md) 🟡
    ~16 param editors still unwired; aids the tutorial quarter.
11. **NICE** · **Named-args docs polish** — [
    `klangscript-named-args-docs-polish.md`](klangscript-named-args-docs-polish.md) 🔴
    Usage-styles panel, KDoc conventions + `@sample` sweep. Small remainder of a done feature.

## Lower / opportunistic (Q3 "likely, lower priority")

12. **SHOULD** · **Soundfont looping bug** — [`soundfont-looping-investigation.md`](soundfont-looping-investigation.md)
    🔴
    Sustained soundfont instruments loop incorrectly (correctness bug); matters if they feature in tutorials.
13. **SHOULD** · **Code-quality H3** (block-editor loop drop) — [`code-quality-review.md`](code-quality-review.md) 🟡
    The only user-visible item on that list; blocks round-trip drops loop/break/continue.
14. **NICE** · **Filter-envelope curve config** (`lpadsrCurves`) — [
    `filter-envelope-configuration.md`](filter-envelope-configuration.md) 🔴
    Engine / by-ear feature; well-scoped, not started.
15. **NICE** · **Constant-control fast-path** — [`constant-control-fast-path.md`](constant-control-fast-path.md) 🔴
    Optional sprudel perf; modest (~7% of query frame) after the VoiceData grouping. Measure-first.
16. **NICE** · **JS bundle §1 (KSP-registration de-bloat)** — [`reduce-js-bundle-size.md`](reduce-js-bundle-size.md) 🔴
    ~1.2–1.6 MB win, no UX tradeoff; but "hard performance" is deliberately parked late-game.
17. **NICE** · **Sprudel test-coverage sweep** — [
    `sprudel-test-coverage-and-review.md`](sprudel-test-coverage-and-review.md) 🟡 — *ongoing, user-paced*
    Form-(d) chained-mapper cases; done opportunistically as files are touched.
18. **NICE** · **Voice culling** — [`voice-culling.md`](voice-culling.md) 🔴
    Sound-preserving, all-platform CPU win: terminate voices whose *real output* has decayed below −80 dB
    (ignitor-agnostic — no ADSR inference; gated by a per-voice `cullAfter` life-fraction). Reclaims wasted
    silent-tail rendering on dense `sustain=0` sections (Der Schmetterling). Complements the orbit-body move;
    measure the win via `runSongBenchmark`.

## Blocked / off-plan / parked

- **MUST (for commercialization) · ⚪ BLOCKED** — **Copyright audit** — [
  `copyright-audit-00-overview.md`](copyright-audit-00-overview.md) + [
  `-07`](copyright-audit-07-control-vocabulary-legal-review.md)
  Code work done & archived; the one open item awaits external IP counsel. Gates a non-AGPL license, not Q3.
- **NICE · ⚪ blocked** — **Sprudel field accessors** — [`sprudel-field-accessors.md`](sprudel-field-accessors.md) —
  blocked on the klangscript `invoke` operator.
- **NICE** — **Native-object operators** — [
  `klangscript-native-object-operators.md`](klangscript-native-object-operators.md) — prerequisite for field accessors;
  readability win.
- **NICE** — **Ignitor DSL backlog** — [`ignitor-dsl-open-items.md`](ignitor-dsl-open-items.md) — attributes /
  splitAndJoin / convolution (convolution = long-term guitar/body realism).
- **NICE · ⚪ parked** — **IR → modal-table extraction** — [
  `future/ir-to-modal-table-extraction.md`](future/ir-to-modal-table-extraction.md) — real `body()` materials from
  recorded IRs.
- **NICE · ⚪ far-future** — **High-performance native/Wasm backend** — [
  `future/high-performance-audio-backend.md`](future/high-performance-audio-backend.md) — closing the gap to native
  DAWs; explicitly parked until *after* launch ("sound first"; hard performance is late-game).

---

*Stubbed 2026-07-04: `katalyst-dsl.md`, `resource-warehouse-pool.md`, `tutorial-fix-and-through-line.md` (items 3–5).*
*Archived in the 2026-07-04 cleanup: `engine-dsl` (design record), `klang-blocks-take-1`,
`wireformat-enhancements`, `sprudel-dsl-named-args`, `strudel-dsl-documentation`,
`documentation-implementation-status`, `body-vowel-to-orbit-katalyst`. Split: `klangscript-named-arguments`.*
