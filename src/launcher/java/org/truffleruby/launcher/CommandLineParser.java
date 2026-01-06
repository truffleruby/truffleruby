/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2007-2011 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2009 Joseph LaFata <joe@quibb.org>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.graalvm.options.OptionDescriptor;
import org.truffleruby.shared.options.OptionsCatalog;
import org.truffleruby.shared.options.Verbosity;

public class CommandLineParser {

    private static final Logger LOGGER = createLogger();

    private final List<String> arguments;
    private int argumentIndex;
    private final boolean processArgv;
    private final boolean rubyOpts;
    final CommandLineOptions config;
    private int lastInterpreterArgumentIndex;
    private int characterIndex;

    public CommandLineParser(List<String> arguments, CommandLineOptions config, boolean processArgv, boolean rubyOpts) {
        this.argumentIndex = 0;
        this.characterIndex = 0;
        this.lastInterpreterArgumentIndex = -1;
        this.config = config;
        this.processArgv = processArgv;
        this.rubyOpts = rubyOpts;
        this.arguments = Collections.unmodifiableList(arguments);
    }

    public void processArguments() throws CommandLineException {
        while (argumentIndex < arguments.size() && isInterpreterArgument(getCurrentArgument())) {
            processArgument();
            argumentIndex++;
        }

        if (!endOfInterpreterArguments()) {
            lastInterpreterArgumentIndex = argumentIndex;
        }
        assert lastInterpreterArgumentIndex >= 0;

        if (config.executionAction == ExecutionAction.UNSET) {
            if (argumentIndex < arguments.size()) {
                config.executionAction = ExecutionAction.FILE;
                //consume the file name
                config.toExecute = getCurrentArgument();
                argumentIndex++;
            }
        }

        if (processArgv) {
            processArgv();
        }
    }

    private boolean endOfInterpreterArguments() {
        return lastInterpreterArgumentIndex != -1;
    }

    private void processArgv() {
        boolean argvGlobalsOn = config.getOptionRaw(OptionsCatalog.ARGV_GLOBALS).equals("true");
        ArrayList<String> arglist = new ArrayList<>();
        for (; argumentIndex < arguments.size(); argumentIndex++) {
            String arg = getCurrentArgument();
            if (argvGlobalsOn && arg.startsWith("-")) {
                arg = arg.substring(1);
                int split = arg.indexOf('=');
                final String key;
                final String value;
                if (split > 0) {
                    key = arg.substring(0, split);
                    value = arg.substring(split + 1);
                } else {
                    key = arg;
                    value = null;
                }

                // Switches without values are stored separately in ARGV_GLOBAL_FLAGS. Otherwise, it would not be
                // possible to determine if the value is supposed to be `true` or `"true"`.
                final OptionDescriptor optionDescription = value != null
                        ? OptionsCatalog.ARGV_GLOBAL_VALUES
                        : OptionsCatalog.ARGV_GLOBAL_FLAGS;
                // replace dashes with underscores make it a valid global variable name
                config.appendOptionValue(optionDescription, key.replace('-', '_'));
                if (value != null) {
                    config.appendOptionValue(optionDescription, value);
                }
            } else {
                argvGlobalsOn = false;
                arglist.add(arg);
            }
        }
        // Remaining arguments are for the script itself
        config.setArguments(arglist.toArray(CommandLineOptions.EMPTY_STRING_ARRAY));
    }

    private boolean isInterpreterArgument(String argument) {
        return argument.length() > 0 && (argument.charAt(0) == '-' || argument.charAt(0) == '+') &&
                !endOfInterpreterArguments();
    }

    private String getArgumentError(String additionalError) {
        return "invalid argument\n" + additionalError + "\n";
    }

