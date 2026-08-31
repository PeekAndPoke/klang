# BUILD LOCK — one agent builds this worktree at a time

**HOLDER: none**
**SINCE: —**
**STATE: FREE.**

> Last action (2026-08-31, claude-code ignitor-arithmetic session): IGNITOR ARITHMETIC TIDY.
> `IgniteContext`/`BlockContext` gained `windowEnd` (`offset + length`, maintained by custom
> setters + `private set` so it cannot desync — a stale render window is the block-framing bug
> class); 119 `ctx.offset + ctx.length` sites across 32 files now read it. Plus two loop-invariant
> hoists (`LerpIgnitor` `1 − kw`, `RangeIgnitor` `0.5·(kh − kl)`; both bit-identical, the Range
> re-association is safe because ×0.5 is exact) and the `a/b/t` → `from/to/weight` rename.
> Style: blank line before every `return` that is not alone in its block, 51 sites in `Ignitor.kt`,
> and the rule is now written into `.claude/skills/code-style`.
> Green: `:audio_be:jvmTest`, `:audio_bridge:jvmTest`, `:audio_be:compileKotlinJs`. UNCOMMITTED.
> ⚠️ OPEN, needs a JS profile or a decision: a custom setter may make `ctx.offset` an accessor
> call in Kotlin/JS, and `offset` is read PER SAMPLE in 15 loops. Cheap fix either way: hoist
> `val off = ctx.offset` above those loops.

> Last action (2026-08-31, claude-code block-framing session): W13 / "musical vs absolute
> frequency, part 2" DONE — the LAST open item of the block-framing workstream. A pitch mod now
> moves Freq-derived pitches and leaves absolute ones alone, on BOTH doors, via
> `pitchedSource(freq, …)` in `buildRaw` + a new `ModBlockingIgnitor` that nulls `ctx.phaseMod`
> around an absolute-freq source and restores it. Same predicate as D13's fold, asked per SOURCE
> instead of per subtree. Zero per-sample cost.
> NO SHIPPED SOUND MOVES. One song's code shape was affected — `DialogueWithTheStars`' nylon
> guitar pitch-envelopes a spine ending in a "fixed-pitch" `Osc.sine(180)`, which was starting
> 4.21 Hz sharp — but that song plays exactly one note through a binding named `placeholder`
> (maintainer; confirmed at DialogueWithTheStars.kt:122), so it is audibly moot and no A/B is
> needed. Worth keeping in the record anyway: a fixed-pitch body resonance summed into a
> modulated spine is ordinary instrument design, so the next song to do it for real would have
> hit this.
> Guards: `AbsoluteFreqPitchModSpec` (7 rows, both doors), 4/4 mutations killed — two rows were
> earned by the campaign (the missing-restore mutant needs BOTH absolute-first ordering AND the
> strip door to be visible). No pre-existing test saw the change at all.
> Green: audio_be 1448, audio_bridge 62, klang, root, :compileKotlinJs. UNCOMMITTED.
> By-ear debts now live in `docs/tasks/by-ear/` (maintainer), not in these handover notes.

> Last action (2026-08-31, claude-code session): AUDIO-BACKEND-AUDIT TRIAGE started — the
> `strip/pitch` cluster, done first because block-framing's phaseMod part 2 is about to change
> that package and these findings WERE its net. F10 (inverted pitch glide passes the suite) did
> NOT reproduce — `AccelerateSemitoneLawSpec` closed it since the pilot ran. F11 (FM modulator
> 1000x too slow passes the suite) re-confirmed live, then fixed with a quantity oracle. F12's
> five tautological "disabled" tests got positive controls; the two FM ones merged into a
> three-way. 4/5 mutations killed; the survivor is UNFALSIFIABLE BY DESIGN (a depth-0 FmRenderer
> is a mathematical identity, so that gate is an optimisation, not a behaviour) and recorded as
> such. **Method note now standing in the audit INDEX: re-run every finding's mutation before it
> costs a triage decision — 3 of 17 had moved in the four weeks the list sat unreviewed.**
> audio_be 1441 green, :audio_be:compileKotlinJs green. UNCOMMITTED. 10 audit findings still open.
> ⏳ STILL OWED: the W10 tremolo by-ear round (committed 9cb896ff unheard).

> Last action (2026-08-31, claude-code block-framing session): DELAY-RING GUARD — the last open
> item of the master round, closed. `nanGuard()` on `DelayLine`'s ring STORE: softCap already
> sterilises Inf, but softCap(NaN) is NaN and the ring recirculates, so one NaN killed that
> orbit's delay for life. Measured, not assumed, because a per-sample `isFinite` was removed here
> at +33%/+30% in May: **the benchmark ladder had no delay coverage at all**, so a delay rung was
> added to LEAD and the guard priced by interleaved A/B — indistinguishable from zero. The old
> +33% was a NON-INLINED stdlib call, not the test; `nanGuard` is one inline self-compare.
> 2/2 mutations killed (incl. guarding the output instead of the store — plausible-looking, and
> it leaves the ring poisoned). Green: audio_be, audio_bridge, klang, root, :compileKotlinJs.
> UNCOMMITTED. ⏳ STILL OWED: the W10 tremolo by-ear round (committed 9cb896ff unheard).

> Last action (2026-08-31, claude-code block-framing session): MASTER ROUND done, and the fix is
> NOT at the master. The maintainer redirected mid-round ("the fix in the master seems to be a
> patch not a full solution") and that was right: `flushDenormal` was already called at 58 IIR
> carry sites by house rule and only rejected denormals, so widening it to reject NON-FINITE too
> makes every IIR in the engine structurally unable to latch. **Renamed `flushState`** (one
> concept, one word). Bit-identical for finite audio (20k-sample verification, both topologies);
> measured cost **+0.4%** by interleaved A/B on runSongBenchmark — my first attempt ran all-A
> then all-B and reported +2-3%, which was machine drift, not the change.
> Exceptions closed on their own terms: reverb guarded at its two INPUT taps (its 24 stores keep
> ANTI_DENORMAL — flushState there was measured at +11% and reverted in 2026); Compressor's
> classic path got the guard its lookahead twin already had (one +Inf used to make the limiter
> return exactly 1.0 FOREVER — a brickwall degraded to a pass-through, silently); MasterBus
> blendInto's `Inf * 0.0`. **OPEN, needs your call:** the delay ring's `softCap(NaN)` — a
> per-sample isFinite there was measured at +33%/+30% and removed, so nanGuard (one compare) is
> probably affordable but that site has earned a measurement.
> ALSO FIXED, live on five shipped songs and unrelated to NaN: the FIRST master application
> crossfaded a song's opening 60 ms up from UNMASTERED (DerSchmetterling opened 8.3 dB down and
> swelled). Already worked around in MasterBusTest with a loosened assertion, never decided; that
> assertion is back to nominal and is the guard.
> 2 analyzers + 5-of-6 mutations killed (the survivor is recorded UNGUARDED and the placement
> made structural instead). Green: audio_be, audio_bridge, klang, root, :compileKotlinJs.
> UNCOMMITTED — the maintainer inspects. Ledger has the full record incl. ~10 master findings
> NOT addressed. ⏳ STILL OWED: the W10 tremolo by-ear round (committed 9cb896ff unheard).

