# Bugfix: `mask()` swallowed the highlights of the mask string

> **DONE 2026-10-01**, commit `d371c79c`. Reported by the maintainer ("it seems so"), confirmed with a
> red spec, fixed, archived the same day.

## The symptom

In live highlighting, `s("bd sd hh cp").mask("1 0 1 1")` lit up the source atoms (`bd`, `hh`, `cp`)
but never the atoms of the mask string. `struct()` lit up both. `maskAll()` had the same gap as `mask()`.

## The cause

`mask` and `maskAll` run through `StructurePattern` in `Mode.In`
(`sprudel/src/commonMain/kotlin/pattern/StructurePattern.kt`). `queryIn` samples the mask at each
source event's midpoint, decides keep or drop, and added the source event unchanged, so the sampled
mask event's `sourceLocations` were thrown away. `queryOut` (`struct`, `structAll`) already prepends
the structure event's locations.

## The fix

One line in `queryIn`: a kept event becomes
`sourceEvent.prependLocations(otherEvent?.sourceLocations)`, the same order `struct` uses (mask
location outermost, the atom innermost). The editor highlighter (`CodeMirrorHighlightBuffer`) draws
every location in the chain, so the mask atom now lights up with the event it let through.

## Guard

`sprudel/src/commonTest/kotlin/lang/locations/MaskLocationSpec.kt`: `mask`, `mask` with a falsy atom,
`maskAll`, and `struct` as the reference.

- Before the fix: 3 of 4 red (`mask` gave `[[8], [11]]` instead of `[[22, 8], [24, 11]]`).
- After: 4 of 4 green, full `:sprudel:jvmTest` green.
- Mutation check on the `struct` case: removing the prepend in `queryOut` turns it red.

## Not done

- No reviewer round: a one-line fix proven red to green by the spec.
- Not checked in the browser; verified at event level only.
