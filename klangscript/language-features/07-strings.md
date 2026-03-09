# KlangScript — String Methods

> **Design note — Kotlin-style stdlib (not JS-style):**
> String methods will be modelled after **Kotlin's `String` extension functions**, not
> JavaScript's `String.prototype`. This means: no mutating methods (strings are already
> immutable), sensible naming (`trimStart`/`trimEnd` instead of `trimLeft`/`trimRight`),
> and Kotlin-consistent return types. JS quirks like `substr` (deprecated in JS) and
> `localeCompare` will be omitted. The subset of methods that map cleanly to Kotlin
> equivalents will be prioritised.

### 7.1 String Length ❌ `[SEPARATE_STDLIB]` — `str.length()` exists in current stdlib;
`str.length` property form in dedicated String stdlib

```javascript
let str = "hello";
let len = str.length;  // 5
```

**Expected:** len = 5

### 7.2 String charAt/charCodeAt ❌ `[SEPARATE_STDLIB]` —
`charAt()` in current stdlib; dedicated String stdlib will cover both

```javascript
let str = "hello";
let char = str.charAt(1);       // "e"
let code = str.charCodeAt(1);   // 101
```

**Expected:** Results as commented

### 7.3 String indexOf/lastIndexOf ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "hello world";
let idx1 = str.indexOf("o");        // 4
let idx2 = str.lastIndexOf("o");    // 7
let idx3 = str.indexOf("x");        // -1
```

**Expected:** Results as commented

### 7.4 String slice/substring/substr ❌ `[SEPARATE_STDLIB]`

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

### 7.5 String toLowerCase/toUpperCase ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "Hello World";
let lower = str.toLowerCase();  // "hello world"
let upper = str.toUpperCase();  // "HELLO WORLD"
```

**Expected:** Results as commented

### 7.6 String trim ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "  hello  ";
let trimmed = str.trim();          // "hello"
let trimStart = str.trimStart();   // "hello  "
let trimEnd = str.trimEnd();       // "  hello"
```

**Expected:** Results as commented

### 7.7 String split ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "a,b,c,d";
let arr1 = str.split(",");         // ["a", "b", "c", "d"]

let str2 = "hello";
let arr2 = str2.split("");         // ["h", "e", "l", "l", "o"]

let str3 = "a,b,c";
let arr3 = str3.split(",", 2);     // ["a", "b"]
```

**Expected:** Results as commented

### 7.8 String replace ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "hello world";
let r1 = str.replace("world", "there");    // "hello there"
let r2 = str.replace("o", "0");            // "hell0 world" (first only)
```

**Expected:** Results as commented

### 7.9 String repeat ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "ha";
let repeated = str.repeat(3);  // "hahaha"
```

**Expected:** repeated = "hahaha"

### 7.10 String startsWith/endsWith ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "hello world";
let starts = str.startsWith("hello");   // true
let ends = str.endsWith("world");       // true
let starts2 = str.startsWith("world");  // false
```

**Expected:** Results as commented

### 7.11 String includes ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "hello world";
let has = str.includes("llo");     // true
let has2 = str.includes("xyz");    // false
```

**Expected:** Results as commented

### 7.12 String padStart/padEnd ❌ `[SEPARATE_STDLIB]`

```javascript
let str = "5";
let padded1 = str.padStart(3, "0");  // "005"
let padded2 = str.padEnd(3, "0");    // "500"
```

**Expected:** Results as commented

### 7.13 Template Literals (interpolation) ✅

```javascript
let name = "Alice";
let age = 30;
let msg = `Hello, ${name}! You are ${age} years old.`;
// msg is "Hello, Alice! You are 30 years old."

let calc = `2 + 2 = ${2 + 2}`;
// calc is "2 + 2 = 4"
```

**Expected:** Results as commented

### 7.14 Multi-line Template Literals ✅

```javascript
let multi = `Line 1
Line 2
Line 3`;
// multi is "Line 1\nLine 2\nLine 3"
```

**Expected:** multi contains newlines

### 7.15 Tagged Template Literals ❌ `[OUT_OF_SCOPE]` — too niche; standard interpolation (7.13) is sufficient

```javascript
function tag(strings, ...values) {
    return strings[0] + values[0] + strings[1] + values[1] + strings[2];
}

let result = tag`Hello ${5} World ${10}!`;
// result is "Hello 5 World 10!"
```

**Expected:** result = "Hello 5 World 10!"
