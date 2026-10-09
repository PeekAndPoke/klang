# A soloed voice stays protected for its whole life

> **DONE 2026-10-09** (branch `pitch-pipeline`, between pitch pipeline steps 3b and 4), in the batch of small decided
> items beside the pipeline. The record is the last section, "What was done".

Status before archiving: **decided (maintainer, 2026-10-09, Q14), queued beside the pitch pipeline.**

## What it is

The maintainer: "yes solo should protect the whole voice duration".

Today (`audio_be/.../voices/SoloTracker.kt`, since the solo fix,
[`20261009-bugfix-solo-rests-and-amount.md`](20261009-bugfix-solo-rests-and-amount.md))
a soloed source's protection ends 2 s after its last solo EVENT. A long release outlives that, and while another solo
is live the tail is ducked mid-ring:

```
note("c3").sound(myPad).release(5).solo()   // a pad with a long tail
s("bd*4").solo()
// today: the pad's tail is ducked from about 2 s after its last event (-23.6 to -40.8 dB at 4 s)
// decided: the pad rings at full level until its voice ends
```

## The work

- A solo source stays protected while any of its voices is still alive (sounding, releasing or fading), not only
  for the 2 s window after its last event. The window stays for what it is for: holding the background down between
  events of a live solo.
- No allocation per block: the tracker learns "source still has a live voice" from the voices it already walks, as
  `recordRealtimeSolo` does per block for realtime voices.
- Rows: the pad-and-kick example (the tail at full level until the voice ends, then the background released), a
  source whose voices all ended (protection ends then), and the realtime path. Mutation-checked.
- Proof: the corpus. Does any song solo a source with a release longer than 2 s beside another solo? If not,
  bit-identical; if so, that row moves as intended and gets a listening pair.

## Settled alongside (maintainer, 2026-10-09, Q12)

The solo ramps stay as they are (1.5 s in and out, a 2 s hold): "this is fine, solo is not meant to be a musical
thing, more for production use". `solo("<1 0>")` is not a supported toggle; a fast musical mute is a different tool.

## What was done (2026-10-09)

- **The rule as built:** protection follows the SOLOED VOICE. A voice started with a solo amount of its own (positive
  and finite) and a source id is protected for its whole life, sounding, releasing or fading. The source's window
  (2 s after its last solo event) still protects every voice of the source between events, soloed or not, and holds
  the background down while the solo is live. A voice without a solo of its own (`solo(0)`, a zero in an amount
  pattern, a held unsoloed key, a voice that started before its source was soloed) has the window alone, as before
  Q14.
- **How it got there (review, decided by the coordinator, logged for the maintainer as Q28):** the first build kept
  the source's tracker entry alive while ANY voice of it sounded, so `solo(1)` edited to `solo(0)` at the same call
  site, an amount pattern with zeros or a held unsoloed key never un-soloed a sounding source (reviewer B, round 1).
  The second kept the entry while a SOLOED voice of it rang, and an entry protects every voice of the source, so in
  Q28's own example the new `solo(0)` notes stayed at full level from 4.0 s until the old soloed tail ended, then
  dropped mid-note (round 2). Per voice, neither can happen.
- **The code:** `VoiceScheduler.ActiveVoice.soloed`, set once at activation from `VoiceData.solo` (NaN-guarded, as
  `SoloTracker.record` refuses the same values); the render loop protects a voice when it is soloed itself or its
  source's window protects it. `SoloTracker` is HEAD's, unchanged. Nothing new runs per block beyond reading one field.
- **Rows** (`VoiceSchedulerSoloCutSpec`): the pad-and-kick example (a soloed pad with a 5 s release beside a soloed kick
  plays at the level of the pad alone, block for block, until its voice ends); Q28's example (`solo(1)` on a
  `release(5)` pad, then the same pad with `solo(0)`: the `solo(0)` notes ducked from 4.5 s while the soloed pad rings
  unchanged to its end); a plain note of the source past the window is ducked; the realtime path (a released realtime
  solo's 5 s tail at full level beside the kick); `solo(1)` edited to `solo(0)`; `solo("<1 0 0 0>")`; a held realtime
  key with no solo of its own; a voice whose own amount is NaN (ducked past the window); a soloed voice with no source
  id, and one with an amount of +Inf (both ducked past the window). The row "a voice entering or
  leaving protection ramps over one block" keeps HEAD's text: its lead carries no solo and leaves protection at 5.0 s.
- **Mutation-checked:** protection per source again while a soloed voice rings (Q28's row red); the NaN guard dropped
  (the NaN row red); the source-id clause dropped, and the finite clause dropped (the guard row red, each on its own
  voice); the per-voice protection removed (the pad-and-kick, Q28 and realtime-tail rows red); in round 1
  `keepForVoice` for every voice (the narrowing rows red) and an amount of 0 counted as soloed (the zero rows red).
- **The corpus:** no song solos anything (every `solo()` in the built-in and frozen songs is commented out), so it is
  bit-identical, with no listening pair: `tmp/naming/corpus-small-3c.txt`, 17 of 17 identical to
  `corpus-pp-s3b-fm2`, Kokon from HEAD's text `6d22988a5668b97f`. It never reaches a soloed voice, so it shows only
  that nothing else moved; the proof of the change is the rows.
