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

## Callable objects (`__invoke__`)

- A `NativeObjectValue` callee (`Katalyst(...)`, sprudel's `reverb(0.3)`) dispatches to the extension
  method its type registers under the internal symbol `__invoke__` (Kotlin's operator word inside double
  underscores, the scheme of `docs/tasks/klangscript-native-object-operators.md`; no script spells it),
  through the spec-aware member-call path (`Interpreter.evaluateCall`). Errors and the call stack name the
  call as written (`Katalyst`, `adsr`), never the internal symbol; the generated registration names a call
  form's argument errors after the object too (`SpecAwareItem.errorName`). A script never reaches an operator
  symbol (`NativeOperatorNames.isOperatorName`, any `__x__`) by member access, and the "Available ..." lists and
  completion leave them out.
- The docs carry the call form as the object's second variant: the object's symbol holds `val perlin: perlin`
  (a `KlangProperty`) and `perlin(from, to): SprudelPattern` (a receiver-less `KlangCallable` named after the
  object, `KlangSymbol.callForm`); no docs symbol is named after the operator. The editor resolves a call on
  the object's name like any top-level call (`getCallable(name, null)`), and a call on another value of the
  object's type (`Kat(...)`, a local) through `KlangDocsRegistry.getCallForm(type)` (by simple name, else by
  FQCN); a local is never a same-named global, in the inferrer and in `NamedArgumentChecker` alike. The docs page
  shows the object's call form on an alias card too (`KlangDocsRegistry.callFormFor`). The param tools bind a call
  form's arguments to its own parameters, except where a method has the very same parameter list and declares
  tools for the argument (the field accessors and compounds mirror their pattern method: `pan(0.7)`,
  `lpf(800)`), and then the whole-call rewrite is open, since the twin has the same parameter names; the signals'
  `perlin(from, to)` and `Katalyst(configure)` mirror none. A call form is never a
  candidate for an untyped member call (`ArgumentBinding.bindArgument`).
- The call form is declared with `@KlangScript.Invoke` on `operator fun invoke` (2026-09-07; the old
  `@KlangScript.Method(name = "invoke")` spelling is retired). The Kotlin side keeps Kotlin's word `invoke`;
  the script symbol has one definition, `KlangScript.Invoke.NAME` (`__invoke__`, 2026-10-07), read by
  `NativeOperatorNames.INVOKE`. KSP (`InvokeShape`) refuses an `@Invoke` outside an `@Object` class, one not
  on `invoke`, one without `operator`, a second one in the same class (KlangScript has no overloads, so one
  call form), and a `@Method` whose script name is `invoke`, `__invoke__` or any other `__x__` (`methodNameProblem`).
- Only `__invoke__` is wired; the arithmetic operators of
  `docs/tasks/klangscript-native-object-operators.md` are not built.

## Library registration and argument checks

- `Environment.register` merges the receiver-keyed extension maps PER NAME: same name, later import wins.
  A plain `putAll` on a `Map<KClass, MutableMap<String, ...>>` replaces a receiver's whole method map
  (importing sprudel after the stdlib once dropped every stdlib string method). Any registry of that shape
  needs the two-level merge. Spec: `LibraryExtensionMergeSpec`.
- `checkArgsSize` is a MINIMUM check. A surplus argument is caught by the parameter specs, so a door with
  no parameters needs `checkNoArgs` (the builder's zero-arg bridge and the KSP emission both call it).

## Built-in Type Methods

Handled in `Interpreter.evaluateMemberAccess()` — checks for extension methods on `ArrayValue`, `StringValue`,
`ObjectValue` before throwing reference error. Registered in `klangscript-libs/src/commonMain/kotlin/stdlib/KlangStdLib.kt` via `registerType<ArrayValue>` etc.

## Import/Export

`ImportStatement` / `ExportStatement` in AST. Interpreter resolves libraries via registry (lazy loading).
Export aliasing: `Environment` tracks `exportAliases: Map<String, String>`.
Namespace import: creates `ObjectValue` binding for `import * as math from "lib"`.
