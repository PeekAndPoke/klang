# AST-Based Argument Finder for Tool Badges

## Goal

Replace the hand-rolled text scanner in `ArgFinder.kt` (`findCallArgAt`) with an AST-based
approach that uses the parsed `Program` tree to locate function call arguments at a given
cursor position. This gives correct results for nested calls, complex expressions, lambdas,
and multiline code — all cases where the current paren-counting scanner is fragile.

## What We Have

| Piece                                                   | Location                                  | Status                 |
|---------------------------------------------------------|-------------------------------------------|------------------------|
| `CallExpression` with `arguments: List<Expression>`     | `klangscript/ast/Ast.kt:502`              | exists                 |
| `SourceLocation` on every AST node (line/col, 1-based)  | `klangscript/ast/Ast.kt:28`               | exists                 |
| `KlangScriptParser.parse(code) → Program`               | `klangscript/parser/KlangScriptParser.kt` | exists                 |
| `EditorDocContext` — parses code on every edit          | `codemirror/EditorDocContext.kt`          | exists, discards AST   |
| `findCallArgAt` — text scanner returning `CallArgInfo`  | `codemirror/ArgFinder.kt:42`              | exists, to be replaced |
| `argInfoAt` — calls `findCallArgAt` from mouse events   | `codemirror/DslEditorExtension.kt:134`    | exists, consumer       |
| `KlangSymbol` / `KlangCallable` with param metadata     | `klangscript/types/`                      | exists                 |
| `KlangUiToolRegistry.resolve()` for `@param-tool` tools | `klangscript-ui/`                         | exists                 |

## Current Flow

