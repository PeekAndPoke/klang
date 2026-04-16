# KlangScript — Named Arguments + Builder Refactor

## Goal

Add Kotlin-style named arguments to KlangScript (`foo(param = 1)`) and rework the native-interop
bridge so parameter names, optional parameters with defaults, receivers, vararg, CallInfo, and
engine access are declared explicitly at registration time. Consolidates the current overload
zoo (`registerFunction<P1..P5>`, `registerMethod<P1..P5>`, `registerVarargFunction`,
`registerExtensionMethod`, `*WithCallInfo`, `*WithEngine`) into a single type-state builder
hierarchy that emits rich parameter metadata usable by the interpreter, the KSP processor, and
the IntelliSense layer.

---

## Motivating Example

Target end-user experience:

```javascript
// KlangScript source
filter(cutoff = 800, q = 1.2)        // all named — any order, defaults fill missing
filter(800, 1.2)                     // all positional — current behaviour preserved
filter(cutoff = 800)                 // all named + some omitted — defaults fill "q"
```

**Call-site rule: all-or-nothing.** A single call is either fully positional or fully named.
Mixing is a parse/runtime error. Rationale: mixing creates ambiguity when a position both
appears positionally *and* as a named arg, and the readability win of mixing is marginal.
Two call styles, one rule, no edge cases.

