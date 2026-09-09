# KlangScript — Math Object

> **Status:** ❌ `[SEPARATE_STDLIB]` — all Math items below belong in a dedicated Math stdlib library; some already in
> current `stdlib`

### 10.1 Math Constants ❌ `[SEPARATE_STDLIB]`

```javascript
let pi = Math.PI;       // ~3.14159
let e = Math.E;         // ~2.71828
```

**Expected:** Values approximately as shown

### 10.2 Math.abs/Math.sign 🟡 `[SEPARATE_STDLIB]` — `abs` in current stdlib; `sign` missing

```javascript
let a1 = Math.abs(-5);      // 5
let a2 = Math.abs(5);       // 5

let s1 = Math.sign(-5);     // -1
let s2 = Math.sign(5);      // 1
let s3 = Math.sign(0);      // 0
```

**Expected:** Results as commented

### 10.3 Math.ceil/Math.floor/Math.round/Math.trunc 🟡 `[SEPARATE_STDLIB]` — `ceil/floor/round` in current stdlib;
`trunc` missing

```javascript
let c = Math.ceil(4.3);      // 5
let f = Math.floor(4.7);     // 4
let r1 = Math.round(4.4);    // 4
let r2 = Math.round(4.5);    // 4, a tie goes to the even neighbour (kotlin.math.round), see 10.9
let t = Math.trunc(4.7);     // 4
```

**Expected:** Results as commented

### 10.4 Math.min/Math.max 🟡 `[SEPARATE_STDLIB]` — 2-arg form in current stdlib; vararg form missing

```javascript
let min = Math.min(3, 1, 4, 1, 5);   // 1
let max = Math.max(3, 1, 4, 1, 5);   // 5
```

**Expected:** Results as commented

### 10.5 Math.pow/Math.sqrt ✅ — in current `stdlib`

```javascript
let p1 = Math.pow(2, 3);     // 8
let p2 = Math.pow(5, 2);     // 25

let s1 = Math.sqrt(16);      // 4
let s2 = Math.sqrt(2);       // ~1.414
```

**Expected:** Results as commented

### 10.6 Math.random() ❌ `[SEPARATE_STDLIB]`

```javascript
let r = Math.random();
// r is a number between 0 (inclusive) and 1 (exclusive)

let randInt = Math.floor(Math.random() * 10);
// randInt is an integer from 0 to 9
```

**Expected:** Values in expected ranges

### 10.7 Trigonometric Functions ✅ — `sin`, `cos`, `tan` in current `stdlib`

```javascript
let sin = Math.sin(Math.PI / 2);     // 1
let cos = Math.cos(Math.PI);         // -1
let tan = Math.tan(Math.PI / 4);     // ~1
```

**Expected:** Results approximately as commented

### 10.8 Math.log/Math.exp ❌ `[SEPARATE_STDLIB]`

```javascript
let log = Math.log(Math.E);      // 1
let exp = Math.exp(1);           // ~2.71828
```

**Expected:** Results approximately as commented

### 10.9 Number Methods ✅ (in current `stdlib`)

Every number carries its math as a method, so a calculation reads left to right. Where a `Math.*`
twin exists (`pow`, `abs`, `sqrt`, `round`, `floor`, `ceil`, `min`, `max`) it keeps working and the method
is an additional spelling; `clamp`, `rem`, `mod`, the logarithms and the musical conversions exist only
as methods.

```javascript
let fifth = 2.pow(7/12);        // 1.4983, a perfect fifth
let c4 = 440 * 2.pow(-9/12);    // 261.63, middle C
```

**Expected:** Results approximately as commented

The full set: `pow`, `abs`, `sqrt`, `round`, `floor`, `ceil`, `min`, `max`, `clamp`, `rem`, `mod`,
`log2`, `log10`, `ln`, `exp`, `sign`, and the musical ones `semitones`, `cents`, `toSemitones`, `db`,
`toDb`, plus `toRatio()` on an interval name such as `"P5"`.

Four things worth knowing:

- `^` is bitwise XOR, not exponentiation, so `2^(7/12)` is 2. Write `2.pow(7/12)` or `2 ** (7/12)`.
- A negative receiver needs parentheses: `(-8).abs()` is 8, and the ambiguous `-8.abs()` is a syntax error.
- `rem` takes the sign of the dividend (`-1.rem(12)` is -1), `mod` the sign of the divisor
  (`(-1).mod(12)` is 11). `rem` is the method form of `%`; both throw on a zero divisor.
- `7.semitones()` is the frequency ratio 1.4983, and `"P5".toRatio()` says the same thing by name.

Background and the full design: `docs/tasks-archive/2026-09/20260908-klangscript-number-methods.md`.
