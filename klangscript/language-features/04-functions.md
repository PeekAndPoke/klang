# KlangScript — Functions

### 4.1 Function Declarations ❌ `[MEDIUM]` — `function` keyword not in parser; use arrow functions instead

```javascript
function add(a, b) {
    return a + b;
}

let result = add(5, 3);  // 8
```

**Expected:** result = 8

### 4.2 Function Expressions ❌ `[MEDIUM]` — `function() {}` not in parser; use arrow functions instead

```javascript
let multiply = function (a, b) {
    return a * b;
};
let result = multiply(4, 5);  // 20
```

**Expected:** result = 20

### 4.3 Arrow Functions ✅

```javascript
let square = (x) => x * x;
let result1 = square(5);  // 25

let add = (a, b) => a + b;
let result2 = add(3, 4);  // 7

let greet = () => "Hello";
let result3 = greet();  // "Hello"

let complex = (x) => {
    let y = x * 2;
    return y + 1;
};
let result4 = complex(5);  // 11
```

**Expected:** Results as commented

### 4.4 Default Parameters ❌ `[MEDIUM]` — arrow function params stored as plain strings; need typed param list

```javascript
function greet(name = "World") {
    return "Hello, " + name;
}

let r1 = greet();           // "Hello, World"
let r2 = greet("Alice");    // "Hello, Alice"
```

**Expected:** Results as commented

### 4.5 Rest Parameters ❌ `[HARD]` — requires `...` token and spread/rest support throughout

```javascript
function sum(...numbers) {
    let total = 0;
    for (let n of numbers) {
        total += n;
    }
    return total;
}

let r1 = sum(1, 2, 3);           // 6
let r2 = sum(1, 2, 3, 4, 5);     // 15
```

**Expected:** Results as commented

### 4.6 Closures ✅ — works with arrow functions; example uses `function` keyword (4.1 needed for exact syntax)

```javascript
function makeCounter() {
    let count = 0;
    return function () {
        count++;
        return count;
    };
}

let counter = makeCounter();
let r1 = counter();  // 1
let r2 = counter();  // 2
let r3 = counter();  // 3
```

**Expected:** Results as commented

### 4.7 Immediately Invoked Function Expressions (IIFE) ✅ — `(() => { ... })()` works; `(function() {})()` needs 4.1

```javascript
let result = (function () {
    return 42;
})();
// result should be 42

let calculated = (function (x, y) {
    return x + y;
})(5, 3);
// calculated should be 8
```

**Expected:** Results as commented

### 4.8 Recursive Functions 🟡 — works with `let factorial = n => ...` (needs 1.5 assignment);
`function` syntax needs 4.1

```javascript
function factorial(n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

let r1 = factorial(5);  // 120

function fibonacci(n) {
    if (n <= 1) return n;
    return fibonacci(n - 1) + fibonacci(n - 2);
}

let r2 = fibonacci(7);  // 13
```

**Expected:** Results as commented

### 4.9 Higher-Order Functions ✅ — works with arrow functions; example uses
`function` keyword (4.1 needed for exact syntax)

```javascript
function applyOperation(a, b, operation) {
    return operation(a, b);
}

let add = (x, y) => x + y;
let multiply = (x, y) => x * y;
let r1 = applyOperation(5, 3, add);       // 8
let r2 = applyOperation(5, 3, multiply);  // 15
```

**Expected:** Results as commented

### 4.10 Lambdas as arguments to native functions ✅ — typed in the editor, trailing-lambda rule

A script arrow function passed to a native (Kotlin) function whose parameter is function-typed
(`(A) -> B`, or a typealias of one) is converted to a Kotlin lambda and may be called back by the
native (`NativeInterop.convertFunctionToKotlin`). Two rules make this pleasant at the call site:

- **Trailing-lambda rule** (`runtime/ArgAlignment`): when the LAST positional argument is a
  function, the parameter at its index is not function-typed, and exactly one function-typed
  parameter follows, the argument binds to that parameter and the skipped slots take their
  defaults. `Ignitor.supersaw(x => x.voices(9))` lands the lambda in the trailing `configure`
  parameter although `freq` comes first. Only the last argument floats; two candidates are
  ambiguous (name the parameter instead).
- **Typed parameters in the editor**: the analyzer binds the lambda's parameters with the
  callee's declared function-parameter types, so `x.` completes inside the lambda.

```javascript
note("c3").superimpose(x => x.transpose(12))              // x: SprudelPattern
Ignitor.supersaw(x => x.voices(9).spread(0.1)).lowpass(800)   // x: OscSuperSawBuilder (klangscript-libs)
```

The conversion is strict both ways (`NativeInterop.convertToKotlin`): a lambda on a non-function slot is a type
error ("expected Double, got a function"), and since 2026-10-02 so is a non-callable value on a function-typed slot
("expected a function, got a number"); before, a number, a boolean, an array or an object passed through unconverted
and failed inside the native.

The trailing-lambda rule runs on the spec-aware call path, which needs a default thunk for each
skipped slot. Every generated door has one: since 2026-10-06 the KSP processor refuses any optional
parameter whose default is not a literal (number, string, boolean, null). Vararg doors are the one
exception: they render no spec (no named arguments), and Kotlin supplies their defaults.

Tests: `ArgAlignmentTest.kt`, `ConfigureLambdaBindingTest.kt` (runtime), `AnalyzedAstTest.kt`
("configure lambda" cases, analyzer). Plan: `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`.

### 4.11 Callable native objects (`__invoke__`) ✅

A native object registered from Kotlin becomes callable when its type registers a method under the
internal symbol `__invoke__` (`@KlangScript.Invoke` on the `operator fun invoke` member of an
`@KlangScript.Object`, or a hand registration under `NativeOperatorNames.INVOKE`; the name is defined
once, as `KlangScript.Invoke.NAME`, 2026-10-07). No script spells it: `perlin.__invoke__(1, 2)` is "no
method", and no error list or completion shows an operator symbol (`__x__`). KSP rejects an `@Invoke`
that is not `operator fun invoke`, that sits outside an `@Object` class, or that has a sibling
`@Invoke`: KlangScript has no overloads, a callable object has exactly one call form (2026-09-07).
`Katalyst(k => k.gain(2.5))` then dispatches to that method through the SAME spec-aware path as
`Katalyst.build(k => ...)`: named arguments, default thunks and the trailing-lambda rule all apply.
Errors name the call as written (`in Katalyst: unknown parameter ...`). An object without a call form
stays a plain value; calling it is a type error (`'x' cannot be called: it is not a function.`).

The docs carry the call form as the object's second variant (`val Katalyst: Katalyst`, then
`Katalyst(configure): KatalystDsl`), so the analyzer resolves `Katalyst(...)` like any top-level call
(return type, typed lambda parameter, named-argument checks); a value holding the object (`Kat(...)`,
`let d = duck; d(1)`) resolves through `KlangDocsRegistry.getCallForm(type)`. The call form never
appears as a member completion.

```javascript
master(Katalyst(k => k.reverb(0.05).gain(2.5)))             // == Katalyst.build(k => ...)
master(Katalyst())                                          // == Katalyst.build(), the empty chain
```

Tests: `NativeObjectInvokeTest.kt` (runtime), `InvokeAnalysisTest.kt` (analyzer), `CallableObjectDocsSpec`
(sprudel) and `KatalystCallFormSpec` (klangscript-libs) on the real registries. Design:
`docs/tasks/klangscript-native-object-operators.md` (revision 2026-09-05). The arithmetic and
comparison operators of that plan are designed, not built.
