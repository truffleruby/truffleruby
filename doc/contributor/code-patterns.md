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

## Profiles for rarely-taken branches in partially evaluated code

Everything reachable from a node's `execute*` method is partially evaluated and
compiled, including branches that are almost never taken at runtime such as
slow paths, error paths and checks for debugging/tracing features. Without a
profile, such a branch is compiled into the fast path and makes it slower and
bigger. A `BranchProfile`/`ConditionProfile` records in the interpreter whether
the branch was ever taken, so the compiler can leave it out (and deoptimize if
it is taken later).

* In a node using the DSL (`@Specialization`), use an inlined profile:

```java
@Specialization
Object foo(Object object,
        @Cached InlinedBranchProfile slowPathProfile) {
    if (isSlowPath(object)) {
        slowPathProfile.enter(this);
        ...
    }
    ...
}
```

* In a plain node (no DSL), use a profile field:

```java
private final BranchProfile slowPathProfile = BranchProfile.create();

@Override
public Object execute(VirtualFrame frame) {
    if (isSlowPath()) {
        slowPathProfile.enter();
        ...
    }
    ...
}
```

* Use `InlinedConditionProfile`/`ConditionProfile` instead when both sides of the
  condition can be hot.

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
