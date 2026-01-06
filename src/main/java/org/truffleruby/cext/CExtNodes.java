/*
 * Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import org.graalvm.shadowed.org.jcodings.Config;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.IntHolder;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.annotations.Split;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.cext.UnwrapNode.UnwrapCArrayNode;
import org.truffleruby.core.MarkingService.ExtensionCallStack;
import org.truffleruby.core.MarkingServiceNodes;
import org.truffleruby.core.MarkingServiceNodes.RunMarkOnExitNode;
import org.truffleruby.core.array.ArrayToObjectArrayNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.cast.FloatToIntegerNode;
import org.truffleruby.core.cast.HashCastNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.exception.ErrnoErrorNode;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.format.BytesResult;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.rbsprintf.RBSprintfCompiler;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.core.module.ModuleNodes.ConstSetUncheckedNode;
import org.truffleruby.core.module.ModuleNodes.SetMethodVisibilityNode;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.mutex.MutexOperations;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringSupport;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.interop.InteropNodes;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.LazyWarningNode;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.WarningNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.constants.GetConstantNode;
import org.truffleruby.language.constants.LookupConstantNode;
import org.truffleruby.language.control.BreakException;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.LocalReturnException;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.RedoException;
import org.truffleruby.language.control.RetryException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ThrowException;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.LiteralCallNode;
import org.truffleruby.language.globals.ReadGlobalVariableNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.supercall.CallSuperMethodNode;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.parser.IdentifierType;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

@CoreModule("Truffle::CExt")
public abstract class CExtNodes {

    /* These tag values are derived from MRI source and from the Tk gem and are used to represent different control flow
     * states under which code may exit an `rb_protect` block. The fatal tag is defined but I could not find a point
     * where it is assigned, and am not sure if it maps to anything we would use in TruffleRuby. */
    public static final int RUBY_TAG_RETURN = 0x1;
    public static final int RUBY_TAG_BREAK = 0x2;
    public static final int RUBY_TAG_NEXT = 0x3;
    public static final int RUBY_TAG_RETRY = 0x4;
    public static final int RUBY_TAG_REDO = 0x5;
    public static final int RUBY_TAG_RAISE = 0x6;
    public static final int RUBY_TAG_THROW = 0x7;

    /** We need up to 4 \0 bytes for UTF-32. Always use 4 for speed rather than checking the encoding min length.
     * Corresponds to TERM_LEN() in MRI. */
    public static final int NATIVE_STRING_TERMINATOR_LENGTH = 4;

    public static Pointer newNativeStringPointer(RubyLanguage language, RubyContext context, int capacity) {
        Pointer pointer = Pointer.mallocAutoRelease(language, context, capacity + NATIVE_STRING_TERMINATOR_LENGTH);
        pointer.writeInt(capacity, 0);
        return pointer;
    }

    public static Pointer newZeroedNativeStringPointer(RubyLanguage language, RubyContext context, int capacity) {
        return Pointer.callocAutoRelease(language, context, capacity + NATIVE_STRING_TERMINATOR_LENGTH);
    }

    private static long getNativeStringCapacity(Pointer pointer) {
        final long nativeBufferSize = pointer.getSize();
        assert nativeBufferSize > 0;
        // Do not count the extra terminator bytes, like MRI.
        return nativeBufferSize - NATIVE_STRING_TERMINATOR_LENGTH;
    }

    @Primitive(name = "call_with_cext_lock_and_frame")
    public abstract static class CallWithCExtLockAndFrameNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object callWithCExtLockAndFrame(
                VirtualFrame frame,
                Object receiver,
                RubyArray argsArray,
                Object specialVariables,
                Object block,
                boolean useCExtLock,
                @CachedLibrary(limit = "getCacheLimit()") InteropLibrary receivers,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode,
                @Cached TranslateInteropExceptionNode translateInteropExceptionNode,
                @Cached InlinedConditionProfile useCExtLockProfile,
                @Cached InlinedConditionProfile ownedProfile,
                @Cached RunMarkOnExitNode runMarksNode) {
            final ExtensionCallStack extensionStack = getLanguage()
                    .getCurrentThread()
                    .getCurrentFiber().extensionCallStack;
            final boolean keywordsGiven = RubyArguments.getDescriptor(frame) instanceof KeywordArgumentsDescriptor;
            extensionStack.push(keywordsGiven, specialVariables, block);
            try {
                final Object[] args = arrayToObjectArrayNode.executeToObjectArray(argsArray);

                final ReentrantLock lock = getContext().getCExtensionsLock();
                final boolean mustLock = useCExtLockProfile.profile(this, useCExtLock) &&
                        !ownedProfile.profile(this, lock.isHeldByCurrentThread());
                if (mustLock) {
                    MutexOperations.lockInternal(getContext(), lock, this);
                }

                try {
                    return InteropNodes.execute(this, receiver, args, receivers, translateInteropExceptionNode);
                } finally {
                    runMarksNode.execute(this, extensionStack);

                    if (mustLock) {
                        MutexOperations.unlockInternal(lock);
                    }
                }

            } finally {
                extensionStack.pop();
            }
        }

        protected int getCacheLimit() {
            return getLanguage().options.DISPATCH_CACHE;
        }
    }

    @Primitive(name = "call_with_cext_lock_and_frame_and_unwrap")
    public abstract static class CallWithCExtLockAndFrameAndUnwrapNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object callWithCExtLockAndFrame(
                VirtualFrame frame,
                Object receiver,
                RubyArray argsArray,
                Object specialVariables,
                Object block,
                boolean useCExtLock,
                @CachedLibrary(limit = "getCacheLimit()") InteropLibrary receivers,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode,
                @Cached TranslateInteropExceptionNode translateInteropExceptionNode,
                @Cached InlinedConditionProfile useCExtLockProfile,
                @Cached InlinedConditionProfile ownedProfile,
                @Cached RunMarkOnExitNode runMarksNode,
                @Cached UnwrapNode unwrapNode) {
            final ExtensionCallStack extensionStack = getLanguage().getCurrentFiber().extensionCallStack;
            final boolean keywordsGiven = RubyArguments.getDescriptor(frame) instanceof KeywordArgumentsDescriptor;
            extensionStack.push(keywordsGiven, specialVariables, block);
            try {
                final Object[] args = arrayToObjectArrayNode.executeToObjectArray(argsArray);

                final ReentrantLock lock = getContext().getCExtensionsLock();
                final boolean mustLock = useCExtLockProfile.profile(this, useCExtLock) &&
                        !ownedProfile.profile(this, lock.isHeldByCurrentThread());
                if (mustLock) {
                    MutexOperations.lockInternal(getContext(), lock, this);
                }

                try {
                    return unwrapNode.execute(this,
                            InteropNodes.execute(this, receiver, args, receivers, translateInteropExceptionNode));
                } finally {
                    runMarksNode.execute(this, extensionStack);

                    if (mustLock) {
                        MutexOperations.unlockInternal(lock);
                    }
                }

            } finally {
                extensionStack.pop();
            }
        }

        protected int getCacheLimit() {
            return getLanguage().options.DISPATCH_CACHE;
        }
    }

    @Primitive(name = "call_with_cext_lock")
    public abstract static class CallWithCExtLockNode extends PrimitiveArrayArgumentsNode {

        public abstract Object execute(Object receiver, RubyArray argsArray, boolean useCExtLock);

        @Specialization
        Object callWithCExtLock(Object receiver, RubyArray argsArray, boolean useCExtLock,
                @CachedLibrary(limit = "getCacheLimit()") InteropLibrary receivers,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode,
                @Cached TranslateInteropExceptionNode translateInteropExceptionNode,
                @Cached InlinedConditionProfile useCExtLockProfile,
                @Cached InlinedConditionProfile ownedProfile) {
            Object[] args = arrayToObjectArrayNode.executeToObjectArray(argsArray);

            final ReentrantLock lock = getContext().getCExtensionsLock();
            final boolean mustLock = useCExtLockProfile.profile(this, useCExtLock) &&
                    !ownedProfile.profile(this, lock.isHeldByCurrentThread());
            if (mustLock) {
                MutexOperations.lockInternal(getContext(), lock, this);
            }

            try {
                return InteropNodes.execute(this, receiver, args, receivers, translateInteropExceptionNode);
            } finally {
                if (mustLock) {
                    MutexOperations.unlockInternal(lock);
                }
            }

        }

        protected int getCacheLimit() {
            return getLanguage().options.DISPATCH_CACHE;
        }
    }

    public abstract static class SendWithoutCExtLockBaseNode extends PrimitiveArrayArgumentsNode {
        public Object sendWithoutCExtLock(VirtualFrame frame, Object receiver, RubySymbol method, Object block,
                ArgumentsDescriptor descriptor, Object[] args,
                DispatchNode dispatchNode, DispatchConfiguration config, InlinedConditionProfile ownedProfile) {
            final ReentrantLock lock = getContext().getCExtensionsLock();
            assert getContext().getOptions().CEXT_LOCK ||
                    !lock.isHeldByCurrentThread() : "lock held with --cexts-lock=false";
            boolean mustUnlock = getContext().getOptions().CEXT_LOCK &&
                    ownedProfile.profile(this, lock.isHeldByCurrentThread());
            if (mustUnlock) {
                MutexOperations.unlockInternal(lock);
            }

            try {
                return dispatchNode.callWithFrameAndBlock(config, frame, receiver, method.getString(), block,
                        descriptor, args);
            } finally {
                if (mustUnlock) {
                    MutexOperations.internalLockEvenWithException(getContext(), lock, this);
                }
            }
        }
    }

    @Primitive(name = "send_without_cext_lock")
    public abstract static class SendWithoutCExtLockNode extends SendWithoutCExtLockBaseNode {
        @Specialization
        Object sendWithoutCExtLock(
                VirtualFrame frame, Object receiver, RubySymbol method, RubyArray argsArray, Object block,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode,
                @Cached DispatchNode dispatchNode,
                @Cached InlinedConditionProfile ownedProfile) {
            final Object[] args = arrayToObjectArrayNode.executeToObjectArray(argsArray);
            return sendWithoutCExtLock(frame, receiver, method, block, NoKeywordArgumentsDescriptor.INSTANCE, args,
                    dispatchNode, PRIVATE, ownedProfile);
        }

    }

    @Primitive(name = "send_argv_without_cext_lock")
    public abstract static class SendARGVWithoutCExtLockNode extends SendWithoutCExtLockBaseNode {
        @Specialization
        Object sendWithoutCExtLock(VirtualFrame frame, Object receiver, RubySymbol method, Object argv, Object block,
                @Cached UnwrapCArrayNode unwrapCArrayNode,
                @Cached DispatchNode dispatchNode,
                @Cached InlinedConditionProfile ownedProfile) {
            final Object[] args = unwrapCArrayNode.execute(argv);
            return sendWithoutCExtLock(frame, receiver, method, block, NoKeywordArgumentsDescriptor.INSTANCE, args,
                    dispatchNode, PRIVATE, ownedProfile);
        }
    }

    @Primitive(name = "send_argv_keywords_without_cext_lock")
    public abstract static class SendARGVKeywordsWithoutCExtLockNode extends SendWithoutCExtLockBaseNode {
        @Specialization
        Object sendWithoutCExtLock(VirtualFrame frame, Object receiver, RubySymbol method, Object argv, Object block,
                @Cached UnwrapCArrayNode unwrapCArrayNode,
                @Cached HashCastNode hashCastNode,
                @Cached InlinedConditionProfile emptyProfile,
                @Cached DispatchNode dispatchNode,
                @Cached InlinedConditionProfile ownedProfile) {
            Object[] args = unwrapCArrayNode.execute(argv);

            // Remove empty kwargs in the caller, so the callee does not need to care about this special case
            final RubyHash keywords = hashCastNode.execute(this, ArrayUtils.getLast(args));
            if (emptyProfile.profile(this, keywords.empty())) {
                args = LiteralCallNode.removeEmptyKeywordArguments(args);
                return sendWithoutCExtLock(frame, receiver, method, block, NoKeywordArgumentsDescriptor.INSTANCE, args,
                        dispatchNode, PRIVATE, ownedProfile);
            } else {
                return sendWithoutCExtLock(frame, receiver, method, block,
                        KeywordArgumentsDescriptor.EMPTY, args,
                        dispatchNode, PRIVATE, ownedProfile);
            }
        }
    }

    @Primitive(name = "public_send_argv_without_cext_lock", lowerFixnum = 2)
    public abstract static class PublicSendARGVWithoutCExtLockNode extends SendWithoutCExtLockBaseNode {
        @Specialization
        Object publicSendWithoutLock(VirtualFrame frame, Object receiver, RubySymbol method, Object argv, Object block,
                @Cached UnwrapCArrayNode unwrapCArrayNode,
                @Cached DispatchNode dispatchNode,
                @Cached InlinedConditionProfile ownedProfile) {
            final Object[] args = unwrapCArrayNode.execute(argv);
            return sendWithoutCExtLock(frame, receiver, method, block, NoKeywordArgumentsDescriptor.INSTANCE, args,
                    dispatchNode, PUBLIC, ownedProfile);
        }
    }

    @Primitive(name = "public_send_argv_keywords_without_cext_lock")
    public abstract static class PublicSendARGVKeywordsWithoutCExtLockNode extends SendWithoutCExtLockBaseNode {
        @Specialization
        Object sendWithoutCExtLock(VirtualFrame frame, Object receiver, RubySymbol method, Object argv, Object block,
                @Cached UnwrapCArrayNode unwrapCArrayNode,
                @Cached HashCastNode hashCastNode,
                @Cached InlinedConditionProfile emptyProfile,
                @Cached DispatchNode dispatchNode,
                @Cached InlinedConditionProfile ownedProfile) {
            Object[] args = unwrapCArrayNode.execute(argv);

            // Remove empty kwargs in the caller, so the callee does not need to care about this special case
            final RubyHash keywords = hashCastNode.execute(this, ArrayUtils.getLast(args));
            if (emptyProfile.profile(this, keywords.empty())) {
                args = LiteralCallNode.removeEmptyKeywordArguments(args);
                return sendWithoutCExtLock(frame, receiver, method, block, NoKeywordArgumentsDescriptor.INSTANCE, args,
                        dispatchNode, PUBLIC, ownedProfile);
            } else {
                return sendWithoutCExtLock(frame, receiver, method, block,
                        KeywordArgumentsDescriptor.EMPTY, args,
                        dispatchNode, PUBLIC, ownedProfile);
            }
        }
    }

    @Primitive(name = "cext_mark_object_on_call_exit")
    public abstract static class MarkObjectOnCallExit extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object markOnCallExit(Object object,
                @Cached WrapNode wrapNode) {
            ValueWrapper wrapper = wrapNode.execute(object);
            getLanguage().getCurrentThread().getCurrentFiber().extensionCallStack.markOnExitObject(wrapper);
            return nil;
        }
    }

    @CoreMethod(names = "cext_start_new_handle_block", onSingleton = true)
    public abstract static class StartNewHandleBlockNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean startNewHandleBlock() {
            ValueWrapperManager.allocateNewBlock(getContext(), getLanguage());
            return true;
        }
    }

    @Primitive(name = "cext_lock_owned?")
    public abstract static class IsCExtLockOwnedPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isCExtLockOwned() {
            return getContext().getCExtensionsLock().isHeldByCurrentThread();
        }
    }

    @Primitive(name = "use_cext_lock?")
    public abstract static class UseCExtLockNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean useCExtLock() {
            if (getLanguage().getCurrentFiber().threadSafeExtension) {
                return false;
            } else {
                return getContext().getCExtensionsLock().isHeldByCurrentThread();
            }
        }
    }

    @CoreMethod(names = "rb_ulong2num", onSingleton = true, required = 1)
    public abstract static class ULong2NumNode extends CoreMethodArrayArgumentsNode {

        private static final BigInteger TWO_POW_64 = BigInteger.valueOf(1).shiftLeft(64);

        @Specialization
        Object ulong2num(long num,
                @Cached InlinedConditionProfile positiveProfile) {
            if (positiveProfile.profile(this, num >= 0)) {
                return num;
            } else {
                return BignumOperations.createBignum(toUnsigned(num));
            }
        }

        private BigInteger toUnsigned(long num) {
            return BigIntegerOps.add(TWO_POW_64, num);
        }

    }

    @CoreMethod(names = "rb_integer_bytes", onSingleton = true, lowerFixnum = { 2, 3 }, required = 6)
    public abstract static class IntegerBytesNode extends CoreMethodArrayArgumentsNode {

        // The Ruby MRI API for extracting the contents of a integer
        // fills a provided buffer of words with a representation of
        // the number in the specified format. This allows the users
        // to specify the word order in the buffer, the endianness
        // within the word, and whether to encode the number as a
        // magnitude or in two's complement.
        //
        // The API also returns an integer indicating whether the
        // number was positive or negative and the overflow
        // status. That part is implemented in C while this Java
        // method is purely concerned with extracting the bytes into
        // the buffer and getting them in the right order.
        //
        // If the buffer is too short to hold the entire number then
        // it will be filled with the least significant bytes of the
        // number.
        //
        // If the buffer is longer than is required for encoding the
        // number then the remainder of the buffer must not change the
        // interpretation of the number. I.e. if we are encoding in
        // two's complement then it must be filled with 0xff to
        // preserve the number's sign.
        //
        // The API allows the order of words in the buffer to be
        // specified as well as the order of bytes within those
        // words. We separate the process into two stages to make the
        // code easier to understand, first copying the bytes and
        // adding padding at the start of end, and then reordering the
        // bytes within each word if required.

        @Specialization
        @TruffleBoundary
        RubyArray bytes(
                int num, int num_words, int word_length, boolean msw_first, boolean twosComp, boolean bigEndian) {
            BigInteger bi = BigInteger.valueOf(num);
            return bytes(bi, num_words, word_length, msw_first, twosComp, bigEndian);
        }

        @Specialization
        @TruffleBoundary
        RubyArray bytes(
                long num, int num_words, int word_length, boolean msw_first, boolean twosComp, boolean bigEndian) {
            BigInteger bi = BigInteger.valueOf(num);
            return bytes(bi, num_words, word_length, msw_first, twosComp, bigEndian);
        }

        @Specialization
        @TruffleBoundary
        RubyArray bytes(
                RubyBignum num,
                int num_words,
                int word_length,
                boolean msw_first,
                boolean twosComp,
                boolean bigEndian) {
            BigInteger bi = num.value;
            return bytes(bi, num_words, word_length, msw_first, twosComp, bigEndian);
        }

        private RubyArray bytes(BigInteger bi, int num_words, int word_length, boolean msw_first, boolean twosComp,
                boolean bigEndian) {
            if (!twosComp) {
                bi = bi.abs();
            }
            int num_bytes = num_words * word_length;
            // We'll put the bytes into ints because we lack a byte array strategy.
            int[] bytes = new int[num_bytes];
            byte[] bi_bytes = bi.toByteArray();
            int bi_length = bi_bytes.length;
            boolean negative = bi.signum() == -1;

            // If we're not giving a twos comp answer then a leading
            // zero byte should be discarded.
            if (!twosComp && bi_bytes[0] == 0) {
                bi_length--;
            }
            int bytes_to_copy = Math.min(bi_length, num_bytes);
            if (msw_first) {
                // We must copy the LSBs if the buffer would overflow,
                // so calculate an offset based on that.
                int offset = bi_bytes.length - bytes_to_copy;

                for (int i = 0; i < bytes_to_copy; i++) {
                    bytes[i] = bi_bytes[offset + i];
                }
                if (negative) {
                    for (int i = 0; i < num_bytes - bytes_to_copy; i++) {
                        bytes[i] = -1;
                    }
                }
            } else {
                for (int i = 0; i < bytes_to_copy; i++) {
                    bytes[i] = bi_bytes[bi_bytes.length - 1 - i];
                }
                if (negative) {
                    for (int i = bytes_to_copy; i < num_bytes; i++) {
                        bytes[i] = -1;
                    }
                }
            }

            // Swap bytes around if they aren't in the right order for
            // the requested endianness.
            if (bigEndian ^ msw_first && word_length > 1) {
                for (int i = 0; i < num_words; i++) {
                    for (int j = 0; j < word_length / 2; j++) {
                        int pos_a = i * word_length + j;
                        int pos_b = (i + 1) * word_length - 1 - j;
                        int a = bytes[pos_a];
                        bytes[pos_a] = bytes[pos_b];
                        bytes[pos_b] = a;
                    }
                }
            }
            return createArray(bytes);
        }


    }

    @CoreMethod(names = "rb_absint_bit_length", onSingleton = true, required = 1)
    public abstract static class BignumAbsBitLengthNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        int bitLength(int num) {
            return BigInteger.valueOf(num).abs().bitLength();
        }

        @Specialization
        @TruffleBoundary
        int bitLength(long num) {
            return BigInteger.valueOf(num).abs().bitLength();
        }

        @Specialization
        @TruffleBoundary
        int bitLength(RubyBignum num) {
            return num.value.abs().bitLength();
        }
    }

    @CoreMethod(names = "rb_2scomp_bit_length", onSingleton = true, required = 1)
    public abstract static class Bignum2sCompBitLengthNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        int bitLength(int num) {
            return BigInteger.valueOf(num).bitLength();
        }

        @Specialization
        @TruffleBoundary
        int bitLength(long num) {
            return BigInteger.valueOf(num).bitLength();
        }

        @Specialization
        @TruffleBoundary
        int bitLength(RubyBignum num) {
            return num.value.bitLength();
        }
    }

    @Primitive(name = "rb_int_singlebit_p")
    public abstract static class IntSinglebitPPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int intSinglebitP(int num) {
            assert num >= 0;
            return Integer.bitCount(num) == 1 ? 1 : 0;
        }

        @Specialization
        int intSinglebitP(long num) {
            assert num >= 0;
            return Long.bitCount(num) == 1 ? 1 : 0;
        }

        @Specialization
        @TruffleBoundary
        int intSinglebitP(RubyBignum num) {
            assert num.value.signum() >= 0;
            return num.value.bitCount() == 1 ? 1 : 0;
        }
    }

    @CoreMethod(names = "DBL2BIG", onSingleton = true, required = 1)
    public abstract static class DBL2BIGNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object dbl2big(double num,
                @Cached FloatToIntegerNode floatToIntegerNode) {
            return floatToIntegerNode.execute(this, num);
        }

    }

    @CoreMethod(names = "rb_long2int", onSingleton = true, required = 1)
    public abstract static class Long2Int extends CoreMethodArrayArgumentsNode {

        @Specialization
        int long2fix(int num) {
            return num;
        }

        @Specialization(guards = "fitsInInteger(num)")
        int long2fixInRange(long num) {
            return (int) num;
        }

        @Specialization(guards = "!fitsInInteger(num)")
        int long2fixOutOfRange(long num) {
            throw new RaiseException(getContext(), coreExceptions().rangeErrorConvertToInt(num, this));
        }
    }

    @CoreMethod(names = "rb_enc_coderange_clear", onSingleton = true, required = 1)
    public abstract static class RbEncCodeRangeClear extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString clearCodeRange(RubyString string,
                @Cached StringToNativeNode stringToNativeNode) {
            stringToNativeNode.executeToNative(this, string, true);
            string.clearCodeRange();

            return string;
        }

    }

    @CoreMethod(names = "code_to_mbclen", onSingleton = true, required = 2, lowerFixnum = 1)
    public abstract static class CodeToMbcLenNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int codeToMbcLen(int code, RubyEncoding encoding) {
            return StringSupport.codeLength(encoding.jcoding, code);
        }

    }

    @CoreMethod(names = "rb_enc_codepoint_len", onSingleton = true, required = 1)
    public abstract static class RbEncCodePointLenNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        static RubyArray rbEncCodePointLen(Object string,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                @Cached InlinedBranchProfile errorProfile,
                @Bind Node node) {
            var tstring = strings.getTString(node, string);
            var encoding = strings.getEncoding(node, string);
            var tencoding = encoding.tencoding;

            final int r = byteLengthOfCodePointNode.execute(tstring, 0, tencoding, ErrorHandling.RETURN_NEGATIVE);

            if (!StringSupport.MBCLEN_CHARFOUND_P(r)) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
            }

            int codePoint = codePointAtByteIndexNode.execute(tstring, 0, tencoding, ErrorHandling.RETURN_NEGATIVE);
            assert codePoint != -1;

            return createArray(node, new int[]{ StringSupport.MBCLEN_CHARFOUND_LEN(r), codePoint });
        }
    }

    @CoreMethod(names = "rb_enc_isalnum", onSingleton = true, required = 2, lowerFixnum = 1)
    public abstract static class RbEncIsAlNumNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean rbEncIsAlNum(int code, RubyEncoding value) {
            return value.jcoding.isAlnum(code);
        }

    }

    @CoreMethod(names = "rb_enc_isspace", onSingleton = true, required = 2, lowerFixnum = 1)
    public abstract static class RbEncIsSpaceNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean rbEncIsSpace(int code, RubyEncoding value) {
            return value.jcoding.isSpace(code);
        }

    }

    @CoreMethod(names = "rb_str_new_nul", onSingleton = true, required = 1, lowerFixnum = 1)
    public abstract static class RbStrNewNulNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString rbStrNewNul(int byteLength,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode) {
            final Pointer pointer = newZeroedNativeStringPointer(getLanguage(), getContext(), byteLength);
            var nativeTString = fromNativePointerNode.execute(pointer, 0, byteLength, Encodings.BINARY.tencoding,
                    false);
            return createMutableString(nativeTString, Encodings.BINARY);
        }

    }

    /** Alternative to rb_str_new*() which does not copy the bytes from native memory, to use when the copy is
     * unnecessary. */
    @CoreMethod(names = "rb_tr_temporary_native_string", onSingleton = true, required = 3, lowerFixnum = 2)
    public abstract static class TemporaryNativeStringNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString temporaryNativeString(Object pointer, int byteLength, RubyEncoding encoding,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode) {
            var nativeTString = fromNativePointerNode.execute(pointer, 0, byteLength, encoding.tencoding, false);
            return createMutableString(nativeTString, encoding);
        }
    }

    @CoreMethod(names = "rb_str_capacity", onSingleton = true, required = 1)
    public abstract static class RbStrCapacityNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long capacity(Object string,
                @Cached StringToNativeNode stringToNativeNode) {
            return getNativeStringCapacity(stringToNativeNode.executeToNative(this, string, true));
        }
    }

    @CoreMethod(names = "rb_str_set_len", onSingleton = true, required = 2, lowerFixnum = 2)
    public abstract static class RbStrSetLenNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString strSetLen(RubyString string, int newByteLength,
                @Cached RubyStringLibrary libString,
                @Cached StringToNativeNode stringToNativeNode,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode,
                @Cached InlinedConditionProfile minLengthOneProfile) {
            var pointer = stringToNativeNode.executeToNative(this, string, true);

            var encoding = libString.getEncoding(this, string);
            int minLength = encoding.jcoding.minLength();
            // Like MRI
            if (minLengthOneProfile.profile(this, minLength == 1)) {
                pointer.writeByte(newByteLength, (byte) 0);
            } else if (minLength == 2) {
                pointer.writeShort(newByteLength, (short) 0);
            } else if (minLength == 4) {
                pointer.writeInt(newByteLength, 0);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }

            var newNativeTString = fromNativePointerNode.execute(pointer, 0, newByteLength, encoding.tencoding, false);
            string.setTString(newNativeTString);

            return string;
        }
    }

    @CoreMethod(names = "rb_str_resize", onSingleton = true, required = 2, lowerFixnum = 2)
    public abstract static class RbStrResizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString rbStrResize(RubyString string, int newByteLength,
                @Cached RubyStringLibrary libString,
                @Cached StringToNativeNode stringToNativeNode,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode) {
            var pointer = stringToNativeNode.executeToNative(this, string, true);
            var tencoding = libString.getTEncoding(this, string);
            int byteLength = string.tstring.byteLength(tencoding);

            if (byteLength == newByteLength) {
                // Like MRI's rb_str_resize()
                string.clearCodeRange();
                return string;
            } else {
                var newNativeTString = TrStrCapaResizeNode.resize(getLanguage(), getContext(), pointer, newByteLength,
                        newByteLength, tencoding, fromNativePointerNode);
                string.setTString(newNativeTString);

                // Like MRI's rb_str_resize()
                string.clearCodeRange();

                return string;
            }
        }
    }

    @CoreMethod(names = "rb_tr_str_capa_resize", onSingleton = true, required = 2, lowerFixnum = 2)
    public abstract static class TrStrCapaResizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString trStrCapaResize(RubyString string, int newCapacity,
                @Cached RubyStringLibrary libString,
                @Cached StringToNativeNode stringToNativeNode,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode) {
            var pointer = stringToNativeNode.executeToNative(this, string, true);
            var tencoding = libString.getTEncoding(this, string);

            if (getNativeStringCapacity(pointer) == newCapacity) {
                return string;
            } else {
                int byteLength = string.tstring.byteLength(tencoding);
                var newNativeTString = resize(getLanguage(), getContext(), pointer, newCapacity, byteLength, tencoding,
                        fromNativePointerNode);
                string.setTString(newNativeTString);

                return string;
            }
        }

        static MutableTruffleString resize(RubyLanguage language, RubyContext context, Pointer pointer, int newCapacity,
                int newByteLength, TruffleString.Encoding tencoding,
                MutableTruffleString.FromNativePointerNode fromNativePointerNode) {
            final Pointer newPointer = newNativeStringPointer(language, context, newCapacity);
            newPointer.writeBytes(0, pointer, 0, Math.min(pointer.getSize(), newCapacity));

            return fromNativePointerNode.execute(newPointer, 0, newByteLength, tencoding, false);
        }
    }

    @CoreMethod(names = "rb_keyword_given_p", onSingleton = true)
    public abstract static class RbKeywordGivenNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean keywordGiven() {
            return getLanguage().getCurrentFiber().extensionCallStack.areKeywordsGiven();
        }
    }

    @CoreMethod(names = "rb_block_proc", onSingleton = true)
    public abstract static class BlockProcNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object block() {
            return getLanguage().getCurrentFiber().extensionCallStack.getBlock();
        }
    }

    @Primitive(name = "cext_special_variables_from_stack")
    public abstract static class VarsFromStackNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object variables() {
            return getLanguage().getCurrentFiber().extensionCallStack.getSpecialVariables();
        }
    }

    @CoreMethod(names = "rb_check_frozen", onSingleton = true, required = 1)
    public abstract static class CheckFrozenNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean rb_check_frozen(Object object,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            raiseIfFrozenNode.execute(this, object);
            return true;
        }

    }

    @CoreMethod(names = "rb_str_locktmp", onSingleton = true, required = 1)
    public abstract static class RbStrLockTmpNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString rbStrLockTmp(RubyString string,
                @Cached InlinedBranchProfile errorProfile,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            raiseIfFrozenNode.execute(this, string);

            if (string.locked) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(),
                        coreExceptions().runtimeError("temporal locking already locked string", this));
            }
            string.locked = true;
            return string;
        }

        @Specialization
        RubyString rbStrLockTmpImmutable(ImmutableRubyString string) {
            throw new RaiseException(getContext(this), coreExceptions(this).frozenError(string, this));
        }

    }

    @CoreMethod(names = "rb_str_unlocktmp", onSingleton = true, required = 1)
    public abstract static class RbStrUnlockTmpNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString rbStrUnlockTmp(RubyString string,
                @Cached InlinedBranchProfile errorProfile,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            raiseIfFrozenNode.execute(this, string);

            if (!string.locked) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(),
                        coreExceptions().runtimeError("temporal unlocking already unlocked string", this));
            }
            string.locked = false;
            return string;
        }

        @Specialization
        ImmutableRubyString rbStrUnlockTmpImmutable(ImmutableRubyString string) {
            throw new RaiseException(getContext(this), coreExceptions(this).frozenError(string, this));
        }

    }

    @CoreMethod(names = "rb_const_get", onSingleton = true, required = 2)
    public abstract static class RbConstGetNode extends CoreMethodArrayArgumentsNode {

        @Child private LookupConstantNode lookupConstantNode = LookupConstantNode.create(true, true);

        @Specialization
        Object rbConstGet(RubyModule module, Object name,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached GetConstantNode getConstantNode) {
            final var nameAsString = toJavaStringNode.execute(this, name);
            return getConstantNode
                    .lookupAndResolveConstant(LexicalScope.IGNORE, module, nameAsString, false, lookupConstantNode,
                            true);
        }

    }

    @CoreMethod(names = "rb_const_get_from", onSingleton = true, required = 2)
    public abstract static class RbConstGetFromNode extends CoreMethodArrayArgumentsNode {

        @Child private LookupConstantNode lookupConstantNode = LookupConstantNode.create(true, false);

        @Specialization
        Object rbConstGetFrom(RubyModule module, Object name,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached GetConstantNode getConstantNode) {
            final var nameAsString = toJavaStringNode.execute(this, name);
            return getConstantNode
                    .lookupAndResolveConstant(LexicalScope.IGNORE, module, nameAsString, false, lookupConstantNode,
                            true);
        }

    }

    @CoreMethod(names = "rb_const_set", onSingleton = true, required = 3)
    public abstract static class RbConstSetNode extends CoreMethodArrayArgumentsNode {


        @Specialization
        Object rbConstSet(RubyModule module, Object name, Object value,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached ConstSetUncheckedNode constSetUncheckedNode) {
            final var nameAsString = toJavaStringNode.execute(this, name);
            return constSetUncheckedNode.execute(module, nameAsString, value);
        }
    }

    @Primitive(name = "rb_gv_get")
    public abstract static class RbGvGetNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object rbGvGet(VirtualFrame frame, String name,
                @Cached InlinedBranchProfile notExistsProfile,
                @Cached LazyWarningNode lazyWarningNode,
                @Cached RbGvGetInnerNode rbGvGetInnerNode) {
            boolean exists = getContext().getCoreLibrary().globalVariables.contains(name);

            // Check if it exists and return if not, before creating the GlobalVariableStorage
            if (!exists) {
                notExistsProfile.enter(this);
                WarningNode warningNode = lazyWarningNode.get(this);
                if (warningNode.shouldWarn()) {
                    warningNode.warningMessage(getContext(this).getCallStack().getTopMostUserSourceSection(),
                            StringUtils.format("global variable '%s' not initialized", name));
                }
                return nil;
            }

            return rbGvGetInnerNode.execute(frame, this, name);
        }
    }


    @ReportPolymorphism // inline cache
    @GenerateInline
    @GenerateCached(false)
    public abstract static class RbGvGetInnerNode extends RubyBaseNode {

        public abstract Object execute(VirtualFrame frame, Node node, String name);

        @Specialization(guards = "name == cachedName", limit = "getDefaultCacheLimit()")
        static Object rbGvGetCached(VirtualFrame frame, Node node, String name,
                @Cached("name") String cachedName,
                @Cached(value = "create(cachedName)", inline = false) ReadGlobalVariableNode readGlobalVariableNode) {
            return readGlobalVariableNode.execute(frame);
        }

        @Specialization
        static Object rbGvGetUncached(Node node, String name,
                @Cached(inline = false) DispatchNode dispatchNode) {
            return dispatchNode.call(coreLibrary(node).topLevelBinding, "eval", name);
        }
    }

    @CoreMethod(names = "cext_module_function", onSingleton = true, required = 2)
    public abstract static class CextModuleFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule cextModuleFunction(RubyModule module, RubySymbol name,
                @Cached SetMethodVisibilityNode setMethodVisibilityNode) {
            setMethodVisibilityNode.execute(this, module, name, Visibility.MODULE_FUNCTION);
            return module;
        }

    }

    @CoreMethod(names = "caller_frame_visibility", onSingleton = true, required = 1)
    public abstract static class CallerFrameVisibilityNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean checkCallerVisibility(RubySymbol visibility) {
            final Frame callerFrame = getContext().getCallStack().getCallerFrame(FrameAccess.READ_ONLY);
            final Visibility callerVisibility = DeclarationContext.findVisibility(callerFrame);

            switch (visibility.getString()) {
                case "private":
                    return callerVisibility == Visibility.PRIVATE;
                case "protected":
                    return callerVisibility == Visibility.PROTECTED;
                case "module_function":
                    return callerVisibility == Visibility.MODULE_FUNCTION;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @CoreMethod(names = "rb_iter_break_value", onSingleton = true, required = 1)
    public abstract static class IterBreakValueNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object iterBreakValue(Object value) {
            throw new BreakException(BreakID.ANY_BLOCK, value);
        }

    }

    @CoreMethod(names = "rb_sourcefile", onSingleton = true)
    public abstract static class SourceFileNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyString sourceFile() {
            final SourceSection sourceSection = getTopUserSourceSection("rb_sourcefile");
            final String file = getLanguage().getSourcePath(sourceSection.getSource());

            return createString(fromJavaStringNode, file, Encodings.UTF_8);
        }

        @TruffleBoundary
        public static SourceSection getTopUserSourceSection(String... methodNames) {
            return Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Node callNode = frameInstance.getCallNode();

                if (callNode != null) {
                    final RootNode rootNode = callNode.getRootNode();

                    if (rootNode instanceof RubyRootNode && rootNode.getSourceSection().isAvailable() &&
                            !nameMatches(
                                    ((RubyRootNode) rootNode).getSharedMethodInfo().getMethodName(),
                                    methodNames)) {
                        return frameInstance.getCallNode().getEncapsulatingSourceSection();
                    }
                }

                return null;
            });
        }

        private static boolean nameMatches(String name, String... methodNames) {
            for (String methodName : methodNames) {
                if (methodName.equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    @CoreMethod(names = "rb_sourceline", onSingleton = true)
    public abstract static class SourceLineNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        int sourceLine() {
            final SourceSection sourceSection = SourceFileNode.getTopUserSourceSection("rb_sourceline");
            return getLanguage().getStartLineAdjusted(sourceSection);
        }

    }

    @CoreMethod(names = "rb_is_local_id", onSingleton = true, required = 1)
    public abstract static class IsLocalIdNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isLocalId(RubySymbol symbol) {
            return symbol.getType() == IdentifierType.LOCAL;
        }

    }

    @CoreMethod(names = "rb_is_instance_id", onSingleton = true, required = 1)
    public abstract static class IsInstanceIdNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isInstanceId(RubySymbol symbol) {
            return symbol.getType() == IdentifierType.INSTANCE;
        }

    }

    @CoreMethod(names = "rb_is_const_id", onSingleton = true, required = 1)
    public abstract static class IsConstIdNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isConstId(RubySymbol symbol) {
            return symbol.getType() == IdentifierType.CONST;
        }

    }

    @CoreMethod(names = "rb_is_class_id", onSingleton = true, required = 1)
    public abstract static class IsClassVariableIdNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isClassVariableId(RubySymbol symbol) {
            return symbol.getType() == IdentifierType.CLASS;
        }

    }

    @CoreMethod(names = "rb_call_super_splatted", onSingleton = true, rest = true)
    public abstract static class CallSuperNode extends CoreMethodArrayArgumentsNode {

        @Child private CallSuperMethodNode callSuperMethodNode = CallSuperMethodNode.create();

        @Specialization
        Object callSuper(VirtualFrame frame, Object[] args,
                @Cached MetaClassNode metaClassNode) {
            final Frame callingMethodFrame = findCallingMethodFrame();
            final InternalMethod callingMethod = RubyArguments.getMethod(callingMethodFrame);
            final Object callingSelf = RubyArguments.getSelf(callingMethodFrame);
            final RubyClass callingMetaclass = metaClassNode.execute(this, callingSelf);
            final MethodLookupResult superMethodLookup = ModuleOperations
                    .lookupSuperMethod(callingMethod, callingMetaclass);
            final InternalMethod superMethod = superMethodLookup.getMethod();
            // This C API only passes positional arguments, but maybe it should be influenced by ruby2_keywords hashes?
            return callSuperMethodNode.execute(
                    frame, callingSelf, superMethod, NoKeywordArgumentsDescriptor.INSTANCE, args, nil);
        }

        @TruffleBoundary
        private static Frame findCallingMethodFrame() {
            return Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);

                final InternalMethod method = RubyArguments.tryGetMethod(frame);

                if (method == null) {
                    return null;
                } else if (method.getName().equals(/* Truffle::CExt. */ "rb_call_super") ||
                        method.getName().equals(/* Truffle::Interop. */ "execute_without_conversion") ||
                        method.getName().equals(/* Truffle::CExt. */ "rb_call_super_splatted")) {
                    // TODO CS 11-Mar-17 must have a more precise check to skip these methods
                    return null;
                } else {
                    return frame;
                }
            });
        }

    }

    @CoreMethod(names = "rb_frame_this_func", onSingleton = true, rest = true)
    public abstract static class FrameThisFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object frameThisFunc(VirtualFrame frame, Object[] args) {
            final Frame callingMethodFrame = findCallingMethodFrame();
            final InternalMethod callingMethod = RubyArguments.getMethod(callingMethodFrame);
            return getSymbol(callingMethod.getName());
        }

        @TruffleBoundary
        private static Frame findCallingMethodFrame() {
            return Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);

                final InternalMethod method = RubyArguments.tryGetMethod(frame);

                if (method == null) {
                    return null;
                } else if (method.getName().equals(/* Truffle::CExt. */ "rb_frame_this_func") ||
                        method.getName().equals(/* Truffle::Interop */ "execute_without_conversion")) {
                    // TODO CS 11-Mar-17 must have a more precise check to skip these methods
                    return null;
                } else {
                    return frame;
                }
            });
        }

    }

    @CoreMethod(names = "rb_syserr_fail", onSingleton = true, required = 2, lowerFixnum = 1)
    public abstract static class RbSysErrFail extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object rbSysErrFail(int errno, Object string,
                @Cached ErrnoErrorNode errnoErrorNode) {
            final Backtrace backtrace = getContext().getCallStack().getBacktrace(this);
            throw new RaiseException(getContext(), errnoErrorNode.execute(null, errno, string, null, backtrace));
        }

    }

    @CoreMethod(names = "rb_hash", onSingleton = true, required = 1)
    public abstract static class RbHashNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int rbHash(Object object,
                @Cached HashingNodes.ToHashByHashCode toHashByHashCode) {
            return toHashByHashCode.execute(this, object);
        }
    }

    /** If inplace is true, this node mutates the RubyString to use native memory. It should be avoided unless there is
     * no other way because e.g. Regexp matching later on that String would then copy to managed byte[] back, and
     * copying back-and-forth is quite expensive. OTOH if the String will need to be used as native memory again soon
     * after and without needing to go to managed in between then it is valuable to avoid extra copies. */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class StringToNativeNode extends RubyBaseNode {

        public abstract Pointer executeToNative(Node node, Object string, boolean inplace);

        @Specialization
        static Pointer toNative(Node node, RubyString string, boolean inplace,
                @Cached RubyStringLibrary libString,
                @Cached InlinedConditionProfile convertProfile,
                @Cached(inline = false) TruffleString.CopyToNativeMemoryNode copyToNativeMemoryNode,
                @Cached(inline = false) MutableTruffleString.FromNativePointerNode fromNativePointerNode,
                @Cached(inline = false) TruffleString.GetInternalNativePointerNode getInternalNativePointerNode) {
            CompilerAsserts.partialEvaluationConstant(inplace);

            var tstring = string.tstring;
            var tencoding = libString.getTEncoding(node, string);

            final Pointer pointer;
            if (convertProfile.profile(node, tstring.isNative())) {
                assert tstring.isMutable();
                pointer = (Pointer) getInternalNativePointerNode.execute(tstring, tencoding);
            } else {
                int byteLength = tstring.byteLength(tencoding);
                pointer = allocateAndCopyToNative(getLanguage(node), getContext(node), tstring, tencoding, byteLength,
                        copyToNativeMemoryNode);

                if (inplace) {
                    var nativeTString = fromNativePointerNode.execute(pointer, 0, byteLength, tencoding, false);
                    string.setTString(nativeTString);
                }
            }

            return pointer;
        }

        @Specialization
        static Pointer toNativeImmutable(Node node, ImmutableRubyString string, boolean inplace) {
            return string.getNativeString(getLanguage(node), getContext(node));
        }

        public static Pointer allocateAndCopyToNative(RubyLanguage language, RubyContext context,
                AbstractTruffleString tstring, TruffleString.Encoding tencoding, int capacity,
                TruffleString.CopyToNativeMemoryNode copyToNativeMemoryNode) {
            final Pointer pointer = newNativeStringPointer(language, context, capacity);
            copyToNativeMemoryNode.execute(tstring, 0, pointer, 0, capacity, tencoding);
            return pointer;
        }

    }

    @Primitive(name = "string_pointer_to_native")
    public abstract static class StringPointerToNativeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long toNative(Object string,
                @Cached StringToNativeNode stringToNativeNode) {
            return stringToNativeNode.executeToNative(this, string, true).getAddress();
        }
    }

    @CoreMethod(names = "string_to_ffi_pointer_inplace", onSingleton = true, required = 1)
    public abstract static class StringToFFIPointerInplaceNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPointer toFFIPointerInplace(Object string,
                @Cached StringToNativeNode stringToNativeNode) {
            var pointer = stringToNativeNode.executeToNative(this, string, true);

            final RubyPointer instance = new RubyPointer(
                    coreLibrary().truffleFFIPointerClass,
                    getLanguage().truffleFFIPointerShape,
                    pointer);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @CoreMethod(names = "string_to_ffi_pointer_copy", onSingleton = true, required = 1)
    public abstract static class StringToFFIPointerCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPointer toFFIPointerCopy(Object string,
                @Cached StringToNativeNode stringToNativeNode) {
            var pointer = stringToNativeNode.executeToNative(this, string, false);

            final RubyPointer instance = new RubyPointer(
                    coreLibrary().truffleFFIPointerClass,
                    getLanguage().truffleFFIPointerShape,
                    pointer);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @Primitive(name = "string_is_native?")
    public abstract static class StringPointerIsNativeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isNative(RubyString string) {
            return string.tstring.isNative();
        }

        @TruffleBoundary
        @Specialization
        boolean isNative(ImmutableRubyString string) {
            return string.isNative();
        }

    }

    @CoreMethod(names = "rb_tr_debug", onSingleton = true, rest = true)
    public abstract static class DebugNode extends CoreMethodArrayArgumentsNode {

        @Child DispatchNode toSCall;

        @TruffleBoundary
        @Specialization
        Object debug(Object... objects) {
            if (objects.length > 1) {
                System.err.printf("Printing %d values%n", objects.length);
            }

            final RubyStringLibrary libString = RubyStringLibrary.getUncached();
            for (Object object : objects) {
                final String representation;

                if (libString.isRubyString(this, object)) {
                    var tstring = libString.getTString(this, object);
                    final byte[] bytes = TStringUtils.getBytesOrCopy(tstring, libString.getEncoding(this, object));
                    final StringBuilder builder = new StringBuilder();

                    for (int i = 0; i < bytes.length; i++) {
                        if (i % 4 == 0 && i != 0 && i != bytes.length - 1) {
                            builder.append(" ");
                        }
                        builder.append(String.format("%02x", bytes[i]));
                    }

                    representation = tstring + " (" + builder + ")";
                } else if (RubyGuards.isRubyValue(object)) {
                    representation = object.toString() + " (" + StringOperations.getJavaString(callToS(object)) + ")";
                } else {
                    representation = object.toString();
                }

                System.err.printf("%s @ %s: %s%n", object.getClass(), System.identityHashCode(object), representation);
            }
            return nil;
        }

        private Object callToS(Object object) {
            if (toSCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSCall = insert(DispatchNode.create());
            }

            return toSCall.call(object, "to_s");
        }

    }

    @CoreMethod(names = "capture_exception", onSingleton = true, needsBlock = true)
    public abstract static class CaptureExceptionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object captureException(RubyProc block,
                @Cached InlinedBranchProfile exceptionProfile,
                @Cached InlinedBranchProfile noExceptionProfile,
                @Cached CallBlockNode yieldNode) {
            try {
                yieldNode.yield(this, block);
                noExceptionProfile.enter(this);
                return nil;
            } catch (Throwable e) {
                exceptionProfile.enter(this);
                return new CapturedException(e);
            }
        }
    }

    @CoreMethod(names = "store_exception", onSingleton = true, required = 1)
    public abstract static class StoreException extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object storeException(CapturedException captured) {
            final ExtensionCallStack extensionStack = getLanguage().getCurrentFiber().extensionCallStack;
            extensionStack.setException(captured);
            return nil;
        }
    }

    @CoreMethod(names = "retrieve_exception", onSingleton = true)
    public abstract static class RetrieveException extends CoreMethodArrayArgumentsNode {

        @Specialization
        CapturedException retrieveException() {
            final ExtensionCallStack extensionStack = getLanguage().getCurrentFiber().extensionCallStack;
            return extensionStack.getException();
        }
    }

    @CoreMethod(names = "extract_ruby_exception", onSingleton = true, required = 1)
    public abstract static class ExtractRubyException extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object extractRubyException(CapturedException captured,
                @Cached InlinedConditionProfile rubyExceptionProfile) {
            final Throwable e = captured.getException();
            if (rubyExceptionProfile.profile(this, e instanceof RaiseException)) {
                return ((RaiseException) e).getException();
            } else {
                return nil;
            }
        }
    }

    @CoreMethod(names = "extract_tag", onSingleton = true, required = 1)
    public abstract static class ExtractRubyTag extends CoreMethodArrayArgumentsNode {

        @Specialization
        int extractRubyTag(CapturedException captured,
                @Cached ExtractRubyTagHelperNode helperNode) {
            return helperNode.execute(this, captured.getException());
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ExtractRubyTagHelperNode extends RubyBaseNode {

        // Object instead of Throwable to workaround Truffle DSL bug GR-46797
        public abstract int execute(Node node, Object e);

        @Specialization
        static int dynamicReturnTag(DynamicReturnException e) {
            return RUBY_TAG_RETURN;
        }

        @Specialization
        static int localReturnTag(LocalReturnException e) {
            return RUBY_TAG_RETURN;
        }

        @Specialization
        static int breakTag(BreakException e) {
            return RUBY_TAG_BREAK;
        }

        @Specialization
        static int nextTag(NextException e) {
            return RUBY_TAG_NEXT;
        }

        @Specialization
        static int retryTag(RetryException e) {
            return RUBY_TAG_RETRY;
        }

        @Specialization
        static int redoTag(RedoException e) {
            return RUBY_TAG_REDO;
        }

        @Specialization
        static int raiseTag(RaiseException e) {
            return RUBY_TAG_RAISE;
        }

        @Specialization
        static int throwTag(ThrowException e) {
            return RUBY_TAG_THROW;
        }

        // Object instead of Throwable to workaround Truffle DSL bug GR-46797
        @Fallback
        static int noTag(Object e) {
            return 0;
        }
    }

    @CoreMethod(names = "raise_exception", onSingleton = true, required = 1)
    public abstract static class RaiseExceptionNode extends CoreMethodArrayArgumentsNode {

        /** Profiled version of {@link ExceptionOperations#rethrow(Throwable)} */
        @Specialization
        Object raiseException(CapturedException captured,
                @Cached InlinedConditionProfile runtimeExceptionProfile,
                @Cached InlinedConditionProfile errorProfile) {
            final Throwable e = captured.getException();
            if (runtimeExceptionProfile.profile(this, e instanceof RuntimeException)) {
                throw (RuntimeException) e;
            } else if (errorProfile.profile(this, e instanceof Error)) {
                throw (Error) e;
            } else {
                throw CompilerDirectives.shouldNotReachHere("Checked Java Throwable rethrown", e);
            }
        }
    }

    @CoreMethod(names = "rb_tr_enc_mbc_case_fold", onSingleton = true, required = 4, lowerFixnum = 1)
    public abstract static class RbTrMbcCaseFoldNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getCacheLimit()")
        static Object rbTrEncMbcCaseFold(int flags, Object string, Object advance_p, Object p,
                @Cached RubyStringLibrary strings,
                @CachedLibrary("advance_p") InteropLibrary receivers,
                @Cached TranslateInteropExceptionNode translateInteropExceptionNode,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Bind Node node) {
            var tstring = strings.getTString(node, string);
            var encoding = strings.getEncoding(node, string);
            var bytes = TStringUtils.getBytesOrFail(tstring, encoding, byteArrayNode);

            final byte[] to = new byte[bytes.length];
            final IntHolder intHolder = new IntHolder();
            intHolder.value = 0;

            final int resultLength = encoding.jcoding.mbcCaseFold(flags, bytes, intHolder, bytes.length, to);

            InteropNodes.execute(node, advance_p, new Object[]{ p, intHolder.value }, receivers,
                    translateInteropExceptionNode);

            final byte[] result = new byte[resultLength];
            if (resultLength > 0) {
                System.arraycopy(to, 0, result, 0, resultLength);
            }

            return createString(node, fromByteArrayNode, result, Encodings.US_ASCII);
        }

        protected int getCacheLimit() {
            return getLanguage().options.DISPATCH_CACHE;
        }
    }

    @CoreMethod(names = "rb_tr_code_to_mbc", onSingleton = true, required = 2, lowerFixnum = 2)
    public abstract static class RbTrMbcPutNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object rbTrEncMbcPut(RubyEncoding enc, int code,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final Encoding encoding = enc.jcoding;
            final byte[] buf = new byte[Config.ENC_CODE_TO_MBC_MAXLEN];
            final int resultLength = encoding.codeToMbc(code, buf, 0);
            final byte[] result = new byte[resultLength];
            if (resultLength > 0) {
                System.arraycopy(buf, 0, result, 0, resultLength);
            }
            return createString(fromByteArrayNode, result, Encodings.US_ASCII);
        }
    }

    @CoreMethod(names = "rb_enc_mbmaxlen", onSingleton = true, required = 1)
    public abstract static class RbEncMaxLenNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object rbEncMaxLen(RubyEncoding value) {
            return value.jcoding.maxLength();
        }
    }

    @CoreMethod(names = "rb_enc_mbminlen", onSingleton = true, required = 1)
    public abstract static class RbEncMinLenNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object rbEncMinLen(RubyEncoding value) {
            return value.jcoding.minLength();
        }
    }

    @CoreMethod(names = "rb_enc_mbclen", onSingleton = true, required = 1)
    public abstract static class RbEncMbLenNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        static Object rbEncMbLen(Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode) {
            var tstring = strings.getTString(node, string);
            var tencoding = strings.getTEncoding(node, string);
            return byteLengthOfCodePointNode.execute(tstring, 0, tencoding, ErrorHandling.BEST_EFFORT);
        }
    }

    @CoreMethod(names = "rb_enc_precise_mbclen", onSingleton = true, required = 1)
    public abstract static class RbEncPreciseMbclenNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        static int rbEncPreciseMbclen(Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode) {
            var tstring = strings.getTString(node, string);
            var tencoding = strings.getTEncoding(node, string);
            return byteLengthOfCodePointNode.execute(tstring, 0, tencoding, ErrorHandling.RETURN_NEGATIVE);
        }
    }

    @CoreMethod(names = "rb_enc_strlen", onSingleton = true, required = 1)
    public abstract static class RbEncStrlen extends CoreMethodArrayArgumentsNode {

        @Specialization
        static int rbEncStrlen(Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            var tstring = strings.getTString(node, string);
            var tencoding = strings.getTEncoding(node, string);

            return codePointLengthNode.execute(tstring, tencoding);
        }

    }

    @CoreMethod(names = "rb_enc_left_char_head", onSingleton = true, required = 3, lowerFixnum = 3)
    public abstract static class RbEncLeftCharHeadNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object rbEncLeftCharHead(RubyEncoding enc, Object string, int p,
                @Cached RubyStringLibrary strings) {
            byte[] bytes = TStringUtils.getBytesOrFail(strings.getTString(this, string),
                    strings.getEncoding(this, string));
            return enc.jcoding.leftAdjustCharHead(bytes, 0, p, bytes.length);
        }
    }

    @CoreMethod(names = "rb_enc_mbc_to_codepoint", onSingleton = true, required = 1)
    public abstract static class RbEncMbcToCodepointNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        static int rbEncMbcToCodepoint(Object string,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Cached InlinedConditionProfile brokenProfile,
                @Bind Node node) {
            var tstring = strings.getTString(node, string);
            var encoding = strings.getEncoding(node, string);
            int codepoint = codePointAtByteIndexNode.execute(tstring, 0, encoding.tencoding,
                    ErrorHandling.RETURN_NEGATIVE);
            if (brokenProfile.profile(node, codepoint == -1)) {
                var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);
                return StringSupport.mbcToCode(encoding.jcoding, byteArray.getArray(), byteArray.getOffset(),
                        byteArray.getEnd());
            } else {
                return codepoint;
            }
        }
    }

    @Primitive(name = "cext_sym2id")
    public abstract static class Sym2IDNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object sym2id(RubySymbol symbol,
                @Cached SymbolToIDNode symbolToIDNode) {
            return symbolToIDNode.execute(symbol);
        }

    }

    @Primitive(name = "cext_wrap")
    public abstract static class WrapValueNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        ValueWrapper wrap(Object value,
                @Cached WrapNode wrapNode) {
            return wrapNode.execute(value);
        }
    }

    @Primitive(name = "cext_unwrap")
    public abstract static class UnwrapValueNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object unwrap(Object value,
                @Cached InlinedBranchProfile exceptionProfile,
                @Cached UnwrapNode unwrapNode) {
            Object object = unwrapNode.execute(this, value);
            if (object == null) {
                exceptionProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().runtimeError(exceptionMessage(value), this));
            } else {
                return object;
            }
        }

        @TruffleBoundary
        private String exceptionMessage(Object value) {
            return String.format("native handle not found (%s)", value);
        }
    }

    @Primitive(name = "cext_to_wrapper")
    public abstract static class CExtToWrapperNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        ValueWrapper toWrapper(Object value,
                @Cached UnwrapNode.ToWrapperNode toWrapperNode) {
            ValueWrapper wrapper = toWrapperNode.execute(this, value);
            if (wrapper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("ValueWrapper not found for " + value);
            }
            return wrapper;
        }
    }


    @CoreMethod(names = "create_mark_list", onSingleton = true, required = 1)
    public abstract static class NewMarkerList extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        Object createNewMarkList(RubyDynamicObject object,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            getContext().getMarkingService().startMarking(
                    getLanguage().getCurrentThread().getCurrentFiber().extensionCallStack,
                    (Object[]) objectLibrary.getOrDefault(object, Layouts.MARKED_OBJECTS_IDENTIFIER, null));
            return nil;
        }
    }

    @CoreMethod(names = "rb_gc_mark", onSingleton = true, required = 1)
    public abstract static class AddToMarkList extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object rbGCMark(Object markedObject,
                @Cached InlinedBranchProfile noExceptionProfile,
                @Cached UnwrapNode.ToWrapperNode toWrapperNode) {
            ValueWrapper wrappedValue = toWrapperNode.execute(this, markedObject);
            if (wrappedValue != null) {
                noExceptionProfile.enter(this);
                getContext().getMarkingService()
                        .addMark(getLanguage().getCurrentThread().getCurrentFiber().extensionCallStack, wrappedValue);
            }
            // We do nothing here if the handle cannot be resolved. If we are marking an object
            // which is only reachable via weak refs then the handles of objects it is itself
            // marking may have already been removed from the handle map.
            return nil;
        }

    }

    @CoreMethod(names = "rb_tr_gc_guard", onSingleton = true, required = 1)
    public abstract static class GCGuardNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object addToMarkList(Object guardedObject,
                @Cached MarkingServiceNodes.KeepAliveNode keepAliveNode,
                @Cached InlinedBranchProfile noExceptionProfile,
                @Cached UnwrapNode.ToWrapperNode toWrapperNode) {
            ValueWrapper wrappedValue = toWrapperNode.execute(this, guardedObject);
            if (wrappedValue != null) {
                noExceptionProfile.enter(this);
                keepAliveNode.execute(this, wrappedValue);
            }
            return nil;
        }

    }

    @CoreMethod(names = "set_mark_list_on_object", onSingleton = true, required = 1)
    public abstract static class SetMarkList extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object setMarkList(RubyDynamicObject structOwner,
                @Cached WriteObjectFieldNode writeMarkedNode) {
            writeMarkedNode.execute(
                    this,
                    structOwner,
                    Layouts.MARKED_OBJECTS_IDENTIFIER,
                    getContext().getMarkingService()
                            .finishMarking(getLanguage().getCurrentThread().getCurrentFiber().extensionCallStack));
            return nil;
        }
    }

    @CoreMethod(names = "rb_thread_check_ints", onSingleton = true, required = 0)
    public abstract static class CheckThreadInterrupt extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object checkInts() {
            TruffleSafepoint.pollHere(this);
            return nil;
        }
    }

    @CoreMethod(names = "rb_tr_is_native_object_function", onSingleton = true, required = 0)
    public abstract static class IsNativeObjectFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object isNativeObjectFunction() {
            return new ValueWrapperManager.IsNativeObjectFunction();
        }
    }

    @CoreMethod(names = "rb_tr_unwrap_function", onSingleton = true, required = 0)
    public abstract static class UnwrapperFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object unwrapFunction() {
            return new ValueWrapperManager.UnwrapperFunction();
        }
    }

    @CoreMethod(names = "rb_tr_id2sym_function", onSingleton = true, required = 0)
    public abstract static class UnwrapperIDFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object unwrapFunction() {
            return new ValueWrapperManager.ID2SymbolFunction();
        }
    }

    @CoreMethod(names = "rb_tr_sym2id_function", onSingleton = true, required = 0)
    public abstract static class Sym2IDFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object unwrapFunction() {
            return new ValueWrapperManager.Symbol2IDFunction();
        }
    }

    @CoreMethod(names = "rb_tr_wrap_function", onSingleton = true, required = 0)
    public abstract static class WrapperFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object wrapFunction() {
            return new ValueWrapperManager.WrapperFunction();
        }
    }

    @CoreMethod(names = "rb_tr_force_native_function", onSingleton = true, required = 0)
    public abstract static class ToNativeFunctionNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object wrapFunction() {
            return new ValueWrapperManager.ToNativeObjectFunction();
        }
    }

    @CoreMethod(names = "rb_check_symbol_cstr", onSingleton = true, required = 1)
    public abstract static class RbCheckSymbolCStrNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object checkSymbolCStr(Object string,
                @Cached RubyStringLibrary strings) {
            final RubySymbol sym = getLanguage().symbolTable.getSymbolIfExists(
                    strings.getTString(this, string),
                    strings.getEncoding(this, string));
            return sym == null ? nil : sym;
        }
    }

    @CoreMethod(names = "rb_ary_new_from_values", onSingleton = true, required = 1)
    public abstract static class RbAryNewFromValues extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyArray rbAryNewFromValues(Object cArray,
                @Cached UnwrapCArrayNode unwrapCArrayNode) {
            final Object[] values = unwrapCArrayNode.execute(cArray);
            return createArray(values);
        }
    }

    @CoreMethod(names = "rb_tr_sprintf_types", onSingleton = true, required = 1)
    public abstract static class RBSprintfFormatNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        RubyArray types(Object format,
                @Cached RubyStringLibrary libFormat,
                @Cached AsTruffleStringNode asTruffleStringNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
            AbstractTruffleString tstring = libFormat.getTString(this, format);
            RubyEncoding encoding = libFormat.getEncoding(this, format);

            var cacheKey = new TStringWithEncoding(asTruffleStringNode, tstring, encoding);
            int[] cachedTypes = RubyLanguage.sprintfCompilerTypeLists.get(cacheKey);
            if (cachedTypes != null) {
                return createArray(cachedTypes);
            }

            RubyArray types = compileArgTypes(tstring, libFormat.getEncoding(this, format), byteArrayNode);
            int[] typesIntArray = (int[]) ArrayStoreLibrary.getUncached().toJavaArrayCopy(types.getStore(), types.size);
            RubyLanguage.sprintfCompilerTypeLists.putIfAbsent(cacheKey, typesIntArray);
            return types;
        }

        @TruffleBoundary
        protected RubyArray compileArgTypes(AbstractTruffleString format, RubyEncoding encoding,
                TruffleString.GetInternalByteArrayNode byteArrayNode) {
            try {
                return new RBSprintfCompiler(getLanguage(), this)
                        .typeList(format, encoding, byteArrayNode, getContext(), getLanguage());
            } catch (InvalidFormatException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
        }
    }

    @CoreMethod(names = "rb_tr_sprintf", onSingleton = true, required = 3, split = Split.ALWAYS)
    public abstract static class RBSprintfNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        static RubyString format(Object format, Object stringReader, RubyArray argArray,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode,
                @Cached RubyStringLibrary libFormat,
                @Cached AsTruffleStringNode asTruffleStringNode,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached InlinedBranchProfile exceptionProfile,
                @Cached InlinedConditionProfile resizeProfile,
                @Cached IndirectCallNode formatNode,
                @Bind Node node) {
            var tstring = libFormat.getTString(node, format);
            var encoding = libFormat.getEncoding(node, format);
            final Object[] arguments = arrayToObjectArrayNode.executeToObjectArray(argArray);

            RootCallTarget callTarget;
            try {
                callTarget = compileFormat(tstring, encoding, stringReader, asTruffleStringNode, node);
            } catch (FormatException e) {
                exceptionProfile.enter(node);
                throw FormatExceptionTranslator.translate(getContext(node), node, e);
            }

            final BytesResult result = (BytesResult) formatNode.call(callTarget,
                    new Object[]{ arguments, arguments.length, null });

            int formatLength = tstring.byteLength(encoding.tencoding);
            byte[] bytes = result.getOutput();

            if (resizeProfile.profile(node, bytes.length != result.getOutputLength())) {
                bytes = Arrays.copyOf(bytes, result.getOutputLength());
            }

            return createString(node, fromByteArrayNode, bytes,
                    result.getEncoding().getEncodingForLength(formatLength));
        }

        @TruffleBoundary
        protected static RootCallTarget compileFormat(AbstractTruffleString format, RubyEncoding encoding,
                Object stringReader, AsTruffleStringNode asTruffleStringNode, Node node) {
            var cacheKey = new TStringWithEncoding(asTruffleStringNode, format, encoding);

            RootCallTarget cachedCallTarget = RubyLanguage.sprintfCompilerCallTargets.get(cacheKey);
            if (cachedCallTarget != null) {
                return cachedCallTarget;
            }

            RootCallTarget callTarget;
            try {
                callTarget = new RBSprintfCompiler(getLanguage(node), node).compile(format, encoding, stringReader);
            } catch (InvalidFormatException e) {
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError(e.getMessage(), node));
            }

            RubyLanguage.sprintfCompilerCallTargets.putIfAbsent(cacheKey, callTarget);
            return callTarget;
        }

    }

    @CoreMethod(names = "ruby_native_thread_p", onSingleton = true)
    public abstract static class RubyThreadNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isRubyThread(VirtualFrame frame) {
            ThreadManager threadManager = getContext().getThreadManager();
            return Thread.currentThread() == threadManager.getRootJavaThread() ||
                    threadManager.isRubyManagedThread(Thread.currentThread());
        }
    }

    @CoreMethod(names = "zlib_get_crc_table", onSingleton = true)
    public abstract static class ZLibGetCRCTable extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyArray zlibGetCRCTable() {
            return createArray(ZLibCRCTable.TABLE.clone());
        }
    }

    @CoreMethod(names = "set_thread_safe", onSingleton = true, required = 1, lowerFixnum = 1)
    public abstract static class SetThreadSafe extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object setThreadSafe(int threadSafe) {
            getLanguage().getCurrentFiber().threadSafeExtension = threadSafe != 0;
            return nil;
        }
    }

}
