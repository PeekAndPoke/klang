# KlangScript Expected Features Test Suite

This document contains code snippets designed to test and validate KlangScript's JavaScript-like capabilities (excluding
classes and inheritance).

## 1. Basic Literals and Variables

### 1.1 Number Literals

```javascript
let a = 42;
let b = 3.14159;
let c = -17;
let d = 0;
let e = 1e6;
let f = 2.5e-3;
```

**Expected:** All assignments succeed. Values: 42, 3.14159, -17, 0, 1000000, 0.0025

### 1.2 String Literals

```javascript
let s1 = "hello";
let s2 = 'world';
let s3 = "it's";
let s4 = 'say "hi"';
let s5 = "";
```

**Expected:** All assignments succeed. Values as shown.

### 1.3 Boolean Literals

```javascript
let t = true;
let f = false;
```

**Expected:** t is true, f is false

### 1.4 Null and Undefined

```javascript
let n = null;
let u = undefined;
let v;
```

**Expected:** n is null, u and v are undefined

### 1.5 Variable Reassignment

```javascript
let x = 10;
x = 20;
x = "changed";
```

**Expected:** x is "changed"

## 2. Basic Operators

### 2.1 Arithmetic Operators

```javascript
let add = 5 + 3;        // 8
let sub = 10 - 4;       // 6
let mul = 6 * 7;        // 42
let div = 20 / 4;       // 5
let mod = 17 % 5;       // 2
let exp = 2 ** 8;       // 256
```

**Expected:** Results as commented

### 2.2 Unary Operators

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

### 2.3 Comparison Operators

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

### 2.4 Logical Operators

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

### 2.5 String Concatenation

```javascript
let concat1 = "hello" + " " + "world";  // "hello world"
let concat2 = "number: " + 42;          // "number: 42"
let concat3 = 1 + 2 + " items";         // "3 items"
let concat4 = "items: " + (1 + 2);      // "items: 3"
```

**Expected:** Results as commented

### 2.6 Ternary Operator

```javascript
let result1 = true ? "yes" : "no";      // "yes"
let result2 = false ? "yes" : "no";     // "no"
let result3 = 5 > 3 ? "bigger" : "smaller"; // "bigger"
let nested = true ? (false ? "a" : "b") : "c"; // "b"
```

**Expected:** Results as commented

## 3. Control Flow

### 3.1 If-Else Statements

```javascript
let x = 10;
let result;
if (x > 5) {
    result = "big";
}
// result should be "big"

if (x < 5) {
    result = "small";
} else {
    result = "not small";
}
// result should be "not small"

if (x < 5) {
    result = "a";
} else if (x < 15) {
    result = "b";
} else {
    result = "c";
}
// result should be "b"
```

**Expected:** Results as commented

### 3.2 Switch Statements

```javascript
let day = 3;
let name;
switch (day) {
    case 1:
        name = "Monday";
        break;
    case 2:
        name = "Tuesday";
        break;
    case 3:
        name = "Wednesday";
        break;
    default:
        name = "Other";
}
// name should be "Wednesday"

let x = 2;
let result = "";
switch (x) {
    case 1:
        result += "one";
    case 2:
        result += "two";
    case 3:
        result += "three";
        break;
    default:
        result += "default";
}
// result should be "twothree" (fall-through)
```

**Expected:** Results as commented

### 3.3 While Loops

```javascript
let i = 0;
let sum = 0;
while (i < 5) {
    sum += i;
    i++;
}
// sum should be 10, i should be 5
```

**Expected:** sum = 10, i = 5

### 3.4 Do-While Loops

```javascript
let i = 0;
let sum = 0;
do {
    sum += i;
    i++;
} while (i < 5);
// sum should be 10, i should be 5

let j = 10;
do {
    j++;
} while (j < 5);
// j should be 11 (executes once even if condition is false)
```

**Expected:** Results as commented

### 3.5 For Loops

```javascript
let sum = 0;
for (let i = 0; i < 5; i++) {
    sum += i;
}
// sum should be 10

let product = 1;
for (let i = 1; i <= 5; i++) {
    product *= i;
}
// product should be 120

for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        // nested loop test
    }
}
// should complete without error
```

**Expected:** Results as commented

### 3.6 Break and Continue

```javascript
let sum = 0;
for (let i = 0; i < 10; i++) {
    if (i === 5) break;
    sum += i;
}
// sum should be 10 (0+1+2+3+4)

let evenSum = 0;
for (let i = 0; i < 10; i++) {
    if (i % 2 !== 0) continue;
    evenSum += i;
}
// evenSum should be 20 (0+2+4+6+8)
```

**Expected:** Results as commented

## 4. Functions

### 4.1 Function Declarations

```javascript
function add(a, b) {
    return a + b;
}

let result = add(5, 3);  // 8
```

**Expected:** result = 8

### 4.2 Function Expressions

```javascript
let multiply = function (a, b) {
    return a * b;
};
let result = multiply(4, 5);  // 20
```

**Expected:** result = 20

### 4.3 Arrow Functions

```javascript
let square = (x) => x * x;
let result1 = square(5);  // 25

let add = (a, b) => a + b;
let result2 = add(3, 4);  // 7

let greet = () => "Hello";
let result3 = greet();  // "Hello"

let complex = (x) => {
    let y = x * 2;
    return y + 1;
};
let result4 = complex(5);  // 11
```

**Expected:** Results as commented

### 4.4 Default Parameters

