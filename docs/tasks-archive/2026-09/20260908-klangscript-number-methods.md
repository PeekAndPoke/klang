# KlangScript — number methods (`2.pow(7/12)`), and the lexer change they need

> **Status (2026-09-08)**: DONE, both halves.
>
> | Half | State |
> |---|---|
> | **The parser half** (`2.pow` lexes, `-1.0.clamp()` folds) | DONE, commit `b7d540e1`, see "What the parser half delivered" |
> | **The stdlib methods** (all three tiers, `toRatio` on strings, the Kotlin door) | DONE, see "What the stdlib half delivered" |
>
> Open by choice, not by omission: the Kotlin aliases `coerceAtLeast`/`coerceAtMost` for `max`/`min`
> were left out on 2026-09-08 ("we can easily add them later if we want"). `.5` is not a number
> literal (write `0.5`). The `^`-lint of the section below is still unbuilt.
>
> `^`-as-power was the alternative and was DECLINED on 2026-09-08, see
> `../tasks-archive/2026-09/20260908-klangscript-caret-as-power-wont-implement.md`. This task is now
> the whole answer to that footgun.

## Why

Frequency ratios are the bread and butter of writing music as code. An equal-tempered interval of *n*
semitones is `2^(n/12)` in every reference on earth, so that is what people write. In KlangScript `^` is
**bitwise XOR** (`Interpreter.kt:1226`, `left.toInt() xor right.toInt()`), so `2^(7/12)` evaluates to
`2 xor 0` = **2** — a plausible-looking number, silently wrong, no diagnostic.

That exact mistake shipped in Der Schmetterling and survived two versions: three different intervals
(`2^(4/12)`, `2^(2/12)`, `2^(7/12)`) all evaluated to the same value, and the resulting `hptrack` of 3.0
audibly gutted the guitars' low mids before it was caught by measurement.

`Math.pow(2, 7/12)` already works and is the current workaround. The ask is the Kotlin-style form:

```javascript
2.pow(7/12)          // 1.4983 — a perfect fifth
440 * 2.pow(-9/12)   // 261.63 — middle C
```

## The prerequisite: the lexer eats the dot

**`2.pow(x)` does not lex today.** The number scanner (`KlangScriptParser.kt:835`) is:

```kotlin
while (i < source.length && (codes[i].isAsciiDigit() || codes[i] == C_DOT)) { i++; column++ }
```

It consumes any run of digits *and dots*. So `2.pow(7/12)` tokenises as `NUMBER("2.")` followed by
`IDENT("pow")` — two juxtaposed expressions, i.e. **two statements**, the second one's value discarded.

Since 2026-09-08 that at least fails loudly: statement boundaries reject two statements on one line
(`tasks-archive/2026-09/20260908-klangscript-statement-boundaries.md`), and the diagnostic even lands
on the right token: "Expected a newline or ';' between statements. Did you mean '.pow(...)'?" It is
still wrong (the user wrote one expression, not two), so this feature must fix the lexer first, but
the failure is no longer silent.

**The fix:** consume a `.` into a number literal only when the character *after* it is a digit.

```kotlin
while (i < source.length && (codes[i].isAsciiDigit() ||
       (codes[i] == C_DOT && i + 1 < source.length && codes[i + 1].isAsciiDigit()))) { ... }
```

`2.5` still lexes as one number; `2.pow` lexes as `2` `.` `pow`. Note this also fixes the existing
`toString` method, which is already registered on numbers (below) but is unreachable on a literal for
exactly this reason.

**It now fails loudly rather than silently, which changes what to test.** Since statement boundaries
landed (2026-09-08), `2.pow(7/12)` is a parse error reading "Expected a newline or ';' between
statements. Did you mean '.pow(...)'?" That diagnostic is correct today and must DISAPPEAR with this
change: a spec row should assert `2.pow(2)` evaluates to 4, and the boundary spec's own rows must stay
green, since both features read the same token stream.

Two details to get right:

- **`2.` alone** (trailing dot, no digit) currently lexes as the number 2.0. After the change it becomes
  `2` followed by a stray `.`, which should be a parse error. That is an improvement, but check no test
  or example relies on the old spelling.
