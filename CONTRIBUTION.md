# Contributing to Slipstream

Thanks for your interest in contributing! Slipstream is a small, focused project and we want to keep it that way clean, fast, and maintainable. This guide should get you set up and help your contribution land smoothly.

## Quick Start

```bash
git clone https://github.com/Si6gma/slipstream.git
cd slipstream
./gradlew build
```

**Requirements**

- Java 25 or newer
- Gradle (wrapper included)

## Project Structure

This repo produces two artifacts:

| Module       | Path            | What it is                                                                                      |
| ------------ | --------------- | ----------------------------------------------------------------------------------------------- |
| Fabric mod   | `src/`          | Core mod physics, particles, config, mixins                                                     |
| Paper plugin | `paper-plugin/` | Serverside companion syncs config to Fabric clients and spawns particles for non-modded players |

Both share the same math utilities. Changes to `GroundEffectMath` affect both sides, so keep them in sync.

## Coding Standards

We don't enforce a formatter, but please match the style already in the codebase:

- **4 spaces** for indentation (no tabs)
- **Opening braces on the same line**
- `final` utility classes with a `private` no-arg constructor
- `@Unique` prefix for mixin fields (we use `ege$`)
- Descriptive variable names; single-letter is fine for math loops/vectors
- Javadoc for public API methods in `GroundEffectMath`
- Keep mixin methods concise; heavy logic belongs in helper classes

**Example:**

```java
public final class MyHelper {
    private MyHelper() {}

    /** What this does and why. */
    public static double computeSomething(double input) {
        return input * 2.0;
    }
}
```

## Testing

We use JUnit 5. Run tests with:

```bash
./gradlew test
```

- All math in `GroundEffectMath` must have unit tests. Cover edge cases (zero, max values, monotonic behavior).
- Tests live next to the code they test: `src/test/java/<package>/ClassNameTest.java`.
- Use descriptive test names: `methodName_condition_expectedResult`.

If you change physics behavior, make sure existing tests still pass and add new ones for the changed logic.

## Pull Request Process

1. **Fork** the repo and create a feature branch: `git checkout -b feature/short-description`
2. **Make your changes** keep the diff focused. One logical change per PR.
3. **Add tests** if you changed logic or math.
4. **Run the full build** locally: `./gradlew build`
5. **Update docs** (`README.md`, `CONTRIBUTION.md`, etc.) if the user-facing behavior changes.
6. **Open a PR** with a clear title and description. Reference any related issues.

### What makes a PR likely to merge

- Solves a single, well-described problem
- Includes tests for new logic
- Doesn't break existing behavior without a strong justification
- Follows the existing code style
- Doesn't balloon the scope (new features should be discussed in an issue first)

## Areas That Need Help

- **Paper plugin parity** the Paper plugin currently only handles config sync and basic particles. Feature parity with the Fabric mod is a long-term goal.
- **Performance** the mixin runs every tick for every fall-flying entity. Profile before and after if you touch hot paths.
- **Documentation** clearer inline comments for the particle math are always welcome.

## Reporting Bugs

Open an issue with:

- Minecraft version and mod version
- Fabric or Paper (and their versions)
- Steps to reproduce
- Expected vs actual behavior
- Logs if relevant

## License

By contributing, you agree that your code will be released under the [MIT License](LICENSE).
