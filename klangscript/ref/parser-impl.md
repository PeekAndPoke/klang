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
7. `unaryExpr` — `-`, `+`, `!`
8. `postfixExpr` — call `foo()` and member `.prop` — **loop pattern, any alternating order**
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

**Source locations** — 1-based line and column. `Token` tracks `startLine`, `endLine`, `startCol`.

## Key Files

- `parser/KlangScriptParser.kt` — lexer + parser
- `parser/ParseException.kt` — custom parse error
- `ast/Ast.kt` — all AST sealed classes (**do not modify without understanding all downstream impacts**)
