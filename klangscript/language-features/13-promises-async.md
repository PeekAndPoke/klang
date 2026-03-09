# KlangScript — Promises and Async

> **Status:** ❌ `[OUT_OF_SCOPE]` — explicitly excluded; see `ref/language-design.md`

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
