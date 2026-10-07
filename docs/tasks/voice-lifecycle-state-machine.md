# A voice's lifecycle is one state machine inside the voice

Status: **in progress.** Planned 2026-10-07 (maintainer); steps 0 and 1 done 2026-10-07, step 2 done 2026-10-07, step 3 done 2026-10-07, step 4 done 2026-10-07 (awaiting review).

## Why (maintainer, 2026-10-07)

A voice's lifecycle is spread over several layers today: the voice, copies in two contexts, the scheduler and the
orbit lease. That spread leads to bugs that are hard to find, for example pruning and cut getting in each other's
way. The goal: **a state machine inside each `Voice`**. The voice decides from its state how it renders. Terminal
states cannot be left: a culled zombie never sounds again, and a cut voice never comes back.

Rules for the work:

- **Stable implementation first, optimisation only after it, and only if it is needed.** An optimisation that
  falls out of the new structure on its own is taken right away.
- **Everything lives inside the voice; no redundant data.** A copy may come back later for performance, if a
  measurement asks for it (at most about 100 voices play at once, so the state machine costs next to nothing per
  block).
- **Bit-identity is the proof.** Every step that is not meant to change the sound renders the 18-song corpus
  bit-identical to the step before, and the `audio_be` suites stay green.

## Where the lifecycle lived before step 1 (inventory, 2026-10-07)

A snapshot of `main` at the start (line numbers of that tree). Each step changes it: read the code, not these line
numbers, and see each step's "What was done" for what moved. Step 1 replaced the `culled` field by the state.

On the `Voice` (`audio_be/.../voices/Voice.kt`):

| State | Line | Written by | Meaning |
|---|---|---|---|
| `startFrame` | `:53` | factory | onset; `render` returns early before it (`:212`) |
| `gateEndFrame` | `:103` | factory, `releaseGate` | sounding before it, releasing after |
| `endFrame` | `:99` | factory, `releaseGate` | death frame; `render` returns `false` from it (`:211`) |
| `heard` | `:134` | `render` | latch: the voice has been audible once |
| `silentFrames` | `:149` | `render` | silent frames counted in the release |
| `culled` | `:120` | `render` | zombie: renders no stage, renews its orbit lease until `endFrame` |
| `_gainMultiplier` | `:154` | the scheduler, every block | solo/mute gain (an input, not lifecycle) |

Copies of the same limits, kept in sync by hand in `releaseGate` (`Voice.kt:189-198`; forgetting one was a bug
once, "amendment A1"):

- `BlockContext.endFrame`, `BlockContext.gateEndFrame` (`voices/strip/BlockContext.kt:57`, `:63`), absolute
- `IgniteContext.gateEndFrame` (`ignitor/IgniteContext.kt:37`), voice-relative `Int`

In the scheduler (`voices/VoiceScheduler.kt`):

| Part | Line | What it does |
|---|---|---|
| `active` | `:91` | membership is "alive" |
| natural death | `:493-501` | `render` returned `false`: swap with the last, remove |
| cut | `:632-641` | `iterator.remove()`: instant death decided outside the voice, a click; removing mid-list also reorders the orbit lease succession |
| realtime note-off | `:411` | `releaseRealtimeVoice` calls `voice.releaseGate` |
| stop | `:264` | `cleanup` releases held realtime voices only; the rest rings out |
| hard kill | `:285`, `:184` | `cleanupHard` (end of the warmup handshake), `clear` (no caller; deleted in step 3) |
| re-send dedup | `:323` | a playing voice (a zombie too) absorbs an identical incoming one |
| `ActiveVoice.origin` | `:82` | timeline or realtime, `held`: lifecycle facts kept beside the voice |
| culled count | `:487-491` | reads `culled` before and after `render` |

In the orbit: the lease is renewed from two paths, `SendRenderer.kt:30` (a sounding voice) and `Voice.kt:216`
(a zombie). That is why a zombie stays in the list until `endFrame`, and why a zombie keeps owning its orbit's
bus settings until then.

