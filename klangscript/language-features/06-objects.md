# KlangScript — Objects

### 6.1 Object Literals ✅

```javascript
let obj1 = {a: 1, b: 2, c: 3};
let obj2 = {name: "Alice", age: 30};
let obj3 = {};
let nested = {a: 1, b: {c: 2, d: 3}};
```

**Expected:** All objects created successfully

### 6.2 Object Property Access ✅ — dot notation `obj.prop` and bracket notation `obj["key"]`

```javascript
let obj = {name: "Bob", age: 25};
let name1 = obj.name;        // "Bob"
let age1 = obj["age"];       // 25

obj.name = "Alice";
obj["age"] = 30;
// obj is { name: "Alice", age: 30 }

let key = "name";
let value = obj[key];        // "Alice"
```

**Expected:** Results as commented

### 6.3 Object Property Addition/Deletion 🟡 — `obj.prop = val` ✅; `obj["key"] = val` ✅; `delete obj.prop` ❌ `[MEDIUM]`

```javascript
let obj = {a: 1};
obj.b = 2;
obj["c"] = 3;
// obj is { a: 1, b: 2, c: 3 }

delete obj.b;
// obj is { a: 1, c: 3 }
```

**Expected:** Results as commented

### 6.4 Object Methods ❌ `[OUT_OF_SCOPE]` — requires `this` keyword which is not planned

```javascript
let obj = {
    x: 10,
    y: 20,
    sum: function () {
        return this.x + this.y;
    }
};
let result = obj.sum();  // 30
```

**Expected:** result = 30

### 6.5 Object Method Shorthand ❌ `[OUT_OF_SCOPE]` — requires `this` keyword which is not planned

```javascript
let obj = {
    x: 10,
    y: 20,
    sum() {
        return this.x + this.y;
    }
};
let result = obj.sum();  // 30
```

**Expected:** result = 30

### 6.6 Computed Property Names ❌ `[HARD]`

```javascript
let key = "dynamic";
let obj = {
    [key]: "value",
    ["computed" + "Key"]: 42
};
// obj is { dynamic: "value", computedKey: 42 }
```

**Expected:** obj = { dynamic: "value", computedKey: 42 }

### 6.7 Property Shorthand ✅ — desugars to `name: name` at parse time

```javascript
let name = "Alice";
let age = 30;
let obj = {name, age};
// obj is { name: "Alice", age: 30 }
```

**Expected:** obj = { name: "Alice", age: 30 }

### 6.8 Object Destructuring ❌ `[HARD]`

```javascript
let obj = {a: 1, b: 2, c: 3};
let {a, b} = obj;
// a is 1, b is 2

let {c: renamed} = obj;
// renamed is 3

let {d = 10} = obj;
// d is 10 (default value)

let {e: aliased = 20} = obj;
// aliased is 20
```

**Expected:** Results as commented

### 6.9 Nested Destructuring ❌ `[HARD]`

```javascript
let obj = {a: 1, b: {c: 2, d: 3}};
let {b: {c, d}} = obj;
// c is 2, d is 3
```

**Expected:** c = 2, d = 3

### 6.10 Rest Properties in Destructuring ❌ `[HARD]`

```javascript
let obj = {a: 1, b: 2, c: 3, d: 4};
let {a, ...rest} = obj;
// a is 1, rest is { b: 2, c: 3, d: 4 }
```

**Expected:** Results as commented

### 6.11 Spread Operator with Objects ❌ `[HARD]`

```javascript
let obj1 = {a: 1, b: 2};
let obj2 = {...obj1};
// obj2 is { a: 1, b: 2 }

let obj3 = {...obj1, c: 3};
// obj3 is { a: 1, b: 2, c: 3 }

let obj4 = {a: 99, ...obj1};
// obj4 is { a: 1, b: 2 } (obj1.a overwrites)

let obj5 = {...obj1, a: 99};
// obj5 is { a: 99, b: 2 }
```

**Expected:** Results as commented

### 6.12 Object.keys() ✅ — in `stdlib` library

```javascript
let obj = {a: 1, b: 2, c: 3};
let keys = Object.keys(obj);
// keys is ["a", "b", "c"]
```

**Expected:** keys = ["a", "b", "c"]

### 6.13 Object.values() ✅ — in `stdlib` library

```javascript
let obj = {a: 1, b: 2, c: 3};
let values = Object.values(obj);
// values is [1, 2, 3]
```

**Expected:** values = [1, 2, 3]

### 6.14 Object.entries() ✅ — in `stdlib` library

```javascript
let obj = {a: 1, b: 2, c: 3};
let entries = Object.entries(obj);
// entries is [["a", 1], ["b", 2], ["c", 3]]
```

**Expected:** entries = [["a", 1], ["b", 2], ["c", 3]]

### 6.15 Object.assign() ❌ `[SEPARATE_STDLIB]` — will be in a dedicated Object stdlib library

```javascript
let target = {a: 1};
let source = {b: 2, c: 3};
Object.assign(target, source);
// target is { a: 1, b: 2, c: 3 }

let merged = Object.assign({}, {a: 1}, {b: 2}, {c: 3});
// merged is { a: 1, b: 2, c: 3 }
```

**Expected:** Results as commented

### 6.16 Object.hasOwnProperty() ❌ `[SEPARATE_STDLIB]` — will be in a dedicated Object stdlib library

```javascript
let obj = {a: 1, b: 2};
let has_a = obj.hasOwnProperty("a");    // true
let has_c = obj.hasOwnProperty("c");    // false
```

**Expected:** Results as commented

### 6.17 in Operator ✅

```javascript
let obj = {a: 1, b: 2};
let has_a = "a" in obj;      // true
let has_c = "c" in obj;      // false
```

**Expected:** Results as commented

### 6.18 for...in Loop ❌ `[MEDIUM]`

```javascript
let obj = {a: 1, b: 2, c: 3};
let keys = [];
for (let key in obj) {
    keys.push(key);
}
// keys is ["a", "b", "c"]
```

**Expected:** keys = ["a", "b", "c"]

### 6.19 for...of Loop with Arrays ❌ `[MEDIUM]`

```javascript
let arr = [10, 20, 30];
let sum = 0;
for (let value of arr) {
    sum += value;
}
// sum is 60
```

**Expected:** sum = 60

### 6.20 Object with Functions ✅

```javascript
let calculator = {
    add: (a, b) => a + b,
    subtract: (a, b) => a - b,
    multiply: (a, b) => a * b
};
let r1 = calculator.add(5, 3);         // 8
let r2 = calculator.subtract(10, 4);   // 6
let r3 = calculator.multiply(6, 7);    // 42
```

**Expected:** Results as commented
