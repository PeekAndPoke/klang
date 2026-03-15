# KlangScript vs JavaScript — Complete Inventory

**Date:** 2026-03-15
**Purpose:** Reference for tutorial authors and JS developers coming to KlangScript.

---

## Philosophy

KlangScript looks like JavaScript but thinks like Kotlin. It's a domain-specific language for live music coding —
not a general-purpose JS replacement. Many JS features are deliberately excluded because they don't serve music
creation.

---

## 1. Things That Look Like JS But Behave Differently

These are the **gotchas** — a JS developer will type these expecting one thing and get another.

| What                               | JavaScript                                               | KlangScript                                                                           | Gotcha Level |
|------------------------------------|----------------------------------------------------------|---------------------------------------------------------------------------------------|--------------|
| **String + Number**                | `"age: " + 30` → `"age: 30"` (implicit coercion)         | **TypeError** — no implicit coercion                                                  | 🔴 High      |
| **`if/else` returns a value**      | Statement only (need ternary for expression)             | **Expression** — `let x = if (a) { 1 } else { 2 }`                                    | 🟡 Medium    |
| **`.length`**                      | `arr.length` (property), `str.length` (property)         | `.length()` — **method call with parens**                                             | 🔴 High      |
| **`.size()` vs `.length`**         | Arrays have `.length`                                    | Arrays have **`.size()`** (Kotlin-style)                                              | 🔴 High      |
| **Array methods are Kotlin-style** | `.push()`, `.pop()`, `.includes()`, `.join()`            | **`.add()`**, **`.removeLast()`**, **`.contains()`**, **`.joinToString()`**           | 🔴 High      |
| **`===` / `!==`**                  | Strict equality (no coercion) vs `==` (with coercion)    | **Same as `==`/`!=`** — no type coercion exists, so strict/loose are identical        | 🟢 Low       |
| **No `undefined`**                 | Variables start as `undefined`                           | Variables start as **`null`**. `undefined` doesn't exist.                             | 🟡 Medium    |
| **Semicolons**                     | Required (or ASI inserts them)                           | **Optional** — newlines are statement separators                                      | 🟢 Low       |
| **`const` mutability**             | `const` prevents reassignment but allows object mutation | Same behavior — `const` prevents reassignment only                                    | 🟢 Low       |
| **Truthy/falsy**                   | `0`, `""`, `null`, `undefined`, `NaN`, `false` are falsy | `0`, `""`, `null`, `false` are falsy. **No `undefined`, no `NaN`**                    | 🟡 Medium    |
| **String concatenation**           | `"a" + "b"` → `"ab"`, `"a" + 1` → `"a1"`                 | `"a" + "b"` → `"ab"`, `"a" + 1` → **TypeError**. Use template literals: `` `a${1}` `` | 🔴 High      |

---

## 2. Syntax That Exists in JS But NOT in KlangScript

### Deliberately Excluded (Out of Scope — will never be added)

| JS Feature                            | Why Excluded                                                      |
|---------------------------------------|-------------------------------------------------------------------|
| `function` keyword                    | Arrow functions only. No `function name() {}`, no `function() {}` |
| `class` / `extends` / `new`           | No OOP. Use object literals + closures                            |
| `this` keyword                        | No method binding. Store arrow functions on objects instead       |
| `try` / `catch` / `throw`             | No user-level error handling. Errors are interpreter-level        |
| `typeof` / `instanceof`               | No runtime type inspection                                        |
| `async` / `await` / `Promise`         | Synchronous only. Async handled at application layer              |
| `eval()`                              | Never                                                             |
| `RegExp` / `/pattern/flags`           | Not needed for music coding                                       |
| `JSON.parse` / `JSON.stringify`       | Out of scope                                                      |
| `Set` / `Map` / `WeakMap` / `WeakSet` | Out of scope                                                      |
| `Symbol` / `Proxy` / `Reflect`        | Out of scope                                                      |
| `var` keyword                         | Only `let` and `const`. No hoisting quirks                        |
| `switch` / `case` / `default`         | Will get `when`-expression (Kotlin-style) instead                 |
| Tagged template literals              | `` tag`hello ${x}` `` — out of scope                              |
| `void` operator                       | Not needed                                                        |
| Comma operator                        | Not supported                                                     |
| `with` statement                      | Never                                                             |
| Labels (`outer: for...`)              | Not supported                                                     |

### Not Yet Implemented (Planned)

