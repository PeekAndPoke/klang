# Bare function references as function-typed arguments

Status: **future / papercut.** Found 2026-08-31 while compiling the sprudel DSL's own KDoc
examples (`docs/tasks/dsl-doc-example-rot.md`). Not a correctness bug in the engine: the
workaround is one pair of parentheses. It is on this list because the failure mode is an
internal cast error rather than a diagnostic, and because the spelling that fails is the one
every Tidal and Strudel user will reach for first.

## What happens

```
s("bd rim hh").pickF("<0 1 2>", [rev, fast(2), jux(rev)])
```

```
Internal error in native function 'jux(p1=[native function rev])':
NativeInteropKt$$Lambda cannot be cast to kotlin.jvm.functions.Function1
```

`jux(rev())` works. `jux(rev)` does not.

## Why, precisely

Two different things in sprudel both read as "a transform", and only one of them is a value:

| Spelling | What it is | Passing it to a mapper parameter |
|---|---|---|
| `flipSign` | `val flipSign: PatternMapperFn` | works |
| `fast(2)`, `rev()` | a **call result**, already a `PatternMapperFn` | works |
| `rev` | `fun rev(n: PatternLike = 1): PatternMapperFn` | **fails** |

`rev` has every parameter defaulted, so a bare `rev` looks like a ready-made transform, but in
KlangScript it is a native *function* value. The interop hands it to a parameter typed
`PatternMapperFn` (a Kotlin `Function1`) and the cast blows up inside the callee.

Note that `rev` sitting in the array literal in the same line is fine. Only the function-typed
**argument** position is affected, because that is the only place a Kotlin function type is
demanded.

## Two ways to close it

1. **Auto-apply.** When a native function value lands in a function-typed parameter and all of
   its own parameters are defaulted, call it and pass the result. This makes `jux(rev)` mean what
   a reader expects and matches the point-free style of the languages sprudel borrows from. It
   needs care: only do it when the arities cannot be confused, and never silently for a function
   that has required parameters.
2. **Diagnose.** If auto-applying is too clever, refuse it in the interop with a real message
   ("`rev` is a function, did you mean `rev()`?") instead of letting a `ClassCastException`
   surface as "Internal error in native function". The parser already produces good
   did-you-mean text elsewhere, so this would match the house standard.

Either way it should get a test, because nothing exercised this path until the doc-example gate
did.

## Affected surface

Every sprudel function that takes a `PatternMapperFn` parameter, which is where a user might
naturally write a bare transform name. Any of these paired with a bare `rev`, `brak`,
`palindrome`, `ply`, or similar defaulted-argument transform hits it:

`almostAlways`, `almostNever`, `always`, `apply`, `applyN`, `chunkBack`, `chunkBackInto`,
`chunkInto`, `echoWith`, `every`, `fastChunk`, `firstOf`, `inside`, `jux`, `juxBy`, `lastOf`,
`layer`, `never`, `off`, `often`, `outside`, `plyWith`, `rarely`, `someCycles`, `someCyclesBy`,
`sometimes`, `sometimesBy`, `stutWith`, `superimpose` (plus their lowercase aliases).

## Status of the docs

The three `pickF` examples that found this are back in the KDoc, spelled `jux(rev())`, and pass
the gate. The internal prose note above `applyPickF` explains the call and points here, so the
next person to write `jux(rev)` in a doc example has the answer next to them.
