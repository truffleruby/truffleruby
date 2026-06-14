# Truffle Interop

TruffleRuby supports the standard interop message system of the Truffle
Language Implementation Framework. This document explains what it does when it
receives them, how to get it to explicitly send them, how to get it to send
them using more idiomatic Ruby, and how what messages it sends for normal Ruby
operations on foreign objects.

This document only explains how TruffleRuby uses messages. The messages
themselves are explained in the
[Truffle JavaDoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html).

There is a separate document aimed at people using interop to write
[polyglot programs](../user/polyglot.md). The current document gives more internal
details.

* [How Ruby responds to messages](#how-ruby-responds-to-messages)
* [How to explicitly send messages from Ruby](#how-to-explicitly-send-messages-from-ruby)
* [How to send messages using idiomatic Ruby](#how-to-send-messages-using-idiomatic-ruby)
* [What messages are sent for Ruby syntax on foreign objects](#what-messages-are-sent-for-ruby-syntax-on-foreign-objects)
* [Conversion of primitive values](#conversion-of-primitive-values)
* [Import and export](#import-and-export)
* [Interop eval](#interop-eval)
* [Java interop](#java-interop)
* [Additional methods](#additional-methods)
* [Notes on method resolution](#notes-on-method-resolution)
* [Notes on coercion](#notes-on-coercion)
* [Notes on source encoding](#notes-on-source-encoding)

## How Ruby responds to messages

All interop message implementations of different Ruby object types are defined
in the Java class that represents the object (this is indicated by the `@ExportLibrary(InteropLibrary.class)`
annotation):

- `org.truffleruby.language.RubyDynamicObject`: Common behaviour for all, overriden by the other classes in this list.
- `org.truffleruby.core.array.RubyArray`
- `org.truffleruby.core.hash.RubyHash`
- `org.truffleruby.core.string.RubyString`
- `org.truffleruby.language.Nil`
- and many more (search for `@ExportLibrary(InteropLibrary.class)` for a full list)

As expected:
- `BasicObject` has polyglot members
- `Array` has polyglot array elements
- `Proc` and `Method` are polyglot executable
- `String` and `Symbol` are polyglot strings
- `Hash` has polyglot hash entries

Any Ruby object can implement the polyglot array, pointer or member behavior by
implementing the appropriate `polyglot_*` methods. It is called the **dynamic
polyglot API**. The list of the methods which have to be implemented can be
found in the [details](interop_details.md), see polyglot pointer, polyglot array, polyglot hash,
and polyglot members. If the `polyglot_*` method need to raise an
[InteropException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropException.html)
exception like
[UnsupportedMessageException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/UnsupportedMessageException.html)
it raises corresponding Ruby exception available in `Truffle::Interop` module.
The names are the same, e.g. `Truffle::Interop::UnsupportedMessageException`.
These exceptions inherit from `Exception`, therefore they are not caught by
default in `rescue` (which catches descendants of `StandardError`). Only `Truffle::Interop::ArityException`takes
arguments, two `Integer`s to describe the minimum and maximum number of expected arguments.

The detailed definitions of the behavior can be found in
[another document](interop_details.md).

## How to explicitly send InteropLibrary messages from Ruby - Explicit polyglot API

For every message in the
[InteropLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html)
there is a Ruby method in the `Truffle::Interop` module. The name of the message
is translated as follows:

- `snake_case` is used instead of `camelCase`
- `?` is appended for predicate messages (messages with `is` and `has` prefixes)
- the `is_` and `get_` prefixes are removed (`has_` is kept)

Few examples: `isString` becomes `string?`, `hasArrayElements` becomes
`has_array_elements?`, `getArraySize` becomes `array_size`.

If the message returns `void` the Ruby method returns `nil`.

If any of the sent messages fails then the Java `InteropException` is translated
to a Ruby exception as follows:

- `UnsupportedMessageException` → `Polyglot::UnsupportedMessageError`
- `InvalidArrayIndexException`  → `IndexError`
- `UnknownIdentifierException`  → `NameError` or `NoMethodError` if the message is `invokeMember`
- `ArityException`              → `ArgumentError`
- `UnsupportedTypeException`    → `TypeError`
- `UnknownKeyException`         → `KeyError`

## What messages are sent for Ruby syntax on foreign objects - Implicit polyglot API

The syntax for each message is detailed in the [Implicit Polyglot API documentation](interop_implicit_api.md).

TruffleRuby automatically provides these special methods on a foreign object.
They have priority over methods that the foreign object actually provides.

`object + other` and other operator method calls (method name not beginning with
a letter) will send `IS_BOXED` on `object` and based on that will possibly
`UNBOX` it, convert to Ruby, and then call the method on the unboxed version.

`java_object.is_a?(java_class)` does `java_object instanceof java_class`, using
the host object instance, rather than any runtime interop wrapper.

`object.is_a?(java_class)` does `object instanceof java_class`, using the
runtime object instance.

`foreign_object.is_a?(ruby_class)` returns `false`.

`foreign_object.is_a?(foreign_class)` raises a `TypeError`.

`foreign_object.kind_of?` works like `foreign_object.is_a?`.

`object.respond_to?(name)` for other names returns `false`.

`object.__send__(name, *args)` works in the same way as literal method call on
the foreign object, including allowing the special-forms listed above (see
[notes on method resolution](#notes-on-method-resolution)).

## Conversion of primitive values

A call from Ruby to a foreign language using `NEW`, `EXECUTE`, `INVOKE`, `READ`,
`WRITE`, or `UNBOX`, that returns a `byte`, `short` or `float` will convert the
returned value to respectively `int`, `int` or `double`. You can avoid this
conversion for `EXECUTE` using `Truffle::Interop.execute_without_conversion`,
for `READ` using `Truffle::Interop.read_without_conversion`, and for `UNBOX`
using `Truffle::Interop.unbox_without_conversion`.

`Polyglot.import` also converts `byte/short/float`.

## Import and export

`Truffle::Interop.export(:name, value)`

`Truffle::Interop.export_method(:name)` (looks for `name` in `Object`)

`value = Truffle::Interop.import(:name)`

`Truffle::Interop.import_method(:name)` (defines `name` in `Object`)

## Interop Eval

`Truffle::Interop.eval(mime_type, source)`

`Truffle::Interop.import_file(path)` evals an entire file, guessing the correct
language MIME type.

## Java interop

`Truffle::Interop.java_type(name)` returns a Java class object, given a name
such as `java.lang.Integer` or `int[]`.

`java_class.new(*args)` creates a new instance of a Java class object.

`Truffle::Interop.from_java_array(array)` creates a shallow copy of a Java
array as a Ruby array.

## Additional methods

`Truffle::Interop.mime_type_supported?(mime_type)` reports if a language's MIME
type is supported for interop.

`Truffle::Interop.foreign?(object)`

`Truffle::Interop.java?(object)`

`Truffle::Interop.java_class?(object)`

`Truffle::Interop.java_string?(object)`

`Truffle::Interop.to_java_string(ruby_string)`

`Truffle::Interop.from_java_string(java_string)`

`Truffle::Interop.enumerable(object)` gives you an `Enumerable` interface to a
foreign object.

`Truffle::Interop.to_java_array(array)` gives you a proxied Java array copied
from the Ruby array.

`Truffle::Interop.java_array(a, b, c...)` a literal variant of the former.

`Truffle::Interop.to_java_collection(array)` gives you a proxied Java `List`
copied from the Ruby array.

`Truffle::Interop.to_java_map(hash)` gives you a proxied Java `HashMap`
copied from the Ruby hash.

`Truffle::Interop.to_array(object)` converts to a Ruby array by calling
`GET_SIZE` and sending `READ` for each index from zero to the size.

`Truffle::Interop.to_hash(object)` converts to a Ruby hash by reading all
members.

`Truffle::Interop.meta_object(object)` returns the Truffle meta-object that
describes the object (unrelated to the metaclass).

`Truffle::Interop.unbox_if_needed(object)` calls `UNBOX` on an object if
`IS_BOXED` and it's a foreign object.

`Truffle::Interop.to_string(object)` calls the Java `toString` on the object.

`Truffle::Interop.identity_hash_code(object)` calls
`System.identityHashCode(object)`.

## Notes on method resolution

Method calls on foreign objects are usually translated exactly into foreign
`READ`, `INVOKE` and other messages. The other methods listed in
[what messages are sent for Ruby syntax on foreign objects](#what-messages-are-sent-for-ruby-syntax-on-foreign-objects)
are a kind of special-form - they are implemented as a special case in the
call-site logic. They are not being provided by `BasicObject` or `Kernel` as you
may expect. This means that for example `#method` isn't available, and you can't
use it to get the method for `#to_a` on a foreign object, as it is a
special-form, not a method.

Interop ignores visibility entirely.

## Notes on coercion

Methods such as `Kernel.Integer`, `Kernel.Float`, `Numeric#coerce` will call
`Truffle::Interop.unbox_if_needed` on objects, in order to unbox any boxed
foreign objects, before continuing with the normal coercion routine.

## Notes on source encoding

Sources created from the Polyglot API only contain a Java `String` and the
original bytes are not available to be re-interpreted in a different encoding
from the one already to create it. Therefore TruffleRuby interprets these
sources as UTF-8 and cannot set the file encoding using a magic comment, except
for encodings which are sub-sets of UTF-8, such as 7-bit ASCII.
