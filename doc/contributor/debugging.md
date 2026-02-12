# Debugging TruffleRuby

This document is about debugging TruffleRuby itself at the Java level.

If you want to debug an application running on TruffleRuby or debug at the Ruby level, see [this documentation](../user/debugging.md) instead.

## Print Debugging

`System.err.println(...);` in Java and `p` in Ruby is a great first tool to use for
debugging. You can add them easily, and modifications to the core library in
Ruby don't even require any recompilation if you're running via `jt ruby` in
the JVM runtime configuration.

When you're in Java, you run arbitrary Ruby code, including `p`, using for
example `DebugHelpers.eval("p a", "a", a)`. You can print a Ruby backtrace
from Java using `DebugHelpers.eval("puts nil, caller, nil")`.

## IntelliJ IDEA Debugging

Run Ruby with `jt ruby --jdebug ...` and Ruby will wait for you to attach
the IntelliJ IDEA debugger. When running some other commands like `jt test`,
`--jdebug` might need be passed after like `jt test some/spec -- --jdebug`.
See `jt help` for where `ruby options` are passed.
You can attach the IntelliJ debugger by using *Run*, *Debug 'GraalDebug'*,
which is a pre-configured debugging profile. TruffleRuby should then continue to
run as normal.

You can then pause at any point, and inspect the Java stack trace and variables.
Use *Run*, *Debugging Actions*, *Evaluate Expression...*, to get a Java REPL.
Remember that you can use code like `DebugHelpers.eval("p a", "a", a)` to effectively
get a Ruby REPL within this Java REPL. You can also set breakpoints, and
conditional breakpoints, both while the program is already running and also
ahead of time. Conditional breakpoints can use `DebugHelpers` to break based on
Ruby conditions, but this may be very slow as it'll all be interpreted.

It is often useful to get a Ruby backtrace to find where the interpreter is in the Ruby code.
This can be done conveniently by right-clicking the `Variables` pane of the debugger, then
`New Watch...` and paste `org.truffleruby.language.backtrace.BacktraceFormatter.printableRubyBacktrace(this)`.
You can then click `View` to see the Ruby backtrace at any time, and also copy/paste that output to
`Analyze` => `Stack Trace or Thread Dump...` to be able to simply click on backtrace entries
and make IntelliJ open the file in the IDE.

A common problem is that the first thing TruffleRuby does when it starts
is to execute a lot of Ruby code to load the core library. If you set a
breakpoint it's likely to be triggered while loading the core, rather than
when in your test code. To get around this if that's not what you want, we can
set a breakpoint in `RubyLanguage#applicationStarts`, and when that breakpoint is reached,
then set or enable the breakpoints that we want for our application code.

Generally, IDEA has a very powerful debugger that you may not expect if you're
coming from the perspective of a Ruby developer, and you should explore it and
see what you're able to do beyond this.

## GDB Debugging

You can debug native extensions by using `gdb`.

For example by running `gdb --args ruby ...`.

### Save history in GDB

Add this in `~/.gdbinit`:

```
set history save on
set history filename ~/.gdb_history
set history size 10000
set history remove-duplicates unlimited
```

### Ignore SIGSEGV from JVM

```
(gdb) handle SIGSEGV pass nostop noprint
```

### Missing Symbols

Sometimes missing symbols are not shown, e.g. because STDERR is redirected.
In that case the program may exit with exit code 127 but no other information.
You can use gdb to figure out which is the missing symbol.

On Linux:
```
(gdb) b __GI__dl_fatal_printf
Function "__GI__dl_fatal_printf" not defined.
Make breakpoint pending on future shared library load? (y or [n]) y
Breakpoint 1 (__GI__dl_fatal_printf) pending.
```

And then run the program:
```
(gdb) run
...
Thread 1 "ruby" hit Breakpoint 1, __GI__dl_fatal_printf (fmt=fmt@entry=0x7ffff7ff14cc "%s: %s: %s%s%s%s%s\n")
    at dl-printf.c:305

(gdb) bt
#0  __GI__dl_fatal_printf (fmt=fmt@entry=0x7ffff7ff14cc "%s: %s: %s%s%s%s%s\n") at dl-printf.c:305
#1  0x00007ffff7fc63a9 in fatal_error (errcode=<optimized out>,
    objname=0x5555558e160f "/home/eregon/code/readline-ext/lib/readline.so", occasion=0x7ffff7ff195b "symbol lookup error",
    errstring=<optimized out>) at dl-catch.c:83
#2  0x00007ffff7fc643a in __GI__dl_signal_exception (errcode=<optimized out>, exception=<optimized out>,
    occasion=<optimized out>) at dl-catch.c:107
#3  0x00007ffff7fc6552 in _dl_signal_cexception (errcode=errcode@entry=0, exception=exception@entry=0x7ffffffe7eb0,
    occasion=occasion@entry=0x7ffff7ff195b "symbol lookup error") at dl-catch.c:165
#4  0x00007ffff7fcfdfe in _dl_lookup_symbol_x (undef_name=0x7fffc2c3912d "rb_enc_check",
    undef_map=undef_map@entry=0x55555659d270, ref=ref@entry=0x7ffffffe7f30, symbol_scope=<optimized out>, version=0x0,
    type_class=type_class@entry=1, flags=5, skip_map=0x0) at dl-lookup.c:810
...
```

This shows the missing symbol on the `_dl_lookup_symbol_x` frame, which is `rb_enc_check` in this example.

### Catch Process Exit

A way to catch the process exit before it's performed is:
```
(gdb) catch syscall exit_group
```

Then you can print the stacktrace, etc to find out what "called exit".
This also works for finding missing symbols for example.
