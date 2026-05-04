# KlangScript — Language Design

## Design Principles

- **No `undefined`** — `undefined` is a plain alias for `null`; there is no distinct undefined type or value
- **Semicolons optional** — newlines act as statement separators
- **Arrow functions only** — no `function` keyword; both expression and block bodies supported
- **Immutable engine** — builder pattern for configuration, immutable after build (thread-safe)
- **ES6-compatible imports/exports** — familiar to web developers
- **Export control** — explicit exports prevent scope pollution in libraries
- **Control flow is expression-based** — `if`, `switch`, and similar constructs evaluate to a value so they can appear
  anywhere an expression is valid: `let x = if (cond) { 1 } else { 2 }`. A construct used as a statement simply discards
  the result.
- **Stdlib is modular, not built-in** — `Array`, `String`, `Math`, `Object` utility methods live in separate, opt-in
  stdlib libraries (not hardcoded in the core). The language core only defines syntax and semantics.

## Supported Syntax

**Literals:** numbers, strings (double/single/backtick), booleans, `null`, objects `{}`, arrays `[]`

**Operators:** arithmetic (`+`, `-`, `*`, `/`, `%`), comparison (`==`, `!=`, `<`, `<=`, `>`, `>=`), logical (`&&`,
`||`), unary (`-`, `+`, `!`)

**Functions:**

```javascript
let f = x => x + 1                // expression body
let g = (x, y) => { return x + y } // block body
let h = x => ({ key: x })          // object literal (must wrap in parens)
```

**Variables:** `let x = 1` / `const x = 1` (both mutable at runtime for simplicity)

**Imports/Exports:**

```javascript
import * from "lib"
import * as math from "lib"
import { x, y as z } from "lib"
export { x, y as z }       // re-export of existing top-level bindings
export name = expr         // combined immutable binding + auto-export under same name
```

The `export name = expr` form is the preferred way for library/module files to expose
named parts: it makes the public surface obvious at the declaration site and the bound
name is immutable (like `const`). Module identifier strings can be namespaced and / or
versioned (e.g. `"peekandpoke/der-schmetterling@1.0"`) — the parser treats them as
opaque, so namespaced URIs work today even though the resolver only handles
locally-registered libraries in v0.

**Built-in methods:**

- Array: `length()`, `push()`, `pop()`, `shift()`, `unshift()`, `slice()`, `concat()`, `join()`, `reverse()`,
  `indexOf()`, `includes()`
- String: `length()`, `charAt()`, `substring()`, `indexOf()`, `split()`, `toUpperCase()`, `toLowerCase()`, `trim()`,
  `startsWith()`, `endsWith()`, `replace()`, `slice()`, `concat()`, `repeat()`
- Object: `Object.keys()`, `Object.values()`, `Object.entries()`

## Out of Scope — Never Implement

These features are **explicitly excluded** from KlangScript. Do not plan, prototype, or add stubs for them:

- **`Promise`, `async`/`await`** — no async execution model; KlangScript is synchronous by design. Different solutions
  for async/reactive patterns will be provided at the application layer.
- **JS stdlib global objects** — `Date`, `Math` (as a global, stdlib version is different), `JSON`, `RegExp`, `Error`,
  `Map`, `Set`, `WeakMap`, `WeakSet`, `Symbol`, `Proxy`, `Reflect`, `Intl`, etc. If domain-specific utilities are
  needed, they will be added as native Kotlin extensions via `KlangScriptExtensionBuilder`.
- **`typeof` / `instanceof`** — runtime type inspection operators are not needed; use pattern-based design instead.
- **`class` / `extends` / `new`** — no OOP class hierarchy; use object literals and closures.
- **`try` / `catch` / `throw`** — no user-level exception handling; errors propagate as interpreter-level exceptions.
- **`eval`** — never.
- **`undefined` as a distinct value** — `undefined` is just a global alias for `null`. There is no separate undefined
  type, `typeof x === "undefined"` pattern, or `void 0`. Use `null` everywhere.

## Known Limitations (not yet implemented)

- Array indexing: `arr[0]` — requires `IndexAccess` AST node
- Control flow: `if/else`, `while`, `for`
- Strict equality: `===`, `!==` (current `==` uses reference equality for objects/arrays)
- Template string interpolation: `` `hello ${name}` ``
- Spread operator: `...args`
- Destructuring: `let { a, b } = obj`
- Higher-order array methods: `map()`, `filter()`, `reduce()` etc. (needs callback execution from extension context)
- Default exports
- REPL
