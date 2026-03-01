# KlangScript — Regular Expressions

> **Status:** ❌ `[OUT_OF_SCOPE]` — RegExp is a JS stdlib object; not planned for KlangScript core

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
