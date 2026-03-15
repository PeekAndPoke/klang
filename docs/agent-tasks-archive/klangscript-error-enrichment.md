# KlangScript Error Enrichment

## Goal

Every error (parse or runtime) must carry enough information for the user to quickly find
and understand the problem. This means: source location, context about what went wrong,
and ideally a reference to the AST node that caused the error.

## Design — Type Hierarchy

```
KlangScriptError (sealed interface)
├── errorType: KlangScriptErrorType (enum)
├── location: SourceLocation?
├── format(): String
│
├── KlangScriptParseError (sealed class, extends Exception)
│   │  No stackTrace, no astNode (AST not built yet)
│   └── KlangScriptSyntaxError
│
└── KlangScriptRuntimeError (sealed class, extends RuntimeException)
    │  Has astNode: AstNode?, stackTrace: List<CallStackFrame>
    ├── KlangScriptTypeError           (was: TypeError)
    ├── KlangScriptReferenceError      (was: ReferenceError)
    ├── KlangScriptArgumentError       (was: ArgumentError)
    ├── KlangScriptImportError         (was: ImportError)
    ├── KlangScriptAssignmentError     (was: AssignmentError)
    └── KlangScriptStackOverflowError  (was: StackOverflowError)
```

### Error Type Enum

```kotlin
enum class KlangScriptErrorType {
    SyntaxError,
    TypeError,
    ReferenceError,
    ArgumentError,
    ImportError,
    AssignmentError,
    StackOverflowError,
}
```

### Naming Convention

All error classes start with `KlangScript*`. No magic strings for error types — use the enum.

### Control Flow Exceptions (unchanged)

`ReturnException`, `BreakException`, `ContinueException` stay separate — internal, not errors.

### Deleted Classes

- `ParseException` → replaced by `KlangScriptSyntaxError`
- `ErrorResult` → replaced by `SourceLocation`
- `TypeError` → replaced by `KlangScriptTypeError`
- `ReferenceError` → replaced by `KlangScriptReferenceError`
- `ArgumentError` → replaced by `KlangScriptArgumentError`
- `ImportError` → replaced by `KlangScriptImportError`
- `AssignmentError` → replaced by `KlangScriptAssignmentError`
- `StackOverflowError` → replaced by `KlangScriptStackOverflowError`

## Current State — Full Inventory

### Error Hierarchies (before)

| Hierarchy      | Base Class                       | Location Type                              | Files                      |
|----------------|----------------------------------|--------------------------------------------|----------------------------|
| Runtime errors | `KlangScriptError` (sealed)      | `SourceLocation?`                          | `runtime/Errors.kt`        |
| Parse errors   | `ParseException` (standalone)    | raw `line`/`column` ints via `ErrorResult` | `parser/ParseException.kt` |
| Stdlib errors  | `IllegalArgumentException` (JDK) | **none**                                   | `stdlib/KlangStdLib.kt`    |

### Complete Throw-Site Inventory

#### Interpreter.kt — 33 throws

