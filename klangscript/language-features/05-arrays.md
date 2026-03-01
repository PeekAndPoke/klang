# KlangScript — Arrays

### 5.1 Array Literals ✅

```javascript
let arr1 = [1, 2, 3, 4, 5];
let arr2 = ["a", "b", "c"];
let arr3 = [1, "two", true, null];
let arr4 = [];
let nested = [[1, 2], [3, 4], [5, 6]];
```

**Expected:** All arrays created successfully

### 5.2 Array Access ❌ `[EASY]` — requires `IndexAccess` AST node

```javascript
let arr = [10, 20, 30, 40];
let first = arr[0];      // 10
let last = arr[3];       // 40
arr[1] = 25;
// arr is now [10, 25, 30, 40]
```

**Expected:** Results as commented

### 5.3 Array Length 🟡 — `arr.length()` ✅; `arr.length` property-style ❌ `[MEDIUM]`; `arr.length = n` assignment ❌
`[MEDIUM]`

```javascript
let arr = [1, 2, 3, 4, 5];
let len = arr.length;  // 5

arr.length = 3;
// arr is now [1, 2, 3]

arr.length = 5;
// arr is now [1, 2, 3, undefined, undefined]
```

**Expected:** Results as commented

### 5.4 Array Methods - Push/Pop ✅

```javascript
let arr = [1, 2, 3];
arr.push(4);
// arr is [1, 2, 3, 4]
arr.push(5, 6);
// arr is [1, 2, 3, 4, 5, 6]

let last = arr.pop();
// last is 6, arr is [1, 2, 3, 4, 5]
```

**Expected:** Results as commented

### 5.5 Array Methods - Shift/Unshift ✅

```javascript
let arr = [1, 2, 3];
arr.unshift(0);
// arr is [0, 1, 2, 3]

let first = arr.shift();
// first is 0, arr is [1, 2, 3]
```

**Expected:** Results as commented

### 5.6 Array Methods - Slice 🟡 — basic `slice(start, end)` ✅; negative indices and single-arg form ❌ `[EASY]`

```javascript
let arr = [1, 2, 3, 4, 5];
let slice1 = arr.slice(1, 3);    // [2, 3]
let slice2 = arr.slice(2);       // [3, 4, 5]
let slice3 = arr.slice(-2);      // [4, 5]
let slice4 = arr.slice(1, -1);   // [2, 3, 4]
```

**Expected:** Results as commented

### 5.7 Array Methods - Splice ❌ `[MEDIUM]`

```javascript
let arr = [1, 2, 3, 4, 5];
let removed = arr.splice(2, 2);
// removed is [3, 4], arr is [1, 2, 5]

arr = [1, 2, 3, 4, 5];
arr.splice(2, 1, 99, 100);
// arr is [1, 2, 99, 100, 4, 5]

arr = [1, 2, 3];
arr.splice(1, 0, 1.5);
// arr is [1, 1.5, 2, 3]
```

**Expected:** Results as commented

### 5.8 Array Methods - Concat ✅

```javascript
let arr1 = [1, 2];
let arr2 = [3, 4];
let combined = arr1.concat(arr2);
// combined is [1, 2, 3, 4]

let multi = [1].concat([2, 3], [4, 5], 6);
// multi is [1, 2, 3, 4, 5, 6]
```

**Expected:** Results as commented

### 5.9 Array Methods - Join ✅

```javascript
let arr = ["a", "b", "c"];
let joined1 = arr.join();        // "a,b,c"
let joined2 = arr.join("");      // "abc"
let joined3 = arr.join(" - ");   // "a - b - c"
```

**Expected:** Results as commented

### 5.10 Array Methods - Reverse ✅

```javascript
let arr = [1, 2, 3, 4, 5];
arr.reverse();
// arr is [5, 4, 3, 2, 1]
```

**Expected:** arr = [5, 4, 3, 2, 1]

### 5.11 Array Methods - Sort 🟡 — `sort()` without comparator ❌ `[EASY]`; `sort((a,b) => ...)` with comparator ❌
`[MEDIUM]`

```javascript
let arr1 = [3, 1, 4, 1, 5, 9, 2, 6];
arr1.sort();
// arr1 is [1, 1, 2, 3, 4, 5, 6, 9]

let arr2 = [3, 1, 4, 1, 5, 9, 2, 6];
arr2.sort((a, b) => b - a);
// arr2 is [9, 6, 5, 4, 3, 2, 1, 1]

let arr3 = ["banana", "apple", "cherry"];
arr3.sort();
// arr3 is ["apple", "banana", "cherry"]
```

**Expected:** Results as commented

