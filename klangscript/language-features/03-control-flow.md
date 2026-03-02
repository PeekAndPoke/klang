# KlangScript — Control Flow

### 3.1 If-Else Expression ✅

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

### 3.2 Switch Statements ❌ `[OUT_OF_SCOPE]` — intentionally NOT implemented; use `when`-expression instead

> **Design decision:** KlangScript will **not** implement JavaScript's `switch` statement.
> Fall-through semantics (`case 1: case 2:` without `break`) are a well-known error pit
> and a source of subtle bugs. Instead, KlangScript will implement a `when`-**expression**
> modelled after Kotlin's `when`, which has no fall-through, always returns a value, and
> requires exhaustive coverage. See section 3.7 below once implemented.

```javascript
// JS switch — NOT supported
switch (x) {
    case 1: ...
    case 2: ...   // fall-through hazard
    default: ...
}

// Future KlangScript when-expression (planned):
// let result = when (x) {
//     1 -> "one"
//     2 -> "two"
//     else -> "other"
// }
```

### 3.7 When-Expression ❌ `[MEDIUM]` — planned replacement for switch

```javascript
let day = 3;
let name = when (day) {
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    else -> "Other"
};
// name should be "Wednesday"

// Multiple values per branch
let type = when (x) {
    1, 2, 3 -> "small"
    4, 5, 6 -> "medium"
    else -> "large"
};
```

**Expected:** Results as commented

### 3.3 While Loops ✅

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

### 3.4 Do-While Loops ✅

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

### 3.5 For Loops ✅

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

### 3.6 Break and Continue ✅

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
