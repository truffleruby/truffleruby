# TruffleRuby Options and Command Line

TruffleRuby has the same command-line interface as our compatible MRI version.

```shell
Usage: truffleruby [options] [--] [filepath] [arguments]
  -0[octal]       Set input record separator ($/):
                  -0 for \0; -00 for paragraph mode; -0777 for slurp mode.
  -a              Split each input line ($_) into fields ($F).
  -c              Check syntax (no execution).
  -Cdirpath       Execute program in specified directory.
  -d, --debug     Set debugging flag ($DEBUG) to true.
  -e 'code'       Execute given Ruby code; multiple -e allowed.
  -Eex[:in], --encoding=ex[:in]
                  Set default external and internal encodings.
  -Fpattern       Set input field separator ($;); used with -a.
  -i[extension]   Set ARGF in-place mode;
                  create backup files with given extension.
  -Idirpath       Add specified directory to load paths ($LOAD_PATH);
                  multiple -I allowed.
  -l              Set output record separator ($\) to $/;
                  used for line-oriented output.
  -n              Run program in gets loop.
  -p              Like -n, with printing added.
  -rlibrary       Require the given library.
  -s              Define global variables using switches following program path.
  -S              Search directories found in the PATH environment variable.
  -v              Print version; set $VERBOSE to true.
  -w              Synonym for -W1.
  -W[level=2|:category]
                  Set warning flag ($-W):
                  0 for silent; 1 for moderate; 2 for verbose.
  -x[dirpath]     Execute Ruby code starting from a #!ruby line.
  --backtrace-limit=num
                  Set backtrace limit.
  --copyright     Print Ruby copyright.
  --disable=features
                  Disable features; see list below.
  --enable=features
                  Enable features; see list below.
  --external-encoding=encoding
                  Set default external encoding.
  --help          Print long help message; use -h for short message.
  --internal-encoding=encoding
                  Set default internal encoding.
  --verbose       Set $VERBOSE to true; ignore input from $stdin.
  --version       Print Ruby version.

Features:
  gems            Rubygems (only for debugging, default: enabled).
  error_highlight error_highlight (default: enabled).
  did_you_mean    did_you_mean (default: enabled).
  syntax_suggest  syntax_suggest (default: enabled).
  rubyopt         RUBYOPT environment variable (default: enabled).
  frozen-string-literal
                  Freeze all string literals (default: disabled).

Warning categories:
  deprecated      Deprecated features.
  experimental    Experimental features.
  performance     Performance issues.
  strict_unused_block
                  Warning unused block strictly

Runtime options:
  --native                                     Ensure to run in Native mode.
  --jvm                                        Ensure to run in JVM mode.
  --vm.[option]                                Pass options to the host VM. To see available options, use '--help:vm'.
  --log.file=<String>                          Redirect guest languages logging into a given file.
  --log.[logger].level=<String>                Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE,
                                               FINER, FINEST or ALL.
  --help                                       Print this help message.
  --help:vm                                    Print options for the host VM.
  --help:engine                                Print engine options.
  --help:compiler                              Print engine compiler options.
  --help:all                                   Print all options.
  --version:graalvm                            Print GraalVM version information and exit.
  --show-version:graalvm                       Print GraalVM version information and continue execution.

Languages:
  [id]        [name]                  [website]
  llvm        LLVM                    https://www.graalvm.org/dev/reference-manual/llvm/
  ruby        Ruby                    https://github.com/truffleruby/truffleruby

Tools:
  [id]        [name]                  [website]
  agentscript Agent Script            
  coverage    Code Coverage           https://www.graalvm.org/tools/code-coverage/
  cpusampler  CPU Sampler             https://www.graalvm.org/tools/profiling/
  cputracer   CPU Tracer              https://www.graalvm.org/tools/profiling/
  dap         Debug Protocol Server   https://www.graalvm.org/tools/dap/
  heap        Heap Dump               
  heapmonitor Heap Allocation Monitor 
  insight     Insight                 https://www.graalvm.org/tools/graalvm-insight/
  inspect     Chrome Inspector        https://www.graalvm.org/tools/chrome-debugger/
  lsp         Language Server         https://www.graalvm.org/tools/lsp/
  memtracer   Memory Tracer           https://www.graalvm.org/tools/profiling/

  Use --help:[id] for component options.

See http://www.graalvm.org for more information.
```

TruffleRuby also reads the `RUBYOPT` environment variable, as in standard
Ruby, if run from the Ruby launcher.

## Unlisted Ruby Switches

MRI has some extra Ruby switches which are not normally listed in help output
but are documented in the Ruby manual page.

```
  -Xdirectory     cd to directory before executing your script (same as -C)
  -U              set the internal encoding to UTF-8
  -K[EeSsUuNnAa]  sets the source and external encoding
  --encoding=external[:internal]
                  the same as --external-encoding=external and optionally --internal-encoding=internal
```

## TruffleRuby Options

TruffleRuby options are set via `--option=value`, or you can use `--ruby.option=value` from any launcher.
You can omit `=value` to set to `true`.

Available options and documentation can be seen with `--help:languages`.
Additionally, set `--help:expert` and `--help:internal` to see those categories of options.
All options all experimental and subject to change at any time.

Options can also be set as JVM system properties, where they have a prefix `polyglot.ruby.`.
For example, `--vm.Dpolyglot.ruby.cexts.remap=true`, or via any other way of setting JVM system properties.
Finally, options can be set as GraalVM polyglot API configuration options.

The priority for options is the command line first, then the Graal-SDK polyglot API configuration, then system properties last.

TruffleRuby options, as well as conventional Ruby options and VM options, can also be set in the `TRUFFLERUBYOPT` and `RUBYOPT` environment variables, if run from the Ruby launcher.

`--` or the first non-option argument stops processing of TrufflRuby and VM options in the same way it stops processing of Ruby arguments.

## VM Options

To set options in the underlying VM, use `--vm.`, valid for both the native configuration and the JVM configuration.
For example, `--vm.Dsystem_property=value` or `--vm.ea`.

To set the classpath, use the `=` notation, rather than two separate arguments.
For example, `--vm.cp=lib.jar` or `--vm.classpath=lib.jar`.

## Other Binary Switches

Other binaries, such as `irb`, `gem`, and so on, support exactly the same switches as in standard Ruby.

## Determining the TruffleRuby Home

TruffleRuby needs to know where to locate files such as the standard library.
These are stored in the TruffleRuby home directory.
The Ruby home is always either the one that the Truffle framework reports or the extracted internal resources.

If the Ruby home appears not to be correct, or is unset, a exception will be thrown.
