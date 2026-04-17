# Aliases

This document explains module/class aliases and type aliases.

## Module/class alias

Module/class aliases give another name to a module/class.
This is useful for some syntaxes that has lexical constraints.

```rbs
class C
end

class D = C         # ::D is an alias for ::C

class E < D         # ::E inherits from ::D, which is actually ::C
end
```

Note that module/class aliases cannot be recursive.

So, we can define a *normalization* of aliased module/class names.
Normalization follows the chain of alias definitions and resolves them to the original module/class defined with `module`/`class` syntax.

```rbs
class C
end

class D = C
class E = D
```

`::E` is defined as an alias, and it can be normalized to `::C`.

## Type alias

The biggest difference from module/class alias is that type alias can be recursive.

```rbs
# cons_cell type is defined recursively
type cons_cell = nil
               | [Integer, cons_cell]
```

This means type aliases *cannot be* normalized generally.
So, we provide another operation for type alias, `DefinitionBuilder#expand_alias` and its family.
It substitutes with the immediate right hand side of a type alias.

```
cons_cell ===> nil | [Integer, cons_cell]                   (expand 1 step)
          ===> nil | [Integer, nil | [Integer, cons_cell]]  (expand 2 steps)
          ===> ...                                          (expand will go infinitely)
```

Note that the namespace of a type alias *can be* normalized, because they are module names.

```rbs
module M
  type t = String
end

module N = M
```

With the type definition above, a type `::N::t` can be normalized to `::M::t`.
And then it can be expanded to `::String`.

> [!NOTE]
> This is something like an *unfold* operation in type theory.

## Type name resolution

Type name resolution in RBS usually rewrites *relative* type names to *absolute* type names.
`Environment#resolve_type_names` converts all type names in the RBS type definitions, and returns a new `Environment` object.

It also *normalizes* modules names in type names.

- If the type name can be resolved and normalized successfully, the AST has *absolute* type names.
- If the type name resolution/normalization fails, the AST has *relative* type names.
