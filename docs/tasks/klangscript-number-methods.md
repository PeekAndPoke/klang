# KlangScript — number methods (`2.pow(7/12)`), and the lexer change they need

> **Status (2026-09-08)**: NOT IMPLEMENTED, and split across two sessions by the maintainer.
>
> | Half | Owner | State |
> |---|---|---|
> | **The lexer fix** (`2.pow` must lex at all) | a separate session | to do FIRST |
> | **The stdlib methods** | this session's successor | BLOCKED until the lexer lands |
>
> Do not write the methods before the lexer change is in: without it `2.pow(2)` does not parse, so
> nothing can be tested end to end and every example in the docs would be a lie.
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

## The implementation itself is trivial

The extension mechanism exists and is proven:
`klangscript/src/commonMain/kotlin/stdlib/KlangScriptNumberExtensions.kt`

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

1. **How far to go.** Tier 1 is the everyday patch math below. Tier 2 adds `log2`/`log10`/`ln`/`exp`
   and `sign` (dB and frequency work is logarithmic). Tier 3 is the musical vocabulary further down
   (`semitones`, `cents`, and a `db`/`toDb` pair), which is where a magic number becomes a statement
   of intent and is arguably worth more than tiers 1 and 2 together.
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
"P5".ratio()      // 1.4983   perfect fifth
"M3".ratio()      // 1.2599   major third
"P8".ratio()      // 2.0      octave
"-P5".ratio()     // 0.6674   a fifth down
```

**Parametric, as number methods** (needs the lexer fix above):

```javascript
7.semitones()     // 1.4983
(-12).semitones() // 0.5
50.cents()        // 1.0289
1.5.toSemitones() // 7.02     — inverse, for analysis
```

Naming rationale: `ratio()` works on a string because the receiver already carries the interval
identity. On a bare number it would be ambiguous — seven *what*? — so the unit goes in the method name,
which is what makes `7.semitones()` and `50.cents()` read correctly.

At a call site this converts a magic number into a statement of intent:

```javascript
.oscp("hptrack", "M3".ratio())     // a major third above the fundamental
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

## Tests

```javascript
2.pow(7/12)        // 1.4983…  (was a parse-level silent failure)
2.5.pow(2)         // 6.25     — dot-digit still lexes as one number
2.toString()       // "2"      — previously unreachable on a literal
(-8).abs()         // 8
1.7.round()        // 2
5.clamp(0, 3)      // 3
```

Plus a regression guard that `2.5`, `.5`, `1e3`, `1.5e-3` all still lex as single number tokens.