| JS Feature                               | KlangScript Status | Difficulty |
|------------------------------------------|--------------------|------------|
| `arr.map(fn)`                            | ❌ Not yet          | MEDIUM     |
| `arr.filter(fn)`                         | ❌ Not yet          | MEDIUM     |
| `arr.forEach(fn)`                        | ❌ Not yet          | MEDIUM     |
| `arr.find(fn)` / `arr.findIndex(fn)`     | ❌ Not yet          | MEDIUM     |
| `arr.reduce(fn)` / `arr.reduceRight(fn)` | ❌ Not yet          | MEDIUM     |
| `arr.every(fn)` / `arr.some(fn)`         | ❌ Not yet          | MEDIUM     |
| `arr.sort(fn)`                           | ❌ Not yet          | MEDIUM     |
| `arr.flat()` / `arr.flatMap(fn)`         | ❌ Not yet          | MEDIUM     |
| `arr.splice()`                           | ❌ Not yet          | MEDIUM     |
| `for...in` / `for...of`                  | ❌ Not yet          | MEDIUM     |
| `when`-expression (replaces `switch`)    | ❌ Not yet          | MEDIUM     |
| Spread operator `...`                    | ❌ Not yet          | HARD       |
| Destructuring `let {a, b} = obj`         | ❌ Not yet          | HARD       |
| Destructuring `let [a, b] = arr`         | ❌ Not yet          | HARD       |
| Default parameters `(x = 5) => ...`      | ❌ Not yet          | MEDIUM     |
| Rest parameters `(...args) => ...`       | ❌ Not yet          | HARD       |
| Computed property names `{[expr]: val}`  | ❌ Not yet          | HARD       |
| `delete obj.prop`                        | ❌ Not yet          | MEDIUM     |

---

## 3. KlangScript Array Methods vs JavaScript

This is the biggest source of confusion for JS developers. KlangScript follows **Kotlin naming conventions**.

| Operation         | JavaScript                       | KlangScript                    |
|-------------------|----------------------------------|--------------------------------|
| Get length        | `arr.length`                     | `arr.size()`                   |
| First element     | `arr[0]`                         | `arr.first()` (also `arr[0]`)  |
| Last element      | `arr[arr.length - 1]`            | `arr.last()`                   |
| Add to end        | `arr.push(item)`                 | `arr.add(item)`                |
| Remove from end   | `arr.pop()`                      | `arr.removeLast()`             |
| Remove from start | `arr.shift()`                    | `arr.removeFirst()`            |
| Remove at index   | `arr.splice(i, 1)`               | `arr.removeAt(i)`              |
| Check contains    | `arr.includes(item)`             | `arr.contains(item)`           |
| Join to string    | `arr.join(sep)`                  | `arr.joinToString(sep)`        |
| Reverse           | `arr.reverse()` (mutates!)       | `arr.reversed()` (returns new) |
| Check empty       | `arr.length === 0`               | `arr.isEmpty()`                |
| Check not empty   | `arr.length > 0`                 | `arr.isNotEmpty()`             |
| First N items     | `arr.slice(0, n)`                | `arr.take(n)`                  |
| Skip N items      | `arr.slice(n)`                   | `arr.drop(n)`                  |
| Sub-array         | `arr.slice(start, end)`          | `arr.subList(start, end)`      |
| Find index        | `arr.indexOf(item)`              | `arr.indexOf(item)` (same!)    |
| Map               | `arr.map(fn)`                    | ❌ Not yet                      |
| Filter            | `arr.filter(fn)`                 | ❌ Not yet                      |
| ForEach           | `arr.forEach(fn)`                | ❌ Not yet                      |
| Reduce            | `arr.reduce(fn, init)`           | ❌ Not yet                      |
| Find              | `arr.find(fn)`                   | ❌ Not yet                      |
| Sort              | `arr.sort(fn)`                   | ❌ Not yet                      |
| Some/Every        | `arr.some(fn)` / `arr.every(fn)` | ❌ Not yet                      |

---

## 4. KlangScript String Methods vs JavaScript

