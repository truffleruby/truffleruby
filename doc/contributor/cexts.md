# C Extensions

## Testing

There are various ways to test C extensions.
Here is a list going from testing single functions to testing Bundler.

```bash
jt test :capi
jt test :truffle_capi
jt test :library
jt test mri --all-sulong
jt test cexts
jt test bundle
```

Note: the last 2 currently require the gem test pack.
This only works for Oracle employees with access to Oracle's internal repositories.
Please open an issue if you would like to run those tests on your own.

## Benchmarking

To run C extension benchmarks, you first need to compile them.

```bash
jt cextc bench/chunky_png/oily_png
```

Then follow the instructions for benchmarking above, and then try:

```bash
USE_CEXTS=true TRUFFLERUBYOPT="--experimental-options --cexts-log-load" jt benchmark bench/chunky_png/chunky-color-r.rb --simple
```

These benchmarks have Ruby fallbacks, so we should carefully check that the
C extension is actually being used by looking for these log lines.

```
[ruby] INFO loading cext module ...
```

## Implementation

We run Ruby C extensions using Sulong, running any dynamically linked libraries
natively.

### Compilation

We compile C extensions using the standard `mkmf` tool, and `gcc`/`clang` compilers.

We pipe C source code through a pre-processor `lib/cext/preprocess.rb` before it
goes to `gcc`/`clang` to work around some limitations.

### API functions

Most API functions are defined in the C header file, the C implementation file,
and then either implemented as a call to a method on one of the arguments using
`polyglot_invoke` to do a foreign call from C into Ruby, or we implement the
function in Ruby in the `Truffle::CExt` module and use `polyglot_invoke` to call
that.

Some functions are re-implemented as macros, and some macros are re-implemented
as functions, where this makes sense.

### C Runtime Implementation Details

#### `VALUE`

`VALUE` is defined as `unsigned long` as on MRI. `VALUE`s represent either
`ValueWrapper` or `LLVM Pointer` type objects.

A `ValueWrapper` holds a Ruby object and can be converted to a native pointer.
Generally, all `VALUE`s should be unwrapped before sending to Ruby and wrapped
when returned from Ruby. Only `VALUE`s can be unwrapped and C int/long can be
passed directly as Ruby Integers. Wrapping results from Ruby may be skipped when
the desired result is a native value like C int/long and the value returned from
Ruby has the corresponding type. Ruby values are converted to native values
using a `polyglot_as*` method.

`rb_tr_wrap`

Wraps a Ruby value in a ValueWrapper.

`rb_tr_unwrap`

`ValueWrapper`, `Pointer` or `Long` values are unwrapped to any Ruby value.

`RUBY_INVOKE(RECV, NAME, ARGS...)`

Calls receiver with the given method name. Unwraps the receiver and arguments
and wraps result.

`RUBY_INVOKE_NO_WRAP(RECV, NAME, ARGS...)`

Calls receiver with the given method name. Unwraps the receiver and arguments
but does not wrap result.

`RUBY_CEXT_INVOKE(NAME, ARGS...)`

Invokes module methods on the `Truffle::CExt` module. Unwraps arguments and
wraps result.

`RUBY_CEXT_INVOKE_NO_WRAP(NAME, ARGS...)`

Invokes module methods on the `Truffle::CExt` module. Unwraps arguments but does
not wrap result. `polyglot_invoke()` is used instead when some of the arguments
are not typed as VALUE.

`polyglot_invoke`

Calls receiver with the given method name. Does not unwrap the receiver and
arguments and does not wrap result. This is useful if some of the arguments are
not VALUE but some other type. `RUBY*_NO_WRAP` can be used when a native value
result is desired and all arguments are `VALUE`. `RUBY*INVOKE` macros are all
implemented using `polyglot_invoke`.

`polyglot_as*`

Converts a polyglot value to a native value. `polyglot_as*` methods are then
used to convert the polyglot result to a native value when the return value is
not a `VALUE`.

`polyglot_*` 

See [polyglot.h](https://github.com/oracle/graal/blob/master/sulong/projects/com.oracle.truffle.llvm.libraries.graalvm.llvm/include/graalvm/llvm/polyglot.h) for documentation regarding the `polyglot_*` methods.

##### Native conversion

See [cext-values.md](cext-values.md) for documentation of the
conversion and management of native handles.

### String pointers

When a Ruby String is accessed via `RSTRING_PTR` or `RSTRING_END`, it is
permanently converted to a rope representing the String in native memory,
instead of storing the bytes in the Java heap.

### Data and typed data

The user data pointer for data and typed data objects needs to be available as a
right-hand-side value which can be assigned to, so we cannot implement this as a
function call. Instead, when a data or typed data object is cast to pointers to
the `RData` or `RTypedData` structures we create a proxy object that pretends to
be a C pointer but really redirects interop reads and writes from C to read and
write the data field in the object.

### `rb_scan_args`

`rb_scan_args` is problematic because it passes pointers to local variables in
variadic arguments. Taking the address of a local variable that holds a Ruby
object and passing that to a function which is not inlined is not supported, and
`clang` will never inline a function with variadic arguments.

We work around this by defining `rb_scan_args` as a macro that redirects to a
function that is not variadic and handles up to ten pointers. This function is
then inlined.

### Integer and pointer data model

We currently assume an LP64 integer and pointer data model.

### Compatibility

See the user [compatibility documentation](../user/compatibility.md).
