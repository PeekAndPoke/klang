# KlangScript — Control Flow

### 3.1 If-Else Statements ❌ `[MEDIUM]`

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

### 3.2 Switch Statements ❌ `[MEDIUM]`

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

### 3.3 While Loops ❌ `[MEDIUM]`

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

### 3.4 Do-While Loops ❌ `[MEDIUM]`

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

### 3.5 For Loops ❌ `[MEDIUM]`

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

### 3.6 Break and Continue ❌ `[MEDIUM]` — depends on loops (3.3–3.5)

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
