# Compatibility

TruffleRuby aims to be fully compatible with the standard implementation of
Ruby, MRI, version 3.3.7, [including C extensions](#c-extension-compatibility).
TruffleRuby is still in development, so it is not 100% compatible yet.

TruffleRuby can run Rails and is compatible with many gems, including C extensions.
TruffleRuby [passes around 97% of ruby/spec](https://eregon.me/blog/2020/06/27/ruby-spec-compatibility-report.html),
more than any other alternative Ruby implementation.

Any incompatibility with MRI is considered a bug, except for rare cases detailed below.
If you find an incompatibility with MRI, please [report](https://github.com/truffleruby/truffleruby/issues) it.

TruffleRuby tries to match the behavior of MRI as much as possible.
In a few limited cases, TruffleRuby is deliberately incompatible with MRI in order to provide a greater capability.

## Identification

TruffleRuby defines these constants for identification:

- `RUBY_ENGINE` is `'truffleruby'`.
- `RUBY_VERSION` is the compatible MRI version.
- `RUBY_REVISION` is the full `git` commit hash used to build TruffleRuby (similar to MRI 2.7+).
- `RUBY_RELEASE_DATE` is the `git` commit date.
- `RUBY_PATCHLEVEL` is always zero.
- `RUBY_ENGINE_VERSION` is the TruffleRuby version, or `0.0-` and the Git commit hash if your build is not part of a TruffleRuby release.

In the C API, the preprocessor macro `TRUFFLERUBY` is defined, which can be checked with `#ifdef TRUFFLERUBY`.

## Ruby 3.x Features

Most features of Ruby 3.2 and earlier are supported in TruffleRuby.
However, some features are not yet implemented.
See the following issues for details:

* [Ruby 3.2 features](https://github.com/truffleruby/truffleruby/issues/3039)
* [Ruby 3.1 features](https://github.com/truffleruby/truffleruby/issues/2733)
* [Ruby 3.0 features](https://github.com/truffleruby/truffleruby/issues/2453)

## Features Entirely Missing

### Continuations and `callcc`

Continuations are obsolete in MRI, and Fibers are recommended instead.
Continuations and `callcc` are unlikely to ever be implemented in TruffleRuby, as their semantics fundamentally do not match the JVM architecture.

### Fork

You cannot `fork` the TruffleRuby interpreter.
The feature is unlikely to ever be supported when running on the JVM but could be supported in the future in the native configuration.
The correct and portable way to test if `fork` is available is:
```ruby
Process.respond_to?(:fork)
```

### Standard libraries

The following standard libraries are unsupported:

* `continuation`: obsolete in MRI
* `debug`: it relies on `RubyVM::InstructionSequence`, use the [VSCode extension](https://www.graalvm.org/tools/vscode/graalvm-extension/polyglot-runtime/#debugging-ruby) or [`--inspect`](tools.md) instead
* `io/console`: partially implemented
* `io/wait`: partially implemented
* `pty`: could be implemented in the future

TruffleRuby provides its own backend implementation for the `ffi` gem, similar to JRuby.
This should be completely transparent and behave the same as on MRI.
The implementation should be fairly complete and passes all the specs of the `ffi` gem except for some rarely-used corner cases.

### Internal MRI functionality

`RubyVM` is not intended for users and is not implemented.

## Features with Major Differences

### Threads run in parallel

In MRI, threads are scheduled concurrently but not in parallel.
In TruffleRuby threads are scheduled in parallel.
As in JRuby and Rubinius, you are responsible for correctly synchronising access to your own shared mutable data structures, and TruffleRuby will be responsible for correctly synchronising the state of the interpreter.

### Fibers do not have the same performance characteristics as in MRI

Most use cases of fibers rely on them being easy and cheap to start up, and with low memory overheads.
In TruffleRuby, fibers are currently implemented using operating system threads, so they have the same performance characteristics as Ruby threads.
This [will be addressed](https://medium.com/graalvm/bringing-fibers-to-truffleruby-1b5d2e258953) once the Loom project becomes stable and available in JVM releases.

### Some classes marked as internal will be different

MRI provides some classes that are described in the documentation as being available only on MRI (CRuby).
These classes are implemented if it is practical to do so, but this is not always the case. For example, `RubyVM` is not available.

### `Regexp`

`Regexp` instances are always immutable in TruffleRuby.
In CRuby 3.1, all literal `Regexp` are immutable, but non-literal are still mutable.
This limitation means that one cannot define singleton methods on a Regexp instance, and cannot create instances of subclasses of Regexp on TruffleRuby.

## Features with Subtle Differences

### Command line switches

The `-y`, `--yydebug`, `--dump=`, and `--debug-frozen-string-literal` switches are ignored with a warning as they are unsupported development tools.

Programs passed in `-e` arguments with magic-comments must have an encoding that is UTF-8 or a subset of UTF-8, as the JVM has already decoded arguments by the time we get them.

The `--jit` option and the `jit` feature have no effect on TruffleRuby and warn. The GraalVM compiler is always used when available.

### Strings have a maximum bytesize of 2<sup>31</sup>-1

Ruby Strings are represented as a Java `byte[]`.
The JVM enforces a maximum array size of 2<sup>31</sup>-1 (by storing the size in a 32-bit signed `int`), and therefore Ruby Strings cannot be longer than 2<sup>31</sup>-1 bytes.
That is, Strings must be smaller than 2GB. This is the same restriction as JRuby.
A possible workaround could be to use natively-allocated strings, but it would be a large effort to support every Ruby String operation on native strings.

### Strings in UTF-16 and UTF-32 encoding

TruffleRuby does not support UTF-16 strings with an odd number of bytes. Similarly, with UTF-32 it needs to be a multiple of 4. This is necessary for optimizations, compression, invariants, etc.

### Threads detect interrupts at different points

TruffleRuby threads may detect that they have been interrupted at different points in the program compared to where they would on MRI.
In general, TruffleRuby seems to detect an interrupt sooner than MRI.
JRuby and Rubinius are also different from MRI; the behavior is not documented in MRI, and it is likely to change between MRI versions, so it is not recommended to depend on interrupt points.

### Polyglot standard I/O streams

If you use standard I/O streams provided by the Polyglot engine, via the experimental `--polyglot-stdio` option, reads and writes to file descriptors 0, 1, and 2 will be redirected to these streams.
That means that other I/O operations on these file descriptors, such as `isatty`, may not be relevant for where these streams actually end up, and operations like `dup` may lose the connection to the polyglot stream.
For example, if you `$stdout.reopen`, as some logging frameworks do, you will get the native standard-out, not the polyglot out.

Also, I/O buffer drains, writes on I/O objects with `sync` set, and `write_nonblock` will not retry the write on `EAGAIN` and `EWOULDBLOCK`, as the streams do not provide a way to detect this.

### Error messages

Error message strings will sometimes differ from MRI, as these are not generally covered by the [Ruby Spec Suite](https://github.com/ruby/spec) or tests.

### Signals

First, `KILL` and `STOP` can never be trapped, per POSIX (`man 2 signal`).
Some signals are reserved on CRuby, and they are also reserved on TruffleRuby, because trapping those would cause all sorts of problems: `SEGV`, `BUS`, `ILL`, `FPE` and `VTALRM`.

When using the native configuration, TruffleRuby allows trapping all the same signals that MRI does.
Consequently, any signal handling code that runs on MRI can run on TruffleRuby without modification in the native configuration.

However, when run on the JVM, TruffleRuby is unable to trap `QUIT`, as this signal is reserved by the JVM.
In such a case `trap(:QUIT) {}` will raise an `ArgumentError`.
Any code that relies on being able to trap this signal will need to fall back to another available signal.

When TruffleRuby is run as part of a polyglot application, any signals that are handled by another language become unavailable for TruffleRuby to trap.

### GC statistics

TruffleRuby provides similar `GC.stat` statistics to MRI, but not all statistics are available, and some statistics may be approximations. Use `GC.stat.keys` to see which are provided with real or approximate values. Missing values will return `0`.

### Caller locations

Using `Kernel#caller_locations` or `Thread.each_caller_location` might contain engine specific location objects and/or
paths. This is expected and should be filtered in application code where necessary.

The enumerator returned by `Thread.to_enum(:each_caller_location)` is not supporting iteration with `.next`. In CRuby
this raises a `StopIteration`, while in TruffleRuby it iterates on an undetermined (related to where and how `.next` is
called) call stack. It is not recommended to use this in any circumstance (neither CRuby nor TruffleRuby).

## Features with Very Low Performance

### `ObjectSpace`

`ObjectSpace#each_object` is implemented but is fairly slow due to needing to iterate the whole heap and essentially doing the equivalent of a GC marking phase.
`ObjectSpace#trace_object_allocations_start` slows down all allocations, similar to the behavior on CRuby.
Using most methods on `ObjectSpace` will temporarily lower the performance of your program.
Using them in test cases and other similar 'offline' operations is fine, but you probably do not want to use them in the inner loop of your production application.

### `set_trace_func`

Using `set_trace_func` will temporarily lower the performance of your program.
As with `ObjectSpace`, it is recommended that you do not use this in the inner loop of your production application.

### Backtraces

Throwing exceptions and other operations which need to create a backtrace are in general slower than on MRI.
This is because TruffleRuby needs to undo optimizations that have been applied to run your Ruby code fast in order to recreate the backtrace entries.
It is not recommended to use exceptions for control flow on any implementation of Ruby anyway.

To help alleviate this problem, backtraces are automatically disabled in cases where we can detect that they will not be used.

## C Extension Compatibility

### Global Extension Lock

Native extensions are by default considered thread-unsafe for maximum compatibility with CRuby and use the global extension lock (unless `--cexts-lock=false` is used).

Extensions can mark themselves as thread-safe either by using `rb_ext_ractor_safe()` or `rb_ext_thread_safe()`.
Such extensions are then run by TruffleRuby without a global extension lock, i.e. in parallel.

See [Thread-Safe Extensions](thread-safe-extensions.md) for how to mark extensions as Ractor-safe or thread-safe.

### Identifiers may be macros or functions

Identifiers which are normally macros may be functions, functions may be macros, and global variables may be macros.
This may cause problems where they are used in a context which relies on a particular implementation (e.g., taking the
address of it, assigning to a function pointer variable, and using `defined()` to check if a macro exists).
These issues should all be considered bugs and be fixed.
Please report these cases.

### `rb_scan_args`

`rb_scan_args` only supports up to 10 pointers.

### `rb_funcall`

`rb_funcall` only supports up to 15 arguments.

### `mark` functions of `RDATA` and `RTYPEDDATA`

The `mark` functions of `RDATA` and `RTYPEDDATA` are not called during garbage collection, but called periodically.
The information about objects is cached as they are assigned to structs, and TruffleRuby periodically runs all `mark` functions when the cache has become full to represent those object relationships in a way that the garbage collector will
understand.
The process should behave identically to MRI.

## Compatibility with JRuby

### Ruby to Java interoperability

TruffleRuby does not support the same interoperability interface to Java as JRuby does.
TruffleRuby provides an [alternate polyglot API](polyglot.md) for interoperating with multiple languages, including Java, instead.

### Java to Ruby interop

Calling Ruby code from Java is supported by the
[GraalVM Polyglot API](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/package-summary.html).

### Java extensions

Using Java extensions written for JRuby is not supported.

## Features Not Yet Supported in Native Configuration

Running TruffleRuby in the native configuration is mostly the same as running
on the JVM. There are differences in resource management, as both VMs use
different garbage collectors, but functionality-wise, they are essentially on
par with one another.

## Java Interoperability With the Native Configuration

Java interoperability works in the native configuration but requires more setup.
By default, only some array classes are available in the image for Java interoperability.
You can add more classes by compiling a native image including TruffleRuby.
See [here](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md#build-native-images-from-polyglot-applications) for more details.

## Spec Completeness

"How many specs are there?" is not a question with an easy, precise answer. The
number of specs varies for the different versions of the Ruby language, different
platforms, and different versions of the specs.
The specs for the standard library and C extension API are also
very uneven and can give misleading results.

[This blog post](https://eregon.me/blog/2020/06/27/ruby-spec-compatibility-report.html)
summarizes how many specs TruffleRuby passes.
