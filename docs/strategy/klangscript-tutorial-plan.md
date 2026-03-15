# KlangScript Tutorial — Implementation Plan

**Date:** 2026-03-15 (v2)
**Source:** Six Hats Analysis (3 rounds) + feature update + tone/structure refinement
**Target file:** `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt`

---

## Page Structure

The KlangScript docs page has **two tabs** on a single route:

1. **"Learn KlangScript"** (default) — The progressive tutorial. Sticky left sidebar with section links + scroll-spy.
2. **"Coming from JavaScript"** — The reference/comparison tables from `klangscript-vs-javascript.md`.

Only the active tab renders (avoids DOM weight of 40+ REPLs + reference tables simultaneously).

Within the Learn tab, each tutorial section that has a JS-specific gotcha gets a small inline **"JS note:"** callout —
one sentence max. Example: *"JS note: KlangScript uses `.size()` instead of `.length` for arrays."*

---

## Design Principles

1. **Lead with output, not theory.** Every section produces visible results in the first example.
2. **Sessions, not chapters.** Each section feels like a studio session.
3. **Three examples per section:** Learn (concept intro) → Apply (practical application) → Experiment (creative prompt).
4. **Zero unimplemented features.** Only features with passing tests.
5. **Machine-verify everything.** Every example must pass `./gradlew :klangscript:jvmTest`.
6. **Last expression is the result.** Prefer showing the result of the last expression over `console.log()` where
   appropriate. Use `console.log()` only when you need to show multiple intermediate values or labeled output. A bare
   `1 + 2` at the end of an example should display `3` in the REPL output.

---

## Tone of Voice & Example Description Style

### Tone

- **Concise, direct, collegial.** Talk to the reader like a knowledgeable friend, not a textbook.
- **No filler.** No "In this section we will learn about..." — just show it.
- **Active voice.** "Use `let` to declare..." not "Variables are declared using..."
- **Musical context where natural.** Reference notes, beats, tempo, patterns — but don't force it. A good math example
  is fine without a music metaphor.

### Per-Example Description Structure

Each `DocExample` now has:

- **`title`**: Short name (2-5 words). What the example demonstrates.
- **`description`** (NEW field — see data model change below): 1-2 sentences explaining what's happening and why.
  Written ABOVE the code. Should answer: "What will I see when I run this?"

### Description examples (good vs bad):

**Good:**
> "The last expression in your code becomes the result. No `console.log` needed — just write an expression."

> "Use `??` to provide a fallback only when a value is `null`. Unlike `||`, it won't trigger on `0` or empty strings."

> "Bitwise AND masks specific bits. Here we extract the lower 4 bits of a byte value."

**Bad:**
> "In this example we will demonstrate the usage of the nullish coalescing operator."

> "This shows how variables work."

> "Below you can see an example of arrays."

---

## Data Model Change

Add a `description` field to `DocExample`:

```kotlin
data class DocExample(
   val title: String? = null,
   val description: String? = null,  // NEW — 1-2 sentences shown above the code
   val code: String,
)
```

The REPL component should render `description` as a small text block above the code editor, if present.

---

## Pre-Flight: Smoke Test

Before writing any tutorial content, create a single KlangScript file exercising every language construct the tutorial
plans to use. Run it through the engine. If it fails, flag it and exclude from tutorial.

**Smoke test file should cover (updated with new features):**

