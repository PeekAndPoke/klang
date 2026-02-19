# Klang Project - Claude Context

## Testing Guidelines

### Running Tests

**IMPORTANT**: This is a Kotlin Multiplatform project with both JVM and JS tests.

#### For strudel module tests:

```bash
# Run JVM tests (PREFERRED for faster feedback)
./gradlew :strudel:jvmTest

# Run specific test class (NO QUOTES around test name)
./gradlew :strudel:jvmTest --tests LangBpmSpec

# Run JS tests (slower, use when testing browser-specific code)
./gradlew :strudel:jsTest
```

**Key Points**:

- ✅ Use `jvmTest` for most test runs (faster)
- ✅ Do NOT put quotes around test class names: `--tests LangBpmSpec` (correct)
- ❌ Do NOT use: `--tests "LangBpmSpec"` or `--tests "*.LangBpmSpec"` (incorrect)
- ✅ Use `jsTest` only when testing browser/JS-specific functionality

### Project Structure

This is a Kotlin Multiplatform project with multiple modules:

- `strudel` - Pattern language implementation (re-implementation of strudel.cc)
- `klangscript` - Live coding language parser
- `audio_be` - Audio backend (synthesis engine)
- `audio_bridge` - Bridge between frontend and audio worklet
- `audio_fe` - Audio frontend
- `klang` - Main application module
- `common` - Shared utilities
- `tones` - Musical note/tone utilities

### Module-Specific Context

See module-specific CLAUDE.md files for detailed information:

- `/strudel/CLAUDE.md` - Strudel pattern language internals
- `/klangscript/CLAUDE.md` - KlangScript parser implementation

## Build Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :strudel:build

# Run the main application
./gradlew :klang:jsRun

# Clean build
./gradlew clean build
```

## Development Notes

- **Language**: Kotlin with Kotlin/JS for frontend, Kotlin/JVM for tests
- **Testing**: Use Kotest for tests (StringSpec style)
- **DSL Pattern**: Uses delegate-based DSL registration (`by dslObject`, `by dslFunction`, etc.)