| #  | Line | Old Type        | Trigger                               | Location                | Stack | Status                                              |
|----|------|-----------------|---------------------------------------|-------------------------|-------|-----------------------------------------------------|
| 1  | 252  | `ImportError`   | Library load failure                  | `importStmt.location`   | yes   | OK                                                  |
| 2  | 305  | `ImportError`   | Namespace + selective import conflict | **`null`**              | yes   | BROKEN — `importStmt.location` available via caller |
| 3  | 324  | `ImportError`   | Non-exported symbol import            | **`null`**              | yes   | BROKEN — `importStmt.location` available via caller |
| 4  | 520  | `ArgumentError` | Wrong arg count on arrow function     | `call.location`         | yes   | OK                                                  |
| 5  | 587  | `TypeError`     | Calling non-function value            | `call.location`         | yes   | OK                                                  |
| 6  | 646  | `TypeError`     | Unary `-` on non-number               | `unaryOp.location`      | yes   | OK                                                  |
| 7  | 659  | `TypeError`     | Unary `+` on non-number               | `unaryOp.location`      | yes   | OK                                                  |
| 8  | 679  | `TypeError`     | Prefix `++` on non-identifier         | `unaryOp.location`      | yes   | OK                                                  |
| 9  | 688  | `TypeError`     | Prefix `++` on non-number             | `unaryOp.location`      | yes   | OK                                                  |
| 10 | 704  | `TypeError`     | Prefix `--` on non-identifier         | `unaryOp.location`      | yes   | OK                                                  |
| 11 | 713  | `TypeError`     | Prefix `--` on non-number             | `unaryOp.location`      | yes   | OK                                                  |
| 12 | 729  | `TypeError`     | Postfix `++` on non-identifier        | `unaryOp.location`      | yes   | OK                                                  |
| 13 | 738  | `TypeError`     | Postfix `++` on non-number            | `unaryOp.location`      | yes   | OK                                                  |
| 14 | 753  | `TypeError`     | Postfix `--` on non-identifier        | `unaryOp.location`      | yes   | OK                                                  |
| 15 | 762  | `TypeError`     | Postfix `--` on non-number            | `unaryOp.location`      | yes   | OK                                                  |
| 16 | 865  | `TypeError`     | `in` operator, left not string        | `binOp.location`        | yes   | OK                                                  |
| 17 | 873  | `TypeError`     | `in` operator, right not object       | `binOp.location`        | yes   | OK                                                  |
| 18 | 890  | `TypeError`     | Binary arithmetic on non-numbers      | `binOp.location`        | yes   | OK                                                  |
| 19 | 920  | `TypeError`     | Binary comparison on non-numbers      | `binOp.location`        | yes   | OK                                                  |
| 20 | 938  | `TypeError`     | Division by zero                      | `binOp.location`        | yes   | OK                                                  |
| 21 | 950  | `TypeError`     | Modulo by zero                        | `binOp.location`        | yes   | OK                                                  |
| 22 | 1076 | `TypeError`     | Native type has no method             | `memberAccess.location` | yes   | OK                                                  |
| 23 | 1102 | `TypeError`     | Type has no method                    | `memberAccess.location` | yes   | OK                                                  |
| 24 | 1114 | `TypeError`     | Property access on non-object         | `memberAccess.location` | yes   | OK                                                  |
| 25 | 1238 | `TypeError`     | Member assign on non-object           | `assignment.location`   | yes   | OK                                                  |
| 26 | 1257 | `TypeError`     | Array index not a number              | `assignment.location`   | yes   | OK                                                  |
| 27 | 1266 | `TypeError`     | Array index out of bounds             | `assignment.location`   | yes   | OK                                                  |
| 28 | 1279 | `TypeError`     | Object key not a string               | `assignment.location`   | yes   | OK                                                  |
| 29 | 1290 | `TypeError`     | Index-assign on non-indexable         | `assignment.location`   | yes   | OK                                                  |
| 30 | 1299 | `TypeError`     | Invalid assignment target             | `assignment.location`   | yes   | OK                                                  |
| 31 | 1339 | `TypeError`     | Array index not a number (read)       | `indexAccess.location`  | yes   | OK                                                  |
| 32 | 1356 | `TypeError`     | Object key not a string (read)        | `indexAccess.location`  | yes   | OK                                                  |
| 33 | 1366 | `TypeError`     | Index into non-indexable (read)       | `indexAccess.location`  | yes   | OK                                                  |

#### Environment.kt — 4 throws

| #  | Line | Old Type          | Trigger                      | Location         | Stack    | Status |
|----|------|-------------------|------------------------------|------------------|----------|--------|
| 34 | 128  | `ReferenceError`  | Undefined variable           | `location` param | yes      | OK     |
| 35 | 154  | `AssignmentError` | Reassign const               | `location` param | yes      | OK     |
| 36 | 166  | `ReferenceError`  | Assign to undefined variable | `location` param | yes      | OK     |
| 37 | 249  | `ImportError`     | Library not found            | **none**         | **none** | BROKEN |

#### NativeInterop.kt — 4 throws

