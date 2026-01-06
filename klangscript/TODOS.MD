# KlangScript live coding engine

## Project Overview

- JavaScript-like scripting language for live coding
- Multiplatform (Kotlin multiplatform)
- Parser: better-parse (https://github.com/h0tk3y/better-parse)
- Runtime: Tree-walking interpreter (start simple, can evolve to bytecode VM later)

## Phase 1: Parser

### Core Components

- [ ] Lexer (tokenization)
- [ ] Parser using better-parse combinator library
- [ ] AST (Abstract Syntax Tree) data structures
- [ ] Error handling and reporting

### Language Features to Parse

- [ ] Literals (numbers, strings, booleans, null/undefined)
- [ ] Variables (let, const, var declarations)
- [ ] Operators (arithmetic, logical, comparison, assignment)
- [ ] Functions (declarations, expressions, arrow functions)
- [ ] Control flow (if/else, switch, loops: for, while, do-while)
- [ ] Objects and arrays
- [ ] Property access (dot notation, bracket notation)
- [ ] Comments (single-line, multi-line)

## Phase 2: Tree-Walking Interpreter

### Core Execution Engine

- [ ] AST visitor/interpreter pattern
- [ ] Evaluation context/environment
- [ ] Scope management (lexical scoping)
- [ ] Value representation (runtime types)

### Runtime Features

- [ ] Variable storage and retrieval
- [ ] Function calls and returns
- [ ] Closures
- [ ] Object creation and property access
- [ ] Array operations
- [ ] Type coercion rules
- [ ] Error handling (runtime exceptions)

## Phase 3: Standard Library / Built-ins

### Core Functions

- [ ] Console output (print, log, etc.)
- [ ] Type checking (typeof, instanceof)
- [ ] String operations
- [ ] Array methods
- [ ] Math operations
- [ ] Object utilities

### Live Coding Specific

- [ ] Hot reload / re-evaluation support
- [ ] State preservation between evaluations
- [ ] REPL capabilities

## Phase 4: Advanced Features (Future)

### Language Features

- [ ] Classes and inheritance
- [ ] Async/await
- [ ] Generators
- [ ] Destructuring
- [ ] Spread operator
- [ ] Template literals
- [ ] Modules/imports

### Optimization

- [ ] Bytecode VM (if performance becomes critical)
- [ ] JIT compilation considerations
- [ ] Caching and memoization

## Testing & Quality

- [ ] Unit tests for parser
- [ ] Unit tests for interpreter
- [ ] Integration tests (end-to-end scripts)
- [ ] Error message quality
- [ ] Performance benchmarks

## Documentation

- [ ] Language specification
- [ ] API documentation
- [ ] Example scripts
- [ ] Tutorial/getting started guide

## Language Specification Details

### Top-Level Function Calls

- Scripts can have top-level function calls (not just inside other functions)
- Example: `setCps(0.5)`
- Functions must be registrable with the engine from Kotlin code
- Functions have return types (Unit, Double, String, etc.)
- Engine must support calling Kotlin functions from script code

### Function Call Syntax

- Multiple parameters supported
- Trailing commas allowed (and common in live coding style)
- Example:
  ```javascript
  stack(
    note("a b c d"),
    sound("bd hh sd hh"),
  )
  ```
- Nested function calls as arguments
- Parser must handle trailing commas gracefully

### Method Chaining / Fluent API

- Functions can be called at top-level OR chained on results
- Functions return values that support further chaining
- Order doesn't matter (composable)
- Examples:
  ```javascript
  note("a b c d").gain(0.5).pan("0.0 0.0 1.0 -1.0")
  gain(0.5).note("a b c d").pan("0.0 0.0 1.0 -1.0")
  ```
- Functions like `note`, `gain`, `pan` must be:
    - Available as top-level functions
    - Available as chainable methods on return values
- Parser needs to support dot notation for method calls
- Runtime needs flexible function registration (dual-mode: standalone + chainable)

### Named Objects with Methods

- Named objects can be registered and accessed in scripts
- Objects have their own methods
- Example:
  ```javascript
  note("a b c d").pan(sine.range(-1.0, 1.0))
  ```
- In this case:
    - `sine` is a named object registered with the engine
    - `range(-1.0, 1.0)` is a method on the `sine` object
    - The result is used as an argument to `pan()`
- Engine must support:
    - Registering named objects (constants/singletons)
    - Registering methods on those objects
    - Objects can also support chaining

### Callbacks / Lambda Functions

- Arrow function syntax for callbacks (JavaScript-style)
- Functions can accept callbacks as parameters
- Example:
  ```javascript
  note("a b c d").superImpose(x => x.detune(0.5))
  ```
- In this case:
    - `superImpose` accepts a callback function
    - `x => x.detune(0.5)` is an arrow function
    - The callback receives a parameter (`x`) and can chain methods on it
- Parser must support:
    - Arrow function syntax: `param => expression`
    - Multiple parameters: `(a, b) => expression`
    - Block bodies: `x => { return x.detune(0.5); }`
- Runtime must support:
    - Function values (first-class functions)
    - Passing functions as arguments
    - Closures (capturing environment)

### Function Registration API (Design)

- [ ] Define interface for registering native Kotlin functions
- [ ] Support typed return values
- [ ] Support typed parameters
- [ ] Handle type conversion between script and Kotlin
- [ ] Example registration API needed:
  ```kotlin
  engine.registerFunction("setCps") { cps: Double ->
      // implementation
      cps // return value
  }
  ```

## Notes

- Focus on live coding use case: transparency, debuggability, hackability
- Keep it simple initially - can always add complexity later
- Multiplatform from the start (JS, JVM, Native targets)
