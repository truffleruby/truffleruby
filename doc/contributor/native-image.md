# Using TruffleRuby with GraalVM Native Images

The GraalVM Native Image Generator is a closed-world analysis ahead-of-time
compiler for Java, and an implementation of parts of a JVM.

We use Native Images to ahead-of-time compile TruffleRuby and the
GraalVM Compiler to a single, statically-linked native binary
executable, that has no dependency on a JVM, and does not link to any JVM
libraries. The technique is more sophisticated than just appending a JAR as a
resource in a copy of the JVM - only parts of the JVM which are needed are
included and they are specialised for how TruffleRuby uses them. There is no
Java bytecode - only compiled native machine code and compiler graphs for
dynamic compilation.

Note that the GraalVM Native Image Generator is not an ahead-of time compiler
for your Ruby program. It only ahead-of-time compiles the Java code that
implements the TruffleRuby interpreter and the GraalVM Compiler.

The GraalVM Native Image Generator itself, like the GraalVM Compiler and
TruffleRuby, is implemented in Java.

https://youtu.be/FJY96_6Y3a4?t=10023

More information can be found in Kevin Menard's
[blog post](http://nirvdrum.com/2017/02/15/truffleruby-on-the-substrate-vm.html).

The TruffleRuby that is distributed in the TruffleRuby [Native Standalone](../../README.md#getting-started)
by default uses a version compiled using [Native Image](https://www.graalvm.org/reference-manual/native-image/) - this is to prioritise fast start-up and
warm-up time for shorter running commands and benchmarks.

```bash
$ cd graalvm
$ otool -L bin/ruby
bin/ruby:
  /System/Library/Frameworks/CoreFoundation.framework/Versions/A/CoreFoundation (compatibility version 150.0.0, current version 1348.28.0)
  /usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1238.0.0)
  /usr/lib/libz.1.dylib (compatibility version 1.0.0, current version 1.2.8)
```

```bash
$ du -h bin/ruby
200M bin/ruby
```

The Native Image version of TruffleRuby has better startup performance and lower memory
footprint than JRuby, Rubinius and TruffleRuby on the JVM, and similar or better
startup performance than MRI.

| Implementation     | Real Time (s) | Max RSS (MB) |
| ------------------ | ------------: | -----------: |
| TruffleRuby Native | 0.025         |  65          |
| MRI 2.6.2          | 0.048         |  14          |
| Rubinius 3.107     | 0.150         |  78          |
| JRuby 9.2.7.0      | 1.357         | 160          |
| TruffleRuby JVM    | 1.787         | 456          |

Run on Linux with an Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz with a SSD.

```bash
cd graalvm-ce-1.0.0-rc15
bin/ruby -e 'puts "Hello"'       # TruffleRuby on the SVM
bin/ruby --jvm -e 'puts "Hello"' # TruffleRuby on the JVM

chruby ruby-2.6.2
ruby -e 'puts "Hello"'

chruby rbx-3.107
rbx -e 'puts "Hello"'

chruby jruby-9.2.7.0
jruby -e 'puts "Hello"'
```

The real time and the maximum resident set size are measured with a custom
[C program](https://gist.github.com/eregon/cbf6c89451ecf815463c00aef9745837)
as `time` cannot measure real time in milliseconds and max RSS.
Each command is run 10 times and the average is reported.
`clock_gettime(CLOCK_MONOTONIC)` is used to measure time and `getrusage()` to
measure max RSS.

You can build a native build of TruffleRuby using the Native Image Tool from a
source distribution using:

```bash
jt build --env native
```

The disadvantages of the Native Image version of TruffleRuby are:

* It has lower peak performance, as the GC is simpler and some optimisations
  such as compressed ordinary object pointers (OOPS) are not yet available.
* You can't use standard Java tools like VisualVM.
* Java interoperability works in the native configuration but requires more setup.
  First, only for classes loaded in the image can be accessed.
  You can add more classes by compiling a native image including TruffleRuby.
  See [Build Native Images from Polyglot Applications](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md#build-native-images-from-polyglot-applications) for details.

So the native version may not be appropriate for all uses.

For the highest performance for long-running processes you want to use the
JVM version of TruffleRuby, using `--jvm`.
