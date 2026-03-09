# KlangScript — Type Coercion and Truthiness

### 8.1 Truthy/Falsy Values ✅ — implemented in interpreter's `toBoolean()`

```javascript
let t1 = !!true;       // true
let t2 = !!1;          // true
let t3 = !!"string";   // true
let t4 = !!{};         // true
let t5 = !![];         // true

let f1 = !!false;      // false
let f2 = !!0;          // false
let f3 = !!"";         // false
let f4 = !!null;       // false
let f5 = !!undefined;  // false
let f6 = !!NaN;        // false
```

**Expected:** Results as commented

### 8.2 Type Conversions ❌ `[SEPARATE_STDLIB]` — `Number()`, `String()`, `Boolean()` as global functions

```javascript
let n1 = Number("42");        // 42
let n2 = Number("3.14");      // 3.14
let n3 = Number("hello");     // NaN
let n4 = Number(true);        // 1
let n5 = Number(false);       // 0

let s1 = String(42);          // "42"
let s2 = String(true);        // "true"
let s3 = String(null);        // "null"

let b1 = Boolean(1);          // true
let b2 = Boolean(0);          // false
let b3 = Boolean("hello");    // true
```

**Expected:** Results as commented

### 8.3 parseInt/parseFloat ❌ `[SEPARATE_STDLIB]`

```javascript
let i1 = parseInt("42");           // 42
let i2 = parseInt("42.5");         // 42
let i3 = parseInt("42px");         // 42
let i4 = parseInt("hello");        // NaN

let f1 = parseFloat("3.14");       // 3.14
let f2 = parseFloat("3.14abc");    // 3.14
```

**Expected:** Results as commented

### 8.4 isNaN/isFinite ❌ `[SEPARATE_STDLIB]`

```javascript
let n1 = isNaN(NaN);           // true
let n2 = isNaN(42);            // false
let n3 = isNaN("hello");       // true

let f1 = isFinite(42);         // true
let f2 = isFinite(Infinity);   // false
let f3 = isFinite(NaN);        // false
```

**Expected:** Results as commented
