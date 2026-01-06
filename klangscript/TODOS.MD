# KlangScript live coding engine

## Project Overview

- JavaScript-like scripting language for live coding
- Multiplatform (Kotlin multiplatform)
- Parser: better-parse (https://github.com/h0tk3y/better-parse)
- Runtime: Tree-walking interpreter (start simple, can evolve to bytecode VM later)

## Design Decisions

### Simplified Initial Scope

1. **Arrow Functions**: Expression bodies only initially (`x => x + 1`)
  - Block bodies `x => { return x; }` moved to Phase 6
  - Avoids ambiguity with object literals

2. **Semicolons**: Optional, newlines act as statement separators
  - No complex ASI (Automatic Semicolon Insertion) rules
  - Keep it simple and intuitive

3. **Import**: Built-in function, not special syntax
  - `import("library")` is just a regular function call
  - No special parsing needed

4. **Statements**: Any expression can be a statement
  - Top-level: `1 + 1` is valid (ExpressionStatement)
  - Top-level: `note("a").gain(0.5)` is valid

5. **Object Literal Disambiguation**:
  - In arrow functions, wrap objects in parentheses: `x => ({ a: 1 })`
  - Without parentheses: `x => x.prop`

6. **No `undefined`**: Only `null` for null values

7. **Chainable Functions Implementation**:
  - Functions return wrapper objects with all chainable methods
  - Registration API handles dual-mode (top-level + chainable)

## Detailed Implementation Plan

### Phase 1: Foundation & Basic Parsing

#### Step 1.1: Project Setup & AST Data Structures ✅

- [x] Create AST sealed class hierarchy for all node types
  - [x] Expressions: Literal, Identifier, BinaryOp, UnaryOp, CallExpr, MemberAccess, ArrowFunction, ObjectLiteral, etc.
  - [x] Statements: LetDeclaration, ExpressionStatement, etc.
  - [x] Program root node
- [x] Write unit tests for AST node creation and structure
- [x] Set up better-parse dependency in build.gradle.kts

#### Step 1.2: Lexer - Basic Tokens ✅

- [x] Implement token types (sealed class/enum)
  - [x] Keywords: `let`, `const`, `var`
  - [x] Literals: Numbers (Int/Double), Strings (single/double/backtick), Booleans, Null (no undefined)
  - [x] Identifiers
  - [x] Operators: `+`, `-`, `*`, `/`, `=`, `=>`, `.`
  - [x] Punctuation: `(`, `)`, `{`, `}`, `,`, `:`, `;`
- [x] Implement tokenizer using better-parse
- [x] Write unit tests for each token type (covered by integration tests)
- [x] Test edge cases (whitespace, newlines, EOF)

#### Step 1.3: Lexer - Comments & Strings ✅

- [x] Implement single-line comment handling (`//`)
- [x] Implement multi-line comment handling (`/* */`)
- [x] Implement string parsing:
  - [x] Single-quoted strings
  - [x] Double-quoted strings
  - [x] Backtick strings (multi-line)
  - [x] Escape sequences (handled at token level)
- [x] Write comprehensive unit tests for comments and strings
- [x] Test edge cases (multi-line strings, special characters, mixed content)

#### Step 1.4: Parser - Literals & Identifiers ✅

- [x] Parse numeric literals (integers and decimals)
- [x] Parse string literals (all three types)
- [x] Parse boolean literals (`true`, `false`)
- [x] Parse `null`
- [x] Parse identifiers
- [x] Write unit tests for all literal types
- [x] Test invalid syntax handling

#### Step 1.5: Parser - Arithmetic Expressions ✅

- [x] Parse binary operators (`+`, `-`, `*`, `/`)
- [x] Implement operator precedence
- [x] Parse parenthesized expressions
- [ ] Parse unary operators (`-`, `+`, `!`) - deferred
- [x] Write unit tests for:
  - [x] Simple arithmetic: `1 + 2`, `3 * 4`
  - [x] Precedence: `1 + 2 * 3` should be `1 + (2 * 3)`
  - [x] Parentheses: `(1 + 2) * 3`
  - [x] Nested expressions
  - [x] Division in arguments: `1/3`, `2/8`

#### Step 1.6: Parser - Function Calls ✅

- [x] Parse simple function calls: `foo()`
- [x] Parse function calls with arguments: `foo(1, 2, 3)`
- [x] Parse nested function calls: `foo(bar(1))`
- [x] Support trailing commas in argument lists
- [x] Write unit tests for:
  - [x] Zero arguments
  - [x] Single argument
  - [x] Multiple arguments
  - [x] Trailing commas
  - [x] Nested calls
  - [x] Expressions as arguments

#### Step 1.7: Parser - Method Chaining ✅

- [x] Parse member access (dot notation): `obj.prop`
- [x] Parse chained method calls: `obj.foo().bar()`
- [x] Parse long chains: `a.b().c(1).d(x => x)`
- [x] Write unit tests for:
  - [x] Simple property access
  - [x] Method calls on results
  - [x] Long chains with mixed calls and properties
  - [x] Chains with complex arguments (including arithmetic)

#### Step 1.8: Parser - Arrow Functions ✅

- [x] Parse single parameter arrow functions: `x => expr`
- [x] Parse multiple parameter arrow functions: `(a, b) => expr`
- [x] Parse arrow functions without parentheses (single param)
- [ ] Support object literals with parentheses: `x => ({ a: 1 })` - deferred to Step 1.10
- [x] Write unit tests for:
  - [x] Single parameter, single expression
  - [x] Multiple parameters
  - [x] Nested arrow functions
  - [x] Arrow functions as arguments
  - [ ] Object literals in arrow functions with parentheses - deferred to Step 1.10
- [x] NOTE: Block bodies `x => { return x; }` are deferred to Phase 6

#### Step 1.9: Parser - Variables ✅

- [x] Parse `let` declarations: `let x = 1`
- [x] Parse `const` declarations: `const x = 1`
- [ ] Parse `var` declarations: `var x = 1` - deferred
- [x] Parse declarations without initialization: `let x`
- [x] Write unit tests for:
  - [x] All declaration types (let and const)
  - [x] With and without initialization
  - [x] Complex initializer expressions
  - [x] Multiple declarations in sequence

#### Step 1.10: Parser - Object Literals ✅

- [x] Parse empty objects: `{}`
- [x] Parse objects with properties: `{ a: 1, b: 2 }`
- [x] Parse nested objects: `{ a: { b: 1 } }`
- [x] Parse string keys: `{ 'a': 1, "b": 2 }`
- [x] Support trailing commas
- [x] Write unit tests for:
  - [x] Empty objects
  - [x] Simple properties
  - [x] String keys
  - [x] Nested objects
  - [x] Trailing commas
  - [x] Complex values (function calls, expressions)

#### Step 1.11: Parser - Complete Program

- [ ] Parse top-level function calls
- [ ] Parse top-level expressions as statements (ExpressionStatement)
- [ ] Parse multiple statements in sequence
- [ ] Parse mixed declarations and expressions
- [ ] Handle newlines as statement separators (semicolons optional)
- [ ] Write unit tests for:
  - [ ] Single statement programs
  - [ ] Multiple statements (newline-separated)
  - [ ] Optional semicolons
  - [ ] Mixed declarations and calls
  - [ ] Real-world examples from Strudel

#### Step 1.12: Parser - Error Handling

- [ ] Implement error recovery strategies
- [ ] Provide meaningful error messages
- [ ] Report line and column numbers
- [ ] Handle unexpected tokens
- [ ] Write unit tests for:
  - [ ] Syntax errors with good messages
  - [ ] Unclosed parentheses/braces
  - [ ] Invalid expressions
  - [ ] Recovery and continued parsing

### Phase 2: Tree-Walking Interpreter

#### Step 2.1: Runtime Value System

- [ ] Create `RuntimeValue` sealed class hierarchy
  - [ ] NumberValue (Double)
  - [ ] StringValue
  - [ ] BooleanValue
  - [ ] NullValue
  - [ ] FunctionValue (native and script functions)
  - [ ] ObjectValue (map of properties)
- [ ] Write unit tests for value creation and type checking
- [ ] Implement value-to-string conversion (for debugging)

#### Step 2.2: Environment & Scope Management

- [ ] Create `Environment` class for variable storage
- [ ] Implement lexical scoping (parent environment chain)
- [ ] Support variable declaration (let, const, var)
- [ ] Support variable lookup (traverse scope chain)
- [ ] Support variable assignment
- [ ] Write unit tests for:
  - [ ] Variable declaration and retrieval
  - [ ] Shadowing (inner scope hides outer)
  - [ ] Scope chain traversal
  - [ ] Undefined variable errors

#### Step 2.3: Interpreter Core - Expressions

- [ ] Create `Interpreter` class with AST visitor pattern
- [ ] Implement evaluation for:
  - [ ] Literal expressions (numbers, strings, booleans, null)
  - [ ] Identifier expressions (variable lookup)
  - [ ] Binary operations (+, -, *, /)
  - [ ] Unary operations (-, +, !)
  - [ ] Parenthesized expressions
- [ ] Write unit tests for:
  - [ ] Each expression type
  - [ ] Arithmetic correctness
  - [ ] Type errors (e.g., "string" + null)

#### Step 2.4: Interpreter - Function Calls (Native)

- [ ] Design function registration API
  - [ ] Interface for native Kotlin functions
  - [ ] Support for typed parameters and return values
  - [ ] Type conversion between runtime values and Kotlin types
- [ ] Implement function call evaluation
- [ ] Register test functions (e.g., `setCps`, `print`)
- [ ] Write unit tests for:
  - [ ] Calling registered functions
  - [ ] Parameter passing
  - [ ] Return value handling
  - [ ] Wrong number of arguments errors
  - [ ] Type conversion

#### Step 2.5: Interpreter - Arrow Functions (Script Functions)

- [ ] Implement closure capture (environment snapshot)
- [ ] Implement arrow function evaluation
- [ ] Support function values as first-class citizens
- [ ] Implement function invocation
- [ ] Write unit tests for:
  - [ ] Simple arrow functions: `x => x + 1`
  - [ ] Closures capturing outer variables
  - [ ] Nested functions
  - [ ] Functions as return values
  - [ ] Functions as arguments

#### Step 2.6: Interpreter - Method Chaining & Member Access

- [ ] Implement member access (dot notation)
- [ ] Support methods on runtime values
- [ ] Design chainable function API
  - [ ] Functions can return objects with methods
  - [ ] Methods can be registered on value types
- [ ] Implement chained call evaluation
- [ ] Write unit tests for:
  - [ ] Simple property access
  - [ ] Method calls on objects
  - [ ] Long method chains
  - [ ] Mixing properties and methods

#### Step 2.7: Interpreter - Objects

- [ ] Implement object literal evaluation
- [ ] Support property access (get/set)
- [ ] Support nested objects
- [ ] Support computed property names (if needed)
- [ ] Write unit tests for:
  - [ ] Creating objects
  - [ ] Reading properties
  - [ ] Setting properties
  - [ ] Nested objects
  - [ ] Objects as function arguments

#### Step 2.8: Interpreter - Named Objects & Built-ins

- [ ] Design API for registering named objects (singletons)
- [ ] Register built-in objects (e.g., `sine`, `saw`)
- [ ] Support methods on named objects
- [ ] Write unit tests for:
  - [ ] Accessing named objects: `sine`
  - [ ] Calling methods on named objects: `sine.range(0, 1)`
  - [ ] Named objects as arguments

#### Step 2.9: Interpreter - Function References

- [ ] Distinguish function calls vs function references in evaluator
- [ ] Support passing function references as arguments
- [ ] Implement partial application (if needed)
- [ ] Write unit tests for:
  - [ ] Passing function by reference: `jux(rev)`
  - [ ] vs calling function: `jux(rev())`
  - [ ] Function references with built-ins

#### Step 2.10: Interpreter - Variables (Let Declarations)

- [ ] Implement `let` declaration execution
- [ ] Implement `const` declaration execution
- [ ] Implement `var` declaration execution
- [ ] Handle uninitialized variables
- [ ] Write unit tests for:
  - [ ] Declaring and using variables
  - [ ] Using variables in expressions
  - [ ] Variable scope
  - [ ] Const assignment errors

#### Step 2.11: Integration Tests - End-to-End

- [ ] Write integration tests for complete scripts
- [ ] Test examples from Strudel:
  - [ ] `setCps(0.5)`
  - [ ] `note("a b c d").gain(0.5).pan("0 0 1 -1")`
  - [ ] `stack(note("a"), sound("bd"))`
  - [ ] `note("a b c d").superImpose(x => x.detune(0.5))`
  - [ ] `let chords = chord("Cm"); stack(chords)`
- [ ] Test error handling and reporting
- [ ] Performance benchmarks (basic)

#### Step 2.12: Error Handling & Debugging

- [ ] Implement runtime error types
  - [ ] TypeError
  - [ ] ReferenceError
  - [ ] ArgumentError
- [ ] Provide stack traces
- [ ] Report source locations for errors
- [ ] Write unit tests for:
  - [ ] Various error types
  - [ ] Error messages quality
  - [ ] Stack trace accuracy

### Phase 3: API & Integration

#### Step 3.1: Public API Design

- [ ] Design `KlangScript` facade class for easy usage
- [ ] API for parsing source code
- [ ] API for executing scripts
- [ ] API for registering functions
- [ ] API for registering objects
- [ ] Write unit tests for:
  - [ ] Basic usage patterns
  - [ ] Multiple scripts in same engine
  - [ ] Isolated vs shared environments

#### Step 3.2: Function Registration System

- [ ] Implement builder/DSL for registering functions
- [ ] Support vararg functions
- [ ] Support optional parameters
- [ ] Support default values
- [ ] Type conversion helpers
- [ ] Write unit tests for:
  - [ ] Various function signatures
  - [ ] Type conversions
  - [ ] Error handling

#### Step 3.3: Chainable Function System

- [ ] Design pattern for dual-mode functions (top-level + chainable)
- [ ] Implement pattern builder/wrapper
- [ ] Support registering chainable functions easily
- [ ] Write unit tests for:
  - [ ] Functions callable both ways
  - [ ] Method chaining behavior
  - [ ] Complex chains

#### Step 3.4: Named Objects System

- [ ] Implement object registration API
- [ ] Support methods on objects
- [ ] Support properties on objects
- [ ] Write unit tests for:
  - [ ] Object registration
  - [ ] Method calls
  - [ ] Property access

#### Step 3.5: Sample Built-in Functions (for testing)

- [ ] Implement `print()` / `console.log()`
- [ ] Implement basic math functions
- [ ] Implement string utilities
- [ ] Write unit tests for each built-in

#### Step 3.6: Live Coding Features

- [ ] Hot reload support (re-parse and re-evaluate)
- [ ] State preservation between evaluations
- [ ] Incremental updates
- [ ] Write unit tests for:
  - [ ] Re-evaluating scripts
  - [ ] State preservation
  - [ ] Error recovery

#### Step 3.7: Import System

- [ ] Implement `import()` as a built-in function (no special parsing)
- [ ] Implement library registry in engine
- [ ] Support registering libraries from Kotlin
- [ ] Import returns library object with functions/values
- [ ] Write unit tests for:
  - [ ] Importing libraries: `let math = import("math")`
  - [ ] Using imported functions: `math.sqrt(16)`
  - [ ] Error handling (library not found)
  - [ ] Multiple imports

#### Step 3.8: REPL Implementation

- [ ] Create interactive REPL
- [ ] Support multi-line input
- [ ] History support
- [ ] Auto-completion (basic)
- [ ] Manual testing of REPL functionality

### Phase 4: Documentation & Examples

#### Step 4.1: API Documentation

- [ ] Document all public APIs
- [ ] KDoc comments on all public classes/methods
- [ ] Usage examples in documentation

#### Step 4.2: Example Scripts

- [ ] Create example scripts showing all features
- [ ] Port some Strudel examples (simplified)
- [ ] Step-by-step tutorial scripts

#### Step 4.3: Language Specification

- [ ] Write formal grammar specification
- [ ] Document semantics and behavior
- [ ] Document differences from JavaScript

### Phase 5: Testing & Quality

#### Step 5.1: Comprehensive Test Suite

- [ ] Achieve >90% code coverage
- [ ] Edge case tests
- [ ] Property-based tests (if applicable)
- [ ] Performance regression tests

#### Step 5.2: Error Message Quality

- [ ] Review all error messages
- [ ] Provide helpful suggestions
- [ ] Clear source location reporting
- [ ] Examples of good vs bad input

#### Step 5.3: Performance Optimization

- [ ] Profile hot paths
- [ ] Optimize AST traversal
- [ ] Consider caching strategies
- [ ] Benchmark against targets

### Phase 6: Future Enhancements (Optional)

#### Advanced Language Features

- [ ] Arrow function block bodies: `x => { return x; }`
- [ ] Return statements
- [ ] Block statements
- [ ] Array literals: `[1, 2, 3]`
- [ ] Spread operator: `...args`
- [ ] Destructuring: `let {a, b} = obj`
- [ ] Template string interpolation: `` `hello ${name}` ``
- [ ] Ternary operator: `x ? y : z`
- [ ] Logical operators: `&&`, `||`
- [ ] Comparison operators: `==`, `===`, `!=`, `!==`, `<`, `>`, `<=`, `>=`
- [ ] Control flow: `if/else`, `while`, `for`

#### Runtime Enhancements

- [ ] Better type coercion
- [ ] Proper `this` binding
- [ ] Prototype chain
- [ ] Array methods (map, filter, reduce)
- [ ] String methods
- [ ] Object methods

#### Performance

- [ ] Bytecode VM implementation
- [ ] JIT compilation exploration
- [ ] Memory optimization

---

## Implementation Summary

**Total Steps: ~40 detailed implementation steps across 6 phases**

- **Phase 1 (Steps 1.1-1.12):** Parser implementation - 12 steps
- **Phase 2 (Steps 2.1-2.12):** Interpreter implementation - 12 steps
- **Phase 3 (Steps 3.1-3.7):** API & Integration - 7 steps
- **Phase 4 (Steps 4.1-4.3):** Documentation - 3 steps
- **Phase 5 (Steps 5.1-5.3):** Testing & Quality - 3 steps
- **Phase 6:** Future enhancements (optional)

**Key Principles:**

- Each step includes unit tests
- Incremental development (each step builds on previous)
- Test-driven approach
- Focus on live coding use case

---

## Language Specification Details (Reference)

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

### Variables and Let Bindings

- Variable declarations using `let`
- Example from Strudel:
  ```javascript
  let chords = chord("<Bbm9 Fm9>/4").dict('ireal')
  ```
- Variables can hold results from function calls
- Variables can be used in expressions later
- Scope rules (lexical scoping)

### Comments

- Single-line comments with `//`
- Example from Strudel:
  ```javascript
  stack( // DRUMS
    s("bd").struct("<[x*<1 2> [~@3 x]]> x>"),
  )
  ```
- Parser must skip comments during tokenization
- Multi-line comments `/* */` might also be needed

### Mini-Notation Strings (Pattern Strings)

- Special string patterns used for musical/data sequences
- Examples: `"<Bbm9 Fm9>/4"`, `"bd"`, `"<[x*<1 2> [~@3 x]]> x>"`
- These are just string literals to the parser
- The registered functions interpret these patterns
- Parser treats them as regular strings

### Import System

- Simple runtime library loading
- Syntax: `import("library-name")`
- Returns library object with functions and values
- Example:
  ```javascript
  let math = import("math")
  math.sqrt(16)
  ```
- Libraries are registered with the engine from Kotlin
- Keep it simple: no file system access, no complex module resolution
- Just a registry of named libraries

### Arithmetic and Division in Arguments

- Numeric literals including decimals
- Division operator in function arguments
- Examples from Strudel:
  ```javascript
  .off(1/3, add(2))
  .early(2/8)
  ```
- Parser must support:
  - Integer and floating-point literals
  - Arithmetic operators: `+`, `-`, `*`, `/`
  - Operator precedence
  - Arithmetic expressions as function arguments

### Multiple Parameters in Callbacks

- Callbacks can use the parameter to call functions
- Passing function references as arguments
- Examples:
  ```javascript
  .layer(
    x => x.s("sawtooth").vib(4),
    x => x.s("square").add(note(12))
  )
  .sometimes(mul(speed("1.5")))
  ```
- In this case:
  - `layer()` takes multiple callback functions
  - `sometimes()` takes a function reference `mul(speed("1.5"))`
  - Callbacks can chain methods on parameters
  - Function references can be passed (partial application)

### Array Notation in Strings (Pattern Feature)

- Mini-notation includes array-like syntax in strings
- Examples: `"c eb g c4"`, `"<C D G A Bb D C A G D Bb A>*[6,6.1]"`
- These are interpreted by the pattern engine, not the parser
- Parser sees them as strings

### Template Literals / Backticks

- Multi-line strings using backticks
- Example from Strudel:
  ```javascript
  sound(`bd*2, - cp,
  - - - oh, hh*4,
  [- casio]*2`)
  ```
- Parser must support:
  - Backtick string delimiters
  - Multi-line string content
  - (No string interpolation needed initially, can be added later)

### Object Literals

- JavaScript-style object notation
- Example from Strudel:
  ```javascript
  samples({
    'gtr': 'gtr/0001_cleanC.wav',
    'moog': { 'g3': 'moog/005_Mighty%20Moog%20G3.wav' }
  })
  ```
- Parser must support:
  - Curly brace object literals `{}`
  - Key-value pairs with colon `:`
  - String keys (quoted)
  - Nested objects
  - Trailing commas in objects

### Passing Function References

- Functions can be passed by name (not called)
- Example:
  ```javascript
  n("0 1 [4 3] 2 0 2 [~ 3] 4").sound("jazz").jux(rev)
  ```
- In this case:
  - `rev` is passed as a function reference (no parentheses)
  - `jux` will call `rev` internally
  - Distinct from `.jux(rev())` which would call rev first
- Parser and runtime must distinguish:
  - Function calls: `foo()`
  - Function references: `foo`

### Built-in Signal/Waveform Objects

- Pre-defined objects for signal generation
- Examples:
  ```javascript
  sound("hh*16").gain(sine)
  sound("hh*16").lpf(saw.range(500, 2000))
  ```
- Objects like `sine`, `saw` are registered as built-in values
- They have methods (like `.range()`)
- Can be used directly as arguments

### Method Chaining with Callback Transformations

- Methods can accept callbacks that transform the pattern
- Example:
  ```javascript
  n("0 [4 <3 2>] <2 3> [~ 1]"
    .off(1/16, x=>x.add(4))
  ).scale("<C5:minor Db5:mixolydian>/2")
  ```
- The `.off()` method takes a time value and a callback
- Callback receives the pattern and transforms it
- Result continues to be chainable

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
