# V1 scope — what "the engine is done" means

**Decided 2026-08-31 with the maintainer.** This supersedes [`_priorities.md`](_priorities.md),
which was a draft ranking from before the August engine work and no longer describes reality.

## The goal

Get the engine and its authoring surfaces stable and finished, so the focus can move to the
frontend and tutorials for a while without that work being invalidated underneath it.

Three layers, worked from the ground up:

1. **Harden the engine.**
2. **Widen and harden the interface** (the DSLs and sprudel).
3. **Write tutorials for the shape of the DSLs and engine.** (Owned by a separate session.)

## The sorting rule

A task is V1 if it **changes the shape a tutorial would teach, or changes how things sound.**
Everything else can run underneath the tutorial phase without invalidating it.

That rule does the work, because a tutorial written against a surface that later gets renamed is
dead, and a tutorial ear-checked against a sound that later gets retuned is dead too. Pure
performance work and internal correctness are invisible to both, so they are explicitly NOT V1
even when they are valuable.

---

## Layer 1: harden the engine (all 10 done or closed; reviewed 2026-09-29)

| # | Task | Source | Why V1 |
|---|---|---|---|
| ~~1~~ | ~~Block-framing **W13**: musical vs absolute frequency, part 2~~ | [`plans/block-framing-invariance.md`](../plans/block-framing-invariance.md) | ✅ **DONE 2026-08-31** (`79249849`). Guards: `AbsoluteFreqPitchModSpec`, 7 rows across both doors, 4/4 mutations killed. Note: no pre-existing test exercised an absolute-freq oscillator under a pitch mod at all |
| ~~2~~ | ~~Block-framing **P4**: the strip renderers~~ | same | ✅ **DONE 2026-08-31** (`615faaf7`, `9d9612e5`). The harness now drives the strip door; vibrato + pitch envelope bit-identical, accelerate bounded (float reassociation, 1.7e-13), FilterMod/FM named Class 2. Also closed audit F3: the `EnvelopeCalc` clamp is the two control-rate renderers' offset compensation |
| ~~3~~ | ~~Block-framing **P5**: the sample path end to end~~ | same | ✅ **DONE 2026-08-31** (`b5cf4eff`). Driver C reaches the sample branch; reintroducing instance 2 turns the harness red |
| ~~4~~ | ~~Track **B1**: scheduler startup protocol~~ | same | ✅ **DONE 2026-09-03.** Not a start command: a clock convention (between renders the clock is the NEXT block). The race was exactly one block, every playback, every time |
| ~~5~~ | ~~Track **B2**: hard-drop admission + per-playback dropped-voice counter~~ | same | ✅ **DONE 2026-09-03**, same change as B1. `oldestAllowedSec` gone; late voices dropped and counted. **The scheduler now delivers the guarantee the DSP was verified against.** Counter not yet surfaced to the FE |
| ~~6~~ | ~~**Audio backend audit**~~ — `voices/` pilot **COMPLETE 2026-08-31** (21 findings: 12 fixed, 5 withdrawn, 2 parked, 3 awaiting a call); 1 of 11 katalyst files done; 5 subsystems never started | [`20260927-audio-backend-audit.md`](../tasks-archive/2026-09/20260927-audio-backend-audit.md) | ✅ **CLOSED 2026-09-27** (maintainer): superseded by the engine redesign and Standard 2 mutation checks. Leftovers: [`audit-audio-backend-leftovers.md`](audit-audio-backend-leftovers.md); parked calls: [`future/audit-parked-decisions.md`](future/audit-parked-decisions.md) |
| ~~7~~ | ~~**Resource warehouse**~~ | [`plans/resource-warehouse.md`](../tasks-archive/2026-09/20260927-resource-warehouse.md) | ✅ **DONE 2026-09-04** (steps 1–2g, cylinders in the warehouse, bucketed 16-orbit warmup, the warmup vocabulary; 5 review rounds, the last clean on code). Rings, reverb networks and cylinders are lazy, shelved by return, zeroed by deferred housekeeping; OOM caught at one site per resource. **Fairphone: the resource stutter is gone; a cold-code spike on the first run was the last symptom, answered by the vocabulary (`da002b73`); measured on the Fairphone the same day, the first run plays (why first-time work is that large stays open: [`future/first-run-spike-v2.md`](future/first-run-spike-v2.md)).** Reporting half ✅ shipped 2026-09-04 too (the warehouse stats feed, `Diagnostics.warehouse`, shown on a click on KLANGMOTOR); closed-form tail (`TailCeiling`) shipped the same day. Nothing open |
| ~~8~~ | ~~`per-playback-engine` **D4** cylinder eviction~~ | same, step 2f | ✅ **DONE 2026-09-04** as engine disposal: the end of a playback returns every unit. Idle cylinders inside a live engine stay (maintainer, settled) |
| ~~9~~ | ~~Soundfont looping bug~~ | [`soundfont-looping-investigation.md`](../tasks-archive/2026-09/20260903-soundfont-looping-investigation.md) | ✅ **DONE 2026-09-03**, confirmed by ear (`aa93eef8`, `c1b503d8`, `f9e076f5`). Three stacked defects; the third (worklet reassembly dropped every sample's metadata) meant **no soundfont had ever looped in the browser**. Left as data curation, not code: JCLive's roots are 0.4–1.4 st sharp, see `soundfont-variant-curation.md` |
| ~~9a~~ | ~~Master limiter surge after deep limiting~~ | [`20260929-bugfix-master-limiter-surge.md`](../tasks-archive/2026-09/20260929-bugfix-master-limiter-surge.md) | ✅ **CLOSED 2026-09-29, measured, not audible**: the limiter dips under every snare hit, but the maintainer heard no surge after it |

## The engine order from here (maintainer, 2026-10-07)

"Quite beefy work on the engine, but worth it": revisiting the engine surfaces subtle bugs (the 16-bit browser
output was one), so each pass is also quality control. The backend should end up as tidy as it can be, for a later
port to Zig.

1. ~~The voice lifecycle state machine~~ **done 2026-10-07 (v0.5.5)**: [`20261007-voice-lifecycle-state-machine.md`](../tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md); then [`code-style-named-args-pass.md`](code-style-named-args-pass.md) (`audio_be` and `audio_bridge` done 2026-10-07, v0.6.0).
2. The pitch pipeline into the tree: [`pitch-pipeline-into-the-tree.md`](in-progress/pitch-pipeline-into-the-tree.md) (decisions D1
   to D7 answered 2026-10-08). Steps 1, 2, 3 and 3b done 2026-10-09 (v0.6.1: `penv`, `vib` and `accelerate` are
   `classic()` stages, the fm modulator follows the pitch); steps 4 (fm) and 5 (the strip's shell) remain.
   Before its step 1: the name check over the `classic()` slots, **done 2026-10-09 (v0.6.0)**: [`20261009-classic-slot-names-check.md`](../tasks-archive/2026-10/20261009-classic-slot-names-check.md).
3. ~~The Katalyst DSL leftovers and the engine tidy-up~~ **done 2026-10-09 (v0.6.0)**: steps 1 to 13 and the V8
   allocation pass, [`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md) (audit
   2026-10-07). Its bug, an empty `variants()` crashing the audio thread, was fixed first (2026-10-07: it is silence
   now). What is still open (the V8 residues, the audit's later steps and decisions):
   [`engine-follow-ups.md`](engine-follow-ups.md), behaviour-neutral items not V1 by the sorting rule, the shape
   and sound items each a question for the maintainer first.
4. Takeover / voice stealing and the cut-group semantics: [`future/cut-group-semantics.md`](future/cut-group-semantics.md)
   decided first, then [`voice-takeover.md`](voice-takeover.md) Phase 1.
5. Pitch takeover (`glide`): [`voice-takeover.md`](voice-takeover.md) Phase 2.

## Layer 2: widen and harden the interface (7 open, 2 of them parked: 1 blocked on a design decision, 2 maintainer calls; the rest done or closed; reviewed 2026-09-29)

| # | Task | Source | Why V1 |
|---|---|---|---|
| ~~10~~ | ~~**Katalyst DSL**~~ | [`20260928-katalyst-dsl.md`](../tasks-archive/2026-09/20260928-katalyst-dsl.md) | ✅ **DONE 2026-09-20** (designed 2026-09-17, steps 1 to 5c-11: the chain on the wire, `katp` slots, `eq` and `gain`, insert-style sends, every orbit stage a state machine that glides). Since 2026-09-28 (phase 3 step 12) the master is the same chain at the output, `master(Katalyst(k => ...))`. Open items moved to their own files, listed at the top of the archived record |
| ~~11~~ | ~~**Filter unification C6** (canonical NAMES only)~~ | [`plans/filter-unification.md`](../tasks-archive/2026-09/20260927-filter-unification.md) | ✅ **DONE 2026-08-31.** The whole plan is COMPLETE (C6a → C0 → C1+C2 → C3 → C4 → C5 → C6); 50 alias names deleted. The filter vocabulary tutorials are written against is now settled |
| 12 | `snd*` sound-function surface redesign | [`sprudel-sound-function-surface.md`](sprudel-sound-function-surface.md), [`sprudel-sound-doors-compound.md`](sprudel-sound-doors-compound.md) | Real DSL debt (per-param patternable sound selection). Shape change. **Review 2026-09-07:** the general "compound colon-string vs named" question this doc asked is ANSWERED by the field-accessor rollout — every compound door is now an object with named slots (archived `20260907-sprudel-field-accessors.md`, `-compound-slots.md`). What remains is the `snd*` family itself (19 per-sound functions, the last surface not on objects): the follow-up doc; maintainer "not fully sure" → a design word, then a batch like the others |
| ~~13~~ | ~~Pipeline DSL coefficient exposure~~ | [`20260927-pipeline-dsl-coefficient-exposure.md`](../tasks-archive/2026-09/20260927-pipeline-dsl-coefficient-exposure.md) | CLOSED 2026-09-27: the Pipeline DSL retired in phase 3 step 9. Was: "widen the interface", ~35 engine coefficients with no DSL home |
| ~~14~~ | ~~Engine tuning **Part B**~~ | [`20260927-engine-tuning-profile.md`](../tasks-archive/2026-09/20260927-engine-tuning-profile.md) | CLOSED 2026-09-27 with the Pipeline DSL (phase 3 step 9). Was: the `Double`-vs-node resolution decision gates the tuning surface |
| ~~15~~ | ~~KlangScript: number methods (`2.pow(2)`)~~ | [`../tasks-archive/2026-09/20260908-klangscript-number-methods.md`](../tasks-archive/2026-09/20260908-klangscript-number-methods.md) | ✅ **DONE 2026-09-08, both halves** (`b7d540e1` parser, `adf16e46` stdlib, `04b79e19` review round 3). `^`-as-power was DECLINED: `**` already existed and nobody had noticed, so the footgun needed one documentation line, not an operator. Number methods shipped for their own sake, all three tiers. Precedence on a negative literal is being reversed to match Kotlin (2026-09-09), see the handover note at the end of the archived doc |
| ~~16~~ | ~~KlangScript statement boundaries~~ | [`../tasks-archive/2026-09/20260908-klangscript-statement-boundaries.md`](../tasks-archive/2026-09/20260908-klangscript-statement-boundaries.md) | ✅ **DONE 2026-09-08 (Phase 1), review round 1 clean (zero major, minors batched).** Two statements may no longer share a line without a `;`, so the dropped dot that silently ate the hats tag is now a parse error with a `Did you mean '.tag(...)'?` hint. Phase 2 (full newline sensitivity) deliberately not done: it is where every song can stop compiling |
| 17 | `voice-takeover` **Phase 1** (`takeover`) | [`voice-takeover.md`](voice-takeover.md) | **BLOCKED on a design decision (2026-09-08): the maintainer is not sold on the current design.** Was "additive surface, cheap, ready to build"; it is not ready until the design question below is settled. Phase 2 (`glide`) additionally blocked on #12 |
| ~~18~~ | ~~Wire the CodeMirror linter stub~~ | [`klangscript-intellisense.md`](klangscript-intellisense.md) | ✅ **DONE 2026-09-09** (step 0): the linter renders `AnalyzedAst.diagnostics` |
| 19 | Unknown-tweak diagnostic | [`future/mini-notation-tweaks-followups.md`](future/mini-notation-tweaks-followups.md) §1 | **Parked 2026-09-29** with every diagnostics item in [`future/editor-diagnostics.md`](future/editor-diagnostics.md) (maintainer: "we will revisit the whole diagnostics topics later"). A misspelled tweak is silently inert today |
| 20 | Silent shape-discard, query-time gap | [`silent-shape-discard-on-error.md`](silent-shape-discard-on-error.md) | A typo in a shape function discards the whole shape, silently. **Parked 2026-09-29, two tasks:** [`klangscript-union-types.md`](klangscript-union-types.md) first, then the diagnostic in [`future/editor-diagnostics.md`](future/editor-diagnostics.md) |
| ~~21~~ | ~~Effect scope (per-orbit vs per-voice) in the docs~~ | [`../tasks-archive/2026-09/20260908-orbit-level-effect-docs.md`](../tasks-archive/2026-09/20260908-orbit-level-effect-docs.md) | ✅ **DONE 2026-09-08 (steps 1 and 2), review round 1 applied.** Scope is metadata now: a `@scope` KDoc tag through KSP, a badge in the popup and the library page, and Lexikon pages to link concepts to instead of re-explaining them. Needed a fourth value, `orbit-send`, because a plain ORBIT badge on `room` would teach the exact misconception the task exists to kill. Step 3 (editor highlight colours) stays parked on the colour-grouping decision |

| ~~22~~ | ~~**Configure lambdas + builder types on every DSL door**~~ | [`20260906-dsl-configure-lambdas.md`](../tasks-archive/2026-09/20260906-dsl-configure-lambdas.md), [`20260906-klangscript-libs-split.md`](../tasks-archive/2026-09/20260906-klangscript-libs-split.md) | ✅ **DONE 2026-09-06.** Every sub-typed door (16 oscillators, eq/phaser/shimmer, Master, Pipeline) takes `configure: x => x.knob()` on an immutable builder; the stdlib moved to `klangscript-libs`; every song, doc and skill migrated; no backward compatibility kept. Standing rules in `/dsl-design`. |

| ~~23~~ | ~~**Sprudel arithmetic: a continuous control is evaluated once per query arc**~~ | [`../tasks-archive/2026-09/20260908-sprudel-arithmetic-continuous-controls.md`](../tasks-archive/2026-09/20260908-sprudel-arithmetic-continuous-controls.md) | ✅ **DONE 2026-09-08, review loop closed after 3 rounds (round 3 clean).** Not option 1: onset sampling reversed two song accent maps (Schmetterling `guitarClip`, Stranger Things velocity), so arithmetic now joins through a new `_appLeft` (Strudel's `appLeft`: source wholes, fragments carry the control value, point queries find the covering fragment). Exposed and fixed a second handoff bug: `segment(n)` answered every point query with the FIRST slice, so every `.seg()` control in every setter was inert within the cycle. By-ear §7 lists the songs whose written intent now plays for the first time |
| — | ~~Editor tools: named arguments resolve the wrong slot~~ | [`20260927-editor-tools-named-arguments.md`](../tasks-archive/2026-09/20260927-editor-tools-named-arguments.md) | ✅ **DONE 2026-09-26** (`ffa490e4`); the leftovers are the Open list of [`sprudel-ui-tools.md`](sprudel-ui-tools.md). **Added 2026-09-07.** Not the shape, not the sound — a UI bug — but it is what a tutorial reader touches first. Maintainer: "the editor tools get a rework of their own"; listed here so it is not lost, sorted as **maintainer call** |
| — | Editor: code completion for local symbols (`let`, `const`, `export`, lambda params) | [`editor-local-symbol-completion.md`](editor-local-symbol-completion.md) | **Added 2026-09-14.** Completion knows library symbols only; a song with twenty rig presets offers none of them. The analyzer already tracks locals (`TypeScope`, `bindingMap`, hover), missing is `localsAt(pos)` plus the editor wiring. Half-typed lines complete from the last successful analysis (already the stale-AST policy). Sorted with the editor-tools rework, **maintainer call** |
| — | The tremolo becomes a composition of the oscillators | [`tremolo-as-composition.md`](../tasks-archive/2026-10/20261002-tremolo-as-composition.md) | **DONE 2026-10-02** (built 2026-09-29, start points accepted by ear 2026-10-02). Removes `TremoloIgnitor` and its own LFO; the doors stay, `skew` and `phase` go; square, sawtooth and ramp lose their clicks (16 ms edges) |
| — | Rename the Ignitor's script object and setter to its own words | [`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md) §11 | **Added 2026-09-28.** A shape change (every instrument a tutorial teaches spells them), held back "until the slot vocabulary has settled", which it has since phase 3. **DONE 2026-10-04**: the maintainer kept the engine words with a short form each (`Ignitor` / `Ign`, `Katalyst` / `Kat`, `ignitorParam` / `ignp`, `katalystParam` / `katp`), [`../plans/ignitor-katalyst-naming.md`](../plans/ignitor-katalyst-naming.md) |
| ~~—~~ | ~~Stereo reverb: every room reaches both ears~~ | [`20260930-stereo-reverb.md`](../tasks-archive/2026-09/20260930-stereo-reverb.md) | ✅ **DONE 2026-09-30.** The reverb was two mono reverbs side by side; now each side's combs are fed `(L + R) / 2`, one room (`CROSS_FEED` 0.5, by ear). Equal-sided input unchanged bit for bit. Follow-ups: [`future/reverb-models.md`](future/reverb-models.md), [`20260930-kokon-one-room.md`](../tasks-archive/2026-09/20260930-kokon-one-room.md) (done) |
| — | **The pitch pipeline moves into the Ignitor tree** | [`pitch-pipeline-into-the-tree.md`](in-progress/pitch-pipeline-into-the-tree.md) | **Added 2026-10-07, HIGH priority (maintainer).** Sprudel's `vib`, `accelerate`, `penv` and `fm` ran as a fixed pitch strip on the `Voice`, the last stage outside the tree. **Steps 1, 2, 3 and 3b done 2026-10-09, released in v0.6.1:** `penv`, `vib` and `accelerate` are `classic()` stages, their wire fields are gone, and the fm modulator follows the pitch. Open: step 4 (fm into `classic()`), step 5 (the strip's shell), the listening round, then the composition block. The engine order above (item 2) is the current line |

**Applied as a gate, not as its own item:** [`dsl-kotlin-surface-parity.md`](dsl-kotlin-surface-parity.md).
Every surface addition above lands on **both doors** (script stdlib + Kotlin extensions) in the same
deliverable. Nothing ships one-door.

## Layer 2.5: lock the sound before tutorials (9 rows, 1 decided 2026-09-29)

All in [`by-ear/`](by-ear/). These are non-delegable and they gate the tutorial phase, because a
tutorial ear-checked against a sound that later gets retuned has to be redone.

| # | Round | Note |
|---|---|---|
| ~~22~~ | [`20260929-analog-drift-ratio-tuning.md`](../tasks-archive/2026-09/20260929-analog-drift-ratio-tuning.md) | ✅ **DECIDED 2026-09-29 by ear:** pitch drift stays, filter drift becomes 2x the pitch drift (it was a quarter). Confirmed on the built-in songs the same day: "things sound good with the 2x" |
| 23 | W10 tremolo shapes (`9cb896ff`, committed unheard) | [`by-ear/README.md`](by-ear/README.md) §1. The oldest debt, and the most new sound |
| 24 | Mini-notation tweaks verdict | [`by-ear/README.md`](by-ear/README.md) §2. Could still send the design back |
| 25 | [`c3-depth-migration-flags.md`](../tasks-archive/2026-10/20261002-c3-depth-migration-flags.md) | **DONE 2026-10-02** by ear: the lament stab fixed, the rest fine |
| 26 | Body resonator material tables | [`by-ear/README.md`](by-ear/README.md) §3 |
| 27 | Der Schmetterling re-voicing | [`by-ear/README.md`](by-ear/README.md) §4. Uncommitted; chosen before the master-opening fix landed |
| 28 | [`phase3-end-checkpoint.md`](../tasks-archive/2026-10/20261002-phase3-end-checkpoint.md) | **DONE 2026-10-02**: no separate listen; `ClassicVoiceBaselineSpec` retired, `BuiltInVoiceMatrixSpec` is today's baseline |
| 29 | [`by-ear/chain-swap-request-during-drain.md`](by-ear/chain-swap-request-during-drain.md) | Added 2026-09-28. Phase 3 step 12 decision (g): a second chain edit waits for the old chain's ring-out |
| 30 | [`by-ear/duck-orbit-switch-click.md`](by-ear/duck-orbit-switch-click.md) | Added 2026-09-28. Katalyst step 5c, measured: moving a ducker's sidechain to a sounding orbit steps the reduction. Leave, blend, or dip |

---

## Explicitly NOT V1

Not "unimportant". These fail the sorting rule, so they can run underneath the frontend and
tutorial phase instead of blocking it.

**Pure performance, invisible to the surface:** unified-eq **D9** and its ramp phase (cut
2026-08-31 with the sprudel `band`/`tap` decision), `voice-culling` (✅ DONE 2026-09-15, [archived](../tasks-archive/2026-09/20260915-voice-culling.md)),
`future/optimize-constant-control-fast-path`, `reduce-js-bundle-size`, and
`svf-coefficient-cache-never-engages.md` (opened 2026-08-31; the hottest path in the engine, but
it changes no surface and no sound, so it fails the rule).

**Additive, so a tutorial written before them does not become wrong:** `filter-envelope-configuration`
(the filter curve doors; done 2026-09-25 in phase 3 step 5b c2), `sprudel-field-accessors` and its `klangscript-native-object-operators`
prerequisite, `future/ignitor-optimizer-open-items`, `master-dsl-followups`
(except the parity audit, which is the gate above), `klangscript-named-args-docs-polish`.

**New capability, not V1 stability:** `pluck-release-tail` (blocks credible physical modelling,
which is a V2 promise).

**Frontend phase, which is where the focus goes next anyway:** `sprudel-ui-tools`,
`runtime-errors-in-the-editor` Phase 4, `realtime-analytics-meters`, `realtime-analysis-ui`,
`auto-mix-advisor`, and the whole MIDI/playback cluster (`midi-keyboard-playground` v1,
`realtime-playback-controller`, `playback-layer-decomposition` P2-P7).

**Optional taste:** unified-eq D7.

**Blocked externally:** the copyright audit (awaiting IP counsel). Gates a non-AGPL license, not V1.

---

## Estimate

> Written 2026-08-31 and kept as the record of that forecast; not recomputed since.

**25 items open, roughly 45 working days, about 6 to 7 weeks** at the pace August sustained.
(Opened at 27; W13 and filter-unification C6 both shipped on 2026-08-31, the day this was written.)

Calibrated on measured spans of comparable finished work in this repo: mini-notation tweaks 2 days,
ignitor envelope ownership 4 days, filter-unification C6a through C5 2 days for six chunks,
unified-eq D0 through D8 about 6 days, block-framing 4 days for five node classes.

**Two items carry nearly all the variance:** the audit is open-ended by construction (it exists
precisely because nobody knows what the suite misses), and Katalyst is a stub with no design, so
its sizing is the least trustworthy figure here. Size Katalyst with a design round before treating
the 7 weeks as a commitment.

The non-delegable share (decisions, review gates, by-ear verdicts, commit inspection) is roughly
**100 to 150 maintainer hours**. That is the binding constraint, not agent time.

## Open decisions

Tracked in the order they bite. See the conversation record for the reasoning.

~~1. **Katalyst design round**~~: designed 2026-09-17, built by 2026-09-20 (#10).
~~2. **Engine tuning Part B**~~: closed 2026-09-27 with the Pipeline DSL (#14).
~~3. **Track B1**~~ — done 2026-09-03 as a clock convention, no design pass needed.
4. **`snd*` as compound objects** (#12's remainder, `sprudel-sound-doors-compound.md`): a design word.
~~5. **`^` as the power operator**~~ — settled 2026-09-08, won't implement. See below. Number
   methods shipped instead and `**` turned out to have been there all along.
6. **Editor tools rework** (the named-argument slot bug): scope.
7. **`voice-takeover` design** (blocks #17, added 2026-09-08). The maintainer is not sold on the
   design as written in `voice-takeover.md`, so Phase 1 is no longer decision-free and must not be
   picked up as ready-to-build work. What the alternatives are, and which problem `takeover` is
   really meant to solve, is the open question.
8. **A general EQ core and sprudel `.eq()`** (added 2026-09-29, [`future/general-eq-core.md`](future/general-eq-core.md)):
   not planned; if it is built it removes `lpf`/`hpf`/`bpf`/`notch`, a shape change, so it lands before the
   tutorials or not in V1. Its engine half (every filter feature as an EQ section) changes no surface.

9. **The maintainer wants to talk about "the EngineDSL"** (queued 2026-09-29, after the tremolo decision). No code
   carries that name any more: the June design (`../tasks-archive/2026-06/20260630-engine-dsl-design-record.md`) became
   the Ignitor, Pipeline (retired) and Katalyst DSLs; only the funding docs still quote it.

~~C6 chunk walkthrough~~ — moot, C6 shipped 2026-08-31.

## Settled, recorded so they are not re-opened

- **`^` will NOT become the power operator** (2026-09-08). The footgun is real (`2^(7/12)` evaluated
  to `2 xor 0` in three places in Der Schmetterling and survived two versions), but re-pointing an
  operator is a language change that buys one spelling. The answer is number methods instead:
  `2.pow(7/12)`, in the extension mechanism the stdlib already uses for strings, arrays and booleans.
  [`../tasks-archive/2026-09/20260908-klangscript-caret-as-power-wont-implement.md`](../tasks-archive/2026-09/20260908-klangscript-caret-as-power-wont-implement.md) keeps the reasoning.
- **No sprudel `band`/`tap` in V1** (2026-08-31). (Re-opened as an idea 2026-09-29: open decision 8, `future/general-eq-core.md`.) C6 ships names only and is unblocked; D9 leaves
  V1 with it. Full record in `../tasks-archive/2026-09/20260927-filter-unification.md` §C6.
- **Tutorials are owned by a separate session.** `tutorial-fix-and-through-line.md` and
  `tutorial-master-plan.md` are stale (they plan work on the 38 tutorials wiped in `92f6d54f`);
  the live plan is `tutorial-curriculum.md`.
