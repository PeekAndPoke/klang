# Member access on a boolean value

_Status: queued. Found in review 2026-10-06 (KSP registration step 1, round 2, DSL role), outside that change._

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
