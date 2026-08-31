# Pluck / SuperPluck — who owns the string's ring-out?

> **Status (2026-08-27): FOLLOWUP, deliberately not started.** Split out of the envelope-ownership
> task so it survives that task being closed, which it now has: the parent shipped and was archived
> on 2026-08-31 as
> [`20260831-ignitor-envelope-ownership.md`](../tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md).
> Priority: **SHOULD** — not release-gating, but it blocks credible physical-modelling
> instruments.
>
> ⚠️ **No quick shot here** (maintainer, 2026-08-27). This needs the same depth the envelope-ownership
> design got: the arithmetic below rules out every obvious answer, and each remaining option is a
> policy decision, not an implementation detail.

## Why this exists

The envelope-ownership work establishes that a voice's tail is **accumulated during the ignitor build**:
each component with a release tail contributes its already-resolved value, and `maxReleaseSec()` is
deleted. The maintainer's judgement at the time was that ADSR is *the* thing that shapes note duration
and release tail, so no `CarriesReleaseTail` abstraction is needed — a fool-me-twice situation.

**`Pluck` / `SuperPluck` are the second implementer, and they already exist.** They ring on after the
note through their own physics, with **no `Adsr` node anywhere in the tree**
(`audio_bridge/IgnitorDsl.kt:643`). So the accumulator reports a zero tail for them.

## The arithmetic that makes this hard

`Pluck.decay` is **not a duration**. `decayDefault = ConstantIgnitor(0.996)`
(`audio_be/ignitor/Ignitors.kt:59`), and it is applied inside the Karplus-Strong feedback loop as

```kotlin
delayLine[writePos] = (filtered * decayVal)      // Ignitors.kt, KarplusStrongIgnitor
```

— once per **delay-line pass**, i.e. once per period of the note. Amplitude after `t` seconds is
therefore `decay^(t·f)`, and the time to fall 60 dB is

```
t60 = ln(0.001) / (f · ln(decay))     ≈  1723 / f  seconds   at the default decay = 0.996
```

| note | frequency | ring to −60 dB |
|---|---|---|
| E2 | 82 Hz | **21.0 s** |
| A4 | 440 Hz | 3.9 s |
| C6 | 1046 Hz | 1.6 s |

Two consequences that kill the easy answers:

1. **The tail is frequency-dependent**, so any `tailSeconds()` query needs the note frequency — it is
   not a property of the patch alone.
2. **The tail can be enormous.** Honouring 21-second voices at E2 would wreck polyphony and CPU. So a
   correct answer necessarily includes a cap, and a cap is a musical decision (where do you mute a
   ringing string?), not a safety detail.

## The reframe that matters

Today's voice envelope is **not merely masking this** — it is **load-bearing**. It is the only thing
preventing 21-second pluck voices. Any change here removes a limiter that is currently doing real work.

This also means the current `.adsrOff()`-on-a-pluck behaviour (accumulator reports 0 → the string is
cut at note-off) **is defensible rather than known-wrong**: a plucked string's decay happens *during*
the note, and note-off is a palm mute. Muting a real string with your hand is exactly what a player
does. The alternative — unbounded ring — is arguably the less musical default.

Note that `.adsrOff()` on a pluck is the first thing someone reaching for physical modelling will try,
so whichever behaviour ships needs to be documented in the pluck reference, not just decided.

## The four decisions this needs

Not an interface — a policy. In rough dependency order:

1. **Threshold.** What counts as "the tail ended"? T60 is the textbook figure but is inaudible under a
   dense mix and generous under a solo. T40 at 82 Hz is 14 s; T20 is 7 s. Absolute-level vs
   relative-to-voice-peak matters here too (a quiet pluck's T60 is already below the noise floor).
2. **Frequency threading.** `freqHz` is known at voice build (`createExciter(sound, data, freqHz, …)`),
   so it can reach the accumulator — but this makes the contributed tail note-dependent, which is a
   change in kind from an ADSR's fixed release.
3. **Cap policy.** Maximum ring-out after note-off, and what happens at the cap — a forced fade (needs
   a fade length) or a hard stop (needs the declick, which at 1 ms is too short for an 82 Hz carrier;
   see the fade-guard discussion in the parent task).
4. **Whether the abstraction is worth it.** With decisions 1–3 made, is `CarriesReleaseTail` actually
   the right shape? An ADSR contributes a *fixed* release; a pluck contributes a *computed,
   frequency-dependent, capped* one. If the two are that different, two contribution sites may be
   honest and one interface may be a lie.

## Also affected

- **`SuperPluck`** — N detuned strings summed; the longest string's tail governs, and detune spread
  shifts frequencies slightly. Same decisions, one extra max.
- **`brightness`** is a lowpass inside the loop, so *harmonics* die faster than the fundamental. The
  t60 above is the fundamental's — which is the right quantity for "is the voice still audible", but
  worth knowing when the by-ear result disagrees with the arithmetic.
- Any future self-decaying generator (physical models, resonators) inherits this whole problem, which
  is the argument *for* eventually having the abstraction.

## Links

- Parent (archived, shipped):
  [`20260831-ignitor-envelope-ownership.md`](../tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md)
  — the build-accumulator design, the six-case ownership table, `.adsrOff()` semantics, the fade guard.
- Code: `audio_bridge/IgnitorDsl.kt:643` (`Pluck` node), `audio_be/ignitor/Ignitors.kt:59`
  (`decayDefault`), `Ignitors.kt:1176+` (`karplusStrong` / `KarplusStrongIgnitor`), `Ignitors.kt:1282+`
  (`superKarplusStrong`).
- Prior art in this repo for "express time relative to the note rather than in absolute units": the
  harmonic-relative string filter and the period-scaled minimum release, both in the parent task.
  The latter is now carried forward on its own in `docs/tasks/future/envelope-shape-followups.md`.