- `let` / `const` declarations
- All arithmetic operators: `+`, `-`, `*`, `/`, `%`, `**`
- All comparison operators: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical operators: `&&`, `||`, `!`
- Ternary: `cond ? a : b`
- Nullish coalescing: `??`
- Optional chaining: `?.`
- Bitwise operators: `&`, `|`, `^`, `~`
- Shift operators: `<<`, `>>`, `>>>`
- Assignment operators: `+=`, `-=`, `*=`, `/=`, `%=`, `**=`, `&=`, `|=`, `^=`, `<<=`, `>>=`
- Unary: `++`, `--` (prefix/postfix)
- Number literals: decimal, `0xFF` (hex), `0o77` (octal), `0b1010` (binary)
- `if/else` as expression (returning value)
- `for` loop (C-style), `while` loop, `do-while` loop
- `break`, `continue`
- Arrow functions (expression body + block body)
- Closures, currying, higher-order functions
- Array literals + all methods
- Object literals + dot/bracket access + Object.keys/values/entries
- Template strings with `${...}` interpolation
- All string methods
- `import * from "stdlib"`, selective, aliased, namespace
- Math methods
- `console.log()`, `print()`
- Comments: `//` and `/* */`
- Short-circuit evaluation, truthy/falsy, `"key" in obj`
- Method chaining

---

## Section-by-Section Specification

### Section 1: Hello KlangScript

**Goal:** Instant gratification. See output in under 10 seconds.

**Features introduced:** `console.log()`, `print()`, string/number literals, comments, last-expression result

**Examples:**

1. **Your first expression**
   *"The simplest program is just an expression. The REPL shows its result automatically."*
   Code: `42` → shows 42. Then `"Hello, KlangScript!"` → shows the string. Then `2 + 3` → shows 5.

2. **console.log for multiple values**
   *"Use `console.log()` when you want to print multiple values or label your output."*
   Code: `import * from "stdlib"` then `console.log("result:", 6 * 7)`.

3. **Comments**
   *"Comments are ignored by the interpreter. Use them to explain your code or temporarily disable lines."*
   Code: Show `//` and `/* */`. Comment out a line, show it doesn't run.

---

### Section 2: Variables & Constants

**Goal:** Store and reuse values.

**Features introduced:** `let`, `const`, `null`, reassignment, types (number, string, boolean, null), number literal
formats

**Examples:**

1. **let and const**
   *"Use `let` for values that change, `const` for values that stay fixed."*
   Code: Declare, print, reassign `let`, show `const` prevents reassignment.

2. **All value types**
   *"KlangScript has numbers, strings, booleans, and null. No `undefined` — uninitialized variables are `null`."*
   Code: All types including hex `0xFF`, octal `0o77`, binary `0b1010`. Template string to display them.
   JS note: *"Unlike JavaScript, there is no `undefined`. Uninitialized variables are `null`."*

3. **Number formats**
   *"Numbers can be written in decimal, hexadecimal (0x), octal (0o), or binary (0b)."*
   Code: `0xFF`, `0o77`, `0b1010`, show their decimal equivalents.

---

### Section 3: Operators & Expressions

**Goal:** Transform values. Do math. Make comparisons. Work with bits.

**Features introduced:** Arithmetic, comparison, logical, ternary, assignment (`+=` etc), unary (`++`/`--`), `in`

**Examples:**

1. **Arithmetic**
   *"Standard math operators, plus `**` for exponentiation and `%` for remainder."*
   Code: All arithmetic ops. Musical context where natural (e.g., `60 / bpm` for beat length).

2. **Comparison and ternary**
   *"Compare values and choose between options. The ternary `? :` is a compact if/else."*
   Code: Comparisons, ternary. `const label = score >= 90 ? "A" : "B"`.

3. **Logical operators and short-circuit**
   *"`&&` and `||` short-circuit: they stop evaluating as soon as the result is known. Use `||` for defaults."*
   Code: `&&`, `||`, `!`, short-circuit default pattern, truthy/falsy values.
   JS note: *"Falsy values are `0`, `""`, `null`, and `false`. No `undefined` or `NaN`."*

4. **Assignment operators and increment**
   *"Compound assignment combines an operator with `=`. Prefix/postfix `++` and `--` increment or decrement."*
   Code: `+=`, `-=`, `*=`, `/=`, `%=`, `**=`. Prefix vs postfix `++`/`--`.

---