### 5.12 Array Methods - IndexOf/LastIndexOf 🟡 — `indexOf()` ✅; `lastIndexOf()` ❌ `[EASY]`

```javascript
let arr = [1, 2, 3, 2, 1];
let idx1 = arr.indexOf(2);       // 1
let idx2 = arr.lastIndexOf(2);   // 3
let idx3 = arr.indexOf(99);      // -1
```

**Expected:** Results as commented

### 5.13 Array Methods - Includes ✅

```javascript
let arr = [1, 2, 3, 4, 5];
let has3 = arr.includes(3);      // true
let has99 = arr.includes(99);    // false
```

**Expected:** Results as commented

### 5.14 Array Methods - Find/FindIndex ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [5, 12, 8, 130, 44];
let found = arr.find(x => x > 10);       // 12
let idx = arr.findIndex(x => x > 10);    // 1
let notFound = arr.find(x => x > 200);   // undefined
```

**Expected:** Results as commented

### 5.15 Array Methods - Filter ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [1, 2, 3, 4, 5, 6];
let evens = arr.filter(x => x % 2 === 0);
// evens is [2, 4, 6]

let greaterThan3 = arr.filter(x => x > 3);
// greaterThan3 is [4, 5, 6]
```

**Expected:** Results as commented

### 5.16 Array Methods - Map ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [1, 2, 3, 4, 5];
let doubled = arr.map(x => x * 2);
// doubled is [2, 4, 6, 8, 10]

let squared = arr.map(x => x * x);
// squared is [1, 4, 9, 16, 25]
```

**Expected:** Results as commented

### 5.17 Array Methods - Reduce ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [1, 2, 3, 4, 5];
let sum = arr.reduce((acc, x) => acc + x, 0);
// sum is 15

let product = arr.reduce((acc, x) => acc * x, 1);
// product is 120

let max = arr.reduce((acc, x) => x > acc ? x : acc);
// max is 5
```

**Expected:** Results as commented

### 5.18 Array Methods - ReduceRight ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = ["a", "b", "c"];
let str = arr.reduceRight((acc, x) => acc + x, "");
// str is "cba"
```

**Expected:** str = "cba"

### 5.19 Array Methods - Every/Some ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [2, 4, 6, 8];
let allEven = arr.every(x => x % 2 === 0);    // true
let hasOdd = arr.some(x => x % 2 !== 0);      // false

let arr2 = [1, 2, 3, 4];
let allPositive = arr2.every(x => x > 0);     // true
let hasNegative = arr2.some(x => x < 0);      // false
```

**Expected:** Results as commented

### 5.20 Array Methods - Flat ❌ `[MEDIUM]`

```javascript
let arr1 = [1, 2, [3, 4]];
let flat1 = arr1.flat();
// flat1 is [1, 2, 3, 4]

let arr2 = [1, [2, [3, [4]]]];
let flat2 = arr2.flat(2);
// flat2 is [1, 2, 3, [4]]

let flat3 = arr2.flat(Infinity);
// flat3 is [1, 2, 3, 4]
```

**Expected:** Results as commented

### 5.21 Array Methods - FlatMap ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [1, 2, 3];
let result = arr.flatMap(x => [x, x * 2]);
// result is [1, 2, 2, 4, 3, 6]
```

**Expected:** result = [1, 2, 2, 4, 3, 6]

### 5.22 Array Methods - ForEach ❌ `[MEDIUM]` — requires callback execution from stdlib context

```javascript
let arr = [1, 2, 3];
let sum = 0;
arr.forEach(x => sum += x);
// sum is 6

let results = [];
arr.forEach((x, i) => results.push(x + i));
// results is [1, 3, 5]
```

**Expected:** Results as commented

### 5.23 Spread Operator with Arrays ❌ `[HARD]`

```javascript
let arr1 = [1, 2, 3];
let arr2 = [...arr1];
// arr2 is [1, 2, 3]

let combined = [...arr1, 4, 5, 6];
// combined is [1, 2, 3, 4, 5, 6]

let arr3 = [7, 8];
let merged = [...arr1, ...arr3];
// merged is [1, 2, 3, 7, 8]
```

**Expected:** Results as commented

### 5.24 Destructuring Arrays ❌ `[HARD]`

```javascript
let [a, b, c] = [1, 2, 3];
// a is 1, b is 2, c is 3

let [first, ...rest] = [1, 2, 3, 4, 5];
// first is 1, rest is [2, 3, 4, 5]

let [x, , z] = [1, 2, 3];
// x is 1, z is 3

let [p, q = 10] = [5];
// p is 5, q is 10
```

**Expected:** Results as commented
