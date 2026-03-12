# Primitives

Primitives are like Ruby methods but they are more efficient because they do not involve a method call.
The Primitive Truffle AST node is added directly in the Truffle AST and so they always execute "inline".
They always have a fixed number of arguments.
They provide functionality which does not exist as regular Ruby methods or would be much slower.
In other words, they are also the most efficient way to call some Java logic from some Ruby code.

## Naming

Primitives are generally named `#{module or class to which it belongs}_#{name of the operation}` such as `string_start_with?`.

The module or class name should be without spaces, so e.g. `matchdata_` and not `match_data_` for Primitives related to `MatchData`.

The `object_` prefix should only be used for instance variables-related operations, like `object_ivars`.

For primitives which are not specific to a module or class, use no prefix, such as `Primitive.is_a?`/`Primitive.equal?`.

For primitives used in many places it is nice to have a shorter name.
OTOH for primitives used in a single place, e.g., to implement part of the logic in Ruby, then a longer name is fine.

## Public Primitives

Some Primitives are "public" which means they can be used outside the TruffleRuby core library.
`# truffleruby_primitives: true` is needed as a magic comment at the top of the file to enable them (without it, `Primitive.foo` is just a call to method `foo` on the constant `Primitive`).

Non-public Primitives cannot be used outside the TruffleRuby core library and will raise a SyntaxError during parsing.

We document every public Primitive here.

### Primitive.as_boolean(object)

Converts `object` to `true` or `false`.
Like `!!object` but not affected by `BasicObject#!` overrides and more efficient.

### Primitive.class(object)

Like `object.class` but not affected by `Kernel#class` overrides and also works on BasicObject instances.

### Primitive.singleton_class(object)

Like `object.singleton_class` but not affected by `Kernel#singleton_class` overrides and also works on BasicObject instances.

### Primitive.is_a?(object, Module|Class module)

Like `object.is_a?(module)` but not affected by `Kernel#is_a?` overrides and also works on BasicObject instances.

### Primitive.nil?(object)

Like `object.nil?` but not affected by `Kernel#nil?` overrides and also works on BasicObject instances.

### Primitive.convert_with_to_int(object)

Converts `object` to an `Integer` with `to_int`, efficiently and with the correct error handling.
Like `rb_to_int()` in the C API.

### Primitive.convert_with_to_str(object)

Converts `object` to a `String` with `to_str`, efficiently and with the correct error handling.
Like `StringValue()` in the C API.

### Primitive.convert_with_to_ary(object)

Converts `object` to an `Array` with `to_ary`, efficiently and with the correct error handling.

### Primitive.convert_with_to_hash(object)

Converts `object` to a `Hash` with `to_hash`, efficiently and with the correct error handling.

### Primitive.convert_type(object, Class, Symbol convert_method)

Converts `object` to an instance of `Class` with `convert_method`, efficiently and with the correct error handling.
Like `rb_convert_type()` in the C API.

### Primitive.regexp_search_with_start(Regexp, String, int from, int start)

Match the given Regexp on the given String from position `from` or after.
Like `String#match(Regexp, from)` but also makes it possible to set `start`, the position that matches `\A`.
`start` must either be `0` or the same as `from`.

### Primitive.regexp_match_at_start(Regexp, String, int from, int start)

Match the given Regexp on the given String at position `from`, and nowhere else.
Like `String#start_with?(Regexp)` but also makes it possible to set `from` and to set `start`, the position that matches `\A`.
`start` must either be `0` or the same as `from`.

### Primitive.matchdata_create_single_group(String|Regexp regexp, String source, int start, int end)

Creates a `MatchData` object with a single capture group (0) from `start` to `end`.
The `regexp` argument can be either a String or Regexp, because there are several cases when one wants to create a `MatchData` object though matching against a String and not a Regexp.

### Primitive.match_data_byte_begin(MatchData, int group)

Like `MatchData#bytebegin` but more efficient by only having to handle Integer group.
The Primitive existed before `MatchData#bytebegin` was added.

### Primitive.match_data_byte_end(MatchData, int group)

Like `MatchData#byteend` but more efficient by only having to handle Integer group.
The Primitive existed before `MatchData#byteend` was added.

### Primitive.string_byte_index_to_character_index(string, byte_index)

Converts a byte index to a character index for a String.
Like `string.byteslice(0, byte_index).length` but more efficient.

### Primitive.string_character_index_to_byte_index(string, character_index)

Converts a character index to a byte index for a String.
Like `string.slice(0, character).bytesize` but more efficient.

### Primitive.blackhole(value)

Ensures the computation of the given value is not optimized away, even if it would be otherwise unused.
Used for benchmarking purposes.

### Primitive.always_split(Module module, Symbol method)

For the given method on `module`, make a copy of it at every call site (i.e. wherever this method is called).
This is called method splitting or method cloning or monomorphization and is a performance optimization.
The main value is providing per-call-site profiling information, such as which branches are taken, and per-call-site inline caches, so that the JITed code is optimal for that call site and needs not to handle logic only needed for other call sites.
The downside is it uses more memory and increases warmup by having to compile N copies instead of one.
Should only be used when the trade-off is worth it by giving a very clear peak performance advantage.

One example is methods taking a block and calling it many times, such as `Integer#times` or `Array#each`.
With splitting those methods will know which block they call (through the inline cache for calling blocks, when given the same block at a given call site)
and have a more efficient call to the block and be able to inline the block.

Another example is methods doing Regexp matching, by splitting they will know which Regexp is used (e.g. when given a literal or constant Regexp at a call site) and be able to inline the Regexp vs having a generic call to a compiled Regexp.

Splitting is actually done automatically when detecting polymorphism or megamorphism,
but we do it explicitly with `Primitive.always_split` when we know it's always worth doing to be a bit more efficient.
Specifically it avoids an extra copy of the method, and ensures that we don't ever compile the generic version of the method but only the splits for specific call sites.