| #  | Line | Old Type        | Trigger                                      | Location | Stack    | Status |
|----|------|-----------------|----------------------------------------------|----------|----------|--------|
| 38 | 34   | `ArgumentError` | Wrong arg count (native fn)                  | **none** | **none** | BROKEN |
| 39 | 98   | `TypeError`     | Cannot convert value to Kotlin type          | **none** | **none** | BROKEN |
| 40 | 161  | `TypeError`     | Script fn has too many params for conversion | **none** | **none** | BROKEN |
| 41 | 215  | `ArgumentError` | Missing expected arg at index                | **none** | **none** | BROKEN |

#### CallStack.kt — 1 throw

| #  | Line | Old Type             | Trigger                 | Location         | Stack    | Status  |
|----|------|----------------------|-------------------------|------------------|----------|---------|
| 42 | 69   | `StackOverflowError` | Max call depth exceeded | `location` param | **none** | PARTIAL |

#### KlangStdLib.kt — 6 throws (ALL wrong exception type)

| #  | Line | Old Type                   | Trigger                           | Location | Stack    | Status              |
|----|------|----------------------------|-----------------------------------|----------|----------|---------------------|
| 43 | 144  | `IllegalArgumentException` | `Object.keys()` non-object arg    | **none** | **none** | BROKEN — wrong type |
| 44 | 150  | `IllegalArgumentException` | `Object.values()` non-object arg  | **none** | **none** | BROKEN — wrong type |
| 45 | 155  | `IllegalArgumentException` | `Object.entries()` non-object arg | **none** | **none** | BROKEN — wrong type |
| 46 | 242  | `IllegalArgumentException` | `concat()` non-array arg          | **none** | **none** | BROKEN — wrong type |
| 47 | 293  | `IllegalArgumentException` | `Number()` conversion failure     | **none** | **none** | BROKEN — wrong type |
| 48 | 305  | `IllegalArgumentException` | `Boolean()` conversion failure    | **none** | **none** | BROKEN — wrong type |

#### KlangScriptParser.kt — 4 throws (separate hierarchy)

| #  | Line | Old Type         | Trigger                             | Location                    | Stack | Status          |
|----|------|------------------|-------------------------------------|-----------------------------|-------|-----------------|
| 49 | 468  | `ParseException` | Unterminated string                 | `line`/`column` ints        | N/A   | NEEDS MIGRATION |
| 50 | 509  | `ParseException` | Unterminated backtick string        | `line`/`column` ints        | N/A   | NEEDS MIGRATION |
| 51 | 573  | `ParseException` | Unexpected character                | `line`/`column` ints        | N/A   | NEEDS MIGRATION |
| 52 | 614  | `ParseException` | General parse error (via `error()`) | `token.line`/`token.column` | N/A   | NEEDS MIGRATION |

#### Control Flow (NOT errors — unchanged)

| Line | Type                | Purpose                         |
|------|---------------------|---------------------------------|
| 182  | `ReturnException`   | Return statement control flow   |
| 214  | `BreakException`    | Break statement control flow    |
| 217  | `ContinueException` | Continue statement control flow |

### Summary

| Status                          | Count  |
|---------------------------------|--------|
| OK (location + stackTrace)      | 33     |
| BROKEN — missing location/stack | 11     |
| BROKEN — wrong exception type   | 6      |
| NEEDS MIGRATION — parse errors  | 4      |
| **Total throw sites**           | **54** |

## Implementation Plan

### Step 1: New error hierarchy in `runtime/Errors.kt`

- Create `KlangScriptErrorType` enum
- Create `KlangScriptError` sealed interface
- Create `KlangScriptParseError` sealed class (extends `Exception`)
- Create `KlangScriptSyntaxError` concrete class
- Create `KlangScriptRuntimeError` sealed class (extends `RuntimeException`) with `astNode`, `stackTrace`
- Create all runtime subclasses: `KlangScriptTypeError`, `KlangScriptReferenceError`, etc.
- Keep old classes temporarily as deprecated typealiases for compilation

