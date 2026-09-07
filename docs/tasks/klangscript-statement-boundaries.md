# KlangScript — statement boundaries: `f()g()` parses as two statements and silently discards one

> **Status (2026-08-21)**: NOT IMPLEMENTED. Found in the wild in `DerSchmetterling.kt`.
> Severity: silent wrong behavior, no diagnostic. Fix is small and well-contained.

## The bug, as found

`DerSchmetterling.kt` (drum section) contained:

```KlangScript
export hats = sound(hats_pat).fast(2).apply(hats_shape).velocity("<1.0 0.85 0.93 0.85>*4")tag("hats")
```

There is a missing `.` before `tag`. This **compiles, renders, and produces audio** — no error, no
warning. What actually happens is that the hats lose their tag.

The chain is:

1. `export hats = sound(...).fast(2).apply(...).velocity(...)` parses as a complete export declaration.
   `velocity(...)` closes, and the expression is finished.
2. The parser returns to its statement loop, sees `tag`, and starts a **new statement**.
3. `parseStatement` falls through every keyword arm to
   `parser/KlangScriptParser.kt:1692` — `else -> parseExpression().let { ExpressionStatement(it, it.location) }`.
4. `tag("hats")` resolves to the bare mapper overload
   `sprudel/.../lang/lang_structural_tag.kt` —
   `fun tag(name: String, callInfo: CallInfo?): PatternMapperFn`. It is a perfectly valid expression
   that evaluates to a `PatternMapperFn`.
5. That value is the result of an `ExpressionStatement` at top level. Nothing consumes it. It is dropped.

So the author wrote a tag, the compiler accepted it, and the events carry no tag. Visualizations reading
`VoicesScheduled.VoiceEvent.data.tags` see nothing for the hats, and there is no way to tell from the
output that anything went wrong — the audio is byte-identical either way, because tags never change the
sound.

## Why nothing catches it

Two independent facts combine:

- **Newlines are not tokens.** The lexer tracks `line` for diagnostics (`KlangScriptParser.kt:300` and
  friends increment it) but never emits a `NEWLINE` token. Whitespace and line breaks are equally
  invisible to the parser.
- **Semicolons are optional separators, not terminators.** Both statement loops are
  `skipSemicolons(); parseStatement(); skipSemicolons()` — `parseProgram` at
  `KlangScriptParser.kt:1844-1847` and `parseBlockStatements` at `:1893-1896`. `skipSemicolons()`
  (`:928`) consumes any number of them, including zero.

The consequence is that **there is no such thing as a statement boundary in KlangScript**. Two
expressions juxtaposed with nothing between them are two statements, on the same line or not. `a()b()`,
`1 2 3`, and `foo bar` are all currently valid programs.

This is worse than JavaScript's ASI. ASI at least only inserts semicolons at newlines; here the
separator is the empty string.

## Why it matters more in KlangScript than in a general language

Every pattern method returns a pattern, and there is a bare function overload for most of them
(`tag`, `pan`, `gain`, `note`, …) so that `apply(...)`/`superimpose(...)` can take them as mappers.
That is a good design, but it means **a dropped `.` almost always still type-checks**: `x.pan(0.7)`
and `pan(0.7)` are both legal, so the truncated chain parses and the orphan evaluates.

The failure is therefore silent by construction, and it gets quieter the more the DSL grows. A missing
dot in front of `.gain()` or `.lpf()` would change the sound and be caught by ear. A missing dot in
front of `.tag()` changes only metadata, and nothing in the pipeline complains.

## Proposed fix

### Phase 1 — reject two statements on one line (minimal, recommended)

**Rule:** a new statement may not begin on the same source line as the end of the previous statement
unless a `;` was consumed between them.

Everything needed is already in the token stream: `Token` carries `line`, `column`, `endLine` and
`endColumn` (`KlangScriptParser.kt:235-242`). In both statement loops, track whether `skipSemicolons()`
actually consumed anything; if it did not, and `peek().line == previous().endLine`, raise a parse error:

> Expected a newline or `;` between statements. Did you mean `.tag(…)`?

The "did you mean" hint is worth including when the offending token is an identifier that also exists as
a pattern method — that is exactly the missing-dot case, and it is the overwhelmingly likely intent.

This rule is deliberately narrow. It cannot break any multi-line chain, because it only fires when two
statements *share a line*.

### Phase 2 — full newline sensitivity (optional, do not rush)

The stricter rule — a newline ends a statement unless the expression is syntactically incomplete — is
what Kotlin, Swift, and Go do, and it would additionally catch a dropped `.` at a line break:

```KlangScript
export hats = sound(hats_pat)
  .velocity("…")
  tag("hats")        // Phase 1 accepts this; Phase 2 rejects it
```

**This needs care, and it is why Phase 1 should ship first.** The house style throughout the codebase
is leading-dot continuation lines:

```KlangScript
export guitar1_shape = x => x.gain(0.5).sound(guitar)
  .oscp("hptrack", 1.00).oscp("hpq", 0.7)
  .clip("…").adsr("…")
  .pan(0.55).superimpose(pan(0.65))
```

Any newline-terminates rule must therefore continue the expression when the next line starts with `.`
(and with `?.`, and after a trailing binary operator, and inside unclosed brackets). Get one of those
wrong and every song in the repo stops compiling. Phase 1 buys most of the safety for a fraction of the
risk.

## Test cases

Must be a parse error after Phase 1:

```KlangScript
s("bd*4")tag("drums")           // the reported bug
let a = f() g()
note("c3") pan(0.5)
1 2
```

Must still parse (regression guards — these are the ones that matter):

```KlangScript
let a = 1; let b = 2            // explicit ; on one line
let a = 1;;; let b = 2          // skipSemicolons tolerates runs
s("bd*4")
  .gain(0.5)                    // leading-dot continuation
  .pan(0.3)
x => x.gain(0.5).pan(0.2)       // arrow bodies
stack(
  a.tag("x"),
  b.tag("y")
).tag("band")                   // multi-line call args
if (a) f() else g()             // if-expression arms on one line
for (let i = 0; i < 4; i++) { } // ; inside the for header, not a statement separator
```

That last one is the trap: `parseForStatement` (`KlangScriptParser.kt:2017-2038`) uses `;` as a
*separator inside the header*, not as a statement terminator. The new rule must not run there.

## Scope note

This is a diagnostics change, not a semantics change. No currently-correct program changes meaning —
programs that were silently dropping a value start failing loudly instead. That is the point.

`DerSchmetterling.kt` should get its missing `.` regardless, independent of this task.
