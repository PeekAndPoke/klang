# KlangScript Implementation History

This document tracks the detailed implementation timeline of KlangScript, including architectural decisions, code
changes, and evolution of the codebase.

---

## Phase 1: Foundation & Basic Parsing (COMPLETE ✅)

### Step 1.11: Array Literals (Complete)

**Implementation Date**: 2024

**Syntax Supported**:

- Empty arrays: `[]`
- With elements: `[1, 2, 3]`
- Mixed types: `[1, "hello", true, null]`
- Expressions: `[x + 1, func(), obj.prop]`
- Nested: `[[1, 2], [3, 4]]`
- Trailing commas: **NOT SUPPORTED** (requires better-parse enhancement)

**Files Modified**:

- `ast/Ast.kt` - Added `ArrayLiteral` expression with 58 lines of KDoc
- `parser/KlangScriptParser.kt` - Added `[` and `]` tokens, array parsing rules
- `runtime/RuntimeValue.kt` - Added `ArrayValue` class
- `runtime/Interpreter.kt` - Added `evaluateArrayLiteral()` method
- **New**: `ArrayLiteralTest.kt` - 19 comprehensive tests

**Test Results**: 329 tests passing (up from 310) on both JVM and JS platforms

**Known Limitations**:

- Trailing commas not supported (parser limitation)
- Array indexing `arr[0]` not yet implemented (deferred to Step 2.13)
- No array methods yet (deferred to Step 2.13)

---

## Phase 2: Tree-Walking Interpreter (COMPLETE ✅)

### Step 2.12: Error Handling & Debugging (Complete)

**Implementation**: Multi-phase

**Phase 1: Error Type System**

- Created `runtime/Errors.kt` with error hierarchy:
    - `TypeError` - Type mismatches
    - `ReferenceError` - Undefined variables
    - `ArgumentError` - Wrong argument counts
    - `ImportError` - Library failures
    - `AssignmentError` - Const reassignment

**Phase 2: Source Location Tracking**

- Added `SourceLocation` data class to `ast/Ast.kt`
- Updated all AST nodes with optional `location` parameter
- Created `toLocation()` extension on TokenMatch
- Parser captures row/column from better-parse tokens

**Phase 3: Stack Traces**

- Created `CallStack.kt` with `CallStackFrame` class
- Interpreter tracks call stack (push/pop on function entry/exit)
- JavaScript-style formatting:
  ```
  TypeError at math.klang:5:12: Cannot add string and number
    at add (math.klang:5:12)
    at calculate (main.klang:10:5)
  ```
- Stack overflow protection (1000-frame limit)
- Custom `StackOverflowError` for multiplatform compatibility

**Test Results**: 276 tests passing (51 error tests + 8 location tests + 12 stack trace tests)

---

## Phase 3: API & Integration (COMPLETE ✅)

### Step 3.5: Standard Library (Complete)

**Implementation**: Created `KlangStdLib` as proper library to battle-test the system

**Functions Implemented**:

- **I/O**: `print()`, `console_log()`
- **Math** (via `Math` object): `sqrt()`, `abs()`, `floor()`, `ceil()`, `round()`, `sin()`, `cos()`, `tan()`, `min()`,
  `max()`, `pow()`
- **String**: `length()`, `toUpperCase()`, `toLowerCase()`

**Design Pattern**: Uses `registerObject()` for JavaScript-compatible `Math` and `console` objects

**Files Created**:

- `stdlib/KlangStdLib.kt` - 200+ lines
- `stdlib/KlangStdLibTest.kt` - 30 comprehensive tests

**Test Results**: 359 tests passing (up from 329)

---

### Step 3.7: Import/Export System (Complete)

**Syntax Implemented**:

```javascript
// Export
export {add, multiply}

// Wildcard import
import * from "math"

// Selective import
import {add} from "math"
```

**Key Features**:

- Export control prevents scope pollution
- Selective and wildcard imports
- Library registry with lazy loading
- Backward compatibility (libraries without exports export all)

**Files Modified**:

- `ast/Ast.kt` - Added `ImportStatement`, `ExportStatement`
- `parser/KlangScriptParser.kt` - Added `import`, `from`, `export` keywords
- `runtime/Interpreter.kt` - Import/export execution logic
- `runtime/Environment.kt` - Export tracking and filtering

---

### Step 3.8: Import/Export Enhancements (Complete)

**Step 3.8.1: Import Aliasing**

```javascript
import {add as sum, mul as multiply} from "math"
```

- Modified `ImportStatement.imports` to `List<Pair<String, String>>`
- 12 comprehensive tests in `ImportAliasingTest.kt`

**Step 3.8.2: Namespace Imports**

```javascript
import * as math from "lib"

math.add(1, 2)
```

- Added `namespaceAlias` field to `ImportStatement`
- Creates `ObjectValue` for namespace binding
- 14 comprehensive tests in `NamespaceImportTest.kt`

