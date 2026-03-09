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
