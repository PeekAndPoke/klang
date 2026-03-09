# KlangScript — JSON

> **Status:** ❌ `[OUT_OF_SCOPE]` — JS stdlib object; not planned for KlangScript core

### 14.1 JSON.stringify()

```javascript
let obj = {a: 1, b: 2, c: 3};
let json = JSON.stringify(obj);
// json is '{"a":1,"b":2,"c":3}'

let arr = [1, 2, 3];
let json2 = JSON.stringify(arr);
// json2 is '[1,2,3]'
```

**Expected:** Results as commented

### 14.2 JSON.parse()

```javascript
let json = '{"a":1,"b":2}';
let obj = JSON.parse(json);
// obj is { a: 1, b: 2 }

let json2 = '[1,2,3]';
let arr = JSON.parse(json2);
// arr is [1, 2, 3]
```

**Expected:** Results as commented

### 14.3 JSON with Nested Structures

```javascript
let obj = {
    name: "Alice",
    age: 30,
    address: {
        city: "NYC",
        zip: "10001"
    },
    hobbies: ["reading", "coding"]
};
let json = JSON.stringify(obj);
let parsed = JSON.parse(json);
// parsed should match obj
```

**Expected:** parsed deeply equals obj
