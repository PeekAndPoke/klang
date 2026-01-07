# KlangScript TODO List

> **For detailed implementation history, see [HISTORY.MD](HISTORY.MD)**

## Project Overview

JavaScript-like scripting language for live coding

- **Parser**: better-parse (combinator library)
- **Runtime**: Tree-walking interpreter
- **Platforms**: Kotlin Multiplatform (JVM + JS)
- **Current Status**: 359+ tests passing on both platforms

---

## Design Decisions

### Core Principles

1. **Arrow Functions**: Expression bodies only (`x => x + 1`). Block bodies deferred to Phase 6
2. **Semicolons**: Optional, newlines as statement separators
3. **No `undefined`**: Only `null` for absent values
4. **Immutable After Build**: Builder pattern for configuration, immutable execution
5. **JavaScript Compatibility**: ES6 imports/exports, `Math` object, familiar syntax

### Implementation Choices

- **Objects**: Wrap in parentheses in arrow functions: `x => ({ a: 1 })`
- **Chainable Functions**: Native objects with extension methods
- **Export Control**: Explicit exports prevent scope pollution
- **Thread-Safe**: No mutations after construction

---

## Implementation Roadmap

### Phase 1: Foundation & Basic Parsing ✅ COMPLETE

- [x] **1.1**: AST Data Structures
- [x] **1.2**: Basic Tokens (keywords, literals, operators)
- [x] **1.3**: Comments & Strings (single-line, multi-line, backticks)
- [x] **1.4**: Literals & Identifiers
- [x] **1.5**: Arithmetic Expressions (operators, precedence, unary)
- [x] **1.6**: Function Calls
- [x] **1.7**: Method Chaining (dot notation)
- [x] **1.8**: Arrow Functions (expression bodies only)
- [x] **1.9**: Variables (`let`, `const`)
- [x] **1.10**: Object Literals
- [x] **1.11**: Array Literals (`[1, 2, 3]`) - *No trailing commas yet*
- [x] **1.12**: Complete Programs (top-level statements)
- [ ] **1.13**: Parser Error Handling - *Deferred*

---

### Phase 2: Tree-Walking Interpreter ✅ COMPLETE

- [x] **2.1**: Runtime Value System (`NumberValue`, `StringValue`, `BooleanValue`, `NullValue`, `FunctionValue`,
  `ObjectValue`, `ArrayValue`)
- [x] **2.2**: Environment & Scope Management (lexical scoping)
- [x] **2.3**: Expression Evaluation (literals, identifiers, binary/unary ops)
- [x] **2.4**: Native Function Calls (registration API, type conversion)
- [x] **2.5**: Script Functions (closures, first-class functions)
- [x] **2.6**: Method Chaining & Member Access
- [x] **2.7**: Object Evaluation (property access, nested objects)
- [x] **2.8**: Named Objects & Built-ins (superseded by Step 3.7)
- [ ] **2.9**: Function References - *Partial* (works, not fully tested)
- [x] **2.10**: Variable Declarations (`let`, `const`)
- [x] **2.11**: Integration Tests (end-to-end scenarios)
- [x] **2.12**: Error Handling & Debugging
  - [x] Error type system (`TypeError`, `ReferenceError`, `ArgumentError`, `ImportError`, `AssignmentError`)
  - [x] Source location tracking (file:line:column)
  - [x] Stack traces (JavaScript-style)
  - [x] Stack overflow protection (1000-frame limit)
- [x] **2.13**: Array, String, Object Methods ✅ **COMPLETE**
  - [x] **Array Methods**: `length()`, `push()`, `pop()`, `shift()`, `unshift()`, `slice()`, `concat()`, `join()`,
    `reverse()`, `indexOf()`, `includes()`
  - [x] **String Methods**: `length()`, `charAt()`, `substring()`, `indexOf()`, `split()`, `toUpperCase()`,
    `toLowerCase()`, `trim()`, `startsWith()`, `endsWith()`, `replace()`, `slice()`, `concat()`, `repeat()`
  - [x] **Object Utilities**: `Object.keys()`, `Object.values()`, `Object.entries()`
  - [ ] Indexing: `arr[0]`, `arr[1]` (requires index access expression) - *Deferred*
  - [ ] Higher-order: `map()`, `filter()`, `forEach()`, `find()`, `some()`, `every()` - *Deferred (requires callback
    execution support)*

---

### Phase 3: API & Integration ✅ COMPLETE

- [x] **3.1**: Public API Design (`KlangScript` facade)
- [x] **3.2**: Function Registration System (builder/DSL)
- [x] **3.3**: Chainable Functions (superseded by 3.10)
- [x] **3.4**: Named Objects (superseded by 3.10)
- [x] **3.5**: Standard Library (`KlangStdLib` - Math, console, string functions)
- [ ] **3.6**: Live Coding Features (hot reload, state preservation) - *Deferred*
- [x] **3.7**: Import/Export System (ES6-style, wildcard/selective imports)
- [x] **3.8**: Import/Export Enhancements
  - [x] **3.8.1**: Import aliasing (`import { add as sum }`)
  - [x] **3.8.2**: Namespace imports (`import * as math`)
  - [x] **3.8.3**: Export aliasing (`export { add as sum }`)
  - [ ] **3.8.4**: Default exports - *Lower priority*