| Operation        | JavaScript                          | KlangScript                  |
|------------------|-------------------------------------|------------------------------|
| Get length       | `str.length` (property)             | `str.length()` (method)      |
| Char at index    | `str.charAt(i)` or `str[i]`         | `str.charAt(i)`              |
| Substring        | `str.substring(s, e)`               | `str.substring(s, e)` (same) |
| Slice            | `str.slice(s, e)`                   | `str.slice(s, e)` (same)     |
| Index of         | `str.indexOf(search)`               | `str.indexOf(search)` (same) |
| Split            | `str.split(sep)`                    | `str.split(sep)` (same)      |
| Upper/lower      | `str.toUpperCase()`                 | `str.toUpperCase()` (same)   |
| Trim             | `str.trim()`                        | `str.trim()` (same)          |
| Starts/ends with | `str.startsWith(x)`                 | `str.startsWith(x)` (same)   |
| Replace          | `str.replace(a, b)`                 | `str.replace(a, b)` (same)   |
| Concat           | `str.concat(other)`                 | `str.concat(other)` (same)   |
| Repeat           | `str.repeat(n)`                     | `str.repeat(n)` (same)       |
| Includes         | `str.includes(x)`                   | ❌ Not yet                    |
| Trim start/end   | `str.trimStart()` / `str.trimEnd()` | ❌ Not yet                    |
| Pad start/end    | `str.padStart()` / `str.padEnd()`   | ❌ Not yet                    |
| Match (regex)    | `str.match(regex)`                  | ❌ Out of scope               |

**Key difference:** `.length()` requires parens in KlangScript. JS developers will forget them.

---

## 5. Math Object

| Method                        | JavaScript    | KlangScript         |
|-------------------------------|---------------|---------------------|
| `Math.sqrt(x)`                | ✅             | ✅ (same)            |
| `Math.abs(x)`                 | ✅             | ✅ (same)            |
| `Math.floor(x)`               | ✅             | ✅ (same)            |
| `Math.ceil(x)`                | ✅             | ✅ (same)            |
| `Math.round(x)`               | ✅             | ✅ (same)            |
| `Math.pow(x, y)`              | ✅             | ✅ (same)            |
| `Math.sin(x)` / `cos` / `tan` | ✅             | ✅ (same)            |
| `Math.min(a, b)` / `max`      | ✅ (variadic)  | ✅ (**2 args only**) |
| `Math.PI` / `Math.E`          | ✅ (constants) | ❌ Not yet           |
| `Math.random()`               | ✅             | ❌ Not yet           |
| `Math.trunc(x)`               | ✅             | ❌ Not yet           |
| `Math.sign(x)`                | ✅             | ❌ Not yet           |
| `Math.log(x)` / `exp`         | ✅             | ❌ Not yet           |

---

## 6. Import/Export System

| Feature                         | JavaScript (ES6) | KlangScript                        |
|---------------------------------|------------------|------------------------------------|
| `import * from "lib"`           | ❌ (not valid JS) | ✅ — imports all exports into scope |
| `import * as ns from "lib"`     | ✅                | ✅ (same)                           |
| `import { x } from "lib"`       | ✅                | ✅ (same)                           |
| `import { x as y } from "lib"`  | ✅                | ✅ (same)                           |
| `import x from "lib"` (default) | ✅                | ❌ Not yet                          |
| `export { x, y }`               | ✅                | ✅ (same)                           |
| `export { x as y }`             | ✅                | ✅ (same)                           |
| `export default`                | ✅                | ❌ Not yet                          |
| `export const x = ...`          | ✅                | ❌ Not yet                          |
| Dynamic `import()`              | ✅                | ❌ Out of scope                     |

**Note:** `import * from "lib"` (without `as`) is KlangScript-specific — it dumps all exports into current scope. This
doesn't exist in JS.

---

## 7. Things KlangScript Has That JS Doesn't

| Feature                               | Description                                                |
|---------------------------------------|------------------------------------------------------------|
| `if/else` as expression               | `let x = if (cond) { a } else { b }` — no need for ternary |
| `import * from "lib"` (bare wildcard) | Dumps all exports into scope directly                      |
| `.size()` on arrays                   | Kotlin-style, clearer intent than `.length`                |
| `.first()` / `.last()`                | Direct access without index math                           |
| `.isEmpty()` / `.isNotEmpty()`        | Boolean checks without comparing `.size() == 0`            |
| `.take(n)` / `.drop(n)`               | Cleaner than `.slice()` for common operations              |
| `.joinToString(sep)`                  | More descriptive than `.join()`                            |
| `.contains(item)`                     | Reads better than `.includes()`                            |
| `.reversed()` (non-mutating)          | Returns new array. JS `.reverse()` mutates in place.       |

---

## Summary: The 5 Things a JS Developer MUST Know

1. **No implicit type coercion.** `"age: " + 30` throws. Use `` `age: ${30}` `` instead.
2. **Array methods are Kotlin-style.** `.size()` not `.length`, `.add()` not `.push()`, `.contains()` not `.includes()`.
3. **`.length()` needs parens.** It's a method, not a property. On both strings and arrays.
4. **`if/else` is an expression.** You can assign it: `let x = if (a) { 1 } else { 2 }`.
5. **No `function` keyword, no `class`, no `this`.** Arrow functions only. Object literals + closures for everything.