**Step 3.8.3: Export Aliasing**

```javascript
export {add as sum, multiply}
import {sum} from "math"
```

- Modified `ExportStatement.exports` to `List<Pair<String, String>>`
- Environment uses `exportAliases` map
- 15 comprehensive tests in `ExportAliasingTest.kt`

---

### Step 3.10: Native Kotlin Interop (Complete)

**Goal**: Enable scripts to call Kotlin methods with full type safety and chaining

**Architecture**:

- `NativeObjectValue<T>` wraps Kotlin objects
- `BoundNativeMethod` for extension methods
- Registry-based lookup by `KClass<*>`
- Auto-wrapping of native returns
- Auto-conversion of parameters

**Registration API**:

```kotlin
engine.registerFunction("note") { pattern: String ->
    StrudelPattern(pattern)
}

engine.registerType<StrudelPattern> {
    registerMethod("sound") { soundName: String ->
        this.sound(soundName)
    }
}
```

**Files Created**:

- `runtime/NativeInterop.kt` - Type conversion helpers
- `NativeInteropTest.kt` - 12 comprehensive tests

**Files Modified**:

- `runtime/RuntimeValue.kt` - Added `NativeObjectValue`, `BoundNativeMethod`
- `KlangScript.kt` - Added native registries
- `runtime/Interpreter.kt` - Extended member access and call evaluation

---

### Step 3.11: Library System Enhancement (Complete)

**Goal**: Bundle script code with native registrations in reusable libraries

**API**:

```kotlin
val lib = klangScriptLibrary("strudel") {
    source(
        """
        let sequence = (pattern) => note(pattern)
        export { sequence }
    """
    )

    registerFunction("note") { pattern: String ->
        StrudelPattern(pattern)
    }

    registerType<StrudelPattern> {
        registerMethod("sound") { name: String -> this.sound(name) }
    }
}

val engine = klangScript {
    registerLibrary(lib)
}
```

**Files Created**:

- `KlangScriptLibrary.kt` - Builder-based library definition
- `LibrarySystemTest.kt` - 16 comprehensive tests

---

### Step 3.12: Unified Native Registry Architecture (Complete)

**Major Refactoring**: Centralized all native registrations through `NativeRegistryBuilder`

**Key Innovation - `registerObject()` Method**:

**Before** (manual wrapper):

```kotlin
klangScriptLibrary("stdlib") {
    source(
        """
        const Math = __createMathObject()
        export { Math }
    """
    )
    registerFunction("__createMathObject") {
        NativeObjectValue(kClass = MathObject::class, ...)
    }
    registerExtensionMethod1<MathObject, Double, Double>("sqrt") { _, x -> sqrt(x) }
}
```

**After** (`registerObject`):

```kotlin
klangScriptLibrary("stdlib") {
    source("export { Math, console }")

    registerObject("Math", MathObject) {
        registerMethod("sqrt") { x: Double -> sqrt(x) }
        registerMethod("abs") { x: Double -> abs(x) }
    }

    registerObject("console", ConsoleObject) {
        registerVarargMethod("log") { args -> ... }
    }
}
```

**Architecture Changes**:

1. **Created `NativeRegistryBuilder` Interface**:
    - Unified interface for all registrations (functions, types, objects, methods)
    - Used by both `KlangScript.Builder` and `KlangScriptLibrary.Builder` via delegation
    - Produces immutable `NativeRegistry`

2. **Created `NativeObjectExtensionBuilder<T>` Class**:
    - Type-safe extension method registration
    - Methods: `registerMethod()` (0-5 parameters), `registerVarargMethod()`
    - Automatic type conversion with reified generics

3. **Unified Builders**:
    - Single implementation (`RegistryBuilderImpl`) shared by both builders
    - Eliminates code duplication
    - Consistent API across engine and library configuration

**Files Created**:

- `builder/NativeRegistryBuilder.kt` - 408 lines, unified registration interface

**Files Modified**:

- `KlangScript.kt` - Delegates to `NativeRegistryBuilder`
- `KlangScriptLibrary.kt` - Delegates to `NativeRegistryBuilder`
- `stdlib/KlangStdLib.kt` - Uses `registerObject()` for Math and console
- `runtime/Environment.kt` - Accepts `NativeRegistry`

**Test Coverage**:

- Created `KlangScriptExtensionBuilderTest.kt` with 28 tests
- Tests all 7 function registration variants (0-5 params + vararg)
- Tests all 7 extension method variants (0-5 params + vararg)
- Tests all 7 registerObject variants
- Tests method chaining and mixed scenarios

**Benefits**:

- **Single Responsibility**: One interface handles all registrations
- **DRY**: No duplicated code
- **Type Safety**: Reified generics throughout
- **Flexibility**: Easy to extend with new methods
- **Testability**: Isolated registration logic

