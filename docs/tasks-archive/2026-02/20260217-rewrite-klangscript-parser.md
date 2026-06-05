Here's the plan:

---

# Plan: Rewrite KlangScriptParser as a Hand-Rolled Recursive Descent Parser

## Motivation

The current parser uses `better-parse` (combinator library), which is broken under Kotlin/JS production builds (
see [better-parse#66](https://github.com/h0tk3y/better-parse/issues/66)). The fix is to replace it with a
zero-dependency hand-rolled lexer + recursive descent parser.

## Scope

- **Replace**: `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`
- **Keep unchanged**: All AST types in `klangscript/src/commonMain/kotlin/ast/Ast.kt`
- **Keep unchanged**: All call sites — the public API `KlangScriptParser.parse(source, sourceName)` returning `Program`
  stays identical
- **Keep unchanged**: All test files — they serve as the **verification suite**
- **Remove after**: `better-parse` dependency from `klangscript/build.gradle.kts`, `strudel/build.gradle.kts`, and
  `klang-notebook/build.gradle.kts`

## Reference Material

- **AST types**: `klangscript/src/commonMain/kotlin/ast/Ast.kt` — defines every node type the parser must produce
- **Existing parser**: `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt` — the authoritative grammar
  specification
- **Working example**: `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt` — an existing hand-rolled
  recursive descent parser in this project, use as a style reference
- **Test suite**: `klangscript/src/commonTest/kotlin/` — ~40+ test files that must all pass unchanged

## Architecture

The new parser consists of two phases in a single file:

### Phase 1: Lexer (Tokenizer)

Converts source string → `List<Token>`.

**Token types** (enum):

```
NUMBER, STRING, BACKTICK_STRING, IDENTIFIER,
TRUE, FALSE, NULL, LET, CONST, IMPORT, EXPORT, FROM, AS, RETURN,
LEFT_PAREN, RIGHT_PAREN, COMMA,
LEFT_BRACE, RIGHT_BRACE, COLON,
LEFT_BRACKET, RIGHT_BRACKET,
ARROW,          // =>
DOUBLE_EQUALS,  // ==
NOT_EQUALS,     // !=
LESS_EQUAL,     // <=
GREATER_EQUAL,  // >=
EQUALS,         // =
PLUS, MINUS, STAR, SLASH, PERCENT,
EXCLAMATION,    // !
LESS_THAN, GREATER_THAN,
DOUBLE_AMP,     // &&
DOUBLE_PIPE,    // ||
DOT,
EOF
```

**Token data class**: `Token(type, text, line, column)` — 1-based line/column for `SourceLocation`.

**Lexer rules** (order matters for multi-char tokens):

1. Skip whitespace (`\s+`)
2. Skip single-line comments (`//` to end of line)
3. Skip multi-line comments (`/* ... */`)
4. Multi-char tokens first: `=>`, `==`, `!=`, `<=`, `>=`, `&&`, `||`
5. Single-char tokens: `( ) , { } : [ ] = + - * / % ! < > .`
6. Backtick strings: `` ` ... ` `` (with escape handling)
7. Double/single-quoted strings: `"..."` or `'...'` (with escape handling)
8. Numbers: `\d+(\.\d+)?`
9. Keywords/identifiers: `[a-zA-Z_][a-zA-Z0-9_]*` — check against keyword map
10. Track line/column for every token (increment line on `\n`, reset column)

### Phase 2: Recursive Descent Parser

Consumes `List<Token>` → `Program` AST.

**Precedence levels** (lowest to highest):

| Level | Method                  | Constructs                                        |
|-------|-------------------------|---------------------------------------------------|
| 1     | `parseArrowExpr()`      | `x => expr`, `(a,b) => expr`, `() => { ... }`     |
| 2     | `parseLogicalOr()`      | `a \|\| b`                                        |
| 3     | `parseLogicalAnd()`     | `a && b`                                          |
| 4     | `parseComparison()`     | `==`, `!=`, `<`, `<=`, `>`, `>=`                  |
| 5     | `parseAddition()`       | `+`, `-`                                          |
| 6     | `parseMultiplication()` | `*`, `/`, `%`                                     |
| 7     | `parseUnary()`          | `-expr`, `+expr`, `!expr`                         |
| 8     | `parseCallExpr()`       | `foo(...)`, `obj.prop`, chaining                  |
| 9     | `parsePrimary()`        | literals, identifiers, `(expr)`, `{...}`, `[...]` |

**Statement parsing** (`parseStatement()`):

- Check current token to decide: `import`, `export`, `let`, `const`, `return`, or expression statement

**Arrow function parsing** (trickiest part):

- This requires **backtracking** or lookahead since `x` could be an identifier OR an arrow function parameter
- Strategy: If current token is identifier and next is `=>`, parse as single-param arrow. If current token is `(`, try
  to parse as `(params) =>` — if `=>` follows the `)`, commit to arrow function; otherwise backtrack and parse as
  parenthesized expression
- Save/restore position for backtracking

**Binary operators** (levels 2-6):

- All use the same left-associative pattern:

```
fun parseLevel(): Expression {
    var left = parseHigherLevel()
    while (currentToken matches this level's operators) {
        val op = consume operator
        val right = parseHigherLevel()
        left = BinaryOperation(left, op, right, location)
    }
    return left
}
```

**Call expressions + member access** (level 8):

- Parse primary, then loop consuming `.identifier` or `(args)` suffixes (fold left)

**Source locations**:

- Every AST node must include `SourceLocation(source, startLine, startColumn, endLine, endColumn)`
- Capture start position before parsing a construct, end position after
- Use `currentSource` (the constructor parameter) for the `source` field

## Steps

1. **Create the new parser file**: `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt` — replace the
   contents entirely
2. **Keep the public API identical**: `KlangScriptParser.parse(source, sourceName): Program`
3. **Run the existing test suite**: All ~40 test files in `klangscript/src/commonTest/kotlin/` must pass
4. **Remove `better-parse` dependency** from:
    - `klangscript/build.gradle.kts` (line: `api(Deps.KotlinLibs.better_parse)`)
    - `strudel/build.gradle.kts` (line: `implementation(Deps.KotlinLibs.better_parse)`)
    - `klang-notebook/build.gradle.kts` (line: `api(Deps.KotlinLibs.better_parse)`)
5. **Remove compiler flags** that were added as workaround (the `-Xir-property-lazy-initialization` and
   `-Xir-minimized-member-names` flags in `klangscript/build.gradle.kts` and `build.gradle.kts`)

## Key Gotchas

- **Arrow function ambiguity**: `x => x + 1` vs `x` as identifier — needs lookahead/backtracking
- **Object literal vs block body**: `{ a: 1 }` (object) vs `{ statement; }` (block) — in arrow bodies, `{` after `=>` is
  a block body; in expression position, `{` starts an object literal
- **Trailing commas**: Allowed in function args, array elements, object properties, arrow params
- **Operator token ordering in lexer**: `=>` before `=`, `==` before `=`, `!=` before `!`, `<=` before `<`, `>=` before
  `>`, `&&` before `&`, `||` before `|`
- **String escape sequences**: Handle `\\`, `\"`, `\'`, `\n`, `\t` etc. inside string literals
- **SourceLocation end position**: The existing parser computes `endColumn` as exclusive (character after last).
  Maintain this convention
- **ParseException**: Throw a clear exception with location info on syntax errors (the existing tests check for
  `ParseException` from better-parse — you may need to create your own exception class or catch/rethrow)