```javascript
function greet(name = "World") {
    return "Hello, " + name;
}

let r1 = greet();           // "Hello, World"
let r2 = greet("Alice");    // "Hello, Alice"
```

**Expected:** Results as commented

### 4.5 Rest Parameters

```javascript
function sum(...numbers) {
    let total = 0;
    for (let n of numbers) {
        total += n;
    }
    return total;
}

let r1 = sum(1, 2, 3);           // 6
let r2 = sum(1, 2, 3, 4, 5);     // 15
```

**Expected:** Results as commented

### 4.6 Closures

```javascript
function makeCounter() {
    let count = 0;
    return function () {
        count++;
        return count;
    };
}

let counter = makeCounter();
let r1 = counter();  // 1
let r2 = counter();  // 2
let r3 = counter();  // 3
```

**Expected:** Results as commented

### 4.7 Immediately Invoked Function Expressions (IIFE)

```javascript
let result = (function () {
    return 42;
})();
// result should be 42

let calculated = (function (x, y) {
    return x + y;
})(5, 3);
// calculated should be 8
```

**Expected:** Results as commented

### 4.8 Recursive Functions

```javascript
function factorial(n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

let r1 = factorial(5);  // 120

function fibonacci(n) {
    if (n <= 1) return n;
    return fibonacci(n - 1) + fibonacci(n - 2);
}

let r2 = fibonacci(7);  // 13
```

**Expected:** Results as commented

### 4.9 Higher-Order Functions

```javascript
function applyOperation(a, b, operation) {
    return operation(a, b);
}

let add = (x, y) => x + y;
let multiply = (x, y) => x * y;
let r1 = applyOperation(5, 3, add);       // 8
let r2 = applyOperation(5, 3, multiply);  // 15
```

**Expected:** Results as commented

## 5. Arrays

### 5.1 Array Literals

```javascript
let arr1 = [1, 2, 3, 4, 5];
let arr2 = ["a", "b", "c"];
let arr3 = [1, "two", true, null];
let arr4 = [];
let nested = [[1, 2], [3, 4], [5, 6]];
```

**Expected:** All arrays created successfully

### 5.2 Array Access

```javascript
let arr = [10, 20, 30, 40];
let first = arr[0];      // 10
let last = arr[3];       // 40
arr[1] = 25;
// arr is now [10, 25, 30, 40]
```

**Expected:** Results as commented

### 5.3 Array Length

```javascript
let arr = [1, 2, 3, 4, 5];
let len = arr.length;  // 5

arr.length = 3;
// arr is now [1, 2, 3]

arr.length = 5;
// arr is now [1, 2, 3, undefined, undefined]
```

**Expected:** Results as commented

### 5.4 Array Methods - Push/Pop

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

### 5.5 Array Methods - Shift/Unshift

```javascript
let arr = [1, 2, 3];
arr.unshift(0);
// arr is [0, 1, 2, 3]

let first = arr.shift();
// first is 0, arr is [1, 2, 3]
```

**Expected:** Results as commented

### 5.6 Array Methods - Slice

```javascript
let arr = [1, 2, 3, 4, 5];
let slice1 = arr.slice(1, 3);    // [2, 3]
let slice2 = arr.slice(2);       // [3, 4, 5]
let slice3 = arr.slice(-2);      // [4, 5]
let slice4 = arr.slice(1, -1);   // [2, 3, 4]
```

**Expected:** Results as commented

### 5.7 Array Methods - Splice

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

### 5.8 Array Methods - Concat

```javascript
let arr1 = [1, 2];
let arr2 = [3, 4];
let combined = arr1.concat(arr2);
// combined is [1, 2, 3, 4]

let multi = [1].concat([2, 3], [4, 5], 6);
// multi is [1, 2, 3, 4, 5, 6]
```

**Expected:** Results as commented

### 5.9 Array Methods - Join

```javascript
let arr = ["a", "b", "c"];
let joined1 = arr.join();        // "a,b,c"
let joined2 = arr.join("");      // "abc"
let joined3 = arr.join(" - ");   // "a - b - c"
```

**Expected:** Results as commented

### 5.10 Array Methods - Reverse

```javascript
let arr = [1, 2, 3, 4, 5];
arr.reverse();
// arr is [5, 4, 3, 2, 1]
```

**Expected:** arr = [5, 4, 3, 2, 1]

### 5.11 Array Methods - Sort

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

### 5.12 Array Methods - IndexOf/LastIndexOf

```javascript
let arr = [1, 2, 3, 2, 1];
let idx1 = arr.indexOf(2);       // 1
let idx2 = arr.lastIndexOf(2);   // 3
let idx3 = arr.indexOf(99);      // -1
```

**Expected:** Results as commented

### 5.13 Array Methods - Includes

```javascript
let arr = [1, 2, 3, 4, 5];
let has3 = arr.includes(3);      // true
let has99 = arr.includes(99);    // false
```

**Expected:** Results as commented

### 5.14 Array Methods - Find/FindIndex

```javascript
let arr = [5, 12, 8, 130, 44];
let found = arr.find(x => x > 10);       // 12
let idx = arr.findIndex(x => x > 10);    // 1
let notFound = arr.find(x => x > 200);   // undefined
```

**Expected:** Results as commented

### 5.15 Array Methods - Filter

```javascript
let arr = [1, 2, 3, 4, 5, 6];
let evens = arr.filter(x => x % 2 === 0);
// evens is [2, 4, 6]

let greaterThan3 = arr.filter(x => x > 3);
// greaterThan3 is [4, 5, 6]
```

**Expected:** Results as commented

