# Realtime note-off: gate release for running voices (v2) — REVIEWED, GO

**Status: REVIEW LOOP CLOSED 2026-08-29 — round 3 CLEAN.** Round 2 (2 fresh reviewers,
two-phase) found 3 MAJOR-class + 9 MINOR — all fixed (zero-length-tap floor, Cleanup releases
held voices, R1-stamp guards, endFrame-mirror guard, scheduler-level playbackId row, moved-gate
strip units; 9 mutations killed). Round 3 (2 fresh reviewers, two-phase): zero CRITICAL/MAJOR,
all round-2 fixes re-derived as holding; MINOR batch applied once (`VoiceOrigin.Realtime.held` —
Cleanup releases ONLY held voices, both predicate directions mutation-killed; releaseGate KDoc
precondition; KDoc links; renderGate comment). **DECIDED (maintainer, 2026-08-29): ACCEPT** the vca-off edge — a bare ignitor (no envelope of
its own) under `vca(on = false)` with an authored release < the 4 ms teardown window enters the
fade mid-ramp on a realtime note-off (up to ~50% step at 2 ms, 100% at 0). Reachable only via
adsrOff + no ignitor envelope + sub-4 ms authored release + held note; documented at the
`renderGate` comment. If it ever turns up audibly, the principled fix is rescaling the ramp in
`renderGate` (a complete shorter ramp from 1.0), NOT extending the voice. UNCOMMITTED —
maintainer inspects the diff and gives the commit go.
Amendments A1 + A3 in as reviewed; **A2 implemented STRICT — see the implementation note in the
A2 section.** A1's manual mutation check ran: exactly the vca-off spec went red. Test-only
surprise: the house master's 5 ms lookahead delays all output ~1.7 blocks, so onset assertions
allow the pipe delay. Parent workstream: `docs/tasks/midi-keyboard-playground.md`.

## Goal

A MIDI key is released → the already-running realtime voice must enter its ADSR **release
tail from its current level** (no hard cut, no click), then die right after the tail.

## What already exists (v0, shipped 2026-08-28)

