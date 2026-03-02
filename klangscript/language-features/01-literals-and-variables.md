# KlangScript — Basic Literals and Variables

### 1.1 Number Literals 🟡 — basic integers/decimals ✅; scientific notation `1e6` / `2.5e-3` ❌
`[EASY]` (lexer fix needed)

```javascript
let a = 42;
let b = 3.14159;
let c = -17;
let d = 0;
let e = 1e6;
let f = 2.5e-3;
```

**Expected:** All assignments succeed. Values: 42, 3.14159, -17, 0, 1000000, 0.0025

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
