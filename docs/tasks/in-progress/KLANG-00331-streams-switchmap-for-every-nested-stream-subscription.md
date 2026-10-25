# KLANG-00331: Streams: switchMap for every nested stream subscription

> **Status (2026-10-25): IN PROGRESS.** Every site ported, review round 1 clean; waits for the maintainer's hand test
> of the oscilloscope and the editor's highlights.

## Why this exists

Ultra 0.115.0 brought `switchMap` and `switchMapNotNull` to its streams: follow a stream that lives inside another
stream's value, the old inner stream released before the new one is subscribed. Klang had the hand-made version in
several places: subscribe to the outer stream, and in its callback unsubscribe the old inner subscription, subscribe
the new one and keep the handle in a field for `onUnmount`. The maintainer (2026-10-25), after the first port worked:
"write a task for the update to this pattern everywhere and then tackle it right away".

The pattern in one line: `Player.player.switchMapNotNull { it.signals }`. No subscription field, no cleanup by hand,
and an old inner subscription is released also when the outer value goes back to null (some hand-made versions kept
it until the next value).

## The sites (scan 2026-10-25)

Found by `subscribingTo(Player.player)`, every `subscribeToStream` in main code, every `subscribingTo(...) { }` with
a callback and every `?.invoke()` of a kept subscription.

| Site | Outer → inner | State |
|------|---------------|-------|
| `PlayerMiniStats` | `Player.player` → `signals`, the latest `Diagnostics` | done, 74c17d14 |
| `PlayerWarehouseStats` | `Player.player` → `signals`, the warehouse snapshot; its 250 ms ticker gone, `.distinct()` | done, 74c17d14 |
| `Oscilloscope` | `props.player` → the analyzer's `waveform` | done, review round 1 clean |
| `KlangCodePlaybackCtrl` | the current playback → its `signals`, forwarded into the controller's `signals` | done, review round 1 clean |

Not this pattern, left as they are: the other `subscribeToStream` calls subscribe to one stream that never changes
(`Player.status`, the rpm debounce, the MIDI events, `OneShotPlayback`'s own signals); `StartPage`'s jingle waits on
one playback's signals.

## Decided

- **Oscilloscope:** `props.player.map { it?.getAnalyzer() }.switchMapNotNull { it.waveform }` through
  `subscribingTo`, which also redraws: the component already asked for a redraw on every waveform frame, so the cost
  stays the same. A null (no player, no analyzer) draws nothing and keeps the last frame, as before.
- **KlangCodePlaybackCtrl:** `signals = _playback.switchMapNotNull { it.signals }`, the cycle count one subscription in
  `init`. The stop keeps its order: the playback leaves the stream before it is stopped, so a signal sent while it
  stops does not reach the editor, as the hand-made unsubscribe did.

## Acceptance

- No kept inner subscription and no hand resubscribe left at the sites above; the code compiles, the tests stay green.
- Review loop clean.
- The maintainer's hand test: the oscilloscope draws as before; play, live update, stop and play again in the editor
  highlight as before, and the cycle counter counts.

## History

- 2026-10-25: created; the Motor stats done before it (74c17d14, hand-tested by the maintainer: "yes the switchMap
  works").
- 2026-10-25: the oscilloscope and the code playback controller ported; review round 1 clean (four nits applied).
