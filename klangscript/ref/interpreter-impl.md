# KlangScript — Interpreter & Runtime

## Runtime Value Types (`runtime/RuntimeValue.kt`)

| Type          | Kotlin class           | Notes                                       |
|---------------|------------------------|---------------------------------------------|
| Number        | `NumberValue`          | Double internally                           |
| String        | `StringValue`          |                                             |
| Boolean       | `BooleanValue`         |                                             |
| Null          | `NullValue`            | only absent-value type (no undefined)       |
| Function      | `FunctionValue`        | closure; body is `ArrowFunctionBody`        |
| Object        | `ObjectValue`          | `Map<String, RuntimeValue>`                 |
| Array         | `ArrayValue`           | `MutableList<RuntimeValue>`                 |
| Native object | `NativeObjectValue<T>` | wraps a Kotlin object                       |
| Bound method  | `BoundNativeMethod`    | extension method bound to a native instance |

Helper extension: `.toIntOrNull()`, `.toDoubleOrNull()`, `.isNumber()`, `.isString()`, etc.

## Environment & Scoping (`runtime/Environment.kt`)

Lexical scoping via parent-chain `Environment`. Each function call creates a child environment.
`NativeRegistry` (produced by builder) is injected at engine creation — immutable after that.

## Error Handling (`runtime/Errors.kt`)

Typed exceptions: `TypeError`, `ReferenceError`, `ArgumentError`, `ImportError`, `AssignmentError`.
All carry `SourceLocation` (file:line:col) and a JavaScript-style stack trace.
Stack overflow protection: 1000-frame limit, custom `StackOverflowError`.

**`ReturnException`** — NOT an error. Thrown by `ReturnStatement`, caught at function call site:

```kotlin
// In interpreter, executing block body:
try {
    statements.forEach { executeStatement(it) }
    NullValue
} catch (e: ReturnException) {
    e.value
}
```

## Native Interop API

Register via `KlangScriptExtensionBuilder` (used in both `klangScript {}` and `klangScriptLibrary {}`):

```kotlin
// Functions (0–5 params + vararg)
registerFunction("add") { a: Int, b: Int -> a + b }
registerVarargFunction("sum") { nums: List<Double> -> nums.sum() }

// Types with extension methods
registerType<MyClass> {
    registerMethod("process") { input: String -> this.process(input) }
    registerVarargMethod("multi") { args: List<Double> -> args.sum() }
}

// Singleton objects (exported by name into script scope)
registerObject("Math", MathObject) {
    registerMethod("sqrt") { x: Double -> sqrt(x) }
}
```

**Auto-conversion:** Kotlin primitives ↔ `RuntimeValue` via reified generics. Native returns are auto-wrapped in
`NativeObjectValue`. Registry-based lookup by `KClass<*>`.

## Built-in Type Methods

Handled in `Interpreter.evaluateMemberAccess()` — checks for extension methods on `ArrayValue`, `StringValue`,
`ObjectValue` before throwing reference error. Registered in `stdlib/KlangStdLib.kt` via `registerType<ArrayValue>` etc.

## Import/Export

`ImportStatement` / `ExportStatement` in AST. Interpreter resolves libraries via registry (lazy loading).
Export aliasing: `Environment` tracks `exportAliases: Map<String, String>`.
Namespace import: creates `ObjectValue` binding for `import * as math from "lib"`.