### 5.16 Array Methods - Map

```javascript
let arr = [1, 2, 3, 4, 5];
let doubled = arr.map(x => x * 2);
// doubled is [2, 4, 6, 8, 10]

let squared = arr.map(x => x * x);
// squared is [1, 4, 9, 16, 25]
```

**Expected:** Results as commented

### 5.17 Array Methods - Reduce

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

### 5.18 Array Methods - ReduceRight

```javascript
let arr = ["a", "b", "c"];
let str = arr.reduceRight((acc, x) => acc + x, "");
// str is "cba"
```

**Expected:** str = "cba"

### 5.19 Array Methods - Every/Some

```javascript
let arr = [2, 4, 6, 8];
let allEven = arr.every(x => x % 2 === 0);    // true
let hasOdd = arr.some(x => x % 2 !== 0);      // false

let arr2 = [1, 2, 3, 4];
let allPositive = arr2.every(x => x > 0);     // true
let hasNegative = arr2.some(x => x < 0);      // false
```

**Expected:** Results as commented

### 5.20 Array Methods - Flat

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

### 5.21 Array Methods - FlatMap

```javascript
let arr = [1, 2, 3];
let result = arr.flatMap(x => [x, x * 2]);
// result is [1, 2, 2, 4, 3, 6]
```

**Expected:** result = [1, 2, 2, 4, 3, 6]

### 5.22 Array Methods - ForEach

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

### 5.23 Spread Operator with Arrays

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

### 5.24 Destructuring Arrays

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

## 6. Objects

### 6.1 Object Literals

```javascript
let obj1 = {a: 1, b: 2, c: 3};
let obj2 = {name: "Alice", age: 30};
let obj3 = {};
let nested = {a: 1, b: {c: 2, d: 3}};
```

**Expected:** All objects created successfully

### 6.2 Object Property Access

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

### 6.3 Object Property Addition/Deletion

```javascript
let obj = {a: 1};
obj.b = 2;
obj["c"] = 3;
// obj is { a: 1, b: 2, c: 3 }

delete obj.b;
// obj is { a: 1, c: 3 }
```

**Expected:** Results as commented

### 6.4 Object Methods

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

### 6.5 Object Method Shorthand

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

### 6.6 Computed Property Names

```javascript
let key = "dynamic";
let obj = {
    [key]: "value",
    ["computed" + "Key"]: 42
};
// obj is { dynamic: "value", computedKey: 42 }
```

**Expected:** obj = { dynamic: "value", computedKey: 42 }

### 6.7 Property Shorthand

```javascript
let name = "Alice";
let age = 30;
let obj = {name, age};
// obj is { name: "Alice", age: 30 }
```

**Expected:** obj = { name: "Alice", age: 30 }

### 6.8 Object Destructuring

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

### 6.9 Nested Destructuring

```javascript
let obj = {a: 1, b: {c: 2, d: 3}};
let {b: {c, d}} = obj;
// c is 2, d is 3
```

**Expected:** c = 2, d = 3

### 6.10 Rest Properties in Destructuring

```javascript
let obj = {a: 1, b: 2, c: 3, d: 4};
let {a, ...rest} = obj;
// a is 1, rest is { b: 2, c: 3, d: 4 }
```

**Expected:** Results as commented

### 6.11 Spread Operator with Objects

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

### 6.12 Object.keys()

```javascript
let obj = {a: 1, b: 2, c: 3};
let keys = Object.keys(obj);
// keys is ["a", "b", "c"]
```

**Expected:** keys = ["a", "b", "c"]

### 6.13 Object.values()

```javascript
let obj = {a: 1, b: 2, c: 3};
let values = Object.values(obj);
// values is [1, 2, 3]
```

**Expected:** values = [1, 2, 3]

### 6.14 Object.entries()

```javascript
let obj = {a: 1, b: 2, c: 3};
let entries = Object.entries(obj);
// entries is [["a", 1], ["b", 2], ["c", 3]]
```

**Expected:** entries = [["a", 1], ["b", 2], ["c", 3]]

### 6.15 Object.assign()

```javascript
let target = {a: 1};
let source = {b: 2, c: 3};
Object.assign(target, source);
// target is { a: 1, b: 2, c: 3 }

let merged = Object.assign({}, {a: 1}, {b: 2}, {c: 3});
// merged is { a: 1, b: 2, c: 3 }
```

**Expected:** Results as commented

### 6.16 Object.hasOwnProperty()

```javascript
let obj = {a: 1, b: 2};
let has_a = obj.hasOwnProperty("a");    // true
let has_c = obj.hasOwnProperty("c");    // false
```

**Expected:** Results as commented

### 6.17 in Operator

```javascript
let obj = {a: 1, b: 2};
let has_a = "a" in obj;      // true
let has_c = "c" in obj;      // false
```

**Expected:** Results as commented

### 6.18 for...in Loop

```javascript
let obj = {a: 1, b: 2, c: 3};
let keys = [];
for (let key in obj) {
    keys.push(key);
}
// keys is ["a", "b", "c"]
```

**Expected:** keys = ["a", "b", "c"]

### 6.19 for...of Loop with Arrays

```javascript
let arr = [10, 20, 30];
let sum = 0;
for (let value of arr) {
    sum += value;
}
// sum is 60
```

**Expected:** sum = 60

### 6.20 Object with Functions

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

## 7. String Methods

### 7.1 String Length

```javascript
let str = "hello";
let len = str.length;  // 5
```

**Expected:** len = 5

### 7.2 String charAt/charCodeAt

