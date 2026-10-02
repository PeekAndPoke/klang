# KlangScript Language Basics

> Paste this into any LLM as context so it can write syntactically correct KlangScript code.

KlangScript is a JavaScript-like scripting language for live coding music.
Every KlangScript music file starts with these imports:

```javascript
import * from "stdlib"
import * from "sprudel"
```

## Variables

```javascript
let x = 42          // mutable variable
let name            // uninitialized (defaults to null)
const PI = 3.14     // immutable constant
```

## Numbers and Strings

```javascript
42                  // integer
3.14                // double
"hello"             // double-quoted string
'hello'             // single-quoted string
`hello`             // backtick string (multiline OK)
`Hello ${name}!`    // template interpolation (backticks only)
true                // boolean
false
null                // null value
```

## Arrow Functions

There is no `function` keyword. All functions use arrow syntax:

```javascript
let double = x => x * 2                  // expression body (implicit return)
let add = (a, b) => a + b                // multiple params need parentheses
let greet = name => { return `Hi ${name}` }  // block body needs explicit return
```

## Operators

```javascript
// Arithmetic
+  -  *  /  %  **

// Comparison
==  !=  <  <=  >  >=

// Logical (short-circuit)
&&  ||  !

// Ternary
let x = cond ? a : b

// Assignment
=  +=  -=  *=  /=
```

## Control Flow

```javascript
// If (expression — returns a value)
let x = if (a > b) { a } else { b }

// Loops
while (cond) { ... }
for (let i = 0; i < 10; i++) { ... }
// break and continue are supported
```

## Arrays and Objects

```javascript
let arr = [1, 2, 3]
let obj = { name: "pad", gain: 0.5 }
arr[0]              // index access
obj.name            // member access
```

## Comments

```javascript
// single-line comment
/* multi-line
   comment */
```

## Import Syntax

```javascript
import * from "stdlib"              // import all into scope
import * from "sprudel"             // import all into scope
import * as math from "stdlib"      // import into namespace
import { sin, cos } from "stdlib"   // named imports
```

## Built-in Methods

Read from the stdlib 2026-10-02 (`klangscript-libs/.../stdlib/KlangScriptArrayExtensions.kt`, `...StringExtensions.kt`,
`KlangScriptMath.kt`). Arrays follow Kotlin, not JavaScript (`add`, not `push`).

**Array:** `size()`, `first()`, `last()`, `add()`, `removeAt()`, `removeLast()`, `removeFirst()`, `reversed()`,
`drop()`, `take()`, `subList()`, `joinToString()`, `indexOf()`, `contains()`, `isEmpty()`, `isNotEmpty()`.
No higher-order methods yet (`map`, `filter`, `reduce`, `forEach`): use a `for` loop.

**String:** `length()`, `charAt()`, `substring()`, `indexOf()`, `split()`, `toUpperCase()`, `toLowerCase()`, `trim()`,
`startsWith()`, `endsWith()`, `replace()`, `slice()`, `concat()`, `repeat()`

**Math:** `Math.sqrt()`, `Math.pow()`, `Math.abs()`, `Math.floor()`, `Math.ceil()`, `Math.round()`, `Math.sin()`,
`Math.cos()`, `Math.tan()`, `Math.min()`, `Math.max()`. No `Math.random()` and no `Math.PI` yet.

**Functions** take a fixed list of named parameters: no rest parameters (`...args`) and no spread. A script
function that should take any number of things takes an array.

## What KlangScript Does NOT Have

- No `class`, `new`, `extends`, `this`
- No `async`/`await`, `try`/`catch`
- No `typeof`, `instanceof`
- No `switch` statement
- No destructuring
- Semicolons are optional (newlines act as separators)