### Section 4: Nullish Coalescing & Optional Chaining

**Goal:** Handle null values safely and concisely.

**Features introduced:** `??`, `?.`

**Examples:**

1. **Nullish coalescing (??)**
   *"`??` returns the right side only when the left is `null`. Unlike `||`, it doesn't trigger on `0` or empty
   strings."*
   Code: `null ?? "default"` → `"default"`. `0 ?? "default"` → `0`. Compare with `||`.
   JS note: *"Same as JavaScript's `??` — returns fallback only for `null`, not all falsy values."*

2. **Optional chaining (?.)**
   *"`?.` safely accesses properties on values that might be `null`. Returns `null` instead of throwing."*
   Code: `const name = user?.name`, where `user` is null. Chain: `config?.audio?.sampleRate`.

3. **Combining ?? and ?.**
   *"Chain `?.` for safe access, then `??` for a fallback. A clean pattern for nested optional data."*
   Code: `config?.audio?.sampleRate ?? 44100`.

---

### Section 5: Bitwise & Shift Operators

**Goal:** Work with individual bits. Useful for flags, masks, and low-level audio manipulation.

**Features introduced:** `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`, compound assignments `&=`, `|=`, `^=`, `<<=`, `>>=`

**Examples:**

1. **Bitwise basics**
   *"Bitwise operators work on individual bits of integer values. `&` masks bits, `|` combines them, `^` toggles them."*
   Code: `0b1100 & 0b1010` → `0b1000` (8). `0b1100 | 0b1010` → `0b1110` (14). `~0b1010`. Show decimal results.

2. **Shift operators**
   *"Shift bits left or right. Left shift doubles the value per shift; right shift halves it."*
   Code: `1 << 4` → 16. `128 >> 3` → 16. `>>>` for unsigned right shift.

3. **Practical: flags and masks**
   *"Combine bitwise operators for flag management — common in audio settings and MIDI processing."*
   Code: Set/check/toggle flags using `|=`, `&`, `^=`. Use `&=`, `|=`, `^=`, `<<=`, `>>=` compound assignments.

---

### Section 6: Strings & Template Literals

**Goal:** Work with text. Build dynamic output.

**Features introduced:** Template strings (`${}`), all string methods

**Examples:**

1. **Template literals**
   *"Backtick strings embed expressions with `${...}`. Cleaner than string concatenation."*
   Code: Interpolation with variables and expressions. Show this is the alternative to `"str" + num` (which throws).
   JS note: *"KlangScript does NOT allow `\"text\" + number`. Use template literals instead: `` `text ${number}` ``."*

2. **String methods — queries**
   *"Check string properties: length, position, prefix, suffix."*
   Code: `.length()`, `.startsWith()`, `.endsWith()`, `.indexOf()`, `.charAt()`.
   JS note: *"`.length()` is a method call with parens, not a property like in JavaScript."*

3. **String methods — transforms**
   *"Transform strings: case, trim, split, replace, repeat. These return new strings — the original is unchanged."*
   Code: `.toUpperCase()`, `.toLowerCase()`, `.trim()`, `.split()`, `.replace()`, `.slice()`, `.substring()`,
   `.repeat()`, `.concat()`. Chain methods together.

---

### Section 7: Arrays

**Goal:** Work with ordered collections.

**Features introduced:** Array literals, index access/assignment, all array methods

**Examples:**

1. **Creating and accessing**
   *"Arrays hold ordered values. Access by index, or use `.first()` and `.last()` for the endpoints."*
   Code: Array literal, `.size()`, `.first()`, `.last()`, `[index]`, `[index] = value`.
   JS note: *"Use `.size()` instead of `.length`, `.first()`/`.last()` instead of index math."*