```javascript
let str = "hello";
let char = str.charAt(1);       // "e"
let code = str.charCodeAt(1);   // 101
```

**Expected:** Results as commented

### 7.3 String indexOf/lastIndexOf

```javascript
let str = "hello world";
let idx1 = str.indexOf("o");        // 4
let idx2 = str.lastIndexOf("o");    // 7
let idx3 = str.indexOf("x");        // -1
```

**Expected:** Results as commented

### 7.4 String slice/substring/substr

```javascript
let str = "hello world";
let slice1 = str.slice(0, 5);        // "hello"
let slice2 = str.slice(6);           // "world"
let slice3 = str.slice(-5);          // "world"

let sub1 = str.substring(0, 5);      // "hello"
let sub2 = str.substring(6);         // "world"

let sub3 = str.substr(0, 5);         // "hello"
let sub4 = str.substr(6, 5);         // "world"
```

**Expected:** Results as commented

### 7.5 String toLowerCase/toUpperCase

```javascript
let str = "Hello World";
let lower = str.toLowerCase();  // "hello world"
let upper = str.toUpperCase();  // "HELLO WORLD"
```

**Expected:** Results as commented

### 7.6 String trim

```javascript
let str = "  hello  ";
let trimmed = str.trim();          // "hello"
let trimStart = str.trimStart();   // "hello  "
let trimEnd = str.trimEnd();       // "  hello"
```

**Expected:** Results as commented

### 7.7 String split

```javascript
let str = "a,b,c,d";
let arr1 = str.split(",");         // ["a", "b", "c", "d"]

let str2 = "hello";
let arr2 = str2.split("");         // ["h", "e", "l", "l", "o"]

let str3 = "a,b,c";
let arr3 = str3.split(",", 2);     // ["a", "b"]
```

**Expected:** Results as commented

### 7.8 String replace

```javascript
let str = "hello world";
let r1 = str.replace("world", "there");    // "hello there"
let r2 = str.replace("o", "0");            // "hell0 world" (first only)
```

**Expected:** Results as commented

### 7.9 String repeat

```javascript
let str = "ha";
let repeated = str.repeat(3);  // "hahaha"
```

**Expected:** repeated = "hahaha"

### 7.10 String startsWith/endsWith

```javascript
let str = "hello world";
let starts = str.startsWith("hello");   // true
let ends = str.endsWith("world");       // true
let starts2 = str.startsWith("world");  // false
```

**Expected:** Results as commented

### 7.11 String includes

```javascript
let str = "hello world";
let has = str.includes("llo");     // true
let has2 = str.includes("xyz");    // false
```

**Expected:** Results as commented

### 7.12 String padStart/padEnd

```javascript
let str = "5";
let padded1 = str.padStart(3, "0");  // "005"
let padded2 = str.padEnd(3, "0");    // "500"
```

**Expected:** Results as commented

### 7.13 Template Literals

```javascript
let name = "Alice";
let age = 30;
let msg = `Hello, ${name}! You are ${age} years old.`;
// msg is "Hello, Alice! You are 30 years old."

let calc = `2 + 2 = ${2 + 2}`;
// calc is "2 + 2 = 4"
```

**Expected:** Results as commented

### 7.14 Multi-line Template Literals

```javascript
let multi = `Line 1
Line 2
Line 3`;
// multi is "Line 1\nLine 2\nLine 3"
```

**Expected:** multi contains newlines

### 7.15 Tagged Template Literals

```javascript
function tag(strings, ...values) {
    return strings[0] + values[0] + strings[1] + values[1] + strings[2];
}

let result = tag`Hello ${5} World ${10}!`;
// result is "Hello 5 World 10!"
```

**Expected:** result = "Hello 5 World 10!"

## 8. Type Coercion and Truthiness

### 8.1 Truthy/Falsy Values

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

### 8.2 Type Conversions

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

### 8.3 parseInt/parseFloat

```javascript
let i1 = parseInt("42");           // 42
let i2 = parseInt("42.5");         // 42
let i3 = parseInt("42px");         // 42
let i4 = parseInt("hello");        // NaN

let f1 = parseFloat("3.14");       // 3.14
let f2 = parseFloat("3.14abc");    // 3.14
```

**Expected:** Results as commented

### 8.4 isNaN/isFinite

```javascript
let n1 = isNaN(NaN);           // true
let n2 = isNaN(42);            // false
let n3 = isNaN("hello");       // true

let f1 = isFinite(42);         // true
let f2 = isFinite(Infinity);   // false
let f3 = isFinite(NaN);        // false
```

**Expected:** Results as commented

## 9. Regular Expressions

### 9.1 RegExp Literals

```javascript
let re1 = /hello/;
let re2 = /[0-9]+/;
let re3 = /\d{3}-\d{3}-\d{4}/;
```

**Expected:** All regex created successfully

### 9.2 RegExp Constructor

```javascript
let re1 = new RegExp("hello");
let re2 = new RegExp("[0-9]+", "g");
```

**Expected:** All regex created successfully

### 9.3 RegExp test()

```javascript
let re = /hello/;
let t1 = re.test("hello world");   // true
let t2 = re.test("goodbye");       // false
```

**Expected:** Results as commented

### 9.4 RegExp exec()

```javascript
let re = /\d+/;
let match = re.exec("abc123def");
// match is ["123"] with index 3
```

**Expected:** match[0] = "123", match.index = 3

### 9.5 String match()