    private void processArgument() throws CommandLineException {
        String argument = getCurrentArgument();

        if (argument.length() == 1) {
            // sole "-" means read from stdin and pass remaining args as ARGV
            lastInterpreterArgumentIndex = argumentIndex;

            if (config.executionAction == ExecutionAction.UNSET) {
                config.executionAction = ExecutionAction.STDIN;
            } else {
                // if other action is set then ignore '-', just threat it as a first script argument,
                // and stop option parsing
            }

            return;
        }

        FOR: for (characterIndex = 1; characterIndex < argument.length(); characterIndex++) {
            switch (argument.charAt(characterIndex)) {
                case '0': {
                    disallowedInRubyOpts(argument);
                    String temp = grabOptionalValue();
                    if (null == temp) {
                        //config.setRecordSeparator("\u0000");
                    } else if (temp.equals("0")) {
                        //config.setRecordSeparator("\n\n");
                    } else if (temp.equals("777")) {
                        //config.setRecordSeparator("\uffff"); // Specify something that can't separate
                    } else {
                        try {
                            Integer.parseInt(temp, 8);
                            //config.setRecordSeparator(String.valueOf((char) val));
                        } catch (Exception e) {
                            throw new CommandLineException(
                                    getArgumentError(" -0 must be followed by either 0, 777, or a valid octal value"),
                                    true);
                        }
                    }
                    //break FOR;
                    throw notImplemented("-0");
                }
                case 'a':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.SPLIT_LOOP, true);
                    break;
                case 'c':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.SYNTAX_CHECK, true);
                    break;
                case 'C':
                case 'X':
                    disallowedInRubyOpts(argument);
                    final String dir = grabValue(
                            getArgumentError(
                                    " -" + argument.charAt(characterIndex) +
                                            " must be followed by a directory expression"));
                    config.setOption(OptionsCatalog.WORKING_DIRECTORY, dir);
                    break FOR;
                case 'd':
                    config.setOption(OptionsCatalog.DEBUG, true);
                    config.setOption(OptionsCatalog.VERBOSITY, Verbosity.TRUE);
                    break;
                case 'e':
                    disallowedInRubyOpts(argument);
                    final String nextArgument = grabValue(
                            getArgumentError(" -e must be followed by an expression to report"));

                    final ExecutionAction currentExecutionAction = config.executionAction;
                    if (currentExecutionAction == ExecutionAction.UNSET ||
                            currentExecutionAction == ExecutionAction.INLINE) {
                        config.executionAction = ExecutionAction.INLINE;
                        config.toExecute += nextArgument + "\n";
                    } else {
                        // ignore option
                    }

                    break FOR;
                case 'E':
                    processEncodingOption(grabValue(getArgumentError("unknown encoding name")));
                    break FOR;
                case 'F':
                    disallowedInRubyOpts(argument);
                    throw notImplemented("-F");
                case 'h':
                    disallowedInRubyOpts(argument);
                    config.showHelp = ShowHelp.SHORT;
                    // cancel other execution actions
                    config.executionAction = ExecutionAction.NONE;
                    break;
                case 'i':
                    disallowedInRubyOpts(argument);
                    throw notImplemented("-i");
                case 'I':
                    String s = grabValue(
                            getArgumentError("-I must be followed by a directory name to add to lib path"));
                    for (String path : s.split(File.pathSeparator)) {
                        if (path.startsWith("~" + File.separator)) {
                            path = System.getProperty("user.home") + File.separator + path.substring(2);
                        }
                        config.appendOptionValue(OptionsCatalog.LOAD_PATHS, path);
                    }
                    break FOR;
                case 'y':
                    disallowedInRubyOpts(argument);
                    warnInternalDebugTool(argument);
                    break FOR;
                case 'K':
                    characterIndex++;
                    if (characterIndex == argument.length()) {
                        break;
                    }
                    final char code = argument.charAt(characterIndex);
                    final String sourceEncodingName;
                    switch (code) {
                        case 'a':
                        case 'A':
                        case 'n':
                        case 'N':
                            sourceEncodingName = "ascii-8bit";
                            break;

                        case 'e':
                        case 'E':
                            sourceEncodingName = "euc-jp";
                            break;

                        case 'u':
                        case 'U':
                            sourceEncodingName = "utf-8";
                            break;

                        case 's':
                        case 'S':
                            sourceEncodingName = "windows-31j";
                            break;

                        default:
                            sourceEncodingName = null;
                            break;
                    }
                    if (sourceEncodingName != null) {
                        config.setOption(OptionsCatalog.SOURCE_ENCODING, sourceEncodingName);
                        config.setOption(OptionsCatalog.EXTERNAL_ENCODING, sourceEncodingName);
                    }
                    break;
                case 'l':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.CHOMP_LOOP, true);
                    break;
                case 'n':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.GETS_LOOP, true);
                    break;
                case 'p':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.PRINT_LOOP, true);
                    config.setOption(OptionsCatalog.GETS_LOOP, true);
                    break;
                case 'r':
                    final String library = grabValue(getArgumentError("-r must be followed by a package to require"));
                    config.appendOptionValue(OptionsCatalog.REQUIRED_LIBRARIES, library);
                    break FOR;
                case 's':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.ARGV_GLOBALS, true);
                    break;
                case 'G':
                    throw notImplemented("-G");
                case 'S':
                    disallowedInRubyOpts(argument);
                    lastInterpreterArgumentIndex = argumentIndex; // set before grabValue increments index

                    String scriptName = grabValue("provide a bin script to execute");

                    if (config.executionAction == ExecutionAction.UNSET) {
                        config.executionAction = ExecutionAction.PATH;
                        config.toExecute = scriptName;
                    } else {
                        // ignore the option
                    }

                    break FOR;
                case 'T':
                    throw notImplemented("-T");
                case 'U':
                    config.setOption(OptionsCatalog.INTERNAL_ENCODING, "UTF-8");
                    break;
                case 'v':
                    config.setOption(OptionsCatalog.VERBOSITY, Verbosity.TRUE);
                    config.showVersion = true;
                    config.defaultExecutionAction = DefaultExecutionAction.NONE;
                    break;
                case 'w':
                    config.setOption(OptionsCatalog.VERBOSITY, Verbosity.TRUE);
                    setAllWarningCategories(true);
                    break;
                case 'W': {
                    String temp = grabOptionalValue();
                    if (temp == null) {
                        temp = "2";
                    }
                    if (temp.startsWith(":")) {
                        switch (temp) {
                            case ":deprecated":
                                config.setOption(OptionsCatalog.WARN_DEPRECATED, true);
                                break;
                            case ":no-deprecated":
                                config.setOption(OptionsCatalog.WARN_DEPRECATED, false);
                                break;
                            case ":experimental":
                                config.setOption(OptionsCatalog.WARN_EXPERIMENTAL, true);
                                break;
                            case ":no-experimental":
                                config.setOption(OptionsCatalog.WARN_EXPERIMENTAL, false);
                                break;
                            case ":performance":
                                config.setOption(OptionsCatalog.WARN_PERFORMANCE, true);
                                break;
                            case ":no-performance":
                                config.setOption(OptionsCatalog.WARN_PERFORMANCE, false);
                                break;
                            default:
                                LOGGER.warning("unknown warning category: `" + temp.substring(1) + "'");
                                break;
                        }
                    } else {
                        switch (temp) {
                            case "0":
                                config.setOption(OptionsCatalog.VERBOSITY, Verbosity.NIL);
                                setAllWarningCategories(false);
                                break;
                            case "2":
                                config.setOption(OptionsCatalog.VERBOSITY, Verbosity.TRUE);
                                setAllWarningCategories(true);
                                break;
                            case "1":
                            default:
                                config.setOption(OptionsCatalog.VERBOSITY, Verbosity.FALSE);
                                config.setOption(OptionsCatalog.WARN_DEPRECATED, false);
                                break;
                        }
                    }
                    break FOR;
                }
                case 'x':
                    disallowedInRubyOpts(argument);
                    config.setOption(OptionsCatalog.IGNORE_LINES_BEFORE_RUBY_SHEBANG, true);
                    String directory = grabOptionalValue();
                    if (directory != null) {
                        throw notImplemented("-x with directory");
                    }
                    break FOR;
                case '-':
                    if (argument.equals("--copyright")) {
                        disallowedInRubyOpts(argument);
                        config.showCopyright = true;
                        // cancel other execution actions
                        config.executionAction = ExecutionAction.NONE;
                        break FOR;
                    } else if (argument.startsWith("--encoding")) {
                        if (argument.equals("--encoding")) {
                            characterIndex = argument.length();
                            String feature = grabValue(getArgumentError("missing argument for " + argument), false);
                            argument = argument + "=" + feature;
                        }
                        final String encodingNames = argument.substring(argument.indexOf('=') + 1);
                        final int index = encodingNames.indexOf(':');
                        if (index == -1) {
                            config.setOption(OptionsCatalog.EXTERNAL_ENCODING, encodingNames);
                        } else {
                            final int secondIndex = encodingNames.indexOf(':', index + 1);
                            if (secondIndex != -1) {
                                throw new CommandLineException(
                                        "extra argument for --encoding: " + encodingNames.substring(secondIndex + 1));
                            }
                            config.setOption(OptionsCatalog.EXTERNAL_ENCODING, encodingNames.substring(0, index));
                            config.setOption(OptionsCatalog.INTERNAL_ENCODING, encodingNames.substring(index + 1));
                        }
                        break FOR;
                    } else if (argument.equals("--external-encoding") || argument.equals("--internal-encoding")) {
                        // Just translate to option=value form and let the Launcher handle it
                        characterIndex = argument.length();
                        String feature = grabValue(getArgumentError("missing argument for " + argument), false);
                        config.getUnknownArguments().add(argument + "=" + feature);
                        break FOR;
                    } else if (argument.equals("--log-process-args") || argument.equals("--log-process-args=true")) {
                        config.logProcessArguments = true;
                        break FOR;
                    } else if (argument.equals("--yydebug")) {
                        disallowedInRubyOpts(argument);
                        warnInternalDebugTool(argument);
                        break FOR;
                    } else if (argument.equals("--help")) {
                        disallowedInRubyOpts(argument);
                        // --help is handled by org.graalvm.launcher.Launcher#printDefaultHelp
                        config.getUnknownArguments().add(argument);
                        break FOR;
                    } else if (argument.equals("--version")) {
                        disallowedInRubyOpts(argument);
                        config.showVersion = true;
                        // cancel other execution actions
                        config.executionAction = ExecutionAction.NONE;
                        break FOR;
                    } else if (argument.equals("--debug-frozen-string-literal")) {
                        warnInternalDebugTool(argument);
                        break FOR;
                    } else if (argument.startsWith("--disable")) {
                        final int len = argument.length();
                        if (len == "--disable".length()) {
                            characterIndex = len;
                            String feature = grabValue(getArgumentError("missing argument for --disable"), false);
                            argument = "--disable=" + feature;
                        }
                        for (String disable : valueListFor(argument, "disable")) {
                            enableDisableFeature(disable, false);
                        }
                        break FOR;
                    } else if (argument.startsWith("--enable")) {
                        final int len = argument.length();
                        if (len == "--enable".length()) {
                            characterIndex = len;
                            String feature = grabValue(getArgumentError("missing argument for --enable"), false);
                            argument = "--enable=" + feature;
                        }
                        for (String enable : valueListFor(argument, "enable")) {
                            enableDisableFeature(enable, true);
                        }
                        break FOR;
                    } else if (argument.startsWith("--dump=")) {
                        warnInternalDebugTool(argument);
                        break FOR;
                    } else if (argument.equals("--jit") || argument.startsWith("--jit-")) {
                        LOGGER.warning("JIT options are not supported - see the Graal documentation instead");
                        break FOR;
                    } else {
                        if (argument.equals("--")) {
                            // ruby interpreter compatibilty
                            // Usage: ruby [switches] [--] [programfile] [arguments])
                            lastInterpreterArgumentIndex = argumentIndex;
                            break;
                        }
                    }
                    config.getUnknownArguments().add(argument);
                    break FOR;
                default:
                    config.getUnknownArguments().add(argument);
                    break FOR;
            }
        }
    }

    private void setAllWarningCategories(boolean value) {
        config.setOption(OptionsCatalog.WARN_DEPRECATED, value);
        config.setOption(OptionsCatalog.WARN_EXPERIMENTAL, value);
        // WARN_PERFORMANCE is excluded here, it is not set by -w/-W2 on CRuby
    }

    private void enableDisableFeature(String name, boolean enable) {
        final BiConsumer<CommandLineParser, Boolean> feature = FEATURES.get(name);

        if (feature == null) {
            LOGGER.warning("warning: unknown argument for --" + (enable ? "enable" : "disable") + ": '" + name + "'");
        } else {
            feature.accept(this, enable);
        }
    }

    private static String[] valueListFor(String argument, String key) throws CommandLineException {
        int length = key.length() + 3; // 3 is from -- and = (e.g. --disable=)
        String[] values = argument.substring(length).split(",");

        if (values.length == 0) {
            errorMissingEquals(key);
        }

        return values;
    }

    private void disallowedInRubyOpts(CharSequence option) throws CommandLineException {
        if (rubyOpts) {
            throw new CommandLineException("invalid switch in RUBYOPT: " + option + " (RuntimeError)");
        }
    }

    private void warnInternalDebugTool(String option) {
        LOGGER.warning("the " + option + " switch is silently ignored as it is an internal development tool");
    }

    private static void errorMissingEquals(String label) throws CommandLineException {
        throw new CommandLineException("missing argument for --" + label + "\n", true);
    }

    /** Split string into (limited) sub-parts.
     * 
     * @param str the string
     * @param sep the separator
     * @param lim has same effect as with {@link String#split(String, int)} */
    private static List<String> split(final String str, final char sep, final int lim) {
        final int len = str.length();
        if (len == 0) {
            return Collections.singletonList(str);
        }

        final ArrayList<String> result = new ArrayList<>(lim <= 0 ? 8 : lim);

        int e;
        int s = 0;
        int count = 0;
        while ((e = str.indexOf(sep, s)) != -1) {
            if (lim == ++count) { // limited (lim > 0) case
                result.add(str.substring(s));
                return result;
            }
            result.add(str.substring(s, e));
            s = e + 1;
        }
        if (s < len || (s == len && lim > 0)) {
            result.add(str.substring(s));
        }

        return result;
    }

    private void processEncodingOption(String value) throws CommandLineException {
        List<String> encodings = split(value, ':', 3);

        if (encodings.size() >= 3) {
            throw new CommandLineException("extra argument for -E: " + encodings.get(2));
        }

        if (encodings.size() >= 2) {
            config.setOption(OptionsCatalog.INTERNAL_ENCODING, encodings.get(1));
        }

        if (encodings.size() >= 1) {
            config.setOption(OptionsCatalog.EXTERNAL_ENCODING, encodings.get(0));
        }
    }

    private String grabValue(String errorMessage) throws CommandLineException {
        return grabValue(errorMessage, true);
    }

    private String grabValue(String errorMessage, boolean usageError) throws CommandLineException {
        String optValue = grabOptionalValue();
        if (optValue != null) {
            return optValue;
        }
        argumentIndex++;
        if (argumentIndex < arguments.size()) {
            return getCurrentArgument();
        }
        throw new CommandLineException(errorMessage, usageError);
    }

    private String grabOptionalValue() {
        characterIndex++;
        String argValue = getCurrentArgument();
        if (characterIndex < argValue.length()) {
            return argValue.substring(characterIndex);
        }
        return null;
    }

    private String getCurrentArgument() {
        return arguments.get(argumentIndex);
    }

    private CommandLineException notImplemented(String option) {
        return new CommandLineException(String.format("the %s option is not implemented", option));
    }

    private static final Map<String, BiConsumer<CommandLineParser, Boolean>> FEATURES = new HashMap<>();

    static {
        FEATURES.put("all", (processor, enable) -> {
            for (Map.Entry<String, BiConsumer<CommandLineParser, Boolean>> feature : FEATURES.entrySet()) {
                if (!feature.getKey().equals("all")) {
                    feature.getValue().accept(processor, enable);
                }
            }
        });

        FEATURES.put(
                "did_you_mean",
                (processor, enable) -> processor.config.setOption(OptionsCatalog.DID_YOU_MEAN, enable));

        FEATURES.put(
                "did-you-mean",
                FEATURES.get("did_you_mean"));

        FEATURES.put(
                "gem",
                (processor, enable) -> processor.config.setOption(OptionsCatalog.RUBYGEMS, enable));

        FEATURES.put(
                "gems",
                FEATURES.get("gem"));

        FEATURES.put(
                "frozen-string-literal",
                (processor, enable) -> processor.config.setOption(OptionsCatalog.FROZEN_STRING_LITERALS, enable));

        FEATURES.put(
                "frozen_string_literal",
                FEATURES.get("frozen-string-literal"));

        FEATURES.put(
                "rubyopt",
                (processor, enable) -> processor.config.readRubyOptEnv = enable);
    }

    private static Logger createLogger() {
        final Logger logger = Logger.getLogger("ruby-launcher");

        logger.setUseParentHandlers(false);

        logger.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                System.err.printf("[ruby] %s %s%n", record.getLevel().getName(), record.getMessage());
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {
            }

        });

        return logger;
    }

}