- [ ] **3.9**: REPL Implementation - *Deferred*
- [x] **3.10**: Native Kotlin Interop (`NativeObjectValue`, extension methods, type-safe registration)
- [x] **3.11**: Library System Enhancement (`KlangScriptLibrary` with builder)
- [x] **3.12**: Immutability & Builder Pattern
  - [x] Unified `NativeRegistryBuilder` interface
  - [x] `registerObject()` method for singleton objects
  - [x] Immutable after build (thread-safe)
  - [x] DSL functions: `klangScript {}`, `klangScriptLibrary {}`

---

### Phase 4: Documentation & Examples ⏳ TODO

- [ ] **4.1**: API Documentation
  - [ ] KDoc comments on all public APIs
  - [ ] Usage examples in documentation
  - [ ] API reference guide
- [ ] **4.2**: Example Scripts
  - [ ] Feature showcase scripts
  - [ ] Tutorial scripts (step-by-step)
  - [ ] Port simplified Strudel examples
- [ ] **4.3**: Language Specification
  - [ ] Formal grammar specification
  - [ ] Semantics and behavior documentation
  - [ ] Differences from JavaScript guide

---

### Phase 5: Testing & Quality ⏳ TODO

- [ ] **5.1**: Comprehensive Test Suite
  - [ ] Achieve >90% code coverage
  - [ ] Edge case tests
  - [ ] Property-based tests (if applicable)
  - [ ] Performance regression tests
- [ ] **5.2**: Error Message Quality
  - [ ] Review all error messages
  - [ ] Provide helpful suggestions
  - [ ] Examples of good vs bad input
  - [ ] Error recovery strategies
- [ ] **5.3**: Performance Optimization
  - [ ] Profile hot paths
  - [ ] Optimize AST traversal
  - [ ] Caching strategies
  - [ ] Benchmark suite

---

### Phase 6: Future Enhancements ⏳ OPTIONAL

#### Advanced Language Features
- [ ] Arrow function block bodies: `x => { return x; }`
- [ ] Return statements
- [ ] Block statements
- [ ] Spread operator: `...args`
- [ ] Destructuring: `let {a, b} = obj`
- [ ] Template string interpolation: `` `hello ${name}` ``
- [ ] Ternary operator: `x ? y : z`
- [ ] Logical operators: `&&`, `||`
- [ ] Comparison operators: `==`, `===`, `!=`, `!==`, `<`, `>`, `<=`, `>=`
- [ ] Control flow: `if/else`, `while`, `for`
- [ ] Trailing commas in all contexts

#### Runtime Enhancements

- [ ] Array methods: `map()`, `filter()`, `reduce()`, `find()`, etc.
- [ ] String methods: `substring()`, `indexOf()`, `split()`, etc.
- [ ] Object methods: `Object.keys()`, `Object.values()`, etc.
- [ ] Better type coercion
- [ ] Proper `this` binding
- [ ] Prototype chain

#### Performance
- [ ] Bytecode VM implementation
- [ ] JIT compilation exploration
- [ ] Memory optimization

---

## Current Priority: Phase 4 - Documentation & Examples 🎯

**Next Steps**:

1. **API Documentation**: Add KDoc comments to all public APIs
2. **Example Scripts**: Create feature showcase and tutorial scripts
3. **Language Specification**: Document grammar, semantics, and JavaScript differences

**Note**: Array/String/Object methods are now complete. Higher-order array methods (map, filter, etc.) are deferred
pending callback execution infrastructure improvements.

---

## Quick Reference

### Library Creation Example

```kotlin
val lib = klangScriptLibrary("mylib") {
  source(
    """
        let add = (a, b) => a + b
        export { add }
    """
  )

  // Register native function
  registerFunction("multiply") { a: Int, b: Int -> a * b }

  // Register native type with methods
  registerType<MyClass> {
    registerMethod("process") { input: String -> this.process(input) }
  }

  // Register singleton object
  registerObject("Math", MathHelper) {
    registerMethod("sqrt") { x: Double -> sqrt(x) }
    registerVarargMethod("max") { numbers: List<Double> -> numbers.maxOrNull() ?: 0.0 }
  }
}
```

### Engine Creation Example

```kotlin
val engine = klangScript {
  registerLibrary(lib)
  registerFunction("print") { args: List<Any?> ->
    println(args.joinToString(" "))
  }
}

engine.execute(
  """
    import * as mylib from "mylib"
    print(mylib.add(1, 2))
"""
)
```

### Registration Method Variants

All methods support 0-5 parameters + vararg:

- **Functions**: `registerFunction()`, `registerVarargFunction()`
- **Extension Methods**: `registerMethod()`, `registerVarargMethod()`
- **Types**: `registerType<T> { }`
- **Objects**: `registerObject(name, instance) { }`

---

## Known Limitations

1. **Trailing commas**: Not supported in arrays, objects, or function calls
2. **Array indexing**: Not yet implemented (requires `IndexAccess` AST node)
3. **Block bodies**: Arrow functions are expression-only
4. **Default exports**: Not implemented
5. **Parser errors**: Limited error recovery
6. **Native function errors**: Don't include source location (architectural constraint)

---

## Test Status

- ✅ **393 tests passing** on both JVM and JS
- ✅ Comprehensive coverage of all implemented features
- ✅ Integration tests validate end-to-end scenarios
- ✅ Error handling and stack trace tests
- ✅ Array, String, and Object method tests
- ✅ Multiplatform compatibility verified

---

For implementation details, architectural decisions, and timeline, see **[HISTORY.MD](HISTORY.MD)**