In the engine (`PlaybackEngine`): `stopped`, `isReleasing`, `TailRelease`. The end of a whole playback is
engine-wide, not per voice, and stays where it is.

## The states

```
Pending ──onset──▶ Sounding ──gate end / note-off──▶ Releasing ──endFrame──▶ Done
                      │                                  │
                      └──────── cut / takeover ──────────┴──▶ Fading ──fade end──▶ Done
                                                         │
                                          silent for the cull window
                                                         ▼
                                                      Zombie ──endFrame / cut / takeover──▶ Done

Any state ──endFrame reached (a release of 0, a negative release), or a hard kill──▶ Done
Pending ──cut / takeover──▶ Done (it has not sounded; promotion runs one block ahead, so a cut can reach it)
```

- `Zombie`, `Fading` and `Done` are terminal: nothing leads back to a sounding state. A cut, takeover or hard kill
  on a silent voice (`Pending`, `Zombie`) needs no fade and sends it straight to `Done`: that keeps today's orbit
  hand-over (today's cut removes zombies too, so the cutting voice takes the orbit after one block; a zombie that
  ignored the cut would keep refusing the new voice's bus settings for its whole remaining release, round-1
  review of step 1). Culling does not run in `Fading`, and a note-off in `Fading` or `Zombie` is ignored.
- `render` dispatches on the state: `Pending` returns early, `Sounding` / `Releasing` run the pipeline, `Fading`
  runs it with the fade ramp, and `Zombie` only renews the lease.
- Culling measures where it measures today: until the voice has been heard, and in `Releasing`. Never in
  `Sounding` once heard (the gate is the held part of a note and may be silent on purpose), and never in
  `Fading` or `Zombie`. That is the organic saving: the states that do not need the measurement skip it.

## Who drives which transition (maintainer, 2026-10-07)

- **Inside the voice:** every time-driven transition (onset, gate end, `endFrame`, fade end) and becoming a
  zombie. The thresholds come in as parameters (the cull window per voice, the floor a constant).
- **From outside, the scheduler:** note-off, cut, takeover, hard kill. The scheduler decides WHO (the group, the
  same-onset rule); it sends an event, and the voice decides by its state whether the event applies and WHAT
  happens.
- **Every voice ends in `Done`**, hard kill included. The scheduler asks each voice once per block whether it is
  done and removes the done ones. No removal anywhere else.

## Steps

Each step: one commit after the review loop, mutation-checked at the engine tier, and the corpus rendered
before and after.

0. **Kokon's ending (a measurement, no code). Done 2026-10-07: not the engine.** The last chord's release is
   LINEAR (`makeGuitar`: `e.curves("linear", "linear", "linear")`, 8 s): the first 6.8 s lose 10 dB, the last
   second loses about 30 dB, and the rig after the envelope holds the upper part up, so the fall bunches at the
   end. No voice ends audibly (each is 35 to 38 dB under its peak at its teardown window; keeping the voices 2 s
   longer changes the output by at most 1.1e-5), no orbit or master stage cuts, and the browser never stops the
   song (it loops after 6 s of silence). A "cube" release makes the fade steady. A song decision by ear (the
   curve is set for every guitar in `makeGuitar`). Report and WAVs: `tmp/reviews/kokon-end-report.md`,
   `tmp/kokon-end/`.