```javascript
let str = "The year is 2024";
let match1 = str.match(/\d+/);
// match1 is ["2024"]

let match2 = str.match(/\d/g);
// match2 is ["2", "0", "2", "4"]
```

**Expected:** Results as commented

### 9.6 String search()

```javascript
let str = "hello world";
let idx = str.search(/world/);  // 6
```

**Expected:** idx = 6

### 9.7 String replace() with RegExp

```javascript
let str = "hello world";
let r1 = str.replace(/world/, "there");    // "hello there"
let r2 = str.replace(/o/g, "0");           // "hell0 w0rld"
```

**Expected:** Results as commented

### 9.8 RegExp Flags

```javascript
let str = "Hello World";
let re_i = /hello/i;  // case insensitive
let t1 = re_i.test(str);  // true

let str2 = "a1 b2 c3";
let re_g = /\d/g;  // global
let matches = str2.match(re_g);
// matches is ["1", "2", "3"]
```

**Expected:** Results as commented

### 9.9 RegExp Groups

```javascript
let re = /(\d{3})-(\d{3})-(\d{4})/;
let match = "Call 555-123-4567".match(re);
// match[0] is "555-123-4567"
// match[1] is "555"
// match[2] is "123"
// match[3] is "4567"
```

**Expected:** Results as commented

## 10. Math Object

### 10.1 Math Constants

```javascript
let pi = Math.PI;       // ~3.14159
let e = Math.E;         // ~2.71828
```

**Expected:** Values approximately as shown

### 10.2 Math.abs/Math.sign

```javascript
let a1 = Math.abs(-5);      // 5
let a2 = Math.abs(5);       // 5

let s1 = Math.sign(-5);     // -1
let s2 = Math.sign(5);      // 1
let s3 = Math.sign(0);      // 0
```

**Expected:** Results as commented

### 10.3 Math.ceil/Math.floor/Math.round/Math.trunc

```javascript
let c = Math.ceil(4.3);      // 5
let f = Math.floor(4.7);     // 4
let r1 = Math.round(4.4);    // 4
let r2 = Math.round(4.5);    // 5
let t = Math.trunc(4.7);     // 4
```

**Expected:** Results as commented

### 10.4 Math.min/Math.max

```javascript
let min = Math.min(3, 1, 4, 1, 5);   // 1
let max = Math.max(3, 1, 4, 1, 5);   // 5
```

**Expected:** Results as commented

### 10.5 Math.pow/Math.sqrt

```javascript
let p1 = Math.pow(2, 3);     // 8
let p2 = Math.pow(5, 2);     // 25

let s1 = Math.sqrt(16);      // 4
let s2 = Math.sqrt(2);       // ~1.414
```

**Expected:** Results as commented

### 10.6 Math.random()

```javascript
let r = Math.random();
// r is a number between 0 (inclusive) and 1 (exclusive)

let randInt = Math.floor(Math.random() * 10);
// randInt is an integer from 0 to 9
```

**Expected:** Values in expected ranges

### 10.7 Trigonometric Functions

```javascript
let sin = Math.sin(Math.PI / 2);     // 1
let cos = Math.cos(Math.PI);         // -1
let tan = Math.tan(Math.PI / 4);     // ~1
```

**Expected:** Results approximately as commented

### 10.8 Math.log/Math.exp

```javascript
let log = Math.log(Math.E);      // 1
let exp = Math.exp(1);           // ~2.71828
```

**Expected:** Results approximately as commented

## 11. Error Handling

### 11.1 Try-Catch

```javascript
let result;
try {
    result = 10 / 2;
} catch (e) {
    result = -1;
}
// result is 5

try {
    throw new Error("test error");
    result = "no error";
} catch (e) {
    result = "caught";
}
// result is "caught"
```

**Expected:** Results as commented

### 11.2 Try-Catch-Finally

```javascript
let result = "";
try {
    result += "try";
} catch (e) {
    result += "catch";
} finally {
    result += "finally";
}
// result is "tryfinally"

result = "";
try {
    throw new Error();
    result += "try";
} catch (e) {
    result += "catch";
} finally {
    result += "finally";
}
// result is "catchfinally"
```

**Expected:** Results as commented

### 11.3 Throw Custom Errors

```javascript
try {
    throw "string error";
} catch (e) {
    // e is "string error"
}

try {
    throw {message: "object error", code: 42};
} catch (e) {
    // e.message is "object error", e.code is 42
}
```

**Expected:** Results as commented

### 11.4 Error Object

```javascript
try {
    throw new Error("Something went wrong");
} catch (e) {
    let msg = e.message;  // "Something went wrong"
}
```

**Expected:** msg = "Something went wrong"

## 12. Advanced Function Patterns

### 12.1 Function Hoisting

```javascript
let result = hoisted();

function hoisted() {
    return "works";
}

// result is "works"
```

**Expected:** result = "works"

### 12.2 Variable Hoisting with var

```javascript
function test() {
    console.log(x);  // undefined (not error)
    var x = 5;
    console.log(x);  // 5
}
```

**Expected:** First log is undefined, second is 5

### 12.3 Scope and Closures

```javascript
function outer() {
    let x = 10;

    function inner() {
        return x + 5;
    }

    return inner;
}

let fn = outer();
let result = fn();  // 15
```

**Expected:** result = 15

### 12.4 Closure with Multiple Functions

```javascript
function createCounter() {
    let count = 0;
    return {
        increment: () => ++count,
        decrement: () => --count,
        getCount: () => count
    };
}

let counter = createCounter();
counter.increment();  // 1
counter.increment();  // 2
counter.decrement();  // 1
let val = counter.getCount();  // 1
```