2. **Array methods — query and transform**
   *"Search, check, and reshape arrays without mutating the original."*
   Code: `.contains()`, `.indexOf()`, `.isEmpty()`, `.isNotEmpty()`, `.reversed()`, `.drop()`, `.take()`, `.subList()`,
   `.joinToString()`.
   JS note: *"`.contains()` = JS `.includes()`. `.joinToString()` = JS `.join()`. `.reversed()` returns a new array (
   JS `.reverse()` mutates!)."*

3. **Array methods — mutate**
   *"Add and remove elements. These methods modify the array in place."*
   Code: `.add()`, `.removeAt()`, `.removeFirst()`, `.removeLast()`.
   JS note: *"`.add()` = JS `.push()`. `.removeLast()` = JS `.pop()`. `.removeFirst()` = JS `.shift()`."*

---

### Section 8: Objects

**Goal:** Group related data. Name your properties.

**Features introduced:** Object literals, dot access, bracket access, dynamic keys, `Object.keys()`, `Object.values()`,
`Object.entries()`, `"key" in obj`

**Examples:**

1. **Object basics**
   *"Objects group named values. Access properties with dot notation or brackets."*
   Code: Create object, dot access, bracket access with dynamic key.

2. **Object utility methods**
   *"`Object.keys()`, `.values()`, and `.entries()` let you inspect an object's contents."*
   Code: Iterate over a config object.

3. **Nested objects and `in` operator**
   *"Objects can contain other objects. Use `in` to check if a key exists."*
   Code: Nested structure, `"key" in obj` check.

---

### Section 9: Control Flow

**Goal:** Make decisions and repeat actions.

**Features introduced:** `if/else` (as expression), `for`, `while`, `do-while`, `break`, `continue`

**Examples:**

1. **if/else as expression**
   *"In KlangScript, `if/else` returns a value — assign it directly to a variable."*
   Code: `let label = if (x > 5) { "big" } else { "small" }`. Chain `else if`.
   JS note: *"This is different from JavaScript where `if/else` is a statement. In KlangScript, it's an expression."*

2. **for and while loops**
   *"Repeat actions with `for` (when you know how many times) or `while` (when you have a condition)."*
   Code: C-style for loop building an array. While loop doubling a value.

3. **do-while**
   *"`do-while` runs the body at least once before checking the condition."*
   Code: `do-while` example.

4. **break and continue**
   *"`break` exits a loop early. `continue` skips to the next iteration."*
   Code: Skip even numbers with `continue`, exit at threshold with `break`.

---

### Section 10: Functions & Closures

**Goal:** Build reusable tools. The power-up moment.

**Features introduced:** Arrow functions (expression body, block body), closures, currying, higher-order functions,
`return`

**Examples:**

1. **Arrow functions — expression vs block**
   *"Expression body returns implicitly. Block body needs an explicit `return`."*
   Code: `(a, b) => a + b` vs `(a, b) => { return a + b }`. Single param: `x => x * 2`.
   JS note: *"KlangScript only has arrow functions. No `function` keyword, no `function name() {}`."*

2. **Closures**
   *"A function can capture variables from its surrounding scope. This is called a closure."*
   Code: `makeCounter` example. Explain lexical scoping.

3. **Higher-order functions and currying**
   *"Functions can take functions as arguments and return functions. Currying builds reusable partial applications."*
   Code: Pass function as arg. `const add = a => b => a + b`. `add(3)(4)`.

---

### Section 11: Imports & the Standard Library

**Goal:** Use the stdlib. Access Math functions. Organize code with modules.

**Features introduced:** `import * from`, `import { x } from`, `import { x as y } from`, `import * as ns from`,
`export`, Math object methods

**Examples:**

1. **Wildcard import and Math**
   *"`import * from \"stdlib\"` loads everything. The Math object provides common mathematical functions."*
   Code: `Math.sqrt()`, `Math.abs()`, `Math.floor()`, `Math.ceil()`, `Math.round()`, `Math.pow()`.

