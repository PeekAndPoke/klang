# KlangScript Tutorial — Implementation Plan

**Date:** 2026-03-15
**Source:** Six Hats Analysis (3 rounds)
**Target file:** `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt`

---

## Design Principles

1. **Lead with output, not theory.** Every section produces visible/audible results in the first example.
2. **Sessions, not chapters.** Each section feels like a studio session — you walk in, make sound, leave with something
   you'd play.
3. **Progressive Beat Machine.** One evolving project across all sections. Each section adds a capability.
4. **Three examples per section:** Learn (concept intro) → Apply (musical application) → Experiment (creative prompt).
5. **Zero unimplemented features.** Only use features with passing tests. No map/filter/reduce, no when, no for-in, no
   spread, no destructuring, no classes.
6. **Machine-verify everything.** Every example must pass `./gradlew :klangscript:jvmTest`.

## Pre-Flight: Smoke Test

Before writing any tutorial content, create a single KlangScript file exercising every language construct the tutorial
plans to use. Run it through the engine. If it fails, you know which sections to cut before investing writing effort.

**Smoke test file should cover:**

- `let` / `const` declarations
- All arithmetic operators: `+`, `-`, `*`, `/`, `%`, `**`
- All comparison operators: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical operators: `&&`, `||`, `!`
- Ternary: `cond ? a : b`
- Assignment operators: `+=`, `-=`, `*=`, `/=`
- Unary: `++`, `--` (prefix/postfix)
- `if/else` as expression (returning value)
- `for` loop (C-style)
- `while` loop
- `do-while` loop
- `break`, `continue`
- Arrow functions (expression body + block body)
- Closures capturing outer variables
- Currying: `x => y => x + y`
- Higher-order: passing functions as arguments
- Array literals, `.size()`, `.first()`, `.last()`, `[index]`, `.add()`, `.reversed()`, `.contains()`,
  `.joinToString()`, `.indexOf()`, `.isEmpty()`, `.isNotEmpty()`, `.drop()`, `.take()`, `.subList()`, `.removeAt()`,
  `.removeFirst()`, `.removeLast()`
- Object literals, dot access, bracket access, dynamic keys
- `Object.keys()`, `Object.values()`, `Object.entries()`
- Template strings with `${...}` interpolation
- String methods: `.length()`, `.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.split()`, `.replace()`, `.startsWith()`,
  `.endsWith()`, `.slice()`, `.substring()`, `.repeat()`, `.concat()`, `.charAt()`, `.indexOf()`
- `import * from "stdlib"`
- `Math.sqrt()`, `Math.abs()`, `Math.floor()`, `Math.ceil()`, `Math.sin()`, `Math.cos()`, `Math.min()`, `Math.max()`,
  `Math.pow()`, `Math.round()`
- `console.log()`, `print()`
- Comments: `//` and `/* */`
- Short-circuit evaluation
- Truthy/falsy values
- `"key" in obj`
- Method chaining

---

## Section-by-Section Specification

### Section 1: Hello KlangScript

**Goal:** Instant gratification. See output in under 10 seconds.

**Features introduced:** `import`, `console.log()`, `print()`, string literals, number literals, comments

**Description:** "Welcome to KlangScript. Type code, see results. Let's start."

**Examples:**

1. **Hello World** — `console.log("Hello, KlangScript!")` with `import * from "stdlib"`. Show that code produces output.
2. **Numbers and strings** — Print a few values: numbers, strings, expressions like `2 + 3`. Show the REPL evaluates
   expressions.
3. **Comments** — Show `//` and `/* */` comments. Demonstrate that commented-out code doesn't run.

---

### Section 2: Variables & Constants

**Goal:** Store and reuse values. Build the foundation for everything.

**Features introduced:** `let`, `const`, `null`, reassignment, types (number, string, boolean, null)

**Description:** "Use `let` for values that change and `const` for values that don't."

**Examples:**