1. **The state, read-only.** Add the state and derive it from today's fields; `render` dispatches on it.
   Bit-identical.

   **What was done (2026-10-07).** `Voice.State`, a plain enum (`Pending`, `Sounding`, `Releasing`, `Zombie`,
   `Done`; a closed param-less set, no allocation per block), held in `Voice.state`. `render` calls `advance`
   (the time-driven transitions at the block's start: `Done` from the first block that starts at or after
   `endFrame`, from any state; `Pending` to `Sounding` on the first block that ends after `startFrame`; `Sounding`
   to `Releasing` on the first block that starts at or after `gateEndFrame`, in the same call when the first
   rendered block already lies past the gate) and then dispatches with an exhaustive `when`. `renderStages` runs
   the pipeline for `Sounding` and `Releasing` and moves a `Releasing` voice to `Zombie` at the block's end, the
   culling decision unchanged: measure while `!heard` or `Releasing`, count only in `Releasing`. The `culled`
   field is gone: `Voice.culled` reads `state == Zombie`, so it is false again once the zombie is `Done` (the
   scheduler's culled count reads it around one `render`, which is unaffected; `VoiceCullingSpec` now records the
   cull while it renders). `releaseGate` returns at once on a `Zombie` or `Done` voice: through the scheduler
   that is equivalent (a zombie's gate lies before the cursor the scheduler releases at, so the natural-gate
   check returned; a `Done` voice has left the active list); only a direct call with an earlier frame differs.
   Not equivalent only off the engine's path: a voice rendered at a block EARLIER than one it already rendered
   (time going backwards) keeps its later state; the scheduler's cursor only moves forward. Proof:
   `VoiceLifecycleSpec` (9 rows, 18 mutations all red), the `audio_be` JVM and browser suites, `:klang:jvmTest`,
   root `:jvmTest`; a JVM micro-benchmark (old and new interleaved) put the per-call cost of a sounding,
   a zombie and a pending block inside the run-to-run spread. The corpus render is the coordinator's.
   Report: `tmp/reviews/vl-step1-report.md`.
2. **One writer for the time limits.** The state machine owns gate end, end frame and (later) fade start, and the
   contexts read them from the voice instead of keeping copies (or, if a hot path needs a copy, one place pushes
   it). Remove the redundant fields. Bit-identical.

   **What was done (2026-10-07).** One home: `VoiceLimits` (`voices/VoiceLimits.kt`: `startFrame`, `gateEndFrame`,
   `endFrame`; the fade start joins it in step 4). The factory builds one instance into the `BlockContext`
   (`BlockContext.limits`, by reference); the `Voice` takes it from there, reads it through `startFrame` /
   `endFrame` getters and is its only production writer (`releaseGate`: two writes, nothing else). Gone:
   `BlockContext.startFrame`, `.endFrame`, `.gateEndFrame`, `BlockContext.signalCtx` (it existed only for the A1
   write), `BlockContext.signal` and `.freqHz` (read by nothing), `IgniteContext.releaseFrames` (read by nothing,
   and wrong after a note-off on a negative release), the `Voice` constructor's three frame parameters, and the
   `startFrame` parameters of `IgniteRenderer`, `FmRenderer`, `PitchEnvelopeRenderer` and `AccelerateRenderer`
   (they read the onset from the limits, so onset and gate cannot disagree). Readers: `TeardownFadeRenderer` (end,
   onset), `PitchEnvelopeRenderer`, `FmRenderer` (onset, gate), `AccelerateRenderer` (onset), the voice itself. `IgniteContext.gateEndFrame` (voice-relative `Int`, read by `AdsrIgnitor`, the filter envelopes and the
   Ignitor pitch envelope) stays a field, as a per-block INPUT like `voiceElapsedFrames`: the ignite stage derives
   it from the limits before every `generate` (one subtraction per block), nothing else writes it inside a voice,
   and a context used without a voice (about 96 constructions outside production: specs and benchmarks) keeps
   setting it at construction. No copy was kept for speed: the interleaved JVM benchmark shows no cost. Baked on
   purpose, because a note-off must not move it: the `accelerate` glide span (`AccelerateRenderer.totalFrames`, the
   scheduled end minus the onset) and `IgniteContext.voiceDurationFrames`; neither is a limit. Proof: `VoiceLifecycleSpec` gained a row where a note-off renders bit for bit what a voice scheduled
   with that gate renders, through the tree's own envelope, the pitch envelope, FM, the state and the end; six
   mutations (no per-block gate derivation, pitch envelope or FM keeping the first gate, `releaseGate` not moving
   the gate or the end, the teardown fade reading a held-horizon end) all red, plus the derived gate off by one,
   caught by two literal assertions on the gate the ignitors see. The last one first survived:
   `RealtimeVoiceSpec`'s teardown-fade note-off row rendered 12 blocks and never reached the death it is about; it
   renders 30 now. Report: `tmp/reviews/vl-step2-report.md`.