### Step 2: Migrate parser — delete `ParseException` + `ErrorResult`

- Update 4 throw sites in `KlangScriptParser.kt` to throw `KlangScriptSyntaxError` with `SourceLocation`
- Delete `parser/ParseException.kt`

### Step 3: Migrate Interpreter.kt — rename all 33 throws

- Mechanical rename: `TypeError(` → `KlangScriptTypeError(`, etc.
- Fix 2 `ImportError` throws with `location = null` → pass `importStmt.location`

### Step 4: Migrate Environment.kt — rename + fix

- Rename 3 OK throws
- Fix `ImportError` at line 249 — add location parameter

### Step 5: Thread location through NativeInterop + KlangScriptExtensionBuilder

- Add `location: SourceLocation?` to `checkArgsSize`, `convertArgToKotlin`
- Update ~15 registration helpers to pass `callLocation` instead of `_`
- Rename 4 throws to new types

### Step 6: Fix KlangStdLib — replace `IllegalArgumentException`

- Replace 6 throws with `KlangScriptTypeError` / `KlangScriptArgumentError`
- Thread `callLocation` from registration lambda

### Step 7: Fix CallStack — add stackTrace to `KlangScriptStackOverflowError`

### Step 8: Remove deprecated typealiases

- Delete old class names once all references are updated

### Step 9: Add `astNode` to Interpreter throws (incremental)

- Pass AST node alongside location for key error types
- Start with: method not found, not callable, wrong arg count, undefined variable

### Step 10: Update `mapToEditorError()` in `editor_helpers.kt`

- Simplify: `KlangScriptError` has `location` directly — no regex parsing needed

### Step 11: Enhance `format()` for richer messages

- Use `errorType` enum for prefix
- When `astNode` is available, show expression context

## Test Scenarios

All tests verify that the thrown error:

1. Is the correct `KlangScriptError` subtype
2. Has `errorType` matching the expected `KlangScriptErrorType` enum value
3. Has a valid `SourceLocation` pointing at the problematic code
4. Has `stackTrace` (for runtime errors)

### Parse Errors — `KlangScriptSyntaxError`

| #   | Scenario                      | Code                 | Assert                                           |
|-----|-------------------------------|----------------------|--------------------------------------------------|
| P1  | Unterminated string           | `let x = "hello`     | errorType = SyntaxError, location at `"`         |
| P2  | Unterminated template literal | `` let x = `hello `` | errorType = SyntaxError, location at `` ` ``     |
| P3  | Unexpected character          | `let x = @`          | errorType = SyntaxError, location at `@`         |
| P4  | Missing closing paren         | `let x = (1 + 2`     | errorType = SyntaxError, location at end         |
| P5  | Missing closing bracket       | `let x = [1, 2`      | errorType = SyntaxError, location at end         |
| P6  | Missing closing brace         | `let x = { a: 1`     | errorType = SyntaxError, location at end         |
| P7  | Invalid token after `let`     | `let 123 = 5`        | errorType = SyntaxError, location at `123`       |
| P8  | Missing `from` in import      | `import * "strudel"` | errorType = SyntaxError, location at `"strudel"` |
| P9  | Unexpected EOF                | `let x =`            | errorType = SyntaxError, location at end         |
| P10 | Duplicate comma               | `let x = [1,,2]`     | errorType = SyntaxError, location at second `,`  |

### Runtime — `KlangScriptTypeError`