Native registration (DSL we're designing):

```kotlin
createFunction("filter")
    .withReceiver<IgnitorDsl>()
    .withOptionalParam<Double>("cutoff") { 1000.0 }
    .withOptionalParam<Double>("q") { 1.0 }
    .body { ignitor, cutoff, q ->
        ignitor.filter(cutoff, q)
    }
```

---

## Non-goals

- No JavaScript-style object-literal-as-kwargs (`foo({ cutoff: 800 })`). Explicitly different syntax.
- No two-phase default resolution (a default that references a previously-resolved param's value).
  If the user needs this, they compute it inside `.body`.
- No named args when calling script-defined arrow functions where the function was passed as a value
  without a name spec — script arrow functions keep their existing positional-only calling.
  (Future: could add `(name: value) =>` param syntax to give them named capability.)
- No `...spread` of an object into named args. Positional spread already excluded.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│  Parse Time                                                    │
│                                                                │
│  parseArguments():                                             │
│    IDENTIFIER + `=` (lookahead)  →  Argument.Named(name, expr) │
│    else                         →  Argument.Positional(expr)   │
│                                                                │
│  CallExpression.arguments: List<Argument>                      │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│  Interpret Time                                                │
│                                                                │
│  evaluateCall():                                               │
│    1. Evaluate every argument expression → RuntimeValue        │
│    2. Classify call style: ALL positional OR ALL named         │
│       (mixing → KlangScriptArgumentError)                      │
│    3. Validate:                                                │
│       - same name not passed twice                             │
│       - named arg must match a known param name                │
│    4. Dispatch:                                                │
│       - NativeFunctionValue → invoke with (CallArgs, loc)      │
│       - FunctionValue       → bind positional+named to params  │
│       - BoundNativeMethod   → invoke with (CallArgs, loc)      │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│  Native Bridge                                                 │
│                                                                │
│  Body invocation inside builder-generated closure:             │
│    1. Pull each declared param out of CallArgs (by name OR     │
│       by position — both filled in step 2 above).              │
│    2. For missing optional params → invoke the default thunk.  │
│    3. For missing required params → throw argument error.      │
│    4. Convert each RuntimeValue → Kotlin type via              │
│       convertArgToKotlin (existing helper).                    │
│    5. If .withCallInfo() or .withEngine() — build and append.  │
│    6. Invoke user's .body lambda with typed args.              │
│    7. wrapAsRuntimeValue(result).                              │
└────────────────────────────────────────────────────────────────┘
```

---

## Files to Change

| Area                     | File                                                                   | Change                                                                                                                 |
|--------------------------|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| AST                      | `klangscript/ast/Ast.kt`                                               | Add `Argument` sealed hierarchy; change `CallExpression.arguments` type                                                |
| Parser                   | `klangscript/parser/KlangScriptParser.kt`                              | `parseArguments()` named-arg detection                                                                                 |
| Interpreter              | `klangscript/runtime/Interpreter.kt`                                   | `evaluateCall()` — resolve CallArgs                                                                                    |
| Runtime                  | `klangscript/runtime/RuntimeValue.kt`                                  | `NativeFunctionValue.function` signature change; `FunctionValue.parameters` → `List<ParamSpec>` with optional defaults |
| Bridge core              | `klangscript/runtime/NativeInterop.kt`                                 | Add `ParamSpec`, `CallArgs`, `resolveCallArgs()` helper                                                                |
| Bridge builder           | `klangscript/builder/KlangScriptExtensionBuilder.kt`                   | Replace typed overloads with `FunctionBuilderN<...>` hierarchy                                                         |
| Bridge builder (gen)     | `klangscript/builder/FunctionBuilderGenerated.kt` (new)                | Generated or hand-written Builder0..Builder10 types                                                                    |
| KSP processor            | `klangscript-ksp/src/main/kotlin/KlangScriptProcessor.kt`              | Emit new builder chain instead of overloaded registers                                                                 |
| Downstream call sites    | `sprudel/lang/KlangScriptStrudelLib.kt`, any manual registrations      | Migrate to new API                                                                                                     |
| Tests — parser           | `klangscript/src/commonTest/kotlin/NamedArgumentsParseTest.kt` (new)   | Coverage                                                                                                               |
| Tests — interpreter      | `klangscript/src/commonTest/kotlin/NamedArgumentsRuntimeTest.kt` (new) | Coverage                                                                                                               |
| Tests — builder          | `klangscript/src/commonTest/kotlin/FunctionBuilderTest.kt` (new)       | Coverage                                                                                                               |
| Docs (language-features) | `klangscript/language-features/04-functions.md`                        | Update status                                                                                                          |
| Intellisense             | `klangscript/intel/ExpressionTypeInferrer.kt` + related                | Accept named-arg call shape for type inference (mostly transparent — args already evaluated left-to-right)             |

Current stdlib files (`KlangScriptOscExtensions.kt`, `KlangStdLib.kt`, etc.) are KSP-annotated;
they don't need manual migration — they're regenerated from the updated processor.

---

## Part 1 — Script-Side Named Arguments

### 1.1 AST

In `klangscript/ast/Ast.kt`:

```kotlin
/** A call-site argument. */
sealed class Argument {
    abstract val value: Expression
    abstract val location: SourceLocation?

    /** Positional: `foo(expr)` */
    data class Positional(
        override val value: Expression,
        override val location: SourceLocation? = value.location,
    ) : Argument()

    /** Named: `foo(name = expr)` */
    data class Named(
        val name: String,
        override val value: Expression,
        val nameLocation: SourceLocation? = null,
        override val location: SourceLocation? = nameLocation,
    ) : Argument()
}

data class CallExpression(
    val callee: Expression,
    val arguments: List<Argument>,       // was: List<Expression>
    override val location: SourceLocation? = null,
) : Expression(location)
```

**Impact**: any existing code that iterates `call.arguments` and expects `Expression` needs to
switch to `call.arguments.map { it.value }` or pattern-match on the sealed class. In-tree:

- `Interpreter.evaluateCall()` — main logic site
- `AstIndex.kt` — AST traversal for IntelliSense
- `AnalyzedAst.kt` + `ExpressionTypeInferrer.kt` — type inference walkers
- `AstCallFinder.kt` — test utility
- `klangblocks/AstToKBlocks.kt` — visual editor AST conversion

### 1.2 Parser

In `parseArguments()` (KlangScriptParser.kt:1482):

```kotlin
private fun parseArguments(): List<Argument> {
    val args = mutableListOf<Argument>()
    if (check(TokenType.RIGHT_PAREN)) return args

    do {
        args.add(parseArgument())
    } while (match(TokenType.COMMA) && !check(TokenType.RIGHT_PAREN))

    return args
}

private fun parseArgument(): Argument {
    // Lookahead: IDENTIFIER followed by EQUALS (not EQ, not PLUS_EQUALS, etc.)
    if (check(TokenType.IDENTIFIER) && checkAt(1, TokenType.EQUALS)) {
        val nameToken = advance()                    // IDENTIFIER
        advance()                                    // EQUALS
        val value = parseExpression()
        return Argument.Named(
            name = nameToken.text,
            value = value,
            nameLocation = nameToken.toSourceLocation(),
        )
    }
    return Argument.Positional(parseExpression(), /* no explicit location */)
}
```

`checkAt(offset, type)` helper may need adding — equivalent to peeking `pos + offset`.

**Grammar trade-off** (previously agreed): in argument position only, `IDENTIFIER = expr` is
now a named argument, not an assignment expression. To assign-as-argument, wrap in parens:
`foo((x = 1))`. This breaks nothing in the current codebase (no such patterns in stdlib or tests).

### 1.3 Interpreter Dispatch

In `Interpreter.evaluateCall()` (Interpreter.kt:493):

Replace `val args = call.arguments.map { evaluate(it) }` with:

```kotlin
// Evaluate in source order for predictable side effects
val evaluatedArgs: List<EvaluatedArgument> = call.arguments.map { arg ->
    when (arg) {
        is Argument.Positional -> EvaluatedArgument.Positional(evaluate(arg.value))
        is Argument.Named -> EvaluatedArgument.Named(arg.name, evaluate(arg.value), arg.nameLocation)
    }
}

val callArgs = CallArgs.resolve(evaluatedArgs, call.location)
```

Where `CallArgs.resolve()` enforces:

- All positional come before named (throw `KlangScriptArgumentError` with source location otherwise).
- No duplicate named args.
- (Param-name validation happens in the callee, which owns the `ParamSpec` list.)

Then the dispatch switch becomes:

- `NativeFunctionValue.function(callArgs, call.location)` — signature changed.
- `FunctionValue` — bind positional first, then overlay named matching the `parameters` list,
  error if extra / missing.
- `BoundNativeMethod.invoker(callArgs, call.location)` — signature changed.

---

## Part 2 — Runtime Shape

### 2.1 `ParamSpec`

Add to `klangscript/runtime/NativeInterop.kt`:

```kotlin
/** Declares one named parameter of a native callable. */
data class ParamSpec(
    val name: String,
    val kotlinType: KClass<*>,
    /** Null = required. Non-null = optional; thunk runs only when arg is missing. */
    val default: (() -> RuntimeValue)? = null,
    val isVararg: Boolean = false,
)
```

### 2.2 `CallArgs`

```kotlin
/** Resolved arguments from a call site. Either ALL positional or ALL named — never mixed. */
sealed class CallArgs {
    /** Every argument was positional. */
    data class Positional(val values: List<RuntimeValue>) : CallArgs()

    /** Every argument was named. */
    data class Named(val values: Map<String, RuntimeValue>) : CallArgs()

    /** Zero-arg calls land here — trivially both. */
    data object Empty : CallArgs()

    companion object {
        fun resolve(
            evaluated: List<EvaluatedArgument>,
            callLocation: SourceLocation?,
        ): CallArgs {
            if (evaluated.isEmpty()) return Empty

            val allPositional = evaluated.all { it is EvaluatedArgument.Positional }
            val allNamed = evaluated.all { it is EvaluatedArgument.Named }

            if (!allPositional && !allNamed) {
                throw KlangScriptArgumentError(
                    message = "Call must use either all positional or all named arguments — no mixing",
                    location = callLocation, /* ... */
                )
            }

            if (allPositional) {
                return Positional(evaluated.map { (it as EvaluatedArgument.Positional).value })
            }

            val map = linkedMapOf<String, RuntimeValue>()
            for (arg in evaluated) {
                arg as EvaluatedArgument.Named
                if (arg.name in map) throw KlangScriptArgumentError(
                    message = "Duplicate named argument: ${arg.name}",
                    location = arg.nameLocation ?: callLocation, /* ... */
                )
                map[arg.name] = arg.value
            }
            return Named(map)
        }
    }
}

sealed class EvaluatedArgument {
    data class Positional(val value: RuntimeValue) : EvaluatedArgument()
    data class Named(val name: String, val value: RuntimeValue, val nameLocation: SourceLocation?) : EvaluatedArgument()
}
```

### 2.3 `resolveByParamSpec()` — used by the builder body wrappers

```kotlin
/**
 * Maps CallArgs to a flat, ordered List<RuntimeValue?> aligned with the given specs.
 * Missing optional params → null (caller invokes default thunk).
 * Missing required params → throws.
 * Extra named args or too many positional → throws.
 */
fun resolveByParamSpec(
    fnName: String,
    specs: List<ParamSpec>,
    args: CallArgs,
    loc: SourceLocation?,
): List<RuntimeValue?> {
    val result = arrayOfNulls<RuntimeValue>(specs.size)

    when (args) {
        CallArgs.Empty -> { /* nothing to bind; required-check below handles it */
        }

        is CallArgs.Positional -> {
            if (args.values.size > specs.size) throw KlangScriptArgumentError(
                message = "$fnName: too many arguments (${args.values.size}, expected ≤ ${specs.size})",
                /* ... */
            )
            args.values.forEachIndexed { i, v -> result[i] = v }
        }

        is CallArgs.Named -> {
            for ((name, v) in args.values) {
                val idx = specs.indexOfFirst { it.name == name }
                if (idx == -1) throw KlangScriptArgumentError(
                    message = "$fnName: unknown parameter '$name' " +
                            "(expected: ${specs.joinToString(", ") { it.name }})",
                    /* ... */
                )
                result[idx] = v
            }
        }
    }

    // Required params must be bound
    specs.forEachIndexed { i, spec ->
        if (result[i] == null && spec.default == null) {
            throw KlangScriptArgumentError("$fnName: missing required parameter '${spec.name}'", /* ... */)
        }
    }
    return result.toList()
}
```

### 2.4 `NativeFunctionValue` and `BoundNativeMethod` signature

```kotlin
data class NativeFunctionValue(
    val name: String,
    val specs: List<ParamSpec>,       // NEW — for error messages & introspection
    val function: (CallArgs, SourceLocation?) -> RuntimeValue,   // was: (List<RuntimeValue>, SourceLocation?)
) : RuntimeValue
```

Same change in `BoundNativeMethod` (see `NativeInterop.kt`).

### 2.5 `FunctionValue` — script arrow functions

Today:

```kotlin
data class FunctionValue(val parameters: List<String>, ...)
```

Extend to:

```kotlin
data class FunctionValue(
    val parameters: List<ScriptParamSpec>,
    val body: ArrowFunctionBody,
    val closureEnv: Environment,
    val engine: KlangScriptEngine,
) : RuntimeValue

data class ScriptParamSpec(
    val name: String,
    /** Optional: expression evaluated in the function's closure when arg is missing. Future extension. */
    val default: Expression? = null,
)
```

For phase 1 script-side defaults are **not** added (user didn't ask for that). `default` stays
null and we just use param names for named-arg binding. Required-ness is implicit (all script
params are required, like today). This keeps the scope tight while still enabling named-arg
calls to script functions.

---

## Part 3 — Bridge Builder Hierarchy

### 3.1 Entry points

```kotlin
/** Build a new top-level function. */
fun createFunction(name: String): FunctionBuilder0 = ...

// The receiver variant is just FunctionBuilder0.withReceiver<T>() — no separate entry.
```

### 3.2 Type-state builders

Core shape (showing 0, 1, 2 arity — extends to 10):

```kotlin
class FunctionBuilder0(
    private val ctx: BuilderCtx,
) {
    fun <R : Any> withReceiver(cls: KClass<R>): FunctionBuilder1<R> =
        FunctionBuilder1(ctx.withReceiver(cls))

    inline fun <reified R : Any> withReceiver(): FunctionBuilder1<R> =
        withReceiver(R::class)

    fun <T : Any> withParam(name: String, cls: KClass<T>): FunctionBuilder1<T> =
        FunctionBuilder1(ctx.addRequired(name, cls))

    inline fun <reified T : Any> withParam(name: String): FunctionBuilder1<T> =
        withParam(name, T::class)

    inline fun <reified T : Any> withOptionalParam(
        name: String,
        noinline default: () -> T,
    ): FunctionBuilder1<T> = FunctionBuilder1(ctx.addOptional(name, T::class, default))

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg<T> =
        TerminalVararg(ctx.setVararg(name, T::class))

    fun withCallInfo(): FunctionBuilder0WithCallInfo = ...
    fun withEngine(): FunctionBuilder0WithEngine = ...

    fun <R : Any> body(fn: () -> R) {
        ctx.register0 { fn() }
    }
}

class FunctionBuilder1<P1 : Any>(
    private val ctx: BuilderCtx,
) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder2<P1, T> =
        FunctionBuilder2(ctx.addRequired(name, T::class))

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder2<P1, T> =
        FunctionBuilder2(ctx.addOptional(name, T::class, default))

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg1<P1, T> = ...

    fun withCallInfo(): FunctionBuilder1WithCallInfo<P1> = ...
    fun withEngine(): FunctionBuilder1WithEngine<P1> = ...

    fun <R : Any> body(fn: (P1) -> R) {
        ctx.register1 { p1 -> fn(p1) }
    }
}

// ... repeat for FunctionBuilder2..FunctionBuilder10.
```

### 3.3 Modifier type-states (`withCallInfo`, `withEngine`)

To keep the type matrix tractable, each modifier doubles the terminal builders **only**, not the
intermediate chain. Instead of nesting (`Builder1WithCallInfoWithEngine`), collect modifiers as
builder flags on the `ctx` and change only the `.body` signature via extension functions scoped
by flag combinations.

**Preferred approach** — use extension functions to vary only the final `.body`:

```kotlin
// Plain body
fun <R : Any> FunctionBuilder2<P1, P2>.body(fn: (P1, P2) -> R) = ...

// After .withCallInfo()
fun <R : Any> FunctionBuilder2WithCallInfo<P1, P2>.body(fn: (P1, P2, CallInfo) -> R) = ...

// After .withCallInfo().withEngine() — returns FunctionBuilder2WithCallInfoWithEngine
fun <R : Any> FunctionBuilder2WithCallInfoAndEngine<P1, P2>.body(fn: (P1, P2, CallInfo, KlangScriptEngine) -> R) = ...
```

With 10 arities × (plain / +CallInfo / +Engine / +Both) = ~44 terminal builder classes. They all
wrap the same `BuilderCtx` and only differ in the `.body` extension method.

**Codegen strategy**: write a Gradle task that generates these 44 classes from a template into
`build/generated/kotlin/...`, same way KSP-generated code is handled. The template lives in
`klangscript/src/commonMain/build-src/FunctionBuilderTemplate.kt.mustache` (or a Kotlin string
template in a buildSrc script). This keeps the core builder module hand-written and clean.

Alternatively, write them by hand — at ~30 LoC each, the full set is ~1,300 LoC, but repetitive
enough that a single regen script is worth the small investment.

### 3.4 `BuilderCtx`

Shared state under the chain (not exposed to users):

```kotlin
class BuilderCtx(
    private val name: String,
    private val extensionBuilder: KlangScriptExtensionBuilder,
) {
    private var receiver: KClass<*>? = null
    private val specs = mutableListOf<ParamSpec>()
    private var varargSpec: ParamSpec? = null
    private var callInfo = false
    private var engine = false

    fun withReceiver(cls: KClass<*>): BuilderCtx = apply {
        require(receiver == null) { "Receiver already set" }
        require(specs.isEmpty()) { "withReceiver must be first" }
        receiver = cls
    }

    fun addRequired(name: String, cls: KClass<*>): BuilderCtx = apply {
        specs += ParamSpec(name, cls, default = null)
    }

    fun addOptional(name: String, cls: KClass<*>, default: () -> Any): BuilderCtx = apply {
        specs += ParamSpec(name, cls, default = { wrapAsRuntimeValue(default()) })
    }

    fun setVararg(name: String, cls: KClass<*>): BuilderCtx = apply {
        varargSpec = ParamSpec(name, cls, isVararg = true)
    }

    // Internal register functions invoked by .body:
    fun register0(fn: () -> Any) {
        ...
    }
    fun <P1> register1(fn: (P1) -> Any) {
        ...
    }
    // ... etc, up to 10

    // And CallInfo / Engine variants.
}
```

Each register method builds a closure that:

1. Gets `(CallArgs, SourceLocation?)` from the interpreter.
2. Calls `resolveByParamSpec(...)` → flat `List<RuntimeValue?>`.
3. For each slot: if non-null, `convertArgToKotlin(...)` to the declared type; else invoke
   the default thunk.
4. Invokes the user's `.body` lambda with the typed args.
5. Returns `wrapAsRuntimeValue(result)`.
6. Registers via `registerFunctionRaw(name)` — the old low-level API still exists as the
   internal storage mechanism.

### 3.5 Receiver routing

If `receiver != null`, the `BuilderCtx` registers via the extension-method path:

```kotlin
extensionBuilder.registerExtensionMethod(receiver!!, name) { self, args, loc, /* engine */ -> ... }
```

Otherwise via:

```kotlin
extensionBuilder.registerFunctionRaw(name) { args, loc -> ... }
```

Both of these need to accept `CallArgs` — so `registerFunctionRaw` also gets a new signature.
We keep the current positional signature for in-module use during migration by renaming the
old one or providing a tiny adapter.

### 3.6 Enforcement rules (type-state guarantees)

| Rule                                     | Enforced by                                                   |
|------------------------------------------|---------------------------------------------------------------|
| `withReceiver<T>` only once, first       | Only `FunctionBuilder0` exposes `withReceiver`                |
| `withVararg` must be last                | `withVararg` returns `TerminalVararg*` with no `withParam`    |
| `withCallInfo` / `withEngine` idempotent | Flag-based; second call errors at runtime (rare, cheap check) |
| `withCallInfo` after vararg              | Exposed on terminal builders too                              |
| Required param after optional param      | Allowed (callers must use named) — same as Kotlin             |
| Param name collision                     | `ctx.addRequired` / `addOptional` throws on duplicate         |

---

## Part 4 — Interpreter Argument Resolution Order

Two cases, picked by the call-site classification (all-or-nothing):

### 4.1 All-positional call

Arguments bind by index to specs. Missing optional → default thunk; missing required → error.

```
specs = [a, b, c]   (a required, b required, c optional default=7)
positional = [v1, v2]
→ [v1, v2, 7]
```

### 4.2 All-named call

Arguments bind by name to specs. Order in source is irrelevant. Same default / required rules.

```
specs = [a, b, c]   (a required, b optional default=5, c optional default=7)
named = { c: v3, a: v1 }
→ [v1, 5, v3]
```

### 4.3 Error cases (throw `KlangScriptArgumentError`)

- **Mixing**: `foo(1, x = 2)` → "Call must use either all positional or all named arguments".
- **Too many positional**: positional count > specs count.
- **Unknown named**: `foo(nope = 1)` where spec has no "nope" — "unknown parameter 'nope' (expected: a, b, c)".
- **Duplicate named**: `foo(a = 1, a = 2)` → "Duplicate named argument: a".
- **Missing required**: after binding, a required spec is still unbound → "missing required parameter 'b'".

---

## Part 5 — KSP Processor Rewrite

File: `klangscript-ksp/src/main/kotlin/KlangScriptProcessor.kt`.

### 5.1 Emission change

Old emission for `@Method fun filter(cutoff: Double = 1000.0, q: Double = 1.0)`:

```kotlin
// generated — old
builder.registerExtensionMethod(cls, "filter") { receiver, args, loc ->
    checkArgsSize(...)
    wrapAsRuntimeValue(
        if (args.size >= 2) {
            ... IgnitorDsl.filter(receiver, cutoff, q)
        } else if (args.size >= 1) {
            ... IgnitorDsl.filter(receiver, cutoff)
        } else {
            IgnitorDsl.filter(receiver)
        }
    )
}
```

New emission:

```kotlin
// generated — new
createFunction("filter")
    .withReceiver<IgnitorDsl>()
    .withOptionalParam<Double>("cutoff") { 1000.0 }
    .withOptionalParam<Double>("q") { 1.0 }
    .body { receiver, cutoff, q -> IgnitorDsl.filter(receiver, cutoff, q) }
```

**Default value extraction from KSP**: already available via `KSValueParameter.hasDefault`. KSP
cannot easily grab the *expression text* of the default — but here we sidestep that by always
calling the Kotlin function at its *max* arity and re-supplying defaults from script-visible
defaults in the generated bridge. That is: the generated `.withOptionalParam` thunk *is* the
default. Two strategies:

1. **Delegate to Kotlin defaults** (current approach, preserved): generate one call-site per
   missing-arg combination as today, but wrapped in the new bridge. The thunk just returns a
   sentinel that means "don't pass this arg to the Kotlin fn" and the generator produces a
   dispatch table. Preserves Kotlin semantics. Uglier generated code, more predictable.

2. **Lift defaults into the bridge** (cleaner): require the Kotlin author to restate the default
   expression when the parameter has a non-trivial default. Simpler generated code, but a double-
   source-of-truth risk. Can be mitigated with a `@KlangScript.Default("expr")` annotation, but
   that's ugly.

**Recommendation: Strategy 1.** The KSP processor generates a bridge that either:

- passes N args to Kotlin if N positional/named slots are filled, or
- passes fewer args and lets Kotlin fill with its own defaults.

Practically this means `.withOptionalParam(...) { error("should not reach — default resolved by Kotlin side") }` plus a
small "has user supplied this" flag per slot in `CallArgs`. Concrete emission:

```kotlin
createFunction("filter")
    .withReceiver<IgnitorDsl>()
    .withOptionalParam<Double>("cutoff")     // NO thunk — delegated
    .withOptionalParam<Double>("q")
    .bodyRawResolved { receiver, present, cutoff, q ->
        // `present` is List<Boolean> aligned with specs
        when {
            present[1] -> IgnitorDsl.filter(receiver, cutoff!!, q!!)
            present[0] -> IgnitorDsl.filter(receiver, cutoff!!)
            else -> IgnitorDsl.filter(receiver)
        }
    }
```

To support this we need one extra entry point on each arity builder:

```kotlin
fun <R : Any> FunctionBuilder2<P1, P2>.bodyRawResolved(
    fn: (P1, P2, BitSet) -> R
) = ...
```

Only the KSP processor uses `bodyRawResolved`; hand-written registrations use the cleaner
`.withOptionalParam(name) { default }` form.

### 5.2 Annotation changes

No new annotations needed. `@KlangScript.Method` / `@Function` already capture parameter names,
types, and `hasDefault`. The processor just emits the new builder chain.

### 5.3 Doc generation

Existing `generatedLibraryDocs` / `generateCallableDoc` output is unaffected — it reads from
`getScriptParams(fn)` which stays the same. Intellisense will still see param names.

---

## Part 6 — AST Analyzer Diagnostics & IntelliSense

Two sub-goals: (a) report errors at analysis time, not just at runtime; (b) surface param names in
completion.

### 6.1 Static named-arg validation

The `KlangDocsRegistry` already stores `KlangParam` entries with names and types (produced by the
KSP doc generator in `generateCallableDoc`). Extend the analyzer to cross-reference each
`CallExpression` against the registry and emit diagnostics.

**New file**: `klangscript/src/commonMain/kotlin/intel/NamedArgumentChecker.kt`

```kotlin
/**
 * Walks a parsed AST, resolves the callee via KlangDocsRegistry + ExpressionTypeInferrer,
 * and emits Diagnostic.Error entries for:
 *   - unknown named parameters
 *   - mixed positional + named in a single call
 *   - duplicate named parameters
 *   - all-named calls missing a required parameter
 *
 * Silent when the callee cannot be resolved (handled by existing "undefined function" diagnostic).
 */
class NamedArgumentChecker(
    private val docs: KlangDocsRegistry,
    private val typeInferrer: ExpressionTypeInferrer,
) {
    fun check(ast: Program): List<Diagnostic> {
        ...
    }

    private fun visitCall(call: CallExpression): List<Diagnostic> {
        val spec = resolveCallableSpec(call) ?: return emptyList()  // unknown callee

        val diagnostics = mutableListOf<Diagnostic>()

        val positional = call.arguments.filterIsInstance<Argument.Positional>()
        val named = call.arguments.filterIsInstance<Argument.Named>()

        // Rule 1: no mixing
        if (positional.isNotEmpty() && named.isNotEmpty()) {
            diagnostics += Diagnostic.error(
                message = "Call uses both positional and named arguments — pick one style",
                location = named.first().location ?: call.location,
            )
            return diagnostics  // skip further checks to keep noise low
        }

        // Rule 2: unknown named params
        val specNames = spec.params.map { it.name }.toSet()
        val seen = mutableSetOf<String>()
        for (arg in named) {
            if (arg.name !in specNames) {
                diagnostics += Diagnostic.error(
                    message = "Unknown parameter '${arg.name}' on '${spec.name}'. " +
                            "Expected: ${specNames.joinToString(", ")}",
                    location = arg.nameLocation ?: arg.location ?: call.location,
                )
            }
            if (!seen.add(arg.name)) {
                diagnostics += Diagnostic.error(
                    message = "Duplicate named argument: '${arg.name}'",
                    location = arg.nameLocation ?: arg.location ?: call.location,
                )
            }
        }

        // Rule 3: missing required params (only checkable for all-named calls;
        // for positional, arity is not statically known without optional-awareness in
        // the doc registry — which the new builder will feed in)
        if (named.isNotEmpty() && positional.isEmpty()) {
            val required = spec.params.filter { !it.isOptional }.map { it.name }
            val supplied = named.map { it.name }.toSet()
            for (r in required) {
                if (r !in supplied) {
                    diagnostics += Diagnostic.error(
                        message = "Missing required parameter '$r' on '${spec.name}'",
                        location = call.location,
                    )
                }
            }
        }

        return diagnostics
    }
}
```

### 6.2 `KlangParam` needs an `isOptional` flag

Currently `KlangParam` records name, type, description, vararg flag — but **not** whether the
param has a default. Add:

```kotlin
data class KlangParam(
    val name: String,
    val type: KlangType,
    val isVararg: Boolean = false,
    val isOptional: Boolean = false,      // NEW — true if registered via withOptionalParam
    val description: String = "",
    val uitools: List<String> = emptyList(),
    val subFields: Map<String, String> = emptyMap(),
)
```

KSP generator emits `isOptional = true` whenever `param.hasDefault` on the Kotlin side.

### 6.3 Integration with existing `klangscript-intellisense` plan

Per `docs/agent-tasks/klangscript-intellisense.md`, analyzer output is rendered by the CodeMirror
linter extension. `NamedArgumentChecker` plugs into the same pipeline:

```
parse → AstIndex → [NamedArgumentChecker, …other checkers] → Diagnostic[] → setDiagnostics()
```

Run inside the web worker alongside other checkers. No main-thread impact.

### 6.4 Completion (nice-to-have, not blocking)

Receiver-aware completion inside a call's argument list:

```
filter(█                   → show unfilled param names as named-arg completions
filter(cutoff = █          → no name suggestion; show value completions typed as Number
filter(cutoff = 800, █     → show remaining unfilled params (here: "q")
```

Slots naturally into existing `ExpressionTypeInferrer` — when the cursor is inside a call's
argument list and the callee has resolvable `KlangParam` specs, suggest unfilled param names.

---

## Part 7 — Web UI Docs: Showing Named-Arg Usage

The in-app docs page (`src/jsMain/kotlin/pages/docs/KlangScriptLibraryDocsPage.kt`) is what a
musician actually reads. It must show the new named-call syntax so people know it exists.

Three levels of surfacing, in order of impact:

### 7.1 Signature block — show named/optional annotation

Today the docs render each callable with a one-line signature like
`filter(cutoff: Number, q: Number): IgnitorDsl`. Extend the renderer to surface optional params
with a trailing `?` and show default values:

```
filter(cutoff: Number? = 1000, q: Number? = 1): IgnitorDsl
```

Two pieces feed this rendering:

- **`KlangParam.isOptional: Boolean`** — drives the `?` marker. Set by the KSP processor when
  `KSValueParameter.hasDefault` is true. Always reliable.
- **`KlangParam.defaultDoc: String?`** — drives the `= 1000` part. Extracted from raw source by
  the KSP processor (see 7.1.1). When extraction fails or returns null, the UI just shows
  `cutoff: Number?` — the `?` already conveys "optional".

#### 7.1.1 KSP default-value extraction (for docs only)

A small helper in the processor reads each defaulted parameter's source location, scans forward
from the `=` token to the matching top-level `,` or `)`, and emits the trimmed string as
`defaultDoc`. The extracted text is **only used in docs** — bridge generation stays on Strategy
1 (delegate to Kotlin's own defaults via arity dispatch), so a flaky extraction never produces
broken generated code.

Scanner requirements:

- Bracket-aware: track `(` `)` `[` `]` `{` `}` depth.
- String-aware: skip over `"…"`, `'…'`, `` `…` `` content (including escaped quotes and
  `${…}` interpolations).
- Comment-aware: skip `// …\n` and `/* … */`.
- Fail-soft: any unexpected token, missing offsets, or unclosed bracket → return `null`,
  caller emits `defaultDoc = null`.

Estimated size: ~80 lines of Kotlin, isolated in
`klangscript-ksp/src/main/kotlin/DefaultValueExtractor.kt` with its own unit tests
(literals, math expressions, multi-line, nested calls, comments inside, edge cases).

Out of scope (deferred or never): pasting the extracted text into the bridge thunk; resolving
references to enclosing-scope symbols; rendering interpolated `${…}` expressions specially.

### 7.2 Add a "Usage styles" section to each callable doc panel

Below the signature, add two collapsed blocks labelled **Positional** and **Named**, each with
a copy-paste snippet generated automatically from the `KlangParam` list:

```
Positional
    filter(1000, 1.2)

Named
    filter(cutoff = 1000, q = 1.2)
```

This is not a hand-authored sample — it's auto-derived from the spec in the frontend component
`KlangScriptLibraryDocsPage.kt`. One small Kotlin helper:

```kotlin
fun renderUsageStyles(callable: KlangCallable): VDom = ui.segments {
    segment {
        heading("Positional")
        code(buildPositionalExample(callable))    // filter(1000, 1.2)
    }
    segment {
        heading("Named")
        code(buildNamedExample(callable))         // filter(cutoff = 1000, q = 1.2)
    }
}
```

Example values come from `KlangParam.uitools` where available, otherwise from a simple
type-based default (`Number → 0`, `String → ""`, etc.).

### 7.3 KDoc `@sample` convention

For hand-authored samples in the stdlib KDocs, update the convention to prefer the named style
whenever a function has 3+ params or any optional param. Add guidance to
`klangscript/ref/adding-features.md` (or a new `klangscript/ref/kdoc-conventions.md`):

```
/**
 * Applies a low-pass filter to the ignitor signal.
 *
 * ```KlangScript
 * // preferred for multi-param calls
 * note("c4").ignitor(saw()).filter(cutoff = 1200, q = 0.8)
 * ```

*
* @param cutoff Cutoff frequency in Hz (default 1000)
* @param q Resonance (default 1)
  */

```

**Mechanical sweep**: grep stdlib KDocs for existing samples; where a call has more than two
args, rewrite to use named syntax. Track in a checklist and finish in the same PR as the KSP
processor rewrite, so docs and runtime land together.

### 7.4 Tutorial code

Tutorials in `src/jsMain/kotlin/pages/docs/tutorials/` should showcase named calls for any
Ignitor / filter / envelope configuration with 3+ params. User preference memo:
*Tutorial code quality — per-orbit effects, chord()+voicing() syntax, pan 0.0–1.0, musicality
over complexity.* Named-arg examples slot in naturally here — they **improve** readability for
beginners because the call reads like an English sentence.

**Out-of-scope for this task**: rewriting existing tutorial bodies. A follow-up pass through
tutorials would tackle that. We flag "named args preferred for 3+ params" as a new rule for
any tutorial written after this ships.

### 7.5 Summary of UI docs changes

| Change                                     | File                                              | Type              |
|--------------------------------------------|---------------------------------------------------|-------------------|
| Add `isOptional` to signature rendering    | `KlangScriptLibraryDocsPage.kt`                   | Modify            |
| Add "Usage styles" auto-examples panel     | `KlangScriptLibraryDocsPage.kt`                   | New helper funcs  |
| Default-doc storage                        | `KlangParam` (already has `description`, add `defaultDoc: String?`) | Model change |
| KSP emission of `isOptional` + `defaultDoc`| `KlangScriptProcessor.generateCallableDoc()`      | Modify            |
| KSP source extractor for default values    | `klangscript-ksp/.../DefaultValueExtractor.kt` (new) + unit tests | New util          |
| KDoc convention doc                        | `klangscript/ref/kdoc-conventions.md` (new)       | Docs              |
| Stdlib KDoc sample sweep                   | All `@KlangScript.Method` / `@Function` sites     | Content rewrite   |

---

## Part 8 — Migration Plan

### Phase 1 — Core (no user-visible change yet)

1. Add `Argument` sealed hierarchy + change `CallExpression.arguments`.
2. Update parser to emit `Argument.Positional` for all current code (no named syntax yet).
3. Update interpreter + all AST walkers to handle the new shape.
4. Run all 970+ tests — should still be green.

**Milestone**: zero behavioral change, new AST shape in place.

### Phase 2 — Parser named-arg syntax

5. Add `checkAt()` lookahead helper.
6. Implement `parseArgument()` named-arg detection.
7. Tests: parse trees with named args.

**Milestone**: script source can use `foo(x = 1)` and it parses correctly, but the interpreter
still routes like positional (first-pass fallback).

### Phase 3 — Runtime resolution

8. Add `ParamSpec`, `CallArgs` (sealed), `resolveByParamSpec`, `EvaluatedArgument`.
9. Change `NativeFunctionValue` + `BoundNativeMethod` signatures.
10. Update interpreter `evaluateCall()` to build and pass `CallArgs`.
11. Update `FunctionValue.parameters` to `List<ScriptParamSpec>` and bind named args for script
    arrow functions.
12. Tests: named-arg routing for script arrow functions + error cases (including mixing rejection).

**Milestone**: interpreter correctly dispatches named args. All native registrations still use
the OLD positional `(List<RuntimeValue>, SourceLocation?) -> RuntimeValue` signature — provide an
adapter that wraps them as `CallArgs.Positional → List<RuntimeValue>`.

### Phase 4 — New builder API

13. Implement `BuilderCtx`, `createFunction`, `FunctionBuilder0..10` (hand-rolled first 3, then
    generated/templated 4..10).
14. Implement `.body` / `.bodyRawResolved` terminals for all modifier combos.
15. Write tests: create a function via the new API, call it with named / positional / default
    combos.

**Milestone**: new DSL usable in tests; old DSL still works.

### Phase 5 — KSP processor rewrite + doc metadata

16. Add `isOptional` (+ optional `defaultDoc`) to `KlangParam`. Implement
    `DefaultValueExtractor` in the KSP module with bracket/string/comment-aware scanning
    and a small unit test suite. Wire it into doc emission only — never into bridge logic.
17. Rewrite `generateMethodRegistration` / `generateFunctionRegistration` to emit the new chain.
18. Regenerate stdlib → `GeneratedStdlibRegistration.kt` uses new builder; doc blocks carry
    `isOptional`.
19. Run all stdlib-consuming tests (sprudel lib, tutorial code, etc.).

**Milestone**: entire stdlib migrated via codegen; docs model carries optional-ness.

### Phase 6 — AST analyzer diagnostics

20. Add `NamedArgumentChecker` wired into the linter pipeline.
21. Tests: unknown named, duplicate named, mixing, missing required.
22. Verify diagnostics clear on fix.

**Milestone**: users get red underlines in CodeMirror for bad named calls, before they hit Run.

### Phase 7 — Web UI docs rendering

23. Extend `KlangScriptLibraryDocsPage.kt` signature line to show `?` for optional params.
24. Add auto-generated "Positional / Named" usage-styles panel per callable.
25. Sweep stdlib KDocs: rewrite multi-arg `@sample` blocks to use named syntax.
26. Add `klangscript/ref/kdoc-conventions.md` with the new rule.

**Milestone**: every multi-param function in the library docs visibly shows both call styles.

### Phase 8 — Sprudel and other hand-written registrations

27. Migrate `sprudel/lang/KlangScriptStrudelLib.kt`.
28. Any other `registerFunction` / `registerMethod` call sites outside tests.

### Phase 9 — Deprecation + cleanup

29. Mark `registerFunction<P1..P5>`, `registerMethod<P1..P5>`, etc. as `@Deprecated`.
30. Delete after at least one release cycle — or immediately, since this project does not have
    external API consumers.

---

## Part 9 — Testing Strategy

### 8.1 Parser

- `foo(a = 1)` → `[Named("a", NumberLiteral(1))]`
- `foo(a = 1, b = 2)` → two named
- `foo(a = b + c)` — named value is an arbitrary expression
- `foo(a = (b, c) => b + c)` — named value is an arrow function
- `foo(x == y)` — not a named arg; comparison expression
- `foo((a = 1))` — escape hatch for assignment-as-arg (stays an assignment expression)
- `foo(1, a = 2)` — parses cleanly, but interpreter throws "no mixing" at dispatch time

### 8.2 Interpreter — script functions

- `((x, y) => x + y)(x = 1, y = 2)` → 3
- `((x, y) => x + y)(y = 2, x = 1)` → 3 (named order irrelevant)
- `((x) => x)(x = 1, x = 2)` → duplicate error
- `((x) => x)(y = 1)` → unknown param error
- `((x, y) => x)(1, y = 2)` → mixing error

### 8.3 Interpreter — native functions with specs

- All-positional with all required → OK.
- All-named with all required → OK, any order.
- Optional omitted (all-named call) → default thunk runs.
- Optional omitted (all-positional call, omitted tail) → default thunk runs.
- Too many positional → error.
- Unknown named → error surfaces expected param names.
- Mixing positional + named → error.

### 8.4 Analyzer diagnostics (new — see Part 6)

- Unknown named param on a *known* callable → one `Diagnostic.Error` at the param-name location.
- Mixing positional + named in a single call → one `Diagnostic.Error` at the second-style arg.
- Duplicate named param → one `Diagnostic.Error` at the second occurrence.
- Unknown callee (function name not in registry) → existing diagnostic; *no* named-arg diagnostic
  (we have no spec to check against).
- Call with all-named args, missing a required param → `Diagnostic.Error` at the call site.

All diagnostics must clear when the offending text is fixed and the document is re-analyzed.

### 8.4 Builder type-state

Compile-time checks (build should fail for invalid orderings):

```kotlin
// Should fail: withReceiver after withParam
createFunction("x").withParam<Int>("a").withReceiver<String>()   // ERROR

// Should fail: withParam after withVararg
createFunction("x").withVararg<Int>("args").withParam<Int>("b")  // ERROR

// Should fail: .body arity mismatch
createFunction("x").withParam<Int>("a").body { /* no args */ -> 0 }  // ERROR
```

One test file per rule uses `kotlin-compile-testing` or just `@Ignore`d snippets + a build-
verification task.

### 8.5 Migration regression

All 970+ existing tests must continue passing after each phase.

---

## Part 10 — Decisions (formerly Open Questions)

All resolved — implementation can proceed.

1. **Required-after-optional** — ✅ **Allowed at any position**. Because the call-site rule is
   all-or-nothing, there's no ambiguity: a caller who wants to skip an optional must use named
   syntax anyway. Library authors get free composition; new required params can be added at any
   position without reordering existing ones.

2. **Positional-after-named mixing** — ❌ **Disallowed**. All-or-nothing at every call site.
   Mixing would create ambiguity (same position bound twice) and the readability gain is
   marginal. Enforced at parse-then-classify stage and by the analyzer.

3. **KSP default strategy** — ✅ **Strategy 1: delegate to Kotlin defaults**. The generated
   bridge registers params as optional with placeholder thunks and dispatches by "which args
   were supplied" to call the Kotlin fn at the right arity, letting Kotlin's own default
   mechanism fill the rest. Single source of truth. Default *display values* in the Web UI docs
   come from the author's `@param` KDoc prose (no new annotation needed).

4. **Max arity** — ✅ **10 total slots, receiver counts as slot 0**. A function declared with
   `withReceiver<T>()` plus 9 `withParam` calls is at capacity. Keeps builder codegen simple
   (one `FunctionBuilderN` family, not two). KSP errors if an annotated Kotlin function has more
   than 10 script-visible params — author must use vararg or pack into an options object.
   Ceiling can be raised later without breaking callers.

5. **Script-defined default values** — 🔜 **Deferred**. Script arrow functions stay
   positional-only-at-body-bind for Phase 1. Future work: add `(name = default) =>` syntax.

---

## Rough Effort Estimate

| Phase                                        | Effort        | Blocks                     |
|----------------------------------------------|---------------|----------------------------|
| 1 — AST + parser (no-op shape change)        | 1 day         | All downstream phases      |
| 2 — Parser named syntax                      | 0.5 day       | Phase 3                    |
| 3 — Runtime resolution + native adapter      | 1 day         | Phase 4                    |
| 4 — Builder hierarchy (+ template / codegen) | 1.5 days      | Phase 5                    |
| 5 — KSP processor rewrite + doc metadata     | 1 day         | Phase 6                    |
| 6 — AST analyzer diagnostics                 | 0.5 day       | Phase 7                    |
| 7 — Web UI docs rendering + KDoc sweep       | 1 day         | Phase 8                    |
| 8 — Sprudel and hand-written migrations      | 0.5 day       | Phase 9                    |
| 9 — Deprecate + clean up                     | 0.5 day       | —                          |
| **Total**                                    | **~7.5 days** | (wall-clock, focused work) |

---

## Success Criteria

- [ ] `filter(cutoff = 800, q = 1.2)` works end-to-end with a KSP-annotated Kotlin function.
- [ ] Positional calls still work unchanged (`filter(800, 1.2)`).
- [ ] Mixing positional + named (`filter(800, q = 1.2)`) → analyzer error AND runtime error.
- [ ] Missing optional params trigger default thunks.
- [ ] Missing required params throw `KlangScriptArgumentError` with source location.
- [ ] Type-state builder rejects invalid chain orderings at compile time.
- [ ] All 970+ existing tests still pass.
- [ ] `KlangScriptExtensionBuilder.kt` shrinks by ~300 lines of overload noise.
- [ ] AST analyzer surfaces unknown-named-param / duplicate-named / mixing / missing-required
  diagnostics live in CodeMirror.
- [ ] Web UI library docs show the `?` marker for optional params and an auto-generated
  "Positional / Named" usage-styles panel per callable.
- [ ] All stdlib KDoc `@sample` blocks with 3+ args rewritten to use named syntax.
- [ ] IntelliSense completion surfaces named params inside `(` (nice-to-have).

---

## Risks & Mitigations

| Risk                                                                        | Mitigation                                                                                      |
|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| AST-shape change touches many walkers; easy to miss one                     | Make `CallExpression.arguments` type change a single grep point; compile errors flag every site |
| `checkAt(offset)` lookahead changes parser performance                      | Lookahead is 2 tokens max; negligible compared to existing method-chaining logic                |
| Type-state builder explosion (10 × 4 = 40+ classes)                         | Generate from template via Gradle task; don't hand-write                                        |
| KSP default-dispatch regression (Kotlin default semantics)                  | Keep Strategy 1 arity dispatch; same logic as today, just wrapped in new bridge                 |
| `foo(x = 1)` silently changes meaning for anyone who wrote it as assignment | Pre-migration grep of all `.klang` scripts in repo; add a parser warning phase if any hits      |
| Builder migration for `sprudel` and third-party modules out-of-tree         | We control all consumers; single-commit migration with codegen                                  |
