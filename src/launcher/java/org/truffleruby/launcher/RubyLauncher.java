/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.launcher;

import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.ProcessBuilder.Redirect;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.maven.downloader.Main;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.truffleruby.shared.ProcessStatus;
import org.truffleruby.shared.Platform;
import org.truffleruby.shared.Metrics;
import org.truffleruby.shared.TruffleRuby;
import org.truffleruby.shared.options.OptionsCatalog;
import org.truffleruby.signal.LibRubySignal;

public class RubyLauncher extends AbstractLanguageLauncher {

    private CommandLineOptions config;
    private String implementationName = null;
    private boolean helpOptionUsed = false; // Any --help* option
    private String rubyHome = null;

    /** NOTE: not actually used by thin launchers. The first method called with the arguments is
     * {@link #preprocessArguments}. */
    public static void main(String[] args) {
        new RubyLauncher().launch(args);
    }

    private static void trufflerubyPolyglotGet(List<String> originalArgs) {
        String rubyHome = getPropertyOrFail("org.graalvm.language.ruby.home");
        String outputDir = rubyHome + "/modules";
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add(outputDir);
        args.add("-v");
        args.add(getPropertyOrFail("org.graalvm.version"));
        if (originalArgs.size() == 1 && !originalArgs.get(0).startsWith("-")) {
            args.add("-a");
        }
        args.addAll(originalArgs);
        try {
            Main.main(args.toArray(CommandLineOptions.EMPTY_STRING_ARRAY));
        } catch (Exception e) {
            throw new Error(e);
        }
        System.exit(0);
    }

    private static String getPropertyOrFail(String property) {
        String value = System.getProperty(property);
        if (value == null) {
            throw new UnsupportedOperationException("Expected system property " + property + " to be set");
        }
        return value;
    }

    @Override
    protected String getLanguageId() {
        return TruffleRuby.LANGUAGE_ID;
    }

    @Override
    protected String[] getDefaultLanguages() {
        // Allow all languages in launcher without needing --polyglot, as in graal 25.1
        return CommandLineOptions.EMPTY_STRING_ARRAY;
    }

    @Override
    protected String getMainClass() {
        return RubyLauncher.class.getName();
    }

    @Override
    protected void printVersion() {
        getOutput().println(TruffleRuby.getVersionString(getImplementationNameFromEngine()));
        getOutput().println();
        printPolyglotVersions();
    }

