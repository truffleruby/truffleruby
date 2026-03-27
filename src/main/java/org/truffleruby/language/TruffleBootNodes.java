/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.options.OptionDescriptor;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.RubyLanguage.RubySourceOptions;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.exceptions.TopLevelRaiseHandler;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.loader.MainLoader;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.Metrics;
import org.truffleruby.shared.options.OptionsCatalog;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreModule("Truffle::Boot")
public abstract class TruffleBootNodes {

    @CoreMethod(names = "ruby_home", onSingleton = true)
    public abstract static class RubyHomeNode extends CoreMethodNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        Object rubyHome() {
            final String home = getLanguage().getRubyHome();
            return createString(fromJavaStringNode, home, Encodings.UTF_8);
        }

    }

    @CoreMethod(names = "force_context", onSingleton = true)
    public abstract static class ForceContextNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object forceContext() {
            return nil;
        }
    }

    @CoreMethod(names = "preinitializing?", onSingleton = true)
    public abstract static class IsPreinitializingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isPreinitializingContext() {
            return getContext().isPreInitializing();
        }
    }

    @CoreMethod(names = "was_preinitialized?", onSingleton = true)
    public abstract static class WasPreinitializedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean wasPreinitializedContext() {
            return getContext().wasPreInitialized();
        }
    }

    @CoreMethod(names = "main", onSingleton = true, required = 4, lowerFixnum = 1)
    public abstract static class MainNode extends CoreMethodArrayArgumentsNode {

        @Child TopLevelRaiseHandler topLevelRaiseHandler = new TopLevelRaiseHandler();
        @Child DispatchNode checkSyntax = DispatchNode.create();
        @Child IndirectCallNode callNode = IndirectCallNode.create();
        @Child DispatchNode requireNode = DispatchNode.create();
        @Child TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        int main(int argc, long argv, String kind, String toExecute) {
            return topLevelRaiseHandler.execute(() -> {
                getContext().nativeArgc = argc;
                getContext().nativeArgv = argv;
                setArgvGlobals();

                // Need to set $0 before loading required libraries
                // Also, a non-existing main script file errors out before loading required libraries
                final RubySource rubySource = loadMainSourceSettingDollarZero(kind, toExecute.intern()); //intern() to improve footprint

                // Load libraries required from the command line (-r LIBRARY)
                for (String requiredLibrary : getContext().getOptions().REQUIRED_LIBRARIES) {
                    requireNode.call(coreLibrary().mainObject, "require", utf8(requiredLibrary));
                }

                if (getContext().getOptions().SYNTAX_CHECK) {
                    checkSyntax.call(coreLibrary().truffleBootModule, "check_syntax", rubySource);
                } else {
                    var callTarget = getContext().getCodeLoader().parseTopLevelWithCache(rubySource, null);

                    final CodeLoader.DeferredCall deferredCall = getContext().getCodeLoader().prepareExecute(
                            callTarget,
                            ParserContext.TOP_LEVEL_FIRST,
                            DeclarationContext.topLevel(getContext()),
                            null,
                            coreLibrary().mainObject,
                            getContext().getRootLexicalScope());

                    deferredCall.call(callNode);
                }
            });
        }

        private void setArgvGlobals() {
            if (getContext().getOptions().ARGV_GLOBALS) {
                String[] global_values = getContext().getOptions().ARGV_GLOBAL_VALUES;
                assert global_values.length % 2 == 0;
                for (int i = 0; i < global_values.length; i += 2) {
                    String key = global_values[i];
                    String value = global_values[i + 1];

                    getContext().getCoreLibrary().globalVariables.define("$" + key, utf8(value), this);
                }

                String[] global_flags = getContext().getOptions().ARGV_GLOBAL_FLAGS;
                for (String flag : global_flags) {
                    getContext().getCoreLibrary().globalVariables.define("$" + flag, true, this);
                }
            }
        }

        private RubySource loadMainSourceSettingDollarZero(String kind, String toExecute) {
            final RubySource rubySource;
            final String mainScriptName;
            final MainLoader mainLoader = new MainLoader(getContext(), getLanguage());
            try {
                switch (kind) {
                    case "FILE":
                        rubySource = mainLoader.loadFromFile(getContext().getEnv(), this, toExecute);
                        mainScriptName = toExecute;
                        break;

                    case "STDIN":
                        rubySource = mainLoader.loadFromStandardIn(this, "-");
                        mainScriptName = "-";
                        break;

                    case "INLINE":
                        rubySource = mainLoader.loadFromCommandLineArgument(toExecute);
                        mainScriptName = "-e";
                        break;

                    default:
                        throw CompilerDirectives.shouldNotReachHere(kind);
                }
            } catch (IOException e) {
                throw new RaiseException(getContext(), coreExceptions().ioError(e, this));
            }
            assert rubySource.getSource().getOptions(getLanguage()).get(RubySourceOptions.MainScript);

            getContext().initializeMainScriptName(mainScriptName);

            return rubySource;
        }

        private RubyString utf8(String string) {
            return createString(fromJavaStringNode, string, Encodings.UTF_8);
        }

    }

    @CoreMethod(names = "original_argv", onSingleton = true)
    public abstract static class OriginalArgvNode extends CoreMethodNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyArray originalArgv() {
            final String[] argv = getContext().getEnv().getApplicationArguments();
            final Object[] array = new Object[argv.length];

            for (int n = 0; n < array.length; n++) {
                array[n] = createString(
                        fromJavaStringNode,
                        argv[n],
                        getContext().getEncodingManager().getDefaultExternalEncoding());
            }

            return createArray(array);
        }

    }

    @CoreMethod(names = "extra_load_paths", onSingleton = true)
    public abstract static class ExtraLoadPathsNode extends CoreMethodNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyArray extraLoadPaths() {
            final String[] paths = getContext().getOptions().LOAD_PATHS;
            final Object[] array = new Object[paths.length];

            for (int n = 0; n < array.length; n++) {
                array[n] = createString(fromJavaStringNode, paths[n], Encodings.UTF_8);
            }

            return createArray(array);
        }

    }

    @CoreMethod(names = "source_of_caller", onSingleton = true)
    public abstract static class SourceOfCallerNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        Object sourceOfCaller() {
            final int[] frameCount = new int[]{ 0 };

            final Source source = Truffle.getRuntime().iterateFrames(frameInstance -> {
                if (frameCount[0] == 2) {
                    return frameInstance.getCallNode().getEncapsulatingSourceSection().getSource();
                } else {
                    frameCount[0] += 1;
                    return null;
                }
            });

            if (source == null) {
                return nil;
            }

            return createString(fromJavaStringNode, RubyLanguage.getPath(source), Encodings.UTF_8);
        }

    }

    @CoreMethod(names = "inner_check_syntax", onSingleton = true, required = 1)
    public abstract static class InnerCheckSyntaxNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object innerCheckSyntax(RubySource source) {
            RubyContext context = getContext();
            RootCallTarget callTarget = context
                    .getCodeLoader()
                    .parse(source, ParserContext.TOP_LEVEL, null, context.getRootLexicalScope(), null);
            RubyRootNode rubyRootNode = RubyRootNode.of(callTarget);
            EmitWarningsNode emitWarningsNode = NodeUtil.findFirstNodeInstance(rubyRootNode, EmitWarningsNode.class);
            if (emitWarningsNode != null) {
                emitWarningsNode.printWarnings(context);
            }
            return nil;
        }

    }

    @CoreMethod(names = "get_option", onSingleton = true, required = 1)
    public abstract static class GetOptionNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization(guards = "libOptionName.isRubyString(this, optionName)", limit = "1")
        Object getOption(Object optionName,
                @Cached RubyStringLibrary libOptionName) {
            final String optionNameString = StringOperations.getJavaString(optionName);
            final OptionDescriptor descriptor = OptionsCatalog.fromName("ruby." + optionNameString);
            if (descriptor == null) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions()
                                .nameError("option not defined: " + optionNameString, nil, optionNameString, this));
            }

            Object value = getContext().getOptions().fromDescriptor(descriptor);
            if (value != null && getContext().isPreInitializing()) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().runtimeError(
                                "Truffle::Boot.get_option() should not be called during pre-initialization as context options might change at runtime.\n" +
                                        "Use Truffle::Boot.{delay,redo} to delay such a check to runtime, or make the option a language option.",
                                this));
            }

            if (value == null) {
                value = getLanguage().options.fromDescriptor(descriptor);
            }

            if (value instanceof Boolean || value instanceof Integer) {
                return value;
            } else if (value instanceof Enum) {
                return getSymbol(value.toString());
            } else if (value instanceof String) {
                return createString(fromJavaStringNode, (String) value, Encodings.UTF_8);
            } else if (value instanceof String[]) {
                return toRubyArray((String[]) value);
            } else {
                throw CompilerDirectives
                        .shouldNotReachHere("Unknown value for option " + optionNameString + ": " + value);
            }
        }

        private RubyArray toRubyArray(String[] strings) {
            final Object[] objects = new Object[strings.length];
            for (int n = 0; n < strings.length; n++) {
                objects[n] = createString(fromJavaStringNode, strings[n], Encodings.UTF_8);
            }
            return createArray(objects);
        }

    }

    @CoreMethod(names = "print_time_metric", onSingleton = true, required = 1)
    public abstract static class PrintTimeMetricNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object printTimeMetric(RubySymbol name) {
            Metrics.printTime(name.getString());
            return nil;
        }

    }

    @CoreMethod(names = "single_threaded?", onSingleton = true)
    public abstract static class SingleThreadedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean singleThreaded() {
            return getContext().getOptions().SINGLE_THREADED;
        }

    }

    @CoreMethod(names = "toolchain_executable", onSingleton = true, required = 1)
    public abstract static class ToolchainExecutableNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object toolchainExecutable(RubySymbol executable,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final String name = executable.getString();
            final Toolchain toolchain = getToolchain(getContext(), this);
            final TruffleFile path = toolchain.getToolPath(name);
            if (path != null) {
                return createString(fromJavaStringNode, path.getPath(), Encodings.UTF_8);
            } else {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("Toolchain executable " + name + " not found", this));
            }
        }

    }

    @CoreMethod(names = "toolchain_paths", onSingleton = true, required = 1)
    public abstract static class ToolchainPathsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object toolchainPaths(RubySymbol pathName,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final String name = pathName.getString();
            final Toolchain toolchain = getToolchain(getContext(), this);
            final List<TruffleFile> paths = toolchain.getPaths(name);
            if (paths != null) {
                String path = StringUtils.join(paths.toArray(), File.pathSeparator);
                return createString(fromJavaStringNode, path, Encodings.UTF_8);
            } else {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("Toolchain path " + name + " not found", this));
            }
        }

    }

    private static Toolchain getToolchain(RubyContext context, Node currentNode) {
        final LanguageInfo llvmInfo = context.getEnv().getInternalLanguages().get("llvm");
        if (llvmInfo == null) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError(
                            "Could not find Sulong in internal languages",
                            currentNode));
        }
        final Toolchain toolchain = context.getEnv().lookup(llvmInfo, Toolchain.class);
        if (toolchain == null) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError("Could not find the LLVM Toolchain", currentNode));
        }
        return toolchain;
    }

    @CoreMethod(names = "read_abi_version", onSingleton = true)
    public abstract static class ReadABIVersionNode extends CoreMethodNode {

        private static final String ABI_VERSION_FILE = "lib/cext/include/truffleruby/truffleruby-abi-version.h";

        @TruffleBoundary
        @Specialization
        RubyString basicABIVersion() {
            TruffleFile file = getLanguage().getRubyHomeTruffleFile().resolve(ABI_VERSION_FILE);
            byte[] bytes;
            try {
                bytes = file.readAllBytes();
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }

            int startQuote = ArrayUtils.indexOf(bytes, 0, bytes.length, (byte) '"');
            if (startQuote < 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid contents for " + ABI_VERSION_FILE);
            }
            int start = startQuote + 1;
            int endQuote = ArrayUtils.indexOf(bytes, start, bytes.length, (byte) '"');
            if (endQuote < 0) {
                throw CompilerDirectives.shouldNotReachHere("Invalid contents for " + ABI_VERSION_FILE);
            }

            String basicVersion = new String(bytes, start, endQuote - start, StandardCharsets.US_ASCII);
            return createString(TruffleString.FromJavaStringNode.getUncached(), basicVersion, Encodings.UTF_8);
        }

    }

}
