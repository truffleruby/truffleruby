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
specifying a custom mark function to be used with particular objects.

Every `VALUE` in native code is a handle (a long). A handle for an
object is resolved through the handle block map to the object's
`ValueWrapper`. The `ValueWrapper` is itself the `WeakReference` to
its object: the object strongly references its wrapper (a plain field
on `RubyDynamicObject` and `ImmutableRubyObject`), while the wrapper
only weakly references the object, so the handle map never keeps
objects alive by itself. For `VALUE`s whose Ruby value is a boxed
primitive (a Float or a Bignum-range Integer), the box cannot
reference its wrapper back, so the wrapper references such a box
strongly instead (see `ValueWrapper#strongRef`).

What must keep things alive are the keep-alive lists described below.
An entry in such a list is `ValueWrapper#keepAliveObject()`: the
object itself when it strongly references its wrapper back (keeping
the wrapper and its handle block alive transitively), otherwise the
wrapper (keeping its handle block, and the primitive box through
`strongRef`).

### Keeping objects alive on the stack

We implement an `ExtensionCallStack` object to keep track of various
bits of useful information during a call to a C extension. Each stack
entry contains a growable `preservedObjects` array holding the
keep-alive object of every `VALUE` converted to a native handle during
the call (see `MarkingServiceNodes.KeepAliveNode`). When a new call is
made a new `ExtensionCallStackEntry` is added to the stack, and when
the call exits that entry is popped off again, dropping the
references.

### Keeping objects alive in structures

We don't have a way to run markers when doing garbage collection, but
we know we're keeping objects alive during the lifetime of a C call,
and we can record when the structure is accessed via `DATA_PTR` (which
should be required for the internal state of that structure to be
mutated). To do this each stack entry keeps a growable
`markOnExitObjects` array of the data objects whose mark functions
should run, and when we exit the C call we'll call those markers
(`MarkingServiceNodes.RunMarkOnExitNode`).

### Running mark functions

We run markers by recording the object being marked on the extension
stack, and then calling the marker which will in turn call
`rb_gc_mark` for the individual `VALUE`s which are held by the
structure. We'll record the keep-alive objects of those marked
`VALUE`s in a temporary array also held on the extension stack, and
then attach that array to the object wrapping the struct when the mark
function has finished (as the `MARKED_OBJECTS` hidden variable), so
the marked `VALUE`s stay usable until the next run of the mark
function.


## Managing the conversion of `VALUE`s to and from native handles

When converted to native, the `ValueWrapper` takes the following long values.

| Represented Value | Low Handle Bits | Comments |
|-------------------|-----------------|----------|
| false             | 0000            | |
| nil               | 0010            | |
| true              | 0110            | |
| undefined         | 1010            | |
| Integer           | xxx1            | Lowest bit set, small longs only, convert to long using >> 1 |
| Object            | xxx000          | See below |

These match MRI's special constants and tagging (with `USE_FLONUM=false`), see
`lib/cext/include/ruby/internal/special_consts.h` and `ValueWrapperManager`.

An object handle is of the form (most significant bits first): the 20
bits `0x0bade`, a 29-bit block index, a 12-bit offset within the block
and 3 zero bits (so handles are 8-byte aligned like pointers). This
range is above 2^48 and therefore not a valid memory address on 64-bit
machines: dereferencing a handle by mistake segfaults immediately
instead of reading unrelated memory.

The built in objects, `true`, `false`, `nil`, and `undefined` are
handled specially, and integers are relatively easy because there is a
well defined mapping from the native representation to the integer and
vice versa, but to manage objects we need to do a little more work.

When we convert an object `VALUE` to its native representation we need
to record the mapping from handle to `ValueWrapper` somewhere. The
mapping from `ValueWrapper` to handle must also be stable, so a symbol
or other immutable object that can outlive a context stores that
mapping in a process-wide map on the `RubyLanguage` class.

We achieve all this through a combination of handle block maps and
allocators. We deal with handles in blocks of 4096, and the current
`RubyFiber` holds onto a `HandleBlockHolder` which in turn holds the
current block for mutable objects (which cannot outlive the
`RubyContext`) and immutable objects (which can outlive the
context). Each fiber will take handles from those blocks until they
become exhausted. When a block is exhausted then `RubyLanguage` holds
a `HandleBlockAllocator` which is responsible for allocating new
blocks and recycling the handle ranges of dead ones.

A `HandleBlock` references its `ValueWrapper`s strongly, and each
wrapper references its `HandleBlock` strongly (the block must stay
usable as long as any of its wrappers is reachable). This does not
keep the wrapped objects alive because the wrapper only references its
object weakly. Once none of the objects of a block are alive anymore,
the block and its wrappers form an unreachable cycle which is
collected together, and a `Cleaner` then returns the block's handle
range to the `HandleBlockAllocator` for reuse. Blocks for immutable
objects are process-wide and kept alive forever (see
`RubyLanguage#keepSharedHandleBlockAlive`), since handles of immutable
objects like symbols must remain valid across contexts.