    @Override
    protected List<String> preprocessArguments(List<String> args, Map<String, String> polyglotOptions) {
        String launcherName = System.getProperty("org.graalvm.launcher.executablename", "miniruby");
        if (launcherName.endsWith("truffleruby-polyglot-get")) {
            if (isAOT()) {
                throw abort("truffleruby-polyglot-get is not available for the native standalone");
            } else {
                trufflerubyPolyglotGet(args);
            }
        }

        // Set default options for the launcher which don't match the OptionKey's default.
        // These options can still be overridden if set explicitly.
        polyglotOptions.put(OptionsCatalog.EMBEDDED.getName(), "false");
        if (isAOT()) {
            final String launcher = ProcessProperties.getExecutableName();
            polyglotOptions.put(OptionsCatalog.LAUNCHER.getName(), launcher);
        }

        // TruffleRuby is never distributed without the GraalVM compiler, so this warning is not necessary
        polyglotOptions.put("engine.WarnInterpreterOnly", "false");

        config = new CommandLineOptions(args);

        try {
            config.executionAction = ExecutionAction.UNSET;

            final CommandLineParser argumentCommandLineParser = new CommandLineParser(args, config, true, false);
            argumentCommandLineParser.processArguments();

            if (config.readRubyOptEnv) {
                /* Calling processArguments() here will also add any unrecognized arguments such as
                 * --jvm/--native/--vm.* arguments and polyglot options to `config.getUnknownArguments()`, which will
                 * then be processed by AbstractLanguageLauncher. For VM arguments, #validateVmArguments() will be
                 * called to check that the guessed --vm.* arguments match the actual ones (should always be the case,
                 * except if --vm.* arguments are added dynamically like --vm.Xmn1g for gem/bundle on native). If they
                 * do not match then the thin launcher will relaunch by execve(). Polyglot options are parsed by
                 * AbstractLanguageLauncher#parseUnrecognizedOptions. */
                // Process RUBYOPT
                final List<String> rubyoptArgs = getArgsFromEnvVariable("RUBYOPT");
                new CommandLineParser(rubyoptArgs, config, false, true).processArguments();
                // Process TRUFFLERUBYOPT
                final List<String> trufflerubyoptArgs = getArgsFromEnvVariable("TRUFFLERUBYOPT");
                new CommandLineParser(trufflerubyoptArgs, config, false, false).processArguments();
            }

            // Process RUBYLIB, must be after arguments and RUBYOPT
            final List<String> rubyLibPaths = getPathListFromEnvVariable("RUBYLIB");
            for (String path : rubyLibPaths) {
                config.appendOptionValue(OptionsCatalog.LOAD_PATHS, path);
            }

            if (config.isGemOrBundle()) {
                // Apply options to run gem/bundle more efficiently
                if (isAOT()) {
                    config.getUnknownArguments().add(0, "--vm.Xmn1g");
                }
            }

        } catch (CommandLineException commandLineException) {
            getError().println("truffleruby: " + commandLineException.getMessage());
            if (commandLineException.isUsageError()) {
                printHelp(getError());
            }
            System.exit(1);
        }

        return config.getUnknownArguments();
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {
        Metrics.begin();
        printPreRunInformation(config);
        final int exitValue = runRubyMain(contextBuilder, config);
        Metrics.end();
        System.exit(exitValue);
    }

    /** This is only used to provide suggestions when an option is misspelled. It should only list options which are
     * parsed directly by the CommandLineParser. Normal SDK options are already handled by the common Launcher code. */
    @Override
    protected void collectArguments(Set<String> options) {
        options.addAll(Arrays.asList(
                "-0",
                "-a",
                "-c",
                "-C",
                "-d",
                "-e",
                "-E",
                "-F",
                "-i",
                "-I",
                "-l",
                "-n",
                "-p",
                "-r",
                "-s",
                "-S",
                "-T",
                "-v",
                "-w",
                "-W",
                "-x",
                "--copyright",
                "--disable",
                "--enable",
                "--encoding",
                "--version"));
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        printHelp(getOutput());
    }

    @Override
    protected AbortException abortUnrecognizedArgument(String argument) {
        throw abortInvalidArgument(
                argument,
                "truffleruby: invalid option " + argument + "  (Use --help for usage instructions.)");
    }

    @Override
    protected boolean parseCommonOption(String defaultOptionPrefix, Map<String, String> polyglotOptions,
            boolean experimentalOptions, String arg) {
        if (arg.startsWith("--help")) {
            helpOptionUsed = true;
        }

        return super.parseCommonOption(defaultOptionPrefix, polyglotOptions, experimentalOptions, arg);
    }

    @Override
    protected boolean runLauncherAction() {
        String pager;
        if (helpOptionUsed && isTTY() && !(pager = getPagerFromEnv()).isEmpty()) {
            try {
                Process process = new ProcessBuilder(pager.split(" "))
                        .redirectOutput(Redirect.INHERIT) // set the output of the pager to the terminal and not a pipe
                        .redirectError(Redirect.INHERIT) // set the error of the pager to the terminal and not a pipe
                        .start();
                PrintStream out = new PrintStream(process.getOutputStream(), false, StandardCharsets.UTF_8);

                setOutput(out);
                boolean code = super.runLauncherAction();

                out.flush();
                out.close();
                process.waitFor();

                return code;
            } catch (IOException | InterruptedException e) {
                throw abort(e);
            }
        } else {
            return super.runLauncherAction();
        }
    }

    private int runRubyMain(Context.Builder contextBuilder, CommandLineOptions config) {
        if (config.executionAction == ExecutionAction.UNSET) {
            switch (config.defaultExecutionAction) {
                case NONE:
                    return 0;
                case STDIN:
                    config.executionAction = ExecutionAction.STDIN;
                    break;
                case IRB:
                    config.executionAction = ExecutionAction.PATH;
                    if (isTTY()) {
                        getError().println(
                                "[ruby] WARNING: truffleruby starts IRB when stdin is a TTY instead of reading from stdin, use '-' to read from stdin");
                        config.executionAction = ExecutionAction.PATH;
                        config.toExecute = "irb";
                    } else {
                        config.executionAction = ExecutionAction.STDIN;
                    }
                    break;
            }
        }

        if (config.executionAction == ExecutionAction.NONE) {
            return 0;
        }

        if (config.isGemOrBundle() && getImplementationNameFromEngine().contains("Graal")) {
            // Apply options to run gem/bundle more efficiently
            contextBuilder.option("engine.Mode", "latency");
            if (Boolean.getBoolean("truffleruby.launcher.log")) {
                getError().println("[ruby] CONFIG: detected gem or bundle command, using --engine.Mode=latency");
            }
        }

        contextBuilder.options(config.getOptions());

        contextBuilder.arguments(TruffleRuby.LANGUAGE_ID, config.getArguments());

        int processStatus = runContext(contextBuilder, config);

        final boolean runTwice = config.getUnknownArguments().contains("--run-twice") ||
                config.getUnknownArguments().contains("--run-twice=true");
        if (runTwice) {
            final int secondResult = runContext(contextBuilder, config);
            if (secondResult != 0 && processStatus == 0) {
                processStatus = secondResult;
            }
        }

        // SignalExeption exit, we need to raise(3) the native signal to set the correct process waitpid(3) status
        if (ProcessStatus.isSignal(processStatus)) {
            int signalNumber = ProcessStatus.toSignal(processStatus);

            LibRubySignal.loadLibrary(rubyHome, Platform.LIB_SUFFIX);
            LibRubySignal.restoreSystemHandlerAndRaise(signalNumber);
            // Some signals are ignored by default, such as SIGWINCH and SIGCHLD, in that exit with 1 like CRuby
            return 1;
        }

        return processStatus;
    }

    private int runContext(Context.Builder builder, CommandLineOptions config) {
        try (Context context = builder.build()) {
            Metrics.printTime("before-run");

            if (config.executionAction == ExecutionAction.PATH) {
                final Source source = source(// language=ruby
                        "-> name { Truffle::Boot.find_s_file(name) }");

                config.executionAction = ExecutionAction.FILE;
                final Value file = context.eval(source).execute(config.toExecute);
                if (file.isString()) {
                    config.toExecute = file.asString();
                } else {
                    getError()
                            .println("truffleruby: No such file or directory -- " + config.toExecute + " (LoadError)");
                    return ProcessStatus.exitCode(1);
                }
            }

            if (config.logProcessArguments) {
                Value logInfo = context.eval(source(
                        // language=ruby
                        "-> message { Truffle::Debug.log_info(message) }"));
                String message = "new process: truffleruby " + String.join(" ", config.initialArguments);
                logInfo.executeVoid(message);
            }

            final Source source = source(// language=ruby
                    "-> argc, argv, kind, to_execute { Truffle::Boot.main(argc, argv, kind, to_execute) }");

            final int argc = getNativeArgc();
            final long argv = getNativeArgv();
            final String kind = config.executionAction.name();
            final int processStatus = context.eval(source).execute(argc, argv, kind, config.toExecute).asInt();

            if (ProcessStatus.isSignal(processStatus)) {
                // Only fetch the ruby home when necessary, because chromeinspector tests
                // currently only work with a single Context#eval.
                Value rubyHome = context.eval(source(// language=ruby
                        "Truffle::Boot.ruby_home"));
                this.rubyHome = rubyHome.asString();
            }

            Metrics.printTime("after-run");
            return processStatus;
        } catch (PolyglotException e) {
            getError().println(
                    "truffleruby: an exception escaped out of the interpreter - this is an implementation bug");
            e.printStackTrace(System.err);
            return ProcessStatus.exitCode(1);
        }
    }

    private static Source source(String code) {
        return Source.newBuilder(TruffleRuby.LANGUAGE_ID, code, TruffleRuby.BOOT_SOURCE_NAME).internal(true)
                .buildLiteral();
    }

    private static List<String> getArgsFromEnvVariable(String name) {
        String value = System.getenv(name);
        if (value != null) {
            value = value.strip();
            if (!value.isEmpty()) {
                return new ArrayList<>(Arrays.asList(value.split("\\s+")));
            }
        }
        return Collections.emptyList();
    }

    private static List<String> getPathListFromEnvVariable(String name) {
        final String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            return new ArrayList<>(Arrays.asList(value.split(":")));
        }
        return Collections.emptyList();
    }

