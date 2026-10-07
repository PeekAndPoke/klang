# Member access on a boolean value

_Status: **done 2026-10-07** (branch `correctness-fixes`). Found in review 2026-10-06 (KSP registration step 1, round 2, DSL role), outside that change._

## Problem

`KlangScriptBooleanExtensions` registers `toString` (and its other methods) on boolean values, but the
interpreter never reaches them: a method call on a boolean fails at member access, on the JVM and in the browser.

Repro:

```javascript
import * from "stdlib"
true.toString()
```

Result: `KlangScriptTypeError: Cannot access property 'toString' on non-object value: true`.
Expected: `"true"` (the method is registered).

## Where

`klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`, the member-access evaluation (around line 1437): a
`BooleanValue` ends at the generic "non-object value" error, so the registered extension method is apparently
never looked up for it. Not yet investigated beyond that.

## Done when

- `true.toString()`, `false.toString()` and every other method of `KlangScriptBooleanExtensions` resolve, on JVM
  and JS, with a common-test row each (mutation-checked).
- The member-access path is the same for every value kind that has registered extensions, so no other kind is
  left out the same way.

## What was done (2026-10-07)

**The cause.** `Interpreter.evaluateMemberAccess` looked up registered extensions for a native object and for exactly
three built-in kinds (`ArrayValue`, `StringValue`, `NumberValue`); every other kind went straight to the generic
"non-object value" error, so `KlangScriptBooleanExtensions.toString` was registered and unreachable.

**The fix.** One branch for every value kind except a script object: a number, a string, a boolean, an array, `null`, a
script function, a native function and a bound method all look up the extensions registered on their runtime class the
same way (property first, then method, then the ranked "has no method" error when the kind has extensions, else the
generic error). The ranked error names the kind by its script name (`Type 'Boolean' has no method 'toStrin'`, was
`BooleanValue`; round 1). A script object keeps plain property access (its own properties are its members, a missing one is
`null`); no library registers an extension on `ObjectValue`.

**The audit**, kind by kind, of what is registered and how member access reaches it now:

| Kind | Registered extensions (stdlib, sprudel) | Before | Now |
|------|------------------------------------------|--------|-----|
| number (`NumberValue`) | stdlib number methods | reached | reached |
| string (`StringValue`) | stdlib string methods, 292 sprudel methods | reached | reached |
| array (`ArrayValue`) | stdlib array methods | reached | reached |
| boolean (`BooleanValue`) | stdlib `toString` | **not reached** | reached |
| `null` (`NullValue`) | none | generic error | same path, generic error (`null?.x` is still `null`) |
| script function (`FunctionValue`) | none | generic error | same path, generic error |
| native function, bound method | none | generic error | same path, generic error |
| script object (`ObjectValue`) | none | own properties | own properties (unchanged on purpose) |
| native object (`NativeObjectValue`) | `SprudelPattern`, `Function1`, `PatternMapperProvider`, the builders | reached | reached (unchanged) |

One thing the audit shows that is not a lookup gap: sprudel's 246 `Function1` methods (`fast(2).slow(3)`) are
registered on Kotlin's `Function1`, which a native mapper is and a script arrow is not, so `(x => x).slow(2)` still ends
at the generic error. Making a script arrow a pattern mapper is a language decision, not part of this fix.

**The editor side** needed nothing: the inferrer types `true` as `Boolean`, the docs carry `toString` with the receiver
`Boolean`, so completion after `true.` offers it and the hover on `toString` shows the boolean variant only. Now pinned.

**Specs.** `MemberAccessValueKindsSpec` (klangscript, common: one registered method per kind reached through member
access, the object row, the ranked error, the generic error), `StdLibBooleanMethodsTest` (klangscript-libs, common: a
row per boolean method, the editor rows, and a completeness check against the registered boolean methods). Mutations:
the branch narrowed back to the three kinds (red: the kinds row, the ranked-error row, the boolean rows), the function
kind excluded (red: the kinds row), the object kind included (red: the object row). Report:
`tmp/reviews/fix-script-report.md`.
