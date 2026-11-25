# `VALUE`s in C extensions

## Semantics on MRI

Before we discuss the mechanisms used to represent MRI's `VALUE`
semantics we should outline what those are. A `VALUE`in a local
variable (i.e. on the stack) will keep the associated object alive as
long as that stack entry lasts (so either until the function exits, or
until that variable is no longer live). We can also wrap C structures
in Ruby objects, and when we do this we're able to specify a marking
function. This marking function is used by MRI's garbage collector to
find all the objects reachable from the structure, and allows it to
mark them in the same way it would with normal instance
variables. There are also a couple of utility methods and macros for
keeping a value alive for the duration of a function call even if it
is no longer being held in a variable, and for globally preserving a
value held in a static variable.

Because `VALUE`s are essentially tagged pointers on MRI there are also
some semantics that may be obvious but are worth stating anyway:

* Any two `VALUE`s associated with the same object will be
  identical. In other words as long as an object is alive its `VALUE`
  will remain constant.
* A `VALUE` for a live object can reuse the same tagged pointer that
  was previously used for a now dead object.

## Emulating the semantics in TruffleRuby

Emulating these semantics on TruffleRuby is non-trivial. Although we
are running under a garbage collector it doesn't know that a `VALUE`
maps to an object, and neither does it have any mechanism for
specifying a custom mark function to be used with particular
objects. As long as `VALUE`s can remain as `ValueWrapper` objects then
we don't need to do much. Ruby objects maintain a strong reference to
their associated `ValueWrapper`, and vice versa, so we only really
need to consider situations where `VALUE`s are converted into native
handles.

### Keeping objects alive on the stack

We implement an `ExtensionCallStack` object to keep track of various
bits of useful information during a call to a C extension. Each stack
entry contains a `preservedObject`, and an additional potential
`preservedObjects` list which together will contain all the
`ValueWrapper`s converted to native handles during the process of a
call. When a new call is made a new `ExtensionCallStackEntry` is added
to the stack, and when the call exits that entry is popped off again.

### Keeping objects alive in structures

We don't have a way to run markers when doing garbage collection, but
we know we're keeping objects alive during the lifetime of a C call,
and we can record when the structure is accessed via DATA_PTR (which
should be required for the internal state of that structure to be
mutated). To do this we keep a list of objects to be marked in a
similar manner to the objects that should be kept alive, and when we
exit the C call we'll call those markers.

### Running mark functions

We run the marker of each object recorded on the extension stack when the C call exits. `rb_gc_mark` does not call into Ruby for every `VALUE` held by the structure: a mark function such as the one of a `Nokogiri::XML::NodeSet` marks every node it contains, and it runs every time a C call accessed the data of that object. Instead, `rb_tr_run_marker` (in `gc.c`) sets up a buffer for the current native thread before calling the marker, `rb_gc_mark` and its variants (`rb_gc_mark_movable`, `rb_gc_mark_maybe` and `rb_gc_mark_locations`) append the `VALUE` to that buffer, and when the marker returns the whole buffer is passed to Ruby with a single `rb_tr_gc_mark_values` upcall. That upcall resolves the handles to their `ValueWrapper`s and stores them in a temporary array held on the extension stack, which is attached to the object wrapping the struct when the mark function has finished.

The buffer starts on the C stack and moves to the heap if the marker marks more `VALUE`s than fit in it. The previous buffer is saved and restored around each marker, so a marker which causes another marker to run (for example by calling a Ruby method implemented in C which accesses the data of another object) keeps its own marks.

Some consequences of collecting the marks in C:

* Calling `rb_gc_mark` when no marker is running on the current thread does nothing, since there is no buffer to append to. The first such call is logged when running with `--cexts-log-warnings`.
* If a marker raises an exception, the `VALUE`s it marked before raising are never passed to Ruby, and the object keeps the objects marked by the previous run of its marker.
* A handle is only resolved to its `ValueWrapper` once the marker has returned, and not when `rb_gc_mark` is called.


## Managing the conversion of `VALUE`s to and from native handles

When converted to native, the `ValueWrapper` takes the following long values.

| Represented Value | Handle Bits                         | Comments |
|-------------------|-------------------------------------|----------|
| false             | 00000000 00000000 00000000 00000000 | |
| true              | 00000000 00000000 00000000 00000010 | |
| nil               | 00000000 00000000 00000000 00000100 | |
| undefined         | 00000000 00000000 00000000 00000110 | |
| Integer           | xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxx1 | Lowest mask bit set, small longs only, convert to long using >> 1 |
| Object            | xxxxxxxx xxxxxxxx xxxxxxxx xxxxx000 | No mask bits set and does not equal 0, value is index into handle map |

The built in objects, `true`, `false`, `nil`, and `undefined` are
handled specially, and integers are relatively easy because there is a
well defined mapping from the native representation to the integer and
vice versa, but to manage objects we need to do a little more work.

When we convert an object `VALUE` to its native representation we need
to keep the corresponding `ValueWrapper` object alive, and we need to
record that mapping from handle to `ValueWrapper` somewhere. The
mapping from `ValueWrapper` to handle must also be stable, so a symbol
or other immutable object that can outlive a context will need to
store that mapping somewhere on the `RubyLanguage` object.

We achieve all this through a combination of handle block maps and
allocators. We deal with handles in blocks of 4096, and the current
`RubyFiber` holds onto a `HandleBlockHolder` which in turn holds the
current block for mutable objects (which cannot outlive the
`RubyContext`) and immutable objects (which can outlive the
context). Each fiber will take values from those blocks until they
becomes exhausted. When that block is exhausted then `RubyLanguage`
holds a `HandleBlockAllocator` which is responsible for allocating new
blocks and recycling old ones. These blocks of handles however only
hold weak references, because we don't want a conversion to native to
keep the `ValueWrapper` alive longer that it should.

Conversely the `HandleBlock` _must_ live for as long as there are any
reachable `ValueWrapper`s in that block, so a `ValueWrapper` keeps a
strong reference to the `HandleBlock` it is in.