3. **Events from outside.** Note-off and hard kill become events on the voice; every voice ends in `Done`; the
   scheduler removes only done voices. Decide whether `held` / `liveId` move onto the voice or stay as the
   scheduler's provenance. Bit-identical.

   **What was done (2026-10-07).** Two events on the voice, each deciding by the state whether it applies. The
   note-off (`Voice.releaseGate`) applies to `Pending` and `Sounding` only; `Releasing` joined `Zombie` and `Done`
   in ignoring it (through the scheduler it already did nothing there: such a voice rendered a block starting at
   or after its gate, and the scheduler releases at its later cursor, so the natural-gate check returned). The
   hard kill (`Voice.kill`) sends any state to `Done`. The scheduler writes no voice state and no limit (F2):
   `cleanupHard` kills, then `removeDoneVoices` removes the `Done` voices order-preserving (`removeAll`, what
   `cleanupHard` did before); the render loop removes a voice whose `render` returned false (`Done`) by
   swap-with-last, as before. `VoiceScheduler.clear` had no caller anywhere in the repo and is deleted.
   **A deliberate deviation from "in one place": two removal paths.** A killed voice must leave `active` at once,
   because `cleanupHard` is followed by the engine's disposal and no render comes that could remove it; and
   folding the render loop's swap-with-last into a sweep after the loop would change the render order within a
   block, so no single path keeps today's renders bit-identical. (The order the sweep keeps is not observable in
   production: each playback has its own scheduler, `engineFor(playbackId)`, and `cleanupHard` kills every voice
   in it.) Equivalence: between blocks no voice is `Done` except the killed ones (the render loop removes each at
   once), so `cleanupHard` removes the same voices in the same order. The cut (`activateVoice`) still removes
   without the event: the seam step 4 closes. `held` / `liveId` / `Timeline.source` stay in the scheduler
   (`ActiveVoice.origin`): they answer WHO an event goes to (a stop by `liveId`, `cleanup`'s held voices, the
   re-send dedup), which the plan gives the scheduler; the voice needs none of them to decide WHAT happens (a
   held voice is a voice whose gate lies at the horizon). Proof: `VoiceLifecycleSpec` (a kill from every state,
   a note-off on every state), `VoiceSchedulerHardKillSpec` (the sweep after `cleanupHard`; renamed `VoiceSchedulerRemovalSpec` in step 4); 8 mutations all red
   (S7 tested `clear`, deleted since); the step 1 campaign re-run with the fixed runner (M1 to M18 and the `SampleInstrumentSpec` pair, all
   red). Report: `tmp/reviews/vl-step3-report.md`.

   **Forward note F2 (step 2 review, 2026-10-07).** The single writer of `VoiceLimits` is a convention, not a
   compiler guarantee: its setters are `internal`, so anything in `audio_be` could write them. Every event, the
   cut included, writes through a `Voice` method, never from the scheduler.
