# KlangScript — make `^` the power operator

> **Status (2026-09-08): WON'T IMPLEMENT.** Kept for the reasoning, not as a plan.
>
> The problem below is real and the evidence still stands. The decision is that the fix is not to
> re-point an operator: `2.pow(7/12)` as an ordinary number method reads as well, costs no language
> change, and rides the extension mechanism the stdlib already has. See
> `20260908-klangscript-number-methods.md` (done 2026-09-08, same archive month), which was the live task. `^` stays bitwise XOR.
>
> Do not re-open this without new evidence; it was weighed and declined.

## Why

KlangScript is a language for writing music. Music is exponential almost everywhere:

- equal temperament — an interval of *n* semitones is `2^(n/12)`
- decibels — `10^(dB/20)`
- octaves, tempo doublings, filter cutoffs — all geometric
- envelope and drive curves — `10^(amount·k)`, `(e^Kx−1)/(e^K−1)`

Bitwise XOR, by contrast, has no musical meaning. Giving the domain's most common mathematical
operation the awkward spelling while a never-used operation gets the natural one is backwards.

**The decisive evidence is that the language's own author fell into the trap.** `2^(7/12)` was written
in `DerSchmetterling.kt` in three places, evaluated to `2 xor 0` = 2 in all three, and survived two
versions until measurement caught the resulting `hptrack` of 3.0 audibly gutting the guitars' low mids.
No error, no warning — just a plausible wrong number.

**And the codebase already speaks this way.** Every `^` written in prose across `docs/` means
exponentiation, not XOR:

| file | text |
|---|---|
| `20260320-enhanced-distortion-impl.md:41` | `drive = 10^(amount * 1.2)` |
| `20260320-enhanced-distortion.md:48` | `x - x^3/3` |
| `20260618-compressor-gain-smoothing.md:34` | `(e^Kx−1)/(e^K−1)` |
| `20260324-audio-be-module-review.md:279` | `step = 2^(amount / totalFrames)` |
| `audio-backend-audit.md:286` | `2^53` |
| `20260129-strudel-dsl-next-steps.md:80` | `decay^i` |

The people building this engine already think `^` means power when they write mathematics. Only the
parser disagrees.

## Blast radius: essentially zero

A sweep for `^` used as an operator in actual KlangScript code — songs, examples, tutorials, tests —
found:

- **Zero** uses in any `.sprudel` file or built-in song.
- **One** test file: `klangscript/src/commonTest/kotlin/BitwiseOperatorsTest.kt` (`"5 ^ 3 should be 6"`).
- Documentation that *plans* bitwise lessons but does not depend on them:
  `docs/strategy/klangscript-tutorial-plan.md` (lesson listing `&`, `|`, `^`, `~`, `<<`, `>>`) and
  `docs/strategy/klangscript-vs-javascript.md` (a JS-parity table).

There is no user code to break. This is as cheap as a breaking language change ever gets, and it will
only get more expensive from here.

## The design

**`^` becomes exponentiation.** `**` stays as an alias — it already exists
(`DOUBLE_STAR` → `BinaryOperator.POWER`), costs nothing to keep, and means JS-trained users and
copy-pasted code keep working.

**XOR becomes a method, not a new operator.** `a.xor(b)`.

Do **not** invent `^|` or similar. A rare operation deserves a spelled-out name, not more punctuation —
and the number-method mechanism is already being added for `pow()` (see
`klangscript-number-methods.md`), so `xor` costs one more function in the same object. This also matches
Kotlin, which the engine is written in and which spells these `xor`, `and`, `or`, `shl`, `shr`.

**Move only `^`.** `&`, `|`, `~`, `<<`, `>>`, `>>>` collide with nothing musical and can stay as
operators. Splitting `xor` out of that family is a small inconsistency, but moving all of them is a
larger break for no benefit. (Adding `.and()`, `.or()`, `.shl()` etc. as *additional* spellings is
harmless if wanted.)

`^=` follows `^` and becomes power-assign: `x ^= 2` means `x = x ^ 2`.

## Precedence and associativity — the part that must be right

This is where the change goes wrong if it's rushed. `^` today is `BITWISE_XOR` with **very low**
precedence (C-family: below comparison). Power needs **very high** precedence. Simply repointing the
token to `BinaryOperator.POWER` without moving it in the precedence chain would silently regroup
expressions.

Required behaviour:

| expression | must be | note |
|---|---|---|
| `2^3^2` | `512` | **right-associative** — `2^(3^2)`, not `(2^3)^2` |
| `2*3^2` | `18` | binds tighter than `*` and `/` |
| `-2^2` | `-4` | power binds tighter than unary minus **on the left** |
| `2^-1` | `0.5` | the exponent still accepts a unary minus **on the right** |
| `2^(7/12)` | `1.4983…` | the case that started this |

That left/right asymmetry around unary minus is deliberate and matches Python, Julia, R and standard
mathematical notation. `parseUnary` must not swallow the base before `^` is considered.

The existing `POWER` handling for `**` (`KlangScriptParser.kt:1361`, `Interpreter.kt:1260`) already
does `leftValue.value.pow(rightValue.value)` — the evaluation side needs no change, only the token
mapping and the precedence position.

**Not affected:** sprudel mini-notation is parsed by a separate parser from string literals, so pattern
strings are untouched. Regex and string content are likewise unaffected.

## Migration

1. Repoint `TokenType.CARET` → `BinaryOperator.POWER` and move it to the power precedence level,
   right-associative (`KlangScriptParser.kt:1215` is the current XOR binding site).
2. Same for `CARET_EQUALS` → power-assign (`:993`).
3. Add `xor` to `KlangScriptNumberExtensions` alongside `pow` (see the companion doc).
4. Rewrite `BitwiseOperatorsTest.kt`'s XOR cases to `5.xor(3)`, and add the precedence table above as
   new tests.
5. Update `docs/strategy/klangscript-tutorial-plan.md` and `klangscript-vs-javascript.md` — the latter
   gains a row noting this as a deliberate divergence from JavaScript, with the reason.

## The one argument against, and why it loses

KlangScript is JavaScript-shaped — `let`, arrow functions, template literals, method chaining — so a JS
programmer will read `^` as XOR. The change trades one trap for another.

But the two traps are not equal. Today, `2^(7/12)` returns **2**: a plausible number, silently wrong,
essentially undetectable without measuring audio. After the change, `flags ^ mask` returns a
power — a wildly wrong number that fails loudly and immediately, in code that this domain almost never
writes. A rare, loud failure beats a common, silent one.

`klangscript-vs-javascript.md` exists precisely to document where the languages diverge on purpose.
This belongs in it.