**Test Results**: 359 tests passing on both JVM and JS platforms

---

### Step 3.12: Immutability & Builder Pattern (Complete)

**Goal**: Make `KlangScript` and `KlangScriptLibrary` immutable after construction

**Before**:

```kotlin
val engine = KlangScript()
engine.registerFunction("print") { ... }  // Mutates engine
```

**After**:

```kotlin
val engine = klangScript {
    registerFunction("print") { ... }
}  // Immutable after build
```

**Implementation**:

- Constructor is `private`, takes all configuration
- Builder collects registrations without mutating
- `build()` creates immutable instance
- DSL functions: `klangScript {}`, `klangScriptLibrary {}`

**Benefits**:

- Thread-safe after construction
- All configuration declared upfront
- Prevents mid-execution mutations
- Clearer separation: configuration vs execution

**Test Updates**: All 21 test files updated to use builder pattern

---

## Current Status (2026-01-07)

**Test Count**: 359+ tests passing on both JVM and JS platforms

**Completed Phases**:

- ✅ Phase 1: Foundation & Parsing (Steps 1.1-1.12)
- ✅ Phase 2: Interpreter (Steps 2.1-2.12)
- ✅ Phase 3: API & Integration (Steps 3.1-3.12)

**Pending Phases**:

- ⏳ Phase 4: Documentation & Examples
- ⏳ Phase 5: Testing & Quality
- ⏳ Phase 6: Future Enhancements

**Next Logical Step**: Implement array methods (`Step 2.13`) - arrays exist but lack essential methods like `push()`,
`map()`, `filter()`

---

## Key Design Decisions

### 1. No `undefined`, Only `null`

Simplifies type system, avoids JavaScript's confusing duality

### 2. Builder Pattern for Immutability

Configuration phase (builder) separate from execution phase (immutable engine)

### 3. Native Interop via Extension Methods

Type-safe, flexible, leverages Kotlin's type system

### 4. JavaScript-Compatible API

`Math` object, `console.log()`, ES6 imports/exports - familiar to web developers

### 5. Multiplatform from Day One

JVM and JS targets, tests run on both platforms

### 6. Unified Registration Architecture

Single `NativeRegistryBuilder` interface, eliminates duplication, consistent API

### 7. Export Control for Libraries

Prevents scope pollution, explicit API surfaces

---

### Step 2.13 & 3.13: Built-in Type Extension Methods (Complete)

**Implementation Date**: January 2026

**Goal**: Add JavaScript-compatible methods to built-in types (ArrayValue, StringValue, ObjectValue)

**Challenge**: Extension methods work for `NativeObjectValue`, but built-in `RuntimeValue` types needed special handling
in the interpreter.

**Solution**: Modified `Interpreter.evaluateMemberAccess()` to check for extension methods on built-in types before
falling through to error handling.

**Array Methods Implemented** (11 methods):

- **Property-like**: `length()`
- **Mutating**: `push()`, `pop()`, `shift()`, `unshift()`
- **Non-mutating**: `slice()`, `concat()`, `join()`, `reverse()`
- **Search**: `indexOf()`, `includes()`

**String Methods Implemented** (14 methods):

- `length()`, `charAt()`, `substring()`, `indexOf()`, `split()`
- `toUpperCase()`, `toLowerCase()`, `trim()`
- `startsWith()`, `endsWith()`, `replace()`
- `slice()`, `concat()`, `repeat()`

**Object Utilities Implemented** (3 methods):

- `Object.keys()`, `Object.values()`, `Object.entries()`

**Files Modified**:

- `runtime/Interpreter.kt` - Added built-in type handling in `evaluateMemberAccess()` (lines 633-663)
- `stdlib/KlangStdLib.kt` - Added `registerType<ArrayValue>`, `registerType<StringValue>`, added `ObjectUtility` object
- `builder/KlangScriptExtensionBuilder.kt` - Removed debug println statements

**Files Created**:

- `ArrayMethodsTest.kt` - 14 comprehensive tests for array methods

**Test Results**: 393 tests passing (up from 359) on both JVM and JS platforms

**Higher-Order Methods Deferred**: `map()`, `filter()`, `forEach()`, `find()`, `some()`, `every()` require callback
execution infrastructure (access to interpreter/libraryLoader from extension method context). Deferred to future update.

**Key Technical Decision**: Used `registerExtensionMethod()` directly for methods needing `RuntimeValue` parameters (
concat, join, indexOf, includes) instead of type-safe `registerMethod()`.

---

## Technical Highlights

**Parser**: Uses better-parse combinator library, captures source locations

**Interpreter**: Tree-walking with lexical scoping, closure capture, call stack tracking

**Error Handling**: JavaScript-style stack traces with file:line:column precision

**Type System**: Reified generics for type-safe native function registration

**Architecture**: Immutable after configuration, thread-safe by design
