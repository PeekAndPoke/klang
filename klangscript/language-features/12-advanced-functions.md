# KlangScript — Advanced Function Patterns

### 12.1 Function Hoisting ❌ `[OUT_OF_SCOPE]` — KlangScript uses arrow functions only; no `function` keyword hoisting

```javascript
let result = hoisted();

function hoisted() {
    return "works";
}

// result is "works"
```

**Expected:** result = "works"

### 12.2 Variable Hoisting with var ❌ `[OUT_OF_SCOPE]` — `var` keyword not in KlangScript; use `let`/`const`

```javascript
function test() {
    console.log(x);  // undefined (not error)
    var x = 5;
    console.log(x);  // 5
}
```

**Expected:** First log is undefined, second is 5

### 12.3 Scope and Closures ✅

```javascript
function outer() {
    let x = 10;

    function inner() {
        return x + 5;
    }

    return inner;
}

let fn = outer();
let result = fn();  // 15
```

**Expected:** result = 15

### 12.4 Closure with Multiple Functions ✅

```javascript
function createCounter() {
    let count = 0;
    return {
        increment: () => ++count,
        decrement: () => --count,
        getCount: () => count
    };
}

let counter = createCounter();
counter.increment();  // 1
counter.increment();  // 2
counter.decrement();  // 1
let val = counter.getCount();  // 1
```

**Expected:** val = 1

### 12.5 Partial Application ✅ — works with arrow functions; example uses `function` keyword (see 4.1)

```javascript
function multiply(a, b) {
    return a * b;
}

function partial(fn, a) {
    return function (b) {
        return fn(a, b);
    };
}

let double = partial(multiply, 2);
let result = double(5);  // 10
```

**Expected:** result = 10

### 12.6 Function Composition ✅

```javascript
let add5 = x => x + 5;
let multiply2 = x => x * 2;
let compose = (f, g) => x => f(g(x));
let addThenMultiply = compose(multiply2, add5);
let result = addThenMultiply(10);  // (10 + 5) * 2 = 30
```

**Expected:** result = 30

### 12.7 Memoization 🟡 — blocked on 1.5 (assignment) + 6.17 (`in` operator); both are
`[EASY]`; will work once those land

```javascript
function memoize(fn) {
    let cache = {};
    return function (n) {
        if (n in cache) {
            return cache[n];
        }
        let result = fn(n);
        cache[n] = result;
        return result;
    };
}

let slowFib = n => n <= 1 ? n : slowFib(n - 1) + slowFib(n - 2);
let fastFib = memoize(slowFib);
let result = fastFib(10);  // 55
```

**Expected:** result = 55

### 12.8 Currying ❌ `[HARD]` — requires rest parameters (`...args`) and `fn.length` property access

```javascript
function curry(fn) {
    return function curried(...args) {
        if (args.length >= fn.length) {
            return fn(...args);
        }
        return function (...nextArgs) {
            return curried(...args, ...nextArgs);
        };
    };
}

let add3 = (a, b, c) => a + b + c;
let curriedAdd = curry(add3);
let result1 = curriedAdd(1)(2)(3);      // 6
let result2 = curriedAdd(1, 2)(3);      // 6
let result3 = curriedAdd(1)(2, 3);      // 6
```

**Expected:** All results = 6
