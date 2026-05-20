# KlangScript Annotation Taxonomy Redesign

> **Status**: planned, ready to implement after the ignitor-slots cleanup.
> Touches `klangscript-annotations`, `klangscript-ksp`, `klangscript` runtime,
> and ~50 caller sites.

## Goal

Today `@KlangScript.Property` is positionally bound to "top-level `val`". The name
implies "property on a thing"; the placement (top-level only) does not. We want:

- `@KlangScript.Constant` — top-level `val` (NEW; takes over the current `Property` job).
- `@KlangScript.Property` — `val` **inside** `@Object` or `@TypeExtensions` (NEW semantics).
- `@KlangScript.Method` and `@KlangScript.Function` unchanged in semantics
  but documented + enforced as "member-only" / "top-level-only" respectively.
- KSP refuses to compile when an annotation is placed in the wrong scope —
  the rule is structural and easy to violate accidentally.

With the redesign, `Osc.slot.analog` (no-parens property chain) becomes possible:

```kotlin
@KlangScript.Object("Osc")
object KlangScriptOsc {
    @KlangScript.Property
    val slot: KlangScriptOscSlot = KlangScriptOscSlot
}

@KlangScript.Object("OscSlot")
object KlangScriptOscSlot {
    @KlangScript.Property
    val analog: IgnitorDsl = IgnitorDsl.Slots.analog
    // ...
}
```

```
// Script:
let pad = Osc.sine().analog(Osc.slot.analog)
```

## Background — what's in place today

### Annotation file (klangscript-annotations/.../KlangScript.kt)

```
@KlangScript.Library    @file or @class           (no scope rule needed)
@KlangScript.Object     @class                    (currently any class — flat reg only)
@KlangScript.TypeExtensions   @class              (currently any class)
@KlangScript.Function   @fun                      (treated as top-level; receiver = …
                                                   form supports extension on a Kotlin type)
@KlangScript.Method     @fun                      (treated as @Object/@TypeExtensions member)
@KlangScript.Property   @property                 (treated as TOP-LEVEL property — misleading)
```

### KSP processor (klangscript-ksp/.../KlangScriptProcessor.kt)

- Collects `topLevelFunctions` and `topLevelProperties` separately. No scope
  validation: a `@Property` declared inside an `@Object` is silently dropped.
- For top-level `@Property`, emits `registerObject("<name>", <kotlinIdent>)`
  (so the value is bound as a named singleton). The runtime type is also
  registered so extension methods can dispatch on it.
- `@Method` / `@Function` paths render via `RegistrationModel.kt`
  (`FixedMethodItem` / `ArityDispatchItem` / etc.).

### Runtime dispatch (klangscript/.../runtime/Interpreter.kt:1372 evaluateMemberAccess)

- On `NativeObjectValue<T>.foo`, calls `engine.getExtensionMethod(objValue, "foo")`.
  If a method is registered → returns a `BoundNativeMethod`. Calling it executes
  the underlying lambda.
- No property-getter lookup. `Osc.slot` today only resolves if `slot` is a
  registered method, which produces a `BoundNativeMethod` — so `Osc.slot.analog`
  parses as "access .analog on a BoundNativeMethod", which doesn't dispatch.
- The runtime extension table is keyed by (KClass → method-name → invoker);
  there is no "property table" parallel structure yet.

## Plan

Land in three commits if possible — keeps each piece reviewable.

### Commit A — Add `@Constant`, migrate callers (mechanical)

1. Add `@KlangScript.Constant` annotation in
   `klangscript-annotations/src/commonMain/kotlin/KlangScript.kt` with the
   same target (`PROPERTY`) and the same default-name behaviour as today's
   `@Property`. KDoc explains it is for top-level vals only.
2. Add a parallel handling branch in `KlangScriptProcessor.kt` that treats
   `@Constant` the way `@Property` is treated today (registerObject + register
   runtime type). The existing `@Property` branch remains for one commit so
   the migration can happen file-by-file without compile breaks.
3. Mechanical rename of all 47 `@KlangScript.Property` usages →
   `@KlangScript.Constant`. Files (from `grep`):
    - `klangscript/.../stdlib/KlangScriptConstants.kt` (×2: PI, E)
    - `sprudel/.../lang/lang_random.kt` (×4)
    - `sprudel/.../lang/lang_continuous.kt` (×19)
    - `sprudel/.../lang/addons/lang_continuous_addons.kt` (×7)
    - `sprudel/.../lang/addons/lang_arithmetic_addons.kt` (×4)
    - `sprudel/.../lang/addons/lang_osc_addons.kt` (×N — `silence`, `rest`, etc.)
    - Any others reported by `grep -rn @KlangScript.Property`.

After commit A: no functional change, but the new name is in use everywhere.

### Commit B — Repurpose `@Property` for member properties

1. Remove the old top-level `@Property` handling from `KlangScriptProcessor.kt`.
2. Add new member-property collection inside the existing `@Object` /
   `@TypeExtensions` traversals: `KSClassDeclaration.getDeclaredProperties()`
   filtered to those carrying `@KlangScript.Property`.
