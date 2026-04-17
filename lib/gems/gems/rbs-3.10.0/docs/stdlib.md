# Testing Core API and Standard Library Types

This is a guide for testing core/stdlib types.

## Add Tests

We support writing tests for core/stdlib signatures.

### Writing tests

First, execute `generate:stdlib_test` rake task with a class name that you want to test.

```console
$ bundle exec rake 'generate:stdlib_test[String]'
Created: test/stdlib/String_test.rb
```

It generates `test/stdlib/[class_name]_test.rb`.
The test scripts would look like the following:

```rb
class StringSingletonTest < Test::Unit::TestCase
  include TypeAssertions

  testing "singleton(::String)"

  def test_initialize
    assert_send_type "() -> String",
                     String, :new
    assert_send_type "(String) -> String",
                     String, :new, ""
    assert_send_type "(String, encoding: Encoding) -> String",
                     String, :new, "", encoding: Encoding::ASCII_8BIT
    assert_send_type "(String, encoding: Encoding, capacity: Integer) -> String",
                     String, :new, "", encoding: Encoding::ASCII_8BIT, capacity: 123
    assert_send_type "(encoding: Encoding, capacity: Integer) -> String",
                     String, :new, encoding: Encoding::ASCII_8BIT, capacity: 123
    assert_send_type "(ToStr) -> String",
                     String, :new, ToStr.new("")
    assert_send_type "(encoding: ToStr) -> String",
                     String, :new, encoding: ToStr.new('Shift_JIS')
    assert_send_type "(capacity: ToInt) -> String",
                     String, :new, capacity: ToInt.new(123)
  end
end

class StringTest < Test::Unit::TestCase
  include TypeAssertions

  testing "::String"

  def test_gsub
    assert_send_type "(Regexp, String) -> String",
                     "string", :gsub, /./, ""
    assert_send_type "(String, String) -> String",
                     "string", :gsub, "a", "b"
    assert_send_type "(Regexp) { (String) -> String } -> String",
                     "string", :gsub, /./ do |x| "" end
    assert_send_type "(Regexp) { (String) -> ToS } -> String",
                     "string", :gsub, /./ do |x| ToS.new("") end
    assert_send_type "(Regexp, Hash[String, String]) -> String",
                     "string", :gsub, /./, {"foo" => "bar"}
    assert_send_type "(Regexp) -> Enumerator[String, self]",
                     "string", :gsub, /./
    assert_send_type "(String) -> Enumerator[String, self]",
                     "string", :gsub, ""
    assert_send_type "(ToStr, ToStr) -> String",
                     "string", :gsub, ToStr.new("a"), ToStr.new("b")
  end
end
```

You need include `TypeAssertions` which provide useful methods for you.
`testing` method call tells which class is the subject of the class.
You may need `library` call to test a library if the type definition is provided as a library (under `stdlib` dir).

Note that the instrumentation is based on refinements and you need to write all method calls in the unit class definitions.
If the execution of the program escape from the class definition, the instrumentation is disabled and no check will be done.

#### 📣 Method type assertions

`assert_send_type` method call asserts to be valid types and confirms to be able to execute without exceptions.
And you write the sample programs which calls all of the patterns of overloads.

We recommend write method types as _simple_ as possible inside the assertion.
It's not very easy to define _simple_, but we try to explain it with a few examples.

* Instead of `(String | Integer) -> Symbol?`, use `(String) -> Symbol` or `(Integer) -> nil`, because we know the exact argument type we are passing in the test
* Instead of `self`, `instance`, or `class`, use concrete types like `String`, `singleton(IO)`, because we know the exact type of the receiver
* Sometimes, you need union types if the method is nondeterministic -- `() -> (Integer | String)` for `[1, ""].sample` (But you can rewrite the test code as `[1].sample` instead)
* Sometimes, you need union types for heterogeneous collections -- `() { (Integer | String) -> String } -> Array[String | Integer]` for `[1, "2"].each {|i| i.to_s }` (But you can rewrite the test code as `[1, 2].each {|i| i.to_s }`)
* Using `void` is allowed if the RBS definition is `void`

Generally _simple_ means:

* The type doesn't contain `self`, `instance`, `class`, `top`, `bot`, and `untyped`
* The type doesn't contain unions and optionals

Use them if you cannot write the test without them.

One clear exception to using _simple_ types is when you use `with_int` or family helpers, that yield values with each case of the given union:

```ruby
def test_something
  with_int(3) do |int|
    # Yields twice with `Integer` and `ToInt`
    assert_send_type(
      "(int) -> Integer",
      some, :test, int
    )
  end
end
```

It's clear having type aliases makes sense.

#### 📣 Constant type assertions

We also have `assert_const_type` method, to test the type of constant is correct with respect to RBS type definition.

```ruby
class FloatConstantTest < Test::Unit::TestCase
  include TypeAssertions

  def test_infinity
    assert_const_type "Float", "Float::INFINITY"
  end
end
```

It confirms:

1. The type of constant `Float::INFINITY` is `Float`
2. The type of constant `Float::INFINITY` is correct with respect to RBS definition

We don't have any strong recommendation about where the constants test should be written in.
The `FloatConstantTest` example defines a test case only for the constant tests.
You may write the tests inside `FloatInstanceTest` or `FloatSingletonTest`.

### Running tests

You can run the test with:

```console
$ bundle exec rake stdlib_test                # Run all tests
$ bundle exec ruby test/stdlib/String_test.rb # Run specific tests
```
