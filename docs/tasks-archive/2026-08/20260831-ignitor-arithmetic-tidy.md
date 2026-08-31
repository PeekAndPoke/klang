# Ignitor arithmetic tidy — one window field, two hoists, and a blank-line rule

**Status:** ✅ DONE (opened and archived the same day, 2026-08-31)

A small session, filed because three of its pieces are conventions rather than one-off edits, and
because the way it went wrong in the middle is worth not repeating.

## What was done

### 1. `LerpIgnitor` reads like what it is

`a` / `b` / `t` → `from` / `to` / `weight`, with a `@param` block tying each back to its
`IgnitorDsl.Lerp` field, and a KDoc on the public `lerp()` that states the endpoints and the scratch
cost (ONE buffer for a block-constant weight, two when it is audio-rate — the old one-liner claimed
two unconditionally).

Context: the maintainer asked whether a `blend()` shorthand existed for
`Osc.saw().mul(0.9).add(Osc.sine().mul(0.1))`. It does, as `lerp()` / `mix()` — one `Lerp` node
instead of three arithmetic nodes, so one pass over the block instead of three, and no `safeOut`
clamp. **Argument order stays weight-LAST** (`mix(other, t)`), matching GLSL and every lerp
anywhere; a third name with a flipped order was considered and rejected.

### 2. Two loop invariants stop being recomputed

- `LerpIgnitor`, constant-weight branch: `1 − kw` hoisted.
- `RangeIgnitor`, constant-bounds branch: `0.5 · (kh − kl)` hoisted, saving one multiply per sample.

Both bit-identical. The `Range` re-association is safe because scaling by 0.5 is exact, so
`((x+1)·0.5)·span` and `(x+1)·(span·0.5)` are each ONE rounding of the same real product;
`ConstantFoldParitySpec` pins that loop against the audio-rate one either way.

**What was deliberately NOT done**, and is commented at both sites so it is not "fixed" later:

- The audio-rate branches (`1 − w` in Lerp, `h − l` in Range) are per-sample by nature.
- Lerp's cheaper algebraic form `from + (to − from)·w` would save a multiply and is refused twice
  over: it breaks bit-parity with the constant path (`ConstantFoldParitySpec`), and it loses the
  exact endpoint (`w = 1` must return `to` bit-exactly, pinned by `IgnitorArithmeticTest`).

### 3. `windowEnd` replaces `ctx.offset + ctx.length`

Maintainer's call, and the right one: *"Kleinvieh macht auch Mist."* Both `IgniteContext` and
`BlockContext` now carry the window as three `private set` fields behind three functions:

```kotlin
fun updateOffsetAndLength(offset: Int, length: Int)   // windowEnd computed ONCE
fun updateOffset(offset: Int)
fun updateLength(length: Int)
```

119 read sites across 32 files now read `ctx.windowEnd`; 98 write sites use the functions. The
private setters are the load-bearing part, not the arithmetic: a stale derived window is the
block-framing bug class (`docs/plans/block-framing-invariance.md`), and there is now no way to
desync it. `offset`/`length` had to leave `IgniteContext`'s constructor parameter list for the body,
because a constructor property takes no custom accessor.

**Worth knowing for the next such change:** of those 98 write sites, exactly **two** are production
(`Voice`, `IgniteRenderer`). The rest are per-spec `IgniteContext` fixtures — 47 spec files each
carrying their own copy of the same four lines. A shared `testIgniteContext(...)` helper would have
made this a three-line edit. Not done here; it is a test-only refactor and wants its own pass.

### 4. Blank line before `return`

New house rule, written into `.claude/skills/code-style/SKILL.md` beside the existing blank-line
policy: a blank line before every `return` that is not the only statement in its block, with the
blank going ABOVE a comment attached to the return, never between comment and return. Applied to
`Ignitor.kt`: 51 sites.

## The mistake, recorded on purpose

The bulk rewrite of the write sites was a regex over the whole repo — and it rewrote the assignments
inside the update functions themselves:

```kotlin
fun updateOffsetAndLength(offset: Int, length: Int) {
    this.updateOffsetAndLength(offset, length)   // calls itself
    …
}
```

852 of 1448 tests died with `StackOverflowError`. **A bulk call-site rewrite must exclude the
definition site**, and the only reason this cost minutes instead of a debugging session is that the
suite is fast and broad. Three further sites used an inline `apply { length = 3 }` that the pattern
missed; the private setter turned those into compile errors, which is the failure mode you want.

## Verified

`:audio_be:jvmTest` + `:audio_bridge:jvmTest` — **1510 tests, 0 failures**; `:audio_be:compileKotlinJs`
and `:audio_benchmark:compileKotlinJvm` clean. Build lock taken and released around each run.

## Left open

**Does a non-default accessor make `ctx.offset` a method call in Kotlin/JS?** It is read PER SAMPLE
in 15 loops (`FmRenderer:50`, `EnvelopeRenderer:92`, `SampleIgnitor:50,77`, `PitchModFactories:200,344`
and 9 more). On the JVM C2 inlines it; on JS, V8 should inline a monomorphic trivial getter, but this
engine has a scar exactly there (a non-inlined call measured at +33% in a per-sample position, see
the delay-ring note in `.claude/BUILD-LOCK.md`). It could not be checked here: `compileKotlinJs`
emits a klib and the only bundle on disk is minified, so names are gone, and getting real evidence
means building the frontend.

**The fix that makes the question moot is one line per site**: hoist `val off = ctx.offset` above
those 15 loops. Better practice regardless of how the compiler emits the property.

## Also this session (already committed separately)

- `docs/tasks/ignitor-envelope-ownership.md` and `docs/tasks/voice-elapsed-frames-offset-mismatch.md`
  cross-checked against the code and archived; the second was fixed by the block-framing audit as its
  "Confirmed instance 1", not as its own task.
- `docs/tasks/future/envelope-shape-followups.md` filed with the three findings the envelope-ownership
  work measured but deliberately did not act on (convex attack default, period-relative minimums,
  the missing tail cap for an `.adsrOff()` voice).
