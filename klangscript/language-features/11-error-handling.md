# KlangScript — Error Handling

> **Status:** ❌ `[OUT_OF_SCOPE]` — `try`/`catch`/`throw` explicitly excluded; see `ref/language-design.md`

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