| #   | Scenario                         | Code                         | Assert                                |
|-----|----------------------------------|------------------------------|---------------------------------------|
| R1  | Call non-function                | `let x = 5; x()`             | location at `x()`, stackTrace present |
| R2  | Unary minus on string            | `-"hello"`                   | location at `-"hello"`                |
| R3  | Unary plus on string             | `+"hello"`                   | location at `+"hello"`                |
| R4  | Prefix ++ on non-number          | `let x = "a"; ++x`           | location at `++x`                     |
| R5  | Prefix -- on non-number          | `let x = "a"; --x`           | location at `--x`                     |
| R6  | Postfix ++ on non-number         | `let x = "a"; x++`           | location at `x++`                     |
| R7  | Postfix -- on non-number         | `let x = "a"; x--`           | location at `x--`                     |
| R8  | Binary add non-numbers           | `null + 1`                   | location at `null + 1`                |
| R9  | Division by zero                 | `1 / 0`                      | location at `1 / 0`                   |
| R10 | Modulo by zero                   | `1 % 0`                      | location at `1 % 0`                   |
| R11 | `in` left not string             | `1 in {}`                    | location at expression                |
| R12 | `in` right not object            | `"a" in 5`                   | location at expression                |
| R13 | Property on non-object           | `let x = 5; x.foo`           | location at `x.foo`                   |
| R14 | Method not found on object       | `let x = {}; x.foo()`        | location at `x.foo`                   |
| R15 | Array index not number           | `let a = [1]; a["x"]`        | location at `a["x"]`                  |
| R16 | Object key not string (index)    | `let o = {}; o[5]`           | location at `o[5]`                    |
| R17 | Index into non-indexable         | `let x = 5; x[0]`            | location at `x[0]`                    |
| R18 | Assign property on non-object    | `let x = 5; x.foo = 1`       | location at assignment                |
| R19 | Index-assign array bad index     | `let a = [1]; a["x"] = 2`    | location at assignment                |
| R20 | Index-assign array out of bounds | `let a = [1]; a[5] = 2`      | location at assignment                |
| R21 | Index-assign on non-indexable    | `let x = 5; x[0] = 1`        | location at assignment                |
| R22 | Native type has no method        | (via registered native type) | location at member access             |
| R23 | Type has no method (suggestion)  | (via strudel type)           | location at member access             |

### Runtime — `KlangScriptReferenceError`

| #   | Scenario                | Code            | Assert          |
|-----|-------------------------|-----------------|-----------------|
| R24 | Undefined variable      | `x`             | location at `x` |
| R25 | Undefined in expression | `let a = x + 1` | location at `x` |
| R26 | Assign to undefined     | `x = 5`         | location at `x` |

### Runtime — `KlangScriptAssignmentError`

| #   | Scenario       | Code                 | Assert              |
|-----|----------------|----------------------|---------------------|
| R27 | Reassign const | `const x = 1; x = 2` | location at `x = 2` |

### Runtime — `KlangScriptArgumentError`

| #   | Scenario                  | Code                        | Assert                |
|-----|---------------------------|-----------------------------|-----------------------|
| R28 | Too few args (script fn)  | `let f = (a, b) => a; f(1)` | location at `f(1)`    |
| R29 | Too many args (script fn) | `let f = (a) => a; f(1, 2)` | location at `f(1, 2)` |
| R30 | Native fn wrong arg count | (registered native fn)      | location at call site |
| R31 | Native fn missing arg     | (registered native fn)      | location at call site |

### Runtime — `KlangScriptImportError`

| #   | Scenario                       | Code                           | Assert                       |
|-----|--------------------------------|--------------------------------|------------------------------|
| R32 | Library not found              | `import * from "nonexistent"`  | location at import statement |
| R33 | Non-exported symbol            | `import { foo } from "stdlib"` | location at import statement |
| R34 | Namespace + selective conflict | (programmatic)                 | location at import statement |
| R35 | Library load failure           | (engine returns error)         | location at import statement |

### Runtime — `KlangScriptStackOverflowError`

| #   | Scenario           | Code                     | Assert                               |
|-----|--------------------|--------------------------|--------------------------------------|
| R36 | Infinite recursion | `let f = () => f(); f()` | location present, stackTrace present |

### Runtime — Stdlib Errors (currently `IllegalArgumentException`)

