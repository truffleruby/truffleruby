# Migration from JRuby to TruffleRuby

When trying TruffleRuby on your gems and applications, you are encouraged to [get in touch with the TruffleRuby team](../../README.md#contact) for help.

## Deployment

If you are migrating from JRuby, probably the easiest way to use TruffleRuby is to install the [TruffleRuby JVM Standalone](../../README.md#getting-started).

If you do not need the Java interoperability capabilities of TruffleRuby, then you can install the [TruffleRuby Native Standalone](../../README.md#getting-started).

## Using Ruby from Java

JRuby supports many different ways to embed Ruby in Java, including JSR 223 (also know as `javax.script`), the Bean Scripting Framework (BSF), JRuby Embed (also known as Red Bridge), and the JRuby direct embedding API.

The best way to embed TruffleRuby is to use the Polyglot API.
The API is different because it is designed to support many languages, not just Ruby.

TruffleRuby also supports JSR 223, compatible with JRuby, to make it easier to run legacy JRuby code.
See [this documentation](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md#compatibility-with-jsr-223-scriptengine) for how to use it.

You will need to use the JVM standalone or depend on the `org.graalvm.polyglot:polyglot` Maven package to use the Polyglot API.

See the [polyglot](polyglot.md) documentation for more information about how to use Ruby from other languages including Java; this document only shows the comparison to JRuby.

### Creating a Context

In JRuby with JSR 223 you would have written:

```java
ScriptEngineManager m = new ScriptEngineManager();
ScriptEngine scriptEngine = m.getEngineByName("ruby");
```

Or with BSF you would have written:

```java
BSFManager.registerScriptingEngine("jruby", "org.jruby.embed.bsf.JRubyEngine", null);
BSFManager bsfManager = new BSFManager();
```

Or with JRuby Embed you would have written:

```java
ScriptingContainer container = new ScriptingContainer();
```

Or with the direct embedding API you would have written:

```java
Ruby ruby = Ruby.newInstance(new RubyInstanceConfig());
```

In TruffleRuby you now write:

```java
Context polyglot = Context.newBuilder().allowAllAccess(true).build();
```

The `allowAllAccess(true)` method allows the permissive access privileges that Ruby needs for full functionality.
GraalVM by default disallows many privileges which may not be safe, such as native file access, but a normal Ruby installation uses these so we enable them.
You can decide not to grant those privileges, but this will restrict some of Ruby's functionality.

```java
// No privileges granted, restricts functionality
Context polyglot = Context.newBuilder().build();
```

You would normally create your context inside a `try` block to ensure it is properly disposed:

```java
try (Context polyglot = Context.newBuilder().allowAllAccess(true).build()) {
}
```

See the [Context API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) for detailed documentation about `Context`.

### Setting Options

You can set TruffleRuby [options](options.md) via system properties, or via the `.option(name, value)` builder method.

### Evaluating Code

In JRuby where you would have written one of these JRuby examples, the options available are given:

```java
scriptEngine.eval("puts 'hello'");
bsfManager.exec("jruby", "<script>", 1, 0, "puts 'hello'");
container.runScriptlet("puts 'hello'");
ruby.evalScriptlet("puts 'hello'");
```

In TruffleRuby you now write this:

```java
polyglot.eval("ruby", "puts 'hello'");
```

Note that `eval` supports multiple languages, so you need to specify the language each time.

### Evaluating Code with Parameters

In JRuby with JSR 223 you can pass parameters, called bindings, into a script:

```java
Bindings bindings = scriptEngine.createBindings();
bindings.put("a", 14);
bindings.put("b", 2);
scriptEngine.eval("puts a + b", bindings);
```

In TruffleRuby the `eval` method does not take parameters. Instead you should return a proc which does take parameters, and then call `execute` on this value:

```java
polyglot.eval("ruby", "-> a, b { puts a + b }").execute(14, 2);
```

### Primitive Values

The different embedding APIs handle primitive values in different ways.
In JSR 223, BSF, and JRuby Embed, the return type is `Object` and can be cast to a primitive like `long` and checked with `instanceof`.
In the direct embedding API the return is the root `IRubyObject` interface and you will need to convert a primitive to an `Integer`, and from there to a Java `long`:

```java
(long) scriptEngine.eval("14 + 2");
(long) bsfManager.eval("jruby", "<script>", 1, 0, "14 + 2");
(long) container.runScriptlet("14 + 2");
ruby.evalScriptlet("14 + 2").convertToInteger().getLongValue();
```

In TruffleRuby the return value is always an encapsulated `Value` object, which can be accessed as a `long` if that is possible for the object. `fitsInLong()` can test this:

```java
polyglot.eval("ruby", "14 + 2").asLong();
```

### Calling Methods

To call a method on an object you get from an `eval`, or any other object, in the JRuby embedding APIs you either need to ask the context to invoke the method, or in the case of direct embedding you need to call a method on the receiver and marshal the arguments into JRuby types yourself.
The BSF does not appear to have a way to call methods:

```java
((Invocable) scriptEngine).invokeMethod(scriptEngine.eval("Math"), "sin", 2);
container.callMethod(container.runScriptlet("Math"), "sin", 2);
ruby.evalScriptlet("Math").callMethod(ruby.getCurrentContext(), "sin", new IRubyObject[]{ruby.newFixnum(2)})
```

In TruffleRuby the `Value` class has a `getMember` method to return Ruby methods on an object, which you can then call by calling `execute`.
You do not need to marshal the arguments:

```java
polyglot.eval("ruby", "Math").getMember("sin").execute(2);
```

To call methods on a primitive, use a lambda:

```java
polyglot.eval("ruby", "-> x { x.succ }").execute(2).asInt();
```

### Passing Blocks

Blocks are a Ruby-specific language feature, so they don't appear in language agnostic APIs like JSR 223 and BSF.
The JRuby Embed API and direct embedding do allow passing a `Block` parameter to the `callMethod` method, but it is not clear how you would create a `Block` object to use this.

In TruffleRuby you should return a Ruby lambda that performs your call, passing a block that executes a Java lambda that you pass in:

```java
polyglot.eval("ruby", "-> block { (1..3).each { |n| block.call n } }")
  .execute(polyglot.asValue((IntConsumer) n -> System.out.println(n)));
```

### Creating Objects

JRuby embedding APIs don't have support for creating new objects, but you can just call the `new` method yourself:

```java
((Invocable) scriptEngine).invokeMethod(scriptEngine.eval("Time"), "new", 2021, 3, 18);
container.callMethod(container.runScriptlet("Time"), "new", 2021, 3, 18)
ruby.evalScriptlet("Time").callMethod(ruby.getCurrentContext(), "new",
  new IRubyObject[]{ruby.newFixnum(2021), ruby.newFixnum(3), ruby.newFixnum(8)})
```

In TruffleRuby you can create an object from a Ruby `class` using `newInstance`.
You can use `canInstantiate` to see if this will be possible:

```java
polyglot.eval("ruby", "Time").newInstance(2021, 3, 18);
```

### Handling Strings

In JRuby's embedding APIs you would use `toString` to convert to a Java `String`.
Use `asString` in TruffleRuby (and `isString` to check).

### Accessing Arrays

JRuby's arrays implement `List<Object>`, so you can cast to this interface to access them:

```java
((List) scriptEngine.eval("[3, 4, 5]")).get(1);
((List) container.runScriptlet("[3, 4, 5]")).get(1);
((List) bsfManager.eval("jruby", "<script>", 1, 0, "[3, 4, 5]")).get(1);
((List) ruby.evalScriptlet("[3, 4, 5]")).get(1);
```

In TruffleRuby you can use `getArrayElement`, `setArrayElement`, and
`getArraySize`, or you can use `as(List.class)` to get a `List<Object>`:

```java
polyglot.eval("ruby", "[3, 4, 5]").getArrayElement(1);
polyglot.eval("ruby", "[3, 4, 5]").as(List.class).get(1);
```

### Accessing Hashes

JRuby's hashes implement `Map<Object, Object>`, so you can cast to this
interface to access them:

```java
((Map) scriptEngine.eval("{'a' => 3, 'b' => 4, 'c' => 5}")).get("b");
((Map) scriptEngine.eval("{3 => 'a', 4 => 'b', 5 => 'c'}")).get(4);
```

In TruffleRuby there is currently no uniform way to access hashes or dictionary-like data structures.
At the moment we recommend using a lambda accessor:

```java
Value hash = polyglot.eval("ruby", "{'a' => 3, 'b' => 4, 'c' => 5}");
Value accessor = polyglot.eval("ruby", "-> hash, key { hash[key] }");
accessor.execute(hash, "b");
```

### Implementing Interfaces

You may want to implement a Java interface using a Ruby object (example copied from the JRuby wiki):

```java
interface FluidForce {
  double getFluidForce(double a, double b, double depth);
}
```

```ruby
class EthylAlcoholFluidForce
  def getFluidForce(x, y, depth)
    area = Math::PI * x * y
    49.4 * area * depth
  end
end

EthylAlcoholFluidForce.new
```

```
String RUBY_SOURCE = "class EthylAlcoholFluidForce\n  def getFluidForce...";
```

In JSR 223 you can use `getInterface(object, Interface.class)`.
In JRuby Embed you can use `getInstance(object, Interface.class)`.
In direct embedding you can use `toJava(Interface.class)`.
BSF does not appear to support implementing interfaces:

```java
FluidForce fluidForce = ((Invocable) scriptEngine).getInterface(scriptEngine.eval(RUBY_SOURCE), FluidForce.class);
FluidForce fluidForce = container.getInstance(container.runScriptlet(RUBY_SOURCE), FluidForce.class);
FluidForce fluidForce = ruby.evalScriptlet(RUBY_SOURCE).toJava(FluidForce.class);
fluidForce.getFluidForce(2.0, 3.0, 6.0);
```

In TruffleRuby you can get an interface implemented by your Ruby object by using `as(Interface.class)`:

```java
FluidForce fluidForce = polyglot.eval("ruby", RUBY_SOURCE).as(FluidForce.class);
fluidForce.getFluidForce(2.0, 3.0, 6.0);
```

JRuby allows the name of the Ruby method to be `get_fluid_force`, using Ruby conventions, instead of `getFluidForce`, using Java conventions.
TruffleRuby does not support this at the moment.

### Implementing Lambdas

As far as we know, JSR 223, BSF, JRuby Embed, and direct embedding do not have a convenient way to get a Java lambda from a Ruby lambda.

In TruffleRuby you can get a Java lambda (really an implementation of a functional interface) from a Ruby lambda by using
`as(FunctionalInterface.class)`:

```java
BiFunction<Integer, Integer, Integer> adder = polyglot.eval("ruby", "-> a, b { a + b }").as(BiFunction.class);
adder.apply(14, 2).intValue();
```

### Parse Once Run Many Times

Some of the JRuby embedding APIs allow a script to be compiled once and then eval'd several times:

```java
CompiledScript compiled = ((Compilable) scriptEngine).compile("puts 'hello'");
compiled.eval();
```

In TruffleRuby you can simply return a lambda from parsing and execute this many times.
It will be subject to optimization like any other Ruby code:

```java
Value parsedOnce = polyglot.eval("ruby", "-> { run many times }");
parsedOnce.execute();
```

## Using Java from Ruby

TruffleRuby provides its own scheme for Java interoperability that is consistent for use from any GraalVM language, to any other GraalVM language.
This is not compatible with existing JRuby-Java interoperability, so you will need to migrate.

Polyglot programming in general is [documented elsewhere](polyglot.md) - this section describes it relative to JRuby.

This example is from the JRuby wiki:

```ruby
require 'java'

# With the 'require' above, you now can refer to things that are part of the
# standard Java platform via their full paths.
frame = javax.swing.JFrame.new("Window") # Creating a Java JFrame
label = javax.swing.JLabel.new("Hello World")

# You can transparently call Java methods on Java objects, just as if they were defined in Ruby.
frame.add(label)  # Invoking the Java method 'add'.
frame.setDefaultCloseOperation(javax.swing.WindowConstants::EXIT_ON_CLOSE)
frame.pack
frame.setVisible(true)
sleep
```

In TruffleRuby we would write that this way instead:

```ruby
Java.import 'javax.swing.JFrame'
Java.import 'javax.swing.JLabel'
Java.import 'javax.swing.WindowConstants'

frame = JFrame.new("Window")
label = JLabel.new("Hello World")

frame.add(label)
frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
frame.pack
frame.setVisible(true)
sleep
```

Instead of using Ruby metaprogramming to simulate a Java package name, we explicitly import classes.
`Java.import` is similar to JRuby's `java_import`, and does `ClassName = Java.type('package.ClassName')`.

Constants are read by reading properties of the class rather than using Ruby notation.

### Require Java

Do not `require 'java'` in TruffleRuby. However, you do need to run in `--jvm` mode.
This is not available in the Native Standalone.

### Referring to Classes

In JRuby, Java classes can either be referenced in the `Java` module, such as `Java::ComFoo::Bar`, or if they have a common TLD they can be referenced as `com.foo.Bar`. `java_import com.foo.Bar` will define `Bar` as a top-level constant.

In TruffleRuby, Java classes are referred to using either `Java.type('com.foo.Bar')`, which you would then normally assign to a constant, or you can use `Java.import 'com.foo.Bar'` to have `Bar` defined in the enclosing module.

### Wildcard Package Imports

JRuby lets you `include_package 'com.foo'` which will make all classes in that package available as constants in the current scope.

In TruffleRuby you refer to classes explicitly.

### Calling Methods and Creating Instances

In both JRuby and TruffleRuby you call Java methods as you would a Ruby method.

JRuby will rewrite method names such as `my_method` to the Java convention of `myMethod`, and convert `getFoo` to `foo`, and `setFoo` to `foo=`.
TruffleRuby does not perform these conversions.

### Calling Overloaded Method

When multiple overloads are possible, it needs to be selected explicitly.
For instance `java.util.concurrent.ExecutorService` has both `submit(Runnable)` and `submit(Callable<T> task)`.
Calling submit without specifying which one shows the possible overloads:
```
$ ruby -e 'Java.type("java.util.concurrent.Executors").newFixedThreadPool(1).submit {}'
-e:1:in '<main>': Multiple applicable overloads found for method name submit (candidates: [
  Method[public java.util.concurrent.Future java.util.concurrent.AbstractExecutorService.submit(java.util.concurrent.Callable)],
  Method[public java.util.concurrent.Future java.util.concurrent.AbstractExecutorService.submit(java.lang.Runnable)]],
  arguments: [RubyProc@4893b344 (RubyProc)]) (TypeError)
```

You can select a specific overload using:
```ruby
executor = Java.type("java.util.concurrent.Executors").newFixedThreadPool(1)
executor['submit(java.lang.Runnable)'].call(-> { 1 })
# or
executor.send("submit(java.lang.Runnable)") { 1 }
```

### Referring to Constants

In JRuby, Java constants are modelled as Ruby constants, `MyClass::FOO`.
In TruffleRuby you use the read notation to read them as a property, `MyClass.FOO` or `MyClass[:FOO]`.

### Using Classes from JAR files

In JRuby you can add classes and JARs to the classpath using `require`.
In TruffleRuby at the moment you use the `-classpath` JVM flag as normal.

### Additional Java-Specific Methods

JRuby defines these methods on Java objects; use these equivalents instead.

`java_class` - use `class`.

`java_kind_of?` - use `is_a?`

`java_object` - not supported.

`java_send` - use `__send__`.

`java_method` - not supported.

`java_alias` - not supported.

### Creating Java Arrays

In JRuby you use `Java::byte[1024].new`.

In TruffleRuby you would use `Java.type('byte[]').new(1024)`.

### Implementing Java Interfaces

JRuby has several ways to implement an interface.
For example, to add an action listener to a Swing button we could do any of the following three things:

```ruby
class ClickAction
  include java.awt.event.ActionListener

  def actionPerformed(event)
   javax.swing.JOptionPane.showMessageDialog nil, 'hello'
  end
end

button.addActionListener ClickAction.new
```

```ruby
button.addActionListener do |event|
  javax.swing.JOptionPane.showMessageDialog nil, 'hello'
end
```

```ruby
button.addActionListener -> event {
  javax.swing.JOptionPane.showMessageDialog nil, 'hello'
}
```

In TruffleRuby we'd always use the last option to generate an interface:

```ruby
button.addActionListener -> event {
  JOptionPane.showMessageDialog nil, 'hello'
}
```

### Generating Java Classes at Runtime

JRuby supports converting a Ruby class to a concrete Java class using `become_java!`.

TruffleRuby does not support this.
We recommend using a proper Java interface as your interface between Java and Ruby.

### Reopening Java Classes

Java classes cannot be reopened in TruffleRuby.

### Subclassing Java Classes

Java classes cannot be subclassed in TruffleRuby. Use composition or interfaces instead.

## Extending TruffleRuby Using Java

JRuby supports extensions written in Java. These extensions are written against an informal interface that is simply the entire internals of JRuby, similar to how the MRI C extension interface works.

TruffleRuby does not support writing these kind of Java extensions at the moment.
We recommend using Java interop as described above.

## Tooling

### Standalone Classes and JARs

JRuby supports compiling to standalone source classes and compiled JARs from Ruby using `jrubyc`.

TruffleRuby does not support compiling Ruby code to Java. We recommend using the Polyglot API as your entry point from Java to Ruby.

### Warbler

JRuby supports building WAR files for loading into enterprise Java web servers.

TruffleRuby does not support this at the moment.

### VisualVM

VisualVM works for TruffleRuby as for JRuby.

Additionally, VisualVM understands Ruby objects, rather than Java objects, when you use the heap dump tool.