- **Ranges/spreads** — if `..` or `...` is ever added, the digit lookahead already keeps `1..5` working.

## What the parser half delivered (2026-09-08)

Two parser-level changes, both in `KlangScriptParser.kt`, pinned by
`klangscript/src/commonTest/kotlin/parser/NumberLiteralMethodCallSpec.kt` (24 rows, mutation-checked:
15 of the first 21 fail against the old parser, the survivors are the regression guards; the exponent
lookahead and the `--` fold each have their own killing mutant):

1. **The lexer takes a `.` into a number only when a digit or an exponent follows it, and only once.** The
   scan is now `scanDecimalEnd()` (a companion function, so the lexer index stays unboxed): digits, at
   most one fraction, optional exponent. `2.5` and `0.5` and `1.5e-3` and the JS spelling `2.e5` are one
   token; `2.pow(2)` is `2` `.` `pow` and `2.exp()` is a member call; a trailing `2.` and a doubled `1.2.3` are parse errors
   ("Expected property name after '.'"), where `1.2.3` used to be a `NumberFormatException` from inside
   the lexer. The corpus (builtin songs, tutorials) has neither spelling. `.5` never lexed as a number
   (the dot has no digit-run before it), so it is not a regression and was left alone.
2. **A minus sign in front of a number literal is part of the number** (maintainer decision, 2026-09-08,
   "I would like to avoid that the user needs to write `(-1.0).clamp()`"). `parseUnary` folds `-` plus
   a `NUMBER` token into one negative `NumberLiteral` before the postfix loop runs, so `-1.0.clamp(0, 1)`
   is `(-1.0).clamp(0, 1)` and `-7.semitones()` is `(-7).semitones()`. This is a deliberate divergence
   from Kotlin and JS, where `-7.0.pow(2)` is `-(7.0.pow(2))` = -49: for a player that is a plausible
   wrong number with no diagnostic, the same failure class as the `^` bug.

   What does NOT fold: `-x.abs()` stays `-(x.abs())` (only a literal folds), `-(7).abs()` stays a unary
   operation, and a binary minus never reaches the rule (`a -1` and `3 -1.abs()` are subtractions).
   `--10`, `- -10`, `-2 * 3`, `2 - -3` evaluate as before. Whitespace does not matter: `- 7.abs()` folds too,
   and the second minus of `--` folds like a lone one, so `--1.abs()` and `- -1.abs()` are both `-((-1).abs())`.
   The folded literal's location spans the minus and the digits.

   Consequence for the AST: `-42` is a `NumberLiteral(-42.0)`, no longer a `UnaryOperation`. Two tests
   that pinned the old shape were updated (`CompleteProgramTest`, `AnalyzedAstTest`); the analyzer,
   interpreter and named-argument checker needed no change.

The parenthesised spellings in this file, `(-8).abs()`, `(-12).semitones()`, `(-6).db()`, keep working and
mean the same thing; the stdlib docs should show the bare form, `-8.abs()`, since that is the point.

`2.toString()` works end to end already (`StdLibNumberMethodsTest`), which proves the stdlib dispatch
sees a call on a literal.

## What the stdlib half delivered (2026-09-08)

Written by two Opus workers under a coordinator (worker A the script doors, worker B the Kotlin door),
reviewed in the project loop.

- **Script doors**, `klangscript-libs/.../stdlib/KlangScriptNumberExtensions.kt`: `pow(exp)`, `abs()`,
  `sqrt()`, `round()`, `floor()`, `ceil()`, `min(other)`, `max(other)`, `clamp(lo, hi)`, `rem(n)`, `mod(n)`,
  `log2()`, `log10()`, `ln()`, `exp()`, `sign()`, `semitones()`, `cents()`, `toSemitones()`, `db()`, `toDb()`;
  and `toRatio()` on strings in `KlangScriptStringExtensions.kt`. Tiers 1 and 2 delegate to the same
  `kotlin.math` calls as `Math.*`, so `round` is ties-to-even like `Math.round`. `rem`/`mod` throw the
  interpreter's "Modulo by zero"; `clamp` with `lo > hi` throws a `KlangScriptTypeError` naming both
  bounds instead of letting Kotlin's IllegalArgumentException escape; `ratio` of a non-name throws
  "... is not an interval name (examples: P5, M3, m7, -5P)".