**Expected:** val = 1

### 12.5 Partial Application

```javascript
function multiply(a, b) {
    return a * b;
}

function partial(fn, a) {
    return function (b) {
        return fn(a, b);
    };
}

let double = partial(multiply, 2);
let result = double(5);  // 10
```

**Expected:** result = 10

### 12.6 Function Composition

```javascript
let add5 = x => x + 5;
let multiply2 = x => x * 2;
let compose = (f, g) => x => f(g(x));
let addThenMultiply = compose(multiply2, add5);
let result = addThenMultiply(10);  // (10 + 5) * 2 = 30
```

**Expected:** result = 30

### 12.7 Memoization

```javascript
function memoize(fn) {
    let cache = {};
    return function (n) {
        if (n in cache) {
            return cache[n];
        }
        let result = fn(n);
        cache[n] = result;
        return result;
    };
}

let slowFib = n => n <= 1 ? n : slowFib(n - 1) + slowFib(n - 2);
let fastFib = memoize(slowFib);
let result = fastFib(10);  // 55
```

**Expected:** result = 55

### 12.8 Currying

```javascript
function curry(fn) {
    return function curried(...args) {
        if (args.length >= fn.length) {
            return fn(...args);
        }
        return function (...nextArgs) {
            return curried(...args, ...nextArgs);
        };
    };
}

let add3 = (a, b, c) => a + b + c;
let curriedAdd = curry(add3);
let result1 = curriedAdd(1)(2)(3);      // 6
let result2 = curriedAdd(1, 2)(3);      // 6
let result3 = curriedAdd(1)(2, 3);      // 6
```

**Expected:** All results = 6

## 13. Promises and Async (if supported)

### 13.1 Promise Creation

```javascript
let p1 = new Promise((resolve, reject) => {
    resolve(42);
});

let p2 = Promise.resolve(100);
let p3 = Promise.reject("error");
```

**Expected:** Promises created successfully

### 13.2 Promise.then()

```javascript
let result;
Promise.resolve(42).then(value => {
    result = value * 2;
});
// result should eventually be 84
```

**Expected:** result = 84 (after async execution)

### 13.3 Promise.catch()

```javascript
let result;
Promise.reject("error").catch(err => {
    result = "caught: " + err;
});
// result should be "caught: error"
```

**Expected:** result = "caught: error"

### 13.4 Promise Chaining

```javascript
let result;
Promise.resolve(5)
    .then(x => x * 2)
    .then(x => x + 3)
    .then(x => result = x);
// result should be 13
```

**Expected:** result = 13

### 13.5 Promise.all()

```javascript
let result;
Promise.all([
    Promise.resolve(1),
    Promise.resolve(2),
    Promise.resolve(3)
]).then(values => {
    result = values;
});
// result should be [1, 2, 3]
```

**Expected:** result = [1, 2, 3]

### 13.6 Promise.race()

```javascript
let result;
Promise.race([
    Promise.resolve(1),
    Promise.resolve(2)
]).then(value => {
    result = value;
});
// result should be 1 (first to resolve)
```

**Expected:** result = 1

### 13.7 Async/Await

```javascript
async function test() {
    let x = await Promise.resolve(10);
    let y = await Promise.resolve(20);
    return x + y;
}

let result = await test();  // 30
```

**Expected:** result = 30

### 13.8 Async Error Handling

```javascript
async function test() {
    try {
        let x = await Promise.reject("error");
    } catch (e) {
        return "caught";
    }
}

let result = await test();  // "caught"
```

**Expected:** result = "caught"

## 14. JSON

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

## 15. Sets and Maps

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

## 16. Symbol (if supported)

### 16.1 Symbol Creation

```javascript
let sym1 = Symbol();
let sym2 = Symbol("description");
let sym3 = Symbol("description");
let different = sym2 !== sym3;  // true (each symbol is unique)
```

**Expected:** different = true

### 16.2 Symbol as Object Key

```javascript
let sym = Symbol("key");
let obj = {
    [sym]: "value"
};
let val = obj[sym];  // "value"
```

**Expected:** val = "value"

### 16.3 Symbol.for()

```javascript
let sym1 = Symbol.for("shared");
let sym2 = Symbol.for("shared");
let same = sym1 === sym2;  // true (global symbol registry)
```

**Expected:** same = true

## 17. Generators (if supported)

### 17.1 Basic Generator

```javascript
function* generator() {
    yield 1;
    yield 2;
    yield 3;
}

let gen = generator();
let v1 = gen.next().value;  // 1
let v2 = gen.next().value;  // 2
let v3 = gen.next().value;  // 3
let done = gen.next().done; // true
```

**Expected:** Results as commented

### 17.2 Generator with Loop

```javascript
function* range(start, end) {
    for (let i = start; i < end; i++) {
        yield i;
    }
}

let arr = [];
for (let n of range(1, 5)) {
    arr.push(n);
}
// arr is [1, 2, 3, 4]
```

**Expected:** arr = [1, 2, 3, 4]

### 17.3 Generator Return Value

```javascript
function* gen() {
    yield 1;
    yield 2;
    return 3;
}

let g = gen();
let v1 = g.next();  // { value: 1, done: false }
let v2 = g.next();  // { value: 2, done: false }
let v3 = g.next();  // { value: 3, done: true }
```

**Expected:** Results as commented

## 18. Proxy and Reflect (if supported)

### 18.1 Basic Proxy

