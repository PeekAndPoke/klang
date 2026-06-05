# KlangScript Annotation-Based Registration — Plan

## Goal

Replace hand-written builder DSL calls for registering native Kotlin functions, objects,
and type extensions with KlangScript with a set of annotations processed by KSP.
The processor generates the equivalent builder code at compile time.

## Motivation

The current approach (`registerObject`, `registerMethod`, `registerType`, `registerFunction`, etc.)
is verbose and repetitive. Every new function requires manual wiring with correct type parameters,
argument conversion, and null handling. An annotation-based approach:

- Reduces boilerplate to a single annotation per function/method
- Catches registration errors at compile time (wrong param count, missing CallInfo placement)
- Makes the native API surface scannable — grep for annotations instead of reading builder DSL
- Keeps the existing runtime machinery unchanged (builder, RuntimeValue, Environment)

## Library Name Constants

To avoid repeating library names as string literals, define constants:

```kotlin
object KlangScriptLibraries {
    const val STDLIB = "stdlib"
}
```

These constants are referenced in `@KlangScript.Library(KlangScriptLibraries.STDLIB)`.
Kotlin allows `const val` in annotation arguments, so this works without issues.

## Annotation Set

All annotations live under a `KlangScript` container object for namespace cleanliness.

### `@KlangScript.Library(name: String)`

| Property    | Description                                                                                                                              |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Target      | `file`, `class`                                                                                                                          |
| Purpose     | Assigns annotated elements to a named library                                                                                            |
| Inheritance | When on a class/object, all `@Method` functions inside inherit it. When on a file, all `@Function` declarations in that file inherit it. |

### `@KlangScript.Object(name: String = "")`

| Property      | Description                                                                     |
|---------------|---------------------------------------------------------------------------------|
| Target        | `class` (typically `object`)                                                    |
| Purpose       | Exposes an object as a named singleton in KlangScript (e.g., `Math`, `console`) |
| Name default  | Kotlin class name if `name` is empty                                            |
| Combines with | `@KlangScript.Library` on the same class                                        |

### `@KlangScript.TypeExtensions(type: KClass<*>)`

| Property      | Description                                                                                   |
|---------------|-----------------------------------------------------------------------------------------------|
| Target        | `class` (typically `object`)                                                                  |
| Purpose       | Registers methods as extensions on a RuntimeValue subtype (e.g., `StringValue`, `ArrayValue`) |
| Combines with | `@KlangScript.Library` on the same class                                                      |

### `@KlangScript.Function(name: String = "")`

| Property              | Description                                  |
|-----------------------|----------------------------------------------|
| Target                | `function` (top-level)                       |
| Purpose               | Registers a top-level KlangScript function   |
| Name default          | Kotlin function name if `name` is empty      |
| Inherits library from | `@KlangScript.Library` on the enclosing file |

### `@KlangScript.Method(name: String = "")`

| Property     | Description                                                   |
|--------------|---------------------------------------------------------------|
| Target       | `function` (inside `@Object` or `@TypeExtensions` class)      |
| Purpose      | Registers a method on an object or as a type extension method |
| Name default | Kotlin function name if `name` is empty                       |

## Signature Conventions (KSP Infers From Kotlin)

The processor inspects the Kotlin function signature to decide which registration variant to generate.
No flags or extra annotations needed.

### Fixed-arity functions

```kotlin
@KlangScript.Function
fun myFunc(a: Double, b: String): Double = ...
```

KSP sees two params (`Double`, `String`), generates `registerFunction<Double, String, Double>("myFunc") { ... }`.

### Vararg functions

```kotlin
@KlangScript.Function
fun print(vararg args: Any?) {
    ...
}
```

KSP sees `vararg` keyword, generates `registerVarargFunction(...)`.

### CallInfo-aware functions

```kotlin
@KlangScript.Function
fun note(vararg args: Any, callInfo: CallInfo): SprudelPattern {
    ...
}
```

KSP sees `CallInfo` as the last parameter, generates `registerVarargFunctionWithCallInfo(...)`.
If `CallInfo` appears anywhere other than last position, KSP emits a compile error.

### Extension methods on types

```kotlin
@KlangScript.TypeExtensions(StringValue::class)
object StringExtensions {
    @KlangScript.Method
    fun StringValue.split(separator: String) = ...
}
```

KSP sees the receiver type matches `@TypeExtensions(type)`, generates `registerExtensionMethod(...)`.

### Methods on objects

```kotlin
@KlangScript.Object("Math")
object KlangScriptMath {
    @KlangScript.Method
    fun sqrt(x: Double) = kotlin.math.sqrt(x)
}
```

KSP generates `registerObject("Math", KlangScriptMath) { registerMethod("sqrt") { ... } }`.

## KSP Validation Rules

The processor enforces these at compile time:

| Rule                                                           | Error                                                           |
|----------------------------------------------------------------|-----------------------------------------------------------------|
| `@Method` outside `@Object` or `@TypeExtensions`               | "Method must be inside an @Object or @TypeExtensions class"     |
| `CallInfo` param not in last position                          | "CallInfo must be the last parameter"                           |
| `@Function` on a member function (not top-level)               | "Function must be top-level"                                    |
| `@TypeExtensions` receiver mismatch                            | "Extension function receiver must match @TypeExtensions type"   |
| More than 5 fixed params (current builder limit)               | "Maximum 5 parameters supported" — or generate raw registration |
| `@Library` missing for `@Object`/`@TypeExtensions`/`@Function` | "Must specify a library via @Library"                           |

## Generated Code

For each library, KSP generates a single registration function:

```kotlin
// Generated by KSP — do not edit
fun KlangScriptExtensionBuilder.registerStdlibAnnotated() {
    // From @Object("Math") on KlangScriptMath
    registerObject("Math", KlangScriptMath) {
        registerMethod("sqrt") { x: Double -> KlangScriptMath.sqrt(x) }
        registerMethod("abs") { x: Double -> KlangScriptMath.abs(x) }
        // ...
    }

    // From @TypeExtensions(StringValue::class) on StringExtensions
    registerType<StringValue> {
        registerMethod("length") { StringExtensions.length(this) }
        registerMethod("split") { separator: String -> StringExtensions.split(this, separator) }
        // ...
    }

    // From @Function on top-level functions
    registerVarargFunction("print") { args -> print(*args.toTypedArray()) }
}
```

Libraries call this generated function instead of hand-writing registrations:

```kotlin
fun create(): KlangScriptLibrary = klangScriptLibrary("stdlib") {
    registerStdlibAnnotated()  // generated
    // ... any remaining dynamic registrations (e.g., outputHandler)
}
```

## Dependency Injection / Dynamic Registration

Some registrations need runtime values (e.g., `outputHandler` in stdlib).
These cannot be fully annotation-driven. Two strategies:

1. **Hybrid**: Annotate the static parts, keep builder DSL for dynamic parts.
   The generated `registerStdlibAnnotated()` handles static registrations,
   and the `create(outputHandler)` function adds the dynamic ones manually.

2. **Factory annotation** (future): `@KlangScript.Param` on a property that
   becomes a parameter of the generated factory function.

Start with strategy 1 — it's pragmatic and doesn't block anything.

## Module Structure

```
klangscript-annotations/          # new module — annotation definitions only
  src/commonMain/kotlin/
    KlangScript.kt                # @Library, @Object, @TypeExtensions, @Function, @Method

klangscript-ksp/                  # new module — KSP processor (JVM only)
  src/main/kotlin/
    KlangScriptSymbolProcessor.kt # the processor
    KlangScriptCodeGenerator.kt   # generates builder code

klangscript/                      # existing module
  build.gradle.kts                # add KSP plugin + dependency on annotations module
```

The annotations module is KMP (shared across JVM + JS).
The KSP processor is JVM-only (runs at compile time).

## Migration Path

1. **Phase 1**: Implement annotations + KSP processor. Add annotations to `KlangStdLib`
   alongside existing builder code. Verify generated code matches manual registrations via tests.

2. **Phase 2**: Replace manual registrations in `KlangStdLib` with annotation-generated code.
   Keep `outputHandler` as hybrid (manual) registration.

3. **Phase 3**: Annotate sprudel DSL registrations. This is the bigger payoff — sprudel has
   many functions registered via `SprudelRegistry` property delegates.

4. **Phase 4**: Remove unused builder overloads if all registrations are annotation-driven.
   Keep the builder API available for dynamic/test use cases.

## Automatic Documentation Generation

### Goal

KSP extracts KDoc from `@Method` and `@Function` annotations and generates `KlangDocsRegistry`
registrations — producing `KlangSymbol`, `KlangCallable`, `KlangParam`, and `KlangType` entries
automatically. This replaces both:

- The hand-written docs in `KlangStdLib` (currently none — stdlib has no docs)
- The sprudel-specific `SprudelDocsProcessor` (which can eventually be replaced by the general processor)

### What Already Exists

The `sprudel-ksp` module has a working KSP processor (`SprudelDocsProcessor`) that:

1. Finds `@SprudelDsl`-annotated functions/properties
2. Parses KDoc via `KDocParser` → `ParsedKDoc` (description, @param, @return, @category, @tags, @alias, @param-tool,
   @param-sub, code samples)
3. Generates `KlangSymbol` / `KlangCallable` / `KlangParam` / `KlangType` code
4. Outputs as `actual val generatedSprudelKlangSymbols: Map<String, KlangSymbol>`
5. Chunks output to avoid JVM `MethodTooLargeException`

### Target Docs Model (existing, unchanged)

