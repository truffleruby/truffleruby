# 26.0.0

New features:

* `Hash` is now thread-safe on TruffleRuby, based on Benoit Daloze's PhD research (#3869, #3112, #2763, @eregon).
* TruffleRuby now ships with its own `libyaml` and no longer needs a system `libyaml` (#3479, @eregon).
* TruffleRuby now ships with its own `libssl` and no longer needs a system `libssl`. It still requires CA certificates from the system. (#3479, @eregon).

Bug fixes:


Compatibility:

* Fix extra warnings for `Kernel#sprintf` (#3938, @eregon).
* Support using tailwindcss-ruby's native platform gems (@nirvdrum).
* Fix a `TypeError` when calling `Kernel#warn` with explicit `uplevel: nil` (@earlopain).
* Fix inspect output of symbols that contain non-ASCII letters (@earlopain).
* Implement `rb_struct_initialize()` for both `Struct` and `Data` (#3997, @eregon).
* Fix `Hash#{select,slice,transform_values}` and `Hash.[]` methods and retain `compare_by_identity` flag (@andrykonchin).
* Fix `Hash#reject` and do not retain `default` and `default_proc` values (@andrykonchin).
* Fix `Comparable#clamp` when the given an infinite interval (#3945, @andrykonchin).

Performance:


Changes:

* The `--single-threaded` option now defaults to false also when embedding TruffleRuby (#3958, @eregon).

Memory Footprint:


# 25.0.0

New features:


Bug fixes:

* Fix `Range#cover?` on begin-less ranges and non-integer values (@nirvdrum, @rwstauner).
* Fix `Time.new` with String argument and handle nanoseconds correctly (#3836, @andrykonchin).
* Fix a possible case of infinite recursion when implementing `frozen?` in a native extension (@nirvdrum).
* Fix parameters forwarding to a method call executed with `Kernel#eval` (@andrykonchin).
* Fix segfaults in native extensions when the reference processing thread is interrupted and would `longjmp()` incorrectly (#3903, @eregon).

Compatibility:

* Support Timezone argument to `Time.{new,at}` and `Time#{getlocal,localtime}` (#1717, @patricklinpl, @manefz, @rwstauner).
* Updated to Ruby 3.3.7 (@andrykonchin).
* Implement `StringScanner#{peek_byte,scan_byte,scan_integer,named_captures}` methods (#3788, @andrykonchin).
* Support String patterns in `StringScanner#{exist?,scan_until,skip_until,check_until,search_full}` methods (@andrykonchin).
* Implement `ObjectSpace::WeakKeyMap` (#3681, @andrykonchin).
* Fix `String#slice` called with negative offset (@andrykonchin).
* Fix explicitly inherited `Struct` subclasses and don't provide `#members` method (#3802, @andrykonchin).
* Support Digest plugins (#1390, @nirvdrum).
* Joni has been updated from 2.2.1 to 2.2.6 (@andrykonchin).
* Fix numeric coercing when `#coerce` method is not public (#3848, @andrykonchin).
* Fix `Kernel#raise` and don't override `cause` at exception re-raising (#3831, @andrykonchin).
* Return a pointer with `#type_size` of 1 for `Pointer#read_pointer` (@eregon).
* Fix `rb_str_locktmp()` and `rb_str_unlocktmp()` to raise `FrozenError` when string argument is frozen (#3752, @andrykonchin).
* Fix Struct setters to raise `FrozenError` when a struct is frozen (#3850, @andrykonchin).
* Fix `Struct#initialize` when mixed positional and keyword arguments (#3855, @andrykonchin).
* Fix `Integer.sqrt` for large values (#3872, @tompng).
* Fix `Data#inspect` when data contains a recursive attribute (#3847, @andrykonchin).
* Fix `StringIO#{gets,readline}` when it is called with both separator and limit to truncate the separator if the limit is exceeded (#3856, @andrykonchin).
* Implement `rb_error_frozen_object` for the google-protobuf gem (@nirvdrum).
* Adjust a `FrozenError`'s message and add a receiver when a frozen module or class is modified (e.g. by defining or undefining an instance method or by defining a nested module (@andrykonchin).
* Fix `Kernel#sprintf` and `%p` format specification to produce `"nil"` for `nil` argument (#3846, @andrykonchin).
* Reimplement `Data#with` to not call `Data.new` that can be removed or redefined (#3890, @andrykonchin).

Performance:

* Use TRegex for binary Regexps with non-US-ASCII characters in the pattern like `/[\x80-\xff]/n` (#3858, @eregon).
* Run C extensions marked as `rb_ext_ractor_safe()` or `rb_ext_thread_safe()` in parallel (without the C extension lock) (#2136, @eregon).

Changes:


Memory Footprint:


# 24.2.0

New features:

* Updated to Ruby 3.3.5 (#3681, @andrykonchin, @eregon).

Bug fixes:

* Fix `Module#name` called inside the `Module#const_added` callback when the module is defined in the top-level scope (#3683, @andrykonchin).
* Fix duplicated calls of a `Module#const_added` callback when a module with nested modules is assigned to a constant (@andrykonchin).
* Support OpenSSL 1.1-3.4 and prefer in order OpenSSL 3.0.x, 3.x and 1.1 (EOL). There was a compilation issue with OpenSSL 3.4 (#3724, @eregon).
* Fix `Time{.at,.new,.now,#getlocal,#localtime}` methods and validation of seconds in utc offset in String format (@andrykonchin).
* Fix `ObjectSpace.undefine_finalizer` and raise `FrozenError` when called for a frozen object (@andrykonchin).
* Fix `Integer#/` when called with a bignum argument (@andrykonchin).

Compatibility:

* Fix `Module#include` so a module included into a reopened nested module is added into an ancestors chain (#3570, @andrykonchin).
* Fix `Kernel#eval` to ignore shebang with non-Ruby interpreter (#3623, @andrykonchin).
* Fix `Env#delete` and return value returned by a block if variable doesn't exist (@andrykonchin).
* Fix `Env#update` and accept multiple hashes (@andrykonchin).
* Add `MAJOR`, `MINOR`, `TEENY`, `PATCHLEVEL`, `RUBY_API_VERSION`, and `RUBY_PROGRAM_VERSION` to `RbConfig::CONFIG` (#3396, @rwstauner).
* Set `RbConfig::CONFIG['archincludedir']` (#3396, @andrykonchin).
* Support the index/length arguments for the string argument to `String#bytesplice` added in 3.3 (#3656, @rwstauner).
* Implement `rb_str_strlen()` (#3697, @Th3-M4jor).
* Support `Time.new` with String argument and error when invalid (#3693, @rwstauner).
* Implement `rb_enc_interned_str()` (#3703, @Th3-M4jor).
* Implement `rb_hash_bulk_insert()` (#3705, @Th3-M4jor).
* Remove deprecated `Pathname#{taint,untaint}` methods (#3681, @andrykonchin).
* Add `rb_category_warn` function (#3710, @andrykonchin).
* Add `rb_gc_mark_locations()` (#3704, @andrykonchin).
* Implement `rb_str_format()` (#3716, @andrykonchin).
* Add `IO#{pread, pwrite}` methods (#3718, @andrykonchin).
* Add `rb_io_closed_p()` (#3681, @andrykonchin).
* Add `rb_io_open_descriptor()` (#3681, @andrykonchin).
* Support serializing of `Data` instances into Marshal format (#3726, @andrykonchin).
* `Array#pack` now raises `ArgumentError` for unknown directives (#3681, @Th3-M4jor).
* `String#unpack` now raises `ArgumentError` for unknown directives (#3681, @Th3-M4jor).
* `Thread::Queue#freeze` now raises `TypeError` when called (#3681, @Th3-M4jor).
* `Thread::SizedQueue#freeze` now raises `TypeError` when called (#3681, @Th3-M4jor).
* Add `Range#reverse_each` (#3681, @andrykonchin).
* Emit a warning when `it` call without arguments is used in a block without parameters (#3681, @andrykonchin).
* Add `rb_syserr_fail_str()` (#3732, @andrykonchin).
* Add `Dir.for_fd` (#3681, @andrykonchin).
* Add `Dir.fchdir` (#3681, @andrykonchin).
* Add `Dir#chdir` (#3681, @andrykonchin).
* Declare `File::SHARE_DELETE` constant (#3745, @andrykonchin).
* Support `symbolize_names` argument to `MatchData#named_captures` (#3681, @rwstauner).
* Support `Proc#initialize_{dup,copy}` for subclasses (#3681, @rwstauner).
* Remove deprecated `Encoding#replicate` method (#3681, @rwstauner).
* Add `ObjectSpace::WeakMap#delete` (#3681, @andrykonchin).
* `Kernel#lambda` with now raises `ArgumentError` when given a non-lambda, non-literal block (#3681, @Th3-M4jor).
* Add `rb_data_define()` to define Data (#3681, @andrykonchin).
* Add `Refinement#target` (#3681, @andrykonchin).
* Add `Range#overlap?` (#3681, @andrykonchin).
* Update `NoMethodError#message` to not use `#inspect` on receiver (#3681, @rwstauner).
* Socket `#recv*` methods (`{BasicSocket,IPSocket,TCPSocket,UDPSocket,Socket}#{recv,recv_nonblock,recvmsg,recvmsg_nonblock,recvfrom,recvfrom_nonblock}`) return `nil` instead of an empty String on closed connections (#3681, @andrykonchyn).
* Fix `Marshal.dump` when a Float value is dumped repeatedly (#3747, @andrykochin).
* Emit warning when `Kernel#format` called with excessive arguments (@andrykonchin).
* Fix `Integer#ceil` when self is 0 (@andrykonchin).
* Fix `Module#remove_const` and emit warning when constant is deprecated (@andrykonchin).
* Add `Module#set_temporary_name` (#3681, @andrykonchin).
* Modify `Float#round` to match MRI behavior (#3676, @andrykonchin).

Performance:

* Speedup some C extensions like `sqlite3`, `trilogy` and `json` by 2 to 3 times by using the Panama NFI backend for faster upcalls in JVM mode (@eregon).
* Optimize encoding negotiation for ASCII-compatible encodings (@eregon, @andrykonchin).

Changes:
* Inherit `Polyglot::ForeignException` from `StandardError` instead of `Exception` (#3620, @andrykonchin).

Memory Footprint:

# 24.1.0

New features:

* Add `--reuse-precompiled-gems` option (@andrykonchin).
* Update to Ruby 3.2.4 (@andrykonchin).

Bug fixes:

* Add missing thread-safe objects write barriers for `TruffleRuby::ConcurrentMap` (#3179, @eregon).
* Fix repeated calling of methods `Dir#{each,each_child,children}` (#3464, @andrykonchin).
* Fix `IO#{wait,wait_readable,wait_writable}` methods and switch the current thread into a sleep state (@andrykonchin).
* Fix `rb_global_variable()` for `Float` and bignum values during the `Init_` function (#3478, @eregon).
* Fix `rb_gc_register_mark_object()` for `Float` and bignum values (#3502, @eregon, @andrykonchin).
* Fix parsing literal floats when the locale does not use `.` for the decimal separator (e.g. `LANG=fr_FR.UTF-8`) (#3512, @eregon).
* Fix `IO#{read_nonblock,readpartial,sysread}`, `BasicSocket#{recv,recv_nonblock}`, `{Socket,UDPSocket}#recvfrom_nonblock`, `UnixSocket#recvfrom` and preserve a provided buffer's encoding (#3506, @andrykonchyn).
* Repair `IO#{wait_readable,wait_writable,wait}` to be interruptible (#3504, @andrykonchin).
* Fix Hash value omission for constant names (@andrykonchin).
* Fix `MatchData#[index, length]` when index is larger than number of matched values (@andrykonchin).
* Fix `#each` for a foreign iterator which is also iterable (#3630, @eregon).

Compatibility:

* Move `IO#wait_readable`, `IO#wait_writable`, `IO#wait_priority` and `IO#wait` into core library (@andrykonchin).
* Change assignment evaluation order for fully qualified constant and evaluate left-hand-side before right-hand-side (#3039, @andrykonchin).
* Fix evaluation order for multi-assignment and evaluate left-hand-side before right-hand-side (@andrykonchin).
* Add `Regexp.linear_time?` method (#3039, @andrykonchin).
* Allow null encoding pointer in `rb_enc_interned_str_cstr` (@thomasmarshall).
* Allow anonymous memberless Struct (@simonlevasseur).
* Set `$!` when a `Kernel#at_exit` hook raises an exception (#3535, @andrykonchin).
* Support `:buffer` keyword argument to `Array#pack` (#3559, @andrykonchyn).
* Set `RbConfig::CONFIG['host_cpu']` to `arm64` on darwin platform (#3571, @andrykonchin).
* Fix `RegexpError` messages to match CRuby better (#3398, @andrykonchin).
* Fix `Enumerable#reduce` to handle non-Symbol method name parameter (#2931, @andrykonchin).
* Fix `RangeError` message to match CRuby for `Integer#chr` called with invalid codepoint argument (#2795, @andrykonchin).
* Joni has been updated from 2.1.44 to 2.2.1 (@andrykonchin).
* Fix `Hash#to_h` called with a block and pass key and value to the block as separate arguments (#3607, @andrykonchin).
* Fix `StringIO#initialize` and preserve initial string's encoding when mode is `w` so the initial string is truncated (#3599, @andrykonchin).
* Fix `IO#{autoclose=,autoclose?}` and raise `IOError` when io is closed (@andrykonchin).
* Fix `Thread#{thread_variable_get,thread_variable_set,thread_variable?,key?,[],[]=,fetch}` and convert a non-String/Symbol thread-local variable name to String using `#to_str` (@andrykonchin).
* Fix formatting in `Exception#full_message` when `RuntimeError` is not handled and `highlight` option is specified (@andrykonchin).
* Fix `String#encode` and convert fallback values into String using `#to_str` (@andrykonchin).
* Fix `Kernel.warn` and don't call `Warning#warn` if a specified category is disabled (@andrykonchin).
* Fix `$!` global variable and make it fiber-local (@andrykonchin).
* Fix `rb_set_errinfo` and `rb_errinfo` and store an error separately from `$!` (#2890, @andrykonchin).
* Fix `rb_mutex_synchronize` to not wrap/unwrap result value (#3624, @andrykonchin).
* Add `StringIO#set_encoding_by_bom` method (#3632, @andrykonchin).

Performance:

* Fix inline caching for Regexp creation from Strings (#3492, @andrykonchin, @eregon).
* Optimize `Integer#pow` method for small modulus values (#3544, @andrykonchin).
* Avoid repeated copies from native to managed string when matching Regexps on a native string (#2193, @eregon).

Changes:

Memory Footprint:

* Use inlined core method nodes even when modules are prepended to core classes (#3546, @eregon).

# 24.0.0

New features:

* C/C++ extensions are now compiled using the system toolchain and executed natively instead of using GraalVM LLVM (Sulong). This leads to faster startup, no warmup, better compatibility, smaller distribution and faster installation for C/C++ extensions (#3118, @eregon).
* Full support for the Ruby 3.2 (and Ruby 3.3) _syntax_ by adopting the [Prism](https://github.com/ruby/prism) parser, which is about twice as fast as the old parser (#3117, #3038, #3039, @andrykonchin, @eregon).
* Pattern matching is now fully supported (#3332, #2683, @eregon, @razetime).

Bug fixes:

* Fix `rb_enc_left_char_head()` so it is not always `ArgumentError` (#3267, @eregon).
* Fix `IO.copy_stream` with a `Tempfile` destination (#3280, @eregon).
* Fix `Regexp.union` negotiating the wrong result encoding (#3287, @nirvdrum, @simonlevasseur).
* Fix `Proc#parameters` and return all the numbered parameters lower than the used explicitly ones (@andrykonchin).
* Fix some C API functions which were failing when called with Ruby values represented as Java primitives (#3352, @eregon).
* Fix `IO.select([io], nil, [io])` on macOS, it was hanging due to a bug in macOS `poll(2)` (#3346, @eregon, @andrykonchin).
* Run context cleanup such as showing the output of tools when `SignalException` and `Interrupt` escape (@eregon).
* Handle a new variable inside the `case` target expression correctly (#3377, @eregon).
* The arguments of `Thread.new(*args, &block)` need to be marked as shared between multiple threads (#3179, @eregon).
* Fix `Range#bsearch` and raise `TypeError` when range boundaries are non-numeric and block not passed (@andrykonchin).
* Fix using the `--cpusampler` profiler when there are custom unblock functions for `rb_thread_call_without_gvl()` (#3013, @eregon).
* Fix recursive raising `FrozenError` exception when redefined `#inspect` modifies an object (#3388, @andrykonchin).
* Fix `Integer#div` returning the wrong object type when the divisor is a `Rational` (@simonlevasseur, @nirvdrum).
* Remove constant `Random::DEFAULT` (#3039, @patricklinpl)

Compatibility:

* Add `Exception#detailed_message` method (#3257, @andrykonchin).
* Fix `rb_enc_vsprintf` and force String encoding instead of converting it (@andrykonchin).
* Add `rb_gc_mark_movable` function (@andrykonchin).
* Promote `File#path` and `File#to_path` to `IO#path` and `IO#to_path` and make IO#new accept an optional `path:` keyword argument (#3039, @moste00)
* Display "unhandled exception" as the message for `RuntimeError` instances with an empty message (#3255, @nirvdrum).
* Set `RbConfig::CONFIG['configure_args']` for openssl and libyaml (#3170, #3303, @eregon).
* Support `Socket.sockaddr_in(port, Socket::INADDR_ANY)` (#3361, @mtortonesi).
* Implement the `Data` class from Ruby 3.2 (#3039, @moste00, @eregon).
* Make `Coverage.start` and `Coverage.result` accept parameters (#3149, @mtortonesi, @andrykonchin).
* Implement `rb_check_funcall()` (@eregon).
* Implement `MatchData#{byteoffset,deconstruct,deconstruct_keys}` from Ruby 3.2 (#3039, @rwstauner).
* Add `Integer#ceildiv` method (#3039, @simonlevasseur, @nirvdrum).
* Implement `Class#attached_object` method (#3039, @andrykonchin).
* Fix `ENV#{clone,dup}` and raise `TypeError` (#3039, @andrykonchin).
* Fix `Coverage.supported?` and raise `TypeError` if argument is not Symbol (#3039, @andrykonchin).
* Accept options argument to `Regexp.{new,compile}` of String and warn for unknown types (#3039, @rwstauner).
* Implement `Time#deconstruct_keys` from Ruby 3.2 (#3039, @rwstauner).
* Do not autosplat a proc that accepts a single positional argument and keywords (#3039, @andrykonchin).
* Support passing anonymous * and ** parameters as method call arguments (#3039, @andrykonchin).
* Handle either positional or keywords arguments by default in `Struct.new` (#3039, @rwstauner).
* Promote `Set` class to core library (#3039, @andrykonchin).
* Support `connect_timeout` keyword argument to `TCPSocket.{new,open}` (#3421, @manefz, @patricklinpl, @nirvdrum, @rwstauner).
* Add `File.lutime` and `Pathname#lutime` methods (#3039, @andrykonchin).
* Add a deprecation warning for `Encoding#replicate` (#3039, @patricklinpl, @manefz, @nirvdrum).
* Change `UnboundMethod#{==,inspect}` to use the owner module rather than the origin (#3039, @rwstauner, @manefz, @patricklinpl)
* Support `lambda` keyword argument in `Proc#parameters` (#3039, @thomasmarshall, @goyox86).
* Limit maximum encoding set size by 256 (#3039, @thomasmarshall, @goyox86).
* Remove deprecated methods `Dir.exists?`, `File.exists?`, and `Kernel#=~` (#3039, @patricklinpl, @nirvdrum).
* Remove deprecated `FileTest.exists?` method (#3039, @andrykonchin).
* Fix {Method,Proc}#parameters and return `*`, `**` and `&` names for anonymous parameters (@andrykonchin).
* Remove deprecated `Fixnum` and `Bignum` constants (#3039, @andrykonchin).
* Add `rb_enc_interned_str_cstr` function (#3408, @goyox86, @thomasmarshall).
* Add `rb_str_to_interned_str` function (#3408, @thomasmarshall).
* Add `SyntaxError#path` method (#3039, @wasabigeek).

Performance:

* Change the `Hash` representation from traditional buckets to a "compact hash table" for improved locality, performance and memory footprint (#3172, @moste00).
* Optimize calls with `ruby2_keywords` forwarding by deciding it per call site instead of per callee thanks to [my fix in CRuby 3.2](https://bugs.ruby-lang.org/issues/18625) (@eregon).
* Optimize feature loading when require is called with an absolute path to a .rb file (@rwstauner).
* Avoid extra copies for Strings passed as `:string` arguments to a FFI call and used later for Regexp matching (#3293, @eregon).

Changes:


Memory Footprint:


# 23.1.0

New features:

* Updated to Ruby 3.2.2 (#3039, @eregon, @andrykonchin).
* TruffleRuby Native on Oracle GraalVM on Linux now uses the G1 garbage collector which is much faster (@eregon).

Bug fixes:

* Fix `Dir.glob` returning blank string entry with leading `**/` in glob and `base:` argument (@rwstauner).
* Fix class lookup after an object's class has been replaced by `IO#reopen` (@itarato, @nirvdrum, @eregon).
* Fix `Marshal.load` and raise `ArgumentError` when dump is broken and is too short (#3108, @andrykonchin).
* Fix `super` method lookup for unbounded attached methods (#3131, @itarato).
* Fix `Module#define_method(name, Method)` to respect `module_function` visibility (#3181, @andrykonchin).
* Fix stack overflow with `Kernel.require` and `zeitwerk` (#3224, @eregon).
* Reimplement `IO.select` with `poll(2)` to support file descriptors >= 1024 (#3201, @eregon).

Compatibility:

* Fix `Hash#shift` when Hash is empty but has initial default value or initial default proc (#3039, @itarato).
* Make `Array#shuffle` produce the same results as CRuby (@rwstauner).
* Add `Process.argv0` method (@andrykonchin).
* Add support for array pattern matching. This is opt-in via `--pattern-matching` since pattern matching is not fully supported yet (#2683, @razetime).
* Fix `Array#[]` with `ArithmeticSequence` argument when step is negative (#3039, @itarato).
* Fix `Range#size` and return `nil` for beginningless Range when end isn't Numeric (#3039, @rwstauner).
* Alias `String#-@` to `String#dedup` (#3039, @itarato).
* Fix `Pathname#relative_path_from` to convert string arguments to Pathname objects (@rwstauner).
* Add `String#bytesplice` (#3039, @itarato).
* Add `String#byteindex` and `String#byterindex` (#3039, @itarato).
* Add implementations of `rb_proc_call_with_block`, `rb_proc_call_kw`, `rb_proc_call_with_block_kw` and `rb_funcall_with_block_kw` (#3068, @andrykonchin).
* Add optional `timeout` argument to `Thread::Queue#pop` (#3039, @itarato).
* Add optional `timeout` argument to `Thread::SizedsQueue#pop` (#3039, @itarato).
* Handle `long long` and aliases in `Fiddle` (#3128, @eregon).
* Add `Module#refinements` (#3039, @itarato).
* Add `Refinement#refined_class` (#3039, @itarato).
* Add `rb_hash_new_capa` function (#3039, @itarato).
* Fix `Encoding::Converter#primitive_convert` and raise `FrozenError` when a destination buffer argument is frozen (@andrykonchin).
* Add `Module#undefined_instance_methods` (#3039, @itarato).
* Add `Thread.each_caller_location` (#3039, @itarato).
* Add `timeout` argument to `Thread::SizedQueue#push` (#3039, @itarato).
* Add `rb_syserr_new` function (@rwstauner).
* Add `Enumerator#product` (#3039, @itarato).
* Add `Module#const_added` (#3039, @itarato).
* Show the pointer size information (if available) in `FFI::Pointer#inspect` (@nirvdrum).
* Implement performance warnings (`Warning[:performance]`) like in CRuby 3.3 (@eregon).
* The output of `Marshal.dump` is now compatible with CRuby for `Rational` and `Complex` instances (#3228, @eregon).

Performance:

* Improve `Truffle::FeatureLoader.loaded_feature_path` by removing expensive string ops from a loop. Speeds up feature lookup time (#3010, @itarato).
* Improve `String#-@` performance by reducing unnecessary data copying and supporting substring lookups (@nirvdrum)
* Specialize `Array#<<` and related methods appending elements per call site to have a single array storage strategy in the inline cache for most cases (@eregon).

Changes:

* `gu install $LANGUAGE` is replaced by `truffleruby-polyglot-get $LANGUAGE`, available in the TruffleRuby JVM standalone (@eregon).
* The TruffleRuby `ScriptEngine` implementation is removed in favor of the generic [ScriptEngine](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md#compatibility-with-jsr-223-scriptengine) in GraalVM docs (@eregon).

Memory Footprint:

* Replaced `RubyLibrary` with `FreezeNode` and `IsFrozenNode` (@horakivo).
* Address many truffle-sharing warnings (@horakivo).
* Address many truffle-inlining warnings (@horakivo).


# 23.0.0

New features:

* Updated to Ruby 3.1.3 (#2733, @andrykonchin, @eregon).
* `foreign_object.is_a?(foreign_meta_object)` is now supported (@eregon).
* Foreign big integers are now supported and work with all `Numeric` operators (@eregon).

Bug fixes:

* Ensure every parse node has a source section and fix the source section for `ensure` (#2758, @eregon).
* Fix `spawn(..., fd => fd)` on macOS, it did not work due to a macOS bug (@eregon).
* Fix `rb_gc_register_address()`/`rb_global_variable()` to read the latest value (#2721, #2734, #2720, @eregon).
* Synchronize concurrent writes to the same StringIO (@eregon).
* Fix `StringIO#write(str)` when `str` is of an incompatible encoding and position < buffer size (#2770, @eregon).
* Fix `rb_thread_fd_select()` to correctly initialize fdset copies and handle the timeout (@eregon).
* Fix `TracePoint#inspect` when it's called outside of a callback (@andrykonchin).
* Fix `Signal.trap` when signal argument is not supported (#2774, @andrykonchin).
* Fix `Dir.mkdir` and convert permissions argument to `Integer` (#2781, @andrykonchin).
* Fix `String#dump` and use `\u{xxxx}` notation (with curly brackets) for characters that don't fit in `\uxxxx` (#2794, @andrykonchin).
* Fix `Marshal.dump` when big Integer (that cannot be expressed with 4 bytes) is serialized (#2790, @andrykonchin).
* Fix `Array#pack` and accept `Numeric` values when `Float` is expected (#2815, @andrykonchin).
* Fix `\P{}` matching in regular expressions (#2798, @andrykonchin).
* Fix constants lookup when `BasicObject#instance_eval` method is called with a String (#2810, @andrykonchin).
* Don't trigger the `method_added` event when changing a method's visibility or calling `module_function` (@paracycle, @nirvdrum).
* Fix `rb_time_timespec_new` function to not call `Time.at` method directly (@andrykonchin).
* Fix `StringIO#write` to transcode strings with encodings that don't match the `StringIO`'s `external_encoding` (#2839, @flavorjones).
* Fix processing of proc rest arguments located at the beginning if there are no actual arguments (#2921, @andrykonchin).
* Fix `Monitor#exit` to raise `ThreadError` when monitor not owned by the current thread (#2922, @andrykonchin).
* Fix `MatchData#[]` to support negative `length` argument (#2929, @andrykonchin).
* Fix `IO` line reading calls when using a multi-byte delimiter (`IO#{each,gets,readline,readlines,etc.}`) (#2961, @vinistock, @nirvdrum).
* Fix the exception type raised when type coercion raises a `NoMethodError` (#2903, @paracycle, @nirvdrum).
* Fix `Method` and `Proc` `#parameters` method to return `_` parameter name without synthetic suffix when there are multiple `_` parameters (@paracycle).
* Fixed errors in IRB when attempting to navigate beyond bounds in singleline mode (@rwstauner).

Compatibility:

* Fix `MatchData#[]` when passed unbounded Range (#2755, @andrykonchin).
* Updated `rb_define_class`, `rb_define_class_under`, and `rb_define_class_id_under` to allow class names that aren't valid in Ruby (#2739, @nirvdrum).
* Fixed `rb_gv_get` so that it no longer implicitly creates global variables (#2748, @nirvdrum).
* Added implementations of `rb_gvar_val_getter` and `rb_define_virtual_variable` (#2750, @nirvdrum).
* Implement `rb_warning_category_enabled_p` to support the `syntax_tree` gem (#2764, @andrykonchin).
* Fix desctructuring of a single block argument that implements `#to_ary` dynamically (#2719, @andrykonchin).
* Fix `Kernel#Complex` and raise exception when an argument is formatted incorrectly (#2765, @andrykonchin).
* Add `#public?`, `#private?` and `#protected?` methods for `Method` and `UnboundMethod` classes (@andrykonchin).
* Add optional argument to `Thread::Queue.new` (@andrykonchin).
* Support a module as the second argument of `Kernel#load` (@andrykonchin).
* Improve argument validation in `Struct#valies_at` - raise `IndexError` or `RangeError` when arguments are out of range (#2773, @andrykonchin).
* Fix `MatchData#values_at` and handling indices that are out of range (#2783, @andrykonchin).
* Add support for `%-z` (UTC for unknown local time offset, RFC 3339) to `Time#strftime` (@andrykonchin).
* Add support for `UTC` and `A`-`Z` utc offset values, as well as `+/-HH`, `+/-HHMM`, `+/-HHMMSS` (without `:`) (@andrykonchin).
* Treat time with `UTC`, `Z` and `-00:00` utc offset as UTC time (@andrykonchin).
* Raise `FrozenError` when `Time#localtime`, `Time#utc` and `Time#gmtime` is called on a frozen time object (@andrykonchin).
* Validate a microseconds argument used to create a time object (@andrykonchin).
* Support accessing `dmark` and `dfree` fields for `RData` (#2771, @eregon).
* Implement `rb_enc_nth()` (#2771, @eregon).
* Support `offset` keyword argument for `String#unpack` and `String#unpack1` (@andrykonchin).
* Fix `Process.detach` and cast `pid` argument to `Integer` (#2782, @andrykonchin).
* `rb_to_id()` should create a static `ID`, used by RMagick (@eregon).
* Resolve the current user home even when `$HOME` is not set (#2784, @eregon).
* Fix `IO#lineno=` and convert argument to `Integer` more strictly (#2786, @andrykonchin).
* Fix argument implicit convertion in `IO#pos=` and `IO#seek` methods (#2787, @andrykonchin).
* Warn about unknown directive passed to `Array#pack` in verbose mode (#2791, @andrykonchin).
* Added constants `IO::SEEK_DATE` and `IO::SEEK_HOLE` (#2792, @andrykonchin).
* Fix `StringIO.new` to accept keyword arguments (#2793, @andrykonchin).
* `Process#spawn` should call `#to_io` on non-IO file descriptor objects (#2809, @jcouball).
* Add constants `IO::SEEK_DATE` and `IO::SEEK_HOLE` (#2792, @andrykonchin).
* Add `Class#subclasses` method (#2733, @andrykonchin).
* Implement `Coverage.running?` method (@andrykonchin).
* Fix arguments implicit type conversion for `Enumerable#zip` and `Array#zip` (#2788, @andrykonchin).
* Fix `Array#unshift` to not depend on `Array#[]=` and allow overriding `#[]=` in a subclass (#2772, @andrykonchin).
* Fix syntactic check for `void value expression` (#2821, @eregon).
* Fix `Range#step` with no block and non-`Numeric` values (#2824, @eregon).
* Fix execution order of `END` blocks and `at_exit` callbacks (#2818, @andrykonchin).
* Fix `String#casecmp?` for empty strings of different encodings (#2826, @eregon).
* Implement `Enumerable#compact` and `Enumerator::Lazy#compact` (#2733, @andrykonchin).
* Implement `Array#intersect?` (#2831, @nirvdrum).
* Record the source location in the constant for the `module`/`class` keywords (#2833, @eregon).
* Fix `File.open` and support `flags` option (#2820, @andrykonchin).
* Support writing to `RData.dfree` for native extensions (#2830, #2732, #2165, @eregon).
* Fix `IO#write` and support multiple arguments with different encodings (#2829, @andrykonchin).
* Fix `Array` methods `reject`, `reject!`, `inject`, `map`, `select`, `each_index` and handle a case when array is modified by a passed block like CRuby does (#2822, andrykonchin, @eregon).
* Fix `EncodingError` exception message when Symbol has invalid encoding (#2850, @andrykonchin).
* Raise `EncodingError` at parse time when Hash literal contains a Symbol key with invalid encoding (#2848, @andrykonchin).
* Fix `Array` methods `reject`, `reject!`, `inject`, `map`, `select`, `each_index` and handle a case when array is modified by a passed block like CRuby does (#2822, @andrykonchin, @eregon).
* Fix `Array` methods `select!` and `keep_if` and handle a case when exception is raised in a passed block properly (@andrykonchin).
* Fix `Enumerable` methods `each_cons` and `each_slice` to return receiver (#2733, @horakivo).
* `Module` methods `#private`, `#public`, `#protected`, `#module_function` now returns their arguments like in CRuby 3.1 (#2733, @horakivo).
* `Kernel#exit!`, killing Fibers and internal errors do not run code in `ensure` clauses anymore, the same as CRuby (@eregon).
* Implement `UnboundMethod#original_name` (@paracycle, @nirvdrum).
* Implement `Thread#native_thread_id` method (#2733, @horakivo).
* Modify `Struct#{inspect,to_s}` to match MRI when the struct is nested inside of an anonymous class or module (@st0012, @nirvdrum).
* `Fiber.current` and `Fiber#transfer` are available without `require 'fiber'` like in CRuby 3.1 (#2733, @eregon).
* Add `freeze` keyword argument to `Marshal.load` (#2733, @andrykonchin).
* Add `Integer.try_convert` (#2733, @moste00, @eregon).
* Support optional `:in` keyword argument for `Time.now` and `Time.new` (#2733, @andrykonchin).
* Add optional `Hash` argument to `Enumerable#tally` (#2733, @andrykonchin).
* Update `$LOAD_PATH.resolve_feature_path` to return `nil` instead of raising `LoadError` when feature isn't found (#2733, @andrykonchin).
* Add `objspace/trace` file (#2733, @andrykonchin).
* Add `Process._fork` (#2733, @horakivo).
* Update to JCodings 1.0.58 and Joni 2.1.44 (@eregon).
* Add `MatchData#match` and `MatchData#match_length` (#2733, @horakivo).
* Add `StructClass#keyword_init?` method (#2377, @moste00).
* Support optional `level` argument for `File.dirname` method (#2733, @moste00).
* Add `Thread::Backtrace.limit` method (#2733, @andrykonchin).
* Deprecate `rb_gc_force_recycle` and make it a no-op function (#2733, @moste00).
* Add `Refinement#import_methods` method and add deprecation warning for `Refinement#include` and `Refinement#prepend` (#2733, @horakivo).
* Upgrading `UNICODE` version to 13.0.0 and `EMOJI` version to 13.1 (#2733, @horakivo).
* Add `rb_io_maybe_wait_readable`, `rb_io_maybe_wait_writable` and `rb_io_maybe_wait` functions (#2733, @andrykonchin).
* `StringIO#set_encoding` should coerce the argument to an Encoding (#2954, @eregon).
* Implement changes of Ruby 3.0 to `IO#wait` (#2953, @larskanis).
* Implement `rb_io_descriptor()` (@eregon).

Performance:

* Marking of native structures wrapped in objects is now done on C call exit to reduce memory overhead (@aardvark179).
* Splitting (copying) of call targets has been optimized by implementing `cloneUninitialized()` (@andrykonchin, @eregon).
* `Process.pid` is now cached per process like `$$` (#2882, @horakivo).
* Use the system `libyaml` for `psych` to improve warmup when parsing YAML (#2089, @eregon).
* Fixed repeated deoptimizations for methods building an `Array` which is growing over multiple calls at a given call site (@eregon).

Changes:

* Remove `Truffle::Interop.deproxy` as it is unsafe and not useful (@eregon).
* Removed `Truffle::Interop.unbox_without_conversion` (should not be needed by user code) (@eregon).

# 22.3.0

New features:

* Foreign strings now have all methods of Ruby `String`. They are treated as `#frozen?` UTF-8 Ruby Strings (@eregon).
* Add `Java.add_to_classpath` method to add jar paths at runtime (#2693, @bjfish).
* Add support for Ruby 3.1's Hash shorthand/punning syntax (@nirvdrum).
* Add support for Ruby 3.1's anonymous block forwarding syntax (@nirvdrum).
* Added the following keyword arguments to `Polyglot::InnerContext.new`: `languages, language_options, inherit_all_access, code_sharing` (@eregon).

Bug fixes:

* Fix `StringIO` to set position correctly after reading multi-byte characters (#2207, @aardvark179).
* Update `Process` methods to use `module_function` (@bjfish).
* Fix `File::Stat`'s `#executable?` and `#executable_real?` predicates that unconditionally returned `true` for a superuser (#2690, @andrykonchin).
* The `strip` option `--keep-section=.llvmbc` is not supported on macOS (#2697, @eregon).
* Disallow the marshaling of polyglot exceptions since we can't properly reconstruct them (@nirvdrum).
* Fix `String#split` missing a value in its return array when called with a pattern of `" "` and a _limit_ value > 0 on a string with trailing whitespace where the limit hasn't been met (@nirvdrum).
* Fix `Kernel#sleep` and `Mutex#sleep` for durations smaller than 1 millisecond (#2716, @eregon).
* Fix `IO#{wait,wait_readable,wait_writable}` with a timeout > INT_MAX seconds (@eregon).
* Use the compatible encoding for `String#{sub,gsub,index,rindex}` (#2749, @eregon).
* Fix `Warning#warn` called with category specified is no longer throwing exception (#20446, @horakivo).

Compatibility:

* Fix `Array#fill` to raise `TypeError` instead of `ArgumentError` when the length argument is not numeric (#2652, @andrykonchin).
* Warn when a global variable is not initialized (#2595, @andrykonchin).
* Fix escaping of `/` by `Regexp#source` (#2569, @andrykonchin).
* Range literals of integers are now created at parse time like in CRuby (#2622, @aardvark179).
* Fix `IO.pipe` - allow overriding `IO.new` that is used to create new pipes (#2692, @andykonchin).
* Fix exception message when there are missing or extra keyword arguments - it contains all the missing/extra keywords now (#1522, @andrykonchin).
* Always terminate native strings with enough `\0` bytes (#2704, @eregon).
* Support `#dup` and `#clone` on foreign strings (@eregon).
* Fix `Regexp.new` to coerce non-String arguments (#2705, @andrykonchin).
* Fix `Kernel#sprintf` formatting for `%c` when used non-ASCII encoding (#2369, @andrykonchin).
* Fix `Kernel#sprintf` argument casting for `%c` (@andrykonchin).
* Implement the `rb_enc_strlen` function for use by native extensions (@nirvdrum).
* Match tag values used by `rb_protect` and `rb_jump_tag` for the `tk` gem (#2556, @aardvark179).
* Implement `rb_eval_cmd_kw` to support the `tk` gem (#2556, @aardvark179).
* Fix `rb_class2name` to call `inspect` on anonymous classes like in CRuby (#2701, @aardvark179).
* Implement `rb_ivar_foreach` to iterate over instance and class variables like in CRuby (#2701, @aardvark179).
* Fix the absolute path of the main script after chdir (#2709, @eregon).
* Fix exception for `Fiddle::Handle.new` with a missing library (#2714, @eregon).
* Fix arguments implicit type conversion for `BasicObject#instance_eval`, `Module#class_eval`, `Module#module_eval`, `Module#define_method` (@andrykonchin).
* Raise `ArgumentError` unconditionally when `Proc.new` is called without a block argument (@andrykonchin).
* Fix `UnboundMethod#hash` to not depend on a module it was retrieved from (#2728, @andrykonchin).

Performance:

* Replace a call of `-"string"` with frozen string literal at parse time (@andrykonchin).
* Report polymorphism inside `Hash#[]` to recover performance (@aardvark179).
* Improved interpreter performance by optimizing for better host inlining (@eregon).
* Use `poll` instead of `select` for simple IO waiting to reduce overheads (#1584, @aardvark179).

Changes:

* No more conversion between Java Strings and Ruby Strings at the interop boundary (@eregon).
* Removed `Truffle::Interop.{import_without_conversion,export_without_conversion}` (use `Polyglot.{import,export}` instead).
* Removed `Truffle::Interop.members_without_conversion` (use `Truffle::Interop.members` instead).
* Refactored internals of `rb_sprintf` to simplify handling of `VALUE`s in common cases (@aardvark179).
* Refactored sharing of array objects between threads using new `SharedArrayStorage` (@aardvark179).

Security:

* The native access permission is now properly checked before any native pointer (e.g. `Truffle::FFI::Pointer`) is created (@eregon).

# 22.2.0

New features:

* Add support for `darwin-aarch64` (macOS M1) (#2181, @lewurm, @chrisseaton, @eregon).
* Add support for OpenSSL 3.0.0 by updating the openssl gem (@aardvark179, @eregon).

Bug fixes:

* Fix `rb_id2name` to ensure the native string will have the same lifetime as the id (#2630, @aardvark179).
* Fix `MatchData#[]` exception when passing a length argument larger than the number of match values (#2636, @nirvdrum).
* Fix `MatchData#[]` exception when supplying a large negative index along with a length argument (@nirvdrum).
* Fix capacity computation for huge `Hash` (#2635, @eregon).
* Fix aliased methods to return the correct owner when method is from a superclass (@bjfish).
* Fix `String#[Regexp, Integer]` when the capture group exists but is not matched (@eregon).
* Fix `File.open` mode string parsing when binary option is the third character (@bjfish).
* Fix `rb_scan_args_kw` macro to avoid shadowing variables (#2649, @aardvark179).
* Fix `String#unpack("Z")` to not advance after the null byte, like CRuby (#2659, @aardvark179).
* Fix `Float#round` to avoid losing precision during the rounding process (@aardvark179).
* Fix `String#insert` to not call a subclassed string method (@bjfish).
* Fix `rb_obj_call_init` to pass any block argument to the `initialize` method (#2675, @aardvark179).
* Fix issue with feature loading not detecting a previously loaded feature (#2677, @bjfish).
* Fix `/#{...}/o` to evaluate only once per context when splitting happens (@eregon).
* Fix `Kernel#sprintf` formatting of floats to be like CRuby (@aardvark179).
* Fix `Process.egid=` to accept `String`s (#2615, @ngtban).
* Fix optional assignment to only evaluate index arguments once (#2658, @aardvark179).

Compatibility:

* Updated to Ruby 3.0.3. The 3 CVEs did not affect TruffleRuby, this is to bring the stdlib and gem updates (@eregon).
* Fix `Marshal.dump` to raise an error when an object has singleton methods (@bjfish).
* `Exception#full_message` now defaults the order to `:top` like CRuby 3+ (@eregon).
* Fix `Process.wait2` to return `nil` when the `WNOHANG` flag is given and the child process is still running (@bjfish).
* Disable most `nokogiri` C extension patches when system libraries are not being used (#2693, @aardvark179).
* Implement `rb_gc_mark_maybe` and `rb_global_variable` to ensure `VALUE` stay live in C extensions (@aardvark179).
* Implement `rb_imemo_tmpbuf` allocation for `ripper` (@aardvark179).
* Implement `inherit` argument for `Module#class_variables` (#2653, @bjfish).
* Fix `Float#/` when dividing by `Rational` (@bjfish).
* `Process.euid=` should accept String (#2615, @ngtban).
* Fix `instance_variable_get` and `instance_variable_set` for immutable objects (@bjfish).
* `Thread#raise(exc, message)` now calls `exc.exception` in the target thread like CRuby (@eregon).
* Define `Process::{CLOCK_BOOTTIME,CLOCK_BOOTTIME_ALARM,CLOCK_REALTIME_ALARM}` (#1480, @eregon).
* Improve support of `:chomp` keyword argument in `IO` and `StringIO` methods (#2650, @andrykonchin). 
* Implement specializations for immutable ruby objects for ObjectSpace methods (@bjfish).
* Use `$PAGER` for `--help` and `--help*`, similar to CRuby (#2542, @Strech).
* Ensure all headers are warnings-free (#2662, @eregon).
* All `IO` instances should have `T_FILE` as their `rb_type()`, not only `File` instances (#2662, @eregon).
* Make `rb_fd_select` retry on `EINTR` (#1584, @aardvark179).

Performance:

* Reimplement `Float#to_s` for better performance (#1584, @aardvark179).
* Improve reference processing by making C object free functions and other finalizers more lightweight (@aardvark179).
* Improve performance of `RSTRING_PTR` for interned strings (@aardvark179).
* Cache constant argument formats used with `rb_scan_args_kw` (@aardvark179).

Changes:

* `-Werror=implicit-function-declaration` is now used for compiling C extensions to fail more clearly and earlier if a function is missing, like CRuby 3.2 (#2618, @eregon).
* Disable thread pool for Fibers as it causes correctness issues (#2551, @eregon).

# 22.1.0

New features:

* Foreign exceptions are now fully integrated and have most methods of `Exception` (@eregon).
* Foreign exceptions can now be rescued with `rescue Polyglot::ForeignException` or `rescue foreign_meta_object` (#2544, @eregon).

Bug fixes:

* Guard against unterminated ranges in file matching patterns (#2556, @aardvark179).
* Fixed `rb_proc_new` to return a proc that will pass all required arguments to C (#2556, @aardvark179).
* Fixed `String#split` to return empty array when splitting all whitespace on whitespace (#2565, @bjfish).
* Raise `RangeError` for `Time.at(bignum)` (#2580, @eregon).
* Fix `Integer#{<<,>>}` with RHS bignum and long (@eregon).
* Fix a resource leak from allocators defined in C extensions (@aardvark179).
* `SIGINT`/`Interrupt`/`Ctrl+C` now shows the backtrace and exits as signaled, like CRuby (@eregon).
* Update patch feature finding to prefer the longest matching load path (#2605, @bjfish).
* Fix `Hash#{to_s,inspect}` for keys whose `#inspect` return a frozen String (#2613, @eregon).
* Fix `Array#pack` with `x*` to not output null characters (#2614, @bjfish).
* Fix `Random#rand` not returning random floats when given float ranges (#2612, @bjfish).
* Fix `Array#sample` for `[]` when called without `n` and a `Random` is given (#2612, @bjfish).
* Fix `Module#const_get` to raise a `NameError` when nested modules do not exist (#2610, @bjfish).
* Ensure native `VALUE`s returned from C are unwrapped before the objects can be collected (@aardvark179).
* Fix `Enumerator::Lazy#with_index` to start with new index for multiple enumerations (@bjfish).
* Fix `rb_id2name` to ensure the native string will have the same lifetime as the id (#2630, @aardvark179).
* Fix `Integer#fdiv` and `Rational#to_f` for large `Integer` values (#2631, @bjfish).
* Remove the `RB_NEWOBJ/NEWOBJ` and `OBJSETUP` macros since we cannot support them in TruffleRuby and native extensions may use `#ifdef` to detect features (#2869, @nirvdrum).
* Fix memory leak in `--native` mode for native extension handles and native pointers (@eregon).

Compatibility:

* Implement full Ruby 3 keyword arguments semantics (#2453, @eregon, @chrisseaton).
* Implement `ruby_native_thread_p` for compatibility (#2556, @aardvark179).
* Add `rb_argv0` for the `tk` gem (#2556, @aardvark179).
* Implement more correct conversion of array elements by `Array#pack`(#2503, #2504, @aardvark179).
* Implement `Pathname#{empty?, glob}` (#2559, @bjfish).
* Fixed `Rational('')` to raise error like MRI (#2566, @aardvark179).
* Freeze instances of `Range` but not subclasses, like CRuby (#2570, @MattAlp).
* When writing to STDOUT redirected to a closed pipe, no broken pipe error message will be shown now (#2532, @gogainda).
* Use `#to_a` for converting `list` in `rescue *list` (#2572, @eregon).
* Implement 'rb_str_buf_append' (@bjfish).
* Add patch for `digest` so that TruffleRuby implementation is not overridden (@bjfish).
* Handle encoding conversion errors when reading directory entries (@aardvark179).
* Follow symlinks when processing `*/` directory glob patterns (#2589, @aardvark179).
* Set `@gem_prelude_index` variable on the default load paths (#2586 , @bjfish).
* Do not call `IO#flush` dynamically from `IO#close` (#2594, @gogainda).
* Implement `rb_str_new_static` for C extensions that use it (@aardvark179).
* Rewrote `ArrayEachIteratorNode` and re-introduced `each` specs for MRI parity when mutating arrays whilst iterating, rather than crashing (#2587, @MattAlp).
* Update `String#rindex` to only accept `Regexp` or objects convertable to `String` as the first parameter (#2608, @bjfish).
* Update `String#<<` to require one argument (#2609, @bjfish).
* Update `String#split` to raise `TypeError` when false is given (#2606, @bjfish).
* Update `String#lstrip!` to remove leading null characters (#2607, @bjfish).
* Update `File.utime` to return the number of file names in the arguments (#2616, @bjfish).
* Update `Dir.foreach` to accept an `encoding` parameter (#2627, @bjfish).
* Update `IO.readlines` to ignore negative limit parameters (#2625 , @bjfish).
* Update `Math.sqrt` to raise a `Math::DomainError` for negative numbers (#2621, @bjfish).
* Update `Enumerable#inject` to raise an `ArgumentError` if no block or symbol are given (#2626, @bjfish).

Performance:

* Increase dispatch limit for string library to handle mutable, immutable and non-strings (@aardvark179).
* Switch to `Arrays.mismatch()` in string comparison for better performance (@aardvark179).
* Removed extra array allocations for method calls in the interpreter to improve warmup performance (@aardvark179).
* Optimize `Dir[]` by sorting entries as they are found and grouping syscalls (#2092, @aardvark179).
* Reduce memory footprint by tracking `VALUE`s created during C extension init separately (@aardvark179).
* Rewrote `ArrayEachIteratorNode` to optimize performance for a constant-sized array and reduce specializations to 1 general case (#2587, @MattAlp).
* Reduce conversion of `VALUE`s to native handle during common operations in C extensions (@aardvark179).
* Improved performance of regex boolean matches (e.g., `Regexp#match?`) by avoiding match data allocation in TRegex (#2588, @nirvdrum).
* Remove overhead when getting using `RDATA_PTR` (@aardvark179).
* Additional copy operations have been reduced when performing IO (#2536, @aardvark179).

Changes:

* Foreign exceptions are no longer translated to `RuntimeError` but instead remain as foreign exceptions, see the [documentation](doc/user/polyglot.md) for how to rescue them (@eregon).

# 22.0.0

New features:

* Updated to Ruby 3.0.2 (#2453, @eregon).

Bug fixes:

* Fix `File.utime` to use nanoseconds (#2448, @bjfish).
* Capture the intercepted feature path during patching to reuse during patch require (#2441, @bjfish).
* Update `Module#constants` to filter invalid constant identifiers (#2452, @bjfish).
* Fixed `-0.0 <=> 0.0` and `-0.0 <=> 0` to return `0` like on CRuby (#1391, @eregon).
* Fixed `Range#step` to return correct class with begin-less range (@ccocchi, #2516).
* Fixed exception creation when an `Errno` is sub-classed (@bjfish, #2521).
* Fixed `String#[]=` to use the negotiated encoding (@bjfish, #2545).

Compatibility:

* Implement `rb_sprintf` in our format compiler to provide consistent formatting across C standard libraries (@eregon).
* Update `defined?` to return frozen strings (#2450, @bjfish).
* Use compensated summation for `{Array,Enumerable}#sum` when floating point values are included (@eregon).
* `Module#attr_*` methods now return an array of method names (#2498, @gogainda).
* Fixed `Socket#(local|remote)_address` to retrieve family and type from the file descriptor (#2444, @larskanis).
* Add `Thread.ignore_deadlock` accessor (#2453, @bjfish).
* Allow `Hash#transform_keys` to take a hash argument (@ccocchi, #2464).
* Add `Enumerable#grep{_v}` optimization for `Regexp` (#2453, @bjfish).
* Update `IO#write` to accept multiple arguments (#2501, @bjfish).
* Do not warn when uninitialized instance variable is accessed (#2502, @andrykonchin).
* Remove `TRUE`, `FALSE`, and `NIL` constants like CRuby 3.0 (#2505, @andrykonchin).
* `Symbol#to_proc` now returns a lambda like in Ruby 3 (#2508, @andrykonchin).
* `Kernel#lambda` now warns if called without a literal block (#2500, @andrykonchin).
* Implement Hash#except (#2463, @wildmaples).
* Remove special `$SAFE` global and related C API methods (#2453, @bjfish).
* Assigning to a numbered parameter raises `SyntaxError` (#2506, @andrykonchin).
* Implement `--backtrace-limit` option (#2453, @bjfish).
* Update `String` methods to return `String` instances when called on a subclass (#2453, @bjfish).
* Update `String#encode` to support the `:fallback` option (#1391, @aardvark179).
* `Module#alias_method` now returns the defined alias as a symbol(#2499, @gogainda).
* Implement `Symbol#name` (#2453, @bjfish).
* Update `Module#{public, protected, private, public_class_method, private_class_method}` and top-level `private` and `public` methods to accept single array argument with a list of method names (#2453, @bjfish).
* Constants deprecated by `Module#deprecate_constant` only warn if `Warning[:deprecated]` is `true` (@eregon).
* All Array methods now return Array instances and not subclasses (#2510, @Strech).
* Integer#zero? overrides Numeric#zero? for optimization (#2453, @bjfish).
* Default `Kernel#eval` source file and line to `(eval):1` like CRuby 3 (#2453, @aardvark179).
* Add `GC.auto_compact` accessors for compatibility (#2453, @bjfish).
* Update accessing a class variable from the top-level scope to be a `RuntimeError` (#2453, @bjfish).
* Update interpolated strings to not be frozen (#2453, @bjfish).
* Add `WERRORFLAG` to `RbConfig` (#2519, @bjfish).
* Update `MatchData` methods to return `String` instances when called on a subclass (#2453, @bjfish).
* Implement `Proc#{==,eql?}` (#2453, @bjfish).
* Implement all `StringScanner` methods (#2520, @eregon).
* Handle `Kernel#clone(freeze: true)` (#2512, @andrykonchin).
* Relax `Fiber#transfer` limitations (#2453, @bjfish).
* Implement `Fiber#blocking?` like CRuby 3 (#2453, @aardvark179).
* Sort by default for `Dir.{glob,[]}` and add `sort:` keyword argument (#2523, @Strech).
* Implement `rb_str_locktmp` and `rb_str_unlocktmp` (#2524, @bjfish).
* Update `Kernel#instance_variables` to return insertion order (@bjfish).
* Fixed `rb_path2class()` to not error for a module (#2511, @eregon).
* Update `Kernel#print` to print `$_` when no arguments are given (#2531, @bjfish).
* Add category kwarg to Kernel.warn and Warning.warn (#2533, @Strech).
* Implement `GC.{measure_total_time, total_time}` and update `GC.stat` to update provided hash (#2535, @bjfish).
* Implement `Array#slice` with `ArithmeticSequence` (#2526, @ccocchi).
* Update `Hash#each` to consistently yield a 2-element array (#2453, @bjfish).
* Remove `Hash#{__store__, index}` methods for compatibility (#2546, @bjfish).
* Implement more correct conversion of array elements by `Array#pack` (#2503, #2504, @aardvark179).
* Update `String#split` to raise a `RangeError` when `limit` is larger than `int` (@bjfish).

Performance:

* Regexp objects are now interned in a similar way to symbols (@aardvark179).
* Improve performance of regexps using POSIX bracket expressions (e.g., `[[:lower:]]`) matching against ASCII-only strings (#2447, @nirvdrum).
* `String#sub`, `sub!`, `gsub`, and `gsub!` have been refactored for better performance (@aardvark179).
* Don't allocate a `MatchData` object when `Regexp#match?` or `String#match?` is used (#2509, @nirvdrum).
* Add `ENV.except` (#2507, @Strech).
* Fully inline the `Integer#+` and `Integer#-` logic for interpreter speed (#2518, @smarr).
* Remove unnecessary work in negotiating the encoding to use in a Regexp match (#2522, @nirvdrum).
* Add new fast paths for encoding negotiation between strings with different encodings, but which match common default cases (#2522, @nirvdrum).
* Reduce footprint by removing unnecessary nodes for accessing the `FrameOnStackMarker` (#2530, @smarr).

Changes:

* TruffleRuby now requires Java 11+ and no longer supports Java 8 (@eregon).

# 21.3.0

New features:

* [TRegex](https://github.com/oracle/graal/tree/master/regex) is now used by default, which provides large speedups for matching regular expressions.
* Add `Polyglot.languages` to expose the list of available languages.
* Add `Polyglot::InnerContext` to eval code in any available language in an inner isolated context (#2169).
* Foreign objects now have a dynamically-generated class based on their interop traits like `ForeignArray` and are better integrated with Ruby objects (#2149).
* Foreign arrays now have all methods of Ruby `Enumerable` and many methods of `Array` (#2149).
* Foreign hashes now have all methods of Ruby `Enumerable` and many methods of `Hash` (#2149).
* Foreign iterables (`InteropLibrary#hasIterator`) now have all methods of Ruby `Enumerable` (#2149).
* Foreign objects now implement `#instance_variables` (readable non-invocable members) and `#methods` (invocable members + Ruby methods).

Bug fixes:

* Fix `Marshal.load` of multiple `Symbols` with an explicit encoding (#1624).
* Fix `rb_str_modify_expand` to preserve existing bytes (#2392).
* Fix `String#scrub` when replacement is frozen (#2398, @LillianZ).
* Fix `Dir.mkdir` error handling for `Pathname` paths (#2397).
* `BasicSocket#*_nonblock(exception: false)` now only return `:wait_readable/:wait_writable` for `EAGAIN`/`EWOULDBLOCK` like MRI (#2400).
* Fix issue with `strspn` used in the `date` C extension compiled as a macro on older glibc and then missing the `__strspn_c1` symbol on newer glibc (#2406).
* Fix constant lookup when loading the same file multiple times (#2408).
* Fix handling of `break`, `next` and `redo` in `define_method(name, &block)` methods (#2418).
* Fix handling of incompatible types in `Float#<=>` (#2432, @chrisseaton).
* Fix issue with escaping curly braces for `Dir.glob` (#2425).
* Fix `base64` decoding issue with missing output (#2435).
* Fix `StringIO#ungetbyte` to treat a byte as a byte, not a code point (#2436). 
* Fix `defined?(yield)` when used inside a block (#2446).
* Fix a couple issues related to native memory allocation and release.

Compatibility:

* Implement `Process::Status.wait` (#2378).
* Update `rb_str_modify` and `rb_str_modify_expand` to raise a `FrozenError` when given a frozen string (#2392).
* Implement `rb_fiber_*` functions (#2402).
* Implement `rb_str_vcatf`.
* Add support for tracing allocations from C functions (#2403, @chrisseaton).
* Implement `rb_str_catf`.
* Search the executable in the passed env `PATH` for subprocesses (#2419).
* Accept a string as the pattern argument to `StringScanner#scan` and `StringScanner#check` (#2423).

Performance:

* Moved most of `MonitorMixin` to primitives to deal with interrupts more efficiently (#2375).
* Improved the performance of `rb_enc_from_index` by adding cached lookups (#2379, @nirvdrum).
* Improved the performance of many `MatchData` operations (#2384, @nirvdrum).
* Significantly improved performance of TRegex calls by allowing Truffle splitting (#2389, @nirvdrum).
* Improved `String#gsub` performance by adding a fast path for the `string_byte_index` primitive (#2380, @nirvdrum).
* Improved `String#index` performance by adding a fast path for the `string_character_index` primitive (#2383, @LillianZ).
* Optimized conversion of strings to integers if the string contained a numeric value (#2401, @nirvdrum).
* Use Truffle's `ContextThreadLocal` to speedup access to thread-local data.
* Provide a new fast path for `rb_backref*` and `rb_lastline*`functions from C extensions.

Changes:

* `foreign_object.class` on foreign objects is no longer special and uses `Kernel#class` (it used to return the `java.lang.Class` object for a Java type or `getMetaObject()`, but that is too incompatible with Ruby code).
* `Java.import name` imports a Java class in the enclosing module instead of always as a top-level constant.
* `foreign_object.keys` no longer returns members, use `foreign_object.instance_variables` or `foreign_object.methods` instead.
* `foreign_object.respond_to?(:class)` is now always true (before it was only for Java classes), since the method is always defined.

Security:

* Updated to Ruby 2.7.4 to fix CVE-2021-31810, CVE-2021-32066 and CVE-2021-31799.

# 21.2.0

New features:

* New `TruffleRuby::ConcurrentMap` data structure for use in [`concurrent-ruby`](https://github.com/ruby-concurrency/concurrent-ruby) (#2339, @wildmaples).

Bug fixes:

* Fix of different values of self in different scopes.
* `Truffle::POSIX.select` was being redefined repeatedly (#2332).
* Fix the `--backtraces-raise` and `--backtraces-rescue` options in JVM mode (#2335).
* Fix `File.{atime, mtime, ctime}` to include nanoseconds (#2337).
* Fix `Array#[a, b] = "frozen string literal".freeze` (#2355).
* `rb_funcall()` now releases the C-extension lock (similar to MRI).

Compatibility:

* Updated to Ruby 2.7.3. The `resolv` stdlib was not updated (`resolv` in 2.7.3 has [bugs](https://bugs.ruby-lang.org/issues/17748)).
* Make interpolated strings frozen for compatibility with Ruby 2.7 (#2304, @kirs).
* `require 'socket'` now also requires `'io/wait'` like CRuby (#2326).
* Support precision when formatting strings (#2281, @kirs).
* Make rpartition compatible with Ruby 2.7 (#2320, @gogainda).
* Include the type name in exception messages from `rb_check_type` (#2307).
* Fix `Hash#rehash` to remove duplicate keys after modifications (#2266, @MattAlp).
* Only fail `rb_check_type` for typed data, not wrapped untyped structs (#2331).
* Decide the visibility in `Module#define_method` based on `self` and the default definee (#2334).
* Configure `mandir` value in `RbConfig::CONFIG` and `RbConfig::MAKEFILE_CONFIG` (#2315).
* TruffleRuby now supports the Truffle polyglot Hash interop API.
* Implement `Fiber#raise` (#2338).
* Update `File.basename` to return new `String` instances (#2343).
* Allow `Fiber#raise` after `Fiber#transfer` like Ruby 3.0 (#2342).
* Fix `ObjectSpace._id2ref` for Symbols and frozen String literals (#2358).
* Implemented `Enumerator::Lazy#filter_map` (#2356).
* Fix LLVM toolchain issue on macOS 11.3 (#2352, [oracle/graal#3383](https://github.com/oracle/graal/issues/3383)).
* Implement `IO#set_encoding_by_bom` (#2372, pawandubey).
* Implemented `Enumerator::Lazy#with_index` (#2356).
* Implement `rb_backref_set`.
* Fix `Float#<=>` when comparing `Infinity` to other `#infinite?` values.
* Implement `date` library as a C extension to improve compatibility (#2344).

Performance:

* Make `#dig` iterative to make it faster and compile better for calls with 3+ arguments (#2301, @chrisseaton, @jantnovi).
* Make `Struct#dig` faster in interpreter by avoiding exceptions (#2306, @kirs).
* Reduce the number of AST nodes created for methods and blocks (#2261).
* Fiber-local variables are much faster now by using less synchronization.
* Improved the performance of the exceptional case of `String#chr` (#2318, @chrisseaton).
* Improved the performance of `IO#read_nonblock` when no data is available to be read.
* `TruffleSafepoint` is now used instead of custom logic, which no longer invalidates JITed code for guest safepoints (e.g., `Thread#{backtrace,raise,kill}`, `ObjectSpace`, etc).
* Significantly improved performance of `Time#strftime` for common formats (#2361, @wildmaples, @chrisseaton).
* Faster solution for lazy integer length (#2365, @lemire, @chrisseaton).
* Speedup `rb_funcallv*()` by directly unwrapping the C arguments array instead of going through a Ruby `Array` (#2089).
* Improved the performance of several `Truffle::RegexOperations` methods (#2374, @wildmapes, @nirvdrum).

Changes:

* `rb_iterate()` (deprecated since 1.9) no longer magically passes the block to `rb_funcall()`, use `rb_block_call()` instead.

Security:

* Updated to Ruby 2.7.3 to fix CVE-2021-28965 and CVE-2021-28966.

# 21.1.0

New features:

* Access to local variables of the interactive Binding via language bindings is now supported: `context.getBindings("ruby").putMember("my_var", 42);` (#2030).
* `VALUE`s in C extensions now expose the Ruby object when viewed in the debugger, as long as they have not been converted to native values.
* Signal handlers can now be run without triggering multi-threading.
* Fibers no longer trigger Truffle multi-threading.

Bug fixes:

* `Range#to_a` wasn't working for `long` ranges (#2198, @tomstuart and @LillianZ).
* Show the interleaved host and guest stacktrace for host exceptions (#2226).
* Fix the label of the first location reported by `Thread#backtrace_locations` (#2229).
* Fix `Thread.handle_interrupt` to defer non-pure interrupts until the end of the `handle_interrupt` block (#2219).
* Clear and restore errinfo on entry and normal return from methods in C extensions (#2227).
* Fix extra whitespace in squiggly heredoc with escaped newline (#2238, @wildmaples and @norswap).
* Fix handling of signals with `--single-threaded` (#2265).
* Fix `Enumerator::Lazy#{chunk_while, slice_before, slice_after, slice_when}` to return instances of `Enumerator::Lazy` (#2273).
* Fix `Truffle::Interop.source_location` to return unavailable source sections for modules instead of null (#2257).
* Fix usage of `Thread.handle_interrupt` in `MonitorMixin#mon_synchronize`.
* Fixed `TruffleRuby.synchronized` to handle guest safepoints (#2277).
* Fix control flow bug when assigning constants using ||= (#1489).
* Fix `Kernel#raise` argument handling for hashes (#2298).
* Set errinfo when `rb_protect` captures a Ruby exception (#2245).
* Fixed handling of multiple optional arguments and keywords when passed a positional `Hash` (#2302).

Compatibility:

* Prepend the GraalVM LLVM Toolchain to `PATH` when installing gems (#1974, #1088, #1343, #1400, #1947, #1931, #1588).
* Installing the `nokogiri` gem now defaults to use the vendored `libxml2` and `libxslt`, similar to CRuby, which means the corresponding system packages are no longer needed (#62).
* Implemented `$LOAD_PATH.resolve_feature_path`.
* Add `Pathname#/` alias to `Pathname#+` (#2178).
* Fixed issue with large `Integer`s in `Math.log` (#2184).
* Updated `Regexp.last_match` to support `Symbol` and `String` parameter (#2179).
* Added support for numbered block parameters (`_1` etc).
* Fixed `String#upto` issue with non-ascii strings (#2183).
* Implemented partial support for pattern matching (#2186).
* Make `File.extname` return `'.'` if the path ends with one (#2192, @tomstuart).
* Include fractional seconds in `Time#inspect` output (#2194, @tomstuart).
* Add support for `Integer#[Range]` and `Integer#[start, length]` (#2182, @gogainda).
* Allow private calls with `self` as an explicit receiver (#2196, @wildmaples).
* Fixed `:perm` parameter for `File.write`.
* Implemented `Time#floor` and `#ceil` (#2201, @wildmaples).
* Allow `Range#include?` and `#member?` with `Time` (#2202, @wildmaples).
* Implemented `Comparable#clamp(Range)` (#2200, @wildmaples).
* Added a `Array#minmax` to override `Enumerable#minmax` (#2199, @wildmaples).
* Implemented `chomp` parameter for `IO.{readlines, foreach}` (#2205).
* Implemented the Debug Inspector C API.
* Added beginless range support for `Range#{new, bsearch, count, each, equal_value, first, inspect, max, min, size, cover?, include?, ===}`.
* Added beginless range support for `Array#{[], []=, slice, slice!, to_a, fill, values_at}` (#2155, @LillianZ).
* Added beginless range support for `String#{byteslice, slice, slice!}` and `Symbol#slice` (#2211, @LillianZ).
* Added beginless range support for `Kernel#{caller, caller_locations}` and `Thread#backtrace_locations` (#2211, @LillianZ).
* Make rand work with exclusive range with Float (#1506, @gogainda).
* Fixed `String#dump`'s formatting of escaped unicode characters (#2217, @meganniu).
* Switched to the io-console C extension from C ruby for better performance and compatibility in `irb`.
* Coerce the message to a `String` for `BasicSocket#send` (#2209, @HoneyryderChuck).
* Support buffer argument for `UDPSocket#recvfrom_nonblock` (#2209, @HoneyryderChuck).
* Fixed `Integer#digits` implementation to handle more bases (#2224, #2225).
* Support the `inherit` parameter for `Module#{private, protected, public}_method_defined?`.
* Implement `Thread.pending_interrupt?` and `Thread#pending_interrupt?` (#2219).
* Implement `rb_lastline_set` (#2170).
* Implemented `Module#const_source_location` (#2212, @tomstuart and @wildmaples).
* Do not call `File.exist?` in `Dir.glob` as `File.exist?` is often mocked (#2236, @gogainda).
* Coerce the inherit argument to a boolean in `Module#const_defined?` and `Module#const_get` (#2240).
* Refinements take place at `Object#method` and `Module#instance_method` (#2004, @ssnickolay).
* Add support for `rb_scan_args_kw` in C API (#2244, @LillianZ).
* Update random implementation layout to be more compatible (#2234).
* Set `RbConfig::CONFIG['LIBPATHFLAG'/'RPATHFLAG']` like MRI to let `$LIBPATH` changes in `extconf.rb` work.
* Access to path and mode via `rb_io_t` from C has been changed to improve compatibility for io-console.
* Implemented the `Time.at` `in:` parameter.
* Implemented `Kernel#raise` `cause` parameter.
* Improved compatibility of `Signal.trap` and `Kernel#trap` (#2287, @chrisseaton).
* Implemented `GC.stat(:total_allocated_objects)` as `0` (#2292, @chrisseaton).
* `ObjectSpace::WeakMap` now supports immediate and frozen values as both keys and values (#2267).
* Call `divmod` when coercion to `Float` fails for `#sleep` (#2289, @LillianZ).

Performance:

* Multi-Tier compilation is now enabled by default, which improves warmup significantly.
* Improve the performance of checks for recursion (#2189, @LillianZ).
* Improve random number generation performance by avoiding synchronization (#2190, @ivoanjo).
* We now create a single call target per block by default instead of two.
* Some uses of class variables are now much better optimized (#2259, @chrisseaton).
* Several methods that need the caller frame are now always inlined in their caller, which speeds up the interpreter and reduces footprint.
* Pasting code in IRB should be reasonably fast, by updating to `irb` 1.3.3 and `reline` 0.2.3 (#2233).

Changes:

* Standalone builds of TruffleRuby are now based on JDK11 (they used JDK8 previously). There should be no user-visible changes. Similarly, JDK11 is now used by default in development instead of JDK8.
* The deprecated `Truffle::System.synchronized` has been removed.
* `Java.synchronized` has been removed, it did not work on host objects.

# 21.0.0

Release notes:

* The new IRB is quite slow when copy/pasting code into it. This is due to an inefficient `io/console` implementation which will be addressed in the next release. A workaround is to use `irb --readline`, which disables some IRB features but is much faster for copy/pasting code.

New features:

* Updated to Ruby 2.7.2 (#2004).

Bug fixes:

* Fix error message when the method name is not a Symbol or String for `Kernel#respond_to?` (#2132, @ssnickolay).
* Fixed setting of special variables in enumerators and enumerables (#1484).
* Fixed return value of `Enumerable#count` and `Enumerable#uniq` with multiple yielded arguments (#2145, @LillianZ).
* Fixed `String#unpack` for `w*` format (#2143).
* Fixed issue with ``Kernel#` `` when invalid UTF-8 given (#2118).
* Fixed issue with `Method#to_proc` and special variable storage (#2156).
* Add missing `offset` parameter for `FFI::Pointer#put_array_of_*` (#1525).
* Fixed issue with different `Struct`s having the same hash values (#2214).

Compatibility:

* Implement `String#undump` (#2131, @kustosz).
* `Errno` constants with the same `errno` number are now the same class.
* Implement `Enumerable#tally` and `Enumerable#filter_map` (#2144 and #2152, @LillianZ).
* Implement `Range#minmax`.
* Pass more `Enumerator::Lazy#uniq` and `Enumerator::Lazy#chunk` specs (#2146, @LillianZ).
* Implement `Enumerator#produce` (#2160, @zverok).
* Implement `Complex#<=>` (#2004, @ssnickolay).
* Add warning for `proc` without block (#2004, @ssnickolay).
* Implemented `FrozenError#receiver`.
* `Proc#<<` and `Proc#>>` raises TypeError if passed not callable object (#2004, @ssnickolay).
* Support time and date related messages for `Time` (#2166).
* Updated `Dir.{glob,[]}` to raise `ArgumentError` for nul-separated strings.
* `Kernel#lambda` with no block in a method called with a block raises an exception (#2004, @ssnickolay).
* Implemented `BigDecimal` as C extension to improve compatibility.
* Comment lines can be placed between fluent dot now (#2004, @ssnickolay).
* Implemented `rb_make_exception`.
* `**kwargs` now accept non-Symbol keys like Ruby 2.7.
* Updated the Unicode Emoji version (#2173, @wildmaples).
* Added `Enumerator::Yielder#to_proc`.
* Implemented `Enumerator::Lazy#eager`.
* Updated `Method#inspect` to include paremeter information.
* Update `Module#name` to return the same frozen string.
* Implemented `inherit` argument for `Module#autoload?`.

Performance:

* Refactor and implement more performant `MatchData#length` (#2147, @LillianZ).
* Refactor and implement more performant `Array#sample` (#2148, @LillianZ).
* `String#inspect` is now more efficient.

Changes:

* All `InteropLibrary` messages are now exposed consistently as methods on `Truffle::Interop` (#2139). Some methods were renamed to match the scheme described in the documentation.

# 20.3.0

Bug fixes:

* Handle foreign null object as falsy value (#1902, @ssnickolay).
* Fixed return value of `Enumerable#first` with multiple yielded arguments (#2056, @LillianZ).
* Improve reliability of the post install hook by disabling RubyGems (#2075).
* Fixed top level exception handler to print exception cause (#2013).
* Fixed issue when extending FFI from File (#2094).
* Fixed issue with `Kernel#freeze` not freezing singleton class (#2093).
* Fixed `String#encode` with options issue (#2091, #2095, @LillianZ).
* Fixed issue with `spawn` when `:close` redirect is used (#2097).
* Fixed `coverage` issue when `*eval` is used (#2078).
* Use expanded load paths for feature matching (#1501).
* Fixed handling of post arguments for `super()` (#2111).
* Fixed `SystemStackError` sometimes replaced by an internal Java `NoClassDefFoundError` on JVM (#1743).
* Fixed constant/identifier detection in lexer for non-ASCII encodings (#2079, #2102, @ivoanjo).
* Fixed parsing of `--jvm` as an application argument (#2108).
* Fix `rb_rescue2` to ignore the end marker `(VALUE)0` (#2127, #2130).
* Fix status and output when SystemExit is subclassed and raised (#2128).
* Fix `String#{chomp, chomp!}` issue with invalid encoded strings (#2133).

Compatibility:

* Run `at_exit` handlers even if parsing the main script fails (#2047).
* Load required libraries (`-r`) before parsing the main script (#2047).
* `String#split` supports block (#2052, @ssnickolay).
* Implemented `String#{grapheme_clusters, each_grapheme_cluster}`.
* Fix the caller location for `#method_added` (#2059).
* Fix issue with `Float#round` when `self` is `-0.0`.
* Fix `String#unpack` issue with `m0` format (#2065).
* Fix issue with `File.absolute_path` returning a path to current directory (#2062).
* Update `Range#cover?` to handle `Range` parameter.
* Fix `String#{casecmp, casecmp?}` parameter conversion.
* Fix `Regexp` issue which raised syntax error instead of `RegexpError` (#2066).
* Handle `Object#autoload` when autoload itself (#1616, @ssnickolay).
* Skip upgraded default gems while loading RubyGems (#2075).
* Verify that gem paths are correct before loading RubyGems (#2075).
* Implement `rb_ivar_count`.
* Implemented `rb_yield_values2`.
* Implemented `Digest::Base#{update, <<}` (#2100).
* Pass the final `super` specs (#2104, @chrisseaton).
* Fix arity for arguments with optional kwargs (#1669, @ssnickolay).
* Fix arity for `Proc` (#2098, @ssnickolay).
* Check bounds for `FFI::Pointer` accesses when the size of the memory behind is known.
* Implement negative line numbers for eval (#1482).
* Support refinements for `#to_s` called by string interpolation (#2110, @ssnickolay).
* Module#using raises error in method scope (#2112, @ssnickolay).
* `File#path` now returns a new mutable String on every call like MRI (#2115).
* Avoid infinite recursion when redefining `Warning#warn` and calling `Kernel#warn` (#2109).
* Convert objects with `#to_path` in `$LOAD_PATH` (#2119).
* Handle the functions being native for `rb_thread_call_without_gvl()` (#2090).
* Support refinements for Kernel#respond_to? (#2120, @ssnickolay).
* JCodings has been updated from 1.0.45 to 1.0.55.
* Joni has been updated from 2.1.30 to 2.1.40.

Performance:

* Calls with a literal block are no longer always split but instead the decision is made by the Truffle splitting heuristic.
* `Symbol#to_proc` is now AST-inlined in order to not rely on splitting and to avoid needing the caller frame to find refinements which apply.
* `Symbol#to_proc` is now globally cached per Symbol and refinements, to avoid creating many redundant `CallTargets`.
* Setting and access to the special variables `$~` and `$_` has been refactored to require less splitting.

Changes:

* Migrated from JLine 2 to JLine 3 for the `readline` standard library.

# 20.2.0

New features:

* Updated to Ruby 2.6.6.
* Use `InteropLibrary#toDisplayString()` to better display objects from other languages.
* Implement writing to the top scope for global variables (#2024).
* `foreign_object.to_s` now uses `InteropLibrary#toDisplayString()` (and still `asString()` if `isString()`).
* `foreign_object.inspect` has been improved to be more useful (include the language and meta object).
* `foreign_object.class` now calls `getMetaObject()` (except for Java classes, same as before).
* Add basic support for Linux AArch64.
* `foreign_object.name = value` will now call `Interoplibrary#writeMember("name", value)` instead of `invokeMember("name=", value)`.
* Always show the Ruby core library files in backtraces (#1414).
* The Java stacktrace is now shown when sending SIGQUIT to the process, also on TruffleRuby Native, see [Debugging](doc/user/debugging.md) for details (#2041).
* Calls to foreign objects with a block argument will now pass the block as the last argument.
* `foreign.name` will now use `invokeMember` if invocable and if not use `readMember`, see `doc/contrib/interop_implicit_api.md` for details.
* `foreign.to_f` and `foreign.to_i` will now attempt to convert to Ruby `Float` and `Integer` (#2038).
* `foreign.equal?(other)` now uses `InteropLibrary#isIdentical(other)` and `foreign.object_id/__id__` now uses `InteropLibrary#identityHashCode()`.

Bug fixes:

* Fix `#class_exec`, `#module_exec`, `#instance_eval`, and `instance_exec` to use activated refinements (#1988, @ssnickolay).
* Fixed missing method error for FFI calls with `blocking: true` when interrupted.
* Use upgraded default gems when installed (#1956).
* Fixed `NameError` when requiring an autoload path that does not define the autoload constant (#1905).
* Thread local IO buffers are now allocated using a stack to ensure safe operating if a signal handler uses one during an IO operation.
* Fixed `TracePoint` thread-safety by storing the state on the Ruby `Thread` (like MRI) instead of inside the `TracePoint` instance.
* Make `require 'rubygems/package'` succeed and define `Gem::Deprecate` correctly (#2014).
* Fix `MBCLEN_CHARFOUND_P` error.
* Fix `rb_enc_str_new` when `NULL` encoding is given with a constant string.
* Fixed `rb_enc_precise_mbclen` to handle more inputs.
* The output for `--engine.TraceCompilation` is now significantly easier to read, by having shorter method names and source names (oracle/graal#2052).
* Fix indentation for squiggly heredoc with single quotes (#1564).
* Only print members which are readable for foreign `#inspect` (#2027).
* Fixed the return value of the first call to `Kernel#srand` in a Thread (#2028).
* Fix missing flushing when printing an exception at top-level with a custom backtrace, which caused no output being shown (#1750, #1895).
* Use the mode of the given `IO` for `IO#reopen(IO)` which is important for the 3 standard IOs (#2034).
* Fix potential deadlock when running finalizers (#2041).
* Let `require 'rubygems/specification'` work before `require 'rubygems'`.

Compatibility:

* Implement `UnboundMethod#bind_call`.
* Implemented `ObjectSpace::WeakMap` (#1385, #1958).
* Implemented `strtod` and `ruby_strtod` (#2007).
* Fix detection of `#find_type` in FFI to ignore `MakeMakefile#find_type` from `mkmf` (#1896, #2010).
* Implemented `rb_uv_to_utf8` (#1998, @skateman).
* Implemented `rb_str_cat_cstr`.
* Implemented `rb_fstring`.
* Support `#refine` for Module (#2021, @ssnickolay).
* Implemented `rb_ident_hash_new`.
* Improved the compatibility of `Symbol.all_symbols` (#2022, @chrisseaton).
* Implemented `rb_enc_str_buf_cat`.
* Implemented `rb_int_positive_pow`.
* Implemented `rb_usascii_str_new_lit`.
* Define `#getch` and `#getpass` on `StringIO` when `io/console` is required.
* Implemented `rb_uv_to_utf8` (#1998).
* Single character IDs now behave more like those in MRI to improve C extension compatibility, so `rb_funcall(a, '+', b)` will now do the same thing as in MRI.
* Removed extra public methods on `String`.
* Implemented `rb_array_sort` and `rb_array_sort_bang`.
* Do not create a finalizers `Thread` if there are other public languages, which is helpful for polyglot cases (#2035).
* Implemented `rb_enc_isalnum` and `rb_enc_isspace`.
* `RUBY_REVISION` is now the full commit hash used to build TruffleRuby, similar to MRI 2.7+.
* Implemented `rb_enc_mbc_to_codepoint`.
* Changed the lookup methods to achieve Refinements specification (#2033, @ssnickolay).
* Implemented `Digest::Instance#new` (#2040).
* Implemented `ONIGENC_MBC_CASE_FOLD`.
* Fixed `Thread#raise` to call the exception class' constructor with no arguments when given no message (#2045).
* Fixed `refine + super` compatibility (#2039, #2048, @ssnickolay).
* Make the top-level exception handler more compatible with MRI (#2047).
* Implemented `rb_enc_codelen`.
* Implemented `Ripper` by using the C extension (#1585).

Changes:

* RubyGems gem commands updated to use the `--no-document` option by default.

Performance:

* Enable lazy translation from the parser AST to the Truffle AST for user code by default. This should improve application startup time (#1992).
* `instance variable ... not initialized` and similar warnings are now optimized to have no peak performance impact if they are not printed (depends on `$VERBOSE`).
* Implement integer modular exponentiation using `BigInteger#mod_pow` (#1999, @skateman).
* Fixed a performance issue when computing many substrings of a given non-leaf `String` with non-US-ASCII characters.
* Speedup native handle to Ruby object lookup for C extensions.

# 20.1.0

New features:

* Nightly builds of TruffleRuby are now available, see the README for details (#1483).
* `||=` will not compile the right-hand-side if it's only executed once, to match the idiomatic lazy-initialisation use-case ([blog post](https://engineering.shopify.com/blogs/engineering/optimizing-ruby-lazy-initialization-in-truffleruby-with-deoptimization), #1887, @kipply).
* Added `--metrics-profile-require` option to profile searching, parsing, translating and loading files.
* Added support for captured variables for the Truffle instruments (e.g. Chrome debugger).

Bug fixes:

* Fixed `Exception#dup` to copy the `Exception#backtrace` string array.
* Fixed `rb_warn` and `rb_warning` when used as statements (#1886, @chrisseaton).
* Fixed `NameError.new` and `NoMethodError.new` `:receiver` argument.
* Correctly handle large numbers of arguments to `rb_funcall` (#1882).
* Added arity check to `Module#{include, prepend}`.
* Fix `OpenSSL::Digest.{digest,hexdigest,base64digest}` to handle `algorithm, data` arguments (#1889, @bdewater).
* Fixed `SystemCallError.new` parameter conversion.
* Fixed `File#{chmod, umask}` argument conversion check.
* Added warning in `Hash.[]` for non-array elements.
* Fixed `File.lchmod` to raise `NotImplementedError` when not available.
* `RSTRING_PTR()` now always returns a native pointer, resolving two bugs `memcpy`ing to (#1822) and from (#1772) Ruby Strings.
* Fixed issue with duping during splat (#1883).
* Fixed `Dir#children` implementation.
* Fixed `SignalException.new` error when bad parameter given.
* Added deprecation warning to `Kernel#=~`.
* Fixed `puts` for a foreign objects, e.g. `puts Polyglot.eval('js', '[]')` (#1881).
* Fixed `Exception#full_message` implementation.
* Updated `Kernel.Complex()` to handle the `exception: false` parameter.
* Fixed `Kernel#dup` to return self for `Complex` and `Rational` objects.
* Updated `Kernel.Float()` to handle the `exception: false` parameter.
* Fixed `String#unpack` `M` format (#1901).
* Fixed error when `SystemCallError` message contained non-ASCII characters.
* Fixed `rb_rescue` to allow null rescue methods (#1909, @kipply).
* Fixed incorrect comparisons between bignums and doubles.
* Prevented some internal uses of `Kernel#caller_locations` to be overridden by user code (#1934).
* Fixed an issue caused by recursing inlining within `Regexp#quote` (#1927).
* Updated `Kernel.Float()` to return given string in error message (#1945).
* Parameters and arity of methods derived from `method_missing` should now match MRI (#1921).
* Fixed compile error in `RB_FLOAT_TYPE_P` macro (#1928).
* Fixed `Symbol#match` to call the block with the `MatchData` (#1933).
* Fixed `Digest::SHA2.hexdigest` error with long messages (#1922).
* Fixed `Date.parse` to dup the coerced string to not modify original (#1946).
* Update `Comparable` error messages for special constant values (#1941).
* Fixed `File.ftype` parameter conversion (#1961).
* Fixed `Digest::Instance#file` to not modify string literals (#1964).
* Make sure that string interpolation returns a `String`, and not a subclass (#1950).
* `alias_method` and `instance_methods` should now work correctly inside a refinement (#1942).
* Fixed `Regexp.union` parameter conversion (#1963).
* `IO#read(n)` no longer buffers more than needed, which could cause hanging if detecting readability via a native call such as `select(2)` (#1951).
* Fixed `Random::DEFAULT.seed` to be different on boot (#1965, @kipply).
* `rb_encoding->name` can now be read even if the `rb_encoding` is stored in native memory.
* Detect and cut off recursion when inspecting a foreign object, substituting an ellipsis instead.
* Fixed feature lookup order to check every `$LOAD_PATH` path entry for `.rb`, then every entry for native extension when `require` is called with no extension.
* Define the `_DARWIN_C_SOURCE` macro in extension makefiles (#1592).
* Change handling of var args in `rb_rescue2` to handle usage in C extensions (#1823).
* Fixed incorrect `Encoding::CompatibilityError` raised for some interpolated Regexps (#1967).
* Actually unset environment variables with a `nil` value for `Process.spawn` instead of setting them to an empty String.
* Core library methods part of the Native Image heap are no longer added in the compilation queue on the first call, but after they reach the thresholds like other methods.
* Fix `RbConfig::CONFIG['LIBRUBY_SO']` file extension.
* Fix `char`, `short`, `unsigned char`, `unsigned int`, and `unsigned short` types in `Fiddle` (#1971).
* Fix `IO#select` to reallocate its buffer if it is interrupted by a signal.
* Fix issue where interpolated string matched `#` within string as being a variable (#1495).
* Fix `File.join` to raise error on strings with null bytes.
* Fix initialization of Ruby Thread for foreign thread created in Java.
* Fix registration of default specs in RubyGems (#1987).

Compatibility:

* The C API type `VALUE` is now defined as `unsigned long` as on MRI. This enables `switch (VALUE)` and other expressions which rely on `VALUE` being an integer type (#1409, #1541, #1675, #1917, #1954).
* Implemented `Float#{floor, ceil}` with `ndigits` argument.
* Implemented `Thread#fetch`.
* Implemented `Float#truncate` with `ndigits` argument.
* Made `String#{byteslice, slice, slice!}` and `Symbol#slice` compatible with endless ranges.
* Implemented "instance variable not initialized" warning.
* Make `Kernel#{caller, caller_locations}` and `Thread#backtrace_locations` compatible with endless ranges.
* Implemented `Dir#each_child`.
* Implemented `Kernel.{chomp, chop}` and `Kernel#{chomp, chop}`.
* Implemented `-p` and `-a`, and `-l` CLI options.
* Convert the argument to `File.realpath` with `#to_path` (#1894).
* `StringIO#binmode` now sets the external encoding to BINARY like MRI (#1898).
* `StringIO#inspect` should not include the contents of the `StringIO` (#1898).
* Implemented `rb_fd_*` functions (#1623).
* Fixed uninitialized variable warnings in core and lib (#1897).
* Make `Thread#backtrace` support omit, length and range arguments.
* Implemented `Range#%`.
* Fixed the type of the `flags` field of `rb_data_type_t` (#1911).
* Implemented `rb_obj_is_proc` (#1908, @kipply, @XrXr).
* Implemented C API macro `RARRAY_ASET()`.
* Implemented `num2short` (#1910, @kipply).
* `RSTRING_END()` now always returns a native pointer.
* Removed `register` specifier for `rb_mem_clear()` (#1924).
* Implemented `Thread::Backtrace::Locations#base_label` (#1920).
* Implemented `rb_mProcess` (#1936).
* Implemented `rb_gc_latest_gc_info` (#1937).
* Implemented `RBASIC_CLASS` (#1935).
* Yield 2 arguments for `Hash#map` if the arity of the block is > 1 (#1944).
* Add all `Errno` constants to match MRI, needed by recent RubyGems.
* Silence `ruby_dep` warnings since that gem is unmaintained.
* Clarify error message for not implemented `Process.daemon` (#1962).
* Allow multiple assignments in conditionals (#1513).
* Update `NoMethodError#message` to match MRI (#1957).
* Make `StringIO` work with `--enable-frozen-string-literal` (#1969).
* Support `NULL` for the status of `rb_protect()`.
* Ensure `BigDecimal#inspect` does not call `BigDecimal#to_s` to avoid behaviour change on `to_s` override (#1960).
* Define all C-API `rb_{c,m,e}*` constants as C global variables (#1541).
* Raise `ArgumentError` for `Socket.unpack_sockaddr_un` if the socket family is incorrect.
* Implemented `RTYPEDDATA_*()` macros and `rb_str_tmp_new()` (#1975).
* Implemented `rb_set_end_proc` (#1959).
* Implemented `rb_to_symbol`.
* Implemented `rb_class_instance_methods`, `rb_class_public_instance_methods`, `rb_class_protected_instance_methods`, and `rb_class_private_instance_methods`.
* Implemented `rb_tracepoint_new`, `rb_tracepoint_disable`, `rb_tracepoint_enable`, and `rb_tracepoint_enabled_p` (#1450).
* Implemented `RbConfig::CONFIG['AR']` and `RbConfig::CONFIG['STRIP']` (#1973).
* Not yet implemented C API functions are now correctly detected as missing via `mkmf`'s `have_func` (#1980).
* Accept `RUBY_INTERNAL_EVENT_{NEWOBJ,FREEOBJ}` events but warn they are not triggered (#1978, #1983).
* `IO.copy_stream(in, STDOUT)` now writes to `STDOUT` without buffering like MRI.
* Implemented `RbConfig['vendordir']`.
* Implemented `Enumerator::ArithmeticSequence`.
* Support `(struct RBasic *)->flags` and `->klass` from `ruby.h` (#1891, #1884, #1978).

Changes:

* `TRUFFLERUBY_RESILIENT_GEM_HOME` has been removed. Unset `GEM_HOME` and `GEM_PATH` instead if you need to.
* The deprecated `Truffle::System.full_memory_barrier`, `Truffle::Primitive.logical_processors`, and `Truffle::AtomicReference` have been removed.
* The implicit interface for allowing Ruby objects to behave as polyglot arrays with `#size`, `#[]` methods has been removed and replaced with an explicit interface where each method starts with `polyglot_*`.
* Hash keys are no longer reported as polyglot members.
* All remaining implicit polyglot behaviour for `#[]` method was replaced with `polyglot_*` methods.
* Rename dynamic API to match InteropLibrary. All the methods keep the name as it is in InteropLibrary with the following changes: use snake_case, add `polyglot_` prefix, drop `get` and `is` prefix, append `?` on all predicates.
* Split `Truffle::Interop.write` into `.write_array_element` and `.write_member` methods.
* Rename `Truffle::Interop.size` to `.array_size`.
* Rename `Truffle::Interop.is_boolean?` to `.boolean?`.
* Split `Truffle::Interop.read` into `.read_member` and `.read_array_element`.
* Drop `is_` prefix in `Truffle::Interop.is_array_element_*` predicates.
* `Truffle::Interop.hash_keys_as_members` has been added to treat a Ruby Hash as a polyglot object with the Hash keys as members.

Performance:

* Optimized `RSTRING_PTR()` accesses by going to native directly, optimized various core methods, use Mode=latency and tune GC heap size for Bundler. This speeds up `bundle install` from 84s to 19s for a small Gemfile with 6 gems (#1398).
* Fixed memory footprint issue due to large compilation on Native Image, notably during `bundle install` (#1893).
* `ArrayBuilderNode` now uses a new Truffle library for manipulating array stores.
* Ruby objects passed to C extensions are now converted less often to native handles.
* Calling blocking system calls and running C code with unblocking actions has been refactored to remove some optimisation boundaries.
* `return` expressions are now rewritten as implicit return expressions where control flow allows this to be safely done as a tail optimisation. This can improve interpreter performance by up to 50% in some benchmarks, and can be applied to approximately 80% of return nodes seen in Rails and its dependencies (#1977).
* The old array strategy code has been removed and all remaining nodes converted to the new `ArrayStoreLibrary`.
* Updated `nil` to be a global immutable singleton (#1835).

# 20.0.0

New features:

* Enable and document `--coverage` option (#1840, @chrisseaton).
* Update the internal LLVM toolchain to LLVM 9 and reduce its download size.
* Updated to Ruby 2.6.5 (#1749).
* Automatically set `PKG_CONFIG_PATH` as needed for compiling OpenSSL on macOS (#1830).

Bug fixes:

* Fix `Tempfile#{size,length}` when the IO is not flushed (#1765, @rafaelfranca).
* Dump and load instance variables in subclasses of `Exception` (#1766, @rafaelfranca).
* Fix `Date._iso8601` and `Date._rfc3339` when the string is an invalid date (#1773, @rafaelfranca).
* Fail earlier for bad handle unwrapping (#1777, @chrisseaton).
* Match out of range `ArgumentError` message with MRI (#1774, @rafaelfranca).
* Raise `Encoding::CompatibilityError` with incompatible encodings on `Regexp` (#1775, @rafaelfranca).
* Fixed interactions between attributes and instance variables in `Struct` (#1776, @chrisseaton).
* Coercion fixes for `TCPServer.new` (#1780, @XrXr).
* Fix `Float#<=>` not calling `coerce` when `other` argument responds to it (#1783, @XrXr).
* Do not warn / crash when requiring a file that sets and trigger autoload on itself (#1779, @XrXr).
* Strip trailing whitespaces when creating a `BigDecimal` with a `String` (#1796, @XrXr).
* Default `close_others` in `Process.exec` to `false` like Ruby 2.6 (#1798, @XrXr).
* Don't clone methods when setting method to the same visibility (#1794, @XrXr).
* `BigDecimal()` deal with large rationals precisely (#1797, @XrXr).
* Make it possible to call `instance_exec` with `rb_block_call` (#1802, @XrXr).
* Check for duplicate members in `Struct.new` (#1803, @XrXr).
* `Process::Status#to_i` return raw `waitpid(2)` status (#1800, @XrXr).
* `Process#exec`: set close-on-exec to false for fd redirection (#1805, @XrXr, @rafaelfranca).
* Building C extensions should now work with frozen string literals (#1786).
* Keep the Truffle working directory in sync with the native working directory.
* Rename `to_native` to `polyglot_to_native` to match `polyglot_pointer?` and `polyglot_address` methods.
* Fixed missing partial evaluation boundary in `Array#{sort,sort!}` (#1727).
* Fixed the class of `self` and the wrapping `Module` for `Kernel#load(path, wrap=true)` (#1739).
* Fixed missing polyglot type declaration for `RSTRING_PTR` to help with native/managed interop.
* Fixed `Module#to_s` and `Module#inspect` to not return an extra `#<Class:` for singleton classes.
* Arrays backed by native storage now allocate the correct amount of memory (#1828).
* Fixed issue in `ConditionVariable#wait` that could lose a `ConditionVariable#signal`.
* Do not expose TruffleRuby-specific method `Array#swap` (#1816).
* Fixed `#inspect` on broken UTF-8 sequences (#1842, @chrisseaton).
* `Truffle::Interop.keys` should report methods of `String` and `Symbol` (#1817).
* `Kernel#sprintf` encoding validity has been fixed (#1852, @XrXr).
* Fixed `ArrayIndexOutOfBoundsException` in `File.fnmatch` (#1845).
* Make `String#concat` work with no or multiple arguments (#1519).
* Make `Array#concat` work with no or multiple arguments (#1519).
* Coerce `BigDecimal(arg)` using `to_str` (#1826).
* Fixed `NameError#dup`, `NoMethodError#dup`, and `SystemCallError#dup` to copy internal fields.
* Make `Enumerable#chunk` work without a block (#1518).
* Fixed issue with `SystemCallError.new` setting a backtrace too early.
* Fixed `BigDecimal#to_s` formatting issue (#1711).
* Run `END` keyword block only once at exit.
* Implement `Numeric#clone` to return `self`.
* Fixed `Symbol#to_proc` to create a `Proc` with `nil` `source_location` (#1663).
* Make `GC.start` work with keyword arguments.
* Fixed `Kernel#clone` for `nil`, `true`, `false`, `Integer`, and `Symbol`.
* Make top-level methods available in `Context#getBindings()` (#1838).
* Made `Kernel#caller_locations` accept a range argument, and return `nil` when appropriate.
* Made `rb_respond_to` work with primitives (#1869, @chrisseaton).
* Fixed issue with missing backtrace for `rescue $ERROR_INFO` (#1660).
* Fixed `Struct#hash` for `keyword_init: true` `Struct`.
* Fixed `String#{upcase!,downcase!,swapcase!}(:ascii)` for non-ASCII-compatible encodings like UTF-16.
* Fixed `String#capitalize!` for strings that weren't full ASCII.
* Fixed enumeration issue in `ENV.{select, filter}`.
* Fixed `Complex` and `Rational` should be frozen after initializing.
* Fixed `printf` should raise error when not enough arguments for positional argument.
* Removed "shadowing outer local variable" warning.
* Fixed parameter conversion to `String` in ENV methods.
* Fixed deprecation warning when `ENV.index` is called.
* Fixed issue with `ENV.each_key`.
* Fixed `ENV.replace` implementation.
* Fixed `ENV.udpate` implementation.
* Fixed argument handling in `Kernel.printf`.
* Fixed character length after conversion to binary from a non-US-ASCII String.
* Fixed issue with installing latest bundler (#1880).
* Fixed type conversion for `Numeric#step` `step` parameter.
* Fixed `Kernel#Integer` conversion.
* Fixed `IO.try_convert` parameter conversion.
* Fixed linking of always-inline C API functions with `-std=gnu90` (#1837, #1879).
* Avoid race conditions during `gem install` by using a single download thread.
* Do not use gems precompiled for MRI on TruffleRuby (#1837).
* Fixed printing foreign arrays that were also pointers (#1679).
* Fixed `nil#=~` to not warn.
* Fixed `Enumerable#collect` to give user block arity in the block passed to `Enumerable#each`.

Compatibility:

* Implemented `String#start_with?(Regexp)` (#1771, @zhublik).
* Various improvements to `SignalException` and signal handling (#1790, @XrXr).
* Implemented `rb_utf8_str_new`, `rb_utf8_str_new_cstr`, `rb_utf8_str_new_static` (#1788, @chrisseaton).
* Implemented the `unit` argument of `Time.at` (#1791, @XrXr).
* Implemented `keyword_init: true` for `Struct.new` (#1789, @XrXr).
* Implemented `MatchData#dup` (#1792, @XrXr).
* Implemented a native storage strategy for `Array` to allow better C extension compatibility.
* Implemented `rb_check_symbol_cstr` (#1814).
* Implemented `rb_hash_start` (#1841, @XrXr).
* JCodings has been updated from 1.0.42 to 1.0.45.
* Joni has been updated from 2.1.25 to 2.1.30.
* Implemented `Method#<<` and `Method#>>` (#1821).
* The `.bundle` file extension is now used for C extensions on macOS (#1819, #1837).
* Implemented `Comparable#clamp` (#1517).
* Implemented `rb_gc_register_mark_object` and `rb_enc_str_asciionly_p` (#1856, @chrisseaton).
* Implemented `rb_io_set_nonblock` (#1741).
* Include the major kernel version in `RUBY_PLATFORM` on macOS like MRI (#1860, @eightbitraptor).
* Implemented `Enumerator::Chain`, `Enumerator#+`, and `Enumerable#chain` (#1859, #1858).
* Implemented `Thread#backtrace_locations` and `Exception#backtrace_locations` (#1556).
* Implemented `rb_module_new`, `rb_define_class_id`, `rb_define_module_id`, (#1876, @XrXr, @chrisseaton).
* Implemented `-n` CLI option (#1532).
* Cache the `Symbol` of method names in call nodes only when needed (#1872).
* Implemented `rb_get_alloc_func` and related functions (#1874, @XrXr).
* Implemented `rb_module_new`, `rb_define_class_id`, `rb_define_module_id`, (#1876, @chrisseaton).
* Implemented `ENV.slice`.
* Support for the Darkfish theme for RDoc generation has been added back.
* Implemented `Kernel#system` `exception: true` option.
* Implemented `Random.bytes`.
* Implemented `Random.random_number`.
* Added the ability to parse endless ranges.
* Made `Range#{to_a, step, each, bsearch, step, last, max, min, to_s, ==}` compatible with endless ranges.
* Made `Array#{[], []=, values_at, fill, slice!}` compatible with endless ranges.
* Defined `Array#{min, max}` methods.

Performance:

* Use a smaller limit for identity-based inline caches to improve warmup by avoiding too many deoptimizations.
* `long[]` array storage now correctly declare that they accept `int` values, reducing deoptimisations and promotions to `Object[]` storage.
* Enable inline caching of `Symbol` conversion for `rb_iv_get` and `rb_iv_set`.
* `rb_type` information is now cached on classes as a hidden variable to improve performance.
* Change to using thread local buffers for socket calls to reduce allocations.
* Refactor `IO.select` to reduce copying and optimisation boundaries.
* Refactor various `String` and `Rope` nodes to avoid Truffle performance warnings.
* Reading caller frames should now work in more cases without deoptimisation.

# 19.3.0

New features:

* Compilation of C extensions is now done with an internal LLVM toolchain producing both native code and bitcode. This means more C extensions should compile out of the box and this should resolve most linker-related issues.
* It is no longer necessary to install LLVM for installing C extensions on TruffleRuby.
* It is no longer necessary to install libc++ and libc++abi for installing C++ extensions on TruffleRuby.
* On macOS, it is no longer necessary to install the system headers package (#1417).
* License updated to EPL 2.0/GPL 2.0/LGPL 2.1 like recent JRuby.

Bug fixes:

* `rb_undef_method` now works for private methods (#1731, @cky).
* Fixed several issues when requiring C extensions concurrently (#1565).
* `self.method ||= value` with a private method now works correctly (#1673).
* Fixed `RegexpError: invalid multibyte escape` for binary regexps with a non-binary String (#1433).
* Arrays now report their methods to other languages for interopability (#1768).
* Installing `sassc` now works due to using the LLVM toolchain (#1753).
* Renamed `Truffle::Interop.respond_to?` to avoid conflict with Ruby's `respond_to?` (#1491).
* Warn only if `$VERBOSE` is `true` when a magic comment is ignored (#1757, @nirvdrum).
* Make C extensions use the same libssl as the one used for the openssl C extension (#1770).

Compatibility:

* `GC.stat` can now take an optional argument (#1716, @kirs).
* `Kernel#load` with `wrap` has been implemented (#1739).
* Implemented `Kernel#spawn` with `:chdir` (#1492).
* Implemented `rb_str_drop_bytes`, notably used by OpenSSL (#1740, @cky).
* Include executables of default gems, needed for `rails new` in Rails 6.
* Use compilation flags similar to MRI for C extension compilation.
* Warn for `gem update --system` as it is not fully supported yet and is often not needed.
* Pass `-undefined dynamic_lookup` to the linker on macOS like MRI.

Performance:

* Core methods are no longer always cloned, which reduces memory footprint and should improve warmup.
* Inline cache calls to `rb_intern()` with a constant name in C extensions.
* Improve allocation speed of native handles for C extensions.
* Improve the performance of `NIL_P` and `INT2FIX` in C extensions.
* Various fixes to improve Rack performance.
* Optimize `String#gsub(String)` by not creating a `Regexp` and using `String#index` instead.
* Fixed "FrameWithoutBoxing should not be materialized" compilation issue in `TryNode`.

# 19.2.0, August 2019

New features:

* `Fiddle` has been implemented.

Bug fixes:

* Set `RbConfig::CONFIG['ruby_version']` to the same value as the TruffleRuby version. This fixes reusing C extensions between different versions of TruffleRuby with Bundler (#1715).
* Fixed `Symbol#match` returning `MatchData` (#1706, @zhublik).
* Allow `Time#strftime` to be called with binary format strings.
* Do not modify the argument passed to `IO#write` when the encoding does not match (#1714).
* Use the class where the method was defined to check if an `UnboundMethod` can be used for `#define_method` (#1710).
* Fixed setting `$~` for `Enumerable` and `Enumerator::Lazy`'s `#grep` and `#grep_v`.
* Improved errors when interacting with single-threaded languages (#1709).

Compatibility:

* Added `Kernel#then` (#1703, @zhublik).
* `FFI::Struct#[]=` is now supported for inline character arrays.
* `blocking: true` is now supported for `FFI::Library#attach_function`.
* Implemented `Proc#>>` and `#<<` (#1688).
* `Thread.report_on_exception` is now `true` by default like MRI 2.5+.
* `BigDecimal` compatibility has been generally improved in several ways.

Changes:

* An interop read message sent to a `Proc` will no longer call the `Proc`.

Performance:

* Several `String` methods have been made faster by the usage of vector instructions
  when searching for a single-byte character in a String.
* Methods needing the caller frame are now better optimized.

# 19.1.0, June 2019

*Ruby is an experimental language in the GraalVM 19.1.0 release*

Bug fixes:

* Sharing for thread-safety of objects is now triggered later as intended, e.g., when a second `Thread` is started.
* Fixed `Array#to_h` so it doesn't set a default value (#1698).
* Removed extra `public` methods on `IO` (#1702).
* Fixed `Process.kill(signal, Process.pid)` when the signal is trapped as `:IGNORE` (#1702).
* Fixed `Addrinfo.new(String)` to reliably find the address family (#1702).
* Fixed argument checks in `BasicSocket#setsockopt` (#1460).
* Fixed `ObjectSpace.trace_object_allocations` (#1456).
* Fixed `BigDecimal#{clone,dup}` so it now just returns the receiver, per Ruby 2.5+ semantics (#1680).
* Fixed creating `BigDecimal` instances from non-finite `Float` values (#1685).
* Fixed `BigDecimal#inspect` output for non-finite values (e.g, NaN or -Infinity) (#1683).
* Fixed `BigDecimal#hash` to return the same value for two `BigDecimal` objects that are equal (#1656).
* Added missing `BigDecimal` constant definitions (#1684).
* Implemented `rb_eval_string_protect`.
* Fixed `rb_get_kwargs` to correctly handle optional and rest arguments.
* Calling `Kernel#raise` with a raised exception will no longer set the cause of the exception to itself (#1682).
* Return a `FFI::Function` correctly for functions returning a callback.
* Convert to intuitive Ruby exceptions when INVOKE fails (#1690).
* Implemented `FFI::Pointer#clear` (#1687).
* Procs will now yield to the block in their declaration context even when called with a block argument (#1657).
* Fixed problems with calling POSIX methods if `Symbol#[]` is redefined (#1665).
* Fixed sharing of `Array` and `Hash` elements for thread-safety of objects (#1601).
* Fixed concurrent modifications of `Gem::Specification::LOAD_CACHE` (#1601).
* Fix `TCPServer#accept` to set `#do_not_reverse_lookup` correctly on the created `TCPSocket`.

Compatibility:

* Exceptions from `coerce` are no longer rescued, like MRI.
* Implemented `Integer#{allbits?,anybits?,nobits?}`.
* `Integer#{ceil,floor,truncate}` now accept a precision and `Integer#round` accepts a rounding mode.
* Added missing `Enumerable#filter` and `Enumerator::Lazy#filter` aliases to the respective `select` method (#1610).
* Implemented more `Ripper` methods as no-ops (#1694, @Mogztter).
* Implemented `rb_enc_sprintf` (#1702).
* Implemented `ENV#{filter,filter!}` aliases for `select` and `select!`.
* Non-blocking `StringIO` and `Socket` APIs now support `exception: false` like MRI (#1702).
* Increased compatibility of `BigDecimal`.
* `String#-@` now performs string deduplication (#1608).
* `Hash#merge` now preserves the key order from the original hash for merged values (#1650).
* Coerce values given to `FFI::Pointer` methods.
* `FrozenError` is now defined and is used for `can't modify frozen` object exceptions.
* `StringIO` is now available by default like in MRI, because it is required by RubyGems.

Changes:

* Interactive sources (like the GraalVM polyglot shell) now all share the same binding (#1695).
* Hash code calculation has been improved to reduce hash collisions for `Hash` and other cases.

Performance:

* `eval(code, binding)` for a fixed `code` containing blocks is now much faster. This improves the performance of rendering `ERB` templates containing loops.
* `rb_str_cat` is faster due to the C string now being concatenated without first being converted to a Ruby string or having its encoding checked. As a side effect the behaviour of `rb_str_cat` should now more closely match that of MRI.

# 19.0.0, May 2019

*Ruby is an experimental language in the GraalVM 19.0.0 release*

Bug fixes:

* The debugger now sees global variables as the global scope.
* Temporary variables are no longer visible in the debugger.
* Setting breakpoints on some lines has been fixed.
* The OpenSSL C extension is now always recompiled, fixing various bugs when using the extension (e.g., when using Bundler in TravisCI) (#1676, #1627, #1632).
* Initialize `$0` when not run from the 'ruby' launcher, which is needed to `require` gems (#1653).

Compatibility:

* `do...end` blocks can now have `rescue/else/ensure` clauses like MRI (#1618).

Changes:

* `TruffleRuby.sulong?` has been replaced by `TruffleRuby.cexts?`, and `TruffleRuby.graal?` has been replaced by `TruffleRuby.jit?`. The old methods will continue to work for now, but will produce warnings, and will be removed at a future release.

# 1.0 RC 16, 19 April 2019

Bug fixes:

* Fixed `Hash#merge` with no arguments to return a new copy of the receiver (#1645).
* Fixed yield with a splat and keyword arguments (#1613).
* Fixed `rb_scan_args` to correctly handle kwargs in combination with optional args.
* Many fixes for `FFI::Pointer` to be more compatible with the `ffi` gem.

New features:

* Rounding modes have been implemented or improved for `Float`, `Rational`, `BigDecimal` (#1509).
* Support Homebrew installed in other prefixes than `/usr/local` (#1583).
* Added a pure-Ruby implementation of FFI which passes almost all Ruby FFI specs (#1529, #1524).

Changes:

* Support for the Darkfish theme for RDoc generation has been removed.

Compatibility:

* The `KeyError` raised from `ENV#fetch` and `Hash#fetch` now matches MRI's message formatting (#1633).
* Add the missing `key` and `receiver` values to `KeyError` raised from `ENV#fetch`.
* `String#unicode_normalize` has been moved to the core library like in MRI.
* `StringScanner` will now match a regexp beginning with `^` even when not scanning from the start of the string.
* `Module#define_method` is now public like in MRI.
* `Kernel#warn` now supports the `uplevel:` keyword argument.

# 1.0 RC 15, 5 April 2019

Bug fixes:

* Improved compatibility with MRI's `Float#to_s` formatting (#1626).
* Fixed `String#inspect` when the string uses a non-UTF-8 ASCII-compatible encoding and has non-ASCII characters.
* Fixed `puts` for strings with non-ASCII-compatible encodings.
* `rb_protect` now returns `Qnil` when an error occurs.
* Fixed a race condition when using the interpolate-once (`/o`) modifier in regular expressions.
* Calling `StringIO#close` multiple times no longer raises an exception (#1640).
* Fixed a bug in include file resolution when compiling C extensions.

New features:

* `Process.clock_getres` has been implemented.

Changes:

* `debug`, `profile`, `profiler`, which were already marked as unsupported, have been removed.
* Our experimental JRuby-compatible Java interop has been removed - use `Polyglot` and `Java` instead.
* The Trufle handle patches applied to `psych` C extension have now been removed.
* The `rb_tr_handle_*` functions have been removed as they are no longer used in any C extension patches.
* Underscores and dots in options have become hyphens, so `--exceptions.print_uncaught_java` is now `--exceptions-print-uncaught-java`, for example.
* The `rb_tr_handle_*` functions have been removed as they are no longer used in any C extension patches.

Bug fixes:

* `autoload :C, "path"; require "path"` now correctly triggers the autoload.
* Fixed `UDPSocket#bind` to specify family and socktype when resolving address.
* The `shell` standard library can now be `require`-d.
* Fixed a bug where `for` could result in a `NullPointerException` when trying to assign the iteration variable.
* Existing global variables can now become aliases of other global variables (#1590).

Compatibility:

* ERB now uses StringScanner and not the fallback, like on MRI. As a result `strscan` is required by `require 'erb'` (#1615).
* Yield different number of arguments for `Hash#each` and `Hash#each_pair` based on the block arity like MRI (#1629).
* Add support for the `base` keyword argument to `Dir.{[], glob}`.

# 1.0 RC 14, 18 March 2019

Updated to Ruby 2.6.2.

Bug fixes:

* Implement `rb_io_wait_writable` (#1586).
* Fixed error when using arrows keys first within `irb` or `pry` (#1478, #1486).
* Coerce the right hand side for all `BigDecimal` operations (#1598).
* Combining multiple `**` arguments containing duplicate keys produced an incorrect hash. This has now been fixed (#1469).
* `IO#read_nonblock` now returns the passed buffer object, if one is supplied.
* Worked out autoloading issue (#1614).

New features:

* Implemented `String#delete_prefix`, `#delete_suffix`, and related methods.
* Implemented `Dir.children` and `Dir#children`.
* Implemented `Integer#sqrt`.

Changes:

* `-Xoptions` has been removed - use `--help:languages` instead.
* `-Xlog=` has been removed - use `--log.level=` instead.
* `-J` has been removed - use `--vm.` instead.
* `-J-cp lib.jar` and so on have removed - use `--vm.cp=lib.jar` or `--vm.classpath=lib.jar` instead.
* `--jvm.` and `--native.` have been deprecated, use `--vm.` instead to pass VM options.
* `-Xoption=value` has been removed - use `--option=value` instead.
* The `-X` option now works as in MRI.
* `--help:debug` is now `--help:internal`.
* `ripper` is still not implemented, but the module now exists and has some methods that are implemented as no-ops.

# 1.0 RC 13, 5 March 2019

Note that as TruffleRuby RC 13 is built on Ruby 2.4.4 it is still vulnerable to CVE-2018-16395. This will be fixed in the next release.

New features:

* Host interop with Java now works on SubstrateVM too.

Bug fixes:

* Fixed `Enumerator::Lazy` which wrongly rescued `StandardError` (#1557).
* Fixed several problems with `Numeric#step` related to default arguments, infinite sequences, and bad argument types (#1520).
* Fixed incorrect raising of `ArgumentError` with `Range#step` when at least one component of the `Range` is `Float::INFINITY` (#1503).
* Fixed the wrong encoding being associated with certain forms of heredoc strings (#1563).
* Call `#coerce` on right hand operator if `BigDecimal` is the left hand operator (#1533, @Quintasan).
* Fixed return type of division of `Integer.MIN_VALUE` and `Long.MIN_VALUE` by -1 (#1581).
* `Exception#cause` is now correctly set for internal exceptions (#1560).
* `rb_num2ull` is now implemented as well as being declared in the `ruby.h` header (#1573).
* `rb_sym_to_s` is now implemented (#1575).
* `R_TYPE_P` now returns the type number for a wider set of Ruby objects (#1574).
* `rb_fix2str` has now been implemented.
* `rb_protect` will now work even if `NilClass#==` has been redefined.
* `BigDecimal` has been moved out of the `Truffle` module to match MRI.
* `StringIO#puts` now correctly handles `to_s` methods which do not return strings (#1577).
* `Array#each` now behaves like MRI when the array is modified (#1580).
* Clarified that `$SAFE` can never be set to a non-zero value.
* Fix compatibility with RubyGems 3 (#1558).
* `Kernel#respond_to?` now returns false if a method is protected and the `include_all` argument is false (#1568).

Changes:

* `TRUFFLERUBY_CEXT_ENABLED` is no longer supported and C extensions are now always built, regardless of the value of this environment variable.
* Getting a substring of a string created by a C extension now uses less memory as only the requested portion will be copied to a managed string.
* `-Xoptions` has been deprecated and will be removed - use `--help:languages` instead.
* `-Xlog=` has been deprecated and will be removed - use `--log.level=` instead.
* `-J` has been deprecated and will be removed - use `--jvm.` instead.
* `-J-cp lib.jar` and so on have been deprecated and will be removed - use `--jvm.cp=lib.jar` or `--jvm.classpath=lib.jar` instead.
* `-J-cmd`, `--jvm.cmd`, `JAVA_HOME`, `JAVACMD`, and `JAVA_OPTS` do not work in any released configuration of TruffleRuby, so have been removed.
* `-Xoption=value` has been deprecated and will be removed - use `--option=value` instead.
* `TracePoint` now raises an `ArgumentError` for unsupported events.
* `TracePoint.trace` and `TracePoint#inspect` have been implemented.

Compatibility:

* Improved the exception when an `-S` file isn't found.
* Removed the message from exceptions raised by bare `raise` to better match MRI (#1487).
* `TracePoint` now handles the `:class` event.

Performance:

* Sped up `String` handling in native extensions, quite substantially in some cases, by reducing conversions between native and managed strings and allowing for mutable metadata in native strings.

# 1.0 RC 12, 4 February 2019

Bug fixes:

* Fixed a bug with `String#lines` and similar methods with multibyte characters (#1543).
* Fixed an issue with `String#{encode,encode!}` double-processing strings using XML conversion options and a new destination encoding (#1545).
* Fixed a bug where a raised cloned exception would be caught as the original exception (#1542).
* Fixed a bug with `StringScanner` and patterns starting with `^` (#1544).
* Fixed `Enumerable::Lazy#uniq` with infinite streams (#1516).

Compatibility:

* Change to a new system for handling Ruby objects in C extensions which greatly increases compatibility with MRI.
* Implemented `BigDecimal#to_r` (#1521).
* `Symbol#to_proc` now returns `-1` like on MRI (#1462).

# 1.0 RC 11, 15 January 2019

New features:

* macOS clocks `CLOCK_MONOTONIC_RAW`, `_MONOTONIC_RAW_APPROX`, `_UPTIME_RAW`, `_UPTIME_RAW_APPROX`, and `_PROCESS_CPUTIME_ID` have been implemented (#1480).
* TruffleRuby now automatically detects native access and threading permissions from the `Context` API, and can run code with no permissions given (`Context.create()`).

Bug fixes:

* FFI::Pointer now does the correct range checks for signed and unsigned values.
* Allow signal `0` to be used with `Process.kill` (#1474).
* `IO#dup` now properly sets the new `IO` instance to be close-on-exec.
* `IO#reopen` now properly resets the receiver to be close-on-exec.
* `StringIO#set_encoding` no longer raises an exception if the underlying `String` is frozen (#1473).
* Fix handling of `Symbol` encodings in `Marshal#dump` and `Marshal#load` (#1530).

Compatibility:

* Implemented `Dir.each_child`.
* Adding missing support for the `close_others` option to `exec` and `spawn`.
* Implemented the missing `MatchData#named_captures` method (#1512).

Changes:

* `Process::CLOCK_` constants have been given the same value as in standard Ruby.

Performance:

* Sped up accesses to native memory through FFI::Pointer.
* All core files now make use of frozen `String` literals, reducing the number of `String` allocations for core methods.
* New -Xclone.disable option to disable all manual cloning.

# 1.0 RC 10, 5 December 2018

New features:

* The `nkf` and `kconv` standard libraries were added (#1439).
* `Mutex` and `ConditionVariable` have a new fast path for acquiring locks that are unlocked.
* `Queue` and `SizedQueue`, `#close` and `#closed?`, have been implemented.
* `Kernel#clone(freeze)` has been implemented (#1454).
* `Warning.warn` has been implemented (#1470).
* `Thread.report_on_exception` has been implemented (#1476).
* The emulation symbols for `Process.clock_gettime` have been implemented.

Bug fixes:

* Added `rb_eEncodingError` for C extensions (#1437).
* Fixed race condition when creating threads (#1445).
* Handle `exception: false` for IO#write_nonblock (#1457, @ioquatix).
* Fixed `Socket#connect_nonblock` for the `EISCONN` case (#1465, @ioquatix).
* `File.expand_path` now raises an exception for a non-absolute user-home.
* `ArgumentError` messages now better match MRI (#1467).
* Added support for `:float_millisecond`, `:millisecond`, and `:second` time units to `Process.clock_gettime` (#1468).
* Fixed backtrace of re-raised exceptions (#1459).
* Updated an exception message in Psych related to loading a non-existing class so that it now matches MRI.
* Fixed a JRuby-style Java interop compatibility issue seen in `test-unit`.
* Fixed problem with calling `warn` if `$stderr` has been reassigned.
* Fixed definition of `RB_ENCODING_GET_INLINED` (#1440).

Changes:

* Timezone messages are now logged at `CONFIG` level, use `-Xlog=CONFIG` to debug if the timezone is incorrectly shown as `UTC`.

# 1.0 RC 9, 5 November 2018

Security:

* CVE-2018-16396, *tainted flags are not propagated in Array#pack and String#unpack with some directives* has been mitigated by adding additional taint operations.

New features:

* LLVM for Oracle Linux 7 can now be installed without building from source.

Bug fixes:

* Times can now be created with UTC offsets in `+/-HH:MM:SS` format.
* `Proc#to_s` now has `ASCII-8BIT` as its encoding instead of the incorrect `UTF-8`.
* `String#%` now has the correct encoding for `UTF-8` and `US-ASCII` format strings, instead of the incorrect `ASCII-8BIT`.
* Updated `BigDecimal#to_s` to use `e` instead of `E` for exponent notation.
* Fixed `BigDecimal#to_s` to allow `f` as a format flag to indicate conventional floating point notation. Previously only `F` was allowed.

Changes:

* The supported version of LLVM for Oracle Linux has been updated from 3.8 to 4.0.
* `mysql2` is now patched to avoid a bug in passing `NULL` to `rb_scan_args`, and now passes the majority of its test suite.
* The post-install script now automatically detects if recompiling the OpenSSL C extension is needed. The post-install script should always be run in TravisCI as well.
* Detect when the system libssl is incompatible more accurately and add instructions on how to recompile the extension.

# 1.0 RC 8, 19 October 2018

New features:

* `Java.synchronized(object) { }` and `TruffleRuby.synchronized(object) { }` methods have been added.
* Added a `TruffleRuby::AtomicReference` class.
* Ubuntu 18.04 LTS is now supported.
* macOS 10.14 (Mojave) is now supported.

Changes:

* Random seeds now use Java's `NativePRNGNonBlocking`.
* The supported version of Fedora is now 28, upgraded from 25.
* The FFI gem has been updated from 1.9.18 to 1.9.25.
* JCodings has been updated from 1.0.30 to 1.0.40.
* Joni has been updated from 2.1.16 to 2.1.25.

Performance:

* Performance of setting the last exception on a thread has now been improved.

# 1.0 RC 7, 3 October 2018

New features:

* Useful `inspect` strings have been added for more foreign objects.
* The C extension API now defines a preprocessor macro `TRUFFLERUBY`.
* Added the rbconfig/sizeof native extension for better MRI compatibility.
* Support for `pg` 1.1. The extension now compiles successfully, but may still have issues with some datatypes.

Bug fixes:

* `readline` can now be interrupted by the interrupt signal (Ctrl+C). This fixes Ctrl+C to work in IRB.
* Better compatibility with C extensions due to a new "managed struct" type.
* Fixed compilation warnings which produced confusing messages for end users (#1422).
* Improved compatibility with Truffle polyglot STDIO.
* Fixed version check preventing TruffleRuby from working with Bundler 2.0 and later (#1413).
* Fixed problem with `Kernel.public_send` not tracking its caller properly (#1425).
* `rb_thread_call_without_gvl()` no longer holds the C-extensions lock.
* Fixed `caller_locations` when called inside `method_added`.
* Fixed `mon_initialize` when called inside `initialize_copy` (#1428).
* `Mutex` correctly raises a `TypeError` when trying to serialize with `Marshal.dump`.

Performance:

* Reduced memory footprint for private/internal AST nodes.
* Increased the number of cases in which string equality checks will become compile-time constants.
* Major performance improvement for exceptional paths where the rescue body does not access the exception object (e.g., `x.size rescue 0`).

Changes:

* Many clean-ups to our internal patching mechanism used to make some native extensions run on TruffleRuby.
* Removed obsoleted patches for Bundler compatibility now that Bundler 1.16.5 has built-in support for TruffleRuby.
* Reimplemented exceptions and other APIs that can return a backtrace to use Truffle's lazy stacktraces API.

# 1.0 RC 6, 3 September 2018

New features:

* `Polyglot.export` can now be used with primitives, and will now convert strings to Java, and `.import` will convert them from Java.
* Implemented `--encoding`, `--external-encoding`, `--internal-encoding`.
* `rb_object_tainted` and similar C functions have been implemented.
* `rb_struct_define_under` has been implemented.
* `RbConfig::CONFIG['sysconfdir']` has been implemented.
* `Etc` has been implemented (#1403).
* The `-Xcexts=false` option disables C extensions.
* Instrumentation such as the CPUSampler reports methods in a clearer way like `Foo#bar`, `Gem::Specification.each_spec`, `block in Foo#bar` instead of just `bar`, `each_spec`, `block in bar` (which is what MRI displays in backtraces).
* TruffleRuby is now usable as a JSR 223 (`javax.script`) language.
* A migration guide from JRuby (`doc/user/jruby-migration.md`) is now included.
* `kind_of?` works as an alias for `is_a?` on foreign objects.
* Boxed foreign strings unbox on `to_s`, `to_str`, and `inspect`.

Bug fixes:

* Fix false-positive circular warning during autoload.
* Fix Truffle::AtomicReference for `concurrent-ruby`.
* Correctly look up `llvm-link` along `clang` and `opt` so it is no longer needed to add LLVM to `PATH` on macOS for Homebrew and MacPorts.
* Fix `alias` to work when in a refinement module (#1394).
* `Array#reject!` no longer truncates the array if the block raises an exception for an element.
* WeakRef now has the same inheritance and methods as MRI's version.
* Support `-Wl` linker argument for C extensions. Fixes compilation of`mysql2` and `pg`.
* Using `Module#const_get` with a scoped argument will now correctly autoload the constant if needed.
* Loaded files are read as raw bytes, rather than as a UTF-8 string and then converted back into bytes.
* Return 'DEFAULT' for `Signal.trap(:INT) {}`. Avoids a backtrace when quitting a Sinatra server with Ctrl+C.
* Support `Signal.trap('PIPE', 'SYSTEM_DEFAULT')`, used by the gem `rouge` (#1411).
* Fix arity checks and handling of arity `-2` for `rb_define_method()`.
* Setting `$SAFE` to a negative value now raises a `SecurityError`.
* The offset of `DATA` is now correct in the presence of heredocs.
* Fix double-loading of the `json` gem, which led to duplicate constant definition warnings.
* Fix definition of `RB_NIL_P` to be early enough. Fixes compilation of `msgpack`.
* Fix compilation of megamorphic interop calls.
* `Kernel#singleton_methods` now correctly ignores prepended modules of non-singleton classes. Fixes loading `sass` when `activesupport` is loaded.
* Object identity numbers should never be negative.

Performance:

* Optimize keyword rest arguments (`def foo(**kwrest)`).
* Optimize rejected (non-Symbol keys) keyword arguments.
* Source `SecureRandom.random_bytes` from `/dev/urandom` rather than OpenSSL.
* C extension bitcode is no longer encoded as Base64 to pass it to Sulong.
* Faster `String#==` using vectorization.

Changes:

* Clarified that all sources that come in from the Polyglot API `eval` method will be treated as UTF-8, and cannot be re-interpreted as another encoding using a magic comment.
* The `-Xembedded` option can now be set set on the launcher command line.
* The `-Xplatform.native=false` option can now load the core library, by enabling `-Xpolyglot.stdio`.
* `$SAFE` and `Thread#safe_level` now cannot be set to `1` - raising an error rather than warning as before. `-Xsafe` allows it to be set, but there are still no checks.
* Foreign objects are now printed as `#<Foreign:system-identity-hash-code>`, except for foreign arrays which are now printed as `#<Foreign [elements...]>`.
* Foreign objects `to_s` now calls `inspect` rather than Java's `toString`.
* The embedded configuration (`-Xembedded`) now warns about features which may not work well embedded, such as signals.
* The `-Xsync.stdio` option has been removed - use standard Ruby `STDOUT.sync = true` in your program instead.

# 1.0 RC 5, 3 August 2018

New features:

* It is no longer needed to add LLVM (`/usr/local/opt/llvm@4/bin`) to `PATH` on macOS.
* Improve error message when LLVM, `clang` or `opt` is missing.
* Automatically find LLVM and libssl with MacPorts on macOS (#1386).
* `--log.ruby.level=` can be used to set the log level from any launcher.
* Add documentation about installing with Ruby managers/installers and how to run TruffleRuby in CI such as TravisCI (#1062, #1070).
* `String#unpack1` has been implemented.

Bug fixes:

* Allow any name for constants with `rb_const_get()`/`rb_const_set()` (#1380).
* Fix `defined?` with an autoload constant to not raise but return `nil` if the autoload fails (#1377).
* Binary Ruby Strings can now only be converted to Java Strings if they only contain US-ASCII characters. Otherwise, they would produce garbled Java Strings (#1376).
* `#autoload` now correctly calls `main.require(path)` dynamically.
* Hide internal file from user-level backtraces (#1375).
* Show caller information in warnings from the core library (#1375).
* `#require` and `#require_relative` should keep symlinks in `$"` and `__FILE__` (#1383).
* Random seeds now always come directly from `/dev/urandom` for MRI compatibility.
* SIGINFO, SIGEMT and SIGPWR are now defined (#1382).
* Optional and operator assignment expressions now return the value assigned, not the value returned by an assignment method (#1391).
* `WeakRef.new` will now return the correct type of object, even if `WeakRef` is subclassed (#1391).
* Resolving constants in prepended modules failed, this has now been fixed (#1391).
* Send and `Symbol#to_proc` now take account of refinements at their call sites (#1391).
* Better warning when the timezone cannot be found on WSL (#1393).
* Allow special encoding names in `String#force_encoding` and raise an exception on bad encoding names (#1397).
* Fix `Socket.getifaddrs` which would wrongly return an empty array (#1375).
* `Binding` now remembers the file and line at which it was created for `#eval`. This is notably used by `pry`'s `binding.pry`.
* Resolve symlinks in `GEM_HOME` and `GEM_PATH` to avoid related problems (#1383).
* Refactor and fix `#autoload` so other threads see the constant defined while the autoload is in progress (#1332).
* Strings backed by `NativeRope`s now make a copy of the rope when `dup`ed.
* `String#unpack` now taints return strings if the format was tainted, and now does not taint the return array if the format was tainted.
* Lots of fixes to `Array#pack` and `String#unpack` tainting, and a better implementation of `P` and `p`.
* Array literals could evaluate an element twice under some circumstances. This has now been fixed.

Performance:

* Optimize required and optional keyword arguments.
* `rb_enc_to_index` is now faster by eliminating an expensive look-up.

Changes:

* `-Xlog=` now needs log level names to be upper case.
* `-Dtruffleruby.log` and `TRUFFLERUBY_LOG` have been removed - use `-Dpolyglot.log.ruby.level`.
* The log format, handlers, etc are now managed by the Truffle logging system.
* The custom log levels `PERFORMANCE` and `PATCH` have been removed.

# 1.0 RC 4, 18 July 2018

*TruffleRuby was not updated in RC 4*

# 1.0 RC 3, 2 July 2018

New features:

* `is_a?` can be called on foreign objects.

Bug fixes:

* It is no longer needed to have `ruby` in `$PATH` to run the post-install hook.
* `Qnil`/`Qtrue`/`Qfalse`/`Qundef` can now be used as initial value for global variables in C extensions.
* Fixed error message when the runtime libssl has no SSLv2 support (on Ubuntu 16.04 for instance).
* `RbConfig::CONFIG['extra_bindirs']` is now a String as other RbConfig values.
* `SIGPIPE` is correctly caught on SubstrateVM, and the corresponding write() raises `Errno::EPIPE` when the read end of a pipe or socket is closed.
* Use the magic encoding comment for determining the source encoding when using eval().
* Fixed a couple bugs where the encoding was not preserved correctly.

Performance:

* Faster stat()-related calls, by returning the relevant field directly and avoiding extra allocations.
* `rb_str_new()`/`rb_str_new_cstr()` are much faster by avoiding extra copying and allocations.
* `String#{sub,sub!}` are faster in the common case of an empty replacement string.
* Eliminated many unnecessary memory copy operations when reading from `IO` with a delimiter (e.g., `IO#each`), leading to overall improved `IO` reading for common use cases such as iterating through lines in a `File`.
* Use the byte[] of the given Ruby String when calling eval() directly for parsing.

# 1.0 RC 2, 6 June 2018

New features:

* We are now compatible with Ruby 2.4.4.
* `object.class` on a Java `Class` object will give you an object on which you can call instance methods, rather than static methods which is what you get by default.
* The log level can now also be set with `-Dtruffleruby.log=info` or `TRUFFLERUBY_LOG=info`.
* `-Xbacktraces.raise` will print Ruby backtraces whenever an exception is raised.
* `Java.import name` imports Java classes as top-level constants.
* Coercion of foreign numbers to Ruby numbers now works.
* `to_s` works on all foreign objects and calls the Java `toString`.
* `to_str` will try to `UNBOX` and then re-try `to_str`, in order to provoke the unboxing of foreign strings.

Changes:

* The version string now mentions if you're running GraalVM Community Edition (`GraalVM CE`) or GraalVM Enterprise Edition (`GraalVM EE`).
* The inline JavaScript functionality `-Xinline_js` has been removed.
* Line numbers `< 0`, in the various eval methods, are now warned about, because we don't support these at all. Line numbers `> 1` are warned about (at the fine level) but they are shimmed by adding blank lines in front to get to the correct offset. Line numbers starting at `0` are also warned about at the fine level and set to `1` instead.
* The `erb` standard library has been patched to stop using a -1 line number.
* `-Xbacktraces.interleave_java` now includes all the trailing Java frames.
* Objects with a `[]` method, except for `Hash`, now do not return anything for `KEYS`, to avoid the impression that you could `READ` them. `KEYINFO` also returns nothing for these objects, except for `Array` where it returns information on indices.
* `String` now returns `false` for `HAS_KEYS`.
* The supported additional functionality module has been renamed from `Truffle` to `TruffleRuby`. Anything not documented in `doc/user/truffleruby-additions.md` should not be used.
* Imprecise wrong gem directory detection was replaced. TruffleRuby newly marks its gem directories with a marker file, and warns if you try to use TruffleRuby with a gem directory which is lacking the marker.

Bug fixes:

* TruffleRuby on SubstrateVM now correctly determines the system timezone.
* `Kernel#require_relative` now coerces the feature argument to a path and canonicalizes it before requiring, and it now uses the current directory as the directory for a synthetic file name from `#instance_eval`.

# 1.0 RC 1, 17 April 2018

New features:

* The Ruby version has been updated to version 2.3.7.

Security:

* CVE-2018-6914, CVE-2018-8779, CVE-2018-8780, CVE-2018-8777, CVE-2017-17742 and CVE-2018-8778 have been mitigated.

Changes:

* `RubyTruffleError` has been removed and uses replaced with standard exceptions.
* C++ libraries like `libc++` are now not needed if you don't run C++ extensions. `libc++abi` is now never needed. Documentation updated to make it more clear what the minimum requirements for pure Ruby, C extensions, and C++ extensions separately.
* C extensions are now built by default - `TRUFFLERUBY_CEXT_ENABLED` is assumed `true` unless set to `false`.
* The `KEYS` interop message now returns an array of Java strings, rather than Ruby strings. `KEYS` on an array no longer returns indices.
* `HAS_SIZE` now only returns `true` for `Array`.
* A method call on a foreign object that looks like an operator (the method name does not begin with a letter) will call `IS_BOXED` on the object and based on that will possibly `UNBOX` and convert to Ruby.
* Now using the native version of Psych.
* The supported version of LLVM on Oracle Linux has been dropped to 3.8.
* The supported version of Fedora has been dropped to 25, and the supported version of LLVM to 3.8, due to LLVM incompatibilities. The instructions for installing `libssl` have changed to match.

# 0.33, April 2018

New features:

* The Ruby version has been updated to version 2.3.6.
* Context pre-initialization with TruffleRuby `--native`, which significantly improves startup time and loads the `did_you_mean` gem ahead of time.
* The default VM is changed to SubstrateVM, where the startup is significantly better. Use `--jvm` option for full JVM VM.
* The `Truffle::Interop` module has been replaced with a new `Polyglot` module which is designed to use more idiomatic Ruby syntax rather than explicit methods. A [new document](doc/user/polyglot.md) describes polyglot programming at a higher level.
* The `REMOVABLE`, `MODIFIABLE` and `INSERTABLE` Truffle interop key info flags have been implemented.
* `equal?` on foreign objects will check if the underlying objects are equal if both are Java interop objects.
* `delete` on foreign objects will send `REMOVE`, `size` will send `GET_SIZE`, and `keys` will send `KEYS`. `respond_to?(:size)` will send `HAS_SIZE`, `respond_to?(:keys)` will send `HAS_KEYS`.
* Added a new Java-interop API similar to the one in the Nashorn JavaScript implementation, as also implemented by Graal.js. The `Java.type` method returns a Java class object on which you can use normal interop methods. Needs the `--jvm` flag to be used.
* Supported and tested versions of LLVM for different platforms have been more precisely [documented](doc/user/installing-llvm.md).

Changes:

* Interop semantics of `INVOKE`, `READ`, `WRITE`, `KEYS` and `KEY_INFO` have changed significantly, so that `INVOKE` maps to Ruby method calls, `READ` calls `[]` or returns (bound) `Method` objects, and `WRITE` calls `[]=`.

Performance:

* `Dir.glob` is much faster and more memory efficient in cases that can reduce to direct filename lookups.
* `SecureRandom` now defers loading OpenSSL until it's needed, reducing time to load `SecureRandom`.
* `Array#dup` and `Array#shift` have been made constant-time operations by sharing the array storage and keeping a starting index.

Bug fixes:

* Interop key-info works with non-string-like names.

Internal changes:

* Changes to the lexer and translator to reduce regular expression calls.
* Some JRuby sources have been updated to 9.1.13.0.

# 0.32, March 2018

New features:

* A new embedded configuration is used when TruffleRuby is used from another language or application. This disables features like signals which may conflict with the embedding application, and threads which may conflict with other languages, and enables features such as the use of polyglot IO streams.

Performance:

* Conversion of ASCII-only Ruby strings to Java strings is now faster.
* Several operations on multi-byte character strings are now faster.
* Native I/O reads are about 22% faster.

Bug fixes:

* The launcher accepts `--native` and similar options in the `TRUFFLERUBYOPT` environment variable.

Internal changes:

* The launcher is now part of the TruffleRuby repository, rather than part of the GraalVM repository.
* `ArrayBuilderNode` now uses `ArrayStrategies` and `ArrayMirrors` to remove direct knowledge of array storage.
* `RStringPtr` and `RStringPtrEnd` now report as pointers for interop purposes, fixing several issues with `char *` usage in C extensions.
