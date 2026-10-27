# KLANG-00137: Master DSL — follow-ups

> **Status (2026-10-20): SCHEDULED.**
>
> **Part of:** KLANG-00357 (Effects: the Katalyst and the master)

> Carried out of KLANG-00136 (master dsl)
> when that shipped (2026-08-03). Nothing here blocks anything; each item is small and independent.
> Priority: **NICE**, except the parity audit, which the user raised to a standing principle.

> **Status 2026-09-28, after phase 3 step 12** (KLANG-00238 (phase3 step12 master as katalyst)):
> the Master DSL is gone; the output runs the same Katalyst chain an orbit runs, written
> `master(Katalyst(k => ...))`. Section 2 is DONE (decisions (i) and (j)), section 5 is DONE (every Katalyst stage,
> `eq` included, works at the output). Sections 1, 3, 4, 6 and 7 stay open and are rewritten below to the current
> surface; where a section still says "master" it means the Katalyst at the output position.

## 1. Parameter parity audit — the principle, applied everywhere

**The rule (user, 2026-08-02):** *"The same params must be available in the sprudel DSL, Ignitor, Master etc. and they
need to mean the same thing. This is important for developer experience."*

The master DSL work proved this is not theoretical: `roomSize` silently meant a ~1 s tail on an orbit and ~12.5 s on the
master because sprudel divided by 10 and the master did not — and `roomFade`, the parameter that *actually* sets the
tail, existed on only one of them. Both are fixed for reverb/delay; nothing has audited the rest.

What an audit would cover, per effect: does the same concept have the same **name**, the same **scale**, the same
**availability** on every surface it could sensibly appear on (sprudel per-voice, sprudel per-orbit, Ignitor,
the Katalyst on an orbit and at the output; the Pipeline DSL retired in phase 3 step 9 and the Master DSL in step 12)?

Known asymmetries already spotted, as a starting list:

- ~~**`room` (orbit) vs `wet` (master)** — same thing, two words.~~ RESOLVED by C4.2
  (2026-08-24): the orbit knob is the `wet` slot of `room`/`delay` (2026-09-07; `roomWet`/`delayWet` between 2026-08-24 and then); both doors say `wet`.
- RESOLVED 2026-09-16 (KLANG-00208 (reverb naming unification)): `fade` is gone, `fade = x` is
  `size = 10x`. Was: **`room(size)` is ~0..10 but `room(fade)` is 0..1**, and `fade` silently makes `size` inert. Inherited from the
  old `room("a:b:c")` colon packing (removed in C0, semantics kept); documented rather than fixed, because redefining it
  would retune shipped songs.
- RESOLVED 2026-09-16: the master's `damp` is gone, `lowpass` (Hz) is the one damping knob on both doors, and the door
  word is `reverb` everywhere. Was: **`damp` is master-only**; sprudel reaches damping through `room(lowpass)` (Hz) instead.
- RESOLVED 2026-09-07: `cap` is the fourth slot of `delay(wet, time, feedback, cap)`. Was: **`delaycap`/`dcap` is not a `delay()` parameter** (the per-param C0 form covers amount/time/feedback only), while
  the reverb family exposes all five of its knobs.
- RESOLVED 2026-09-16: `roomDim` and `iResponse` (with the `iresponse`/`ir` doors) are gone end to end. Was:
  **`roomDim` / `iResponse`** are stored but never read on **both** paths (`Reverb.kt` TODO) — dead vocabulary that
  still appears in the DSL and docs.

**Superseded 2026-09-27 (phase 3 step 12 decision (a), C2):** `lookahead` is now a knob of the Katalyst `compressor`
and `limiter` doors and runs at ANY position; the orbit or playback it sits on runs late by it, the author's choice,
nothing compensates. It is still not a sprudel `compressor()` knob, because it is fixed per chain (it sizes a ring)
while a sprudel door writes slots on the running chain. The authored `limiter()` default stays at lookahead 0
(`AUTHORED_LIMITER_LOOKAHEAD_SECONDS`) against the house limiter's 5 ms; the `limiter` row of
`KlangScriptKatalystDoorParitySpec` pins its numbers. The recorded exception it replaces, kept for history:

**A DELIBERATE exception (until 2026-09-27):** `lookahead` exists on the master limiter **only**, not on
sprudel's `compressor()`, not per-orbit, not per-voice. A lookahead limiter delays the signal it protects; on the summed
master that delay is uniform and harmless, but on an orbit it would shift that orbit late against every other one — a
silent timing bug that reads as "my drums feel loose". Same reasoning one level up keeps the *authored*
the authored `limiter` stage at
`lookaheadSeconds = 0` while the house limiter runs at 5 ms: the house one is global and post-sum, the authored one is
per-playback. `MasterDefaultsSyncSpec` asserts both the shared values and the divergence. See
KLANG-00225 (master limiter lookahead) §3 and §4.

Wants its own task doc once someone starts it; this is the brief.

## 2. Engine disposal truncates a long delay tail (shared orbit + master)

> **DONE 2026-09-28** (phase 3 step 12 decisions (i) and (j)). After a stop, every tail that can end on its own rings
> out in full; only an endless tail (a delay at `|feedback| >= 1`, on an orbit or at the output,
> `KatalystChain.sustainsItself`) triggers the release, with whatever rings beside it (the engine's whole output, the
> simple rule in the `PlaybackEngine.isIdle` KDoc), 20 s after the last note (`PlaybackEngine.MAX_TAIL_HOLD_SECONDS`), by an
> exponential release (`TailRelease`, 60 dB per 3 s, retired under -90 dB), so there is no hard cut and no leaked
> engine. A chain swapped away drains for at most `ChainSwap.MAX_DRAIN_SECONDS` (20 s), then gets the same release.
> The text below is the original brief.

`PlaybackEngine.isIdle()` bounds the master tail hold at 20 s of silence, then disposes the engine **between two
samples** — a step, not a fade. A genuine (non-runaway) delay, e.g.
`time(2.0).feedback(0.85)`, can still be ~14 dB down at that point, so it clicks.

The orbit path has the *opposite* half of the same hole: a delay at `feedback >= 1.0` keeps
`cylinders.anyActive()` true forever, so `hasOwnSound()` never clears and the engine is never disposed at all — one
leaked engine per stop, and the drone survives deleting the pattern line.

Both are the same question ("when is a tail finished, and how do we stop rendering it politely"), so a shared fix is
likely better than two. Options for the master half: fade the engine out over the last N blocks before disposal; raise
the bound; or accept the step. The `cap` slot of `delay` and its doc example (`delay(feedback = 1.0).delay(cap = 2.0)`) actively
invite the orbit half.

## 3. Cache eviction can rebuild a master chain on the audio thread

Returning to a master last used more than `MAX_CACHED_CHAINS` (8) edits ago is a cache miss, and
`MasterBus.chainFor` rebuilds the `KatalystChain` when the request lands (inside the scheduler's block, on the audio
thread). Bounded and documented, but reachable by ordinary live-coding A/B ("was 2.0 better?").

**Updated 2026-09-28 (phase 3 step 12 C4):** a chain out of service holds no units (eager retire), so even a cache
HIT re-rents its reverb unit and delay ring at its first configure. The units come from the backend's shelves
(`ReverbUnits`, `SizedBuffers`), shared with the orbits; the render thread allocates only when the shelf has none
idle. What a miss still allocates on the audio thread is the chain's stage shells.

**Proper fix belongs to the resource warehouse pool** — already written up there, see
KLANG-00230 (resource warehouse) §"Master chains — the second customer".

## 4. Delete-to-undo for `master(...)`

`master == null` means "no change", so deleting the line leaves the last chain in place until the playback stops.
**Decided 2026-08-02: accept it**, with `master(Katalyst())` (the empty chain; `Master.default()` until 2026-09-28) as the named, discoverable way back: it swaps to the empty
chain through the normal crossfade and releases the engine properly.