1. **let and const** — Declare variables, print them, reassign `let`, show `const` is immutable.
2. **All value types** — Numbers (int, float, negative), strings (single/double/backtick quotes), booleans, null.
   Template string showing types: `` `x is ${x}` ``.
3. **Reassignment and mutation** — Show `let` being reassigned. Show `const` error on reassignment attempt (if the REPL
   shows errors well). Show `let` without initializer = `null`.

---

### Section 3: Operators & Expressions

**Goal:** Transform values. Do math. Make comparisons.

**Features introduced:** Arithmetic (`+`, `-`, `*`, `/`, `%`, `**`), comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`),
logical (`&&`, `||`, `!`), ternary (`? :`), assignment (`+=`, `-=`, `*=`, `/=`), unary (`++`, `--`), `in` operator

**Description:** "Operators transform values. KlangScript supports arithmetic, comparison, logical, and assignment
operators."

**Examples:**

1. **Arithmetic** — All arithmetic operators with musical context (e.g., calculating intervals, BPM math:
   `const beatLength = 60 / bpm`).
2. **Comparison and ternary** — Compare values, use ternary for conditional labels. Example:
   `const label = score >= 90 ? "A" : "B"`.
3. **Logical operators and short-circuit** — `&&`, `||`, `!`. Show short-circuit: `const name = userInput || "default"`.
   Show truthy/falsy: `0`, `""`, `null`, `false` are falsy.
4. **Assignment operators and increment** — `+=`, `-=`, `*=`, `/=`, `%=`. Prefix/postfix `++` and `--`.

---

### Section 4: Strings & Template Literals

**Goal:** Work with text. Build dynamic output. Master string manipulation.

**Features introduced:** Template strings (`${}`), all string methods

**Description:** "Strings hold text. Template literals let you embed expressions. KlangScript has Kotlin-style string
methods."

**Examples:**

1. **Template literals** — String interpolation with `${}`. Embed expressions, variables, function calls inside
   templates.
2. **String methods — queries** — `.length()`, `.startsWith()`, `.endsWith()`, `.indexOf()`, `.charAt()`. Build a note
   name parser example.
3. **String methods — transforms** — `.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.split()`, `.replace()`,
   `.slice()`, `.substring()`, `.repeat()`, `.concat()`. Chain multiple methods together.

---

### Section 5: Arrays

**Goal:** Work with ordered collections. Build sequences and lists.

**Features introduced:** Array literals, index access/assignment, all array methods

**Description:** "Arrays are ordered collections. Use them to build note sequences, pattern steps, and data lists."

**Examples:**

1. **Creating and accessing** — Array literal, `.size()`, `.first()`, `.last()`, `[index]`, `[index] = value`.
2. **Array methods — query** — `.contains()`, `.indexOf()`, `.isEmpty()`, `.isNotEmpty()`. Search through a list of
   notes.
3. **Array methods — transform** — `.reversed()`, `.drop()`, `.take()`, `.subList()`, `.joinToString()`. Build and
   reshape note sequences.
4. **Array methods — mutate** — `.add()`, `.removeAt()`, `.removeFirst()`, `.removeLast()`. Build a step sequencer by
   adding/removing beats.

---

### Section 6: Objects

**Goal:** Group related data. Name your properties. Structure your sound parameters.

**Features introduced:** Object literals, dot access, bracket access, dynamic keys, `Object.keys()`, `Object.values()`,
`Object.entries()`, `"key" in obj`

**Description:** "Objects group named values. Use them to describe instruments, presets, or song structures."

**Examples:**

1. **Object basics** — Create an object with properties. Access with dot and bracket notation. Dynamic key access.
2. **Object utility methods** — `Object.keys()`, `Object.values()`, `Object.entries()`. Iterate over an instrument
   config.
3. **Nested objects and `in` operator** — Objects containing objects. Check for key existence with `in`. Build a track
   config with sections.

---

### Section 7: Control Flow

**Goal:** Make decisions and repeat actions. The code gets smart.

**Features introduced:** `if/else` (as expression), `for`, `while`, `do-while`, `break`, `continue`

**Description:** "Control flow lets your code make decisions and repeat actions. In KlangScript, `if/else` is an
expression — it returns a value."

**Examples:**

1. **if/else as expression** — Use if/else to assign values. Chain `else if`. Show that the last expression in a block
   is the return value.
2. **for and while loops** — C-style for loop, while loop. Build a pattern by accumulating values in an array inside a
   loop.
3. **do-while** — Execute at least once. Use case: prompt/retry pattern.
4. **break and continue** — Skip iterations, exit early. Filter odd numbers from a range. Find first match in a list.

---

### Section 8: Functions & Closures

**Goal:** Build reusable tools. The power-up moment.

**Features introduced:** Arrow functions (expression body, block body), closures, currying, higher-order functions,
`return`

**Description:** "Functions are reusable blocks of code. Arrow functions are the only function syntax in KlangScript."

**Examples:**

1. **Arrow functions — expression vs block** — `(a, b) => a + b` vs `(a, b) => { return a + b }`. Single parameter
   shorthand: `x => x * 2`.
2. **Closures** — Function captures outer variable. Classic `makeCounter` example. Explain lexical scoping.
3. **Higher-order functions and currying** — Pass functions as arguments. Return functions from functions. Currying:
   `const add = a => b => a + b`. Build a "sound transformer factory."

---

### Section 9: Imports & the Standard Library

**Goal:** Use the stdlib. Access Math functions. Organize code with modules.

**Features introduced:** `import * from`, `import { x } from`, `import { x as y } from`, `import * as ns from`,
`export`, Math object methods

**Description:** "KlangScript has a module system. The standard library provides Math, console, and more."

**Examples:**

1. **Wildcard import and Math** — `import * from "stdlib"`. Use `Math.sqrt()`, `Math.abs()`, `Math.floor()`,
   `Math.ceil()`, `Math.round()`, `Math.pow()`.
2. **Trigonometry** — `Math.sin()`, `Math.cos()`, `Math.tan()`. Calculate wave values. `Math.min()`, `Math.max()` for
   clamping.
3. **Selective and aliased imports** — `import { sqrt, pow } from "stdlib"`. `import { abs as absolute } from "stdlib"`.
   Show export syntax.

---

### Section 10: Method Chaining & Putting It All Together

**Goal:** Chain operations fluently. Combine everything learned into a complete program.

**Features introduced:** Method chaining (combining previously learned methods), no new syntax — synthesis of all prior
sections.

**Description:** "Chain method calls for fluent, expressive code. Then combine everything you've learned."

**Examples:**

1. **String chaining** — Chain `.trim().toLowerCase().replace().split()`. Process a melody string.
2. **Array + string chaining** — Build a note sequence, transform it, join it. Show how multiple operations compose.
3. **Beat Machine capstone** — A complete mini-program combining: variables, arrays, objects, loops, functions,
   closures, template strings, Math, imports. The "graduation" example. Should be 20-30 lines producing interesting
   output.

---

## Feature Coverage Checklist

Every feature must appear in at least one example. The coding agent should verify coverage after writing all sections.

| Feature                                      | Section(s)         |
|----------------------------------------------|--------------------|
| `let` / `const`                              | 2, used throughout |
| `null`                                       | 2                  |
| Number / String / Boolean literals           | 1, 2               |
| `//` and `/* */` comments                    | 1                  |
| Arithmetic: `+`, `-`, `*`, `/`, `%`, `**`    | 3                  |
| Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=` | 3                  |
| Logical: `&&`, `\|\|`, `!`                   | 3                  |
| Ternary: `? :`                               | 3                  |
| Assignment: `+=`, `-=`, `*=`, `/=`, `%=`     | 3                  |
| Unary: `++`, `--` (prefix/postfix)           | 3                  |
| Short-circuit evaluation                     | 3                  |
| Truthy/falsy values                          | 3                  |
| `"key" in obj`                               | 6                  |
| Template strings `${}`                       | 4                  |
| String methods (all 15)                      | 4                  |
| Array literals + index access                | 5                  |
| Array methods (all 14)                       | 5                  |
| Object literals, dot/bracket access          | 6                  |
| `Object.keys/values/entries`                 | 6                  |
| `if/else` as expression                      | 7                  |
| `for` loop (C-style)                         | 7                  |
| `while` loop                                 | 7                  |
| `do-while` loop                              | 7                  |
| `break`, `continue`                          | 7                  |
| Arrow function (expression body)             | 8                  |
| Arrow function (block body + return)         | 8                  |
| Closures                                     | 8                  |
| Currying                                     | 8                  |
| Higher-order functions                       | 8                  |
| `import * from "stdlib"`                     | 1, used throughout |
| Selective import `{ x }`                     | 9                  |
| Aliased import `{ x as y }`                  | 9                  |
| Namespace import `* as ns`                   | 9                  |
| `export`                                     | 9                  |
| Math methods (all 12)                        | 9                  |
| `console.log()`                              | 1, used throughout |
| `print()`                                    | 1                  |
| Method chaining                              | 10                 |

## NOT Covered (Unimplemented)

These features do NOT exist yet. Do NOT use them in any example:

- `map()`, `filter()`, `forEach()`, `reduce()`, `find()`, `some()`, `every()`, `sort(comparator)`
- `when` expression
- `for...in` / `for...of` loops
- Spread operator (`...`)
- Destructuring (`let [a, b] = ...` or `let {x, y} = ...`)
- Regular expressions
- Classes / prototypes
- Async / promises
- JSON parse/stringify
- Set / Map collections

---

## Implementation Steps for Coding Agent

### Step 0: Pre-flight smoke test

Create a KlangScript test file that exercises every feature from the checklist above. Run
`./gradlew :klangscript:jvmTest`. If any feature fails, flag it and exclude from tutorial.

### Step 1: Read existing content

Read `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt` to understand current structure.

### Step 2: Rewrite `klangScriptDocSections`

Replace the existing 8 sections with the 10 sections specified above. Each section is a
`DocSection(title, description, examples)` with `DocExample(title, code)` entries. All code strings use `.trimMargin()`
with `|` prefix.

### Step 3: Every example must include `import * from "stdlib"` as the first line

This ensures Math, console.log, and string/array methods are available.

### Step 4: Every example must produce visible output

Use `console.log()` to print results. The REPL shows console output. No silent examples.

### Step 5: Keep examples concise

Each example: 5-15 lines of code. No example longer than 20 lines (except the capstone).

### Step 6: Test

Run `./gradlew :klangscript:jvmTest` to verify all examples compile and execute.
If a `KlangScriptDocContentTest` exists, it should automatically validate all doc examples.

### Step 7: Verify feature coverage

Cross-check the feature coverage checklist. Every feature should appear in at least one example.

---

## Section Descriptions (copy-paste ready for DocSection.description)

1. "Welcome to KlangScript! Type code, see results. Every example is interactive — edit and run it yourself."
2. "Use 'let' for values that change and 'const' for values that don't. KlangScript supports numbers, strings, booleans,
   and null."
3. "Operators transform values. Arithmetic, comparison, logical, ternary, and assignment operators — plus
   increment/decrement and short-circuit evaluation."
4. "Template literals with \${...} let you embed expressions in strings. KlangScript provides rich string methods for
   searching, transforming, and splitting text."
5. "Arrays are ordered collections with Kotlin-style methods. Build note sequences, filter data, and reshape lists."
6. "Objects group named values with dot and bracket access. Use Object.keys(), Object.values(), and Object.entries() to
   inspect them."
7. "Control flow lets your code make decisions and repeat actions. In KlangScript, if/else is an expression that returns
   a value."
8. "Arrow functions are the only function syntax in KlangScript. They support closures, currying, and higher-order
   patterns."
9. "The standard library provides Math, console, and more. Use import/export to organize code into modules."
10. "Chain methods for fluent, expressive code. Combine everything you've learned into a complete program."
