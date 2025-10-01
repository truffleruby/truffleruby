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

## Polymorphic inline caches

When `@Cached` is used to create a Polymorphic Inline Cache, add a `limit`
property to set the maximum size of the cache, and add a corresponding entry to
`Options`. For examples see
https://github.com/truffleruby/truffleruby/commit/cbacdd5a2be32d74ed152a1c306beaa927e80e4e.
