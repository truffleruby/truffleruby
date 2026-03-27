/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is transliterated from C++ code in Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core;

import java.util.Map.Entry;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToRubyIntegerNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.support.RubyIO;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.SafepointAction;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ThrowException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.platform.Signals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@CoreModule(value = "VMPrimitives", isClass = true)
public abstract class VMPrimitiveNodes {

    @Primitive(name = "vm_catch")
    public abstract static class CatchNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object doCatch(Object tag, RubyProc block,
                @Cached InlinedBranchProfile catchProfile,
                @Cached InlinedConditionProfile matchProfile,
                @Cached ReferenceEqualNode referenceEqualNode,
                @Cached CallBlockNode yieldNode) {
            try {
                return yieldNode.yield(this, block, tag);
            } catch (ThrowException e) {
                catchProfile.enter(this);
                if (matchProfile.profile(this, referenceEqualNode.execute(this, e.getTag(), tag))) {
                    return e.getValue();
                } else {
                    throw e;
                }
            }
        }
    }

    // The hard #exit!
    @Primitive(name = "vm_exit", lowerFixnum = 0)
    public abstract static class VMExitNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object vmExit(int status) {
            throw new ExitException(status, this);
        }

    }

    @Primitive(name = "vm_extended_modules")
    public abstract static class VMExtendedModulesNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object vmExtendedModules(Object object, RubyProc block,
                @Cached MetaClassNode metaClassNode,
                @Cached CallBlockNode yieldNode,
                @Cached InlinedConditionProfile isSingletonProfile) {
            final RubyClass metaClass = metaClassNode.execute(this, object);

            if (isSingletonProfile.profile(this, metaClass.isSingleton)) {
                for (RubyModule included : metaClass.fields.prependedAndIncludedModules()) {
                    yieldNode.yield(this, block, included);
                }
            }

            return nil;
        }

    }

    @Primitive(name = "vm_method_is_basic?")
    public abstract static class VMMethodIsBasicNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean vmMethodIsBasic(RubyMethod method) {
            return method.method.isBuiltIn();
        }

    }

    @Primitive(name = "vm_builtin_method?")
    public abstract static class IsBuiltinMethodNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isBuiltinMethod(VirtualFrame frame, Object receiver, RubySymbol name,
                @Cached LookupMethodOnSelfNode lookupMethodNode) {
            final InternalMethod method = lookupMethodNode.lookupIgnoringVisibility(frame, receiver, name.getString());
            if (method == null) {
                return false;
            } else {
                return method.isBuiltIn();
            }
        }

    }

    @Primitive(name = "vm_method_lookup")
    public abstract static class VMMethodLookupNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object vmMethodLookup(VirtualFrame frame, Object receiver, Object name,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LookupMethodOnSelfNode lookupMethodNode) {
            // TODO BJF Sep 14, 2016 Handle private
            final String normalizedName = nameToJavaStringNode.execute(this, name);
            InternalMethod method = lookupMethodNode.lookupIgnoringVisibility(frame, receiver, normalizedName);
            if (method == null) {
                return nil;
            }
            final RubyMethod instance = new RubyMethod(
                    coreLibrary().methodClass,
                    getLanguage().methodShape,
                    receiver,
                    method);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @Primitive(name = "vm_raise_exception")
    public abstract static class VMRaiseExceptionNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object vmRaiseException(RubyException exception,
                @Cached InlinedConditionProfile reRaiseProfile) {
            final Backtrace backtrace = exception.backtrace;
            RaiseException raiseException = null;
            if (reRaiseProfile.profile(this,
                    backtrace != null && (raiseException = backtrace.getRaiseException()) != null)) {
                // We need to rethrow the existing RaiseException, otherwise we would lose the
                // TruffleStackTrace stored in it.
                assert raiseException.getException() == exception;

                if (getContext().getOptions().BACKTRACE_ON_RAISE) {
                    getContext().getDefaultBacktraceFormatter().printRubyExceptionOnEnvStderr("raise: ",
                            raiseException);
                }
                throw raiseException;
            } else {
                throw new RaiseException(getContext(), exception);
            }
        }

        @Specialization(guards = "!isRubyException(exception)", limit = "getInteropCacheLimit()")
        static Object foreignException(Object exception,
                @CachedLibrary("exception") InteropLibrary interopLibrary,
                @Cached TranslateInteropExceptionNode translateInteropExceptionNode,
                @Bind Node node) {
            try {
                throw interopLibrary.throwException(exception);
            } catch (UnsupportedMessageException e) {
                throw translateInteropExceptionNode.execute(node, e);
            }
        }

        public static RaiseException reRaiseException(RubyContext context, RubyException exception) {
            final Backtrace backtrace = exception.backtrace;
            if (backtrace != null && backtrace.getRaiseException() != null) {
                // We need to rethrow the existing RaiseException, otherwise we would lose the
                // TruffleStackTrace stored in it.
                throw backtrace.getRaiseException();
            } else {
                throw new RaiseException(context, exception);
            }
        }
    }

    @Primitive(name = "vm_throw")
    public abstract static class ThrowNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object doThrow(Object tag, Object value) {
            throw new ThrowException(tag, value);
        }

    }

    @Primitive(name = "vm_watch_signal", argumentNames = "action")
    public abstract static class VMWatchSignalNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(
                guards = { "libSignalString.isRubyString(this, signalString)", "libAction.isRubyString(this, action)" },
                limit = "1")
        boolean watchSignalString(Object signalString, boolean isRubyDefaultHandler, Object action,
                @Cached @Shared RubyStringLibrary libSignalString,
                @Cached @Exclusive RubyStringLibrary libAction) {
            final String actionString = StringOperations.getJavaString(action);
            final String signalName = StringOperations.getJavaString(signalString);

            switch (actionString) {
                case "DEFAULT":
                    return restoreDefaultHandler(signalName);
                case "SYSTEM_DEFAULT":
                    return restoreSystemHandler(signalName);
                case "IGNORE":
                    return registerIgnoreHandler(signalName);
                default:
                    throw CompilerDirectives.shouldNotReachHere(actionString);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "libSignalString.isRubyString(this, signalString)")
        boolean watchSignalProc(Object signalString, boolean isRubyDefaultHandler, RubyProc proc,
                @Cached @Shared RubyStringLibrary libSignalString) {
            final RubyContext context = getContext();

            if (getLanguage().getCurrentThread() != context.getThreadManager().getRootThread()) {
                // The proc will be executed on the main thread
                SharedObjects.writeBarrier(getLanguage(), proc);
            }

            final String signalName = StringOperations.getJavaString(signalString);

            return registerHandler(signalName, signal -> {
                var rootThread = context.getThreadManager().getRootThread();
                context.getSafepointManager().pauseRubyThreadAndExecute(null,
                        callProcSafepointAction(proc, signal, rootThread));
            }, isRubyDefaultHandler);
        }

        @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
        private static SafepointAction callProcSafepointAction(RubyProc proc, Signal signal, RubyThread rootThread) {
            return new SafepointAction("Handling of signal " + signal, rootThread, true, false) {
                @Override
                public void run(RubyThread rubyThread, Node currentNode) {
                    ProcOperations.rootCall(proc, NoKeywordArgumentsDescriptor.INSTANCE, signal.getNumber());
                }
            };
        }

        @TruffleBoundary
        private boolean restoreDefaultHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "restoring default handler for signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                return Signals.restoreRubyDefaultHandler(getContext(), signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
        }

        @TruffleBoundary
        private boolean restoreSystemHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "restoring system handler for signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.restoreSystemHandler(signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

        @TruffleBoundary
        private boolean registerIgnoreHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "ignoring signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.registerIgnoreHandler(signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

        @TruffleBoundary
        private boolean registerHandler(String signalName, SignalHandler newHandler, boolean isRubyDefaultHandler) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "trapping signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.registerHandler(getContext(), newHandler, signalName, isRubyDefaultHandler);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

    }

    @Primitive(name = "vm_get_config_item")
    public abstract static class VMGetConfigItemNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object get(Object key) {
            final String keyString = StringOperations.getJavaString(key);
            final Object value = getContext().getNativeConfiguration().get(keyString);

            if (value == null) {
                return nil;
            } else {
                return value;
            }
        }

    }

    @Primitive(name = "vm_get_config_section")
    public abstract static class VMGetConfigSectionNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object getSection(Object section, RubyProc block,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                @Cached CallBlockNode yieldNode) {
            for (Entry<String, Object> entry : getContext()
                    .getNativeConfiguration()
                    .getSection(StringOperations.getJavaString(section))) {
                final RubyString key = createString(fromJavaStringNode, entry.getKey(), Encodings.UTF_8); // CR_7BIT
                yieldNode.yield(this, block, key, entry.getValue());
            }

            return nil;
        }

    }

    @Primitive(name = "vm_set_class")
    public abstract static class VMSetClassNode extends PrimitiveArrayArgumentsNode {

        /** Only support it on IO for IO#reopen since this is the only case which needs it */
        @TruffleBoundary
        @Specialization
        RubyIO setClass(RubyIO object, RubyClass newClass) {
            SharedObjects.propagate(getLanguage(), object, newClass);
            synchronized (object) {
                object.setMetaClass(newClass);
            }
            return object;
        }

    }

    @Primitive(name = "vm_dev_urandom_bytes", lowerFixnum = 0)
    public abstract static class VMDevUrandomBytes extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "count >= 0")
        RubyString readRandomBytes(int count,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final byte[] bytes = getContext().getRandomSeedBytes(count);

            return createString(fromByteArrayNode, bytes, Encodings.BINARY);
        }

        @Specialization(guards = "count < 0")
        RubyString negativeCount(int count) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().argumentError(
                            coreStrings().NEGATIVE_STRING_SIZE.createInstance(getContext()), this, null));
        }

    }

    @Primitive(name = "vm_hash_start")
    public abstract static class VMHashStartNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long startHash(long salt) {
            return getContext().getHashing(this).start(salt);
        }

        @Specialization
        long startHashBigNum(RubyBignum salt) {
            return getContext().getHashing(this).start(BigIntegerOps.hashCode(salt));
        }

        @Specialization(guards = "!isRubyNumber(salt)")
        Object startHashNotNumber(Object salt,
                @Cached ToRubyIntegerNode toRubyInteger,
                @Cached InlinedConditionProfile isIntegerProfile,
                @Cached InlinedConditionProfile isLongProfile,
                @Cached InlinedConditionProfile isBignumProfile) {
            Object result = toRubyInteger.execute(this, salt);
            if (isIntegerProfile.profile(this, result instanceof Integer)) {
                return getContext().getHashing(this).start((int) result);
            } else if (isLongProfile.profile(this, result instanceof Long)) {
                return getContext().getHashing(this).start((long) result);
            } else if (isBignumProfile.profile(this, result instanceof RubyBignum)) {
                return getContext().getHashing(this).start(BigIntegerOps.hashCode((RubyBignum) result));
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }

        }
    }

    @Primitive(name = "vm_hash_update")
    public abstract static class VMHashUpdateNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long updateHash(long hash, long value) {
            return Hashing.update(hash, value);
        }

        @Specialization
        long updateHash(long hash, RubyBignum value) {
            return Hashing.update(hash, BigIntegerOps.hashCode(value));
        }

        @Specialization(guards = "!isRubyNumber(value)")
        Object updateHash(long hash, Object value,
                @Cached ToRubyIntegerNode toRubyInteger,
                @Cached InlinedConditionProfile isIntegerProfile,
                @Cached InlinedConditionProfile isLongProfile,
                @Cached InlinedConditionProfile isBignumProfile) {
            Object result = toRubyInteger.execute(this, value);
            if (isIntegerProfile.profile(this, result instanceof Integer)) {
                return Hashing.update(hash, (int) result);
            } else if (isLongProfile.profile(this, result instanceof Long)) {
                return Hashing.update(hash, (long) result);
            } else if (isBignumProfile.profile(this, result instanceof RubyBignum)) {
                return Hashing.update(hash, BigIntegerOps.hashCode((RubyBignum) result));
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }

        }
    }

    @Primitive(name = "vm_hash_end")
    public abstract static class VMHashEndNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long endHash(long hash) {
            return Hashing.end(hash);
        }

    }

    /** Initialize RaiseException, StackOverflowError and related classes eagerly, so StackOverflowError is correctly
     * handled and does not become, e.g., NoClassDefFoundError: Could not initialize class SomeExceptionRelatedClass */
    @Primitive(name = "vm_stack_overflow_error_to_init_classes")
    public abstract static class InitStackOverflowClassesEagerlyNode extends PrimitiveArrayArgumentsNode {

        private static final String MESSAGE = "initStackOverflowClassesEagerly";

        public static boolean ignore(StackOverflowError e) {
            return e.getMessage() == MESSAGE;
        }

        public static boolean ignore(Object exceptionObject) {
            if (!(exceptionObject instanceof RubyException)) {
                return false;
            }
            RubyException rubyException = (RubyException) exceptionObject;
            final Backtrace backtrace = rubyException.backtrace;
            final Throwable throwable = backtrace == null ? null : backtrace.getJavaThrowable();
            return throwable instanceof StackOverflowError && ignore((StackOverflowError) throwable);
        }

        @Specialization
        Object initStackOverflowClassesEagerly() {
            final StackOverflowError stackOverflowError = new StackOverflowError("initStackOverflowClassesEagerly");
            TruffleStackTrace.fillIn(stackOverflowError);
            throw stackOverflowError;
        }
    }

    @Primitive(name = "should_not_reach_here")
    public abstract static class ShouldNotReachHereNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "libString.isRubyString(this, message)", limit = "1")
        Object shouldNotReachHere(Object message,
                @Cached RubyStringLibrary libString) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(StringOperations.getJavaString(message));
        }

    }

    @Primitive(name = "vm_java_version")
    public abstract static class VMJavaVersionNode extends PrimitiveArrayArgumentsNode {

        private static final int JAVA_SPECIFICATION_VERSION = getJavaSpecificationVersion();

        private static int getJavaSpecificationVersion() {
            String value = System.getProperty("java.specification.version");
            if (value.startsWith("1.")) {
                value = value.substring(2);
            }
            return Integer.parseInt(value);
        }

        @Specialization
        int javaVersion() {
            return JAVA_SPECIFICATION_VERSION;
        }

    }

    @Primitive(name = "arguments")
    public abstract static class ArgumentsNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyArray arguments(VirtualFrame frame) {
            return createArray(RubyArguments.getRawArguments(frame));
        }
    }

    @Primitive(name = "arguments_descriptor")
    public abstract static class ArgumentsDescriptorNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyArray argumentsDescriptor(VirtualFrame frame) {
            return descriptorToArray(RubyArguments.getDescriptor(frame));
        }

        @TruffleBoundary
        private RubyArray descriptorToArray(ArgumentsDescriptor descriptor) {
            if (descriptor == NoKeywordArgumentsDescriptor.INSTANCE) {
                return createEmptyArray();
            } else if (descriptor instanceof KeywordArgumentsDescriptor keywordArgumentsDescriptor) {
                var keywords = keywordArgumentsDescriptor.getKeywords();

                Object[] array = new Object[1 + keywords.length];
                array[0] = getSymbol("keywords");
                for (int i = 0; i < keywords.length; i++) {
                    array[i + 1] = getSymbol(keywords[i]);
                }

                return createArray(array);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Primitive(name = "vm_native_argv")
    public abstract static class VMNativeArgvNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        long argv() {
            return getContext().nativeArgv;
        }
    }

    @Primitive(name = "vm_native_argv_length")
    public abstract static class VMNativeArgvLengthNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        long argvLength() {
            long nativeArgvLength = getContext().nativeArgvLength;
            if (nativeArgvLength != -1L) {
                return nativeArgvLength;
            }

            int argc = getContext().nativeArgc;
            Pointer argv = new Pointer(getContext(), getContext().nativeArgv, argc * Pointer.SIZE);
            Pointer first = argv.readPointer(getContext(), 0);
            Pointer last = argv.readPointer(getContext(), (argc - 1) * Pointer.SIZE);
            long lastByte = last.getAddress() + last.findNullByte(getContext(), InteropLibrary.getUncached(), 0);
            nativeArgvLength = lastByte - first.getAddress();

            getContext().nativeArgvLength = nativeArgvLength;
            return nativeArgvLength;
        }
    }

    @Primitive(name = "vm_single_context?")
    public abstract static class VMSingleContext extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean singleContext() {
            return isSingleContext();
        }
    }

    @Primitive(name = "vm_splitting_enabled?")
    public abstract static class VMSplittingEnabledNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean isSplittingEnabled() {
            return getContext().getCoreLibrary().isSplittingEnabled();
        }
    }

}