```javascript
let target = {x: 10};
let handler = {
    get: (obj, prop) => {
        return prop in obj ? obj[prop] : 42;
    }
};
let proxy = new Proxy(target, handler);
let v1 = proxy.x;  // 10
let v2 = proxy.y;  // 42
```

**Expected:** Results as commented

### 18.2 Proxy Set Trap

```javascript
let target = {};
let handler = {
    set: (obj, prop, value) => {
        if (typeof value === "number") {
            obj[prop] = value;
            return true;
        }
        return false;
    }
};
let proxy = new Proxy(target, handler);
proxy.x = 10;    // succeeds
proxy.y = "hi";  // fails silently
let hasX = "x" in target;  // true
let hasY = "y" in target;  // false
```

**Expected:** Results as commented

## 19. WeakMap and WeakSet (if supported)

### 19.1 WeakMap Basic Operations

```javascript
let wm = new WeakMap();
let obj = {};
wm.set(obj, "value");
let val = wm.get(obj);  // "value"
let has = wm.has(obj);  // true
wm.delete(obj);
let has2 = wm.has(obj); // false
```

**Expected:** Results as commented

### 19.2 WeakSet Basic Operations

```javascript
let ws = new WeakSet();
let obj = {};
ws.add(obj);
let has = ws.has(obj);  // true
ws.delete(obj);
let has2 = ws.has(obj); // false
```

**Expected:** Results as commented

## 20. Complex Edge Cases

### 20.1 Nested Ternary

```javascript
let x = 10;
let result = x < 5 ? "small" : x < 15 ? "medium" : x < 25 ? "large" : "huge";
// result is "medium"
```

**Expected:** result = "medium"

### 20.2 Short-Circuit Evaluation

```javascript
let x = 0;
let result1 = x && x.property;  // 0 (doesn't error)
let result2 = null || "default"; // "default"
let result3 = undefined ?? "fallback"; // "fallback" (nullish coalescing)
```

**Expected:** Results as commented

### 20.3 Optional Chaining

```javascript
let obj = {a: {b: {c: 42}}};
let v1 = obj?.a?.b?.c;          // 42
let v2 = obj?.x?.y?.z;          // undefined
let v3 = obj?.a?.b?.c?.d?.e;    // undefined
```

**Expected:** Results as commented

### 20.4 Nullish Coalescing

```javascript
let a = null ?? "default";      // "default"
let b = undefined ?? "default"; // "default"
let c = 0 ?? "default";         // 0
let d = "" ?? "default";        // ""
let e = false ?? "default";     // false
```

**Expected:** Results as commented

### 20.5 Complex Destructuring

```javascript
let obj = {
    a: 1,
    b: [2, 3, 4],
    c: {d: 5, e: 6}
};
let {a, b: [first, ...restB], c: {d}} = obj;
// a is 1, first is 2, restB is [3, 4], d is 5
```

**Expected:** Results as commented

### 20.6 Array/Object Mix Destructuring

```javascript
let data = [
    {name: "Alice", age: 30},
    {name: "Bob", age: 25}
];
let [{name: name1}, {age: age2}] = data;
// name1 is "Alice", age2 is 25
```

**Expected:** Results as commented

### 20.7 Swapping Variables

```javascript
let a = 1, b = 2;
[a, b] = [b, a];
// a is 2, b is 1
```

**Expected:** a = 2, b = 1

### 20.8 Function Parameter Destructuring

```javascript
function test({a, b = 10}) {
    return a + b;
}

let r1 = test({a: 5});        // 15
let r2 = test({a: 5, b: 20}); // 25
```

**Expected:** Results as commented

### 20.9 Complex Array Manipulations

```javascript
let arr = [1, 2, 3, 4, 5];
let result = arr
    .filter(x => x % 2 !== 0)
    .map(x => x * x)
    .reduce((acc, x) => acc + x, 0);
// result is 35 (1² + 3² + 5² = 1 + 9 + 25)
```

**Expected:** result = 35

### 20.10 Chained Object Operations

```javascript
let obj = Object.entries({a: 1, b: 2, c: 3})
    .filter(([key, value]) => value > 1)
    .map(([key, value]) => [key, value * 2])
    .reduce((acc, [key, value]) => {
        acc[key] = value;
        return acc;
    }, {});
// obj is { b: 4, c: 6 }
```

**Expected:** obj = { b: 4, c: 6 }

### 20.11 Comma Operator

```javascript
let x = (1, 2, 3);  // x is 3
let y = (a = 1, b = 2, a + b);  // y is 3
```

**Expected:** Results as commented

### 20.12 Void Operator

```javascript
let x = void 0;  // undefined
let y = void (1 + 2);  // undefined
```

**Expected:** x and y are undefined

### 20.13 typeof Operator

```javascript
let t1 = typeof 42;              // "number"
let t2 = typeof "hello";         // "string"
let t3 = typeof true;            // "boolean"
let t4 = typeof undefined;       // "undefined"
let t5 = typeof null;            // "object" (quirk)
let t6 = typeof {};              // "object"
let t7 = typeof [];              // "object"
let t8 = typeof function () {
};   // "function"
```

**Expected:** Results as commented

### 20.14 instanceof Operator

```javascript
let arr = [];
let isArray = arr instanceof Array;  // true

let obj = {};
let isObject = obj instanceof Object;  // true

let isArrayObject = arr instanceof Object;  // true
```

**Expected:** Results as commented

### 20.15 Array.isArray()

```javascript
let a1 = Array.isArray([]);          // true
let a2 = Array.isArray([1, 2, 3]);   // true
let a3 = Array.isArray({});          // false
let a4 = Array.isArray("array");     // false
```