| #   | Scenario                         | Code                   | Assert                                 |
|-----|----------------------------------|------------------------|----------------------------------------|
| R37 | `Object.keys()` on non-object    | `Object.keys(5)`       | KlangScriptTypeError, location at call |
| R38 | `Object.values()` on non-object  | `Object.values("x")`   | KlangScriptTypeError, location at call |
| R39 | `Object.entries()` on non-object | `Object.entries(null)` | KlangScriptTypeError, location at call |
| R40 | `concat()` on non-array          | non-array receiver     | KlangScriptTypeError, location at call |
| R41 | `Number()` bad input             | `Number({})`           | KlangScriptTypeError, location at call |
| R42 | `Boolean()` bad input            | `Boolean({})`          | KlangScriptTypeError, location at call |

### Chained Call Errors (correct node reported)

| #   | Scenario              | Code                       | Assert                                  |
|-----|-----------------------|----------------------------|-----------------------------------------|
| R43 | Error at end of chain | `let x = {}; x.a.b.c`      | location at first failing `.b`          |
| R44 | Error in nested call  | `let f = (x) => x(); f(5)` | location at `x()`, stackTrace shows `f` |

### Error Formatting

| #  | Scenario                                  | Assert                                               |
|----|-------------------------------------------|------------------------------------------------------|
| F1 | `format()` includes error type enum name  | Starts with `"TypeError"` etc.                       |
| F2 | `format()` includes location              | Contains `"at 3:5"` or similar                       |
| F3 | `format()` includes stack trace (runtime) | Contains indented stack frames                       |
| F4 | `format()` without location               | Gracefully omits location part                       |
| F5 | `KlangScriptError` sealed interface       | `is KlangScriptError` matches both parse and runtime |
| F6 | `when(error)` exhaustive                  | Sealed hierarchy enables exhaustive matching         |

### Error Identity

| #  | Scenario                                     | Assert                                                             |
|----|----------------------------------------------|--------------------------------------------------------------------|
| I1 | Parse error is `KlangScriptError`            | `error is KlangScriptError` = true                                 |
| I2 | Parse error is `KlangScriptParseError`       | `error is KlangScriptParseError` = true                            |
| I3 | Parse error is NOT `KlangScriptRuntimeError` | `error is KlangScriptRuntimeError` = false                         |
| I4 | Runtime error is `KlangScriptError`          | `error is KlangScriptError` = true                                 |
| I5 | Runtime error is `KlangScriptRuntimeError`   | `error is KlangScriptRuntimeError` = true                          |
| I6 | Runtime error is NOT `KlangScriptParseError` | `error is KlangScriptParseError` = false                           |
| I7 | errorType enum matches class                 | `KlangScriptTypeError.errorType == KlangScriptErrorType.TypeError` |

## Key Files

| File                                     | Changes                                                                 |
|------------------------------------------|-------------------------------------------------------------------------|
| `runtime/Errors.kt`                      | New hierarchy: enum, sealed interface, sealed classes, concrete classes |
| `parser/ParseException.kt`               | DELETE — replaced by `KlangScriptSyntaxError`                           |
| `parser/KlangScriptParser.kt`            | Update 4 throw sites to `KlangScriptSyntaxError` + `SourceLocation`     |
| `runtime/Interpreter.kt`                 | Rename 33 throws, fix 2 null locations, add `astNode` (incremental)     |
| `runtime/Environment.kt`                 | Rename 3 throws, fix 1 missing location                                 |
| `runtime/NativeInterop.kt`               | Add `location` param, rename 4 throws                                   |
| `runtime/CallStack.kt`                   | Rename + add stackTrace                                                 |
| `builder/KlangScriptExtensionBuilder.kt` | Pass `callLocation` instead of `_` (~15 sites)                          |
| `stdlib/KlangStdLib.kt`                  | Replace 6 `IllegalArgumentException` with proper types                  |
| `comp/editor_helpers.kt`                 | Simplify `mapToEditorError()` — use `location` directly                 |

## Decision: AST Node in Errors

**Carry `astNode: AstNode?` on `KlangScriptRuntimeError` only — not on parse errors.**

- Parse errors: AST didn't build → `astNode` is always null → field not on `KlangScriptParseError`
- Runtime errors: AST is in memory → cheap reference, enables future fix suggestions
- The full `Program` is NOT carried — caller holds it separately if needed