- **Kotlin door**, `common/src/commonMain/kotlin/math/PitchAndGain.kt`: `Double.semitones()`, `cents()`,
  `toSemitones()`, `db()`, `toDb()`, one formula each (the engine's `applySemitoneDetuneToFrequency`
  in `audio_be/DspUtil.kt` spells the same formula and was left alone); and `val Interval.ratio` in
  `klangscript-libs/.../stdlib/IntervalRatio.kt`, built on the `tones` interval vocabulary as planned.
  Tiers 1 and 2 need no Kotlin door: `kotlin.math` is that door.
- **The string door is `toRatio()`, not `ratio()`.** The review found that sprudel registers every
  pattern function as a string method too (`"bd sd".fast(2)`), and sprudel has a `ratio()` (its colon
  ratio step); a song imports stdlib and then sprudel, so `"M3".ratio()` would have reached sprudel's
  parser and yielded a null voice value, no diagnostic. `toRatio()` collides with nothing and reads
  like its siblings `toSemitones()` / `toDb()`. Guard: `sprudel/.../LangStdlibStringMethodCollisionSpec`
  keeps the two name sets apart (two pre-existing collisions, `repeat` and `slice`, are parked there
  for the maintainer: in a song the sprudel versions win).
- **A core bug came out of the same review:** `Environment.register` replaced a receiver's whole
  method map with the map of the library imported last, so EVERY stdlib string method was unreachable
  in a song. It now merges per name (`klangscript/.../LibraryExtensionMergeSpec`), a method of the same
  name and receiver still belongs to the library imported last.
- The `tones` interval parser no longer crashes on an interval number 0 (`"P0"`) or on a number that
  overflows an Int; both are `NoInterval` now, so the door reports "not an interval name" instead of an
  internal error. The methods that throw (`clamp`, `rem`, `mod`, `toRatio`) carry the call location.
- Round 2 of the review: the doc-only `@alias` tags pairing the IgnitorDsl signal doors `mod`/`rem`
  and `pow`/`power` came off, because the library reference merges aliases per symbol and the card
  for `mod` would have shown "@rem" next to the Number method whose whole point is that they differ
  (the signal doors keep "Alias for ..." in their prose and both names stay registered). The `tones`
  parser also refuses interval numbers above 1,000,000, where the semitone arithmetic overflowed Int
  and a name came back non-empty with a garbage size. `klangscript-libs` now declares `tones`.
  Rejected, recorded as a follow-up: a reused engine's CHILD environment caches extension lookups and
  an import only clears the root's caches, so a second `import` on the same engine after a lookup
  can keep serving the earlier library's method of the same name (pre-existing, unreachable in every
  host today since both imports precede user code).
- **Descending intervals are spelled `"-5P"` or `"P-5"`**, never `"-P5"`: that is what the `tones` parser
  accepts (sign before the NUMBER, tonal style; `Interval.fromSemitones(-7)` returns `"-5P"`). This file
  and the brief had it wrong; the specs pin `"-P5"` as rejected so it cannot creep back into the docs.
- `toSemitones()` and `toDb()` of 0 or a negative follow `kotlin.math` (`-Infinity`, `NaN`), documented,
  no clamping: the Motor stays raw and `Math.sqrt(-1)` is `NaN` today as well.
- **Specs**: `StdLibNumberMethodsTest` (values, the `%`-equals-`rem` loop, the zero divisor, the clamp
  bounds, the interval names, the `^` footgun rows, the minus-fold rows), `StdLibNumberMethodsDoorParitySpec`
  (script versus Kotlin, exact), `IntervalRatioSpec`, `common/.../PitchAndGainSpec`. One existing
  analyzer test changed: the stdlib `abs` symbol now has three variants (`Math.abs`, `IgnitorDsl.abs`,
  `Number.abs`).