- Wire: `RealtimeVoice(liveId, data, gateDurSec?)` + `Cmd.StartRealtimeVoice`. `gateDurSec = null`
  means "held": the BE opens the gate at `nowSec + REALTIME_HELD_GATE_SEC` (36 000 s — bounded so
  `VoiceFactory`'s Int gate-duration math cannot overflow; `VoiceFactory.kt:93`).
- BE: `VoiceScheduler.startRealtimeVoice` promotes straight to active via the shared
  `activateVoice`; active voices carry a sealed `VoiceOrigin` — `Timeline(source)` or
  `Realtime(liveId)` — so the stop command has its handle and the replace-dedup structurally
  cannot match realtime voices.
- FE: `KlangRealtimeVoicePlayback.startVoice(data, gateDurSec): Int` (returns liveId).

## The proposed mechanism

### Why it is small: the envelopes are position-based and re-entrant

- Amp VCA (`strip/filter/EnvelopeRenderer.kt`): per sample checks `absPos >= gateEndPos`
  (line ~93); on the first release sample it captures `releaseStartLevel = currentEnv` — i.e.
  **release-from-current-level is already built in**. `gateEndPos` is currently a `val` baked at
  construction (line ~51) from a constructor-passed `gateEndFrame`.
- Control-rate envelopes (`strip/EnvelopeCalc.kt:23` `calculateControlRateEnvelope`) take
  `gateEndFrame` as a parameter per call; callers `FilterModRenderer.kt:28` and `FmRenderer.kt:27`
  each hold their own constructor-baked copy. `FilterModRenderer` computes the level at gate end
  via `levelAtPosition` (the 2026-03 release-from-current-level fix), so a moved gate stays
  consistent.
- `BlockContext` (`strip/BlockContext.kt:49-55`) ALREADY carries absolute `startFrame` /
  `endFrame` / `gateEndFrame` per voice — currently `val`, currently redundant with the baked
  renderer copies.

### The cut

1. **New Cmd** `@WireName("stop-realtime-voice") StopRealtimeVoice(playbackId, liveId)`.
   Dispatcher arm uses `engines[playbackId]?` (a stop must never CREATE an engine, unlike
   `engineFor`). Forwarding arm in `JsAudioBackend`'s exhaustive `when` (~line 178).
2. **`Voice.releaseGate(atFrame)`** — `gateEndFrame` and `endFrame` become `var`:

   ```kotlin
   fun releaseGate(atFrame: Double) {
       if (atFrame >= gateEndFrame) return           // natural gate earlier — no-op
       val releaseSpan = endFrame - gateEndFrame     // preserve this voice's own tail length
       gateEndFrame = atFrame
       endFrame = minOf(endFrame, atFrame + releaseSpan)
       blockCtx.gateEndFrame = gateEndFrame
       blockCtx.endFrame = endFrame
   }
   ```

   Moving `endFrame` is what prevents the leak: without it a released voice renders silence in
   the active list until the 10 h horizon (`Voice.render` returns false only past `endFrame`,
   `Voice.kt:103`).
3. **Single source of truth**: `BlockContext.gateEndFrame`/`endFrame` become `var`;
   `EnvelopeRenderer`, `FilterModRenderer`, `FmRenderer` drop their baked copies and read
   `ctx.gateEndFrame` (recompute `gateEndPos = (ctx.gateEndFrame - startFrame).toInt()` once per
   render call — one subtraction per block, the per-sample loop stays Int).
   `EnvelopeRenderer.kt:196` already reads `ctx.endFrame` for the release landing frame, which
   now updates correctly for free.
4. **Scheduler**: `stopRealtimeVoice(liveId)` scans `active` for
   `origin == VoiceOrigin.Realtime(liveId)` → `voice.releaseGate(context.clock.cursorFrame)`.
   Unknown liveId = silent no-op (releasing a key after a fixed-length voice ended is normal).
5. **FE**: `KlangRealtimeVoicePlayback.stopVoice(liveId)`; the page keeps `held: note → liveId`.
   Note-on on an already-held note (retrigger) stops the old liveId first — otherwise the old
   voice is orphaned into its 10 h gate. Note-off: `held.remove(note)?.let { stopVoice(it) }`.
6. **Specs**: held+stop → silent after the release span; two held voices, stop one → the other
   keeps sounding; unknown liveId → no-op. Plus wire-codec round-trip for the new Cmd.

## ~~Known limitation (deliberate v2 scope)~~ — SUPERSEDED by review amendment A1

The original text deferred moving `IgniteContext`'s gate. The review rejected the deferral:
"believed inaudible" holds only for the default vca-on path. See amendment A1 below — the
IgniteContext move is IN v2 scope.

## Points the reviewing agent should judge

1. **Threading**: `releaseGate` mutates `Voice`/`BlockContext` from the command-drain path.
   Assumption: the dispatcher pump is drain-commands → renderBlock on ONE audio thread, so no
   concurrent access. Confirm nothing else touches voices off-thread.
2. **`endFrame` consumers**: `Voice.endFrame` was `val`; it is read in `Voice.render` and
   `BlockContext.endFrame` (release landing at `EnvelopeRenderer.kt:196`). Are there other
   consumers (SendRenderer, VoiceLease/orbit ownership, diagnostics) that assume immutability?
3. **Release-state machine**: `releaseGate` guards `atFrame >= gateEndFrame`, so the gate only
   ever moves EARLIER, and only while the voice is still gated (cursor < old gate). Confirm no
   renderer caches "not yet released" state across blocks that would miss the moved gate
   (`env.releaseStarted` is set lazily on first release sample — believed safe).
4. **Precomputed release constants**: `EnvelopeRenderer` precomputes `relFrames`/`relDenom`/
   `relOffset` from `env.releaseFrames` at construction — the release SPAN is unchanged by a
   moved gate, so these stay valid. Confirm.
5. **Sample voices**: `endFrame` may be bounded by sample length rather than gate+release
   (`VoiceFactory.kt:359` area). The `minOf` in `releaseGate` only ever shrinks `endFrame` —
   confirm that is the right behavior for samples (and that `releaseSpan` can be ≤ 0 there
   without harm).
6. **Legato**: `VoiceFactory.kt:92-95` scales the gate duration by `legato` at build time.
   `releaseGate` bypasses that (realtime voices are not expected to carry legato). Acceptable?
7. **Alternatives considered** (for completeness):
   - Baked-copies + a `Gated` sub-interface on renderers that Voice walks on release — more
     plumbing, `BlockRenderer` is a `fun interface` (SAM), and it duplicates gate state again.
   - Hard cut via `endFrame` only — rejected: no musical release, and the 1 ms VCA de-click
     fade is a choke, not a tail.
   - Sealed `ScheduledVoice` hierarchy on the wire — rejected earlier in the workstream
     (realtime voices never touch heap/epoch machinery; split at the Cmd level instead).

## Non-goals

- Pedal/sostenuto semantics, polyphony limits / voice takeover (`docs/tasks/voice-takeover.md`),
  velocity curves, CC mapping.
- Any change to the timeline path's behavior — after the refactor it must be bit-identical
  (renderers read the same values from `BlockContext` that they used to hold as copies).

---

# Review (2026-08-29) — verdict, amendments, and implementation notes

Reviewed by the block-framing-audit agent (the one that owns
`docs/plans/block-framing-invariance.md` and the envelope-ownership work in
`docs/tasks/ignitor-envelope-ownership.md`). Every load-bearing claim below was verified against
the code, not assumed.

## Verdict

**Feasible and genuinely small — the mechanism is sound.** The central claim is TRUE in the
code: the envelope machinery is position-based and re-entrant. `EnvelopeRenderer` captures
`releaseStartLevel = currentEnv` lazily on the first release sample, and its release constants
(`relFrames`/`relDenom`/`relOffset`) are computed PER RENDER CALL from `env.releaseFrames`, not
constructor-baked — so a moved gate needs nothing there (judgement point 4 is even safer than
the doc assumed). The step-3 single-source-of-truth refactor deletes duplicated baked state and
is a net simplification.

**No ADSR curve depends on voice length** (maintainer asked, verified on both doors): every
segment length is `authored seconds x sampleRate`, attack/decay anchor at the note START,
release anchors at the GATE END, and sustain is the unbounded `else -> sustain` arm with no
duration of its own. Moving the gate moves WHEN release begins, never what the curves look
like. Note-off during attack/decay releases from the captured mid-segment level — identical to
what a short-authored timeline gate already does today.

## Answers to the judgement points

1. **Threading: confirmed safe.** The worklet realm is single-threaded — command handling and
   `process()` never interleave, so commands land BETWEEN blocks. JVM paths (offline renderer,
   tests) are plain loops. `releaseGate` from the command drain cannot race a render mid-block.
2. **`endFrame` consumers: all move correctly.** `Voice.render` reads it per block (lifecycle +
   `vEnd`); the vca-off teardown fade reads `ctx.endFrame` per call
   (`EnvelopeRenderer.renderGate`), so the 4 ms guard relocates with the moved end for free.
   Its endpoint math requires an INTEGRAL `startFrame` — realtime voices go through the same
   `VoiceFactory` flooring, keep it that way. No other consumers: `SendRenderer`,
   `VoiceLease`/orbit ownership, and diagnostics do not read voice `endFrame`.
3. **Release state machine: safe.** `env.releaseStarted` is set lazily on the first release
   sample and re-cleared on the non-release path; the `atFrame >= gateEndFrame` guard makes a
   double-stop a no-op and the gate can only move earlier.
4. **Release constants: moot** — computed per render call already (see verdict).
5. **Sample voices: NOT safe as proposed — amendment A2.**
6. **Legato bypass: acceptable** (realtime voices carry no legato; nothing else reads it).
7. Alternatives: agreed with the rejections.

## Amendments (all three are in-scope for the cut)

**A1 — move `IgniteContext`'s gate too (the deferral is audible on `.adsrOff()` instruments).**
With `vca(on = false)` the IGNITOR envelope owns amplitude and reads
`IgniteContext.gateEndFrame` (voice-relative Int). If only `BlockContext` moves, note-off on an
adsrOff instrument means: sustain keeps sounding for the whole moved tail, then the 4 ms
`VCA_OFF_TEARDOWN_FADE_SECONDS` chops it — the exact bare-gate knock the envelope-ownership
work removed, plus a sustain overhang. Fix (small): `IgniteContext.gateEndFrame` and
`voiceEndFrame` become `var`; `Voice.releaseGate` also updates the voice's `signalCtx`:

```kotlin
val relGate = (atFrame - startFrame).toInt()
signalCtx.gateEndFrame = relGate
signalCtx.voiceEndFrame = relGate + signalCtx.releaseFrames
```

`AdsrIgnitor` (`IgnitorEnvelopes.kt:88`) has the same release-from-history latch as the strip,
so it releases correctly from the current level; the ignitor filter envelope
(`IgnitorFilters.kt:663`) comes along for consistency. These are the ONLY two ignitor-door gate
consumers (grep-verified).

**A2 — one-shot sample guard.** For a sample shorter than its gate, `endFrame < gateEndFrame`,
so `releaseSpan` goes negative and the proposed `minOf` cuts the sample dead at note-off. Make
it an early no-op:

```kotlin
if (atFrame >= gateEndFrame) return        // natural gate earlier — no-op
if (endFrame <= gateEndFrame) return       // one-shot: ends on its own, note-off must not chop it
```

> **Implementation note (2026-08-29, MIDI-workstream agent — for the audit review):** implemented
> STRICT (`endFrame < gateEndFrame`), diverging from the `<=` above. Verified in `buildVoice`:
> `endFrame = gateEndFrame + release * sampleRate` UNCONDITIONALLY — sample voices are never
> end-bounded by PCM length (SampleIgnitor emits silence past `stopFrame`; the voice lives to
> gate+release). So `<` cannot fire today (defensive only), while `==` is exactly the release-0
> case, where `<=` would make a held release-0 voice IGNORE its note-off and sound until the
> horizon. A release-0 stop drops the voice between blocks WITHOUT rendering another sample —
> no de-click runs and none is needed; identical to how a timeline release-0 note ends at its
> gate (corrected per R9 — an earlier version of this note wrongly claimed the VCA de-click
> fades it). Spec: release-0 note-off is covered in `RealtimeVoiceSpec`;
> the sample-shorter-than-gate spec (requirement 3) is dropped as its state is unconstructible.

**A3 — derive the held-gate horizon from the sample rate.** `REALTIME_HELD_GATE_SEC = 36_000`
is calibrated to 48 kHz (its own comment says so); at 96 kHz the voice-relative Int math
overflows (3.46e9 > 2^31) and the voice dies instantly or misbehaves. Sample rate is a platform
variable exactly like block size (`docs/plans/block-framing-invariance.md`, "block size must be
treated as non-constant"). Use e.g.
`min(36_000.0, (Int.MAX_VALUE / 2).toDouble() / sampleRate)` seconds at the use site.

## Decided semantics (maintainer, 2026-08-29 — do not re-open)

- **Note-off is block-quantised** (`releaseGate(cursorFrame)` lands on the block boundary).
  DECIDED as Class 2 semantics: live-input jitter dwarfs one block (<= 3 ms). Put a short
  comment at the call site naming it (the block-framing audit's convention for deliberate
  block-rate observation, see the O3 precedent in the plan).
- **`accelerate` does nothing on held realtime voices.** It is the engine's single
  length-normalized DSP (`voiceElapsedFrames / voiceDurationFramesD`, glide over gate duration);
  with the held horizon the glide is ~0 — that IS the coherent answer for an unknown duration.
  Do NOT retro-shrink `voiceDurationFrames` at note-off: progress would jump and the pitch would
  leap. Leave `voiceDurationFrames` untouched by `releaseGate`; name the inertness in a comment.

## Complete DSP inventory (so nobody re-derives it)

Sites that carry the moved gate — this is the WHOLE cut on the engine side:
`Voice` (2 vars + `releaseGate`), `BlockContext` (3 fields to `var`), `EnvelopeRenderer` (drop
baked `gateEndPos`, recompute from ctx per render), `FilterModRenderer` (drop baked copy — its
`EnvelopeCalc` path computes level-at-gate-end statelessly, so a moved gate is exact by
construction), `FmRenderer` (drop baked copy; the one-sample fm-depth collapse at gate end is
the pre-existing E11 class, not new), `IgniteContext` (2 fields to `var`, updated in
`releaseGate` — amendment A1).

Verified to need NOTHING: every oscillator/source (the oscillator audit verified zero gate
reads; Karplus strings decay on their own), all filters, tremolo/distort/crush/coarse, the
delay/phaser/shimmer set (freshly audited), orbit + master buses (voice-agnostic),
`PitchEnvelopeRenderer` (onset-anchored), vibrato, `MemoizingIgnitor` (no gate in its key).

## Spec requirements (review standard: every new guard mutation-checked, RED before trusted)

1. **The I4 guard (block-framing):** the release must land on the same NOTE-RELATIVE sample
   regardless of which block the stop arrives in — render the same held note twice with the stop
   issued at the same cursorFrame but different block alignments, outputs bit-identical from the
   release on. (This pins the position-based mechanism; it is the transition-placement invariant
   from the block-framing plan.)
2. **adsrOff release (A1):** a held realtime voice with `vca(on = false)` + ignitor `.adsr()`
   must RAMP at note-off (assert the post-stop block is a decaying curve, not sustain followed
   by a 4 ms cliff). Mutation: revert the signalCtx update — must go red.
3. **One-shot guard (A2):** a sample voice with `endFrame < gateEndFrame` is untouched by a stop.
4. Held+stop -> silent after the release span; two held voices, stop ONE (the scan must release
   EVERY voice matching the liveId, not the first — future layered voices); unknown liveId
   no-op; double-stop idempotent; retrigger stops the old liveId.
5. Wire-codec round-trip for `StopRealtimeVoice`.
6. **Timeline bit-identity:** the step-3 refactor must leave timeline voices byte-identical —
   the existing audio_be suite (1350 tests) pins most of this; run it in full.

## Process notes for the implementing agent

- Gradle is single-writer: read `.claude/BUILD-LOCK.md` as its own step, claim it, run every
  build through `console/with-build-lock.sh`, release with a handover note. Check `ps` for the
  maintainer's FE watcher before building (if it runs, do not invoke Gradle at all).
- Single spec run: `./gradlew :audio_be:jvmTest --tests fully.qualified.Name` — unquoted FQCN,
  ONE per invocation (multiple `--tests` flags break Kotest discovery here).
- Kotest test-class names in `cylinders/` differ from file names; check the class name before
  filtering.
- After the cut: hand the diff to the audit agent for the review-loop (fresh reviewers, loop
  until clean, fixes re-reviewed, new guards mutation-checked).

---

# Audit review — round 1 findings (2026-08-29)

Process: two fresh Opus reviewers (coding + audio/DSP, read-only) over the diff, plus the audit
agent's own mutation pass. **Per the maintainer's instruction, NOTHING was fixed** — this report is
the complete handover; the MIDI agent fixes, then hands back for audit round 2 (fixes are
unreviewed changes). Every CONFIRMED tag below means the claim was verified against the code or
demonstrated empirically by a mutation run; nothing here is speculation.

Housekeeping note: both reviewers saw transient `// MUTATION …` comments in the tree — that was
the audit's own mutation campaign running concurrently. Every mutation was restored byte-exact
(cmp-verified); `grep -rn MUTATION audio_be/src/commonMain` is empty. The tree is the implementer's
cut, untouched.

## Verdict

The mechanism is sound and the refactor is FAITHFUL: both reviewers independently traced every
renderer and all 10 fixture edits — the per-render recomputes read exactly the values the deleted
baked copies held, no expected value was moved, timeline voices are bit-identical value-for-value.
The A2 strict divergence is CORRECT (the audit's own `<=` suggestion would have stranded release-0
voices) and is now mutation-pinned by the release-0 row. **But two MAJOR defects (one production,
one spec) and an FE-lifecycle MAJOR must be fixed before commit.**

## Findings

**R1 — MAJOR, CONFIRMED. The stop cursor is one block STALE: the release enters mid-curve, and a
release <= one block renders NO tail at all.**
`VoiceScheduler.kt:312` passes `context.clock.cursorFrame`, but the cursor is written only at the
top of `renderBlock` and commands drain BETWEEN blocks — so at stop time it holds the start of the
block ALREADY rendered (`VoiceFactory`'s own comment ~:355 states this convention). Consequences:
the first rendered release sample has `relPos = blockFrames` (128), so a 10 ms release enters at
shape(0.709) ~= 0.39 — a one-sample gain step at note-off, smoothed only by the 1 ms de-click on
the vca-on door and RAW on the A1 ignitor door (`AdsrIgnitor.declickSeconds` defaults 0); and any
release span <= blockFrames (~2.9 ms) gives `endFrame < nextBlockStart`, so `Voice.render` drops
the voice at FULL amplitude with zero rendered tail and no de-click — the exact hard cut this
feature exists to remove. The SAME skew already truncates v0 onsets (startFrame is stamped one
block in the past; a 1 ms attack is skipped entirely) — same root cause, fix together.
**Fix:** `releaseGate(context.clock.cursorFrame + context.blockFrames)` — the first frame that can
actually be rendered; `relPos` then starts at 0, matching the timeline path exactly. Block
quantisation (decided Class 2) is unaffected. The coding reviewer checked the existing 11 rows
stay green under this fix. Consider the same `+ blockFrames` for the v0 onset stamp.

**R2 — MAJOR, CONFIRMED EMPIRICALLY. Nothing distinguishes a release tail from a hard cut on the
default vca-on path — the feature's headline behavior is unguarded.**
The audit ran the natural mutation (`endFrame = atFrame`, releaseSpan dropped = a hard cut):
**all 11 rows stay green.** `hasAudio(after.first())` at `RealtimeVoiceSpec:150` is satisfied by
the master's 220-frame pipe delay alone (the first post-stop block contains only pre-stop audio,
whatever the stop did), and every other row checks silence-later or plumbing.
**Fix:** one discriminating row: release ~0.2 s; assert a mid-tail block PAST the pipe delay is
audible AND below the held peak, and that ~3 successive tail blocks decay monotonically. Must go
RED under the hard-cut mutation before it counts.

**R3 — MAJOR (spec), CONFIRMED EMPIRICALLY. The I4 row cannot discriminate the property it
names.** Both the voice start AND the stop are cursor-stamped, so shifting the absolute grid
(`run(0.0)` vs `run(64.0)`) shifts everything together — the two runs are structurally identical
by construction. The audit ran the natural alignment slip
(`gateEndPos = (ctx.gateEndFrame - ctx.blockStart).toInt()` in `EnvelopeRenderer`): the I4 row
stays green. IMPORTANT context so nobody panics: the slip IS killed by the wider suite
(`VoiceLifecycleTest` "gateEndFrame triggers release phase" + `FmSynthesisTest` "FM envelope
release phase" both go red — the audit verified this with a full-suite run), so the refactor is
guarded; it is the REALTIME row that pins less than its name claims. Also latent: the two runs get
different `nowSec`, so a unison-ignitor fixture would seed `PhasePools` differently and the row
would go red for an unrelated reason.
**Fix options:** replace with a block-SIZE sweep (the `BlockFramingInvarianceSpec` idiom: same
note-relative span at 128 vs 64, concatenations bit-identical), or drive `Voice.releaseGate`
directly on a `VoiceTestHelpers` voice with a NON-block-aligned `startFrame`; at minimum rename/
re-comment the row to the absolute-leak property it actually pins.

**R4 — MAJOR, CONFIRMED. FE lifecycle leaks held voices.** `MidiPlaygroundPage.onUnmount`
(:119-124) unhooks MIDI but never releases `heldVoices` — a key held while navigating away
sustains ~6.8 h; on REMOUNT the new component gets an empty map while `createRealtimePlayback` is
idempotent per name, so the old liveId becomes UNREACHABLE for the rest of the session. Same
outcome on device unplug mid-note or a lost note-off. Additionally: CC 120/123 (All Sound/Notes
Off — the hardware panic button) fall into `else -> {}`, and the map keys by NOTE only, so the
same note on two channels collapses (ch-1 note-off kills the ch-2 voice, ch-2 note-off finds
nothing).
**Fix:** release `heldVoices.values` + clear in `onUnmount` AND on device removal; key the map by
(channel, note); handle CC 123/120 as release-all (cheap, closes the panic path).

**R5 — MINOR, CONFIRMED. A negative authored release makes the A2 guard fail STUCK.**
`AdsrDef.Std.resolve` passes `release` through raw (raw-Motor, no coercion), so
`startVoice(data.copy(adsr = AdsrDef.Std(release = -0.5)))` yields `endFrame < gateEndFrame`; the
strict guard returns early and the note-off is SILENTLY IGNORED — the voice sounds to the horizon.
Unreachable from the MIDI page, reachable from the `KlangRealtimeVoicePlayback` API. For a
note-off, ignore is the worst failure mode.
**Fix:** `val releaseSpan = (endFrame - gateEndFrame).coerceAtLeast(0.0)` and DROP the early
return — every case then stops the voice (a negative-release note-off hard-stops like release-0).
Keep a separate, explicitly-scoped guard only if/when a genuinely PCM-bounded voice type exists.

**R6 — MINOR, CONFIRMED. `IgniteContext.voiceEndFrame` has ZERO readers** (repo-grep: constructed
and assigned, never consumed — the ignitor door reads `gateEndFrame` only). Half of A1's
`releaseGate` write is inert and cannot be mutation-pinned, and `VoiceScheduler`'s A3 comment
(:320) misattributes the Int-overflow constraint to it (the real constraints are
`voiceDurationFrames`/`gateEndFrame`).
**Fix:** delete the field + the write (preferred), or mark both sites "currently unread,
forward-looking" so no future reviewer hunts for the missing guard.

**R7 — MINOR, CONFIRMED EMPIRICALLY. The every-match liveId scan is unguarded.** The scheduler
KDoc promises "releases EVERY active realtime voice carrying liveId"; the audit's first-match-only
mutation survives the suite (no fixture ever starts two voices under one liveId).
**Fix:** three-line row — two `start(d, liveId = 1)`, one stop, both silent; must kill the
first-match mutation.

**R8 — MINOR. `stopRealtimeVoice(liveId)` is the only playback-scoped entry point without a
`playbackId`** (its counterpart takes one; `ActiveVoice` carries it). Latent: the day one
scheduler hosts two playbacks, a stop for A releases B's voice — liveIds restart at 1 per FE
playback. **Fix:** take `playbackId` and add it to the match, aligning the signature with
`startRealtimeVoice`.

**R9 — MINOR, CONFIRMED. The release-0 hard-stop does NOT go through the VCA de-click, contrary
to the code comment and the A2 implementation note.** With span 0, `endFrame == atFrame`, and the
next block satisfies `blockStart >= endFrame` — the voice is dropped BETWEEN blocks without
rendering a sample; no de-click, no teardown fade runs. The BEHAVIOR is settled-desired (identical
to a timeline release-0 note), but the stated mechanism does not exist.
**Fix:** correct the comment in `releaseGate` and the sentence in the A2 note ("dropped without
rendering; matches timeline release-0" — not "the VCA de-click fades it").

**R10 — MINOR. The loosened onset rows no longer pin the heap-bypass property.** With
`blocksAfter = 4`, `heard.take(4).any {}` is `heard.any {}` (the take is a no-op) — "sounds
within 11.6 ms", not "in the very next rendered block" as the spec KDoc still claims; a mutation
routing realtime voices through the scheduled heap survives. The pipe delay is a CONSTANT
(220 frames = 1.72 blocks), so a deterministic index pin is available.
**Fix:** after R1's `+ blockFrames` fix lands (it shifts the constant by one block), compute the
tight index once and pin `heard[i].shouldBeTrue()`; update the KDoc to match. Rows :87/:97 may
keep `any {}` — their point is the later silence/sustain.

**R11 — notes (no code change demanded, record + tiny edits):**
- (a) **E11 becomes user-reachable.** The fm-depth envelope ships `releaseFrames = 0.0`
  (`VoiceFactory:244`), so fm depth collapses in one block-rate step at the gate. Before v2 a held
  voice's gate sat hours away; a note-off now fires it on every `fmh` realtime voice, coinciding
  with R1's amp step. Pre-existing (ledger E11, P4 scope + the `fmrelease` surface question) —
  this cut makes that decision more urgent, nothing here to fix.
- (b) `IgniteContext`'s section header still says "Static per voice (set at creation, never
  changes)" above two now-`var` fields — exactly the invitation to bake a copy again. Amend the
  header / move the fields under a "moved by Voice.releaseGate" heading.
- (c) Style: the two brace-less guard returns in `releaseGate` (statement-position ifs always get
  braces, `/code-style` rule 1) and a blank line before the `if` in the stop scan (rule 2).
- (d) The `endFrame`-move (leak-prevention) line is guarded only COINCIDENTALLY: the audit's
  drop-the-move mutation is killed by the release-0 row alone (span 0 makes `endFrame == gate`).
  A direct unit row (`releaseGate(t)`; assert `endFrame == t + span`; `render` false past it)
  would make the guard intentional. Optional but cheap.

## Verified clean — do NOT re-derive (merged from both reviewers + the audit)

Baked-copy refactor faithful, timeline bit-identity value-for-value (incl. sample voices: both
origins are `sampleStartFrame`); all 10 fixture edits are pure plumbing (`FilterEnvSemitoneSpec`'s
sentinel swap keeps the release branch unreachable); release-endpoint math EXACT under a moved
gate (integral Doubles, Sterbenz subtraction, last frame still lands p = 1.0); vca-off teardown
precondition holds (integral startFrame + whole-block cursor); `releaseStarted` latches safe on
both doors (the retro-placed gate lands in a finished block; the latch is captured next block from
the true current level); `MemoizingIgnitor` cannot serve stale blocks (voiceElapsedFrames in the
key; releaseGate runs between blocks); ignitor door reads the gate per call, `computeFilterEnvelope`
stateless-exact; Int conversion exact, A3 headroom holds at any sample rate; per-block cost = one
subtraction + toInt, per-sample loops untouched; scan iteration safe (mutates voices, not the
list); dispatcher arm never creates engines; liveIds monotonic, never reused; velocity-0 note-on
routed to noteOff; A2's unconstructibility argument correct for today's factory (samples are never
end-bounded; `stopFrame` bounds the playhead only).

## Audit mutation table (all restored byte-exact)

| mutation | result |
|---|---|
| releaseGate inert (early return) | KILLED: tail, targeted-liveId, release-0, vca-off rows red |
| A2 guard `<` -> `<=` | KILLED: release-0 row red (the divergence is pinned) |
| idempotence guard removed | KILLED: double-stop row red |
| signalCtx gate line reverted (implementer's own check) | KILLED: vca-off row red |
| scan releases FIRST match only | **SURVIVED -> R7** |
| `gateEndPos` from blockStart (alignment slip) | SURVIVED RealtimeVoiceSpec + harness -> R3; KILLED by full suite (VoiceLifecycleTest, FmSynthesisTest) |
| hard cut (`endFrame = atFrame`) | **SURVIVED all 11 rows -> R2** |
| endFrame move dropped | KILLED by release-0 row only -> R11(d) |

## Handover

Fix order suggestion: R1 first (it changes the constants R2/R10's new assertions calibrate
against), then R2/R3/R7 (the spec teeth), then R4 (FE), then the MINOR/notes batch. After fixing:
full `:audio_be:jvmTest` + `:klang:jvmTest` + `:audio_be:compileKotlinJs`, run the named mutations
(hard cut, first-match, alignment slip vs the new/renamed rows) RED then restore, update this
file's status line, release the build lock with a handover note — then hand back for audit round 2
(fixes are unreviewed changes; the loop closes on a clean round).


---

# Round-1 fixes (2026-08-29, MIDI-workstream agent) — for audit round 2

All findings applied; per-finding notes where the fix deviated from or extended the suggestion:

- **R1**: both the stop AND the v0 onset stamp `context.clock.cursorFrame + context.blockFrames`
  (`VoiceScheduler.startRealtimeVoice` + `stopRealtimeVoice`) — attack and release curves now
  enter at position 0. The onset-row pipe constant shifted accordingly (see R10).
- **R2**: new row "note-off renders a DECAYING release tail, not a hard cut" — mid-tail must
  exceed the AUDIBLE bar (200, same as `hasAudio`), be below the held peak, and fall
  monotonically at 10-block spacing. First draft used `> 0` and the hard-cut mutation SURVIVED
  on 1-2 LSB of DC-blocker residue — the same masking class the audit flagged; threshold fixed,
  mutation now killed by this row.
- **R3**: the dispatcher-level I4 row is REPLACED by a Voice-level one: off-grid `startFrame`
  (37), gate moved to a non-aligned absolute frame (549), grid B offset -50 so BOTH grids render
  every note frame (the VCA de-click smoother is causal state — a grid that skips the first
  samples diverges legitimately; first draft hit exactly that). Kills the alignment-slip
  mutation directly.
- **R4**: `releaseAllHeld()` on unmount AND on input-device disconnect; held map keyed by
  (channel, note); CC 120/123 handled as panic (logged). Display map stays note-keyed (UI only).
- **R5 + R9**: strict A2 guard DROPPED; `releaseSpan = (endFrame - gateEndFrame).coerceAtLeast(0.0)`
  — a negative-release note-off now hard-stops like release-0. Comment corrected per R9: the
  voice is dropped between blocks without rendering; no de-click runs and none is needed.
- **R6**: `IgniteContext.voiceEndFrame` deleted repo-wide (field, factory local, ~50 test
  construction sites); the A3 comment now attributes the Int constraint to
  `voiceDurationFrames`/`gateEndFrame`.
- **R7**: row "note-off releases EVERY voice carrying the liveId" (two voices, one liveId);
  kills the first-match mutation.
- **R8**: `stopRealtimeVoice(playbackId, liveId)`, playbackId in the match.
- **R10**: onset row pins `heard[0] = false` and `heard[2] = true` against the constant
  220-frame pipe (post-R1 constants); KDoc updated.
- **R11(b/c/d)**: IgniteContext headers split static vs gate-moved; braces + blank line per
  `/code-style`; unit row pins `endFrame == atFrame + span` and death past it.

Mutation table round 1-fix state: hard cut → tail row + unit row red; first-match → every-match
row red; alignment slip → I4 row red; all restored byte-exact, `grep MUTATION` clean.
Green: full `:audio_be:jvmTest`, `:klang:jvmTest`, `:audio_be:compileKotlinJs`,
`:audio_bridge:jsTest`.