4. **Cut becomes `Fading`** with the house teardown fade length (4 ms) instead of `iterator.remove()`. The voice
   applies the ramp itself, from a fade-start FRAME (a cut lands mid-block): it cannot rely on
   `TeardownFadeRenderer`, which `VoiceFactory.treeStages` leaves out when the tree ends in its own envelope. A sound change by
   design (no click), but no song uses cut, so the corpus stays bit-identical; `VoiceSchedulerSoloCutSpec`'s rows
   change on purpose. The open questions of `future/cut-group-semantics.md` (`cut(0)`, the reach of a group) are
   answered by the maintainer before this step or with it.

   **Forward note F1 (step 2 review, 2026-10-07).** A `Fading` voice must not end by moving `limits.endFrame`:
   `TeardownFadeRenderer` reads that field, so a voice without its own envelope would get two fades multiplied, and
   a young voice would get the shortened midpoint window. Use a separate fade-start frame and fade end, or skip
   the teardown stage while `Fading`.

   **Forward notes from the step 3 review (2026-10-07).**
   - **F3.** A cut that sends a `Pending` or `Zombie` voice straight to `Done` must be removed by the order-keeping
     sweep (`removeDoneVoices()`), not left for the render loop's swap-with-last, or a different voice takes the
     orbit.
   - **F4.** A `Fading` voice holds the lease about 4 ms longer than today's cut, then leaves by swap-with-last,
     while today's cut removes it at once and keeps the order. So the owner of a shared orbit after a cut
     changes; expect `VoiceSchedulerSoloCutSpec` and lease-order rows to move. A deliberate step 4 change.
   - **L3.** One scheduler serves exactly one playback in production (`engineFor(playbackId)`), so a cut group
     reaches at most the whole playback. The open `cut-group-semantics.md` question is "the whole playback or one
     orbit".

   **What was done (2026-10-07).** The cut-group SEMANTICS are unchanged (maintainer: continue without their
   judgement): who is cut is today's rule (`cut(0)` an ordinary group, the reach the whole playback);
   `cut-group-semantics.md` stays open except question 3, answered: it fades. Only the hard removal changed.
   - **The event.** `Voice.cutOff(fadeStartFrame)` (the property `cut` is the group, so the event is `cutOff`).
     `Pending` and `Zombie` go to `Done` at once; `Sounding` and `Releasing` go to `Fading`; `Fading` and `Done`
     ignore it. The scheduler's sweep in `activateVoice` still runs before the new voice exists (it never cuts
     itself), sends `cutOff` to every voice of the group, then `removeDoneVoices()` (F3).
   - **The fade start** is the cutting voice's onset frame, from `VoiceFactory.onsetFrame`, the one formula
     `makeVoice` uses too, so the sweep needs no built voice (a cutting voice that cannot be built still cuts,
     as before).
   - **The fade.** `CUT_FADE_SECONDS = 0.004` (`audio_bridge/.../constants/EnvelopeDefaults.kt`), linear to exact
     zero (the teardown law), applied by the voice between its stages and its send (`Voice.applyCutFade`), so the
     orbit sends fade too. The fade window lives in `VoiceLimits` (`fadeStartFrame`, `fadeEndFrame`, +Infinity
     until cut), written only by `cutOff`. `endFrame` does not move (F1): `advance` ends the voice at the first
     block that starts at or after the fade end or `endFrame`, whichever is first. A voice whose own end falls
     inside the fade ends at its end; where it has a teardown fade, that stays at its own end and multiplies
     with the cut ramp in the overlap (both continuous, so the product is click-free; skipping the teardown
     while `Fading` would put a step at the cut when the cut lands inside the teardown window). The smoothstep
     of `docs/tasks/voice-takeover.md` is left for `takeover`.
   - **`Fading` in the machine.** The pipeline runs, the ramp, then the send; no cull measurement; a note-off is
     ignored; `kill()` ends it. It renews the orbit lease while it renders and leaves by swap-with-last (F4: the
     orbit owner after a cut changes on purpose).
   - **Not equivalent, by design:** a cut voice keeps sounding (fading) for 4 ms and holds its slot and lease
     until the fade has ended. No shipped song uses cut, so the corpus is bit-identical.
   - **Proof.** `VoiceLifecycleSpec` (the ramp from a mid-block onset to exact zero and `Done` after it, no step
     larger than the slope, the sends fade, `Pending` and `Zombie` to `Done`, a second cut changes nothing, a
     note-off in `Fading` is ignored, an end inside the fade ends at the end with the teardown unmoved, a kill
     from `Fading`); `VoiceSchedulerSoloCutSpec` (the four cut rows rewritten to the fade, plus the fade starting
     at the cutting onset mid-block through the scheduler); `VoiceSchedulerRemovalSpec` (a cut zombie leaves by
     the sweep, the order kept). 16 mutations: 15 red; one equivalent (measuring the cull peak in `Fading` has no
     observable effect, only cost, since culling counts only in `Releasing`). Report: `tmp/reviews/vl-step4-report.md`.
   - **Round 1 (2026-10-07).** The ramp's exact zero sits on the last frame that renders, `ceil(fadeEnd) - 1`, as
     the teardown puts its zero on `floor(endFrame) - 1` (the ramp spans 191 steps at 48 kHz; the voice still ends
     at the first block at or after the fade end). Before, a fade end on a block's last frame left one ramp step
     (1/192) as the last value. A non-finite start is dropped at promotion like a late voice and counted
     (`if (!(absoluteStartSec >= nowSec))`, which also closes the old leak of such a voice staying `Pending` for
     ever), and `cutOff` sends a non-finite fade start straight to `Done`. A NaN rpm would pass
     `KlangPatternScheduler.updateRpm`'s `coerceAtLeast` and reach the start times; no producer was found (the UI
     guards `newRpm > 0.0`, the songs set literals), so it is reported, not fixed here.
   - **Decided: a `Fading` voice ends in `Done` at the fade end** (coordinator, 2026-10-07, under the maintainer's
     "continue unless you need my judgement"). It keeps today's orbit hand-over: the cut victim gives up its lease
     once its fade ends. Revisit with step 5's lease rule.
   - **By ear, for the maintainer: the curve.** Linear stays. Reviewer B measured smoothstep (`1 - p*p*(3 - 2p)`)
     at the same 4 ms on the worst case (a full-scale low sine cut by a low sine): linear leaves 1-2 kHz at -59.8 dB
     and 2-4 kHz at -68.7 dB, 5 to 11 dB above the new note's own onset; smoothstep gives -73.4 and -88.9, below
     the new onset in every band above 500 Hz, at the price of 3.8 dB more at 250-500 Hz and a law that differs
     from the teardown's. Audible only on pure low tones in a quiet room; a one-line law swap if wanted. WAVs:
     `tmp/cut-fade/` (`sine60-fullscale-*`, `sine110-pluck-*`), notes in `tmp/reviews/vl4-r1-B.md`.