    private static String getPagerFromEnv() {
        String pager = System.getenv("RUBY_PAGER");
        if (pager != null) {
            return pager.strip();
        }

        pager = System.getenv("PAGER");
        if (pager != null) {
            return pager.strip();
        }

        return "";
    }

    private void printPreRunInformation(CommandLineOptions config) {
        if (config.showVersion) {
            getOutput().println(TruffleRuby.getVersionString(getImplementationNameFromEngine()));
        }

        if (config.showCopyright) {
            getOutput().println(TruffleRuby.RUBY_COPYRIGHT);
        }

        switch (config.showHelp) {
            case NONE:
                break;
            case SHORT:
                printShortHelp(getOutput());
                break;
            // --help is handled by org.graalvm.launcher.Launcher#printDefaultHelp
        }
    }

    private String getImplementationNameFromEngine() {
        if (implementationName == null) {
            try (Engine engine = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()) {
                implementationName = engine.getImplementationName();
            }
        }

        return implementationName;
    }

    /*-
     * To update this:
     *   - run `ruby --help | ruby -e 'puts STDIN.readlines.map { |line| "out.println(#{line.chomp.inspect});" }'`
     *   - replace "ruby" by "truffleruby" in the first line
     *   - remove unsupported flags (--*jit, --dump, --parser, --crash-report, -y, --yydebug)
     *   - add an extra `out.println();` before:
     *     - `out.println("Features:");` and
     *     - `out.println("Warning categories:");`
     *   - remove the "Dump List:" section
     *   - remove JIT-related lines
     */
    private static void printHelp(PrintStream out) {
        out.println("Usage: truffleruby [options] [--] [filepath] [arguments]");
        out.println("  -0[octal]       Set input record separator ($/):");
        out.println("                  -0 for \\0; -00 for paragraph mode; -0777 for slurp mode.");
        out.println("  -a              Split each input line ($_) into fields ($F).");
        out.println("  -c              Check syntax (no execution).");
        out.println("  -Cdirpath       Execute program in specified directory.");
        out.println("  -d, --debug     Set debugging flag ($DEBUG) to true.");
        out.println("  -e 'code'       Execute given Ruby code; multiple -e allowed.");
        out.println("  -Eex[:in], --encoding=ex[:in]");
        out.println("                  Set default external and internal encodings.");
        out.println("  -Fpattern       Set input field separator ($;); used with -a.");
        out.println("  -i[extension]   Set ARGF in-place mode;");
        out.println("                  create backup files with given extension.");
        out.println("  -Idirpath       Add specified directory to load paths ($LOAD_PATH);");
        out.println("                  multiple -I allowed.");
        out.println("  -l              Set output record separator ($\\) to $/;");
        out.println("                  used for line-oriented output.");
        out.println("  -n              Run program in gets loop.");
        out.println("  -p              Like -n, with printing added.");
        out.println("  -rlibrary       Require the given library.");
        out.println("  -s              Define global variables using switches following program path.");
        out.println("  -S              Search directories found in the PATH environment variable.");
        out.println("  -v              Print version; set $VERBOSE to true.");
        out.println("  -w              Synonym for -W1.");
        out.println("  -W[level=2|:category]");
        out.println("                  Set warning flag ($-W):");
        out.println("                  0 for silent; 1 for moderate; 2 for verbose.");
        out.println("  -x[dirpath]     Execute Ruby code starting from a #!ruby line.");
        out.println("  --backtrace-limit=num");
        out.println("                  Set backtrace limit.");
        out.println("  --copyright     Print Ruby copyright.");
        out.println("  --disable=features");
        out.println("                  Disable features; see list below.");
        out.println("  --enable=features");
        out.println("                  Enable features; see list below.");
        out.println("  --external-encoding=encoding");
        out.println("                  Set default external encoding.");
        out.println("  --help          Print long help message; use -h for short message.");
        out.println("  --internal-encoding=encoding");
        out.println("                  Set default internal encoding.");
        out.println("  --verbose       Set $VERBOSE to true; ignore input from $stdin.");
        out.println("  --version       Print Ruby version.");
        out.println();
        out.println("Features:");
        out.println("  gems            Rubygems (only for debugging, default: enabled).");
        out.println("  error_highlight error_highlight (default: enabled).");
        out.println("  did_you_mean    did_you_mean (default: enabled).");
        out.println("  syntax_suggest  syntax_suggest (default: enabled).");
        out.println("  rubyopt         RUBYOPT environment variable (default: enabled).");
        out.println("  frozen-string-literal");
        out.println("                  Freeze all string literals (default: disabled).");
        out.println();
        out.println("Warning categories:");
        out.println("  deprecated      Deprecated features.");
        out.println("  experimental    Experimental features.");
        out.println("  performance     Performance issues.");
        out.println("  strict_unused_block");
        out.println("                  Warning unused block strictly");
    }

