# Architecture

This guide describes the outline of the architecture of RBS library. It helps you to understand the structure and key features of the library to start contributing to the library.

## Bird's Eye View

The goal of the library is simple: Read RBS files and generate the structure of Ruby programs.

```
RBS files
  ↓         -- RBS::Parser
Syntax tree
  ↓
Environment
  ↓        -- Definition builder
Definition
```

The input is RBS files. The gem ships with RBS type definitions of Ruby core library and some of the standard libraries. You write RBS files for your applications or gems.

Syntax tree is the next representation. `RBS::Parser` transforms the sequence of characters in RBS files into syntax trees.

Syntax tree objects are loaded to `RBS::Environment`. It collects loaded RBS objects, organizes the definitions, and provides some utilities, like resolving type names and finding the declarations.

`RBS::Definition` is the goal of the transformation steps. It is associated with a class singleton, a class object, or an interface. You can find the list of available methods and their types, instance variables, and class hierarchies.

## Core classes

### Types

Types are defined under `RBS::Types`, like `RBS::Types::ClassInstance` or `RBS::Types::Union`. You will find the definition of each type supported in RBS.

### Parsing RBS files

The RBS source code is loaded into `RBS::Buffer`, and `RBS::Parser` is the parser. The parser is implemented in C extension.

`RBS::Parser` provides three entrypoints.

- `RBS::Parser.parse_method_type` parsers a *method type*. (`[T] (String) { (IO) -> T } -> Array[T]`)
- `RBS::Parser.parse_type` parses a *type*. (`Hash[Symbol, untyped]`)
- `RBS::Parser.parse_signature` parses the whole RBS file.

### Environment

RBS AST is loaded to `RBS::Environment` by `RBS::EnvironmentLoader`. `Environment` gives *absolute names* to the declarations, and provides an index from the *absolute name* to their declarations.

Assume we have the following nested RBS declarations:

```rbs
module Hello
  class World
  end
end

class Hello::World
end
```

And the environment organizes the definitions as follows:

- There are two classes `::Hello` and `::Hello::World`
- It provides a mapping from `::Hello` to it's `module` declaration and `::Hello::World` to it's two `class` declarations

### Definition and DefinitionBuilder

`RBS::Definition` tells you:

- The set of available methods in a class/module/interface
- The set of instance variables in a class/module
- The ancestors in a class/module

Definition is constructed for:

- A singleton class of a class/module -- `singleton(String)`, `singleton(Array)`,
- An instance of a class -- `String`, `Array[T]`, or
- An interface -- `_ToS`

Note that generic class instances/interfaces are kept generic. We don't have a definition of `Array[String]` but of `Array[T]`.

`DefinitionBuilder` constructs `Definition` of given type names.

- `DefinitionBuilder#build_singleton` returns a definition of singleton classes of given class/module.
- `DefinitionBuilder#build_instance` returns a definition of instances of given class/module.
- `DefinitionBuilder#build_interface` returns a definition of interfaces.

It uses `AncestorBuilder` to construct ancestor chains of the type. `MethodBuilder` constructs sets of available methods based on the ancestor chains.

The `#build_singleton` calculates the type of `.new` methods based on the definition of `#initialize` method. This is different from Ruby's implementation -- it reused `Class#new` method but we need the custom implementation to give precise `.new` method type of each class.

#### Working with type aliases

`DefinitionBuilder#expand_alias` and its variants provide one step *unfold* operation of type aliases.

```ruby
builder.expand_alias2(RBS::TypeName.parse("::int"), []) # => returns `::Integer | ::_ToInt`
```

We don't have *normalize* operation for type aliases, because RBS allows recursive type alias definition, which cannot be *fully* unfolded.

### Other utilities

`RBS::Validator` provides validation of RBS type declaration. It validates that all of the type name references can be resolved, all type applications have correct arity, and so on.

`RBS::Test` provides runtime type checking, which confirms if a Ruby object can have an RBS type. It also provides an integration to existing Ruby code so that we run Ruby code, assuming unit tests, with runtime type checking.

`RBS::UnitTest` provides utilities to help write unit tests for RBS type definitions. Use the tool to make sure your RBS type definition is consistent with implementation.

`RBS::Prototype` is the core of `rbs prototype` feature. It scans Ruby source code or uses reflection features, and it generates the prototype of RBS files.

`RBS::Collection` includes `rbs collection` features.
