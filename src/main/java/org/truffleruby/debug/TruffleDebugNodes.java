/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.collections.Pair;
import org.prism.ParseResult;
import org.truffleruby.Layouts;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.RubyHandle;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.cast.ToCallTargetNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.debug.TruffleDebugNodes.ForeignArrayNode.ForeignArray;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.interop.BoxedValue;
import org.truffleruby.interop.FromJavaStringNode;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.ByteBasedCharSequence;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.shared.IsSharedNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.utilities.TriState;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.parser.YARPTranslatorDriver;
import org.truffleruby.shared.TruffleRuby;

@CoreModule("Truffle::Debug")
public abstract class TruffleDebugNodes {

    @CoreMethod(names = "print", onSingleton = true, required = 1)
    public abstract static class DebugPrintNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object debugPrint(Object string,
                @Cached RubyStringLibrary strings) {
            final String javaString;
            if (strings.isRubyString(this, string)) {
                javaString = StringOperations.getJavaString(string);
            } else {
                javaString = string.toString();
            }

            getContext().getEnvErrStream().println(javaString);
            return nil;
        }

    }

    @CoreMethod(names = "tstring_to_debug_string", onSingleton = true, required = 1)
    public abstract static class TStringToDebugPrintNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(node, string)", limit = "1")
        static RubyString toStringDebug(Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return createString(node, fromJavaStringNode, strings.getTString(node, string).toStringDebug(),
                    Encodings.US_ASCII);
        }
    }

    @CoreMethod(names = "flatten_string", onSingleton = true, required = 1)
    public abstract static class FlattenStringNode extends CoreMethodArrayArgumentsNode {
        // Also flattens the original String, but that one might still have an offset
        @TruffleBoundary
        @Specialization(guards = "libString.isRubyString(node, string)", limit = "1")
        static RubyString flattenString(Object string,
                @Bind Node node,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached RubyStringLibrary libString) {
            final RubyEncoding rubyEncoding = libString.getEncoding(node, string);
            var tstring = libString.getTString(node, string);
            // Use GetInternalByteArrayNode as a way to flatten the TruffleString.
            // Ensure the result has offset = 0 and length = byte[].length for image build time checks
            byte[] byteArray = TStringUtils.getBytesOrCopy(tstring, rubyEncoding);
            return createString(node, fromByteArrayNode, byteArray, rubyEncoding);
        }
    }

    @CoreMethod(names = "break_handle", onSingleton = true, required = 2, needsBlock = true, split = Split.NEVER,
            lowerFixnum = 2)
    public abstract static class BreakNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, file)", limit = "1")
        RubyHandle setBreak(Object file, int line, RubyProc block,
                @Cached RubyStringLibrary strings) {
            final String fileString = StringOperations.getJavaString(file);

            final SourceSectionFilter filter = SourceSectionFilter
                    .newBuilder()
                    .mimeTypeIs(RubyLanguage.MIME_TYPES)
                    .sourceIs(source -> source != null && getLanguage().getSourcePath(source).equals(fileString))
                    .lineIs(line)
                    .tagIs(StandardTags.StatementTag.class)
                    .build();

            final EventBinding<?> breakpoint = getContext().getInstrumenter().attachExecutionEventFactory(
                    filter,
                    eventContext -> new ExecutionEventNode() {

                        @Child private CallBlockNode yieldNode = CallBlockNode.create();

                        @Override
                        protected void onEnter(VirtualFrame frame) {
                            yieldNode.yieldCached(
                                    block,
                                    BindingNodes.createBinding(
                                            getContext(),
                                            getLanguage(),
                                            frame.materialize(),
                                            eventContext.getInstrumentedSourceSection()));
                        }

                    });

            final RubyHandle instance = new RubyHandle(
                    coreLibrary().handleClass,
                    getLanguage().handleShape,
                    breakpoint);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "remove_handle", onSingleton = true, required = 1)
    public abstract static class RemoveNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object remove(RubyHandle handle) {
            ((EventBinding<?>) handle.object).dispose();
            return nil;
        }

    }

    @CoreMethod(names = "java_class_of", onSingleton = true, required = 1)
    public abstract static class JavaClassOfNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyString javaClassOf(Object value) {
            return createString(fromJavaStringNode, value.getClass().getSimpleName(), Encodings.UTF_8);
        }

    }

    @CoreMethod(names = "print_backtrace", onSingleton = true)
    public abstract static class PrintBacktraceNode extends CoreMethodNode {

        @TruffleBoundary
        @Specialization
        Object printBacktrace() {
            getContext().getDefaultBacktraceFormatter().printBacktraceOnEnvStderr(this);
            return nil;
        }

    }

    @CoreMethod(names = "parse_ast", onSingleton = true, required = 1)
    public abstract static class ParseASTNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(node, code)", limit = "1")
        static Object ast(Object code,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var codeString = new TStringWithEncoding(StringOperations.asTruffleStringUncached(code),
                    RubyStringLibrary.getUncached().getEncoding(node, code));

            var rubySource = createRubySource(codeString);
            var parseResult = getParseResult(getLanguage(node), rubySource);
            if (parseResult.errors.length > 0) {
                return createString(node, fromJavaStringNode, "SyntaxError: " + parseResult.errors[0].message,
                        Encodings.UTF_8);
            } else {
                var ast = parseResult.value;
                return createString(node, fromJavaStringNode, ast.toString(), Encodings.UTF_8);
            }
        }

        private static RubySource createRubySource(TStringWithEncoding code) {
            String name = "<parse_ast>";
            var source = Source.newBuilder("ruby", new ByteBasedCharSequence(code), name).build();
            return new RubySource(source, name);
        }

        private static ParseResult getParseResult(RubyLanguage language, RubySource rubySource) {
            String sourcePath = rubySource.getSourcePath(language).intern();

            return YARPTranslatorDriver.parseToYARPAST(rubySource, sourcePath, rubySource.getBytes(),
                    Collections.emptyList(), language.options.FROZEN_STRING_LITERALS, null,
                    ParserContext.TOP_LEVEL_FIRST);
        }
    }

    @CoreMethod(names = "profile_translator", onSingleton = true, required = 2, lowerFixnum = 2)
    public abstract static class ProfileTranslatorNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object profileTranslator(Object code, int repeat) {
            var codeString = new TStringWithEncoding(StringOperations.asTruffleStringUncached(code),
                    RubyStringLibrary.getUncached().getEncoding(this, code));

            var rubySource = ParseASTNode.createRubySource(codeString);
            var parseResult = ParseASTNode.getParseResult(getLanguage(), rubySource);

            var translator = new YARPTranslatorDriver(getContext());

            for (int i = 0; i < repeat; i++) {
                translator.parse(rubySource, ParserContext.TOP_LEVEL, null, null, getContext().getRootLexicalScope(),
                        this, parseResult);
            }

            return nil;
        }
    }

    @CoreMethod(names = "ast", onSingleton = true, required = 1)
    public abstract static class ASTNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object ast(Object executable,
                @Cached ToCallTargetNode toCallTargetNode) {
            final RootCallTarget callTarget = toCallTargetNode.execute(this, executable);
            return ast(callTarget.getRootNode());
        }

        private Object ast(Node node) {
            if (node == null) {
                return nil;
            }

            final List<Object> array = new ArrayList<>();

            array.add(getSymbol(node.getClass().getSimpleName()));

            for (Node child : node.getChildren()) {
                array.add(ast(child));
            }

            return createArray(array.toArray());
        }
    }

    @CoreMethod(names = "print_ast", onSingleton = true, required = 1)
    public abstract static class PrintASTNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object printAST(Object executable,
                @Cached ToCallTargetNode toCallTargetNode) {
            final RootCallTarget callTarget = toCallTargetNode.execute(this, executable);
            NodeUtil.printCompactTree(getContext().getEnvErrStream(), callTarget.getRootNode());
            return nil;
        }
    }

    @CoreMethod(names = "print_source_sections", onSingleton = true, required = 1)
    public abstract static class PrintSourceSectionsNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object printSourceSections(Object executable,
                @Cached ToCallTargetNode toCallTargetNode) {
            final RootCallTarget callTarget = toCallTargetNode.execute(this, executable);
            NodeUtil.printSourceAttributionTree(getContext().getEnvErrStream(), callTarget.getRootNode());
            return nil;
        }
    }

    @CoreMethod(names = "ast_size", onSingleton = true, required = 1)
    public abstract static class ASTSizeNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int astSize(Object executable,
                @Cached ToCallTargetNode toCallTargetNode) {
            final RootCallTarget callTarget = toCallTargetNode.execute(this, executable);
            return NodeUtil.countNodes(callTarget.getRootNode());
        }
    }

    @CoreMethod(names = "shape", onSingleton = true, required = 1)
    public abstract static class ShapeNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyString shape(RubyDynamicObject object) {
            return createString(fromJavaStringNode, object.getShape().toString(), Encodings.UTF_8);
        }

    }

    @CoreMethod(names = "array_storage", onSingleton = true, required = 1)
    public abstract static class ArrayStorageNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyString arrayStorage(RubyArray array) {
            String storage = ArrayStoreLibrary.getUncached().toString(array.getStore());
            return createString(fromJavaStringNode, storage, Encodings.US_ASCII);
        }

    }

    @CoreMethod(names = "array_capacity", onSingleton = true, required = 1)
    @ImportStatic(ArrayGuards.class)
    public abstract static class ArrayCapacityNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "storageStrategyLimit()")
        long arrayStorage(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return stores.capacity(store);
        }

    }

    @CoreMethod(names = "hash_storage", onSingleton = true, required = 1)
    public abstract static class HashStorageNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyString hashStorage(RubyHash hash) {
            Object store = hash.store;
            String storage = store == null ? "null" : store.getClass().toString();
            return createString(fromJavaStringNode, storage, Encodings.US_ASCII);
        }

    }

    @CoreMethod(names = "shared?", onSingleton = true, required = 1)
    @ImportStatic(SharedObjects.class)
    public abstract static class IsSharedCoreMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isShared(RubyDynamicObject object,
                @Cached IsSharedNode isSharedNode) {
            return isSharedNode.execute(this, object);
        }

        @Specialization
        boolean isSharedImmutable(ImmutableRubyObject object) {
            return true;
        }
    }

    @CoreMethod(names = "log_warning", onSingleton = true, required = 1)
    public abstract static class LogWarningNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object logWarning(Object value,
                @Cached ToJavaStringNode toJavaStringNode) {
            warning(toJavaStringNode.execute(this, value));
            return nil;
        }

        @TruffleBoundary
        static void warning(String message) {
            RubyLanguage.LOGGER.warning(message);
        }

    }

    @CoreMethod(names = "log_info", onSingleton = true, required = 1)
    public abstract static class LogInfoNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object logInfo(Object value,
                @Cached ToJavaStringNode toJavaStringNode) {
            info(toJavaStringNode.execute(this, value));
            return nil;
        }

        @TruffleBoundary
        static void info(String message) {
            RubyLanguage.LOGGER.info(message);
        }

    }

    @CoreMethod(names = "log_config", onSingleton = true, required = 1)
    public abstract static class LogConfigNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object logConfig(Object value,
                @Cached ToJavaStringNode toJavaStringNode) {
            config(toJavaStringNode.execute(this, value));
            return nil;
        }

        @TruffleBoundary
        static void config(String message) {
            RubyLanguage.LOGGER.config(message);
        }

    }

    @CoreMethod(names = "throw_java_exception", onSingleton = true, required = 1)
    public abstract static class ThrowJavaExceptionNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, message)", limit = "1")
        Object throwJavaException(Object message,
                @Cached RubyStringLibrary strings) {
            callingMethod(StringOperations.getJavaString(message));
            return nil;
        }

        // These two named methods makes it easy to test that the backtrace for a Java exception is what we expect

        private static void callingMethod(String message) {
            throwingMethod(message);
        }

        private static void throwingMethod(String message) {
            throw new RuntimeException(message);
        }

    }

    @CoreMethod(names = "throw_java_exception_with_cause", onSingleton = true, required = 1)
    public abstract static class ThrowJavaExceptionWithCauseNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, message)", limit = "1")
        Object throwJavaExceptionWithCause(Object message,
                @Cached RubyStringLibrary strings) {
            var cause2 = new RuntimeException("cause 2");
            var cause1 = new RuntimeException("cause 1", cause2);
            TruffleStackTrace.fillIn(cause2);
            TruffleStackTrace.fillIn(cause1);
            throw new RuntimeException(StringOperations.getJavaString(message), cause1);
        }

    }

    @CoreMethod(names = "throw_assertion_error", onSingleton = true, required = 1)
    public abstract static class ThrowAssertionErrorNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, message)", limit = "1")
        Object throwAssertionError(Object message,
                @Cached RubyStringLibrary strings) {
            throw new AssertionError(StringOperations.getJavaString(message));
        }

    }

    @CoreMethod(names = "assert", onSingleton = true, required = 1)
    public abstract static class AssertNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object doAssert(boolean condition) {
            assert condition;
            return nil;
        }
    }

    @Primitive(name = "assert")
    public abstract static class AssertPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object doAssert(boolean condition) {
            assert condition;
            return nil;
        }
    }

    @CoreMethod(names = "java_class", onSingleton = true)
    public abstract static class JavaClassNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object javaObject() {
            return getContext().getEnv().asGuestValue(BigInteger.class);
        }

    }

    @CoreMethod(names = "java_object", onSingleton = true)
    public abstract static class JavaObjectNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object javaObject() {
            return getContext().getEnv().asGuestValue(new BigInteger("14"));
        }

    }

    @CoreMethod(names = "java_null", onSingleton = true)
    public abstract static class JavaNullNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object javaNull() {
            return getContext().getEnv().asGuestValue(null);
        }

    }

    @CoreMethod(names = "java_character", onSingleton = true)
    public abstract static class JavaCharacterNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Character javaCharacter() {
            return 'C';
        }
    }

    @CoreMethod(names = "foreign_null", onSingleton = true)
    public abstract static class ForeignNullNode extends CoreMethodArrayArgumentsNode {

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignNull implements TruffleObject {

            @ExportMessage
            protected boolean isNull() {
                return true;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign null]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignNull() {
            return new ForeignNull();
        }

    }

    @CoreMethod(names = "foreign_pointer", required = 1, onSingleton = true)
    public abstract static class ForeignPointerNode extends CoreMethodArrayArgumentsNode {

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignPointer implements TruffleObject {

            private final long address;

            public ForeignPointer(long address) {
                this.address = address;
            }

            @ExportMessage
            protected boolean isPointer() {
                return true;
            }

            @ExportMessage
            protected long asPointer() {
                return address;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign pointer]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignPointer(long address) {
            return new ForeignPointer(address);
        }

    }

    @CoreMethod(names = "foreign_object", onSingleton = true)
    public abstract static class ForeignObjectNode extends CoreMethodArrayArgumentsNode {

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignObject implements TruffleObject {
            @ExportMessage
            protected TriState isIdenticalOrUndefined(Object other) {
                return other instanceof ForeignObject ? TriState.valueOf(this == other) : TriState.UNDEFINED;
            }

            @ExportMessage
            protected int identityHashCode() {
                return System.identityHashCode(this);
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign object]";
            }
        }

        @Specialization
        Object foreignObject() {
            return new ForeignObject();
        }

    }

    @CoreMethod(names = "foreign_object_with_members", onSingleton = true)
    public abstract static class ForeignObjectWithMembersNode extends CoreMethodArrayArgumentsNode {

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignObjectWithMembers implements TruffleObject {

            private final Map<String, Object> map;

            public ForeignObjectWithMembers() {
                map = new HashMap<>();
                map.put("a", 1);
                map.put("b", 2);
                map.put("c", 3);
            }

            @ExportMessage
            protected boolean hasMembers() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            protected Object getMembers(boolean includeInternal) {
                return new ForeignArray("a", "b", "c", "method1", "method2");
            }

            @TruffleBoundary
            @ExportMessage
            protected boolean isMemberReadable(String member) {
                return map.containsKey(member) || isMemberInvocable(member);
            }

            @TruffleBoundary
            @ExportMessage
            protected boolean isMemberInvocable(String member) {
                return "method1".equals(member) || "method2".equals(member);
            }

            @TruffleBoundary
            @ExportMessage
            protected Object readMember(String member) throws UnknownIdentifierException {
                if (member.equals("method1")) {
                    return new ForeignExecutableNode.ForeignExecutable(42);
                } else if (member.equals("method2")) {
                    return new ForeignExecutableNode.ForeignExecutable(44);
                }

                final Object value = map.get(member);
                if (value == null) {
                    throw UnknownIdentifierException.create(member);
                }
                return value;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object invokeMember(String member, Object[] arguments) throws UnsupportedMessageException,
                    ArityException, UnknownIdentifierException, UnsupportedTypeException {
                if (!isMemberInvocable(member)) {
                    throw UnknownIdentifierException.create(member);
                }

                return InteropLibrary.getUncached().execute(readMember("method1"));
            }


            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign object with members]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignObjectWithMembers() {
            return new ForeignObjectWithMembers();
        }

    }

    @CoreMethod(names = "foreign_array", onSingleton = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class ForeignArrayNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static class ForeignArray implements TruffleObject {

            private final Object[] array;

            public ForeignArray(Object... values) {
                this.array = values;
            }

            @ExportMessage
            protected boolean hasArrayElements() {
                return true;
            }

            @ExportMessage(name = "isArrayElementReadable")
            @ExportMessage(name = "isArrayElementModifiable")
            protected boolean isArrayElement(long index) {
                return 0 >= index && index < array.length;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object readArrayElement(long index) throws InvalidArrayIndexException {
                try {
                    return array[(int) index];
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw InvalidArrayIndexException.create(index);
                }
            }

            @TruffleBoundary
            @ExportMessage
            protected void writeArrayElement(long index, Object value) throws InvalidArrayIndexException {
                try {
                    array[(int) index] = value;
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw InvalidArrayIndexException.create(index);
                }
            }

            @ExportMessage
            protected final boolean isArrayElementInsertable(long index) {
                return false;
            }

            @ExportMessage
            protected long getArraySize() {
                return array.length;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign array]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignArray() {
            return new ForeignArray(1, 2, 3);
        }
    }

    @CoreMethod(names = "foreign_pointer_array", onSingleton = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class ForeignPointerArrayNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignPointerArray extends ForeignArray {
            public ForeignPointerArray(Object... values) {
                super(values);
            }

            @ExportMessage
            protected boolean isPointer() {
                return true;
            }

            @ExportMessage
            protected long asPointer() {
                return 0; // shouldn't be used
            }

            @Override
            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign pointer array]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignPointerArray() {
            return new ForeignPointerArray(1, 2, 3);
        }
    }

    @CoreMethod(names = "foreign_iterator", onSingleton = true)
    public abstract static class ForeignIteratorNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignIterator implements TruffleObject {
            final int[] values = { 1, 2, 3 };
            int index = 0;

            @ExportMessage
            protected boolean isIterator() {
                return true;
            }

            @ExportMessage
            protected boolean hasIteratorNextElement() {
                return index < values.length;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object getIteratorNextElement() throws StopIterationException {
                if (hasIteratorNextElement()) {
                    Object value = values[index];
                    index++;
                    return value;
                } else {
                    throw StopIterationException.create();
                }
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign iterator]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignIterator() {
            return new ForeignIterator();
        }
    }

    @CoreMethod(names = "foreign_iterable", onSingleton = true)
    public abstract static class ForeignIterableNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignIterable implements TruffleObject {
            @ExportMessage
            protected boolean hasIterator() {
                return true;
            }

            @ExportMessage
            protected Object getIterator() {
                return new ForeignIteratorNode.ForeignIterator();
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign iterable]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignIterable() {
            return new ForeignIterable();
        }
    }

    @CoreMethod(names = "foreign_iterator_iterable", onSingleton = true)
    public abstract static class ForeignIteratorIterableNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignIteratorIterable implements TruffleObject {
            final int[] values = { 1, 2, 3 };
            int index = 0;

            @ExportMessage
            protected boolean hasIterator() {
                return true;
            }

            @ExportMessage
            protected Object getIterator() {
                return this;
            }

            @ExportMessage
            protected boolean isIterator() {
                return true;
            }

            @ExportMessage
            protected boolean hasIteratorNextElement() {
                return index < values.length;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object getIteratorNextElement() throws StopIterationException {
                if (hasIteratorNextElement()) {
                    Object value = values[index];
                    index++;
                    return value;
                } else {
                    throw StopIterationException.create();
                }
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign iterator iterable]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignIteratorIterable() {
            return new ForeignIteratorIterable();
        }
    }

    @CoreMethod(names = "foreign_hash", onSingleton = true)
    public abstract static class ForeignHashNode extends CoreMethodArrayArgumentsNode {

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignHashEntriesIterator implements TruffleObject {
            final ForeignHash foreignHash;
            int index = 0;

            public ForeignHashEntriesIterator(ForeignHash foreignHash) {
                this.foreignHash = foreignHash;
            }

            @ExportMessage
            protected boolean isIterator() {
                return true;
            }

            @ExportMessage
            protected boolean hasIteratorNextElement() {
                return index < 2;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object getIteratorNextElement() throws StopIterationException {
                if (index == 0) {
                    index++;
                    return new ForeignArray(foreignHash.key1, foreignHash.value1);
                } else if (index == 1) {
                    index++;
                    return new ForeignArray(foreignHash.key2, foreignHash.value2);
                } else {
                    throw StopIterationException.create();
                }
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign hash entries iterator]";
            }
        }

        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignHash implements TruffleObject {
            private final RubySymbol key1;
            private final int value1;
            private final RubySymbol key2;
            private final int value2;

            public ForeignHash(RubySymbol key1, int value1, RubySymbol key2, int value2) {
                this.key1 = key1;
                this.value1 = value1;
                this.key2 = key2;
                this.value2 = value2;
            }

            @ExportMessage
            protected boolean hasHashEntries() {
                return true;
            }

            @ExportMessage
            protected long getHashSize() {
                return 2;
            }

            @TruffleBoundary
            @ExportMessage
            protected boolean isHashEntryReadable(Object key) {
                return key == key1 || key == key2;
            }

            @TruffleBoundary
            @ExportMessage
            protected Object readHashValue(Object key) throws UnknownKeyException {
                if (key == key1) {
                    return value1;
                } else if (key == key2) {
                    return value2;
                } else {
                    throw UnknownKeyException.create(key);
                }
            }

            @ExportMessage
            protected Object getHashEntriesIterator() {
                return new ForeignHashEntriesIterator(this);
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign hash]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignHash() {
            return new ForeignHash(getSymbol("a"), 1, getSymbol("b"), 2);
        }
    }

    @CoreMethod(names = "foreign_executable", required = 1, onSingleton = true)
    public abstract static class ForeignExecutableNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignExecutable implements TruffleObject {

            private final Object value;

            public ForeignExecutable(Object value) {
                this.value = value;
            }

            @ExportMessage
            protected boolean isExecutable() {
                return true;
            }

            @ExportMessage
            protected Object execute(Object... arguments) {
                return value;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign executable]";
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignExecutable(Object value) {
            return new ForeignExecutable(value);
        }
    }

    @CoreMethod(names = "foreign_identity_function", onSingleton = true)
    public abstract static class ForeignIdentityFunctionNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignIdentityFunction implements TruffleObject {
            @ExportMessage
            protected final boolean isExecutable() {
                return true;
            }

            @ExportMessage
            protected final Object execute(Object[] args) {
                return args[0];
            }
        }

        @TruffleBoundary
        @Specialization
        Object foreignIdentityFunction() {
            return new ForeignIdentityFunction();
        }
    }

    @CoreMethod(names = "foreign_string", onSingleton = true, required = 1)
    public abstract static class ForeignStringNode extends CoreMethodArrayArgumentsNode {
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignString implements TruffleObject {

            private final String string;

            public ForeignString(String string) {
                this.string = string;
            }

            @ExportMessage
            protected boolean isString() {
                return true;
            }

            @ExportMessage
            protected String asString() {
                return string;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign string]";
            }
        }

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, string)", limit = "1")
        Object foreignString(Object string,
                @Cached RubyStringLibrary strings) {
            return new ForeignString(StringOperations.getJavaString(string));
        }
    }

    @CoreMethod(names = "foreign_exception", required = 1, onSingleton = true)
    public abstract static class ForeignExceptionNode extends CoreMethodArrayArgumentsNode {
        @SuppressWarnings("serial")
        @ExportLibrary(InteropLibrary.class)
        public static final class ForeignException extends AbstractTruffleException {

            public ForeignException(String message) {
                super(message);
            }

            @ExportMessage
            protected boolean isException() {
                return true;
            }

            @ExportMessage
            public RuntimeException throwException() {
                throw this;
            }

            @ExportMessage
            protected String toDisplayString(boolean allowSideEffects) {
                return "[foreign exception]";
            }
        }

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, message)", limit = "1")
        Object foreignException(Object message,
                @Cached RubyStringLibrary strings) {
            return new ForeignException(StringOperations.getJavaString(message));
        }
    }

    @CoreMethod(names = "foreign_boxed_value", onSingleton = true, required = 1)
    public abstract static class ForeignBoxedNumberNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object foreignBoxedNumber(Object number) {
            return new BoxedValue(number);
        }
    }

    @CoreMethod(names = "long", onSingleton = true, required = 1)
    public abstract static class LongNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long asLong(int value) {
            return value;
        }

        @Specialization
        long asLong(long value) {
            return value;
        }

    }

    @CoreMethod(names = "associated", onSingleton = true, required = 1)
    public abstract static class AssociatedNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray associated(RubyString string) {
            final DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();
            Pointer[] associated = (Pointer[]) objectLibrary.getOrDefault(string, Layouts.ASSOCIATED_IDENTIFIER, null);

            if (associated == null) {
                associated = Pointer.EMPTY_ARRAY;
            }

            final long[] associatedValues = new long[associated.length];

            for (int n = 0; n < associated.length; n++) {
                associatedValues[n] = associated[n].getAddress();
            }

            return ArrayHelpers.createArray(getContext(), getLanguage(), associatedValues);
        }

        @TruffleBoundary
        @Specialization
        RubyArray associated(ImmutableRubyString string) {
            return ArrayHelpers.createEmptyArray(getContext(), getLanguage());
        }

    }

    @CoreMethod(names = "drain_finalization_queue", onSingleton = true)
    public abstract static class DrainFinalizationQueueNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object drainFinalizationQueue() {
            getContext().getFinalizationService().drainFinalizationQueue(getContext());
            return nil;
        }

    }

    @Primitive(name = "frame_declaration_context_to_string")
    public abstract static class FrameDeclarationContextToStringNode extends PrimitiveArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @Specialization
        RubyString getDeclarationContextToString(VirtualFrame frame) {
            final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(frame);
            return createString(fromJavaStringNode, declarationContext.toString(), Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "get_frame_bindings", onSingleton = true)
    public abstract static class IterateFrameBindingsNode extends CoreMethodArrayArgumentsNode {

        /** This logic should be kept in sync with
         * {@link org.truffleruby.language.backtrace.BacktraceFormatter#nextAvailableSourceSection(TruffleStackTraceElement[], int) } */
        @TruffleBoundary
        @Specialization
        RubyArray getFrameBindings() {
            var stack = new ArrayDeque<Pair<MaterializedFrame, SourceSection>>();

            getContext().getCallStack().iterateFrameBindings(5, frameInstance -> {
                final RootNode rootNode = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
                var callNode = frameInstance.getCallNode();
                var encapsulatingSourceSection = callNode == null ? null : callNode.getEncapsulatingSourceSection();
                if (rootNode instanceof RubyRootNode && BacktraceFormatter.isAvailable(encapsulatingSourceSection)) {
                    final MaterializedFrame frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
                    assert frame.getFrameDescriptor().getDefaultValue() == nil;
                    assert CallStackManager.isRubyFrame(frame);
                    stack.push(Pair.create(frame, encapsulatingSourceSection));
                } else {
                    stack.push(Pair.empty());
                }
                return null;
            });

            while (!stack.isEmpty() && stack.peek().getRight() == null) {
                stack.pop();
            }

            final List<Object> frameBindings = new ArrayList<>();
            SourceSection lastAvailableSourceSection = null;
            while (!stack.isEmpty()) {
                final Pair<MaterializedFrame, SourceSection> frameAndSource = stack.pop();
                final MaterializedFrame frame = frameAndSource.getLeft();
                final SourceSection source = frameAndSource.getRight();
                if (frame != null) {
                    SourceSection sourceSection;
                    if (source != null) {
                        sourceSection = source;
                        lastAvailableSourceSection = source;
                    } else {
                        sourceSection = lastAvailableSourceSection;
                    }
                    RubyBinding binding = BindingNodes.createBinding(getContext(), getLanguage(), frame, sourceSection);
                    frameBindings.add(binding);
                } else {
                    frameBindings.add(nil);
                }
            }
            Collections.reverse(frameBindings);
            return createArray(frameBindings.toArray());
        }
    }

    @CoreMethod(names = "parse_name", onSingleton = true, required = 1)
    public abstract static class ParseNameNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyString parseName(Object executable,
                @Cached ToCallTargetNode toCallTargetNode,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var rootNode = RubyRootNode.of(toCallTargetNode.execute(this, executable));
            String parseName = rootNode.getSharedMethodInfo().getParseName();
            return createString(fromJavaStringNode, parseName, Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "runtime_name", onSingleton = true, required = 1)
    public abstract static class RuntimeNameNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyString runtimeName(Object executable,
                @Cached ToCallTargetNode toCallTargetNode,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var rootNode = RubyRootNode.of(toCallTargetNode.execute(this, executable));
            String runtimeName = rootNode.getSharedMethodInfo().getRuntimeName();
            return createString(fromJavaStringNode, runtimeName, Encodings.UTF_8);
        }
    }

    /** Creates a Truffle thread which is not {@link ThreadManager#isRubyManagedThread(java.lang.Thread)}}. */
    @CoreMethod(names = "create_polyglot_thread", onSingleton = true, required = 1)
    public abstract static class CreatePolyglotThread extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object createPolyglotThread(Object hostRunnable) {
            Runnable runnable = (Runnable) getContext().getEnv().asHostObject(hostRunnable);
            final Thread thread = getContext().getEnv().newTruffleThreadBuilder(runnable).build();
            return getContext().getEnv().asGuestValue(thread);
        }
    }

    @CoreMethod(names = "primitive_names", onSingleton = true)
    public abstract static class PrimitiveNamesNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyArray primitiveNames() {
            var primitives = getLanguage().primitiveManager.getPrimitiveNames();
            var primitiveNames = ArrayUtils.map(primitives, FromJavaStringNode::executeUncached);
            return createArray(primitiveNames);
        }
    }

    @CoreMethod(names = "cexts_to_native_count", onSingleton = true)
    public abstract static class HandleCreationCountNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long handleCount() {
            return getContext().getValueWrapperManager().totalHandleAllocations();
        }
    }

    @CoreMethod(names = "multithreaded?", onSingleton = true)
    public abstract static class IsMultiThreadedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isMultiThreaded() {
            return getLanguage().isMultiThreaded();
        }
    }

    @CoreMethod(names = "parse_and_dump_truffle_ast", onSingleton = true, required = 4, lowerFixnum = 3)
    public abstract static class ParseAndDumpTruffleASTNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        Object parseAndDump(Object sourceCode, Object focusedNodeClassName, int index, boolean mainScript,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            String nodeClassNameString = StringOperations.getJavaString(focusedNodeClassName);

            var code = new TStringWithEncoding(StringOperations.asTruffleStringUncached(sourceCode),
                    RubyStringLibrary.getUncached().getEncoding(this, sourceCode));

            RubyRootNode rootNode = parse(code, mainScript);
            String output = TruffleASTPrinter.dump(rootNode, nodeClassNameString, index);

            return createString(fromJavaStringNode, output, Encodings.UTF_8);
        }

        private RubyRootNode parse(TStringWithEncoding sourceCode, boolean mainScript) {
            Source source = Source.newBuilder("ruby", new ByteBasedCharSequence(sourceCode), "<parse_ast>").build();
            TranslatorEnvironment.resetTemporaryVariablesIndex();
            var parserContext = mainScript ? ParserContext.TOP_LEVEL_FIRST : ParserContext.TOP_LEVEL;

            final RootCallTarget callTarget = getContext().getCodeLoader().parse(
                    new RubySource(source, source.getName()),
                    parserContext,
                    null,
                    getContext().getRootLexicalScope(),
                    null);

            return RubyRootNode.of(callTarget);
        }
    }

    @CoreMethod(names = "parse_public", onSingleton = true, required = 3)
    @ImportStatic(ArrayGuards.class)
    public abstract static class ParsePublicNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(limit = "storageStrategyLimit()")
        Object parsePublic(Object sourceCode, RubyArray parameters, RubyArray arguments,
                @Bind("parameters.getStore()") Object parametersStore,
                @Bind("arguments.getStore()") Object argumentsStore,
                @CachedLibrary("parametersStore") ArrayStoreLibrary parametersStores,
                @CachedLibrary("argumentsStore") ArrayStoreLibrary argumentsStores) {
            String sourceCodeString = StringOperations.getJavaString(sourceCode);

            String[] names = new String[parameters.size];
            Object[] values = new Object[arguments.size];

            for (int i = 0; i < names.length; i++) {
                Object name = parametersStores.read(parametersStore, i);
                names[i] = StringOperations.getJavaString(name);
            }

            for (int i = 0; i < values.length; i++) {
                values[i] = argumentsStores.read(argumentsStore, i);
            }

            Source source = Source.newBuilder(TruffleRuby.LANGUAGE_ID, sourceCodeString, "parse_public.rb").build();
            var env = getContext().getEnv();

            CallTarget method = env.parsePublic(source, names);
            return method.call(values);
        }
    }

}
