# KLANG-00345: Smoothing as a stream operator: Stream.smooth() in the workshop, the Motor's gauges on it

> **Status (2026-10-26): DONE.** Ultra 0.116.0 carries smooth(); the workshop copy is gone (53c0053b)
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The Motor's needles glide towards their readings (KLANG-00344). That smoothing lived inside `KlangGauge`, a view,
with its own frame loop. Smoothing shapes data, not looks: as a stream operator it is reusable (the knob, an LCD, the
spectrum's columns, the start page's dyno run), testable with a clock driven by hand, and the gauge only draws.

## Decide before implementing

- **In the workshop first, JS only** (the maintainer: "Make it a js operator only first, unless we already have
  "timing" functionalities multiplatform"). The browser's animation frames are the clock for a needle; Ultra's
  `ticker` (coroutines, a monotonic clock) is multiplatform but not in step with the screen.
- **`FrameClock`** is the operator's clock, a parameter: the browser's animation frames by default, a hand-driven one
  in the spec. The maintainer: "i like the FrameClock approach... this would be the glue to make these operator
  multi-platform. So when we hand this over to ultra, ask it to check how the TickerStream currently operates under
  the hood."
- **The law today is exponential**: each 10 ms keeps a share of the way left (0.933 for the Motor), sized by the time
  between frames. Other laws (linear, attack and release, spring) were discussed with the maintainer; the seam for
  them (a pure step function as a parameter) comes with the second law, not before.
- **The laws go to Ultra** (the maintainer, 2026-10-26: "Ok i like the smoothing. We keep .smooth() as it currently is
  default to exponential smoothing while having an overload for the other smoothings. We do not need to do this here.
  Ultra has to put the Smoothing into the ultra/math package next to Ease. and then fully implement all variants and
  use them in .smooth()"). So here `smooth()` stays exponential only; Ultra builds `Smoothing` (exponential, linear,
  attack and release, spring) in its math package beside `Ease`, the overload `smooth(smoothing)`, and the
  multiplatform clock, after checking how its `ticker` works under the hood.
- **Offline passes at once**: `null`, a NaN and an infinity give `null`; after it the motion starts from `rest`.
- **`KlangGauge` draws what it gets**: its own smoothing and frame loop go; hot, the needle and the number follow the
  (smoothed) value. So does the meter screen readers hear: the gauge knows only the smoothed value now (round 2 of the
  review; accepted, a meter that settles within a second).

## The work

1. `workshop/streams`: `FrameClock`, `Stream<Double?>.smooth()`, `SmoothSpec` with a hand-driven clock.
2. `PlayerMiniStats`: its three readings as smoothed streams.
3. `KlangGauge` without smoothing; its spec's smoothing rows move to `SmoothSpec`.
4. Review, mutation checks; then the hand-over to Ultra (Ultra-Main-Dev): the operator, its clock and spec, the
   maintainer's decisions on `Smoothing` and on `FrameClock` and `ticker`. Once Ultra ships it, Klang takes it from
   Ultra and the workshop's copy goes.

## Other places that smooth by hand (the scan, 2026-10-26)

- `StartPage`'s dyno run (`RoundGauge`, its own 16 ms ticker): with the step that moves the Motor into its corner.
- `Spectrumeter`'s glow (`glowHeat`): a leaky accumulator, a law of its own.
- `Spectrumeter`'s columns: raw today; "rise fast, fall slowly" would be an attack and release law.
- `MotorBackground`'s lamp: eases its intensity to a target (with a flicker on top).

## Acceptance

- The Motor's needles glide as before, now through `smooth()`; the gauge has no smoothing of its own.
- `SmoothSpec` green and mutation-checked; the review loop ends clean.

## Review and checks (2026-10-26)

- **Round 1** (level 1, blind): 1 MAJOR, 2 MINOR, 3 NIT, all fixed. The MAJOR: the needles rose from 0 whenever the
  Motor was shown with a song playing (the operator read its not yet subscribed source at construction); now the
  first value of a listening is taken as it is (ledger row, `/kraft-knowhow` lesson). The rest: rows for `rest`,
  several listeners and a resubscribe; a share of 1 or more counts as 0.999; the gauge's comment; `near` per gauge;
  no frames once nobody listens.
- **Round 2** (level 2, two phases): clean, 5 MINOR and 2 NIT: rows for a resubscribe that jumps and a listener
  leaving during an emit; no second chain of frames when a listener writes back into the source (the spec's clock
  now keeps every request); a read while nobody listens reads through to the source; the screen readers' meter now
  hears the smoothed value (accepted); a blank line; the `/kraft-knowhow` note on kept state.
- **Mutation-checked**, 16 mutants, each killed for the right reason (one regex wrote "1.0" where JS prints "1").
- **Tests:** the workshop's `SmoothSpec` 15 rows, klangui's 117, green; the app compiles.

## History

- 2026-10-26: created and started.
- 2026-10-26: reviewed in two rounds (the second clean), committed; the hash sent to Ultra.
- 2026-10-26: handed over to Ultra (Ultra-Main-Dev): ULTRA-00242, with the round 1 fixes; Ultra takes the source
  from Klang's commit and keeps the signature `smooth(sharePer10Ms, rest, near, clock)`, so moving to it is an import.
- 2026-10-26: waiting for Ultra v0.116.0 (the maintainer: "We are waiting for ultra v0.116.0 and then we can adopt
  it's .smooth() function and clean up ours"): then the import moves to Ultra's and `workshop/streams` goes.
- 2026-10-26: Ultra 0.116.0 in (Klang-Main-Dev, 53c0053b): the Motor's gauges import `smooth` from
  `io.peekandpoke.ultra.streams.ops` with Ultra's default clock, the workshop's `smooth`, `FrameClock` and `SmoothSpec`
  are gone (Ultra ported the 15 rows). The app's browser tests green on it; `KlangGauge`'s KDoc and `/kraft-knowhow`
  name Ultra's `smooth()` now. Done.
- 2026-10-26: DONE. Ultra 0.116.0 carries smooth(); the workshop copy is gone (53c0053b)
