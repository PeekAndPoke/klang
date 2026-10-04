# KlangScript — Pending Work

## High Priority

### Language Features

- [x] Array indexing: `arr[0]`, `arr[1]` — `IndexAccess` AST node + interpreter ✅
- [x] Strict equality: `===`, `!==` ✅
- [x] Control flow: `if/else`, `while`, `for`, `do/while`, `break`, `continue` ✅
- [x] Template string interpolation: `` `hello ${name}` `` ✅
- [x] Ternary operator: `x ? y : z` ✅
- [x] Logical operators: `&&`, `||` ✅
- [x] String concatenation: `string + string` ✅ (no implicit coercion — use template literals for mixed types)
- [ ] Higher-order array methods: `map()`, `filter()`, `forEach()`, `find()`, `some()`, `every()`, `reduce()` —
  needs callback execution from extension method context
- [ ] `when`-expression — replacement for JS `switch`; no fall-through; expression form; Kotlin semantics
- [ ] `for...in` loop — iterate over object keys
- [ ] `for...of` loop — iterate over array elements

## Medium Priority

### Language Features

- [ ] Spread operator: `...args` in function calls and array/object literals
- [ ] Destructuring: `let { a, b } = obj` and `let [a, b] = arr`
- [ ] `delete obj.prop`
- [ ] Default parameters: `(x = 10) => x`
- [ ] `function` keyword (low priority — arrow functions cover all use cases)
- [ ] `undefined` as alias for `null`

### Standard Library (Kotlin-style, not JS-style)

- [ ] String stdlib module — Kotlin-convention methods (`trimStart`, `trimEnd`, `padStart`, `padEnd`, etc.)
- [ ] Array/List stdlib module — immutable Kotlin conventions (`sorted`, `distinct`, `groupBy`, etc.)
- [ ] Object stdlib module — `Object.assign`, `hasOwnProperty`
- [ ] Type conversion globals: `Number()`, `String()`, `Boolean()`, `parseInt()`, `parseFloat()`

### Parser Robustness

- [ ] Error recovery — parser must always produce something, with helpful error messages
- [ ] Better error messages — show position and context

## Low Priority

### Phase 5 — Testing & Quality

- [ ] > 90% code coverage
- [ ] Property-based tests
- [ ] Performance regression tests
- [ ] Error message quality review

### Phase 6 — Future

- [ ] REPL
- [ ] Live coding hot reload / state preservation
- [ ] Bytecode VM (long-term)
- [ ] Formal language specification (grammar, semantics, JS differences guide)
