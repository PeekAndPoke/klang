# KlangScript — Sets and Maps

> **Status:** ❌ `[OUT_OF_SCOPE]` — JS stdlib objects; not planned for KlangScript core

### 15.1 Set Creation and Basic Operations

```javascript
let set = new Set();
set.add(1);
set.add(2);
set.add(3);
set.add(2);  // duplicate
let size = set.size;  // 3

let has2 = set.has(2);      // true
let has5 = set.has(5);      // false

set.delete(2);
let size2 = set.size;  // 2
```

**Expected:** Results as commented

### 15.2 Set from Array

```javascript
let set = new Set([1, 2, 3, 2, 1]);
let size = set.size;  // 3
```

**Expected:** size = 3

### 15.3 Set Iteration

```javascript
let set = new Set([1, 2, 3]);
let arr = [];
for (let value of set) {
    arr.push(value);
}
// arr is [1, 2, 3]
```

**Expected:** arr = [1, 2, 3]

### 15.4 Set to Array

```javascript
let set = new Set([1, 2, 3]);
let arr = [...set];
// arr is [1, 2, 3]

let arr2 = Array.from(set);
// arr2 is [1, 2, 3]
```

**Expected:** Results as commented

### 15.5 Map Creation and Basic Operations

```javascript
let map = new Map();
map.set("a", 1);
map.set("b", 2);
map.set("c", 3);

let val = map.get("b");     // 2
let size = map.size;        // 3
let hasB = map.has("b");    // true

map.delete("b");
let size2 = map.size;  // 2
```

**Expected:** Results as commented

### 15.6 Map with Various Key Types

```javascript
let map = new Map();
map.set(1, "number key");
map.set("1", "string key");
map.set(true, "boolean key");
map.set({}, "object key");

let v1 = map.get(1);        // "number key"
let v2 = map.get("1");      // "string key"
```

**Expected:** Results as commented

### 15.7 Map from Array

```javascript
let map = new Map([
    ["a", 1],
    ["b", 2],
    ["c", 3]
]);
let val = map.get("b");  // 2
```

**Expected:** val = 2

### 15.8 Map Iteration

```javascript
let map = new Map([["a", 1], ["b", 2]]);

let keys = [];
for (let key of map.keys()) {
    keys.push(key);
}
// keys is ["a", "b"]

let values = [];
for (let value of map.values()) {
    values.push(value);
}
// values is [1, 2]

let entries = [];
for (let [key, value] of map.entries()) {
    entries.push([key, value]);
}
// entries is [["a", 1], ["b", 2]]
```

**Expected:** Results as commented