**Expected:** Results as commented

### 20.16 Bitwise Operators

```javascript
let and = 5 & 3;     // 1 (0101 & 0011 = 0001)
let or = 5 | 3;      // 7 (0101 | 0011 = 0111)
let xor = 5 ^ 3;     // 6 (0101 ^ 0011 = 0110)
let not = ~5;        // -6 (inverts bits)
let lshift = 5 << 1; // 10 (0101 << 1 = 1010)
let rshift = 5 >> 1; // 2 (0101 >> 1 = 0010)
let urshift = -5 >>> 1; // large positive number
```

**Expected:** Results as commented

### 20.17 Compound Assignment

```javascript
let x = 10;
x += 5;   // 15
x -= 3;   // 12
x *= 2;   // 24
x /= 4;   // 6
x %= 4;   // 2
x **= 3;  // 8
```

**Expected:** x = 8

### 20.18 Logical Assignment

```javascript
let x = null;
x ||= 10;   // x is 10

let y = 5;
y &&= 20;   // y is 20

let z = null;
z ??= 30;   // z is 30
```

**Expected:** Results as commented

### 20.19 Label and Break

```javascript
let result = 0;
outer: for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        if (i === 1 && j === 1) break outer;
        result++;
    }
}
// result is 4 (breaks out of both loops)
```

**Expected:** result = 4

### 20.20 Label and Continue

```javascript
let result = 0;
outer: for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        if (j === 1) continue outer;
        result++;
    }
}
// result is 3 (continues outer loop)
```

**Expected:** result = 3

### 20.21 Array Holes

```javascript
let arr = [1, , 3];  // arr has a hole at index 1
let len = arr.length;  // 3
let hasOwn = arr.hasOwnProperty(1);  // false
let val = arr[1];  // undefined
```

**Expected:** Results as commented

### 20.22 Sparse Array Operations

```javascript
let arr = [1, , , 4];
let mapped = arr.map(x => x * 2);
// mapped is [2, empty, empty, 8]

let filtered = arr.filter(x => true);
// filtered is [1, 4] (holes removed)
```

**Expected:** Results as commented

### 20.23 Array Constructor

```javascript
let arr1 = new Array(3);
// arr1 is [empty, empty, empty] with length 3

let arr2 = new Array(1, 2, 3);
// arr2 is [1, 2, 3]

let arr3 = Array.of(3);
// arr3 is [3] (not array of length 3)
```

**Expected:** Results as commented

### 20.24 Object Property Enumeration

```javascript
let obj = {a: 1, b: 2};
Object.defineProperty(obj, "c", {
    value: 3,
    enumerable: false
});

let keys = Object.keys(obj);
// keys is ["a", "b"] (c is not enumerable)

let allKeys = Object.getOwnPropertyNames(obj);
// allKeys is ["a", "b", "c"]
```

**Expected:** Results as commented (if defineProperty supported)

### 20.25 Getter and Setter

```javascript
let obj = {
    _value: 0,
    get value() {
        return this._value;
    },
    set value(v) {
        this._value = v * 2;
    }
};
obj.value = 5;
let result = obj.value;  // 10
```

**Expected:** result = 10

### 20.26 Multiple Variable Declaration

```javascript
let a = 1, b = 2, c = 3;
// a is 1, b is 2, c is 3

let x, y = 10, z;
// x is undefined, y is 10, z is undefined
```

**Expected:** Results as commented

### 20.27 Global Object Access

```javascript
let global = (function () {
    return this;
})() || globalThis;
// should return global object
```

**Expected:** Returns global object

### 20.28 Number Edge Cases

```javascript
let inf = Infinity;
let negInf = -Infinity;
let nan = NaN;

let div1 = 1 / 0;        // Infinity
let div2 = -1 / 0;       // -Infinity
let div3 = 0 / 0;        // NaN

let nanCheck = NaN === NaN;  // false
```

**Expected:** Results as commented

### 20.29 String Edge Cases

```javascript
let empty = "";
let len = empty.length;  // 0

let char = "hello"[1];   // "e"
let outOfBounds = "hi"[10];  // undefined

let concat = "a" + "b" + "c";  // "abc"
```

**Expected:** Results as commented

### 20.30 Complex This Binding

```javascript
let obj = {
    value: 10,
    getValue: function () {
        return this.value;
    },
    getValueArrow: () => this.value
};

let fn = obj.getValue;
// fn() might return undefined (lost this binding)

let bound = obj.getValue.bind(obj);
let result = bound();  // 10
```

**Expected:** result = 10 for bound function

---

## Summary

This comprehensive test suite covers:

- **Basics**: Variables, literals, operators
- **Control Flow**: Conditionals, loops, breaks
- **Functions**: Declarations, expressions, arrows, closures, recursion
- **Data Structures**: Arrays, objects, sets, maps
- **String Operations**: Methods, templates, manipulation
- **Advanced Features**: Destructuring, spread/rest, optional chaining
- **Error Handling**: Try-catch-finally, throwing errors
- **Functional Programming**: Higher-order functions, composition, currying
- **Type System**: Coercion, typeof, instanceof
- **Regular Expressions**: Patterns, matching, replacing
- **Math Operations**: Constants, functions, trigonometry
- **JSON**: Stringify, parse
- **Edge Cases**: Bitwise ops, labels, sparse arrays, NaN/Infinity

Each snippet includes expected behavior to validate correctness. Start with section 1 and progressively test more
complex features!
