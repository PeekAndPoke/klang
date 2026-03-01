# KlangScript — Pending Work

## High Priority

### Language Features

- [ ] Array indexing: `arr[0]`, `arr[1]` — requires `IndexAccess` AST node
- [ ] Strict equality: `===`, `!==` — current `==` uses reference equality for objects/arrays
- [ ] Control flow: `if/else`, `while`, `for`
- [ ] Template string interpolation: `` `hello ${name}` ``
- [ ] Higher-order array methods: `map()`, `filter()`, `forEach()`, `find()`, `some()`, `every()` — needs callback
  execution from extension method context

## Medium Priority

### Language Features

- [ ] Spread operator: `...args`
- [ ] Destructuring: `let { a, b } = obj`
- [ ] Ternary operator: `x ? y : z`
- [ ] Default exports
- [ ] Logical operators: `&&`, `||` (parser tokens exist; verify interpreter coverage)
- [ ] Function references (partially works, not fully tested — Step 2.9)

### Parser Robustness

- [ ] Error recovery — parser must always produce something, with helpful error messages
- [ ] Better error messages — show position and context

## Low Priority

### Phase 4 — Documentation & Examples

- [ ] KDoc on all public APIs
- [ ] Feature showcase scripts
- [ ] Tutorial scripts
- [ ] Formal language specification (grammar, semantics, JS differences guide)

### Phase 5 — Testing & Quality

- [ ] > 90% code coverage
- [ ] Property-based tests
- [ ] Performance regression tests
- [ ] Error message quality review

### Phase 6 — Future

- [ ] REPL
- [ ] Live coding hot reload / state preservation
- [ ] Bytecode VM (long-term)
- [ ] `this` binding
- [ ] Prototype chain