Recorded here only because the underlying tension stays: the FE cannot distinguish "the code no longer contains
`master(...)`" from "this query chunk happens to contain none", and a sectioned song legitimately has cycles without a
master event. Revisit only if it bites in practice.

## 5. A master `eq` stage (`Master(m => m.eq(...))`), a gentle master-bus EQ (new, 2026-08-11)

> **DONE 2026-09-28** (phase 3 step 12 C5, decision (c)): the output runs a Katalyst chain, so the Katalyst `eq` stage
> works there: `master(Katalyst(k => k.eq(e => e.band(80, 0.707, -2)).gain(1.4).limiter()))`. Its builder is the ignitor
> `.eq`'s (`band(freq, q, db)`, `tap(freq, q, gain)`), one vocabulary on voice, orbit and output, and the stage order is the author's (the house limiter always comes last). No
> dedicated master shelf door was built. The text below is the original brief.

Wanted by KLANG-00143 (auto mix advisor) §3 (closed-loop master correction), and independently useful as
an authored mastering tool. Scope: **2–3 bands — low shelf, high shelf, optionally one mid bell** — deliberately not a
full parametric EQ.

- **Chain position (decided by mechanism, not taste):** before gain and both limiters — reverb → **eq** → gain →
  authored limiter → house limiter. The limiter's detector must see the corrected spectrum; that is what converts a
  low-shelf cut into loudness headroom (measured 2026-08-11 in the klang-ai sessions: −2 dB of low trim bought +1.3 dB
  RMS *and* reduced beat-rate pumping ~10 % at higher drive).
- **Parity (§1 applies):** shelf/bell EQ has no per-voice counterpart in sprudel today (`lpf`/`hpf`/`bandf` are filters,
  not gain shelves). If a per-orbit or per-voice shelf ever appears, names and units must match from day one — propose
  `eqLowDb/eqLowHz`, `eqHighDb/eqHighHz`, `eqMidDb/eqMidHz/eqMidQ` (dB gain + Hz corner, the industry-standard meaning)
  and record them in the parity table before shipping.
  **UPDATE 2026-08-20 — the per-voice counterpart NOW EXISTS**, so this is no longer hypothetical:
  the ignitor DSL ships `.eq(e => e.band(freq, q, db))` (serial bell, dB gain, `q` = the ordinary
  width scale, 0 dB transparent) and `.tap(freq, q, gain)` (parallel boost, LINEAR gain). Whoever
  implements the master `eq` stage must reconcile with those names and units rather than inventing a
  parallel vocabulary — in particular `db` means the same thing on both, and a master `q` should
  mean the same width it means on `.band()`. See also the (now settled, 2026-08-25) `freq`
  unification in KLANG-00153 (filter frequency param naming), which will touch these names.
- **Implementation:** stereo biquad shelves from the existing SVF/biquad infra; plain `var` params (no buffer sizing —
  unlike `lookaheadSeconds` there is no constructor-val constraint); wire model + KSP codec + `MasterDefaultsSyncSpec`
  -style assertion for defaults (all gains 0 dB = bit-transparent, so existing songs are untouched).

## 6. Small documentation / convention debts

- DONE 2026-10-19. The example itself was already gone: `d9b6c8e3` (2026-09-07) removed it with the `delaycap` door,
  and this line was only respelled then. The same claim lived on in prose ("at or above 1.0 ... with `cap` deciding how
  loud") on the Katalyst `delay` door (`KatalystStageDsl.Delay` in `audio_bridge/KatalystDsl.kt`; `scriptDelay` and
  the knob `scriptCap` in `klangscript-libs/.../stdlib/KatalystBuilders.kt`) and in `DelayLine.cap`'s KDoc. All now say
  what `DelayLine.processInternal` does (a ring cell is `cap · softCap((input + feedback · tap) / cap)`, untouched up to
  0.95 of the cap): at exactly 1.0 an echo keeps the level it went in at, and the cap sets the level only above 1.0,
  where the echoes grow until it holds them just under it. The sprudel door's text ("Ceiling the repeats may not
  exceed", "Above 1 builds up") was already right. Docs only, no test. Was: the `delay(cap)` KDoc example
  `delay(feedback = 1.0).delay(cap = 2.0) // endless echo, held at 2.0` is misleading: at exactly
  1.0 the cap never engages (the echo holds at the *input* level); the cap only sets the level once feedback > 1.
