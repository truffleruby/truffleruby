# power_assert
## About
Power Assert shows each value of variables and method calls in the expression.
It is useful for testing, providing which value wasn't correct when the condition is not satisfied.

    Failure:
       assert { 3.times.to_a.include?(3) }
                  |     |    |
                  |     |    false
                  |     [0, 1, 2]
                  #<Enumerator: 3:times>

## Related Projects
In general, you don't need to use this library directly.
Use following test frameworks or extensions instead.

* [test-unit](https://github.com/test-unit/test-unit)(>= 3.0.0)
  * [Document](http://test-unit.github.io/test-unit/en/Test/Unit/Assertions.html#assert-instance_method)
* [minitest-power_assert](https://github.com/hsbt/minitest-power_assert)
* [rspec-power_assert](https://github.com/joker1007/rspec-power_assert)
* [rspec-matchers-power_assert_matchers](https://github.com/kachick/rspec-matchers-power_assert_matchers)
* [pry-power_assert](https://github.com/yui-knk/pry-power_assert)
* [irb-power_assert](https://github.com/kachick/irb-power_assert)
* [power_p](https://github.com/k-tsj/power_p)

## Requirement
* CRuby 3.1+

## Configuration
To colorize output messages, add <code>require "power_assert/colorize"</code> to your code.
(It requires irb 1.3.1+)

## Known Limitations
* Expressions must be on a single line. Splitting an assertion across multiple lines prevents any report from being generated, e.g.:

```ruby
assert do
  # Reported
  func(foo: 0123456789, bar: "abcdefg")
end

assert do
  # Not reported
  func(foo: 0123456789,
       bar: "abcdefg")
end
```

* Expressions must include at least one method call. Assertions without method calls generate no report, e.g.:

```ruby
val = false
assert do
  # Reported
  val == true
end

assert do
  # Not reported
  val
end
```

* Return values from `method_missing` or `super` generate no report, e.g.:

```ruby
class Foo
  def method_missing(*)
    :foo
  end
end
foo = Foo.new

assert do
  # Not reported
  foo.foo
end
```

* Avoid conditional branches inside assertions. Conditional logic may prevent a report from being generated, e.g.:

```ruby
condition = true
expected = false
actual = true
assert do
  # This fails, but nothing is reported
  condition ? expected == actual : expected == actual
end
```

* (CRuby 4.0+) `<Struct subclass>.new` generates no report. Use `<Struct subclass>.[]` instead, e.g.:

```ruby
s = Struct.new(:a)
assert do
  # Not reported
  s.new(0)
end

assert do
  # Reported
  s[0]
end
```

## Reference
* [Power Assert in Ruby (at RubyKaigi 2014) // Speaker Deck](https://speakerdeck.com/k_tsj/power-assert-in-ruby)
