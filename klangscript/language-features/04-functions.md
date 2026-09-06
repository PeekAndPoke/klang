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
  defaults. `Osc.supersaw(x => x.voices(9))` lands the lambda in the trailing `configure`
  parameter although `freq` comes first. Only the last argument floats; two candidates are
  ambiguous (name the parameter instead).
- **Typed parameters in the editor**: the analyzer binds the lambda's parameters with the
  callee's declared function-parameter types, so `x.` completes inside the lambda.

```javascript
note("c3").superimpose(x => x.transpose(12))              // x: SprudelPattern
Osc.supersaw(x => x.voices(9).spread(0.1)).lowpass(800)   // x: OscSuperSawBuilder (klangscript-libs)
```

A door that wants the trailing-lambda rule must give every EARLIER optional parameter a literal
default (number, string, boolean, null): the rule runs on the spec-aware call path, which needs
a default thunk for each skipped slot, and KSP only emits thunks for literals. The KSP processor
refuses a function-typed parameter preceded by a non-literal optional default.

Tests: `ArgAlignmentTest.kt`, `ConfigureLambdaBindingTest.kt` (runtime), `AnalyzedAstTest.kt`
("configure lambda" cases, analyzer). Plan: `docs/tasks/dsl-configure-lambdas.md`.

### 4.11 Callable native objects (`invoke`) ✅

A native object registered from Kotlin becomes callable when its type registers a method named
`invoke` (`@KlangScript.Method(name = "invoke")` on an `@KlangScript.Object` member, or a hand
registration under `NativeOperatorNames.INVOKE`). `Master(m => m.gain(2.5))` then dispatches to
that method through the SAME spec-aware path as `Master.build(m => ...)`: named arguments,
default thunks and the trailing-lambda rule all apply. An object without `invoke` stays a plain
value; calling it is a type error that names the missing method. The analyzer resolves the call
to the `invoke` callable (return type, typed lambda parameter, hover signature rendered as
`Master(...)`), and `invoke` never appears as a member completion.

```javascript
master(Master(m => m.reverb(r => r.wet(0.05)).gain(2.5)))   // == Master.build(m => ...)
master(Master())                                            // == Master.default()
```

Tests: `NativeObjectInvokeTest.kt` (runtime), `InvokeAnalysisTest.kt` (analyzer). Design:
`docs/tasks/klangscript-native-object-operators.md` (revision 2026-09-05). The arithmetic and
comparison operators of that plan are designed, not built.