- Language docs: `klangscript/language-features/10-math.md` 10.9.

## The implementation itself is trivial

The extension mechanism exists and is proven:
`klangscript-libs/src/commonMain/kotlin/stdlib/KlangScriptNumberExtensions.kt`

```kotlin
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(NumberValue::class)
internal object KlangScriptNumberExtensions {

    @KlangScript.Method(name = "toString")
    fun asString(self: NumberValue): StringValue = StringValue(self.toDisplayString())
}
```

Add methods in the same shape, delegating to the same `kotlin.math` calls `KlangScriptMath` already uses
(`KlangScriptMath.kt:188` is `fun pow(base: Double, exp: Double): Double = base.pow(exp)`).

### Open design questions (the maintainer's, not settled)

1. ~~How far to go~~ **settled 2026-09-08: all three tiers.** The full list, so the implementer does
   not have to re-derive it:

   | Tier | Methods |
   |---|---|
   | 1, everyday patch math | `pow(exp)`, `abs()`, `sqrt()`, `round()`, `floor()`, `ceil()`, `min(other)`, `max(other)`, `clamp(lo, hi)`, `mod(n)`, `rem(n)` |
   | 2, logarithmic | `log2()`, `log10()`, `ln()`, `exp()`, `sign()` |
   | 3, musical | `semitones()`, `cents()`, `toSemitones()`, `db()`, `toDb()`, plus `"P5".toRatio()` on String |

   Tier 3 is the one that changes how a song reads: `.oscp("hptrack", "M3".toRatio())` says what it
   means where `1.2599` does not. Tiers 1 and 2 delegate to the same `kotlin.math` calls
   `KlangScriptMath` already uses, so they are close to free once the lexer lands.

   **Check door parity before writing tier 3** (`/dsl-design` §3, a project rule). Tiers 1 and 2 need
   no Kotlin door: `kotlin.math` already IS that door. Tier 3 does not exist on either side yet, and
   `tones/src/commonMain/kotlin/interval/Interval.kt` already carries the interval vocabulary, so the
   Kotlin door should be built on that rather than on a second conversion written by hand.
2. ~~`clamp` or `coerceIn`~~ **settled 2026-09-08: `clamp(lo, hi)`.** What musicians and shader/JS
   people already know; `coerceIn` is Kotlin-internal vocabulary that means nothing to a player.
3. **Kotlin aliases for `min`/`max`: open.** The proposal is to also accept Kotlin's spellings. If it
   happens, the mapping is:

   | Our name | Kotlin alias | Returns |
   |---|---|---|
   | `max(other)` | `coerceAtLeast(other)` | the LARGER of the two |
   | `min(other)` | `coerceAtMost(other)` | the SMALLER of the two |

   **Note the direction, it reads backwards at first glance.** "At least x" RAISES a value, so it is
   `max`; "at most x" LOWERS it, so it is `min`. This repo's own `CycleTime.coerceAtLeast/AtMost`
   (`common/.../CycleTime.kt:79-80`) are written under a `// Min / max` comment and confirm it. Wire
   them the other way round and `5.min(3)` returns 5, with no diagnostic: the same silent-wrong-number
   class as the `^` bug.

   Two arguments against, worth weighing before adding them:
   - **Coherence.** We just declined `coerceIn` in favour of `clamp`. Taking Kotlin's name for the
     one-bound cases while rejecting it for the two-bound case is a mixed message; a reader who learns
     `clamp` has no reason to expect `coerceAtLeast` to exist.
   - **"One word per concept end to end"** is a project rule (`CLAUDE.md`). Aliases are an accepted
     pattern in sprudel (`comp`, `uni`, `o`), but those shorten a long name; these lengthen a short one.

### Both remainders, decided 2026-09-08

Ship `mod` AND `rem`, with Kotlin's meanings, because the difference is invisible until it bites:

| Method | Meaning | `(-1)` over 12 | Twin |
|---|---|---|---|
| `rem(n)` | remainder, sign follows the DIVIDEND | `-1` | the method form of `%` |
| `mod(n)` | floor-mod, sign follows the DIVISOR | `11` | no operator |

