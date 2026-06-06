# AGENTS.md

## Overview

TruffleRuby is the GraalVM high-performance implementation of the Ruby programming language. It is built on the Truffle language implementation framework and the GraalVM compiler. The core library is implemented partly in Java and partly in Ruby.

## Repository Layout

| Directory | Purpose |
|-----------|---------|
| `src/main/java/org/truffleruby/` | Java source for the Ruby language implementation (nodes, core classes, C-API) |
| `src/main/ruby/truffleruby/core/` | Ruby source for the core library |
| `src/main/c/` | Native/C source (cext support, etc.) |
| `lib/truffle/` | TruffleRuby-specific Ruby standard library |
| `lib/cext/` | C extension support libraries |
| `lib/mri/` | Standard library files from MRI |
| `lib/gems/` | Default gems |
| `spec/ruby/` | Ruby Spec Suite (specs should pass on both TruffleRuby and MRI) |
| `spec/truffle/` | TruffleRuby-specific specs |
| `spec/tags/` | Tags marking known failures in specs |
| `test/mri/` | MRI test suite with exclusion files |
| `test/truffle/` | TruffleRuby integration tests |
| `tool/` | Developer tools and scripts (includes `jt.rb`) |
| `mx.truffleruby/` | mx build system configuration (`suite.py`, env files) |
| `doc/contributor/` | Contributor documentation |
| `doc/user/` | User-facing documentation |

## Build System

TruffleRuby uses the `mx` build tool (GraalVM ecosystem) but wraps it behind the **`bin/jt`** command-line tool. Always use `jt` rather than calling `mx` directly.

### Prerequisites

- Ruby >= 2.3 (system Ruby, for running `jt`)
- Python >= 3.8 (for `mx`)
- A JVMCI-enabled JDK (if `JAVA_HOME` is set but doesn't have JVMCI, unset it so `jt` downloads a suitable JDK)
- `make`, `gcc`/`g++`, `cmake`, `git`, `wget`

### Building

```bash
bin/jt build              # Default: JVM-only build (no native image, no Graal compiler)
bin/jt build --env jvm-ee # JVM with the Graal compiler
bin/jt build --env native-ee # Native Image build (slower to build)
```

The built distribution is placed respectively in `mxbuild/truffleruby-jvm`, `mxbuild/truffleruby-jvm-ee` and `mxbuild/truffleruby-native-ee`.

When only files under `src/main/ruby/truffleruby/core/` are modified, there is no need to rebuild, those are files are always read from the repository.

### Running TruffleRuby

```bash
bin/jt ruby <script.rb>         # Run a Ruby script with the default (jvm) build
bin/jt -u jvm-ce ruby ...    # Use a specific build configuration
```

## Linting

Run the fast lint checks (the most important subset):

```bash
bin/jt lint fast
```

Run the full lint suite:

```bash
bin/jt lint
```

Lint includes:
- Java formatting (Eclipse Code Formatter — CI checks this)
- RuboCop for Ruby files
- Custom C linter (`tool/c-linter.rb`)
- Various other checks

## Testing

### Fast Specs (primary quick-feedback test)

```bash
bin/jt test fast
```

This runs a curated subset of the Ruby Spec Suite and is the baseline test to run after any change.

### Running Specific Specs

```bash
bin/jt test spec/ruby/core/string/gsub_spec.rb
bin/jt test spec/ruby/core/array
```

### MRI Tests

```bash
bin/jt test mri test/mri/tests/test_string.rb
```

Exclusions are in `test/mri/failing.exclude` (whole files) and `test/mri/excludes/` (individual methods).

### Compiler Tests

```bash
bin/jt -u jvm-ee test compiler
```

### Running Specs on MRI for Comparison

```bash
bin/jt -u ruby test spec/ruby/core/string/gsub_spec.rb
```

## Working on Failing Specs

Use `jt untag` to run specs currently tagged as failing:

```bash
bin/jt untag spec/ruby/core/string
```

When a spec passes, the tag is automatically removed. Commit both the fix and the tag removal.

## Key Conventions

### ChangeLog

When making user-visible changes, add an entry to `CHANGELOG.md`:
```
* Description (#issue_number, @author).
```

See `CONTRIBUTING.md` for more details.

### Java Code

- Core methods are in `src/main/java/org/truffleruby/core/<ClassName>Nodes.java`
- Annotated with `@CoreModule` and `@CoreMethod`
- Formatted with Eclipse Code Formatter (CI enforced)
- See `doc/contributor/code-patterns.md` for code patterns

### Ruby Core Library

- Located in `src/main/ruby/truffleruby/core/`
- Uses `Primitive.<name>` calls to invoke Java-implemented primitives
- RuboCop checks apply (`.rubocop.yml`)

### Finding Method Implementations

1. Check `src/main/ruby/truffleruby/core/<class>.rb` for Ruby implementations
2. Check `src/main/java/org/truffleruby/core/<Class>Nodes.java` for Java implementations
3. Look for `@CoreMethod(names = "<method_name>")` annotations

## Common Pitfalls

- **JAVA_HOME**: If set to a JDK without JVMCI support, `jt build` will fail. Unset it and let `jt` download a suitable JDK via `jt install jvmci`.

## Useful Commands Reference

| Command | Description |
|---------|-------------|
| `bin/jt build` | Build TruffleRuby (default: jvm) |
| `bin/jt test fast` | Run fast spec subset |
| `bin/jt test spec/ruby/...` | Run specific spec file(s) |
| `bin/jt test mri test/mri/tests/...` | Run specific MRI test |
| `bin/jt lint fast` | Run fast lint checks |
| `bin/jt lint` | Run full lint suite |
| `bin/jt ruby <file>` | Run a Ruby file with TruffleRuby |
| `bin/jt untag <spec_path>` | Run and untag fixed specs |
| `bin/jt retag <test_path>` | Recompute tags for MRI tests |
| `bin/jt --help` | Show all available commands |

## Further Reading

- `doc/contributor/workflow.md` — Full contributor workflow
- `doc/contributor/how-to-guide.md` — How to implement features, add C API functions, etc.
- `doc/contributor/code-patterns.md` — Java code patterns and conventions
- `doc/contributor/cexts.md` — C extension implementation details
- `CONTRIBUTING.md` — Contribution guidelines and style