- RESOLVED 2026-09-07 (the `dcap` doors are gone, `delay(cap = ...)` is the one form). Was: several `dcap` overloads (top-level, `String.dcap`, both `PatternMapperFn` forms) lack
  `@category`/`@tags`/`@alias`, which `sprudel/ref/dsl-conventions.md` requires — so they will not surface in the
  effects category or in search.
- Freeverb's ~0.71 s tail floor (`FEEDBACK_OFFSET = 0.7`): nothing on either bus can go shorter. Worth stating in the
  user-facing docs, or revisiting the constant.

## 7. Greensleeves' authored `limiter()` never engages (found 2026-09-24, by-ear item)

(2026-09-28: the song's master is now `master(Katalyst(k => k.reverb(0.14, 6).gain(1.6).limiter()))`, the same
numbers through the Katalyst `limiter` door, so this item stands unchanged.)

Phase 3 step 3d(ii)'s engagement control perturbed the master limiter's default attack and expected
Greensleeves and ATruthWorthLyingFor to move; only ATruthWorthLyingFor did. Greensleeves peaks at about
-6.2 dBFS after its `gain(1.6)`, below the limiter's knee onset (threshold -1 dBFS minus half the 2 dB
knee), where `Compressor.gainReductionDb` is exactly 0: the stage is present and does nothing. Not a
defect (a ceiling that is never reached is a valid ceiling), but the song carries a stage its author
may believe is shaping it. The maintainer's call, by ear: keep it as a safety ceiling, drop it, or push
the song's gain into it.

## 8. The offline render does not count the master chain's latency (found 2026-10-10, review of the bus distort default)

