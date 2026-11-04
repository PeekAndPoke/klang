# KLANG-00243: One chain host: `Cylinder` and `MasterBus` share their chain plumbing

> **Status (2026-11-04): DONE.** Both steps landed: the package move and the shared `ChainHost`.
>
> **Part of:** KLANG-00354 (The Motor's shape: signal flow and the graph)

Status: **future, not planned.** Opened 2026-09-28 at the close of phase 3 step 12
(KLANG-00238 (phase3 step12 master as katalyst)), which made the
master a Katalyst chain at the output. One step toward the graph plan's "one effect chain type any node can host"
(KLANG-00216 (signal graph engine) section 3, seed list).

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

## The build (2026-11-04)

The maintainer picked it after KLANG-00210 (signal flow redesign) closed, with the package move ("Build it, plus
package move"), knowing the ticket's own rule above (no third host yet, no drift): the coordinator had recommended it
and named the rule only afterwards.

- **Step 1, the package move** (442e75a34): `audio_be/.../cylinders/katalyst/` to `audio_be/.../katalyst/`, main and
  tests, package lines and imports only, the docs that name the path. Three moved KDocs lost their old em dashes (the
  commit hook reads a moved file as new). audio_be jvmTest green.
- **Step 2, the shared host**: `ChainHost` (`audio_be/.../ChainHost.kt`, internal), owned by `Cylinder` and
  `MasterBus`: the chain in service with its key and raw spelling, the bounded cache (`MAX_CACHED_CHAINS`, 8), the
  parked request, the lookup, the `ChainSwap`, the clean slate (`retireAll`). The position's rules stay with the
  position through `ChainHost.Position` (`build`, `land`): the orbit's idle install and sounding fade with the duck
  handover, the output's first-block adoption; the orbit's born-with classic chain answers a declaration of its
  content (`bornWithAnswersItsContent`), the output's empty chain does not, as before. One simplification: the orbit
  parked on `isActive && !swap.settled`, the host on `!swap.settled`, since an idle orbit's swap is always settled.
  Evidence: audio_be jvmTest and jsBrowserTest green (5753 tests); the corpus identical (3 frozen, 13 unedited live
  songs; Kokon and Der Schmetterling edited by the maintainer, information only); a mutation campaign on the host,
  7 of 7 killed. One survived first: `CylinderChainSwapSpec`'s "retire clears the cache, drops the pending name" could
  not see a kept key, because a retired cylinder has no registry; it now adopts onto a registry that knows the name,
  and the mutant is killed.
- **Review**: round 1 (level 1, blind, a code and an audio reviewer) clean from both: every request path, the poll,
  the install, the fade, the land and both clean slates found identical, the idle-orbit simplification verified from
  the code. Its MINORs applied as one batch: stale member names in KDoc and comments (`MasterBus.land`,
  `Cylinder.chainFor`, `installPending`), the eviction's leaving-chain guard kept and named a backstop (a chain lands
  only on a settled swap), `bornWith` private. Recorded, not changed: the master's dispose now retires the chain in
  service before the swap's hard cut, so units return to the shelf in another order (inaudible: a rent hands out
  zeroed memory); the master's `land` returns a constant true.

## History

- 2026-10-20: numbered KLANG-00243 (was `docs/tasks/future/one-chain-host.md`).
- 2026-10-27: part of KLANG-00354 (The Motor's shape: signal flow and the graph), the epic grouping.
- 2026-11-04: IN PROGRESS.
- 2026-11-04: started (maintainer); step 1, the package move, landed (442e75a34); step 2, the shared host, landed after a clean review round; DONE.
- 2026-11-04: DONE.