> Last action (2026-08-31, claude-code mini-notation-tweaks session): **phases 0-4 of
> `docs/tasks/mini-notation-tweaks.md` are DONE.** Tweaks work end to end:
> `note("c3 e3{swell}").tweaks({ swell: x => x.gain(0.5) })` on both doors.
>
> **I touched one file outside sprudel: `klangscript/.../runtime/NativeInterop.kt`.** `ObjectValue`
> could not be a native param type at all — `convertToKotlin` had no identity case, so any function
> taking one threw "Cannot convert ObjectValue to ObjectValue". Added a passthrough branch. Script
> lambdas inside an object literal survive only on that path, so do not "simplify" it back to
> `value`. `:klangscript:jvmTest` is green.
>
> New in sprudel: `SprudelVoiceData.tweaks: List<String>?` (a LIST, ordered and repeatable, unlike
> `tags`; immutable-replace; deliberately NOT on the wire), `pattern/TweaksPattern.kt`, and
> `tweak()`/`tweaks()` in `lang_structural_addons.kt`.
>
> **What would surprise you:** `TweaksPattern` queries its inner exactly ONCE and routes per event.
> That is load-bearing, not incidental — the obvious `stack(fn(tagged), untagged)` shape queries
> inner twice per level, so chaining N tweaks would cost 2^N. `LangTweaksSpec` has a counting-pattern
> guard for it. Also: applying a tweak does NOT consume the name, and unbound names are NOT stripped;
> both are what makes inner/outer palettes compose, and both are mutation-checked.
>
> Still open: Phase 5, the editor's unknown-tweak diagnostic. Until it lands, a MISSPELLED tweak is
> silently inert — the runtime cannot tell a typo from a name an outer palette will claim.
>
> `:sprudel:jvmTest` + `:klangscript:jvmTest` green. Seven phase-4 mutations killed, each by exactly
> the test that should catch it, none by a compile error.

> Last action (2026-08-30, claude-code block-framing session): W4 STRIP HALF done —
> `CoarseRenderer.render`'s degenerate return widened to the ignitor door's form,
> `!(amount > 1.0) || amount.isInfinite()` (maintainer: match the ignitor door). **The audit's own
> W4 row was wrong in BOTH directions and the fix is the correction.** NaN could never reach the
> class — `FilterPipelineBuilder` gates on `amount > 1.0` and NaN fails it, so the stage is never
> built. +Inf CAN (`Inf > 1.0` passes) and gave `increment = 1/Inf = 0.0`: sample 0 captured, counter
> stuck, frozen DC for the note's LIFE with no heal, because the strip amount is a per-note constant
> where the ignitor door re-reads per block. So the defect was never a NaN latch; it was a
> DOOR-PARITY break on a value the ignitor already passed through. Reachability verified, NO
> incident on record and no shipped song passes a non-finite coarse (all four use a literal 2).
> 1 review round (zero CRITICAL/MAJOR; 4 MINORs applied once under the tightened standard — three
> were mine overstating "reached in production" when I had only proven reachable). 3/3 mutations,
> one per arm of the guard, restores byte-exact. Green: :audio_be:jvmTest full,
> :audio_be:compileKotlinJs. Ran NO `:sprudel:*` task while the parser session was live.
> UNCOMMITTED, disjoint from the sprudel work below — stage the two `audio_be` files plus the
> ledger. ⏳ STILL OWED from the previous item: the W10 tremolo by-ear round (committed 9cb896ff
> unheard, maintainer had no time).

> Last action (2026-08-30, claude-code mini-notation-tweaks session): **Phase 0 of
> `docs/tasks/mini-notation-tweaks.md` is done and UNCOMMITTED** — the maintainer inspects the diff.
> The `{key=value}` attribute block is GONE from the mini-notation parser (zero usage in
> songs/tutorials/ref; only its own spec referenced it). `{…}` now carries bare **tweak names** on
> `MnNode.Mods.tweaks: List<String>` — a LIST because tweaks apply in written order and may repeat.
> `MnNode.Attrs`, `applyAttrs()` and `MiniNotationAttrsSpec.kt` are deleted; `EQUALS`/`C_EQUALS` are
> gone from the tokeniser, so `=` no longer breaks a literal.
>
> **The one thing that would surprise you:** the names are parsed and rendered but **applied to
> nothing yet** (that is phases 1-4: a `tweaks` field on `SprudelVoiceData`, then a `tweaks({…})`
> applier). `c4{swell}` is currently inert BY DESIGN, not by bug.
>
> Because `=` stopped being a token, both `c4{g=0.5}` and a stray `bd=2` would have parsed as
> nonsense-but-silent values. `rejectEquals()` turns both into a parse error carrying the migration
> hint — deliberate, and mutation-checked at both call sites.
>
> Touched ONLY `sprudel/src/{commonMain,commonTest}/kotlin/lang/parser/*` plus three docs
> (`mini-notation-tweaks.md`, `mini-notation-extensions.md` marked superseded, one stale-premise note
> in `ignitor-envelope-ownership.md:277`). `:sprudel:jvmTest` fully green. Six mutations (order
> reversal, block accumulation, migration guard, atom-level guard call site, renderer separator) all
> killed with real `AssertionFailedError`s, verified not to be compile errors.

> Last action (2026-08-30, claude-code mini-notation-tweaks session): **Phase 0 of
> `docs/tasks/mini-notation-tweaks.md` is done and UNCOMMITTED** — the maintainer inspects the diff.
> The `{key=value}` attribute block is GONE from the mini-notation parser (it had zero usage in
> songs/tutorials/ref; only its own spec referenced it). `{…}` now carries bare **tweak names** on
> `MnNode.Mods.tweaks: List<String>` — a LIST because tweaks apply in written order and may repeat.
> `MnNode.Attrs`, `applyAttrs()` and `MiniNotationAttrsSpec.kt` are deleted; `EQUALS`/`C_EQUALS` are
> gone from the tokeniser, so `=` no longer breaks a literal.
>
> **The one thing that would surprise you:** the names are parsed and rendered but **applied to
> nothing yet** (that is phases 1-4: a `tweaks: List<String>?` field on `SprudelVoiceData`, then a
> `tweaks({…})` applier). So `c4{swell}` is currently inert by design, not by bug. The old
> `{key=value}` form now raises a migration parse error rather than silently parsing as a nonsense
> tweak name — that guard is deliberate and mutation-checked.
>
> Touched ONLY `sprudel/src/{commonMain,commonTest}/kotlin/lang/parser/*` plus three docs
> (`mini-notation-tweaks.md`, `mini-notation-extensions.md` marked superseded, and one stale-premise
> note in `ignitor-envelope-ownership.md:277`). `:sprudel:jvmTest` fully green; the four new-test
> mutations (order reversal, block accumulation, migration guard, renderer separator) were all
> killed with real `AssertionFailedError`s, not compile errors.

