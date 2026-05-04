# KlangScript — Basic Literals and Variables

### 1.1 Number Literals ✅ — basic integers/decimals, negative, scientific notation `1e6` / `2.5e-3`

```javascript
let a = 42;
let b = 3.14159;
let c = -17;
let d = 0;
let e = 1e6;
let f = 2.5e-3;
```

**Expected:** All assignments succeed. Values: 42, 3.14159, -17, 0, 1000000, 0.0025

### 1.1b Hex, Octal, Binary Number Literals ✅

```javascript
let hex = 0xFF;      // 255
let hex2 = 0XAB;     // 171
let oct = 0o77;      // 63
let oct2 = 0O10;     // 8
let bin = 0b1010;    // 10
let bin2 = 0B11111111; // 255
```

**Expected:** All assignments succeed. Values as commented. Prefixes: `0x`/`0X` (hex), `0o`/`0O` (octal), `0b`/`0B` (
binary).

### 1.2 String Literals ✅

```javascript
let s1 = "hello";
let s2 = 'world';
let s3 = "it's";
let s4 = 'say "hi"';
let s5 = "";
```

**Expected:** All assignments succeed. Values as shown.

### 1.3 Boolean Literals ✅

```javascript
let t = true;
let f = false;
```

**Expected:** t is true, f is false

### 1.4 Null and Undefined 🟡 — `null` literal ✅; `undefined` identifier ❌
`[EASY]` (add as stdlib global alias for null); uninitialised `let v` ✅

```javascript
let n = null;
let u = undefined; // undefined is an alias for 'null' in KlangScript
let v;
```

**Expected:** n is null, u and v are undefined

### 1.5 Variable Reassignment ✅

```javascript
let x = 10;
x = 20;
x = "changed";
```

**Expected:** x is "changed"

### 1.6 Export Declaration ✅ — `export name = expr`

```javascript
export bass = 42
export greeting = "hello"
export song = note("c3 e3 g3").gain(0.5)
```

**Semantics:**

- Combined immutable binding + auto-export: equivalent to
  `const name = expr; export { name }` in one statement.
- The bound name is immutable (reassignment throws an `AssignmentError`).
- Top-level form: intended for use at the top of a library / module file. Inside a
  function body or block the binding works locally but the export marker has no effect
  (consistent with the existing `export { ... }` form).

**Use case:** the canonical way for a klangscript module to expose named parts (lead,
bass, drum kits, song) so importing modules can pull them by name:

```javascript
// In a library:
export lead = note("c3 e3 g3").gain(0.5)
export bass = note("c2 g2").lpf(800)
export song = stack(lead, bass)

// In a consumer:
import { song, bass } from "peekandpoke/der-schmetterling"
play(song)
```

**Expected:** the exported bindings are accessible to importers; reassigning them
throws.
