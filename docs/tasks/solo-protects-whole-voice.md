# A soloed voice stays protected for its whole life

Status: **decided (maintainer, 2026-10-09, Q14), queued beside the pitch pipeline; not started.**

## What it is

The maintainer: "yes solo should protect the whole voice duration".

Today (`audio_be/.../voices/SoloTracker.kt`, since the solo fix,
[`20261009-bugfix-solo-rests-and-amount.md`](../tasks-archive/2026-10/20261009-bugfix-solo-rests-and-amount.md))
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
