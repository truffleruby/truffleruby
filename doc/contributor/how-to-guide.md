# How-To Guide

  * [How to find a Core Method implementation](#how-to-find-a-core-method-implementation)
  * [How to add a Core Method in Java](#how-to-add-a-core-method-in-java)
  * [How to add a new C API function](#how-to-add-a-new-c-api-function)
  * [How to define and expose a POSIX system call to Ruby code](#how-to-define-and-expose-a-posix-system-call-to-ruby-code)
  * [How to add a helper Ruby method](#how-to-add-a-helper-ruby-method)
  * [How to warn in Ruby](#how-to-warn-in-ruby)
  * [How to warn in Java](#how-to-warn-in-java)
  * [How to raise Ruby exception in Java](#how-to-raise-ruby-exception-in-java)
  * [How to cast type implicitly in Ruby](#how-to-cast-type-implicitly-in-ruby)
  * [How to cast type implicitly in Java](#how-to-cast-type-implicitly-in-java)
  * [How to call Java code in Ruby](#how-to-call-java-code-in-ruby)
  * [How to call Ruby method in Java](#how-to-call-ruby-method-in-java)
  * [How to declare an optional method argument in Ruby](#how-to-declare-an-optional-method-argument-in-ruby)
  * [How to accept keyword arguments in Core Method in Java](#how-to-accept-keyword-arguments-in-core-method-in-java)
  * [How to create Ruby Array in Java](#how-to-create-ruby-array-in-java-)
  * [How to call original class method in Ruby](#how-to-call-original-class-method-in-ruby)
  * [How to call original instance method in Ruby](#how-to-call-original-instance-method-in-ruby)
  * [How to get a list of all the Primitives](#how-to-get-a-list-of-all-the-primitives)
  * [How to use the most common Primitives](#how-to-use-the-most-common-primitives)
  * [How to use Java debug helpers](#how-to-use-java-debug-helpers)
  * [How to update supported Unicode version](#how-to-update-supported-unicode-version)
  * [How to patch MRI source files when update Ruby](#how-to-patch-mri-source-files-when-update-ruby)
  * [How to introduce a new platform-specific constant in Ruby](#how-to-introduce-a-new-platform-specific-constant-in-ruby)
  * [How to decide on what to change - ABI version or ABI check](#how-to-decide-on-what-to-change---abi-version-or-abi-check)
  * [How to choose where to add new specs - in TruffleRuby or in ruby/spec repository](#how-to-choose-where-to-add-new-specs---in-truffleruby-or-in-rubyspec-repository)
  * [How to choose between Ruby and Java when implement a Ruby Core Library class method](#how-to-choose-between-ruby-and-java-when-implement-a-ruby-core-library-class-method)
  * [How to write specs for C API](#how-to-write-specs-for-c-api)
  * [How to exclude/include ruby/spec and MRI test cases](#how-to-excludeinclude-rubyspec-and-mri-test-cases)
  * [How to tag slow ruby/spec tests](#how-to-tag-slow-rubyspec-tests)
  * [How to introduce a constant in specs](#how-to-introduce-a-constant-in-specs)
  * [How to add a new spec](#how-to-add-a-new-spec)
  * [How to work on a new CRuby feature](#how-to-work-on-a-new-cruby-feature)
  * [How to prepare PR for changes in the next CRuby version](#how-to-prepare-pr-for-changes-in-the-next-cruby-version)
  * [How to use jt CLI tool](#how-to-use-jt-cli-tool)

## How to find a Core Method implementation

The Core Library is implemented partially in Ruby and partially in Java.

You can find Core Library methods implemented in Ruby in the
`src/main/ruby/truffleruby/core` directory, e.g.

-   `array.rb`
-   `file.rb`
-   ...

Core Library methods implemented in Java are located in the
`src/main/java/org/truffleruby/core` directory in
`<Ruby-class-name>Nodes.java` files.

Such a file contains a class declaration like the following one:

```java
@CoreModule(value = "FalseClass", isClass = true)
public abstract class FalseClassNodes {
    // ...
}
```

where `value = "FalseClass"` means a Ruby class (`FalseClass` in
our case, of which some methods are implemented in this file).

Such a class contains implementation of Ruby class methods that are
declared in the following way:

```java
@CoreMethod(names = "&", needsSelf = false, required = 1)
public abstract static class AndNode extends CoreMethodArrayArgumentsNode {
    // ...
}
```

where `names = "&"` means a Ruby method name (`FalseClass#&` in
our case).

## How to add a Core Method in Java

Ruby Core Methods (methods of Ruby classes from the Core Library)
implemented in Java are represented as AST (Abstract Syntax Tree) nodes
that have an entry point - `execute` method.

Core Methods are located in `src/main/java/org/truffleruby/core`
directory. File names follow the same pattern: `<RubyClass>Nodes`, e.g.
`ArrayNodes`, `HashNodes`, `KernelNodes` etc. Each Core Method is
represented by a Java class named after it, e.g.
`StringNodes.SizeNode` implements Ruby `String#size` method.

Let's consider and example - the implementation of the `Hash#clear` Core Method:

```java
// HashNodes.java
@CoreMethod(names = "clear", raiseIfFrozenSelf = true)
@ImportStatic(HashGuards.class)
public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {

    @Specialization(limit = "hashStrategyLimit()")
    protected RubyHash clear(RubyHash hash,
            @CachedLibrary("hash.store") HashStoreLibrary hashes) {
        hashes.clear(hash.store, hash);
        return hash;
    }
}
```

Let's read this example line by line.

```java
@CoreMethod(names = "clear", raiseIfFrozenSelf = true)
```

The first annotation `@CoreMethod` declares some Core Method
properties, e.g. Ruby method name (`clear`), parameters number etc.
`raiseIfFrozenSelf` attribute here means that it's a method that
mutates a Hash instance, so it shouldn't be called on a frozen Hash.
The `FrozenError` will be raised in this case.

```java
@ImportStatic(HashGuards.class)
```

The `@ImportStatic` annotation makes available in the `ClearNode` class (in the `limit` in this case)
some helper methods declared in the `HashGuards` class.

```java
public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {
```

A Core Method class is called after a Ruby method with a suffix
`Node`. It usually inherits `CoreMethodArrayArgumentsNode` class but
there are other possibilities:

- `CoreMethodNode`
- `AlwaysInlinedMethodNode`

```java
@Specialization(limit = "hashStrategyLimit()")
```

`@Specialization` annotation declares a Java method (`clear` in our
case) as a partial implementation. There are usually several such
specializations in a Core Method class - and what method-specialization
to choose depends on actual argument types.

```java
protected RubyHash clear(RubyHash hash,
```

Core Method specialization method (that implements a non-singleton Ruby
method) accepts a receiver object (`self`) as first parameter. All the
subsequent parameters are parameters of a Ruby method itself. In this
case the method `clear` doesn't have parameters at all. Ruby Core
Library classes are represented in Java as Java classes, e.g.
`RubyHash`, `RubyArray`, `RubyString`, `RubySymbol` etc.

```
@CachedLibrary("hash.store") HashStoreLibrary hashes) {
```

`@Cached`/`@CachedLibrary` are a declaration of extra parameters that will be passed in
automatically by the runtime. `HashStoreLibrary` is a helper class to
manipulate the internal representation (`hash.store`) of Ruby Hash to
store key-value pairs.

```java
hashes.clear(hash.store, hash);
```

It's the main line in the whole class - clearing of a Hash. Actually,
the logic is implemented in the `HashStoreLibrary` so it's just asked
to perform the operation with proper arguments.

```java
return hash;
```

Method `Hash#clear` returns `self`, so the method returns `hash`
argument - receiver of the `clear` Ruby method.

### Specializations

The idea behind specializations is to have different (optimized)
implementations for different arguments.

Note that specializations should be mutually exclusive in the arguments they accept.
For given arguments only one (or at most one) specialization should match the given arguments.
If a specialization `replaces` another then the same applies, except we ignore the replaced specialization(s).

Let's consider one more example - implementation on `Integer#<`:

```java
// IntegerNodes.java
@CoreMethod(names = "<", required = 1)
public abstract static class LessNode extends CoreMethodArrayArgumentsNode {

    @Specialization
    protected boolean less(int a, int b) {
        return a < b;
    }

    @Specialization
    protected boolean less(long a, long b) {
        return a < b;
    }

    @Specialization
    protected boolean less(long a, double b) {
        return a < b;
    }

    @Specialization
    protected boolean less(long a, RubyBignum b) {
        return BigIntegerOps.isPositive(b); // Bignums are never long-valued.
    }

    @Specialization
    protected boolean less(RubyBignum a, long b) {
        return BigIntegerOps.isNegative(a); // Bignums are never long-valued.
    }

    @Specialization
    protected boolean less(RubyBignum a, double b) {
        return BigIntegerOps.less(a, b);
    }

    @Specialization
    protected boolean less(RubyBignum a, RubyBignum b) {
        return BigIntegerOps.compare(a, b) < 0;
    }

    @Specialization(guards = "!isRubyNumber(b)")
    protected Object lessCoerced(Object a, Object b,
            @Cached DispatchNode redoCompare) {
        return redoCompare.call(a, "redo_compare", coreSymbols().LESS_THAN, b);
    }
}
```

There are several specializations defined which have different argument
types and proper comparison logic.

There are two main scenarios - when both objects are Numeric and when
the second argument (`b`, `a` is self, we remember this) is not
Numeric and it should be coerced. All specializations except the last
one cover the first case and the last specialization tries to coerce by
calling a helper method (`redo_compare`, that is implemented in Ruby).

There are the following cases for Numeric arguments:

- int, int
- long, long
- long, double
- long, RubyBignum
- RubyBignum, long
- RubyBignum, double
- RubyBignum, RubyBignum

So for `1 < 2` Ruby expression the first specialization (int, int)
will be used. When one of the numbers doesn't fit into Java int, but
that fits into Java long - then the second specialization (long, long)
will be used, and so on.

### Guards

Let's look at the last specialization in the previous example:

```java
@Specialization(guards = "!isRubyNumber(b)")
protected Object lessCoerced(Object a, Object b,
        @Cached DispatchNode redoCompare) {
    return redoCompare.call(a, "redo_compare", coreSymbols().LESS_THAN, b);
}
```

The first line is pretty interesting - there is a `guards` attribute
that contains something like a code expression as a String literal:

```java
@Specialization(guards = "!isRubyNumber(b)")
```

The `guards` attribute contains a Java boolean expression and is used
to express some additional (to parameter types) and more complex
conditions to choose a specialization. It can contain Java method calls
that should be declared in the class as public methods or imported (with
`@ImportStatic`) from some helper class (e.g. `StringGuards` or
`HashGuards`). The most common guard predicates are defined in
`RubyGuards` and by default available in Java class inheriting from `RubyBaseNode`.

Examples of some guards:

- `"count < 0"`
- `"array != other"`
- `"wasProvided(name)"`
- `"range.isEndless() || range.isBoundless()"`

May be specified several expressions that are combined with logical AND:

```java
@Specialization(guards = { "!isEmptyArray(array)", "count >= 0", "!fitsInInteger(count)" })
```

### Fallback

There is a way to declare a special specialization that is called when
all the other ones in a class don't match arguments. It's declared
with a `@Fallback` annotation this way:

```java
@Fallback
protected boolean immutable(Object object, Object name) {
    return false;
}
```

Note that parameters are declared as instances of `Object` type.

Let's consider an example of `Kernel#instance_variable_defined?`
implementation:

```java
// KernelNodes.java
@CoreMethod(names = "instance_variable_defined?", required = 1)
public abstract static class InstanceVariableDefinedNode extends CoreMethodArrayArgumentsNode {

    @Specialization
    protected boolean isInstanceVariableDefined(RubyDynamicObject object, Object name,
            @Cached CheckIVarNameNode checkIVarNameNode,
            @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary,
            @Cached NameToJavaStringNode nameToJavaStringNode) {
        final String nameString = nameToJavaStringNode.execute(name);
        checkIVarNameNode.execute(object, nameString, name);
        return objectLibrary.containsKey(object, nameString);
    }

    @Fallback
    protected boolean immutable(Object object, Object name) {
        return false;
    }
}
```

The logic is the following

- when self (the first parameter) is `RubyDynamicObject` (so it
  isn't a Symbol or Numeric) then it can have instance variables
- otherwise it cannot have instance variable at all

The method `immutable` is called only when the first argument isn't
`RubyDynamicObject`.

### Method with variable arguments

There are a lot of methods in the Ruby Core Library that accept unknown
number of arguments, e.g. #send, Module#class_eval, etc. There is a way
to implement such a method in Java - just specify `rest = true`
`@CoreMethod` annotation attribute and specializations will have a bit
different parameters list - with `Object[] args` argument.

Let's consider an example of `String#concat` method. The most generic
specialization is the following:

```java
// ModuleNodes.java
@CoreMethod(names = "private_class_method", rest = true)
public abstract static class PrivateClassMethodNode extends CoreMethodArrayArgumentsNode {
    // ...

    @Specialization
    protected RubyModule privateClassMethod(VirtualFrame frame, RubyModule module, Object[] names) {
        final RubyClass singletonClass = singletonClassNode.executeSingletonClass(module);

        for (Object name : names) {
            setMethodVisibilityNode.execute(singletonClass, name, Visibility.PRIVATE);
        }

        return module;
    }
}
```

Let's look at the method parameters list:

```java
protected RubyModule privateClassMethod(VirtualFrame frame, RubyModule module, Object[] names) {
```

The first parameter is an optional argument `frame`. The second one
(`RubyModule module`) is a receiver, that's `self`. `Object[]
names` are actual method call arguments.

### Block argument

If a Core Method accepts a block argument, it should declare the last
parameter as `RubyProc block`.

Let's consider an example - the implementation of the `Module#initialize` method:

```java
// ModuleNodes.java
@CoreMethod(names = "initialize", needsBlock = true)
public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

    public abstract RubyModule execute(RubyModule module, Object block);

    @Specialization
    protected RubyModule initialize(RubyModule module, Nil block) {
        return module;
    }

    @Specialization
    protected RubyModule initialize(RubyModule module, RubyProc block,
            @Cached ClassExecBlockNode classExecBlockNode) {
        classExecBlockNode.execute(EmptyArgumentsDescriptor.INSTANCE, module, new Object[]{ module }, block);
        return module;
    }
}
```

A specialization signature looks like:

```java
protected RubyModule initialize(RubyModule module, RubyProc block,
```

It uses the `ModuleNodes.ClassExecBlockNode` helper node to call a
block that uses under the hood `CallBlockNode` node. `CallBlockNode`
is a common way to call a Ruby block.

### Optional parameter

A lot of Ruby Core Methods have multiple signatures with different
numbers of arguments. So it's a common situation when a positional
parameter might be missing. In Java a Core Method specialization missing
an optional parameter is expressed with `NotProvided` class.

There are several similar approaches to handle optional parameters:

#### Explicit check

The approach is the following:

- declare `Object object` parameter
- manually check if it equals `NotProvided.INSTANCE`

```java
protected RubyArray unpackCached(Object string, Object format, Object offsetObject,
    // ...
{

    final int offset = (offsetObject == NotProvided.INSTANCE) ? 0 : (int) offsetObject;
```

This should use an `InlinedConditionProfile`, unless the method is always `split`.

#### Check with guard

There are dedicated guards (declared in `RubyGuards`):

- wasProvided
- wasNotProvided

```java
protected Object stepFallback(VirtualFrame frame, Object range, Object step, Object block) {
    // ...

    if (RubyGuards.wasNotProvided(step)) {
        step = 1;
    }
```

#### Check on the specializations level

Cases when parameter is present and missing are handled in
different specializations you can rely on filtering by specialization
signature and:

-   define a specialization with `NotProvided object`
-   define another specialization with some specific type, e.g. `int
    object`

Specifying different combinations of parameter types you can easily
differentiate use cases. Let's consider an example:

```java
// HashNodes.java
@CoreMethod(names = "initialize", needsBlock = true, optional = 1, raiseIfFrozenSelf = true,
        split = Split.HEURISTIC)
@ImportStatic(HashGuards.class)
public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

    @Specialization
    protected RubyHash initialize(RubyHash hash, NotProvided defaultValue, Nil block) {
        // ...
    }

    @Specialization
    protected RubyHash initialize(RubyHash hash, NotProvided defaultValue, RubyProc block,
            @Cached @Shared PropagateSharingNode propagateSharingNode) {
        // ...
    }

    @Specialization(guards = "wasProvided(defaultValue)")
    protected RubyHash initialize(RubyHash hash, Object defaultValue, Nil block,
            @Cached @Shared PropagateSharingNode propagateSharingNode) {
        // ...
    }

    @Specialization(guards = "wasProvided(defaultValue)")
    protected Object initialize(RubyHash hash, Object defaultValue, RubyProc block) {
        throw new RaiseException(
                getContext(),
                coreExceptions().argumentError("wrong number of arguments (1 for 0)", this));
    }
```

Please note that in the 3d specialization `wasProvided(defaultValue)`
guard is used. The 1st and the 3d specializations have similar
signatures and match the case when `defaultValue` argument is missing
and an instance of `NotProvided` is passed (because `Object` class
is more generic than `NotProvided`). So the guard is the only way to
differentiate them.

### Class methods

When you need to implement in Java a Ruby singleton/class method - not an instance method -
use `onSingleton = true` attribute of the `@CoreMethod` annotation:

```java
// HashNodes.java
@CoreMethod(names = "ruby2_keywords_hash?", onSingleton = true, required = 1)
public abstract static class IsRuby2KeywordsHashNode extends CoreMethodArrayArgumentsNode {
    @Specialization
    protected boolean isRuby2KeywordsHash(RubyHash hash) {
        return hash.ruby2_keywords;
    }
}
```

Please note that a specialization's arguments don't contain `self` -
only a Ruby method's own parameters (`RubyHash hash` in our example).

## How to add a new C API function

Ruby provides the C API so TruffleRuby should comply with this API as
well.

C API functions implementations are located in the `src/main/c/cext` directory.

C function can just proxy a call to an implementation in Ruby, e.g.
function `rb_time_nano_new` calls (using `polyglot_invoke` function)
another function with the same name and passes all the parameters:

```c
// src/main/c/cext/time.c
VALUE rb_time_nano_new(time_t sec, long nsec) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_time_nano_new", sec, nsec));
}
```

Ruby methods available for C API functions are located in
`lib/truffle/truffle/cext.rb` file and defined in `Truffle::CExt`
module.

```ruby
  # lib/truffle/truffle/cext.rb
  ORIGINAL_TIME_AT = Time.method(:at)

  def rb_time_nano_new(sec, nsec)
    ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond)
  end
```

C extensions related *primitives* are located in
`org/truffleruby/cext/CExtNodes.java` file.

More details about writing a C extensions are provided in the [C Extensions Guide](cexts.md).

## How to define and expose a POSIX system call to Ruby code

There is a way to make a POSIX system call in Ruby code

Use a `Truffle::POSIX` Ruby module to call available syscalls:

```ruby
# src/main/ruby/truffleruby/core/dir.rb
ret = Truffle::POSIX.chdir path
```

All the available system calls are listed in
`src/main/ruby/truffleruby/core/posix.rb` file in the following way:

```ruby
# src/main/ruby/truffleruby/core/posix.rb  
attach_function :chdir, [:string], :int
```

### TruffleRuby specific wrappers

There are also helper C functions and wrappers around POSIX system calls
that are also available in the `Truffle::POSIX` module.

They are declared in the following way:

```ruby
# src/main/ruby/truffleruby/core/posix.rb
attach_function :truffleposix_clock_gettime, [:int], :int64_t, LIBTRUFFLEPOSIX
```

And implemented in the `src/main/c/truffleposix/truffleposix.c` file,
e.g. this function returns current time in nanoseconds:

```c
// src/main/c/truffleposix/truffleposix.c
int64_t truffleposix_clock_gettime(int clock) {
  struct timespec timespec;
  int ret = clock_gettime((clockid_t) clock, &timespec);
  if (ret != 0) {
    return 0;
  }
  return ((int64_t) timespec.tv_sec * 1000000000) + (int64_t) timespec.tv_nsec;
}
```

### Other C libraries

There are also available functions to expose from *libc* and *libcrypt*
as well.

## How to add a helper Ruby method

You would like to extract some code from a Ruby Core class (`Array`,
`IO`, etc) method but don't want it to be visible to the end user. So
a new helper class method or a private instance method is not an option.

The common solution is to create a new Operations helper module:

- create a new Ruby file
  `src/main/ruby/truffleruby/core/truffle/<core-class>_operations.rb`
- create a new Ruby module `Truffle::<core-class>Operations`
- add this file into a `CORE_FILES` list in
  `src/main/java/org/truffleruby/core/CoreLibrary.java`
- extract helper methods to this module `<core-class>Operations`

## How to warn in Ruby

Use `Kernel#warn` method.

```ruby
warn 'not acting on top...'
warn 'IO::new() does not...', uplevel: 1
warn "`byte_ranges` is deprecated..." if $VERBOSE
```

Use `uplevel: 1` option to print a file and a line number of an
application code in a warning message if a warning is triggered in Core
Library Ruby source code.

## How to warn in Java

There are two helper nodes

Use `WarnNode` to warn unless `$VERBOSE` is `nil`:

```ruby
@Specialization
protected Object powBignum(long base, RubyBignum exponent,
        @Cached("new()") WarnNode warnNode) {
    // ...

    if (warnNode.shouldWarn()) {
        warnNode.warningMessage(
                getContext().getCallStack().getTopMostUserSourceSection(),
                "in a**b, b may be too big");
    }
    //... 
}
```

Use `WarningNode` to warn only if `$VERBOSE` is `true`:

```java
@Specialization
protected Object taint(Object object,
        @Cached("new()") WarningNode warningNode) {
    if (warningNode.shouldWarn()) {
        warningNode.warningMessage(
                getSourceSection(),
                "Object#taint is deprecated and will be removed in Ruby 3.2.");
    }
    return object;
}
```

Use `getContext().getCallStack().getTopMostUserSourceSection()`
instead of `getSourceSection()` to print a file and a line number of
an application code in warning message if warning is triggered in a Core
Library method.

## How to raise Ruby exception in Java

Use `throw new RaiseException(...)` to raise a specified Ruby exception

```java
throw new RaiseException(getContext(), coreExceptions().typeError("compared with non class/module", this));
```

Use helper methods (e.g. `typeError` or `frozenError`) to construct
a Ruby exception object defined in a `CoreExceptions` class.

Use a helper method `CoreExceptions.inspect` to add a string
representation of arbitrary Ruby object to an exception message

```java
public RubyException frozenError(Object object, Node currentNode) {
    String className = LogicalClassNode.getUncached().execute(object).fields.getName();
    return frozenError(StringUtils.format("can't modify frozen %s: %s", className, inspect(object)), currentNode,
            object);
}
```

## How to cast type implicitly in Ruby

Ruby Core library uses implicit type conversion to convert method
arguments to expected types using methods like `#to_int`, `#to_str`,
`#to_hash`, `#to_io` etc.

To convert arguments to an expected type in Ruby there are two types of
helpers:

- Ruby methods defined in a `Truffle::Type` module
- *primitives* defined in `src/main/java/org/truffleruby/core/support/TypeNodes.java`

Main helper methods for implicit type conversion are:

- `Truffle::Type.rb_convert_type`
- `Truffle::Type.rb_check_convert_type`

Example for `rb_convert_type`

```ruby
pid = Truffle::Type.rb_convert_type(pid, Integer, :to_int)
```

The main difference between `rb_convert_type` and
`rb_check_convert_type` is that the last one doesn't raise exception
if object doesn't respond to specified method (e.g. `#to_str`) and
returns `nil` instead. It's useful if there are several expected
types or several ways to convert object (e.g. with both `#to_int` and
`#to_i` methods).

Example for `rb_check_convert_type`:

```ruby
# core/kernel.rb
def String(obj)
  str = Truffle::Type.rb_check_convert_type(obj, String, :to_str)
  if Primitive.nil? str
    str = Truffle::Type.rb_convert_type(obj, String, :to_s)
  end
  str
end
```

There are more specific *primitives* defined in `TypeNodes.java`.

The most common are the following:
- `Primitive.rb_num2long`
- `Primitive.rb_num2int`
- `Primitive.rb_to_int`
- `Primitive.as_boolean`

These primitives are preferred to `rb_convert_type`/`rb_check_convert_type` since they are more efficient (for execution speed and footprint).

## How to cast type implicitly in Java

Ruby Core library uses implicit type conversion to convert method
arguments to expected types using methods like `#to_int`, `#to_str`,
`#to_hash`, `#to_io` etc.

To convert arguments to an expected type in Java there are helper
classes in a `org.truffleruby.core.cast` package.

The most common helpers are:

- ToAryNode
- ToIntNode
- ToStrNode

Example:

```java
@Specialization(guards = "range.isEndless()")
protected int[] normalizeEndlessRange(RubyObjectRange range, int size,
        @Cached ToIntNode toInt) {
    int begin = toInt.execute(range.begin);
    return new int[]{ begin >= 0 ? begin : begin + size, size - begin };
}
```

## How to call Java code in Ruby

Use *primitives*, that is Java classes defined in a special way, to be able to
call them in Ruby source code. *Primitives* are usually located in the
`<RubyClass>Nodes` files among Core Method nodes.

Define a *primitive* with `@Primitive` Java annotation and a `name`
attribute:

```java
@Primitive(name = "nil?")
public abstract static class IsNilNode extends PrimitiveArrayArgumentsNode {
    @Specialization
    protected boolean isNil(Object value) {
        return value == nil;
    }
}
```

and use it in Ruby source code as `Primitive.<method name>`:

```ruby
def compact
  reject { |_k, v| Primitive.nil? v }
end
```

To use a *primitive* it may be required to add a magic comment at the
beginning of a file (*primitives* are available by default in Core
Library files):

```ruby
# truffleruby_primitives: true
```

Naming conventions are described in the [Primitives Guide](primitives.md).

## How to call Ruby method in Java

Use `DispatchNode` class to call a method of arbitrary Ruby object:

```java
@Specialization(guards = { "!string.isFrozen()", "!compareByIdentity" })
protected Object dupAndFreeze(RubyString string, boolean compareByIdentity,
        @Cached DispatchNode dupNode) {
    final RubyString copy = (RubyString) dupNode.call(string, "dup");
    copy.freeze();
    return copy;
}
```

### With arguments

`DispatchNode` has several `call*()` methods for up to 3 positional arguments:

```java
callWarnNode.call(context.getCoreLibrary().kernelModule, "warn", warningString); // WarnNode
respondToMissingNode.call(self, "respond_to_missing?", toSymbolNode.execute(name), includeProtectedAndPrivate) // KernelNodes
toEnumWithSize.call( // EnumeratorSizeNode
    coreLibrary().truffleKernelOperationsModule,
    "to_enum_with_size",
    self,
    methodName,
    sizeMethodName);
```

If there are more than 3 arguments - they may be passed as an `Object[]`:

```java
// TruffleRegexpNodes
warnOnFallbackNode.call(
    getContext().getCoreLibrary().truffleRegexpOperationsModule,
    "warn_fallback",
    new Object[]{
        regexp,
        string,
        encoding,
        fromPos,
        toPos,
        atStart,
        startPos });
```

There are also useful helper methods:

- callWithKeywords
- callWithBlock

### Arguments manipulation

The methods listed above are just kind of helpers to simplify working
with the `DispatchNode#dispatch` method which accepts arguments in a "frame arguments" format.
There are numerous helpers in a `RubyArguments` class to create, change and query "frame arguments":

- pack
- repack
- set*
- get*

Example of repack-ing "frame arguments" where the all the
`Kernel#public_send` arguments except the first one (method name to call)
are passed to specified method call:

```java
// ClassNodes
@GenerateUncached
@CoreMethod(names = "public_send", needsBlock = true, required = 1, rest = true, alwaysInlined = true)
public abstract static class PublicSendNode extends AlwaysInlinedMethodNode {

    @Specialization
    protected Object send(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
            @Cached(parameters = "PUBLIC") DispatchNode dispatchNode,
            @Cached NameToJavaStringNode nameToJavaString) {
        Object name = RubyArguments.getArgument(rubyArgs, 0);
        Object[] newArgs = RubyArguments.repack(rubyArgs, self, 1);
        return dispatchNode.dispatch(callerFrame, self, nameToJavaString.execute(name), newArgs);
    }
}
```

This is designed to avoid extra `Object[]` allocations.
As an example for `callWarnNode.call(kernelModule, "warn", warningString)` we don't want to allocate a 1-element `Object[]` with `warningString`,
we want to allocate an `Object[]` in "frame arguments" format with the hidden arguments and `warningString` as the last one.

## How to declare an optional method argument in Ruby

It's a common situation when a Ruby core class method has an optional
parameter.

Problem: you need to distinguish cases when argument is not passed and
when passed `nil` value.

Solution: you may use `undefined` as a default value and a
`Primitive.undefined?` primitive to check the value:

```ruby
# core/enumerable.rb
def min(n = undefined, &block)
  return min_n(n, &block) if !Primitive.undefined?(n) && !Primitive.nil?(n)
  min_max(-1, &block)
end
```

Only use `undefined` when necessary.
If there is a natural default value (i.e., not passing the value is the same as passing the default value explicitly)
then use that instead for best readability.

## How to accept keyword arguments in Core Method in Java

Currently there is no way to accept directly keyword arguments in Java
Core Method implementations, to simplify various aspects of the implementation.

There is a workaround:

1. to create a *primitive* in Java
2. to declare a Core Method in Ruby
3. to call the *primitive* and pass keyword arguments as positional
   arguments

```ruby
# core/string.rb
def unpack(format, offset: undefined)
  format = Truffle::Type.rb_convert_type(format, String, :to_str)
  unless Primitive.undefined?(offset)
    offset = Primitive.rb_num2int(offset) # to guarantee it's `int` finally
  end
  Primitive.string_unpack(self, format, offset)
end
```

## How to create Ruby Array in Java 

There is `createArray` helper method available in `RubyBaseNode` subclasses:

```java
createArray(newStore, size);
createArray(bytes);
```

## How to call original class method in Ruby

You want to implement a Ruby Core library method using a
Ruby Core *class* method and handle a case when this method is redefined
by a user code.

You may call original class method in the following way:

```ruby
ORIGINAL_TIME_AT = Time.method(:at)
ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond)
```

## How to call original instance method in Ruby

You want to implement a Ruby Core library method using a
Ruby Core *instance* method and handle a case when this method is
redefined by a user code.

You may call original instance method in the following way:

```ruby
ARRAY_APPEND = Array.instance_method(:<<)
ARRAY_APPEND.bind_call(array, element)
```

## How to get a list of all the Primitives

Use `Truffle::Debug.primitive_names` method:

```
jt -q ruby -e 'puts Truffle::Debug.primitive_names.sort'
...
array_eql?
array_equal?
array_flatten_helper
array_inject
...
```

## How to use the most common Primitives

Primitives are always more efficient than the corresponding code without the primitive,
both in execution speed (avoids a Ruby method call) and in memory footprint.
Unlike core methods they cannot be redefined, which is useful to avoid calling a potentially-redefined core method.

I would say the following *primitives* are the most often used and  generic:

- `Primitive.nil?`
- `Primitive.undefined?`
- `Primitive.check_frozen`
- `Primitive.single_block_arg`
- `Primitive.is_a?`
- `Primitive.respond_to?`
- `Primitive.class`
- `Primitive.equal?`

#### `Primitive.nil?(object)`

It's equivalent to `object == nil` or `object.nil?`, but more efficient.

#### `Primitive.undefined?(object)`

It's equivalent to `object == undefined` where `undefined` is a
special magic unique object.

The `undefined` object is used to detect whether an optional positional
argument is passed or not and to distinguish default "undefined" value
and `nil` that may be specified. `undefined` is an instance of
`NotProvided` Java class - `NotProvided.INSTANCE`.
It's also the same as (unwrapped) `Qundef` in C.

```ruby
# core/string.rb
def initialize(other = undefined, capacity: nil, encoding: nil)
  unless Primitive.undefined?(other)
    Primitive.check_mutable_string self
    Primitive.string_initialize(self, other, encoding)
  end
  self.force_encoding(encoding) if encoding
  self
end
```

`undefined` should never be returned to user code, so it remains a special object
only the TruffleRuby implementation has access to.

#### `Primitive.check_frozen(object)`

It's equivalent to `raise FrozenError if object.frozen?`.

It's used usually in self modifying methods to check whether `self` is
frozen (so it cannot be modified) and to raise `FrozenError` if it is.

```ruby
# core/array.rb
def sort!(&block)
  Primitive.check_frozen self

  Primitive.steal_array_storage(self, sort(&block))
end
```

#### `Primitive.single_block_arg`

It's used to get a block's arguments as a single value.

It returns:

- `nil` if no arguments are passed
- the single argument if there is a single argument passed
- an array of arguments if there are multiple arguments passed

```ruby
# core/enumerable.rb
def include?(obj)
  each { return true if Primitive.single_block_arg == obj }
  false
end
```

#### `Primitive.is_a?(object, <class>)`

It's equivalent to `object.is_a?(<class>)`.

#### `Primitive.respond_to?(object, name, include_private)`

It's equivalent to `object.respond_to?(name, include_private)`.

#### `Primitive.class(object)`

It's equivalent to `object.class`

#### `Primitive.equal?(object, object)`

It's equivalent to `a.equal?(b)`

## How to use Java debug helpers

The helper methods are implemented in Java in a
`src/main/java/org/truffleruby/debug/TruffleDebugNodes.java` file and
available in Ruby under a `Truffle::Debug` Ruby module.

There are the following Java debug helper methods:

```
jt -q ruby -e 'puts Truffle::Debug.methods(false).sort'
...
java_class_of
...
print
print_ast
print_backtrace
print_source_sections
...
```

### Truffle::Debug.java_class_of

```
jt -q ruby -e 'puts Truffle::Debug.java_class_of([])'
RubyArray
```

### Truffle::Debug.ast

```
jt -q ruby -e 'p Truffle::Debug.ast([].method(:to_ary))'
[:RubyMethodRootNode, [:SequenceNode, [:WriteLocalVariableNode, [:ProfileArgumentNodeGen, [:ReadSelfNode]]], [:SaveMethodBlockNode], [:SelfNode]]]
```

### Truffle::Debug.print_ast

```
$ jt -q ruby -e 'puts Truffle::Debug.print_ast([].method(:to_ary))'
  RubyMethodRootNode
    body = SequenceNode
      body[0] = WriteLocalVariableNode
        valueNode = ProfileArgumentNodeGen
          childNode_ = ReadSelfNode
      body[1] = SaveMethodBlockNode
      body[2] = SelfNode
```

The method `Array#to_ary` is implemented in the following way:

```ruby
def to_ary
  self
end
```

### Truffle::Debug.ast_size

```
jt -q ruby -e 'puts Truffle::Debug.ast_size([].method(:to_ary))'
7
```

## How to update supported Unicode version

We rely on [JCodings](https://github.com/jruby/jcodings) for Unicode/Encoding stuff.
So basically updating  Unicode version is just as upgrade of this library

Steps to do:

- choose a proper jcodings version (probably the latest one)
  - Unicode version is hardcoded [here](https://github.com/jruby/jcodings/blob/jcodings-1.0.58/src/org/jcodings/Config.java)
- ask for approval to upgrade the third-party component (example Jira ticket: GR-43593)
- upgrade the library ([example PR](https://github.com/truffleruby/truffleruby/pull/2930))
- Ruby constants `UNICODE_VERSION` and `UNICODE_EMOJI_VERSION` should have a proper value now

## How to patch MRI source files when update Ruby

When you update Ruby and import MRI source files (both Ruby and C) it's
a common situation when you need to make a change in a file.

Use `defined?(::TruffleRuby)` for Ruby source code:

```ruby
unless defined?(::TruffleRuby)
  require "rbconfig"
end
```

Use `#ifdef TRUFFLERUBY` for C source code:

```cpp
#ifdef TRUFFLERUBY
VALUE rb_imemo_tmpbuf_auto_free_pointer(void);
#else
static inline VALUE rb_imemo_tmpbuf_auto_free_pointer(void);
#endif
```

## How to introduce a new platform-specific constant in Ruby

You need to access in Ruby code a C constant that has a
platform-specific value. There are a bunch of them, e.g. constants
related to files (`SEEK_SET`, `O_RDONLY`), networking (`INET6`,
`IPPROTO_UDP`) or process signals (`SIGINT`, `SIGCHLD`). They are
available in C, but not in Ruby by default.

Such constant values are stored in platform-specific configuration files
(`src/main/java/org/truffleruby/platform`):

- `LinuxAArch64NativeConfiguration.java`
- `LinuxAMD64NativeConfiguration.java`
- `DarwinAArch64NativeConfiguration.java`
- `DarwinAMD64NativeConfiguration.java`

Example of constant configuration:

```java
// src/main/java/org/truffleruby/platform/DarwinAMD64NativeConfiguration.java
configuration.config("platform.file.O_RDONLY", 0);
configuration.config("platform.file.O_WRONLY", 1);
configuration.config("platform.file.O_RDWR", 2);
```

Use `Truffle::Config[<key>]` to access a configured constant
value:

```ruby
# src/main/ruby/truffleruby/core/file.rb
RDONLY   = Truffle::Config['platform.file.O_RDONLY']
WRONLY   = Truffle::Config['platform.file.O_WRONLY']
RDWR     = Truffle::Config['platform.file.O_RDWR']
```

To have a new constant values be added to the platform-specific
configuration files you should declare a constant in
`tool/generate-native-config.rb` file:

```ruby
# tool/generate-native-config.rb
constants 'file' do |cg|
  cg.include 'fcntl.h'
  cg.include 'sys/stat.h'
  # ...

  cg.consts %w[
    O_RDONLY O_WRONLY O_RDWR
    # ...
  ]
end
```

Run `ruby-generate-native-config-*` CI jobs and copy-paste generated
(and printed) platform specific config files.

## How to decide on what to change - ABI version or ABI check

When you modify C-files (*.c, *.h) or compilation-related Ruby files
(e.g. `lib/truffle/rbconfig.rb`) you should either increase ABI
(Application Binary Interface) version if the change does
affect the ABI or increase the ABI *check* version explicitly by modifying one of
the files:

- lib/cext/include/truffleruby/truffleruby-abi-version.h
- lib/cext/ABI_check.txt

ABI change is:
- changing headers or compilation flags
- removing/adding a non-static function
- implementing already declared non-static functions

In case of doubt, bump `truffleruby-abi-version.h`.

## How to choose where to add new specs - in TruffleRuby or in ruby/spec repository

When you want to add specs there are two options - you can add them in:

- in `spec/ruby` directory in the TruffleRuby repository, or
- in the ruby/spec git repository instead ([https://github.com/ruby/spec](https://github.com/ruby/spec))

The `spec/ruby` directory contains the whole ruby/spec test suite.

If you are implementing or fixing some method in TruffleRuby, change specs under `spec/ruby` in the same PR (much more convenient).

If you make a PR with only spec changes, it is best to make a PR to ruby/spec directly
to avoid conflicts with changes in ruby/spec itself.

The `spec/ruby` is being [synchronized with ruby/spec repository](https://github.com/ruby/spec#synchronization-with-ruby-implementations)
periodically. ruby/spec itself is synchronized with other
Ruby implementations (CRuby, JRuby etc). So no manual actions are
required if you add new specs in the `spec/ruby` directory.

## How to choose between Ruby and Java when implement a Ruby Core Library class method

> Prefer Ruby if possible (easier to maintain & read), Java can make
> sense in some cases if it can gain much in terms of performance (e.g.,
> extra inline cache or so which is not possible in Ruby).
> Benoit Daloze.

It's also an option to implement a method partially in Java and
partially in Ruby. It makes sense when there is complex logic but it's
a performance critical method.

So performance critical code paths are implemented in Java and complex
but less critical paths are implemented in Ruby as fallback
specializations.

Let's look at the implementation of the `Float#==` Ruby method:

```java
// FloatNodes.java
@CoreMethod(names = { "==", "===" }, required = 1)
public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

    @Specialization
    protected boolean equal(double a, long b) {
        # ...
    }

    @Specialization
    protected boolean equal(double a, double b) {
        # ...
    }

    @Specialization
    protected boolean equal(double a, RubyBignum b) {
        # ...
    }

    @Specialization(guards = "!isRubyNumber(b)")
    protected Object equal(VirtualFrame frame, double a, Object b) {
        # ...
        return fallbackCallNode.call(a, "equal_fallback", b);
    }
}
```

The helper method `equal_fallback` is implemented as a private method
of the `Float` class.

```ruby
# core/float.rb
def equal_fallback(other)
  # ...
  b, a = math_coerce(other)
  a == b
end
private :equal_fallback
```

## How to write specs for C API

Ruby provides a C API and public functions tested as well as Ruby Core
library.

Specs are located in `spec/ruby/optional/capi` directory and are
written in Ruby.

To make a public C function available in a spec, a helper Ruby method
should be introduced. Each method is implemented in C and calls a public
Ruby API function.

Let's consider an example with the `rb_Array(object)` C function.

```ruby
# spec/ruby/optional/capi/array_spec.rb
describe "C-API Array function" do
  before :each do
    @s = CApiArraySpecs.new
  end

  describe "rb_Array" do
    it "returns obj if it is an array" do
      arr = @s.rb_Array([1,2])
      arr.should == [1, 2]
    end

    # ...
  end
end
```

The class `CApiArraySpecs` is declared in C in the following way:

```c
// spec/ruby/optional/capi/ext/array_spec.c
void Init_array_spec(void) {
  VALUE cls = rb_define_class("CApiArraySpecs", rb_cObject);
  rb_define_method(cls, "rb_Array", array_spec_rb_Array, 1);
  // ...
}
```

When the `CApiArraySpecs#rb_Array` Ruby method is called in a test,
the `array_spec_rb_Array` C function will be called.

Such helper C functions are often simple and just call a Ruby C API function:

```c
// spec/ruby/optional/capi/ext/array_spec.c
static VALUE array_spec_rb_Array(VALUE self, VALUE object) {
  return rb_Array(object);
}
```

When you add specs for a new C API function that was introduced in the
recent Ruby release and doesn't present in previous Ruby versions that
are still maintained such specs will fail on such previous Ruby versions
on a helper functions compilation step.

So Ruby specs should be covered with a `ruby_version_is` guard and
**all the related C code** in specs should be disabled in similar way
with C macros `RUBY_VERSION_IS_<X>_<Y>` (e.g.
`RUBY_VERSION_IS_3_2`) that present if Ruby version is X.Y (3.2 in
our case) or newer:

```c
#ifdef RUBY_VERSION_IS_3_1
  rb_define_method(cls, "rb_io_maybe_wait_writable", io_spec_rb_io_maybe_wait_writable, 3);
  rb_define_method(cls, "rb_io_maybe_wait_readable", io_spec_rb_io_maybe_wait_readable, 4);
  rb_define_method(cls, "rb_io_maybe_wait", io_spec_rb_io_maybe_wait, 4);
#endif
```

C helpers are defined in `spec/ruby/optional/capi/ext/rubyspec.h`

If a C helper should be more sophisticated - then some Ruby C macros
and functions might be helpful:

- `Qnil`, `Qtrue`, `Qfalse` - Ruby `nil`, `true` and `false` objects respectively
- `INT2NUM`, `INT2FIX` - to convert C `int` to Ruby `Integer`
- `NUM2INT`, `FIX2INT` - to convert Ruby `Integer` to C `int`
- `NUM2LONG`, `FIX2LONG` - to convert Ruby `Integer` to C `long`
- `NIL_P` - check whether argument is Ruby `nil`
- `RTEST` - check the "truthy" value (`nil`, `false` - 0, anything else - 1)
- `ID2SYM`, `SYM2ID`
- `rb_str_new`, `rb_str_new_cstr`
- `rb_intern`

## How to exclude/include ruby/spec and MRI test cases

There is a mechanism to skip tests for not yet implemented functionality
or known but not fixed bug.

### ruby/spec specs

Test cases that shouldn't be run by the `jt test` command are tagged
with a tag "fails". Tag files are located in the `spec/tags` directory.

Example of such tagged test case:

```
# spec/tags/core/array/take_tags.txt
fails:Array#take returns a subclass instance for Array subclasses
```

Use command `jt tag` to tag failing specs:

```
jt tag <path-to-spec-file>
```

Or you can also tag specs from a failed specs run's output with:

```
# First copy the output in your clipboard (e.g. Ctrl+C)
$ xsel -b | spec/mspec/tool/tag_from_output.rb
```

Use command `jt untag` to remove passing specs from a tags file

```
jt untag <path-to-spec-file>
```

Use `jt help` command for further details.

### MRI tests

MRI test cases that shouldn't be run by `jt test` command are listed
in the exclude files, that are located in `test/mri/excludes` directory.

Example of such excluded test case:

```ruby
# test/mri/excludes/Test_Symbol/TestType.rb
exclude :test_check_id_invalid_type, "needs investigation"
```

Use command `jt retag` to re-create an exclude file with all the
failed test cases listed:

```
jt retag <path-to-MRI-test-file>
```

If you need to skip the whole file with MRI tests - it should be added
to `test/mri/failing.exclude` file.

More details are explained in the [MRI Tests Guide](mri-tests.md).

## How to tag slow ruby/spec tests

Some test cases are tagged as "slow" to be able to run only fast specs.

To automatically add tag "slow" for a test case run the following shell command:

```
jt test fast <path-to-spec-file>
```

And commit the added/changed tag files.

## How to introduce a constant in specs

It's a common issue when writing new specs that you need a new Ruby
constant or not anonymous class/module.

It's prohibited to introduce a new top-level constant to not pollute
the global namespace and keep specs isolated as much as possible.

The only way to define a new constant or not anonymous class/module is
to define it in one of the spec helper modules. Such modules follow a
name convention `...Specs` and usually created per each test file in
`fixtures/classes.rb` file.

This is a helper module for Array specs:

```ruby
# spec/ruby/core/array/fixtures/classes.rb
module ArraySpecs
  SampleRange = 0..1000

  # ...

  class MyArray < Array
   # ...
  end
  
  # ...
end
```

## How to add a new spec

TruffleRuby uses the ruby/spec test suite (<https://github.com/ruby/spec>).
Tests are located in the `spec/ruby` directory.

There are the following main directories:

- `spec/ruby/core` - specs for the Core Library
- `spec/ruby/library` - specs for the Standard Library
- `spec/ruby/language` - specs for the Ruby syntax itself

## How to work on a new CRuby feature

When you work on a new feature/method usually you make the following steps:

- check a feature/method description in the Ruby documentation
- check a corresponding issue (on the Ruby bug tracker <https://bugs.ruby-lang.org>):
  - a feature description
  - discussion
  - "Associated revisions" tab with commits and diffs (pay attention to added MRI and ruby/spec tests)
- check existing ruby/spec tests in the TruffleRuby repository
- add missing test cases
- untag ruby/spec tests
- implement the feature
- untag MRI tests and ensure they pass too
- mention in a PR description an original issue in a way it's described in the Ruby
  release notes or provide a link to an original ticket (on <https://bugs.ruby-lang.org>)

## How to prepare PR for changes in the next CRuby version

When you work on a new feature that is introduced in Ruby version which TruffleRuby doesn't support yet
(e.g. TruffleRuby is compatible with Ruby 3.1 now but the feature is introduced in Ruby 3.2) there is a
problem with running proper specs locally and on CI (both internal and GitHub Actions).

So you should ensure that the specs for the new feature are run and passed successfully on CI and the
whole suit pass successfully as well:

- add specs for the new feature if they are missing (with a `ruby_version_is` guard)
- use `PRETEND_RUBY_VERSION` environment variable to run specs for the new feature
- add a file with specs for the new feature to the next list in `spec/truffleruby.next-specs` (to run them on CI)
- use `jt test :next` to run locally all the specs for the next CRuby version

If the new feature breaks existing behavior and some specs fail - disable them temporary (until TruffleRuby
is switched to the next CRuby version completely) with tagging as failed (use `jt tag <path-to-spec-file>`)

Look for additional details in [The "Running specs for Ruby 3.3 features" section of the Contributor Workflow document](workflow.md#running-specs-for-ruby-33-features).

## How to use jt CLI tool

The `jt` CLI tool has a lot of commands but there are very few basic ones that you may need to use often:

- `jt ruby` - run TruffleRuby REPL (`irb`)
    - `jt ruby <filename>` - execute a file with Ruby source code with TruffleRuby
    - `jt ruby -e '<Ruby source code>'` - execute a string of Ruby source code with TruffleRuby REPL
    - `jt -q ruby --jdebug -e '<Ruby source code>'` - to launch debugger
- `jt test <path to test file>` - run ruby/spec test file
    - `jt --use ruby test <path to test file>` - to use tests against current CRuby, not TruffleRuby
        - `jt -u ruby test <path to test file>` - there is a short form `-u`
    - `jt test <path to test file> -- --jdebug` - to launch debugger
    - `jt test fast <path to test file>` - run subset of specs and tag slow test cases with tag `slow`
    - `jt test :next` - run specs for features introduced in the next CRuby version, not supported by TruffleRuby currently
- `jt test mri <path to test file>` - run MRI test file
  - `jt test mri <path to test file> --jdebug` - to launch debugger for MRI tests (without `--`)
  - `jt test mri <path to MRI test file> -- -n <test case name>` - to run only specified UnitTest case
- `jt build`
- `jt clean`
- `jt rebuild`
- `jt sync` - to synchronize changes in Standard Library Ruby source code and apply them without rebuilding TruffleRuby
- `jt tag` - ruby/spec only
- `jt untag <path-to-file>` - (ruby/spec only) regenerate an exclude file for particular ruby/spec file (and list all the failed test cases)
- `jt retag <path-to-file>` - (MRI test only) regenerate an exclude file for particular MRI test file (and list all the failed test cases). Do nothing if there aren't any already excluded cases for this file.
- `jt lint fast` - to run some linter checks (that are run on CI) locally
- `jt rubocop` - to run Ruby source code linter separately from other linter checks.
    - `jt rubocop -a` - to correct safely Rubocop warnings automatically
    - `jt rubocop -A` - to correct unsafely Rubocop warnings automatically