3. New `MemberPropertyEntry(name, prop)` data class alongside `MethodEntry`.
4. New rendered registration block for member properties. Two flavors:
    - On `@Object` (e.g. `KlangScriptOsc.slot`): emit a registerProperty call
      that exposes `<objName>.<propName>` returning the Kotlin val.
    - On `@TypeExtensions` (e.g. extending `SprudelPattern`): emit a
      register-extension-property call that takes the receiver as the first
      arg and returns the val.
5. New runtime hooks in `klangscript-runtime`:
    - `ExtensionProperty(getter: (receiver) -> RuntimeValue)`
    - Engine maps: `nativeProperties: Map<KClass, Map<String, ExtensionProperty>>`
    - `KlangScriptExtensionBuilder.registerProperty(name, getter)` (top-level)
    - `KlangScriptExtensionBuilder.registerExtensionProperty(cls, name, getter)`
    - `engine.getExtensionProperty(value, name): ExtensionProperty?`
6. Extend `Interpreter.evaluateMemberAccess`:
    - On `NativeObjectValue<T>.foo`, look up property first; if found, invoke
      the getter and return the value directly.
    - Fallback to method dispatch (current behaviour).
7. Annotation `@Target` becomes `[AnnotationTarget.PROPERTY]` (unchanged
   target, but new contextual semantics enforced by KSP).

### Commit C — KSP scope validation

Add a single Pass-0 validation step in `KlangScriptProcessor.process`:

```
@Function   → KSFile parent
@Constant   → KSFile parent
@Object     → KSFile OR class annotated @Object
@TypeExtensions → KSFile
@Method     → class annotated @Object OR @TypeExtensions
@Property   → class annotated @Object OR @TypeExtensions
```

Violations → `logger.error("@KlangScript.Method on $name must be inside @Object or @TypeExtensions", node)`.
Compile fails with a clear pointer. No runtime side-effect.

### Commit D (optional bonus) — Convert `OscSlot` to property semantics, add `Osc.slot`

After Commits A-C land:

1. In `KlangScriptOscSlot.kt`, convert all `@KlangScript.Method fun analog(): …`
   → `@KlangScript.Property val analog: IgnitorDsl = IgnitorDsl.Slots.analog`.
2. In `KlangScriptOsc.kt`, add:
   ```kotlin
   @KlangScript.Property
   val slot: KlangScriptOscSlot = KlangScriptOscSlot
   ```
3. Update `KlangScriptOscSlotTest.kt`: change `OscSlot.analog()` → `OscSlot.analog`.
4. Add a single sanity test: `Osc.slot.analog` evaluates to
   `IgnitorDsl.Param("analog", 0.0)`.

This is the user-visible payoff: clean no-parens slot access.

### Optional Commit E — IntelliSense / type inference

Update `intel/ExpressionTypeInferrer.kt:46 inferMemberAccess` and any docs/registry
emitted to the IDE to recognise property kinds in addition to methods. Lower
priority — autocomplete still works because method/property names live in the
same identifier space; only the inferred return type differs.

## Migration order summary

| Commit | Risk | Rollback path                                          |
|--------|------|--------------------------------------------------------|
| A      | low  | revert; no caller breakage because old + new both work |
| B      | high | revert; restores top-level `@Property` semantics       |
| C      | low  | revert validation step only                            |
| D      | low  | revert OscSlot conversion + `Osc.slot` addition        |
| E      | low  | revert intel changes                                   |

Recommended: land A first as its own PR. Then bundle B+C+D into a single PR
(they reinforce each other and validation is meaningless until the new
semantics exist). E can come later.

## Out of scope

- Sprudel-DSL named-args migration (`docs/agent-tasks/sprudel-dsl-named-args.md`).
- Renaming `@Function` to `@TopLevelFunction` — current `Function` name is fine
  once the scope rule is enforced.
- Property-setter support — read-only properties only for now. If a user
  wants mutable state, expose a method.

## Acceptance criteria

- All existing tests pass after each commit.
- `@KlangScript.Property` on a top-level val → compile error pointing at the
  declaration ("place inside @Object or @TypeExtensions, or use @Constant").
- `@KlangScript.Method` on a top-level fun → compile error ("use @Function").
- `@KlangScript.Function` on a member fun → compile error ("use @Method").
- After Commit D: `Osc.slot.analog` evaluates without parens to the
  `IgnitorDsl.Slots.analog` singleton.

## Files of interest

- `klangscript-annotations/src/commonMain/kotlin/KlangScript.kt`
- `klangscript-ksp/src/main/kotlin/KlangScriptProcessor.kt`
- `klangscript-ksp/src/main/kotlin/RegistrationModel.kt`
- `klangscript/src/commonMain/kotlin/builder/KlangScriptExtensionBuilder.kt`
- `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt` (`evaluateMemberAccess`)
- `klangscript/src/commonMain/kotlin/runtime/NativeInterop.kt`
- `klangscript/src/commonMain/kotlin/stdlib/KlangScriptOsc.kt`
- `klangscript/src/commonMain/kotlin/stdlib/KlangScriptOscSlot.kt`
- `klangscript/src/commonMain/kotlin/stdlib/KlangScriptConstants.kt`
- All `sprudel/.../lang/**` files carrying `@KlangScript.Property`
