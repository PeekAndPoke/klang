# KlangScript — Basic Operators

### 2.1 Arithmetic Operators ✅ — `+` `-` `*` `/` `%` `**`

```javascript
let add = 5 + 3;        // 8
let sub = 10 - 4;       // 6
let mul = 6 * 7;        // 42
let div = 20 / 4;       // 5
let mod = 17 % 5;       // 2
let exp = 2 ** 8;       // 256
```

**Expected:** Results as commented

### 2.2 Unary Operators ✅ — `-x` `+x` `!x` `++x` `x++` `--x` `x--`

```javascript
let pos = +5;           // 5
let neg = -10;          // -10
let x = 5;
let inc = ++x;          // 6, x is 6
let y = 5;
let postInc = y++;      // 5, y is 6
let z = 10;
let dec = --z;          // 9, z is 9
let w = 10;
let postDec = w--;      // 10, w is 9
```

**Expected:** Results as commented

Note: `-10` is a single negative literal, not a unary operation on `10` (since 2026-09-08, see 1.1).
Precedence is Kotlin's, `-x.abs()` is `-(x.abs())`, and the ambiguous `-10.abs()` is a syntax error:
write `(-10).abs()` or `-(10.abs())`. Arithmetic is unaffected: `-2 * 3`, `2 - -3` and `--10` evaluate
as before.

### 2.3 Comparison Operators ✅ — `==` `!=` `<` `<=` `>` `>=` `===` `!==`

```javascript
let eq1 = 5 == 5;           // true
let eq2 = 5 == "5";         // true (loose equality)
let eq3 = 5 === 5;          // true
let eq4 = 5 === "5";        // false (strict equality)
let neq1 = 5 != 3;          // true
let neq2 = 5 !== "5";       // true
let lt = 3 < 5;             // true
let gt = 7 > 2;             // true
let lte = 5 <= 5;           // true
let gte = 10 >= 8;          // true
```

**Expected:** Results as commented

### 2.4 Logical Operators ✅

```javascript
let and1 = true && true;        // true
let and2 = true && false;       // false
let or1 = false || true;        // true
let or2 = false || false;       // false
let not1 = !true;               // false
let not2 = !false;              // true
let not3 = !!5;                 // true
```

**Expected:** Results as commented

### 2.5 String Concatenation 🟡 — `string + string` ✅; mixed `string + number` / `number + string` ❌ `[MEDIUM]`

> **Design note:** KlangScript does **not** do implicit type coercion for `+`.
> Only `string + string` is supported. For mixed-type string building, use template literals:
> `` `number: ${42}` `` instead of `"number: " + 42`.

```javascript
let concat1 = "hello" + " " + "world";  // "hello world" ✅
let concat2 = `number: ${42}`;          // "number: 42" ✅ (use template literal)

// These throw TypeError — no implicit coercion:
// "number: " + 42   ❌
// 1 + 2 + " items"  ❌ (use `${1 + 2} items` instead)
```

**Expected:** concat1 = "hello world"

### 2.6 Ternary Operator ✅

```javascript
let result1 = true ? "yes" : "no";      // "yes"
let result2 = false ? "yes" : "no";     // "no"
let result3 = 5 > 3 ? "bigger" : "smaller"; // "bigger"
let nested = true ? (false ? "a" : "b") : "c"; // "b"
```

**Expected:** Results as commented

### 2.7 Bitwise Operators ✅ — `&` `|` `^` `~`

```javascript
let and = 5 & 3;       // 1
let or = 5 | 3;        // 7
let xor = 5 ^ 3;       // 6
let not = ~5;           // -6
```

**Expected:** Results as commented. Operands are converted to 32-bit integers.

### 2.8 Shift Operators ✅ — `<<` `>>` `>>>`

```javascript
let shl = 1 << 3;       // 8
let shr = 16 >> 2;      // 4
let ushr = -1 >>> 28;   // 15
```

**Expected:** Results as commented. Operands are converted to 32-bit integers.

### 2.9 Nullish Coalescing ✅ — `??`

```javascript
let a = null ?? "default";   // "default"
let b = "value" ?? "default"; // "value"
let c = 0 ?? "default";      // 0 (0 is NOT null)
```

**Expected:** Returns the left operand if it is not null, otherwise the right operand. Unlike `||`, does not treat `0`,
`""`, or `false` as "nullish".

### 2.10 Optional Chaining ✅ — `?.`

```javascript
let obj = { a: { b: 1 } }
obj.a?.b     // 1
obj.c?.d     // null (no error thrown)
```

**Expected:** Returns the property value if the object is not null, otherwise returns null without throwing.

### 2.11 Compound Assignment Operators ✅

```javascript
let x = 10
x += 5       // 15
x -= 3       // 12
x *= 2       // 24
x /= 4       // 6
x %= 4       // 2
x **= 3      // 8
x &= 0x0F    // bitwise AND assign
x |= 0xF0    // bitwise OR assign
x ^= 0xFF    // bitwise XOR assign
x <<= 2      // shift left assign
x >>= 1      // shift right assign
```

**Expected:** Results as commented
