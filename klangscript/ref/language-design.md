# KlangScript — Language Design

## Design Principles

- **No `undefined`** — only `null` for absent values
- **Semicolons optional** — newlines act as statement separators
- **Arrow functions only** — no `function` keyword; both expression and block bodies supported
- **Immutable engine** — builder pattern for configuration, immutable after build (thread-safe)
- **ES6-compatible imports/exports** — familiar to web developers
- **Export control** — explicit exports prevent scope pollution in libraries

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
export { x, y as z }
```

**Built-in methods:**

- Array: `length()`, `push()`, `pop()`, `shift()`, `unshift()`, `slice()`, `concat()`, `join()`, `reverse()`,
  `indexOf()`, `includes()`
- String: `length()`, `charAt()`, `substring()`, `indexOf()`, `split()`, `toUpperCase()`, `toLowerCase()`, `trim()`,
  `startsWith()`, `endsWith()`, `replace()`, `slice()`, `concat()`, `repeat()`
- Object: `Object.keys()`, `Object.values()`, `Object.entries()`

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