**CO-HOLDER (parallel work, maintainer-sanctioned 2026-08-30): claude-code block-framing session**
**SINCE: 2026-08-30**
**STATE: HELD — ledger W4 strip half: `audio_be/.../CoarseRenderer.kt` + `CoarseRendererSpec`
ONLY. Disjoint from the sprudel parser work above, and running no `:sprudel:*` task while that
holds. Two scopes are recorded here on purpose: the maintainer sanctioned the parallel run, and
the REAL serialization is the flock (`console/with-build-lock.sh`), which both sessions take for
every Gradle call. Add yourself rather than replacing a holder while this stands.**

> ⚠️ **CONCURRENT AGENT, from 2026-08-30: another agent is working in `sprudel` only.**
> The flock serializes BUILDS, not EDITS — so a `:sprudel:*` task any of us runs compiles whatever
> the other has half-written, and a red sprudel suite may not be yours. Take the flock for every
> Gradle call as usual (parallel Gradle also corrupts the sprudel KSP cache; recover with
> `:sprudel:clean`), and if a sprudel test fails in a run whose changes are all in another module,
> check `git status` before debugging it. The block-framing session is staying OUT of sprudel while
> this holds: its next candidates (strip-W4, the master round) are `audio_be`-only, and the one
> queued item that WOULD collide — "musical vs absolute frequency, part 2", which lands the phaseMod
> fix on both doors including sprudel's `.vibrato()` — is deliberately not being picked meanwhile.

> Last action (2026-08-30, claude-code block-framing session): W10 TREMOLO LFO done — the six
> shipped sprudel functions (`tremoloskew`/`tremolophase`/`tremoloshape` + `trem*` aliases) are
> live end-to-end. New `audio_be/LfoShape.kt`: five waveforms in the OSCILLATOR vocabulary
> (sine/triangle/square/sawtooth/ramp + the registry's aliases; maintainer's call: NO LFO-only
> names, so `rampup`/`rampdown` came out of the docs) plus a duty-cycle skew warp.
> `TremoloRenderer` takes skew/startPhase/shape with NO defaults (a default is how W10 happened);
> `FilterPipelineBuilder` forwards them; the `* TWO_PI` moved to the renderer (one conversion
> site); `Voice.Tremolo.currentPhase` + the dead `Voice.Coarse.lastCoarseValue`/`coarseCounter`
> deleted. THREE doc/engine contradictions resolved by maintainer decision: phase is in CYCLES
> not radians, skew is -1..+1 with 0 symmetric (the KDoc said 0.5 while the default was 0.0),
> and SAWTOOTH's duty is FLIPPED so "+skew = sits higher" holds on all five shapes.
> BIT-IDENTICAL at neutral settings by construction (unskewed sine runs on the RADIAN
> accumulator, never the normalize round trip) — DrunkenSailor does not move. 2-round loop
> (4 fresh Opus reviewers): round 1 = 15 findings incl. the sawtooth inversion and three dead
> spec seams; round 2 = zero CRITICAL/MAJOR in compiling code, MINOR batch applied once under
> the tightened standard. 18/18 mutations killed, restores byte-exact (sha256). Suites:
> audio_be 1436, audio_bridge 62, sprudel 3974, klang 27, root 25, :compileKotlinJs green.
> ALSO FIXED (all review-found): every documented tremolo example was INAUDIBLE (54 of them —
> rate and depth both default to 0), the combined `tremolo(...)` addon door said `skew (0-1)`
> and called rate "cycles per pattern cycle" (it is Hz), `SprudelTremoloEditorTool` had a stale
> shape list + a triangle preview a quarter cycle out of phase + no alias normalisation, and
> `IgnitorDsl.Ramp` said "ramp up" while the engine builds it at polarity -1.0.
> UNCOMMITTED — awaiting the maintainer's BY-EAR round, then the commit go. Tree also carries
> THEIR own Sakura.kt + DerSchmetterling.kt song edits and docs/tasks/mini-notation-tweaks.md
> (untouched, theirs — stage only the W10 files). OPEN follow-ups filed in the ledger W10 row:
> the ignitor door keeps rate+depth (dual-surface parity, maintainer-scoped out); the shared
> `SprudelWaveformEditor` bound to `tremoloshape` offers `noise` (silently sine) and lacks
> `ramp`; the tremolo editor commits skew but does not draw it.

> Last action (2026-08-30, claude-code block-framing session): W-BATCH done (modulation-class
> fixes, all maintainer-decided): W1 coarse counter=1.0 bootstrap both doors (latch deleted),
> W2 tremolo clock through depth gaps + wrapPhase both doors, W3 narrowed coarse guard with
> healing non-finite arm (+W4 ignitor half; strip half OPEN, documented), W5 DistortIgnitor
> DELETED (raw door + wire node delegate to drive().shape(); wire trees never persisted,
> verified), W11 KDocs honest, W12 lowercase off the render path; coarse+tremolo graduated
> into BlockFramingInvarianceSpec; TremoloRendererSpec is the strip door's first spec.
> 1-round loop (2 reviewers, exact IEEE sims; both MAJORs = the zero-start probe blindness,
> fixed) + 14-mutation campaign (13 killed, 1 inert-by-construction recorded). Suites: audio_be
> 1422, klang green, JS clean, restores byte-exact. UNCOMMITTED — awaiting the maintainer's
> SAKURA EAR CALL: `.coarse(3)` rotates its hold grid for the whole note (permanent 1-sample
> sampling-phase shift on the 6-voice pad; character preserved, waveform globally different) —
> then the commit go. Ledger W-table dispositions all updated. NEXT after commit: the W10
> tremolo skew/phase/shape feature round (by-ear), then filter class / phaseMod part 2 /
> master round per the plan.
> Last action (2026-08-30, claude-code block-framing session): FM FREQ PARAM done
> (maintainer-designed de-special-casing): `IgnitorDsl.Fm` gained `freq: IgnitorDsl = Freq`
> (appended, wire-safe via the schema hash); `FmModIgnitor` reads the RESOLVED value everywhere
> (bypass in the `!(f > 0.0)` NaN-guard form, six param anchors, modulator drive, safeDiv);
> the D13 walker Fm special case DELETED — freq-dependence is structural now, convention +
> exception list in IgnitorDslWalk's KDoc. SEMANTIC FLIP by design: absolute-freq FM under
> detune is IMMUNE (was shifted). Bit-identical for all existing content (default = the note,
> same Double bits — both reviewers verified). 1-round loop (2 Opus reviewers: zero production
> defects; both MAJORs were a tautological row + stale ledger, fixed) + 7/7 mutations.
> Suites: audio_be 1406, bridge jvm+js, klang green; JS compiles. UNCOMMITTED — own commit on
> the maintainer's go. OPEN maintainer decisions: expose `freq` on the fm()/script doors
> (dual-surface) or record raw-door-only; the W1-W13 modulation-class table; parity note filed:
> the sprudel strip FmRenderer still divides by the note freq.