5. **The lease per state (maintainer decision, by ear).** Which states hold the orbit lease. Today a zombie holds
   it until `endFrame`; restricting it changes which voice owns a shared orbit (the culling design avoided that on
   purpose, measured on Der Schmetterling 2026-09-15). Corpus render plus listening.

   **Input from step 4 (reviewer B, 2026-10-07): the F4 owner change, measured.** Three voices on orbit 0: V1
   (cut 1, reverb wet 0) owns it, a pad V2 (no cut, reverb wet 0.8) is refused, and a run of plucks (cut 1, dry)
   every 0.25 s cuts V1 and then each other. Before step 4 the removal kept the order, so the pad took the lease
   and the whole orbit played with reverb 0.8; since step 4 the faded owner leaves by swap-with-last, the newest
   pluck takes its slot and the lease, and the orbit stays dry for the run. Difference signal -13 dB against the
   mix over 2.5 s (all of it reverb). Neither is "right" under first-writer-wins; the new one is what a natural
   death already does. WAVs: `tmp/cut-fade/shared-orbit-lease-*`.
6. **Then, as their own tasks:** `takeover` (the `Fading` event with its own time) and `glide` (a value the new
   voice gets at its onset), `docs/tasks/voice-takeover.md`.
7. **Optimisation, only if measured to be needed.**

## Open decisions

- ~~Step 4: does a `Fading` voice end in `Done` at the fade end, or in `Zombie` until its old `endFrame`?~~
  Decided 2026-10-07 by the coordinator under the maintainer's "continue unless you need my judgement": `Done` at
  the fade end (it keeps today's orbit hand-over). Revisit at step 5 with the lease rule.
- Step 4, by ear: linear or smoothstep for the cut fade (see step 4's notes).
- Step 5: the lease rule per state.
- `cut-group-semantics.md`: `cut(0)`, and whether a group reaches the whole playback or one orbit.
