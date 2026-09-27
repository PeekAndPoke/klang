# Bare function references as function-typed arguments

Status: **future, correctness.** Found 2026-08-31 while compiling the sprudel DSL's own KDoc
examples (`docs/tasks/bugfix-dsl-doc-example-rot.md`). The workaround is one pair of parentheses, but
the spelling that fails is the one every Tidal and Strudel user will reach for first. Re-checked
2026-09-27 and raised from "papercut": since the interop learned to wrap a native function in the
arity of the slot it lands in, two of the three spellings no longer crash. They play the wrong
thing without saying so, which is worse than an error.

## What happens

As of 2026-09-27 (probed on the JVM, `s("bd rim hh")` over two cycles):

| Script | Result |
|---|---|
| `jux(rev())` | correct |
| `jux(rev)` | crashes: `Internal error in native function 'ReinterpretPattern.jux(p1=[native function rev])': Lang_tempo_reverseKt$$Lambda cannot be cast to SprudelPattern` |
| `every(2, rev())` | correct, cycle 0 reversed |
| `every(2, rev)` | **no error, nothing reversed** |
| `pickF("<0 1 2>", [rev(), fast(2), jux(rev())])` | correct, cycle 0 reversed, cycle 1 fast |
| `pickF("<0 1 2>", [rev, fast(2), jux(rev)])` | **no error, the wrong transforms play** |

On 2026-08-31 the `jux(rev)` error read `NativeInteropKt$$Lambda cannot be cast to
kotlin.jvm.functions.Function1`; the arity fix moved the failure one step further in.

## Why, precisely

Two different things in sprudel both read as "a transform", and only one of them is a value:

| Spelling | What it is | Passing it to a mapper parameter |
|---|---|---|
| `flipSign` | `val flipSign: PatternMapperFn` | works |
| `fast(2)`, `rev()` | a **call result**, already a `PatternMapperFn` | works |
| `rev` | `fun rev(n: PatternLike = 1): PatternMapperFn` | **fails** |

`rev` has every parameter defaulted, so a bare `rev` looks like a ready-made transform, but in
KlangScript it is a native *function* value (it was not converted to a callable object with
`@KlangScript.Invoke`). The interop (`NativeInterop.kt`, the `NativeFunctionValue` branch of
`convertToKotlin`) wraps it as a `Function1` for the `PatternMapperFn` slot, so the callee's
`mapper(pattern)` runs `rev(n = pattern)`. A pattern is a valid `PatternLike`, so that call
succeeds and returns a *transform* where a *pattern* was expected. What happens next depends on
the callee: `jux` casts the result and crashes, `every` and `pickF` swallow it and play the wrong
thing.

The array literal is not safe either (the 2026-08-31 version of this file said it was): the
`pickF` row shows a bare `rev` in the list misbehaving the same way.

## Two ways to close it

1. **Auto-apply.** When a native function value lands in a function-typed parameter and all of
   its own parameters are defaulted, call it and pass the result. This makes `jux(rev)` mean what
   a reader expects and matches the point-free style of the languages sprudel borrows from. It
   needs care: only do it when the arities cannot be confused, and never silently for a function
   that has required parameters.
2. **Diagnose.** If auto-applying is too clever, refuse it in the interop with a real message
   ("`rev` is a function, did you mean `rev()`?") instead of wrapping it and letting the callee
   crash or misplay. The parser already produces good did-you-mean text elsewhere, so this would
   match the house standard.

Either way the fix has to cover the list position too (a native function converted to `Any`
inside an array), and it needs tests for all three rows of the table above: the crash, and the
two silent cases, which are the ones a user cannot find on their own.

## Affected surface

Every sprudel function that takes a `PatternMapperFn` parameter, which is where a user might
naturally write a bare transform name. Any of these paired with a bare `rev`, `brak`,
`palindrome`, `ply`, or similar defaulted-argument transform hits it:

`almostAlways`, `almostNever`, `always`, `apply`, `applyN`, `chunkBack`, `chunkBackInto`,
`chunkInto`, `echoWith`, `every`, `fastChunk`, `firstOf`, `inside`, `jux`, `juxBy`, `lastOf`,
`layer`, `never`, `off`, `often`, `outside`, `plyWith`, `rarely`, `someCycles`, `someCyclesBy`,
`sometimes`, `sometimesBy`, `stutWith`, `superimpose` (plus their lowercase aliases), and every
function that takes a list of transforms, `pickF` first among them.

## Status of the docs

The three `pickF` examples that found this are back in the KDoc, spelled `jux(rev())`, and pass
the gate. The internal prose note above `applyPickF` explains the call and points here, so the
next person to write `jux(rev)` in a doc example has the answer next to them.