`%` in KlangScript is Kotlin's `%` on `Double` (`Interpreter.kt:1314`), so `rem` is exactly the
operator spelled as a method, and `mod` is the one the language cannot currently express at all.

Why both rather than only the safe one: pitch classes and cycle wrapping want `mod` (a negative
semitone offset should land back in the octave, not below it), while phase and time arithmetic often
want `rem` (the signed distance past a boundary). Offering only `mod` would silently change the
answer for anyone reaching for `%`'s behaviour, and offering only `rem` leaves the footgun in place.
Naming them Kotlin's way means a reader who knows either language is not surprised.

Document them as a pair, each naming the other, and give the negative case in both examples: a
plausible wrong number with no diagnostic is the same failure class as the `^` bug that started this
task, and `-1` versus `11` is exactly that shape.

**Zero divisor:** `%` throws `Modulo by zero` (`Interpreter.kt:1316`). Both methods should throw the
same way, so the method and the operator cannot disagree.

**Minimum set** — the ones that read better as methods than as `Math.` calls:

| method | notes |
|---|---|
| `pow(exp)` | the reason for this task |
| `abs()`, `sqrt()` | |
| `round()`, `floor()`, `ceil()` | |
| `min(other)`, `max(other)` | |
| `clamp(lo, hi)` | very common in patch code, currently hand-rolled everywhere |
| `mod(n)`, `rem(n)` | both, see the section above; `rem` is `%`, `mod` is the one nothing can express today |

Everything stays available as `Math.*` too — this is an additional spelling, not a replacement.

## Musical helpers: interval → frequency ratio

The reason this task exists is that people write `2^(n/12)`. Better than making that spelling work is
making it unnecessary. The vocabulary already exists in Kotlin —
`tones/src/commonMain/kotlin/interval/Interval.kt` has an interval type with `.semitones` and name
parsing (`"P5"`, `"M3"`, `"-2m"`) — so expose that rather than inventing a scheme.

**Named intervals, as a String method** (ships today, no lexer change — a string literal followed by
`.` already lexes):

```javascript
"P5".toRatio()      // 1.4983   perfect fifth
"M3".toRatio()      // 1.2599   major third
"P8".toRatio()      // 2.0      octave
"-5P".toRatio()     // 0.6674   a fifth down: the sign goes before the NUMBER, as in tones ("-P5" is not a name)
```

**Parametric, as number methods** (needs the lexer fix above):

```javascript
7.semitones()     // 1.4983
(-12).semitones() // 0.5
50.cents()        // 1.0293
1.5.toSemitones() // 7.02     the inverse, for analysis
```

**Decibels, the same idea in the other currency** (approved 2026-09-08 with tier 3). Every mixing
knob is in dB and every engine gain is linear, so the conversion is written by hand constantly:

```javascript
(-6).db()         // 0.5012   dB to a linear gain, 10^(dB/20)
0.5.toDb()        // -6.02    the inverse
```

Naming rationale: `ratio()` works on a string because the receiver already carries the interval
identity. On a bare number it would be ambiguous — seven *what*? — so the unit goes in the method name,
which is what makes `7.semitones()` and `50.cents()` read correctly.

At a call site this converts a magic number into a statement of intent:

```javascript
.oscp("hptrack", "M3".toRatio())     // a major third above the fundamental
```

Prior art: SuperCollider `7.midiratio` / `1.5.ratiomidi` (closest ancestor; "midi" is the wrong noun
here), Csound `semitone(7)`, Faust `ba.semi2ratio`, Tone.js `intervalToFrequencyRatio(7)`.

Free-function forms (`semitones(7)`, `cents(50)`) work today without any lexer change and can coexist
with the methods.

## The `^` question

Even with `pow()` shipped, `2^(7/12)` stays legal and stays silently wrong. Two ways out:

- ~~**Preferred: make `^` the power operator.**~~ DECLINED 2026-09-08, see [the record](../tasks-archive/2026-09/20260908-klangscript-caret-as-power-wont-implement.md). The blast radius is
  one test file, and it removes the trap permanently instead of warning about it. If that ships, `xor`
  joins this object as a method and the lint below is unnecessary.
- **Otherwise — lint it.** A warning when either operand of `^` is a non-integer:
  > `^` is bitwise XOR, not exponentiation. `7/12` is not an integer. Did you mean `2.pow(7/12)`?

  Narrow and low-false-positive — nobody XORs a fraction on purpose. Belongs with the intellisense
  diagnostics rather than in the parser.

## Docs surfaces

Three of these are hand-written and will not update themselves. Skipping them is how the original
bug survives the fix.

| Surface | What to do | Cost |
|---|---|---|
| Library reference (`/docs/library/...`, `KlangScriptLibraryDocsPage`) | nothing | free: it renders `generatedStdlibDocs`, so any `@KlangScript.Method` with a `@category` appears with its params and examples |
| Editor docs popup | nothing | free, same source |
| **Language reference**, `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt` | three edits, below | hand-written |
| `.claude/skills/klangscript-knowhow` ref files | check whether the operator/number pages need the same lines | hand-written |

The language reference is 14 `DocSection`s of runnable examples, and **`KlangScriptDocContentTest`
executes every one of them**, so anything added there is self-verifying: a wrong example fails the
build rather than misleading a reader.

1. **`Bitwise & Shift Operators` → "Bitwise basics"** (`:337`). Today it says "`&` masks bits, `|`
   combines them, `^` toggles them" and nothing more. This is the single highest-value line in the
   whole feature: it is where a person typing `^` for exponentiation is looking. Say plainly that `^`
   is XOR, that it is NOT a power operator, and point at `**` and `pow`.
2. **`Operators & Expressions` → "Arithmetic"** (`:196`). Already documents `**` correctly. Add the
   method spelling beside it once it lexes.
3. **`Named arguments on stdlib functions`** (`:836`) uses `Math.sqrt(16)` / `Math.pow(2, 10)`. The
   methods are an additional spelling, not a replacement, so show both here rather than rewriting it.

There is no "Number methods" section, though there are three String-method sections (`:415`, `:429`,
`:440`). Tier 1 and 2 warrant one modelled on those; tier 3 (`semitones`, `cents`, `db`) deserves its
own musical example rather than a list, since its whole point is reading as intent.

### `**` already exists, which changes the urgency

`**` is a working exponentiation operator (`Interpreter.kt:1300`, `pow`; `**=` too) and the language
reference documents it at `:197`. Neither this task nor the declined `^` proposal mentioned it: this
doc called `Math.pow` "the current workaround" and the `^` proposal argued exponentiation had only
"the awkward spelling". Both were written without noticing `**`.

So the original footgun already has a correct, idiomatic answer, and `2 ** (7/12)` is what a
JavaScript or Python writer would type first. That does not cancel this task, `2.pow(x)` still reads
well in a chain and tier 3 is where the real value is, but it does mean:

- **the cheapest fix for the actual bug is documentation, not code**: one line in the bitwise section
  pointing at `**`. That could ship today, independent of the lexer;
- nobody should describe `pow` as the thing that makes exponentiation possible. It is a second
  spelling of something the language has had all along.

## Tests

```javascript
2.pow(7/12)        // 1.4983…  (was a parse-level silent failure)
2.5.pow(2)         // 6.25     — dot-digit still lexes as one number
2.toString()       // "2"      previously unreachable on a literal (DONE, StdLibNumberMethodsTest)
-8.abs()           // 8        the minus is part of the literal (parser DONE)
1.7.round()        // 2
5.clamp(0, 3)      // 3
-1.rem(12)         // -1       the negative case for both remainders, see above
-1.mod(12)         // 11
```

The parse-level rows and the regression guard (`2.5`, `0.5`, `1e3`, `1.5e-3`, hex/octal/binary all still lex
as single number tokens) are DONE in `NumberLiteralMethodCallSpec`. The stdlib session adds the value
rows to `StdLibNumberMethodsTest`.