```
KlangSymbol
  ├── name: String
  ├── category: String
  ├── tags: List<String>
  ├── aliases: List<String>
  ├── library: String
  └── variants: List<KlangDecl>
        ├── KlangCallable
        │     ├── name, description, returnDoc, samples
        │     ├── receiver: KlangType?
        │     ├── params: List<KlangParam>
        │     │     ├── name, type: KlangType, isVararg, description
        │     │     ├── uitools: List<String>
        │     │     └── subFields: Map<String, String>
        │     └── returnType: KlangType?
        └── KlangProperty
              ├── name, owner, type, mutability
              └── description, returnDoc, samples
```

### KDoc Convention

The processor parses standard KDoc plus custom tags (same as `SprudelDocsProcessor`):

```kotlin
/**
 * Returns the square root of a number.
 *
 * ```KlangScript
 * Math.sqrt(16)  // 4.0
 * ```

*
* @param x The number to take the square root of
* @return The square root
* @category math
* @tags arithmetic, calculation
  */
  @KlangScript.Method
  fun sqrt(x: Double) = kotlin.math.sqrt(x)

```

Supported tags:

| Tag | Maps to |
|-----|---------|
| Description (before first tag) | `KlangCallable.description` |
| `@param name desc` | `KlangParam.name` + `KlangParam.description` |
| `@return desc` | `KlangCallable.returnDoc` |
| `@category name` | `KlangSymbol.category` |
| `@tags a, b, c` | `KlangSymbol.tags` |
| `@alias a, b` | `KlangSymbol.aliases` |
| `@param-tool name toolA, toolB` | `KlangParam.uitools` |
| `@param-sub paramName fieldName desc` | `KlangParam.subFields` |
| `` ```KlangScript ... ``` `` | `KlangCallable.samples` |

### Type Inference

KSP resolves Kotlin types to `KlangType` automatically:

| Kotlin type | Generated `KlangType` |
|---|---|
| `Double` | `KlangType(simpleName = "Number")` |
| `String` | `KlangType(simpleName = "String")` |
| `Boolean` | `KlangType(simpleName = "Boolean")` |
| `SprudelPattern` | `KlangType(simpleName = "SprudelPattern")` |
| `StringValue` | `KlangType(simpleName = "String")` — RuntimeValue types map to their script names |
| `ArrayValue` | `KlangType(simpleName = "Array")` |
| Nullable types | `KlangType(..., isNullable = true)` |
| Type aliases | `KlangType(..., isTypeAlias = true)` |

The processor maintains a mapping from Kotlin/RuntimeValue types to their KlangScript display names.

### Receiver Inference

| Context | Receiver |
|---|---|
| `@Object("Math")` + `@Method` | `KlangType(simpleName = "Math")` — the object name |
| `@TypeExtensions(StringValue::class)` + `@Method` | `KlangType(simpleName = "String")` |
| `@Function` (top-level) | `null` — no receiver |

### Generated Output

For each library, the processor generates both registration code AND docs:

```kotlin
// Generated by KlangScriptProcessor — do not edit

// --- Registration ---
fun KlangScriptExtensionBuilder.registerStdlibGenerated() { ... }

// --- Documentation ---
val generatedStdlibDocs: Map<String, KlangSymbol> = buildMap {
    putAll(generatedStdlibDocsChunk0())
    putAll(generatedStdlibDocsChunk1())
    // ...
}

private fun generatedStdlibDocsChunk0() = mapOf(
    "sqrt" to KlangSymbol(
        name = "sqrt",
        category = "math",
        tags = listOf("arithmetic", "calculation"),
        library = "stdlib",
        variants = listOf(
            KlangCallable(
                name = "sqrt",
                receiver = KlangType(simpleName = "Math"),
                params = listOf(
                    KlangParam(name = "x", type = KlangType(simpleName = "Number"), description = "The number to take the square root of")
                ),
                returnType = KlangType(simpleName = "Number"),
                description = "Returns the square root of a number.",
                returnDoc = "The square root",
                samples = listOf("Math.sqrt(16)  // 4.0")
            )
        )
    ),
    // ...
)
```

### Shared KDocParser

The existing `KDocParser` + `ParsedKDoc` from `sprudel-ksp` should be extracted to a shared
module (e.g., `klangscript-ksp` or a shared `ksp-common` module) since the parsing logic is
identical. The new `KlangScriptProcessor` and the existing `SprudelDocsProcessor` can both use it.

Eventually `SprudelDocsProcessor` can be retired entirely — sprudel functions would use
`@KlangScript.Function` / `@KlangScript.Method` instead of `@SprudelDsl`, and the unified
processor handles everything.

## Open Questions

1. **Source code bundling**: `@KlangScript.Library` has a `source` param for inline KlangScript code.
   For larger source blocks, should we support `@KlangScript.Library(name = "stdlib", sourceFile = "stdlib.klang")`
   and have KSP read from resources?

2. **Param count limit**: The current builder caps at 5 params. Should KSP generate raw
   `registerFunctionRaw` calls for higher arities, or is 5 sufficient?

3. **Sprudel migration timing**: Should the new processor replace `SprudelDocsProcessor` immediately,
   or run in parallel until the annotation set is proven on stdlib first?