`KlangOfflineRenderer` renders past the musical end by `KlangAudioRenderer.latencyFrames`, which is
`MasterStage.latencyFrames`, the house limiter's lookahead only. A per-playback `master(Katalyst(...))` chain that
delays (a compressor's lookahead, an oversampled `distort`) is not counted, so at `tailSec = 0` its last frames can
stay in the chain when the loop stops. Since the bus `distort` oversamples at 2x by default, any
`master(Katalyst(k => k.distort(...)))` adds 3 uncounted frames (about 0.06 ms at 48 kHz, barely audible). The fix:
add the master chain's `latencyFrames` to the render's run-out.

> **Investigated 2026-10-19, NOT built: the fix moves two corpus rows, the maintainer's call.** Every claim above holds
> (`KlangAudioRenderer.latencyFrames` is `MasterStage.latencyFrames`, the house limiter's 240 frames at 48 kHz;
> nothing reads `KatalystChain.latencyFrames` of the master outside the swap's alignment; the bus `distort` default
> is 2x, 3 frames). One nuance: the loop renders whole 128-frame blocks, so the real run-out is the bound rounded up
> to a block, and a short chain latency (the 3 frames) usually hides in that slack; a lookahead limiter's 240 does not.
>
> **The plainest fix (three getters, no new plumbing):** `MasterBus.latencyFrames` (the chain in service),
> `PlaybackEngine` passes it on, `KlangAudioRenderer.latencyFrames` adds it to the house limiter's, and
> `KlangOfflineRenderer` reads the bound EVERY block (`while (currentFrame < musicalAndTailFrames + renderer.latencyFrames)`)
> and returns the final bound as `totalFrames`. Read before the loop it would be 0: a master lands only when the
> scheduler consumes its event inside the render. The value that counts is the chain in service at the end: it holds
> the music's last frames. A chain swapped away earlier was fed silence after its 60 ms fade, so what it still rings
> is tail (`tailSec`'s job); only a fade that overlaps the very end leaves music in the leaving chain, and
> `max(current, leaving)` would cover that corner in one more line.
>
> **What it moves:** the corpus renders 256 cycles with a 2 s tail through this renderer and hashes every block. Der
> Schmetterling and Kokon (live songs, code unchanged) carry a 244-frame master: `bands` with the mids'
> `distort(0.10, "soft", 4)` (4 frames) and `limiter(lookahead = 0.005)` (240 frames). Their bound grows by 244 frames,
> which is 2 more blocks each (Der Schmetterling 22,438,144 to 22,438,400 frames, Kokon 36,960,256 to 36,960,512), so
> both hashes move and the corpus goes RED (engine-moved, needs `corpus-accept-engine`). Nothing else moves: no
> frozen text has a delaying master (two run reverb and gain, the frozen Stranger Things none), Greensleeves and A
> Synth Worth Lying For use `limiter()` at its authored lookahead 0, the other masters are reverb and gain, and the
> render tests' masters are gain only.
>
> **Options:** (a) build the fix above and accept the two rows with `corpus-accept-engine` (recommended: it is the
> correct run-out, and the two songs only gain 5.3 ms of tail); (b) count the chain latency only where the tail does
> not cover it (`max(tailFrames, chainLatency)`): the corpus stays, but the house and the chain latency are then
> counted two different ways; (c) leave it, as `tailSec = 0` with a delaying master is rare. A sibling, not in this
> item: an orbit chain that delays (a `katalyst(...)` with a lookahead or an oversampled `distort`) is not counted
> either.
>
> **Decided 2026-10-19 (maintainer): (a), "if it is easily done, do it"; scheduled after the release**, as it moves
> two corpus rows (accepted with `corpus-accept-engine` in the same change) and gets the usual review.
>
> **Built 2026-10-23**, the plainest fix as above: `MasterBus.latencyFrames`, `PlaybackEngine.masterLatencyFrames`,
> `KlangAudioRenderer.latencyFrames` adds it, the offline loop reads the bound after every block. Review round 1
> clean (coding and audio), the MINOR batch applied. Two corrections to the investigation: a master event at time 0
> is already in service before the first block (`scheduleVoice` promotes it), so the per-block read matters for a
> master later in the song; and the corner of a swap to a chain that delays less at the very end stays uncounted, named
> in the `MasterBus.latencyFrames` KDoc (a `max` with the leaving chain would also count a draining one, which holds
> only tail). The corpus moved exactly the two predicted rows (Kokon `5a81ae55b90e0e1e` to `05ce0ad31386d65a`, Der
> Schmetterling `7d7bb963fb81231a` to `639bb01e2a5554a2`, peaks unchanged). Both songs were edited since the last
> accept, so they read as information, not red, and a plain `corpus-accept` took them with their edits.
>
> **A sibling, open:** the live front end's latency budget (`JsAudioBackend`, the `MasterStageSpec` comment) leaves
> the master chain's latency out as well, so the code highlighting can run early by up to a master chain's lookahead
> (found by the review, 2026-10-23).

## Links

- Shipped plan + full review history: KLANG-00136 (master dsl)
- Foundation: KLANG-00176 (per playback engine) §H
- Next in the same family: KLANG-00236 (katalyst dsl) (built by 2026-09-20), which follows this application-path and effect-reuse
  precedent
- KLANG-00230 (resource warehouse) — owns item 3
- KLANG-00143 (auto mix advisor): wanted item 5 (the master `eq` stage, done 2026-09-28) for its closed-loop phase

## History

- 2026-10-20: numbered KLANG-00137 (was `docs/tasks/master-dsl-followups.md`).
- 2026-10-23: section 8 built (the offline run-out counts the master chain's latency).
- 2026-10-27: part of KLANG-00357 (Effects: the Katalyst and the master), the epic grouping.