    // Same as above, but with "ruby -h"
    private static void printShortHelp(PrintStream out) {
        out.println("Usage: truffleruby [options] [--] [filepath] [arguments]");
        out.println("  -0[octal]       Set input record separator ($/)");
        out.println("  -a              Split each input line ($_) into fields ($F)");
        out.println("  -c              Check syntax (no execution)");
        out.println("  -Cdirpath       Execute program in specified directory");
        out.println("  -d              Set debugging flag ($DEBUG) to true");
        out.println("  -e 'code'       Execute given Ruby code; multiple -e allowed");
        out.println("  -Eex[:in]       Set default external and internal encodings");
        out.println("  -Fpattern       Set input field separator ($;); used with -a");
        out.println("  -i[extension]   Set ARGF in-place mode");
        out.println("  -Idirpath       Add specified directory to load paths ($LOAD_PATH)");
        out.println("  -l              Set output record separator ($\\) to $/");
        out.println("  -n              Run program in gets loop");
        out.println("  -p              Like -n, with printing added");
        out.println("  -rlibrary       Require the given library");
        out.println("  -s              Define global variables using switches following program path");
        out.println("  -S              Search directories found in the PATH environment variable");
        out.println("  -v              Print version; set $VERBOSE to true");
        out.println("  -w              Synonym for -W1");
        out.println("  -W[level=2|:category]     Set warning flag ($-W)");
        out.println("  -x[dirpath]     Execute Ruby code starting from a #!ruby line");
        out.println("  -h              Print this help message; use --help for longer message");
    }

}