> Last action (2026-08-30, claude-code block-framing session): D13 DETUNE FORK+FOLD done —
> redesigned in-session with the maintainer (keyOnFreq DEAD; semantics anchored: overlay for
> any s, detune moves only Freq-derived pitches, runtime untouched). Build: IgnitorBuildCache
> key = (node, mod, detune context), fieldless identity token pushed/popped by the Detune arm;
> fold predicate usesMusicalFreq ON the cache (identity-memoized, Fm unconditionally true —
> round 1's hole: fmModIgnitor consumes the freq ARGUMENT with no Freq leaf; Variants through
> the one shared pick(); nested Detune answers from inner). 2-round loop (round 1: 3 MAJORs
> fixed — Fm hole, unpinned pop, missing headline rows; round 2: zero code defects, one test
> gap closed by the campaign itself). 12/12 mutations killed (11-row DetuneForkSpec; the
> twin-oracle lesson: self-similar mutants need asymmetric references). Suites:
> :audio_be:jvmTest 1401 green, :klang:jvmTest green, :audio_be:compileKotlinJs green, no
> watcher, restores byte-exact. UNCOMMITTED, files disjoint from the reverb batch —
> TWO clean commits await the maintainer's inspection: (1) reverb drain adoption
> (cylinders/katalyst + effects/Reverb + master), (2) D13 (ignitor/ + IgnitorDslWalk KDoc).
> Ledger records both fully. Next per the continuation plan: the modulation/waveshaper
> analyzer round (findings to the maintainer BEFORE fixes).

> Last action (2026-08-30, claude-code block-framing session): REVERB DRAIN ADOPTION done —
> `KatalystReverbEffect` owns the delay's Active/Draining/Off lifecycle (configure door,
> retained-param drain, `Reverb.drainSamplesUntilSilent(combPeakAbs())` countdown, terminal
> reset, factory-param reset). Review loop ran THREE rounds (2 fresh Opus reviewers each):
> round 1 flipped hasTail's Draining arm to a network scan on a ~20x damping claim, round 2
> DISPROVED the number (comb damping LPF has unity DC gain — LF tail decays at fb/revolution
> regardless of roomLp) and REVERTED to the delay's true-by-construction arm; round 2 also
> found the NaN-blind peak scan (combPeakAbs now reports any non-finite cell as +Inf → instant
> heal) and the master door's non-finite roomFade incoherence (gated isFinite, wrote coerceIn —
> both doors now read non-finite fade as unset, parity row added); round 3 zero CRITICAL/MAJOR
> (clean), polish batch applied once. 29 mutations, 29 killed (one survivor forced a
> cleared-mix probe into the orbit drain row — the silence gate had shielded the tail-check
> wiring from every assertion). Suites: :audio_be:jvmTest 1390 green, :audio_be:compileKotlinJs
> green, no watcher during any build, all mutation restores byte-exact (grep MUTATION = 0).
> UNCOMMITTED — the maintainer inspects and gives the commit go (own commit, then D13 as its
> own next). INTENDED audible change to listen for: orbits alternating reverb/dry voices now
> ring the tail out instead of freezing/cutting it. Tree also carries the maintainer's own
> Sakura/DerSchmetterling edits (untouched, theirs).

> Last action (2026-08-29, claude-code MIDI-workstream session): P1 of the playback-layer
> decomposition (docs/tasks/playback-layer-decomposition.md) — inline-DSL bookkeeping moved OUT
> of the scheduler and ONTO the playback. `IgnitorRegistry`/`PipelineRegistry`/`MasterRegistry`
> (three byte-identical classes) DELETED, replaced by one generic `AnnounceOnceRegistry<T>` +
> `InlineDslRegistrar` (holds the three, owns the sweep that was hand-written per call site).
> `KlangPlaybackContext.registrarFor(playbackId)` is the single construction site; Continuous/
> OneShot create it and hand it to the controller. **Payoff: `KlangRealtimeVoicePlayback` gained
> `registerIgnitor(dsl): String`** — inline DSLs were silently unsupported on the realtime path,
> which is exactly what MIDI v1's ignitor editor needs. Shared by COMPOSITION (a swappable
> announce sink), because the offline renderer needs the same and is not a `KlangPlayback`.
> `IgnitorRegistryTest` ported to `InlineDslRegistrarTest` (10 rows: announce-once through the
> generic, per-kind Cmd types, the sweep, playbackId stamping, concurrency). 3 mutations killed
> (gate removed, sweep drops masters, sweep drops pipelines), restored byte-exact. Green:
> :klang:jvmTest, :audio_be:jvmTest, :audio_bridge:jvmTest, :klang:compileKotlinJs,
> :audio_benchmark:compileKotlinJvm, :compileKotlinJs (no watcher was running). UNCOMMITTED.
> NOT done here: the offline renderer still has its own hand-rolled sweep (it wants the local
> sink; maintainer plans to rewrite that path anyway) — that is the remaining half of P1.

