# KlangScript — Parser Implementation

## Overview

Hand-rolled lexer + recursive descent parser in `parser/KlangScriptParser.kt` (~1005 lines).
Zero external dependencies. Replaces better-parse (broken in Kotlin/JS production builds).

## Token Types (40+)

Keywords: `let`, `const`, `return`, `import`, `export`, `from`, `as`, `true`, `false`, `null`
Literals: `NUMBER`, `STRING`, `IDENTIFIER`
Operators: `PLUS`, `MINUS`, `STAR`, `SLASH`, `PERCENT`, `BANG`, `EQ`, `NEQ`, `LT`, `LTE`, `GT`, `GTE`, `AND`, `OR`,
`ARROW`
Punctuation: `LPAREN`, `RPAREN`, `LBRACE`, `RBRACE`, `LBRACKET`, `RBRACKET`, `DOT`, `COMMA`, `COLON`, `SEMICOLON`

**Critical:** Multi-char tokens must be lexed before single-char tokens:

- `==` before `=`, `!=` before `!`, `<=` before `<`, `>=` before `>`, `=>` before `=`

## Expression Precedence (lowest → highest)

1. `arrowExpr` — `x => expr` / `x => { ... }` (with backtracking for multi-token params)
2. `logicalOrExpr` — `||`
3. `logicalAndExpr` — `&&`
4. `comparisonExpr` — `==`, `!=`, `<`, `<=`, `>`, `>=`
5. `additionExpr` — `+`, `-`
6. `multiplicationExpr` — `*`, `/`, `%`
7. `unaryExpr` — `-`, `+`, `!`. **`-` directly before a `NUMBER` token folds into one negative
   literal** and then enters the postfix loop, so `-1.0.clamp(0, 1)` is `(-1.0).clamp(0, 1)` (maintainer
   decision 2026-09-08, `docs/tasks/klangscript-number-methods.md`; Kotlin/JS would read `-(...)`).
   Only a literal folds: `-x.abs()` is `-(x.abs())`. The `--` token folds its second minus the same way
   (`--1.abs()` is `-((-1).abs())`, helper `negativeLiteral`).
8. `postfixExpr` (`parsePostfix(start)`) — call `foo()` and member `.prop` — **loop pattern, any alternating order**
9. `primaryExpr` — literals, identifiers, `(...)`, `{...}`, `[...]`

## Critical Implementation Details

**Method chaining** uses a postfix loop (not recursion):

```kotlin
var expr = parsePrimary()
while (true) {
    expr = when {
        check(DOT) -> parseMemberAccess(expr)
        check(LPAREN) -> parseCallExpr(expr)
        else -> break
    }
}
```

**Arrow object literal disambiguation** — `x => { key: val }` vs `x => { stmt }`:

- After `=>` and `{`, peek for `identifier` followed by `:` → treat as object literal
- Otherwise → treat as block body

**Arrow function backtracking** — try to parse `(params) =>` first; if `=>` not found, backtrack and re-parse as
parenthesized expression.

**Number scanning** (`scanDecimalEnd(codes, from)`, a companion function so the lexer index `i` is never
captured and boxed): digits, at most one fraction, optional exponent. A `.` joins the number only when a
digit or a complete exponent follows it, so `2.5` and `2.e5` are one token while `2.pow(2)` lexes as `2`
`.` `pow` and `2.exp()` as `2` `.` `exp`; a trailing `2.` or a doubled `1.2.3` reaches the parser as a stray
dot and fails there. Spec: `parser/NumberLiteralMethodCallSpec.kt`.

**Source locations** — 1-based line and column. `Token` tracks `startLine`, `endLine`, `startCol`.

## Key Files

- `parser/KlangScriptParser.kt` — lexer + parser
- `parser/ParseException.kt` — custom parse error
- `ast/Ast.kt` — all AST sealed classes (**do not modify without understanding all downstream impacts**)
