# KlangScript — Basic Operators

### 2.1 Arithmetic Operators 🟡 — `+` `-` `*` `/` `%` ✅; `**` exponentiation ❌ `[EASY]`

```javascript
let add = 5 + 3;        // 8
let sub = 10 - 4;       // 6
let mul = 6 * 7;        // 42
let div = 20 / 4;       // 5
let mod = 17 % 5;       // 2
let exp = 2 ** 8;       // 256
```

**Expected:** Results as commented

### 2.2 Unary Operators 🟡 — `-x` `+x` `!x` ✅; `++x` `x++` `--x` `x--` ❌ `[EASY]`

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

### 2.3 Comparison Operators 🟡 — `==` `!=` `<` `<=` `>` `>=` ✅; `===` `!==` ❌ `[EASY]`

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

### 2.5 String Concatenation ✅

```javascript
let concat1 = "hello" + " " + "world";  // "hello world"
let concat2 = "number: " + 42;          // "number: 42"
let concat3 = 1 + 2 + " items";         // "3 items"
let concat4 = "items: " + (1 + 2);      // "items: 3"
```

**Expected:** Results as commented

### 2.6 Ternary Operator ❌ `[EASY]`

```javascript
let result1 = true ? "yes" : "no";      // "yes"
let result2 = false ? "yes" : "no";     // "no"
let result3 = 5 > 3 ? "bigger" : "smaller"; // "bigger"
let nested = true ? (false ? "a" : "b") : "c"; // "b"
```

**Expected:** Results as commented
