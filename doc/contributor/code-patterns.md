# Code Patterns

## Java code formatting

The java source code is formatted with Eclipse Code Formatter. Our CI checks
that the code is properly formatted. See [using IntelliJ](using-intellij.md) and
[using Eclipse](using-eclipse.md) documents about how to configure your IDE to
format the files automatically.

## Where to allocate helper nodes (a node used in another node)

* If the parent node does not use the DSL (use `@Specialization`), allocate the
  helper node [lazily](#the-lazy-pattern).

* If the helper node is used by only one specialization: use `@Cached`.

```java
@Specialization
public Object lookupMethod(Object object,
        @Cached MetaClassNode metaClassNode) {
    final RubyClass rubyClass = metaClassNode.executeMetaClass(object);
    ...
}
```
If the helper node does not have a static `create()` method, add it.

* If the helper node is needed by every specialization, allocate the helper node
  eagerly as a `@Child`.

```java
public abstract class MyNode extends ContextSourceRubyNode {
    @Child MetaClassNode metaClassNode = MetaClassNode.create();
...
```

### The lazy pattern

* Otherwise use the lazy pattern which __*includes*__ the `execute*` call on the
  helper node.

```java
@Child ToStrNode toStrNode;
...

protected RubyString toStr(VirtualFrame frame, Object object) {
    if (toStrNode == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        toStrNode = insert(ToStrNodeGen.create(getContext(), getSourceSection(), null));
    }

    return toStrNode.executeToStr(frame, object);
}
```

However, if the node already uses `@Cached` *and there are guards on the
`@Cached` values*, consider whether it is useful to have one helper node per
`Specialization` instantiation or only one for the whole node.

## Profiles for branches in partially evaluated code

Everything reachable from a node's `execute*` method or `@Specialization`
without crossing a `@TruffleBoundary` is partially evaluated and compiled.
Every branch and condition in that code (`if`, `?:`, `&&`/`||`, `switch`, loop
exits) must be guarded by a profile. Without one, the compiler knows nothing
about which side is taken at runtime: it compiles both sides into the fast path
and cannot specialize the code on the observed behavior, making it slower and
bigger. A profile records in the interpreter what was observed (which sides were
taken, how often a loop iterates), so the compiler can leave out an untaken side
(and deoptimize if it is taken later) and optimize the taken one.

The only conditions which need no profile are those on compilation constants,
such as `@CompilationFinal` fields, option values and DSL guards, since the
compiler folds them away.

This applies to helper methods of plain (non-`Node`) classes too, such as
`ValueWrapper`, `RubyString` or a storage class: a method there is partially
evaluated whenever it is called from a node and is not a `@TruffleBoundary`, so
its branches need profiles just like code written in the node.

* Use `InlinedConditionProfile`/`ConditionProfile` by default, it covers both
  sides of a condition. Use `InlinedBranchProfile`/`BranchProfile` for a branch
  which is only a slow path (an error path, a check for a debugging/tracing
  feature), and `InlinedLoopConditionProfile`/`LoopConditionProfile` for loops.

* In a node using the DSL (`@Specialization`), use an inlined profile:

```java
@Specialization
Object foo(Object object,
        @Cached InlinedConditionProfile emptyProfile,
        @Cached InlinedBranchProfile slowPathProfile) {
    if (emptyProfile.profile(this, isEmpty(object))) {
        ...
    } else {
        ...
    }
    if (isSlowPath(object)) {
        slowPathProfile.enter(this);
        ...
    }
    ...
}
```

* In a plain node (no DSL), use a profile field:

```java
private final ConditionProfile emptyProfile = ConditionProfile.create();
private final BranchProfile slowPathProfile = BranchProfile.create();

@Override
public Object execute(VirtualFrame frame) {
    if (emptyProfile.profile(isEmpty())) {
        ...
    }
    if (isSlowPath()) {
        slowPathProfile.enter();
        ...
    }
    ...
}
```

* A helper method of a plain class has no node to cache a profile in, so it
  takes the profile (and the `Node node` an inlined profile needs) as
  parameters, and each caller passes its own profile. A caller behind a
  `@TruffleBoundary` (or any other uncached caller) passes
  `InlinedConditionProfile.getUncached()` and `null` for the node:

```java
// in a plain class
public Object keepAliveObject(Node node, InlinedConditionProfile strongRefProfile) {
    return strongRefProfile.profile(node, strongRef != null) ? this : get();
}

// in a node
@Specialization
static void keepObject(Node node, ValueWrapper wrapper,
        @Cached InlinedConditionProfile strongRefProfile) {
    keepAlive(wrapper.keepAliveObject(node, strongRefProfile));
}

// behind a @TruffleBoundary
wrapper.keepAliveObject(null, InlinedConditionProfile.getUncached());
```

* Branches raising a Ruby exception (`RaiseException`) still need a profile,
  since raising is a normal part of program execution. Only for branches which
  must never be taken in compiled code, e.g. a fatal error or a one-time
  rewrite of the node, use `CompilerDirectives.transferToInterpreterAndInvalidate()`
  instead of a profile.

## Polymorphic inline caches

When `@Cached` is used to create a Polymorphic Inline Cache, add a `limit`
property to set the maximum size of the cache, and add a corresponding entry to
`Options`. For examples see
https://github.com/truffleruby/truffleruby/commit/cbacdd5a2be32d74ed152a1c306beaa927e80e4e.