2. **Trigonometry and clamping**
   *"Sine, cosine, and tangent for wave calculations. `min`/`max` for clamping values to a range."*
   Code: `Math.sin()`, `Math.cos()`, `Math.tan()`, `Math.min()`, `Math.max()`.

3. **Selective and aliased imports**
   *"Import only what you need, or rename imports to avoid conflicts."*
   Code: `import { sqrt, pow } from "stdlib"`. Aliased: `import { abs as absolute } from "stdlib"`. Namespace:
   `import * as M from "stdlib"`.

---

### Section 12: Method Chaining & Putting It All Together

**Goal:** Chain operations fluently. Combine everything learned.

**Features introduced:** Method chaining, no new syntax — synthesis of all prior sections.

**Examples:**

1. **String chaining**
   *"Chain method calls left-to-right for clean, readable transformations."*
   Code: `" Hello World ".trim().toLowerCase().replace("world", "klang").split(" ")`.

2. **Array + string chaining**
   *"Build data pipelines by chaining array and string operations together."*
   Code: Build a note sequence, transform it, join it.

3. **Capstone: putting it all together**
   *"A complete program combining variables, arrays, objects, loops, functions, closures, template strings, Math, and
   imports."*
   Code: 20-30 line program producing interesting output. Use multiple features from throughout the tutorial.

---

## Feature Coverage Checklist (Updated)