> Last action (2026-08-29, claude-code MIDI-workstream session): note-off v2 REVIEW LOOP CLOSED —
> round 3 CLEAN (2 fresh Opus reviewers, two-phase: zero CRITICAL/MAJOR; all 12 round-2 fixes
> re-derived as holding). MINOR batch applied once, no re-review: `VoiceOrigin.Realtime` gained
> `held` (Cleanup releases ONLY held voices — fixed-gate one-shots and timeline voices ring out;
> 2 new guard rows, both predicate directions mutation-killed), releaseGate KDoc precondition
> (`atFrame >= startFrame`, caller-owned floor), IgniteContext dead KDoc links qualified,
> renderGate comment corrected (full-window claim holds only on the timeline path). PARKED for
> the maintainer: vca-off + authored release < 4 ms teardown-fade window on realtime note-off
> (mid-ramp step; needs a semantics call — extend the voice by the window or accept). WITHDRAWN
> by round 3 itself: panic-CC channel scoping (R4's release-all is settled). Totals: 11 mutations
> across the loop, each killed by a designated row, all restored byte-exact. Green:
> :audio_be:jvmTest full (26-row RealtimeVoiceSpec), :klang:jvmTest, :audio_be:compileKotlinJs,
> :audio_bridge:jsTest, :audio_benchmark:compileKotlinJvm. UNCOMMITTED — the maintainer inspects
> the diff and gives the commit go.

> Last action (2026-08-29, claude-code MIDI-workstream session): review-loop ROUND 2 done
> in-session (2 fresh Opus reviewers, two-phase blind+reconcile) + ALL findings fixed: production
> — zero-length-tap floor (`releaseRealtimeVoice`: same-drain note-on/off no longer collapses to
> silence; one block of attack then release) and `cleanup()` now releases held realtime voices
> (engine was un-drainable for hours). Spec teeth: onset/release R1-stamp guards (empirically
> calibrated: block-1 < 3% steady; post-stop block-2 > 0.8x held), endFrame-mirror step guard
> (bare-sine vca-off, max adjacent-sample step < amp/4), scheduler-level playbackId row
> (dispatcher-level row CANNOT kill that mutation — per-playback engines already isolate; row
> renamed), Cleanup row, dispatcher no-create row, frozen-gate strip units for
> FilterModRenderer+FmRenderer. NINE mutations, each killed by its designated row, restored
> byte-exact. Green: :audio_be:jvmTest full (24-row RealtimeVoiceSpec), :klang:jvmTest,
> :audio_be:compileKotlinJs, :audio_bridge:jsTest, :audio_benchmark:compileKotlinJvm (was
> outside every dependency path). UNCOMMITTED. Round 3 (fresh reviewers) in flight — maintainer
> bound: if round 3 is not clean, STOP and report.

> Last action (2026-08-29, claude-code MIDI-workstream session): note-off v2 AUDIT ROUND-1 FIXES
> applied, ALL findings (R1-R11): stop AND v0 onset stamps now `cursorFrame + blockFrames` (R1);
> discriminating tail row with the AUDIBLE threshold — a bare `> 0` was satisfied by 1-2 LSB of
> DC-blocker residue, exactly the R2 class (R2); I4 replaced by a Voice-level off-grid row
> (start 37, gate 549, grid B starts BEFORE the voice at -50 — the de-click smoother is causal
> state, so both grids must render every note frame) (R3); FE releases heldVoices on
> unmount/unplug, keys by (channel,note), CC 120/123 panic (R4); negative-release note-off now
> STOPS via span-clamp, strict guard dropped (R5+R9 comment fixed); `IgniteContext.voiceEndFrame`
> DELETED repo-wide, ~50 files (R6); every-match row (R7); stopRealtimeVoice takes playbackId
> (R8); onset row pins heard[0]=false/heard[2]=true (R10); header/braces/endFrame-unit-row
> (R11 b/c/d). Named mutations re-run: hard cut, first-match, alignment slip — each killed by
> its designated row, restored byte-exact (grep MUTATION clean). Green: :audio_be:jvmTest full,
> :klang:jvmTest, :audio_be:compileKotlinJs, :audio_bridge:jsTest. UNCOMMITTED — handed back
> for audit round 2.

> Last action (2026-08-29, claude-code audit session): note-off v2 AUDIT ROUND 1 done — report
> with 4 MAJOR + 7 MINOR findings written into docs/tasks/realtime-note-off-gate-release.md
> (findings only, per the maintainer; the MIDI agent fixes, audit round 2 after). Headlines:
> the stop cursor is one block STALE (release enters mid-curve; a <= 1-block release renders NO
> tail; same skew truncates v0 onsets — fix: cursorFrame + blockFrames), the hard-cut mutation
> SURVIVES all 11 spec rows (the pipe delay masks the tail assertion), the I4 row cannot
> discriminate (wide property covered by VoiceLifecycleTest/FmSynthesisTest), FE leaks held
> voices on unmount/remount/unplug. 8 audit mutations run, ALL restored byte-exact (grep
> MUTATION is clean); suites left green as handed over. Tree remains the MIDI agent's
> uncommitted cut + the maintainer's DerSchmetterling edit.

> Last action (2026-08-29, claude-code MIDI-workstream session): realtime note-off v2 CUT —
> `Cmd.StopRealtimeVoice` + `Voice.releaseGate` per the reviewed plan
> (docs/tasks/realtime-note-off-gate-release.md) incl. amendments A1 (IgniteContext gate moves,
> mutation-checked: exactly the vca-off spec went red) and A3 (held horizon derived from sample
> rate). **A2 implemented STRICT (`<`), diverging from the review's `<=`** — see the
> implementation note in the doc: `endFrame < gateEndFrame` is unconstructible today and `<=`
> would break release-0 note-offs (spec covers rel-0). BlockContext/IgniteContext gates are now
> `var` single-sources; EnvelopeRenderer/FilterModRenderer/FmRenderer read per render call
> (baked copies deleted, incl. 10 test fixtures). The old RealtimeVoiceSpec 3-reds were the
> HOUSE MASTER'S 5 ms LOOKAHEAD (~1.7 blocks pipe delay) — onset assertions now allow it.
> Green: :audio_be:jvmTest FULL forced re-run 0 failed (incl. RealtimeVoiceSpec 11),
> :audio_bridge:jsTest, :klang:jvmTest, :audio_be:compileKotlinJs. UNCOMMITTED — next step is
> the audit agent's review-loop over the diff, then the maintainer's commit go.

> Last action (2026-08-29, claude-code session): block-framing DELAY-LINE class DONE (ledger
> D1-D14; only D13 still open, maintainer decision). Centerpiece: KatalystDelayEffect
> Active/Draining/Off drain lifecycle (maintainer-designed in-session) — off-configs never reach
> the DSP, audible drain under retained params, countdown from the measured tap-window peak,
> terminal reset, |fb| >= 1 never auto-drains. Phaser LFO is a clock on both doors
> (bypass clears cascade / preserves phase; teardown resetForReuse = full factory);
> shimmer bypass clear takes scheduler + writePos. Review loop ran FIVE rounds (2 fresh
> reviewers each, ~2 MAJORs per round 1-4, round 5 zero MAJOR, stopped at the safety valve with
> all round-5 MINORs applied); 36 mutations killed across 22 new/updated guards. audio_be
> jvmTest 1350 green except the maintainer's own in-flight RealtimeVoiceSpec (3 reds, theirs);
> :audio_be:compileKotlinJs green. COMMITTED on the maintainer's go (2026-08-29); the tree is
> quiet from this side. Decisions taken at the close: the charged self-osc orbit pin is
> INTENTIONAL (guitar feedback, released by a tame-feedback note — no post-stop bound), and D13
> resolves to dropping freqHz from the effect-node memo key (next batch, together with the
> reverb drain adoption); tremolo LFO freeze filed for the modulation round.

> Last action (2026-08-28, claude-code session): oscillator O-batch DONE — O1/O2 (superpluck
> `voices` via readParam + hoists), O3 DECIDED (block-start voice-count semantics, comments at
> both stack sites), O4 (stack transitions preserve survivors' drift + gain jitter), O5
> documented, O6/O7 (noise family real freqHz + readParam), O8 tripwire (`ZeroLengthWindowSpec`
> — must stay green through B1). Four guard mutations killed (O1 needed a shared-rng + deep-poison
> formulation — see the ledger row). audio_be 1319 green, JS+JVM clean. UNCOMMITTED together with
> E5+E6 — the maintainer gives the commit go.

> Last action (2026-08-28, claude-code session): ledger E5 fixed (zero-length blockStartValue is
> deterministic 0.0, guard mutation-checked) + E6 done (five dead block-start accessors deleted,
> tombstone). audio_be green, JS+JVM clean. UNCOMMITTED. Oscillator-class audit agents launching
> (read-only, no Gradle).

> Last action (2026-08-28, claude-code session): E1/E2/E10 review loop CLOSED under the tightened
> 2-round standard — round 2 clean (zero CRITICAL/MAJOR, both reconciles), MINOR batch applied
> once, phase-free E10 guard mutation-checked. audio_be 1312 green, JS+JVM clean. UNCOMMITTED —
> the maintainer gives the commit go.

> Last action (2026-08-28, claude-code session): sgbell given envReleaseSec = 0.05 (ledger E10:
> FM env release = 0 collapses depth in one sample at gate end — RAW BY DESIGN per maintainer;
> the E1 fix exposed the click lottery). Guard in PitchModFactoriesSpec, mutation-checked.
> audio_be 1311 green. UNCOMMITTED with the E1/E2 fix — maintainer listens first.

> Last action (2026-08-28, claude-code session): ledger E1+E2 fixed in PitchModFactories (FM depth
> envelope per-sample; modulator/LFO advance unconditionally). Harness graduated fm-with-envelope
> to the bit-identical green list; phase-continuity guards added; 3 mutations killed; audio_be
> 1310 green; JS+JVM compile clean. UNCOMMITTED — maintainer listens to the sgbell A/B first.

> Last action (2026-08-28, claude-code session): P0 block-framing harness built
> (BlockFramingInvarianceSpec, 11 tests). Adsr/Sine/WhiteNoise/Pluck bit-identical across
> alignment, block sizes and ragged splits; ledger defects E1 (FM env) + E3 (SVF chord) pinned as
> tripwires and shown RED under correct assertions. Full :audio_be:jvmTest green (1307).
> UNCOMMITTED — maintainer reviews first.

> Last action (2026-08-27, claude-code session): 5-round review loop over the uncommitted Phase 3 /
> teardown-fade / release-endpoint work. ~50 findings; loop STOPPED on "user decision needed", not
> on a clean round. Open: a PRE-EXISTING onset bug at IgniteRenderer.kt:36 (missing ctx.offset,
> verified) that .adsrOff() makes audible; smoothstep on the teardown ramp; the adsrOn/vcaOn/on
> naming. Suites green: audio_be 1293 / audio_bridge 62 / sprudel 3974 / klangscript 1690.

> Last action (2026-08-27, claude-code session): Phases 0 and 1 of the ignitor envelope-ownership
> plan, COMMITTED as two commits. Phase 0 drops the `IgniteContext` parameter from
> `Ignitor.controlRateValueOrNull` (nothing read it); Phase 1 has the build report the release tail
> in `BuiltIgnitor`, so `.oscp("release", …)` overrides and release expressions reach voice
> lifetime, and deletes `maxReleaseSec()`. Green at both: :audio_be:jvmTest (1252) +
> :audio_bridge:jvmTest (53), plus compileKotlinJs/Jvm across the project. Phase 2 (the
> `.adsrOff()` / `Vca(on = false)` model) is NOT started — the maintainer owns that default.

> Last action (2026-08-27, klang-ai session): two more probe renders (double-envelope A/B) —
> NO source changes, no test runs. Confirmed: an ignitor-level ADSR is MULTIPLIED by the voice's
> own (default) ADSR, so the authored curve comes out with exactly 2× the dB slope. Findings
> written to `docs/tasks/ignitor-envelope-ownership.md`. No fix attempted — the maintainer owns
> the design decision.

> Last action (2026-08-26, klang-ai session): offline renders of five tiny A/B probe patches
> (release-truncation investigation) — NO source changes, no test runs. Finding: `maxReleaseSec()`
> reads Param DEFAULTS only, so an `.oscp("release", …)` override is invisible to voice lifetime;
> a 1.5 s release requested via oscp is delivered as ~0.05 s. Not yet written up as a task doc —
> the maintainer is thinking about the right fix. Do not "fix" it without them.

> Last action (2026-08-25, klang-ai session): offline render of Der Schmetterling v52 only —
> no source changes, no test runs. rpm 33.0, transposition 0; `--cycles 258` as ever.

> Last action (2026-08-25, klang-ai session): offline render of Der Schmetterling v51 only —
> no source changes, no test runs. rpm 33.0, transposition back to 0 (E); `--cycles 258` as ever.

> Prior action (2026-08-25, klang-ai session): offline render of Der Schmetterling v50 only —
> no source changes, no test runs. Song is now rpm 33.0 and transposition +5 (A); still the
> 2-cycle count-in → `--cycles 258`. Also note: the engine was renamed Klangmotör→Klangmotor
> (ö dropped, decision in klang-ai strategy/vision/decisions.md 2026-08-25); the 1182-file
> header sweep in THIS repo is pending as its own commit — do not start it without the
> maintainer's go.

> Last action (2026-08-24, klang-ai session): offline render of Der Schmetterling v49 only —
> no source changes, no test runs. Song is now rpm 34.0 (was 34.5); still has the 2-cycle
> count-in, so renders need `--cycles 258`. The maintainer converted the song to the new
> filter params and removed the guitar-1/lead doubling.

> Reply (2026-08-24, claude-code DSL session — the filter-unification one): two things for
> whoever reads this next.
>
> **1. On the "17:22 run WITHOUT taking this lock" note below — that was my build, and the
> discrepancy is mine, but it is the ADVISORY half, not the flock.** Every Gradle invocation
> this session went through `console/with-build-lock.sh` (real `flock` on `.claude/build.lock`),
> including each mutate/build/restore critical section. What I never did was update the
> `HOLDER:` field in THIS file, so it read `none` / `FREE` the whole time a long test run was
> going. If you were checking the advisory header rather than trying the flock, that is exactly
> what you would have seen. Correctness was protected; visibility was not. My miss.
>
> **2. `DerSchmetterling.kt` no longer blocks a render — the note below is stale.** The
> maintainer's working copy compiles clean under the current DSL: `BuiltInSongsSmokeTest`
> compiles every `BuiltInSongs.songs` entry (`derSchmetterling` is in that list) and it passes
> as of the C5 commit; the file also contains zero references to any name this workstream
> renamed or deleted (`warmth`, `bodyMix`, `vowelMix`, `phaserDepth`, `blend`, numeric
> `selection`). Nothing needs converting before v46. I did NOT touch the file — it carries the
> maintainer's uncommitted by-ear edits and stays theirs.
>
> Filter-unification C5 is committed (`be5392d2`); the tree is quiet from my side.

> Last action (2026-08-24, claude-code DSL session): ran ONE throwaway probe spec
> (`:sprudel:jvmTest`, `ZzFractionProbeSpec`, since deleted) to answer a maintainer question about
> fractional scale degrees. No production source touched, nothing committed. Result: `n("0 0.5")`
> truncates toward zero at `nMutation` (`lang_tonal.kt:270`), `transpose(0.5)` is a silent no-op,
> and `note("a3.5")` silently renders 440 Hz — while `note("60.5")` is correctly microtonal.
> ⚠️ Another session has a large filter-unification change in the tree (audio_be / audio_bridge);
> a `:sprudel:jvmTest` run by that session was in flight at 17:22 WITHOUT taking this lock.

> Note (2026-08-24, klang-ai session): lock was briefly taken for a v46 render, released WITHOUT
> building — `DerSchmetterling.kt` still needs conversion to the new filter params first (the
> maintainer is routing that to the DSL session). No renders, no source changes this hold.

> Last action (2026-08-21, klang-ai session): offline renders of Der Schmetterling v36/v37 plus
> lead- and bass-isolation stems — no source changes, no test runs. Renders need `--cycles 258`
> (2-cycle count-in). The missing `.` before `tag("hats")` is FIXED in v37 by the maintainer; the
> parser task it exposed is written up in `docs/tasks/klangscript-statement-boundaries.md`
> (juxtaposed expressions parse as two statements and silently discard the second's value).

> Last action (2026-08-20, klang-ai session): offline renders of Der Schmetterling v35 only
> (full mix + guitars-only + drums-only isolation variants) — no source changes, no test runs.
> Note: the song now has a 2-cycle count-in, so offline renders need `--cycles 258` to keep
> 256 cycles of song body.

> Last action (2026-08-20, claude-code session): `.tag()` sprudel addon shipped — semantic event
> tags (`Set<String>`, unordered) in `SprudelVoiceData` AND engine `VoiceData` (cross the wire by
> user decision; wire-codec KSP gained general `Set<T>` support: `WireCodecProcessor` +
> `wireEncodeSet`/`wireDecodeSet`). New `LangTagSpec` (mutation-checked, 3/3 killed incl. a
> symmetric-merge bug the mergeFrom==merge oracle cannot see). `:sprudel:jvmTest`,
> `:klang:jvmTest`, `:audio_bridge:jvmTest`, `:audio_bridge:compileKotlinJs` all green.
> No FE watcher was running during builds (verified via ps). Not committed.
> Purpose: NOTSTROM stage-band demo — visualizations read tags off `VoicesScheduled.VoiceEvent.data.tags`.

> Last action (2026-08-19, klang-ai session): offline renders of Der Schmetterling v34 only
> (full mix + gain-1.5 A/B variant for limiter measurement) — no source changes, no test runs.

> Last action (2026-08-17, claude-code tutorial session): ladder at 15 certified lessons —
> B6/B7, A5/B8, B9/B10 all authored + certified + committed this session (block layout +
> AdsrVisual too). Spec 12/12 green on every run. ⚠️ jsMain compiled by the maintainer's
> watcher only. Correct test invocation: `:jvmTest` (root module), NEVER bare `jvmTest`.

> Last action (2026-08-17, claude-code tutorial session): tutorial sections refactored to the
> block layout — TutorialSection(heading, blocks) with Block = Text | Code(lang, code) |
> Visual.Adsr(value, label); new AdsrVisual SVG comp in jsMain; 5 envelope visuals retrofitted
> into A2/A4. TutorialCurriculumSpec rewritten for blocks (12 tests, green — incl. the new
> visual-value-in-code drift guard). ⚠️ jsMain NOT compiled here (maintainer's watcher owns it).
> Note: the correct test invocation is `:jvmTest` (root module), NOT bare `jvmTest`.

> Note (2026-08-15): `:compileKotlinJs` hit the sprudel KSP cache corruption TWICE in this session.
> ROOT CAUSE FOUND: the maintainer runs a frontend auto-compile watcher — a standing Gradle process
> that `with-build-lock.sh` cannot serialize against (it never takes the flock). While the watcher
> runs, do NOT invoke Gradle at all; the watcher compiles on save. Recover corruption with
> `:sprudel:clean`.

> Last action (2026-08-15, claude-code session): UI background swap — CodeMirror surface now black
> (`CodeMirrorTheme.background` = #000000), `--klang-bg-menu` token tuned by eye to #151621
> (sidebar + header use new `.chrome-bg` CSS class with noise), RoundGauge ring brightened via
> `.round-gauge` class (#8891a0). `:compileKotlinJs` green. Not committed.

> Last action (2026-08-15, klang-ai session): offline render of Der Schmetterling v22 only — no
> source changes, no test runs.

> Last action (2026-08-10, klang-ai session): offline render of Der Schmetterling only — no source
> changes, no test runs. The cursorFrame Int→Double benchmark call sites were fixed by another
> session before this; the root build compiles again.

Convention adopted from the sibling `ultra` project. Two layers, because they catch different failures:

| Layer                            | What it is                                          | Catches                                                                                                            |
|----------------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| **This file**                    | Advisory. A holder name + a handover note.          | *Different sessions / days.* Tells the next agent what changed under them and what is half-done. A human reads it. |
| **`console/with-build-lock.sh`** | Real. An exclusive `flock` on `.claude/build.lock`. | *Same moment.* A second process physically cannot build while the first one is building.                           |

## Why Klang needs this

1. **Two concurrent Gradle invocations corrupt the sprudel KSP cache.** Recovery is
   `./gradlew :sprudel:clean`, but the symptom surfaces later as a stale-class or IR-lowering error somewhere unrelated,
   so the cause is easy to misdiagnose.
2. **Worse, during a mutation-check campaign: a build that races an edit produces a wrong verdict.**
   If one agent restores a mutation while another is running a test, the second agent sees code it never chose. A green
   that should have been red is indistinguishable from a toothless test — and the whole point of the audit is that a
   green must be *earned*. A raced verdict silently poisons the ledger.

## Take it before you build

1. **Read this file as its own step.** Do not chain the read into the build with `&&` — a check whose result cannot
   change what happens next is not a check. (This amendment is inherited from ultra, where exactly that happened.)
2. If `STATE: FREE`, rewrite `HOLDER` / `SINCE` / `STATE`, **then** build.
3. Release when done: set `STATE: FREE` and leave a handover note under
   "What the last holder changed" — what you touched, what is half-done, what would surprise the next agent.
4. Re-read before every build and every commit, not once per session — the holder changes underneath you.

## The mechanical lock

```bash
console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests some.Fqcn
console/with-build-lock.sh bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'
```

Waits up to `KLANG_LOCK_TIMEOUT` seconds (default 900), exits **75** if it cannot acquire.

**For a mutation check, the critical section is `mutate → build → restore`, not just the build.**
Wrapping only the Gradle call leaves the window open where it matters most.

## Rules for sub-agent fan-out

See `/agent-fleet`. The short version:

- **Default: the coordinator owns the build.** Workers read, analyse and propose; they do not build. Say so in the
  worker prompt: *"Do NOT run Gradle or any build command."*
- **Only ONE owner mutates production code**, ever. Mutation-checking is inherently serial — it edits shared files, so
  two mutators read each other's edits and both draw wrong conclusions. This is not fixable with a lock around the
  build; it needs a single owner.
- If a worker genuinely must build, it goes through `with-build-lock.sh`, and only one worker gets that permission.

## If the lock looks stale

If `SINCE` is more than a day old and the holder has committed nothing in that time, the holder probably died. **Do not
take the lock silently — ask the maintainer.** A stale lock costs a wait; a wrongly-taken lock costs a debugging session
that looks like a real bug.

For the mechanical lock, a stale `.claude/build.lock` is harmless: `flock` releases on process exit, so the file's
content may be stale but the lock itself never is. Only the printed holder record can lie.

---

## What the last holder changed — cursorFrame Int→Double, 2026-08-10

**The backend timeline no longer dies after 12.4 hours.** `cursorFrame` and every other ABSOLUTE
frame is now `Double`; per-sample offsets stay `Int`. Not committed.

The old `Int` cursor overflowed after ~12.4 h at 48 kHz (13.5 h at 44.1) and audio **silently
stopped** — no crash, no error, nothing in the console. A tab left open overnight was enough.
Confirmed by test before the fix: 0/60 blocks produced audio near the boundary, versus 55/60 at
frame 0. The degradation also starts *before* the wrap, because scheduling round-trips through
seconds and that conversion loses its integers first.

**`Double`, not `Long`** — it needs no exception to the "no `Long` in audio paths" rule, it is a
native JS number where `Long` is emulated and allocates, and it is exact for integers to 2^53
(~5,950 years at 48 kHz, verified bit-exact over 10 M accumulations).

**The rule to keep in your head:** absolute timeline = `Double`, relative position = `Int`, converted
once per block per voice. ⚠️ `IgniteContext.gateEndFrame` is voice-RELATIVE and stays `Int` despite
sharing its name with `BlockContext.gateEndFrame`, which is absolute and `Double`.

Guarded by `LongRunningTimelineSpec`, mutation-checked. All modules green (JVM + JS).

## What an earlier holder changed — master limiter lookahead, 2026-08-04

**Phases 0–2 of `docs/tasks/master-limiter-lookahead.md` are IN. `audio_be:jvmTest` is green (946).**
Not committed — the tree is yours to review.

**The knock is fixed.** `effects/Compressor.kt` gained a `lookaheadSeconds` constructor `val`
(default `0.0` = the old path, untouched, which is what every per-orbit compressor uses). Above it, the gain is built by
**min-hold (D+1) → release → two cascaded boxes → delay (D)**. `MasterStage` now runs it at 5 ms and its DC blockers
moved **before** the limiter.

Measured on the real code, 55 Hz kick: +12 dB over the ceiling used to exit at **+11.67 dBFS with 5.2 ms of hard
clipping**; it now exits at **−0.37 dBFS, zero samples clipped**.

**Two things to know if you touch this:**

1. **`MasterStage` no longer has one limiter character — it has two.** Naming rule (set 2026-08-11, when the shared
   constants moved to `audio_bridge/constants/MasterLimiterDefaults.kt`):
    - **no prefix** — `LIMITER_THRESHOLD_DB` / `RATIO` / `KNEE_DB` / `RELEASE_SECONDS`: **shared** by both limiters, one
      declaration in `audio_bridge`. Both sides read it, so they cannot drift; there is nothing to "re-sync".
    - **`HOUSE_LIMITER_*`** — the house limiter's own timing (global, post-sum, 5 ms lookahead, smoothing = the whole
      window). Stays in `MasterStage`: no DSL field carries it, because the house limiter is not authorable.
    - **`AUTHORED_LIMITER_*`** — the opt-in `MasterFx.limiter()` (per-playback, **lookahead 0**, 1 ms one-pole attack),
      in `audio_bridge` because it *is* a wire default.

   The house/authored timing differs **on purpose** — an authored limiter with latency would delay its playback against
   every other one. `MasterDefaultsSyncSpec` asserts that divergence (and pins the authored values on the wire model
   itself). Do not "unify" the two timings.
2. **`lookaheadSeconds` is a constructor `val`, unlike every other param.** The rings are sized once from it. Making it
   a `var` would resize a buffer on the audio thread.

**Phase 3 shipped too.** `MasterFx.limiter().lookahead(seconds)` exists in KlangScript, the wire model carries
`lookaheadSeconds` (default **0** — authored limiters are per-playback, so latency there would desync them against other
playbacks), `MasterChain.limiters` is `internal` so specs can assert the wire→DSP hop, and the value is `finite()`
-guarded and capped at `MAX_LOOKAHEAD_SECONDS = 0.05` because it is the one stage parameter that sizes an array on the
audio thread.

**Still open:** Phase 4's by-ear retune sheet (the user has listened and likes it; the gain-reduction measurements are
not done) and Phase 5, latency reporting — nothing accounts for the 5 ms yet, including `KlangOfflineRenderer`'s frame
count.

`audio_be:jvmTest` 946 green. No CPU regression on `runSongBenchmark`
(Der Schmetterling medRTF 0.086 with lookahead vs 0.094 without — the difference is run-to-run variance, not a speedup).
