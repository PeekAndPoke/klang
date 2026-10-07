# One chain host: `Cylinder` and `MasterBus` share their chain plumbing

Status: **future, not planned.** Opened 2026-09-28 at the close of phase 3 step 12
([`../../tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`](../../tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md)), which made the
master a Katalyst chain at the output. One step toward the graph plan's "one effect chain type any node can host"
([`../../plans/future/signal-graph-engine.md`](../../plans/future/signal-graph-engine.md) section 3, seed list).

## What is duplicated today

Step 12 unified the chain (`KatalystDsl`, `KatalystChain`), the registry (one `KatalystRegistry` fork per
playback) and the swap (`ChainSwap`). The two HOSTS of the chain still carry the same plumbing each, written twice:

| plumbing | `Cylinder` (`audio_be/.../cylinders/Cylinder.kt`) | `MasterBus` (`audio_be/.../master/MasterBus.kt`) |
|---|---|---|
| a bounded chain cache by key | `chains`, `MAX_CACHED_CHAINS` (8), `chainFor`, `evictIfNeeded`, `buildChain` | `chains`, `MAX_CACHED_CHAINS` (8), `chainFor`, `evictIfNeeded`, `buildChain` |
| the raw-name fast path | `chainRawName` | `currentRawName` |
| the parked request, latest wins | `pendingKey`, `pollPendingChain`, `installPending` | `pendingKey`, `pollPendingSwap` |
| lookup by name | `requestChain`, `KatalystRegistry.findByKey` | `requestSwap`, `KatalystRegistry.findByKey` |
| the swap | a `ChainSwap`, begun from `beginFade` (via `handOverDuck`) | a `ChainSwap`, begun from `land` |
| eager retire of a chain leaving service | `install` | `land` |

## What the output adds (and a shared host must keep)

Three rules of the output position, each pinned today (see the `MasterBus` class KDoc):

1. **Adoption at full weight before the first block**: a chain that lands before the engine has rendered is
   installed at once, no fade (`hasRendered`, plan risk R4, `MasterBusAdoptionSpec`). The orbit's counterpart is
   "the orbit is idle".
2. **No silence reset**: the output never deactivates or resets its chain on silence (plan risk R5), so a limiter
   envelope or a room carries across a gap. A cylinder resets its chain when its orbit goes quiet.
3. **The tail keep-alive**: the output's `isRinging` keeps a stopped engine alive while its chain has a tail
   (`PlaybackEngine.isIdle`); an orbit does the same through its own activity.

The orbit adds its own: the duck handover of `ChainSwap` (`Cylinder.handOverDuck`), the owner commit (`Cylinder.offer`, `Cylinder.commitOwner`) and
`katalystParams` (the output fills no slots: `applyParams(null)`), and the born-with classic chain.

## The idea

A shared chain host (a class both own, not a base class) holding the cache, the raw-name path, the parked key, the
lookup and the `ChainSwap`, with the position's rules passed in as plain values or small functions (when to
install at once, whether silence resets). It removes the duplicate and is the shape a graph node would host.

The same move answers a naming wart the step 12 plan left for later (its section 5, "The package name"): the chain
lives in `audio_be/.../cylinders/katalyst/`, a name that is a little wrong at the output position. A package move
is audio-inert; do it with the shared host, or with the graph plan.

Not planned yet: the duplication is small, both copies are pinned by their specs, and the graph plan may reshape
both hosts anyway. Stone rule: complexity is the enemy; do this only when a third host appears or the two copies
start to drift.