```
mousemove event
  → argInfoAt(event, view)
    → source = view.state.doc.toString()
    → pos = view.posAtCoords(mouse coords)
    → findCallArgAt(source, pos, docProvider)   ← text scanner
      → scan backward for enclosing `(`
      → extract function name from text
      → scan forward counting parens/commas/strings
      → return CallArgInfo(argFrom, argTo, argText, paramIndex, ...)
  → showBadges(argInfo, view, mouseX)
```

## Target Flow

```
code changed (debounced)
  → EditorDocContext.processCode(code)
    → KlangScriptParser.parse(code) → Program
    → cache Program (new)
    → extract imports (existing)

mousemove event
  → argInfoAt(event, view)
    → pos = view.posAtCoords(mouse coords)
    → findCallArgAtAst(cachedProgram, pos, docProvider)   ← AST walker
      → walk AST to find deepest CallExpression containing pos
      → match argument index from argument node positions
      → return CallArgInfo(argFrom, argTo, argText, paramIndex, ...)
  → showBadges(argInfo, view, mouseX)
```

## Implementation Steps

### Step 1: Cache the parsed Program in EditorDocContext

**File:** `klangscript-ui/src/jsMain/kotlin/codemirror/EditorDocContext.kt`

Currently `extractImports()` (line 68) calls `KlangScriptParser.parse(code)` and discards
the `Program`, keeping only import names. Change this to:

- Add a field: `var lastProgram: Program? = null`
- In `extractImports` (or rename to `processParseResult`), store the parsed `Program`
  before extracting imports from it
- On parse error, keep `lastProgram` as-is (stale AST is better than none for hover)

### Step 2: Position conversion utility

**Problem:** AST nodes store `SourceLocation(startLine, startColumn, endLine, endColumn)`
(1-based) while CodeMirror and `CallArgInfo` use character offsets into the source string.

**Solution:** Write a utility function:

```kotlin
fun sourceLocationToOffsets(
    source: String,
    location: SourceLocation
): Pair<Int, Int>  // (from, to) as character offsets
```

Implementation: build a `lineStartOffsets: IntArray` from the source string (index of first
char on each line), then convert:

- `from = lineStartOffsets[startLine - 1] + (startColumn - 1)`
- `to = lineStartOffsets[endLine - 1] + (endColumn - 1)`

This array can be cached alongside the `Program` since it only changes when the source changes.

**File:** Add to `ArgFinder.kt` or a new small utility in the same package.

### Step 3: AST walker — find deepest CallExpression at cursor

Write a function that walks the AST and finds the innermost `CallExpression` whose
source range contains the cursor position. This mirrors the Interpreter's recursive
dispatch pattern (`evaluate` / `evaluateCall` / etc.) but only cares about position
containment.

```kotlin
fun findCallExpressionAt(
    program: Program,
    pos: Int,                        // character offset
    source: String,                  // for offset conversion
): CallExpressionAtResult?

data class CallExpressionAtResult(
    val call: CallExpression,
    val argIndex: Int,               // which argument the cursor is in (-1 if on callee)
    val argument: Expression?,       // the argument node, if argIndex >= 0
)
```

**Algorithm:**

1. Walk all statements recursively
2. For each `CallExpression`, check if `pos` is within its source range
3. If yes, check each `argument` to see if `pos` falls within that argument's range
4. Recurse into the matching argument (it might contain a nested `CallExpression`)
5. Return the deepest match

**Key expression types to recurse into** (from `Ast.kt`):

- `CallExpression` — recurse into `callee` and each `arguments[i]`
- `MemberAccess` — recurse into `object` expression
- `BinaryOperation` — recurse into `left`, `right`
- `UnaryOperation` — recurse into `operand`
- `TernaryExpression` — recurse into `condition`, `consequent`, `alternate`
- `IndexAccess` — recurse into `object`, `index`
- `AssignmentExpression` — recurse into `value`
- `ArrayLiteral` — recurse into `elements`
- `ObjectLiteral` — recurse into values
- `ArrowFunction` / `FunctionExpression` — recurse into `body`
- Leaf nodes (`NumberLiteral`, `StringLiteral`, `Identifier`, etc.) — no children

For statements, recurse into their child expressions/blocks:

- `ExpressionStatement` → expression
- `VariableDeclaration` → initializer
- `BlockStatement` → statements
- `IfStatement`, `WhileLoop`, `ForLoop` → condition + body
- `ReturnStatement` → value

### Step 4: Build CallArgInfo from AST result

Replace `findCallArgAt` with a new function that uses the AST walker:

```kotlin
fun findCallArgAtAst(
    program: Program,
    source: String,
    pos: Int,
    docProvider: (String) -> KlangSymbol?,
): CallArgInfo?
```

**Logic:**

1. Call `findCallExpressionAt(program, pos, source)` to get the call + arg index
2. Extract the function name from `call.callee`:
    - If `Identifier` → use `name`
    - If `MemberAccess` → use `member` (the method name)
3. Look up via `docProvider(functionName)`
4. Convert the argument's `SourceLocation` to character offsets
5. Extract `argText` from source using those offsets, trim whitespace
6. Resolve param name and tools from `KlangCallable.params[argIndex]`
7. Return `CallArgInfo` with all fields populated

### Step 5: Wire it up in DslEditorExtension

**File:** `klangscript-ui/src/jsMain/kotlin/codemirror/DslEditorExtension.kt`

Change `argInfoAt` (line 134) to use the cached AST:

```kotlin
fun argInfoAt(event: dynamic, view: EditorView): CallArgInfo? {
    val program = docContext.lastProgram ?: return null
    val source = view.state.doc.toString()
    val coords = jsObject<dynamic> { this.x = event.clientX; this.y = event.clientY }.unsafeCast<Coords>()
    val pos = view.posAtCoords(coords) ?: return null
    return findCallArgAtAst(program, source, pos, docProvider)
}
```

This requires passing `docContext` (or just the cached program) into `dslEditorExtension()`.
Currently `docProvider` is already a lambda parameter — add a `programProvider: () -> Program?`
lambda alongside it.

### Step 6: Fallback / migration

Keep the old `findCallArgAt` text scanner as a fallback during development:

- If `lastProgram` is null (parse failed entirely), fall back to text scanner
- Once confident the AST approach covers all cases, remove the text scanner

## Testing Strategy

The AST walker and `findCallArgAtAst` live in `commonMain`-compatible code (or at least
the walker does — `CallArgInfo` uses `KlangUiTool` which is JS-only). Consider:

1. **Unit tests for the walker** in `commonTest`: parse a code snippet, call
   `findCallExpressionAt` with various cursor positions, assert correct call + arg index.

2. **Test cases that broke the text scanner:**
    - Nested calls: `foo(bar(1), 2)` — cursor on `1` should resolve to `bar`, param 0
    - Nested calls: `foo(bar(1), 2)` — cursor on `2` should resolve to `foo`, param 1
    - Method chains: `"c3".note().gain(0.5)` — cursor on `0.5` → `gain`, param 0
    - String args with parens: `foo("hello (world)")` — cursor on string → `foo`, param 0
    - Multiline calls
    - Lambda arguments

3. **Position conversion tests**: verify `sourceLocationToOffsets` against known source strings.

## Files Changed (Summary)

| File                               | Change                                                                               |
|------------------------------------|--------------------------------------------------------------------------------------|
| `codemirror/EditorDocContext.kt`   | Cache `Program` from parse                                                           |
| `codemirror/ArgFinder.kt`          | Add `findCallArgAtAst`, AST walker, position converter; keep old scanner as fallback |
| `codemirror/DslEditorExtension.kt` | Pass program provider into extension; update `argInfoAt`                             |
| `codemirror/CodeMirrorComp.kt`     | Thread program provider through `dslEditorExtension()` call                          |

## Risks / Open Questions

- **Parse errors**: When the user is mid-edit, the parser may fail. The cached `Program`
  will be stale (from the last successful parse). This is acceptable — the text scanner has
  the same blind spot since it operates on potentially broken code too. The stale AST may
  actually be more useful since it represents the last valid state.

- **Performance**: The AST walk runs on every `mousemove`. The tree is typically small
  (live coding scripts are short). If needed, cache the walk result keyed on `pos` and
  invalidate on code change.

- **SourceLocation completeness**: Verify that `CallExpression.location` spans from the
  callee through the closing `)`. If it only spans the parens `(...)`, the walker still
  works — just need to check argument positions individually. Worth verifying in the parser
  (`parseCallExpression` around `KlangScriptParser.kt:1337`).

- **MemberAccess chains**: `a.b.c(x)` — the callee is `MemberAccess(MemberAccess(a, b), c)`.
  The function name to look up is `c`. The walker needs to extract the rightmost member name.

## Related

- [KlangScript IntelliSense plan](klangscript-intellisense.md) — proposes a
  `KlangScriptAnalyzer` that walks the AST similarly. The walker from this task could be
  reused or shared with that effort.