| Feature                                             | Section(s)         |
|-----------------------------------------------------|--------------------|
| `let` / `const`                                     | 2, used throughout |
| `null`                                              | 2, 4               |
| Number literals (decimal)                           | 1, 2               |
| Number literals (hex `0x`, octal `0o`, binary `0b`) | 2                  |
| String / Boolean literals                           | 1, 2               |
| `//` and `/* */` comments                           | 1                  |
| Arithmetic: `+`, `-`, `*`, `/`, `%`, `**`           | 3                  |
| Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`        | 3                  |
| Logical: `&&`, `\|\|`, `!`                          | 3                  |
| Ternary: `? :`                                      | 3                  |
| Nullish coalescing: `??`                            | 4                  |
| Optional chaining: `?.`                             | 4                  |
| Bitwise: `&`, `\|`, `^`, `~`                        | 5                  |
| Shift: `<<`, `>>`, `>>>`                            | 5                  |
| Assignment: `+=`, `-=`, `*=`, `/=`, `%=`, `**=`     | 3                  |
| Bitwise assignment: `&=`, `\|=`, `^=`, `<<=`, `>>=` | 5                  |
| Unary: `++`, `--` (prefix/postfix)                  | 3                  |
| Short-circuit evaluation                            | 3                  |
| Truthy/falsy values                                 | 3                  |
| `"key" in obj`                                      | 8                  |
| Template strings `${}`                              | 6                  |
| String methods (all 15)                             | 6                  |
| Array literals + index access                       | 7                  |
| Array methods (all 14)                              | 7                  |
| Object literals, dot/bracket access                 | 8                  |
| `Object.keys/values/entries`                        | 8                  |
| `if/else` as expression                             | 9                  |
| `for` loop (C-style)                                | 9                  |
| `while` loop                                        | 9                  |
| `do-while` loop                                     | 9                  |
| `break`, `continue`                                 | 9                  |
| Arrow function (expression body)                    | 10                 |
| Arrow function (block body + return)                | 10                 |
| Closures                                            | 10                 |
| Currying                                            | 10                 |
| Higher-order functions                              | 10                 |
| `import * from "stdlib"`                            | used throughout    |
| Selective import `{ x }`                            | 11                 |
| Aliased import `{ x as y }`                         | 11                 |
| Namespace import `* as ns`                          | 11                 |
| `export`                                            | 11                 |
| Math methods (all 12)                               | 11                 |
| `console.log()`                                     | 1, used as needed  |
| `print()`                                           | 1                  |
| Method chaining                                     | 12                 |

## NOT Covered (Unimplemented)

Do NOT use these in any example:

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

### Step 0: Add `description` field to DocExample

Add `val description: String? = null` to `DocExample` in `KlangScriptDocContent.kt`. Update the REPL component in
`KlangScriptDocsPage.kt` to render it as a small text block above the code editor when present.

### Step 1: Pre-flight smoke test

Create a KlangScript test file that exercises every feature from the checklist (including new: `??`, `?.`, bitwise,
shifts, hex/octal/binary literals, `**=`, `&=` etc). Run `./gradlew :klangscript:jvmTest`.

### Step 2: Read existing content
Read `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt` to understand current structure.

### Step 3: Rewrite `klangScriptDocSections`

Replace the existing 8 sections with the 12 sections specified above. Each section is a
`DocSection(title, description, examples)` with `DocExample(title, description, code)` entries. All code strings use
`.trimMargin()` with `|` prefix.

### Step 4: Output rules

- **Prefer last-expression result over `console.log()`.** If the example's point is the result of an expression, just
  end with that expression — the REPL displays it.
- **Use `console.log()` only when needed** — for labeled output (`console.log("sum:", x)`), for showing intermediate
  steps, or when the last expression isn't the interesting part.
- **`import * from "stdlib"` only when needed** — only include the import when the example uses Math, console.log, or
  stdlib string/array methods. Simple expression examples (like `1 + 2`) don't need it.

### Step 5: Tone and descriptions

- Every `DocExample` should have a `description` (1-2 sentences, see tone guidelines above).
- Descriptions go ABOVE the code. They answer "what will I see when I run this?"
- No filler. No "in this example we will...". Direct and specific.

### Step 6: JS notes

- Add inline "JS note:" callouts where KlangScript differs from JavaScript. One sentence max.
- These go in the `DocExample.description` field, prefixed with "**JS note:**".
- Key callout points: no type coercion, `.size()` vs `.length`, `.length()` with parens, `if/else` is expression, arrow
  functions only, `??` vs `||`, no `undefined`.

### Step 7: Keep examples concise
Each example: 5-15 lines of code. No example longer than 20 lines (except the capstone).

### Step 8: Test
Run `./gradlew :klangscript:jvmTest` to verify all examples compile and execute.

### Step 9: Verify feature coverage
Cross-check the feature coverage checklist. Every feature should appear in at least one example.

### Step 10: Update klangscript-vs-javascript.md

Add the new features (bitwise, shifts, `??`, `?.`, number literal formats, new compound assignments) to the comparison
document.

---

## Section Descriptions (copy-paste ready for DocSection.description)

1. "Welcome to KlangScript! The REPL shows the result of your last expression automatically. Try editing the code and
   hitting Run."
2. "Use `let` for values that change and `const` for values that don't. KlangScript supports numbers, strings, booleans,
   and null — no `undefined`."
3. "Operators transform values. Arithmetic, comparison, logical, ternary, and assignment operators — plus
   increment/decrement and short-circuit evaluation."
4. "Handle null values cleanly. `??` provides fallbacks for null (not all falsy values), and `?.` safely navigates
   through nullable chains."
5. "Work with individual bits using bitwise and shift operators. Useful for flags, masks, and low-level audio data."
6. "Template literals with `${...}` embed expressions in strings. KlangScript provides rich string methods for
   searching, transforming, and splitting text."
7. "Arrays are ordered collections with Kotlin-style methods. Build sequences, filter data, and reshape lists."
8. "Objects group named values with dot and bracket access. Use `Object.keys()`, `Object.values()`, and
   `Object.entries()` to inspect them."
9. "Control flow lets your code make decisions and repeat actions. In KlangScript, `if/else` is an expression that
   returns a value."
10. "Arrow functions are the only function syntax in KlangScript. They support closures, currying, and higher-order
    patterns."
11. "The standard library provides Math, console, and more. Use import/export to organize code into modules."
12. "Chain methods for fluent, expressive code. Combine everything you've learned into a complete program."
