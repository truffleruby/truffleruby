/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

import java.util.Arrays;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.truffleruby.Layouts;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.collections.SimpleStack;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.array.ArrayBuilderNode.BuilderState;
import org.truffleruby.core.array.ArrayEachIteratorNode.ArrayElementConsumerNode;
import org.truffleruby.core.array.ArrayIndexNodes.ReadNormalizedNode;
import org.truffleruby.core.array.ArrayIndexNodes.ReadSliceNormalizedNode;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.NativeArrayStorage;
import org.truffleruby.core.array.library.SharedArrayStorage;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.CmpIntNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToAryNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToIntClampNode;
import org.truffleruby.core.cast.ToLongNode;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.format.BytesResult;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.FormatRootNode;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.pack.PackCompiler;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqualNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.FixnumLowerNode;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.range.RangeNodes.NormalizedStartLengthNode;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.support.TypeNodes.CheckFrozenNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.WarningNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.control.DeferredRaiseException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;
import org.truffleruby.language.objects.shared.IsSharedNode;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;

@CoreModule(value = "Array", isClass = true)
public abstract class ArrayNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().arrayShape;
            final RubyArray array = new RubyArray(rubyClass, shape, ArrayStoreLibrary.initialStorage(false), 0);
            AllocationTracing.trace(array, this);
            return array;
        }
    }

    @CoreMethod(names = "+", required = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class AddNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                limit = "storageStrategyLimit()")
        static RubyArray addGeneralize(RubyArray a, Object bObject,
                @Cached ToAryNode toAryNode,
                @Bind Node node,
                @Bind("toAryNode.execute(node, bObject)") RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary as,
                @CachedLibrary("bStore") ArrayStoreLibrary bs,
                @Cached IntValueProfile aSizeProfile,
                @Cached IntValueProfile bSizeProfile) {
            final int aSize = aSizeProfile.profile(a.size);
            final int bSize = bSizeProfile.profile(b.size);
            final int combinedSize = aSize + bSize;
            Object newStore = as.unsharedAllocateForNewStore(aStore, bStore, combinedSize);
            as.copyContents(aStore, 0, newStore, 0, aSize);
            bs.copyContents(bStore, 0, newStore, aSize, bSize);
            return createArray(node, newStore, combinedSize);
        }

    }

    @Primitive(name = "array_mul", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class MulNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "count < 0")
        RubyArray mulNeg(RubyArray array, long count) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative argument", this));
        }

        @Specialization(guards = "count == 0")
        RubyArray mulZero(RubyArray array, int count) {
            return createEmptyArray();
        }

        @Specialization(guards = { "count > 0", "isEmptyArray(array)" })
        RubyArray mulEmpty(RubyArray array, long count) {
            return createEmptyArray();
        }

        @Specialization(
                guards = { "count > 0", "!isEmptyArray(array)" },
                limit = "storageStrategyLimit()")
        RubyArray mulOther(RubyArray array, int count,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary arrays,
                @Cached IntValueProfile arraySizeProfile,
                @Cached LoopConditionProfile loopProfile) {

            final int size = arraySizeProfile.profile(array.size);
            final int newSize;
            try {
                newSize = Math.multiplyExact(size, count);
            } catch (ArithmeticException e) {
                throw new RaiseException(getContext(), coreExceptions().rangeError("new array size too large", this));
            }
            final Object newStore = arrays.unsharedAllocator(store).allocate(newSize);
            int n = 0;
            try {
                for (; loopProfile.inject(n < count); n++) {
                    arrays.copyContents(store, 0, newStore, n * size, size);
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, n);
            }

            return createArray(newStore, newSize);
        }

        @Specialization(guards = { "count > 0", "!isEmptyArray(array)", "!fitsInInteger(count)" })
        RubyArray mulLong(RubyArray array, long count) {
            throw new RaiseException(getContext(), coreExceptions().rangeError("array size too big", this));
        }

        @Specialization(guards = { "!isImplicitLong(count)" })
        Object fallback(RubyArray array, Object count) {
            return FAILURE;
        }
    }

    @CoreMethod(names = { "at" }, required = 1, lowerFixnum = 1)
    public abstract static class AtNode extends CoreMethodArrayArgumentsNode {

        abstract Object executeAt(RubyArray array, Object index);

        @NeverDefault
        public static AtNode create() {
            return ArrayNodesFactory.AtNodeFactory.create(new RubyNode[]{ null, null });
        }

        @Specialization
        Object at(RubyArray array, int index,
                @Cached ReadNormalizedNode readNormalizedNode,
                @Cached ConditionProfile denormalized) {
            if (denormalized.profile(index < 0)) {
                index += array.size;
            }
            return readNormalizedNode.executeRead(array, index);
        }

        @Specialization
        Object at(RubyArray array, long index) {
            assert !CoreLibrary.fitsIntoInteger(index);
            return nil;
        }

        @Specialization(guards = "!isImplicitLong(index)")
        static Object at(RubyArray array, Object index,
                @Cached ToLongNode toLongNode,
                @Cached FixnumLowerNode lowerNode,
                @Cached AtNode atNode,
                @Bind Node node) {
            return atNode.executeAt(array, lowerNode.execute(node, toLongNode.execute(node, index)));
        }
    }

    @CoreMethod(
            names = { "[]", "slice" },
            split = Split.ALWAYS,
            required = 1,
            optional = 1,
            lowerFixnum = { 1, 2 },
            argumentNames = { "index_start_or_range", "length" })
    public abstract static class IndexNode extends ArrayCoreMethodNode {

        abstract Object executeIntIndices(RubyArray array, int start, int length);

        @Specialization
        Object index(RubyArray array, int index, NotProvided length,
                @Cached @Shared ConditionProfile negativeIndexProfile,
                @Cached ReadNormalizedNode readNode) {
            if (negativeIndexProfile.profile(index < 0)) {
                index += array.size;
            }
            return readNode.executeRead(array, index);
        }

        @Specialization(guards = "isRubyRange(range)")
        Object indexRange(RubyArray array, Object range, NotProvided length,
                @Cached NormalizedStartLengthNode startLengthNode,
                @Cached @Shared ReadSliceNormalizedNode readSliceNode) {
            final int[] startLength = startLengthNode.execute(range, array.size);
            final int len = Math.max(startLength[1], 0); // negative range ending maps to zero length
            return readSliceNode.executeReadSlice(array, startLength[0], len);
        }

        @Specialization(guards = "isArithmeticSequence(enumerator, isANode)")
        Object indexArithmeticSequence(RubyArray array, Object enumerator, NotProvided length,
                @Cached @Shared IsANode isANode,
                @Cached DispatchNode callSliceArithmeticSequence) {
            return callSliceArithmeticSequence.call(array, "slice_arithmetic_sequence", enumerator);
        }

        @Specialization(
                guards = {
                        "!isInteger(index)",
                        "!isRubyRange(index)",
                        "!isArithmeticSequence(index, isANode)" })
        Object indexFallback(RubyArray array, Object index, NotProvided length,
                @Cached @Shared IsANode isANode,
                @Cached AtNode accessWithIndexConversion) {
            return accessWithIndexConversion.executeAt(array, index);
        }

        @Specialization
        Object slice(RubyArray array, int start, int length,
                @Cached @Shared ReadSliceNormalizedNode readSliceNode,
                @Cached @Shared ConditionProfile negativeIndexProfile) {
            if (negativeIndexProfile.profile(start < 0)) {
                start += array.size;
            }
            return readSliceNode.executeReadSlice(array, start, length);
        }

        @Specialization(guards = { "wasProvided(length)", "!isInteger(start) || !isInteger(length)" })
        Object sliceFallback(RubyArray array, Object start, Object length,
                @Cached ToIntClampNode indexToInt,
                @Cached ToIntClampNode lengthToInt) {
            return executeIntIndices(array, indexToInt.execute(start), lengthToInt.execute(length));
        }

        protected boolean isArithmeticSequence(Object object, IsANode isANode) {
            return isANode.executeIsA(object, coreLibrary().arithmeticSequenceClass);
        }
    }

    @CoreMethod(
            names = "[]=",
            split = Split.ALWAYS,
            required = 2,
            optional = 1,
            lowerFixnum = { 1, 2 },
            raiseIfFrozenSelf = true,
            argumentNames = { "index_start_or_range", "length_or_value", "value" })
    @ImportStatic(ArrayHelpers.class)
    public abstract static class SetIndexNode extends ArrayCoreMethodNode {

        public static SetIndexNode create() {
            return ArrayNodesFactory.SetIndexNodeFactory.create(null);
        }

        abstract Object executeIntIndex(RubyArray array, int index, Object value, NotProvided unused);

        abstract Object executeIntIndices(RubyArray array, int index, int length, Object replacement);

        // array[index] = object

        @Specialization
        Object set(RubyArray array, int index, Object value, NotProvided unused,
                @Cached ArrayWriteNormalizedNode writeNode,
                @Cached @Shared ConditionProfile negativeDenormalizedIndex,
                @Cached @Shared BranchProfile negativeNormalizedIndex) {
            final int size = array.size;
            final int nIndex = normalize(size, index, negativeDenormalizedIndex, negativeNormalizedIndex);
            return writeNode.executeWrite(array, nIndex, value);
        }

        @Specialization(guards = "isRubyRange(range)")
        Object setRange(RubyArray array, Object range, Object value, NotProvided unused,
                @Cached NormalizedStartLengthNode normalizedStartLength,
                @Cached @Exclusive BranchProfile negativeStart) {
            final int[] startLength = normalizedStartLength.execute(range, array.size);
            final int start = startLength[0];
            if (start < 0) {
                negativeStart.enter();
                throw new RaiseException(
                        getContext(),
                        coreExceptions().rangeError(Utils.concat("index ", start, " out of bounds"), this));
            }
            final int length = Math.max(startLength[1], 0); // negative range ending maps to zero length
            return executeIntIndices(array, start, length, value);
        }

        @Specialization(guards = { "!isInteger(start)", "!isRubyRange(start)" })
        Object fallbackBinary(RubyArray array, Object start, Object value, NotProvided unused,
                @Cached @Shared ToIntNode startToInt) {
            return executeIntIndex(array, startToInt.execute(start), value, unused);
        }

        // array[start, length] = array2

        @Specialization(guards = { "wasProvided(replacement)", "length < 0" })
        Object negativeLength(RubyArray array, int start, int length, Object replacement) {
            throw new RaiseException(getContext(), coreExceptions().negativeLengthError(length, this));
        }

        @Specialization(guards = "length >= 0")
        Object setTernary(RubyArray array, int start, int length, RubyArray replacement,
                @Cached @Shared ConditionProfile negativeDenormalizedIndex,
                @Cached @Shared BranchProfile negativeNormalizedIndex,
                @Cached @Exclusive ConditionProfile moveNeeded,
                @Cached ArrayPrepareForCopyNode prepareToCopy,
                @Cached ArrayCopyCompatibleRangeNode shift,
                @Cached ArrayCopyCompatibleRangeNode copyRange,
                @Cached ArrayTruncateNode truncate) {

            final int originalSize = array.size;
            start = normalize(originalSize, start, negativeDenormalizedIndex, negativeNormalizedIndex);
            final Object replacementStore = replacement.getStore();
            final int replacementSize = replacement.size;
            final int overwrittenAreaEnd = start + length;
            final int tailSize = originalSize - overwrittenAreaEnd;

            if (moveNeeded.profile(tailSize > 0)) {
                // There is a tail (the part of the array to the right of the overwritten area) to be moved.
                // Possibly, this is a move of size 0 (replacement size == length), which is optimized in the copy node.

                final int writtenAreaEnd = start + replacementSize;
                final int newSize = originalSize - length + replacementSize;
                final int requiredLength = newSize - start;

                final Object newStore = prepareToCopy.execute(array, replacement, start, requiredLength);
                shift.execute(
                        newStore,
                        newStore,
                        writtenAreaEnd,
                        overwrittenAreaEnd,
                        tailSize);
                copyRange.execute(newStore, replacementStore, start, 0, replacementSize);
                truncate.execute(array, newSize);

            } else {
                // The array is overwritten from `start` to end, there is no tail to be moved.

                final Object newStore = prepareToCopy.execute(array, replacement, start, replacementSize);
                copyRange.execute(newStore, replacementStore, start, 0, replacementSize);
                truncate.execute(array, start + replacementSize);
            }

            return replacement;
        }

        @Specialization(guards = {
                "!isRubyArray(replacement)",
                "wasProvided(replacement)",
                "length >= 0" })
        Object setTernary(RubyArray array, int start, int length, Object replacement,
                @Cached ArrayConvertNode convert,
                @Cached SetIndexNode recurse) {
            recurse.executeIntIndices(array, start, length, convert.execute(replacement));
            return replacement;
        }

        @Specialization(
                guards = { "!isInteger(start) || !isInteger(length)", "wasProvided(replacement)" })
        Object fallbackTernary(RubyArray array, Object start, Object length, Object replacement,
                @Cached @Shared ToIntNode startToInt,
                @Cached @Exclusive ToIntNode lengthToInt) {
            return executeIntIndices(array, startToInt.execute(start), lengthToInt.execute(length), replacement);
        }

        // Helpers

        protected int normalize(int arraySize, int index,
                ConditionProfile negativeDenormalizedIndex, BranchProfile negativeNormalizedIndex) {
            if (negativeDenormalizedIndex.profile(index < 0)) {
                index += arraySize;
                if (index < 0) {
                    negativeNormalizedIndex.enter();
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().indexTooSmallError("array", index, arraySize, this));
                }
            }
            return index;
        }
    }

    @CoreMethod(names = "clear", raiseIfFrozenSelf = true)
    public abstract static class ClearNode extends ArrayCoreMethodNode {

        @Specialization
        RubyArray clear(RubyArray array,
                @Cached IsSharedNode isSharedNode) {
            array.setStoreAndSize(
                    ArrayStoreLibrary.initialStorage(isSharedNode.execute(this, array)),
                    0);
            return array;
        }

    }

    @CoreMethod(names = "compact")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class CompactNode extends ArrayCoreMethodNode {

        @ReportPolymorphism.Exclude
        @Specialization(guards = "stores.isPrimitive(store)", limit = "storageStrategyLimit()")
        RubyArray compactPrimitive(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached ArrayCopyOnWriteNode cowNode) {
            final int size = array.size;
            return createArray(cowNode.execute(array, 0, size), size);
        }

        @Specialization(guards = "!stores.isPrimitive(store)", limit = "storageStrategyLimit()")
        Object compactObjects(RubyArray array,
                @Bind("array.getStore()") Object store,
                @Cached IntValueProfile arraySizeProfile,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached ArrayBuilderNode arrayBuilder,
                @Cached LoopConditionProfile loopProfile) {
            final int size = arraySizeProfile.profile(array.size);
            BuilderState state = arrayBuilder.start(size);

            int m = 0;
            int n = 0;
            try {
                for (; loopProfile.inject(n < size); n++) {
                    Object v = stores.read(store, n);
                    if (v != nil) {
                        arrayBuilder.appendValue(state, m, v);
                        m++;
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, n);
            }

            return createArray(arrayBuilder.finish(state, m), m);
        }

    }

    @CoreMethod(names = "compact!", raiseIfFrozenSelf = true)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class CompactBangNode extends ArrayCoreMethodNode {

        @ReportPolymorphism.Exclude
        @Specialization(guards = "stores.isPrimitive(store)", limit = "storageStrategyLimit()")
        Object compactNotObjects(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return nil;
        }

        @Specialization(guards = "!stores.isPrimitive(store)", limit = "storageStrategyLimit()")
        Object compactObjects(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @CachedLibrary(limit = "1") ArrayStoreLibrary mutableStores,
                @Cached IntValueProfile arraySizeProfile,
                @Cached LoopConditionProfile loopProfile) {
            final int size = arraySizeProfile.profile(array.size);
            final Object oldStore = store;
            final Object newStore;
            if (!stores.isMutable(oldStore)) {
                newStore = stores.allocator(oldStore).allocate(size);
            } else {
                newStore = oldStore;
            }

            int m = 0;
            int n = 0;
            try {
                for (; loopProfile.inject(n < size); n++) {
                    Object v = stores.read(oldStore, n);
                    if (v != nil) {
                        mutableStores.write(newStore, m, v);
                        m++;
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, n);
            }

            stores.clear(oldStore, m, size - m);

            array.setStoreAndSize(newStore, m);

            if (m == size) {
                return nil;
            } else {
                return array;
            }
        }
    }

    @CoreMethod(names = "concat", optional = 1, rest = true, raiseIfFrozenSelf = true)
    @ReportPolymorphism // inline cache
    @ImportStatic(ArrayGuards.class)
    public abstract static class ConcatNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "rest.length == 0")
        RubyArray concatZero(RubyArray array, NotProvided first, Object[] rest) {
            return array;
        }

        @Specialization(guards = { "wasProvided(first)", "rest.length == 0" })
        RubyArray concatOne(RubyArray array, Object first, Object[] rest,
                @Cached @Shared ToAryNode toAryNode,
                @Cached @Shared ArrayAppendManyNode appendManyNode) {
            appendManyNode.executeAppendMany(array, toAryNode.execute(this, first));
            return array;
        }

        @ExplodeLoop
        @Specialization(
                guards = {
                        "wasProvided(first)",
                        "rest.length > 0",
                        "rest.length == cachedLength",
                        "cachedLength <= MAX_EXPLODE_SIZE" },
                limit = "getDefaultCacheLimit()")
        RubyArray concatMany(RubyArray array, Object first, Object[] rest,
                @Cached("rest.length") int cachedLength,
                @Cached @Shared ToAryNode toAryNode,
                @Cached @Shared ArrayAppendManyNode appendManyNode,
                @Cached @Shared ArrayCopyOnWriteNode cowNode,
                @Cached @Shared ConditionProfile selfArgProfile) {
            int size = array.size;
            RubyArray copy = createArray(cowNode.execute(array, 0, size), size);
            RubyArray result = appendManyNode.executeAppendMany(array, toAryNode.execute(this, first));
            for (int i = 0; i < cachedLength; ++i) {
                final RubyArray argOrCopy = selfArgProfile.profile(rest[i] == array)
                        ? copy
                        : toAryNode.execute(this, rest[i]);
                result = appendManyNode.executeAppendMany(array, argOrCopy);
            }
            return result;
        }

        /** Same implementation as {@link #concatMany}, safe for the use of {@code cachedLength} */
        @Specialization(
                guards = { "wasProvided(first)", "rest.length > 0" },
                replaces = "concatMany")
        RubyArray concatManyGeneral(RubyArray array, Object first, Object[] rest,
                @Cached @Shared ToAryNode toAryNode,
                @Cached @Shared ArrayAppendManyNode appendManyNode,
                @Cached @Shared ArrayCopyOnWriteNode cowNode,
                @Cached @Shared ConditionProfile selfArgProfile,
                @Cached LoopConditionProfile loopProfile) {
            final int size = array.size;
            Object store = cowNode.execute(array, 0, size);

            RubyArray result = appendManyNode.executeAppendMany(array, toAryNode.execute(this, first));
            int i = 0;
            try {
                for (; loopProfile.inject(i < rest.length); i++) {
                    Object arg = rest[i];
                    if (selfArgProfile.profile(arg == array)) {
                        result = appendManyNode.executeAppendMany(array, createArray(store, size));
                    } else {
                        result = appendManyNode.executeAppendMany(array, toAryNode.execute(this, arg));
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i);
            }
            return result;
        }
    }

    @CoreMethod(names = "delete", required = 1, needsBlock = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class DeleteNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                guards = "stores.isMutable(store)",
                limit = "storageStrategyLimit()")
        Object deleteMutable(RubyArray array, Object value, Object maybeBlock,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared SameOrEqualNode sameOrEqualNode,
                @Cached @Shared InlinedIntValueProfile arraySizeProfile,
                @Cached @Shared InlinedLoopConditionProfile loopProfile,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared CheckFrozenNode raiseIfFrozenNode) {

            return delete(array, value, maybeBlock, true, store, store, stores, stores, arraySizeProfile, loopProfile,
                    yieldNode, sameOrEqualNode, raiseIfFrozenNode);
        }

        @Specialization(
                guards = "!stores.isMutable(store)",
                limit = "storageStrategyLimit()")
        Object deleteNotMutable(RubyArray array, Object value, Object maybeBlock,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @CachedLibrary(limit = "1") ArrayStoreLibrary newStores,
                @Cached @Shared SameOrEqualNode sameOrEqualNode,
                @Cached @Shared InlinedIntValueProfile arraySizeProfile,
                @Cached @Shared InlinedLoopConditionProfile loopProfile,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared CheckFrozenNode raiseIfFrozenNode) {

            final Object newStore = stores.allocator(store).allocate(arraySizeProfile.profile(this, array.size));
            return delete(array, value, maybeBlock, false, store, newStore, stores, newStores, arraySizeProfile,
                    loopProfile, yieldNode, sameOrEqualNode, raiseIfFrozenNode);
        }

        private Object delete(RubyArray array, Object value, Object maybeBlock,
                boolean sameStores,
                Object oldStore,
                Object newStore,
                ArrayStoreLibrary oldStores,
                ArrayStoreLibrary newStores,
                InlinedIntValueProfile arraySizeProfile,
                InlinedLoopConditionProfile loopProfile,
                CallBlockNode yieldNode,
                SameOrEqualNode sameOrEqualNode,
                CheckFrozenNode raiseIfFrozenNode) {

            assert !sameStores || (oldStore == newStore && oldStores == newStores);

            final int size = arraySizeProfile.profile(this, array.size);
            Object found = nil;

            int i = 0;
            int n = 0;
            try {
                while (loopProfile.inject(this, n < size)) {
                    final Object stored = oldStores.read(oldStore, n);

                    if (sameOrEqualNode.execute(this, stored, value)) {
                        raiseIfFrozenNode.execute(this, array);
                        found = stored;
                        n++;
                    } else {
                        newStores.write(newStore, i, oldStores.read(oldStore, n));

                        i++;
                        n++;
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(this, loopProfile, n);
            }

            if (i != n) {
                if (sameStores) {
                    oldStores.clear(oldStore, i, size - i);
                }
                array.setStoreAndSize(newStore, i);
                return found;
            } else {
                if (maybeBlock == nil) {
                    return nil;
                } else {
                    return yieldNode.yield(this, (RubyProc) maybeBlock, value);
                }
            }
        }
    }

    @CoreMethod(names = "delete_at", required = 1, raiseIfFrozenSelf = true, lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class DeleteAtNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "storageStrategyLimit()")
        static Object doDelete(RubyArray array, Object indexObject,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached ToIntNode toIntNode,
                @Cached InlinedIntValueProfile arraySizeProfile,
                @Cached InlinedConditionProfile negativeIndexProfile,
                @Cached InlinedConditionProfile notInBoundsProfile,
                @Cached InlinedConditionProfile isMutableProfile,
                @Bind Node node) {
            final int size = arraySizeProfile.profile(node, array.size);
            final int index = toIntNode.execute(indexObject);
            int i = index;
            if (negativeIndexProfile.profile(node, index < 0)) {
                i += size;
            }

            if (notInBoundsProfile.profile(node, i < 0 || i >= size)) {
                return nil;
            }

            if (isMutableProfile.profile(node, stores.isMutable(store))) {
                final Object value = stores.read(store, i);
                stores.copyContents(store, i + 1, store, i, size - i - 1);
                stores.clear(store, size - 1, 1);
                array.setStoreAndSize(store, size - 1);
                return value;
            } else {
                final Object mutableStore = stores.allocator(store).allocate(size - 1);
                stores.copyContents(store, 0, mutableStore, 0, i);
                final Object value = stores.read(store, i);
                stores.copyContents(store, i + 1, mutableStore, i, size - i - 1);
                array.setStoreAndSize(mutableStore, size - 1);
                return value;
            }
        }
    }

    @CoreMethod(names = "each", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EachNode extends CoreMethodArrayArgumentsNode implements ArrayElementConsumerNode {

        @Specialization
        Object each(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(this, array, block, 0, this);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object state, Object element, int index,
                BooleanCastNode booleanCastNode) {
            RubyProc block = (RubyProc) state;
            yieldNode.yield(node, block, element);
        }

    }

    @Primitive(name = "array_each_with_index")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EachWithIndexNode extends PrimitiveArrayArgumentsNode
            implements ArrayElementConsumerNode {

        @Specialization
        Object eachOther(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(this, array, block, 0, this);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object state, Object element, int index,
                BooleanCastNode booleanCastNode) {
            RubyProc block = (RubyProc) state;
            yieldNode.yield(node, block, element, index);
        }

    }

    @CoreMethod(names = "empty?")
    public abstract static class EmptyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isEmpty(RubyArray array) {
            return array.size == 0;
        }
    }

    @Primitive(name = "array_equal?")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EqualNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "stores.accepts(bStore)", "stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        static boolean equalSamePrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores,
                @Cached InlinedConditionProfile sameProfile,
                @Cached InlinedIntValueProfile arraySizeProfile,
                @Cached InlinedConditionProfile sameSizeProfile,
                @Cached InlinedBranchProfile trueProfile,
                @Cached InlinedBranchProfile falseProfile,
                @Cached InlinedLoopConditionProfile loopProfile,
                @Cached SameOrEqualNode sameOrEqualNode,
                @Bind Node node) {

            if (sameProfile.profile(node, a == b)) {
                return true;
            }

            final int size = arraySizeProfile.profile(node, a.size);

            if (!sameSizeProfile.profile(node, size == b.size)) {
                return false;
            }

            int i = 0;
            try {
                for (; loopProfile.inject(node, i < size); i++) {
                    if (!sameOrEqualNode.execute(node, stores.read(aStore, i), stores.read(bStore, i))) {
                        falseProfile.enter(node);
                        return false;
                    }
                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, i);
            }
            trueProfile.enter(node);
            return true;
        }

        @Specialization(
                guards = { "!stores.accepts(bStore)", "stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        Object equalDifferentPrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores) {
            return FAILURE;
        }

        @Specialization(
                guards = { "stores.accepts(aStore)", "!stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        Object equalNotPrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyArray(b)")
        Object equalNotArray(RubyArray a, Object b) {
            return FAILURE;
        }

    }

    @Primitive(name = "array_eql?")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EqlNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "stores.accepts(bStore)", "stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        static boolean eqlSamePrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores,
                @Cached SameOrEqlNode eqlNode,
                @Cached InlinedConditionProfile sameProfile,
                @Cached InlinedIntValueProfile arraySizeProfile,
                @Cached InlinedConditionProfile sameSizeProfile,
                @Cached InlinedBranchProfile trueProfile,
                @Cached InlinedBranchProfile falseProfile,
                @Cached InlinedLoopConditionProfile loopProfile,
                @Bind Node node) {

            if (sameProfile.profile(node, a == b)) {
                return true;
            }

            final int size = arraySizeProfile.profile(node, a.size);

            if (!sameSizeProfile.profile(node, size == b.size)) {
                return false;
            }

            int i = 0;
            try {
                for (; loopProfile.inject(node, i < size); i++) {
                    if (!eqlNode.execute(node, stores.read(aStore, i), stores.read(bStore, i))) {
                        falseProfile.enter(node);
                        return false;
                    }
                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, i);
            }

            trueProfile.enter(node);
            return true;
        }

        @Specialization(
                guards = { "!stores.accepts(bStore)", "stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        Object eqlDifferentPrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @Bind("b.getStore()") Object bStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores) {
            return FAILURE;
        }

        @Specialization(
                guards = { "!stores.isPrimitive(aStore)" },
                limit = "storageStrategyLimit()")
        Object eqlNotPrimitiveType(RubyArray a, RubyArray b,
                @Bind("a.getStore()") Object aStore,
                @CachedLibrary("aStore") ArrayStoreLibrary stores) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyArray(b)")
        Object eqlNotArray(RubyArray a, Object b) {
            return FAILURE;
        }

    }

    @CoreMethod(names = "fill", rest = true, needsBlock = true, raiseIfFrozenSelf = true)
    public abstract static class FillNode extends ArrayCoreMethodNode {

        @Specialization(
                guards = { "args.length == 1", "stores.acceptsValue(store, value(args))" },
                limit = "storageStrategyLimit()")
        RubyArray fill(RubyArray array, Object[] args, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached IntValueProfile arraySizeProfile,
                @Cached LoopConditionProfile loopProfile) {
            final Object value = args[0];

            final int size = arraySizeProfile.profile(array.size);

            int i = 0;
            try {
                for (; loopProfile.inject(i < size); i++) {
                    stores.write(store, i, value);
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i);
            }
            return array;
        }

        protected Object value(Object[] args) {
            return args[0];
        }

        @Specialization
        Object fillFallback(VirtualFrame frame, RubyArray array, Object[] args, Nil block,
                @Cached @Shared DispatchNode callFillInternal) {
            return callFillInternal.call(array, "fill_internal", args);
        }

        @Specialization
        Object fillFallback(VirtualFrame frame, RubyArray array, Object[] args, RubyProc block,
                @Cached @Shared DispatchNode callFillInternal) {
            return callFillInternal.callWithDescriptor(array, "fill_internal", block,
                    NoKeywordArgumentsDescriptor.INSTANCE, args);
        }

    }

    @Primitive(name = "hash_internal")
    @ImportStatic(ArrayGuards.class)
    public abstract static class HashNode extends PrimitiveArrayArgumentsNode {

        private static final int CLASS_SALT = 42753062; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

        // Inlined profiles/nodes are @Exclusive to fix truffle-interpreted-performance warning

        @Specialization(limit = "storageStrategyLimit()")
        static long hash(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached HashingNodes.ToHashByHashCode toHashByHashCode,
                @Cached @Exclusive InlinedIntValueProfile arraySizeProfile,
                @Cached @Exclusive InlinedLoopConditionProfile loopProfile,
                @Bind Node node) {
            final int size = arraySizeProfile.profile(node, array.size);
            long h = getContext(node).getHashing(node).start(size);
            h = Hashing.update(h, CLASS_SALT);

            int n = 0;
            try {
                for (; loopProfile.inject(node, n < size); n++) {
                    final Object value = stores.read(store, n);
                    h = Hashing.update(h, toHashByHashCode.execute(node, value));
                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, n);
            }

            return Hashing.end(h);
        }

    }

    @CoreMethod(names = "include?", required = 1)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class IncludeNode extends ArrayCoreMethodNode {

        @Specialization(limit = "storageStrategyLimit()")
        static boolean include(RubyArray array, Object value,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached SameOrEqualNode sameOrEqualNode,
                @Cached InlinedIntValueProfile arraySizeProfile,
                @Cached InlinedLoopConditionProfile loopProfile,
                @Bind Node node) {

            int n = 0;
            try {
                for (; loopProfile.inject(node, n < arraySizeProfile.profile(node, array.size)); n++) {
                    final Object stored = stores.read(store, n);

                    if (sameOrEqualNode.execute(node, stored, value)) {
                        return true;
                    }
                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, n);
            }

            return false;
        }

    }

    @CoreMethod(
            names = "initialize",
            needsBlock = true,
            optional = 2,
            raiseIfFrozenSelf = true,
            lowerFixnum = 1,
            argumentNames = { "size_or_copy", "filling_value", "block" })
    @ImportStatic({ ArrayGuards.class, ArrayStoreLibrary.class })
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode toAryNode;

        protected abstract RubyArray executeInitialize(RubyArray array, Object size, Object fillingValue,
                Object block);

        // Inlined profiles/nodes are @Exclusive to fix truffle-interpreted-performance warning

        @Specialization
        RubyArray initializeNoArgs(RubyArray array, NotProvided size, NotProvided fillingValue, Nil block,
                @Cached @Shared IsSharedNode isSharedNode) {
            array.setStoreAndSize(
                    ArrayStoreLibrary.initialStorage(isSharedNode.execute(this, array)),
                    0);
            return array;
        }

        @Specialization
        RubyArray initializeOnlyBlock(RubyArray array, NotProvided size, NotProvided fillingValue, RubyProc block,
                @Cached @Shared IsSharedNode isSharedNode,
                @Cached("new()") WarningNode warningNode) {
            if (warningNode.shouldWarn()) {
                final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
                warningNode.warningMessage(sourceSection, "given block not used");
            }

            array.setStoreAndSize(
                    ArrayStoreLibrary.initialStorage(isSharedNode.execute(this, array)),
                    0);
            return array;
        }

        @TruffleBoundary
        @Specialization(guards = "size < 0")
        RubyArray initializeNegativeIntSize(RubyArray array, int size, Object unusedFillingValue, Object unusedBlock) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative array size", this));
        }

        @TruffleBoundary
        @Specialization(guards = "size < 0")
        RubyArray initializeNegativeLongSize(
                RubyArray array, long size, Object unusedFillingValue, Object unusedBlock) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative array size", this));
        }

        protected static final long MAX_INT = Integer.MAX_VALUE;

        @TruffleBoundary
        @Specialization(guards = "size >= MAX_INT")
        RubyArray initializeSizeTooBig(RubyArray array, long size, NotProvided fillingValue, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("array size too big", this));
        }

        @Specialization(guards = "size >= 0")
        RubyArray initializeWithSizeNoValue(RubyArray array, int size, NotProvided fillingValue, Nil block,
                @Cached @Shared IsSharedNode isSharedNode,
                @CachedLibrary(limit = "storageStrategyLimit()") @Exclusive ArrayStoreLibrary stores) {
            final Object store;
            if (isSharedNode.execute(this, array)) {
                store = new SharedArrayStorage(new Object[size]);
            } else {
                store = new Object[size];
            }
            stores.fill(store, 0, size, nil);
            array.setStoreAndSize(store, size);
            return array;
        }

        @Specialization(
                guards = { "size >= 0", "wasProvided(fillingValue)" },
                limit = "storageStrategyLimit()")
        RubyArray initializeWithSizeAndValue(RubyArray array, int size, Object fillingValue, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @CachedLibrary(limit = "1") ArrayStoreLibrary allocatedStores,
                @Cached @Exclusive ConditionProfile needsFill,
                @Cached @Exclusive LoopConditionProfile loopProfile) {
            final Object allocatedStore = stores.allocateForNewValue(store, fillingValue, size);
            if (needsFill.profile(!allocatedStores.isDefaultValue(allocatedStore, fillingValue))) {
                int i = 0;
                try {
                    for (; loopProfile.inject(i < size); i++) {
                        allocatedStores.write(allocatedStore, i, fillingValue);
                        TruffleSafepoint.poll(this);
                    }
                } finally {
                    profileAndReportLoopCount(loopProfile, i);
                }
            }
            array.setStoreAndSize(allocatedStore, size);
            return array;
        }

        @Specialization(
                guards = { "wasProvided(size)", "!isImplicitLong(size)", "wasProvided(fillingValue)" })
        RubyArray initializeSizeOther(RubyArray array, Object size, Object fillingValue, Nil block,
                @Cached @Shared ToIntNode toIntNode) {
            int intSize = toIntNode.execute(size);
            return executeInitialize(array, intSize, fillingValue, block);
        }

        // With block

        @Specialization(guards = "size >= 0")
        static Object initializeBlock(RubyArray array, int size, Object unusedFillingValue, RubyProc block,
                @Cached ArrayBuilderNode arrayBuilder,
                @CachedLibrary(limit = "storageStrategyLimit()") @Exclusive ArrayStoreLibrary stores,
                // @Exclusive to fix truffle-interpreted-performance warning
                @Cached @Exclusive IsSharedNode isSharedNode,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @Cached CallBlockNode yieldNode,
                @Bind Node node) {
            BuilderState state = arrayBuilder.start(size);

            int n = 0;
            try {
                for (; loopProfile.inject(n < size); n++) {
                    final Object value = yieldNode.yield(node, block, n);
                    arrayBuilder.appendValue(state, n, value);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, n);
                Object store = arrayBuilder.finish(state, n);
                if (isSharedNode.execute(node, array)) {
                    store = stores.makeShared(store, n);
                }
                array.setStoreAndSize(store, n);
            }

            return array;
        }

        @Specialization
        RubyArray initializeFromArray(RubyArray array, RubyArray copy, NotProvided unusedValue, Object maybeBlock,
                @Cached ReplaceNode replaceNode) {
            replaceNode.executeReplace(array, copy);
            return array;
        }

        @Specialization(
                guards = { "!isImplicitLong(object)", "wasProvided(object)", "!isRubyArray(object)" })
        RubyArray initialize(RubyArray array, Object object, NotProvided unusedValue, Nil block,
                @Cached KernelNodes.RespondToNode respondToNode,
                @Cached @Shared ToIntNode toIntNode) {
            RubyArray copy = null;
            if (respondToNode.executeDoesRespondTo(object, coreSymbols().TO_ARY, true)) {
                Object toAryResult = callToAry(object);
                if (toAryResult instanceof RubyArray) {
                    copy = (RubyArray) toAryResult;
                }
            }

            if (copy != null) {
                return executeInitialize(array, copy, NotProvided.INSTANCE, nil);
            } else {
                int size = toIntNode.execute(object);
                return executeInitialize(array, size, NotProvided.INSTANCE, nil);
            }
        }

        protected Object callToAry(Object object) {
            if (toAryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toAryNode = insert(DispatchNode.create());
            }
            return toAryNode.call(object, "to_ary");
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1, raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray initializeCopy(RubyArray self, Object fromObject,
                @Cached ToAryNode toAryNode,
                @Cached ReplaceNode replaceNode) {
            final var from = toAryNode.execute(this, fromObject);
            if (self == from) {
                return self;
            }
            replaceNode.executeReplace(self, from);
            return self;
        }

    }

    @Primitive(name = "array_inject")
    @ImportStatic(ArrayGuards.class)
    public abstract static class InjectNode extends PrimitiveArrayArgumentsNode implements ArrayElementConsumerNode {

        private static final class State {
            Object accumulator;
            final RubyProc block;

            State(Object accumulator, RubyProc block) {
                this.accumulator = accumulator;
                this.block = block;
            }
        }

        @Child private DispatchNode dispatch = DispatchNode.create();

        // Uses block and no Symbol

        @Specialization(guards = { "isEmptyArray(array)", "wasProvided(initialOrSymbol)" })
        Object injectEmptyArray(RubyArray array, Object initialOrSymbol, NotProvided symbol, RubyProc block) {
            return initialOrSymbol;
        }

        @Specialization(guards = { "isEmptyArray(array)" })
        Object injectEmptyArrayNoInitial(
                RubyArray array, NotProvided initialOrSymbol, NotProvided symbol, RubyProc block) {
            return nil;
        }

        @Specialization(guards = { "!isEmptyArray(array)", "wasProvided(initialOrSymbol)" })
        Object injectWithInitial(RubyArray array, Object initialOrSymbol, NotProvided symbol, RubyProc block,
                @Cached @Shared ArrayEachIteratorNode iteratorNode) {
            return injectBlockHelper(array, block, initialOrSymbol, 0, iteratorNode);
        }

        @Specialization(
                guards = { "!isEmptyArray(array)" },
                limit = "storageStrategyLimit()")
        Object injectNoInitial(RubyArray array, NotProvided initialOrSymbol, NotProvided symbol, RubyProc block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared ArrayEachIteratorNode iteratorNode) {
            return injectBlockHelper(array, block, stores.read(store, 0), 1, iteratorNode);
        }

        private Object injectBlockHelper(RubyArray array,
                RubyProc block, Object initial, int start, ArrayEachIteratorNode iteratorNode) {
            Object accumulator = initial;
            State iterationState = new State(accumulator, block);

            iteratorNode.execute(this, array, iterationState, start, this);

            return iterationState.accumulator;
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object stateObject, Object element,
                int index, BooleanCastNode booleanCastNode) {
            final State state = (State) stateObject;
            state.accumulator = yieldNode.yield(node, state.block, state.accumulator, element);
        }

        // Uses Symbol and no block

        @Specialization(guards = { "isEmptyArray(array)", "wasProvided(initialOrSymbol)" })
        Object injectSymbolEmptyArrayNoInitial(RubyArray array, Object initialOrSymbol, NotProvided symbol, Nil block,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            nameToJavaStringNode.execute(this, initialOrSymbol); // ensure a method name is either a Symbol or could be converted to String
            return nil;
        }

        @Specialization(
                guards = {
                        "isEmptyArray(array)",
                        "wasProvided(initialOrSymbol)",
                        "wasProvided(symbol)" })
        Object injectSymbolEmptyArray(RubyArray array, Object initialOrSymbol, Object symbol, Nil block,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            nameToJavaStringNode.execute(this, symbol); // ensure a method name is either a Symbol or could be converted to String
            return initialOrSymbol;
        }

        @Specialization(
                guards = { "!isEmptyArray(array)", "wasProvided(initialOrSymbol)" },
                limit = "storageStrategyLimit()")
        Object injectSymbolNoInitial(
                VirtualFrame frame, RubyArray array, Object initialOrSymbol, NotProvided symbol, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            String methodName = nameToJavaStringNode.execute(this, initialOrSymbol); // ensure a method name is either a Symbol or could be converted to String
            return injectSymbolHelper(
                    frame,
                    array,
                    methodName,
                    stores,
                    store,
                    stores.read(store, 0),
                    1,
                    arraySizeProfile,
                    loopProfile);
        }

        @Specialization(
                guards = {
                        "!isEmptyArray(array)",
                        "wasProvided(initialOrSymbol)",
                        "wasProvided(symbol)" },
                limit = "storageStrategyLimit()")
        Object injectSymbolWithInitial(
                VirtualFrame frame, RubyArray array, Object initialOrSymbol, Object symbol, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            String methodName = nameToJavaStringNode.execute(this, symbol); // ensure a method name is either a Symbol or could be converted to String
            return injectSymbolHelper(
                    frame,
                    array,
                    methodName,
                    stores,
                    store,
                    initialOrSymbol,
                    0,
                    arraySizeProfile,
                    loopProfile);
        }

        private Object injectSymbolHelper(VirtualFrame frame, RubyArray array, String symbol,
                ArrayStoreLibrary stores, Object store, Object initial, int start,
                IntValueProfile arraySizeProfile, LoopConditionProfile loopProfile) {
            Object accumulator = initial;
            int n = start;
            try {
                for (; loopProfile.inject(n < arraySizeProfile.profile(array.size)); n++) {
                    accumulator = dispatch.callWithFrame(PUBLIC, frame, accumulator, symbol, stores.read(store, n));
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, n);
            }
            return accumulator;
        }

    }

    @CoreMethod(names = { "map", "collect" }, needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    public abstract static class MapNode extends CoreMethodArrayArgumentsNode implements ArrayElementConsumerNode {

        private static final class State {
            final BuilderState builderState;
            final RubyProc block;

            State(BuilderState builderState, RubyProc block) {
                this.builderState = builderState;
                this.block = block;
            }
        }

        @Child private ArrayBuilderNode arrayBuilder = ArrayBuilderNode.create();

        @Specialization
        Object map(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode,
                @Cached InlinedIntValueProfile arraySizeProfile) {
            BuilderState builderState = arrayBuilder.start(arraySizeProfile.profile(this, array.size));
            State iterationState = new State(builderState, block);

            iteratorNode.execute(this, array, iterationState, 0, this);

            final int size = array.size;
            return createArray(arrayBuilder.finish(iterationState.builderState, size), size);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object stateObject, Object element,
                int index, BooleanCastNode booleanCastNode) {
            final State state = (State) stateObject;

            Object value = yieldNode.yield(node, state.block, element);
            arrayBuilder.appendValue(state.builderState, index, value);
        }

    }

    @CoreMethod(names = { "map!", "collect!" }, needsBlock = true, enumeratorSize = "size", raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class MapInPlaceNode extends CoreMethodArrayArgumentsNode
            implements ArrayElementConsumerNode {

        @Child private ArrayWriteNormalizedNode writeNode = ArrayWriteNormalizedNodeGen.create();

        @Specialization
        Object map(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(this, array, block, 0, this);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object state, Object element, int index,
                BooleanCastNode booleanCastNode) {
            RubyProc block = (RubyProc) state;
            writeNode.executeWrite(array, index, yieldNode.yield(node, block, element));
        }

    }

    @Primitive(name = "array_pack", lowerFixnum = 1)
    public abstract static class ArrayPackPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyString pack(RubyArray array, Object format, Object buffer,
                @Cached ToStrNode toStrNode,
                @Cached PackNode packNode) {
            final var formatAsString = toStrNode.execute(this, format);
            return packNode.execute(this, array, formatAsString, buffer);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @ReportPolymorphism // inline cache, CallTarget cache
    @ImportStatic(StringOperations.class)
    public abstract static class PackNode extends RubyBaseNode {

        public abstract RubyString execute(Node node, RubyArray array, Object format, Object buffer);

        @Specialization(
                guards = {
                        "libFormat.isRubyString(this, format)",
                        "libBuffer.isRubyString(this, buffer)",
                        "equalNode.execute(libFormat, format, cachedFormat, cachedEncoding)" },
                limit = "getCacheLimit()")
        static RubyString packCached(Node node, RubyArray array, Object format, Object buffer,
                @Cached @Shared InlinedBranchProfile exceptionProfile,
                @Cached @Shared InlinedConditionProfile resizeProfile,
                @Cached @Shared RubyStringLibrary libFormat,
                @Cached @Shared RubyStringLibrary libBuffer,
                @Cached @Shared WriteObjectFieldNode writeAssociatedNode,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                @Cached("asTruffleStringUncached(format)") TruffleString cachedFormat,
                @Cached("libFormat.getEncoding($node, format)") RubyEncoding cachedEncoding,
                @Cached("cachedFormat.byteLength(cachedEncoding.tencoding)") int cachedFormatLength,
                @Cached("compileFormat(node, getJavaString(format))") RootCallTarget formatCallTarget,
                @Cached("create(formatCallTarget)") DirectCallNode callPackNode,
                @Cached StringHelperNodes.EqualNode equalNode) {
            final byte[] bytes = initOutputBytes(buffer, libBuffer, formatCallTarget, copyToByteArrayNode, node);

            final BytesResult result;

            try {
                result = (BytesResult) callPackNode.call(
                        new Object[]{ array.getStore(), array.size, bytes, libBuffer.byteLength(node, buffer) });
            } catch (FormatException e) {
                exceptionProfile.enter(node);
                throw FormatExceptionTranslator.translate(getContext(node), node, e);
            }

            return finishPack(node, cachedFormatLength, result, resizeProfile, writeAssociatedNode, fromByteArrayNode);
        }

        @Specialization(guards = { "libFormat.isRubyString(this, format)", "libBuffer.isRubyString(this, buffer)" },
                replaces = "packCached")
        static RubyString packUncached(Node node, RubyArray array, Object format, Object buffer,
                @Cached @Shared InlinedBranchProfile exceptionProfile,
                @Cached @Shared InlinedConditionProfile resizeProfile,
                @Cached @Shared RubyStringLibrary libFormat,
                @Cached @Shared RubyStringLibrary libBuffer,
                @Cached @Shared WriteObjectFieldNode writeAssociatedNode,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached IndirectCallNode callPackNode) {
            final String formatString = toJavaStringNode.execute(node, format);
            final RootCallTarget formatCallTarget = compileFormat(node, formatString);
            final byte[] bytes = initOutputBytes(buffer, libBuffer, formatCallTarget, copyToByteArrayNode, node);

            final BytesResult result;

            try {
                result = (BytesResult) callPackNode.call(
                        formatCallTarget,
                        new Object[]{ array.getStore(), array.size, bytes, libBuffer.byteLength(node, buffer) });
            } catch (FormatException e) {
                exceptionProfile.enter(node);
                throw FormatExceptionTranslator.translate(getContext(node), node, e);
            }

            int formatLength = libFormat.getTString(node, format).byteLength(libFormat.getTEncoding(node, format));
            return finishPack(node, formatLength, result, resizeProfile, writeAssociatedNode, fromByteArrayNode);
        }

        private static RubyString finishPack(Node node, int formatLength, BytesResult result,
                InlinedConditionProfile resizeProfile,
                WriteObjectFieldNode writeAssociatedNode, TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] bytes = result.getOutput();

            if (resizeProfile.profile(node, bytes.length != result.getOutputLength())) {
                bytes = Arrays.copyOf(bytes, result.getOutputLength());
            }

            final RubyEncoding rubyEncoding = result.getEncoding().getEncodingForLength(formatLength);
            final RubyString string = createString(node, fromByteArrayNode, bytes, rubyEncoding);

            if (result.getAssociated() != null) {
                writeAssociatedNode.execute(node, string, Layouts.ASSOCIATED_IDENTIFIER, result.getAssociated());
            }

            return string;
        }

        @TruffleBoundary
        protected static RootCallTarget compileFormat(Node node, String format) {
            try {
                return new PackCompiler(getLanguage(node), node).compile(format);
            } catch (DeferredRaiseException dre) {
                throw dre.getException(getContext(node));
            }
        }

        private static byte[] initOutputBytes(Object buffer, RubyStringLibrary libBuffer,
                RootCallTarget formatCallTarget, TruffleString.CopyToByteArrayNode copyToByteArrayNode, Node node) {
            int bufferLength = libBuffer.byteLength(node, buffer);
            var formatRootNode = (FormatRootNode) formatCallTarget.getRootNode();
            int expectedLength = formatRootNode.getExpectedLength();

            // output buffer should be at least expectedLength to not mess up the expectedLength's logic
            final int length = Math.max(bufferLength, expectedLength);
            final byte[] bytes = new byte[length];
            copyToByteArrayNode.execute(libBuffer.getTString(node, buffer), 0, bytes, 0, bufferLength,
                    libBuffer.getTEncoding(node, buffer));
            return bytes;
        }

        protected int getCacheLimit() {
            return getLanguage().options.PACK_CACHE;
        }

    }

    @CoreMethod(names = "pop", raiseIfFrozenSelf = true, optional = 1, lowerFixnum = 1)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class PopNode extends ArrayCoreMethodNode {

        public abstract Object executePop(RubyArray array, Object n);

        @Specialization
        @ReportPolymorphism.Exclude
        Object pop(RubyArray array, NotProvided n,
                @Cached ArrayPopOneNode popOneNode) {
            return popOneNode.executePopOne(array);
        }

        @Specialization(guards = "n < 0")
        @ReportPolymorphism.Exclude
        Object popNNegative(RubyArray array, int n) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorNegativeArraySize(this));
        }

        @Specialization(guards = { "n >= 0", "isEmptyArray(array)" })
        @ReportPolymorphism.Exclude
        RubyArray popEmpty(RubyArray array, int n) {
            return createEmptyArray();
        }

        @Specialization(guards = { "n == 0", "!isEmptyArray(array)" })
        @ReportPolymorphism.Exclude
        RubyArray popZeroNotEmpty(RubyArray array, int n) {
            return createEmptyArray();
        }

        @Specialization(
                guards = { "n > 0", "!isEmptyArray(array)", "!stores.isMutable(array.getStore())" },
                limit = "storageStrategyLimit()")
        RubyArray popNotEmptySharedStorage(RubyArray array, int n,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Shared ConditionProfile minProfile) {
            final int size = arraySizeProfile.profile(array.size);
            final int numPop = minProfile.profile(size < n) ? size : n;

            final Object popped = stores.extractRangeAndUnshare(store, size - numPop, size); // copy on write
            final Object prefix = stores.extractRange(store, 0, size - numPop);    // copy on write
            // NOTE(norswap): if one of these two arrays outlives the other, you get a memory leak

            array.setStoreAndSize(prefix, size - numPop);
            return createArray(popped, numPop);
        }

        @Specialization(
                guards = { "n > 0", "!isEmptyArray(array)", "stores.isMutable(array.getStore())" },
                limit = "storageStrategyLimit()")
        RubyArray popNotEmptyUnsharedStorage(RubyArray array, int n,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Shared ConditionProfile minProfile) {
            final int size = arraySizeProfile.profile(array.size);
            final int numPop = minProfile.profile(size < n) ? size : n;

            // Extract values in a new array
            final Object popped = stores.unsharedAllocator(store).allocate(numPop);
            stores.copyContents(store, size - numPop, popped, 0, numPop);

            // Remove the end from the original array.
            stores.clear(store, size - numPop, numPop);
            array.setSize(size - numPop);

            return createArray(popped, numPop);
        }

        @Specialization(guards = { "wasProvided(n)", "!isInteger(n)" })
        Object popNToInt(RubyArray array, Object n,
                @Cached ToIntNode toIntNode) {
            return executePop(array, toIntNode.execute(n));
        }

    }

    @CoreMethod(names = "<<", raiseIfFrozenSelf = true, required = 1, split = Split.ALWAYS)
    public abstract static class AppendNode extends ArrayCoreMethodNode {

        @Child private ArrayAppendOneNode appendOneNode = ArrayAppendOneNode.create();

        @Specialization
        RubyArray append(RubyArray array, Object value) {
            return appendOneNode.executeAppendOne(array, value);
        }

    }

    @CoreMethod(names = { "push", "append" }, rest = true, optional = 1, raiseIfFrozenSelf = true)
    public abstract static class PushNode extends ArrayCoreMethodNode {

        @Child private ArrayAppendOneNode appendOneNode = ArrayAppendOneNode.create();

        @Specialization(guards = "rest.length == 0")
        RubyArray pushZero(RubyArray array, NotProvided value, Object[] rest) {
            return array;
        }

        @Specialization(guards = { "rest.length == 0", "wasProvided(value)" })
        RubyArray pushOne(RubyArray array, Object value, Object[] rest) {
            return appendOneNode.executeAppendOne(array, value);
        }

        @Specialization(guards = { "rest.length > 0", "wasProvided(value)" })
        RubyArray pushMany(VirtualFrame frame, RubyArray array, Object value, Object[] rest,
                @Cached LoopConditionProfile loopProfile) {
            // NOTE (eregon): Appending one by one here to avoid useless generalization to Object[]
            // if the arguments all fit in the current storage
            appendOneNode.executeAppendOne(array, value);
            int i = 0;
            try {
                for (; loopProfile.inject(i < rest.length); i++) {
                    appendOneNode.executeAppendOne(array, rest[i]);
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i);
            }
            return array;
        }
    }

    @CoreMethod(names = "reject", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    public abstract static class RejectNode extends CoreMethodArrayArgumentsNode implements ArrayElementConsumerNode {

        private static final class State {
            final BuilderState builderState;
            int newArraySize;
            final RubyProc block;

            State(BuilderState builderState, int newArraySize, RubyProc block) {
                this.builderState = builderState;
                this.newArraySize = newArraySize;
                this.block = block;
            }
        }

        @Child private ArrayBuilderNode arrayBuilder = ArrayBuilderNode.create();

        @Specialization
        Object reject(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode,
                @Cached InlinedIntValueProfile arraySizeProfile) {
            BuilderState builderState = arrayBuilder.start(arraySizeProfile.profile(this, array.size));
            State iterationState = new State(builderState, 0, block);

            iteratorNode.execute(this, array, iterationState, 0, this);

            int actualSize = iterationState.newArraySize;
            return createArray(arrayBuilder.finish(builderState, actualSize), actualSize);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object stateObject, Object element,
                int index, BooleanCastNode booleanCastNode) {
            final State state = (State) stateObject;

            if (!booleanCastNode.execute(node, yieldNode.yield(node, state.block, element))) {
                arrayBuilder.appendValue(state.builderState, state.newArraySize, element);
                state.newArraySize++;
            }
        }

    }

    @CoreMethod(names = "replace", required = 1, raiseIfFrozenSelf = true)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "other", type = RubyBaseNodeWithExecute.class)
    @ImportStatic(ArrayGuards.class)
    public abstract static class ReplaceNode extends CoreMethodNode {

        @NeverDefault
        public static ReplaceNode create() {
            return ArrayNodesFactory.ReplaceNodeFactory.create(null, null);
        }

        public abstract RubyArray executeReplace(RubyArray array, RubyArray other);

        @Specialization
        RubyArray replace(RubyArray array, Object otherObject,
                @Cached ToAryNode toAryNode,
                @Cached ArrayCopyOnWriteNode cowNode,
                @Cached IsSharedNode isSharedNode,
                @CachedLibrary(limit = "storageStrategyLimit()") ArrayStoreLibrary stores) {
            final var other = toAryNode.execute(this, otherObject);
            final int size = other.size;
            Object store = cowNode.execute(other, 0, size);
            if (isSharedNode.execute(this, array)) {
                store = stores.makeShared(store, size);
            }
            array.setStoreAndSize(store, size);
            return array;
        }

    }

    @Primitive(name = "array_rotate", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class RotateNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "storageStrategyLimit()")
        RubyArray rotate(RubyArray array, int rotation,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached IntValueProfile arraySizeProfile,
                @Cached IntValueProfile rotationProfile) {
            final int size = arraySizeProfile.profile(array.size);
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;

            final Object rotated = stores.unsharedAllocator(store).allocate(size);
            rotateArrayCopy(rotation, size, stores, store, rotated);
            return createArray(rotated, size);
        }
    }

    protected static void rotateArrayCopy(int rotation, int size, ArrayStoreLibrary arrays,
            Object original, Object rotated) {
        arrays.copyContents(original, rotation, rotated, 0, size - rotation);
        arrays.copyContents(original, 0, rotated, size - rotation, rotation);
    }

    @Primitive(name = "array_rotate_inplace", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class RotateInplaceNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = "stores.isMutable(store)",
                limit = "storageStrategyLimit()")
        RubyArray rotate(RubyArray array, int rotation,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Shared IntValueProfile rotationProfile,
                @Cached LoopConditionProfile loop1Profile,
                @Cached LoopConditionProfile loop2Profile,
                @Cached LoopConditionProfile loop3Profile) {
            final int size = arraySizeProfile.profile(array.size);
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;

            if (CompilerDirectives.isPartialEvaluationConstant(size) &&
                    CompilerDirectives.isPartialEvaluationConstant(rotation) &&
                    size <= RubyBaseNode.MAX_EXPLODE_SIZE) {
                rotateSmallExplode(stores, rotation, size, store);
            } else {
                rotateReverse(
                        stores,
                        rotation,
                        size,
                        store,
                        loop1Profile,
                        loop2Profile,
                        loop3Profile);
            }

            return array;
        }

        @Specialization(
                guards = { "!stores.isMutable(store)" },
                limit = "storageStrategyLimit()")
        RubyArray rotateStorageNotMutable(RubyArray array, int rotation,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Shared IntValueProfile rotationProfile) {
            final int size = arraySizeProfile.profile(array.size);
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;

            final Object rotated = stores.allocator(store).allocate(size);
            rotateArrayCopy(rotation, size, stores, store, rotated);
            array.setStoreAndSize(rotated, size);
            return array;
        }

        @ExplodeLoop
        protected void rotateSmallExplode(ArrayStoreLibrary stores, int rotation, int size, Object store) {
            Object[] copy = new Object[size];
            for (int i = 0; i < size; i++) {
                copy[i] = stores.read(store, i);
            }
            for (int i = 0; i < size; i++) {
                int j = i + rotation;
                if (j >= size) {
                    j -= size;
                }
                stores.write(store, i, copy[j]);
            }
        }

        protected void rotateReverse(ArrayStoreLibrary stores, int rotation, int size, Object store,
                LoopConditionProfile loop1Profile,
                LoopConditionProfile loop2Profile,
                LoopConditionProfile loop3Profile) {
            // Rotating by rotation in-place is equivalent to
            // replace([rotation..-1] + [0...rotation])
            // which is the same as reversing the whole array and
            // reversing each of the two parts so that elements are in the same order again.
            // This trick avoids constantly checking if indices are within array bounds
            // and accesses memory sequentially, even though it does perform 2*size reads and writes.
            // This is also what MRI and JRuby do.
            reverse(stores, store, rotation, size, loop1Profile);
            reverse(stores, store, 0, rotation, loop2Profile);
            reverse(stores, store, 0, size, loop3Profile);
        }

        private void reverse(ArrayStoreLibrary stores,
                Object store, int from, int until, LoopConditionProfile loopProfile) {
            int to = until - 1;
            final int loopCount = (until - from) >> 1;
            try {
                while (loopProfile.inject(from < to)) {
                    final Object tmp = stores.read(store, from);
                    stores.write(store, from, stores.read(store, to));
                    stores.write(store, to, tmp);
                    from++;
                    to--;
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, loopCount);
            }
        }
    }

    @CoreMethod(names = { "select", "filter" }, needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    public abstract static class SelectNode extends CoreMethodArrayArgumentsNode implements ArrayElementConsumerNode {

        private static final class State {
            final BuilderState builderState;
            int selectedSize;
            final RubyProc block;

            State(BuilderState builderState, int selectedSize, RubyProc block) {
                this.builderState = builderState;
                this.selectedSize = selectedSize;
                this.block = block;
            }
        }

        @Child private ArrayBuilderNode arrayBuilder = ArrayBuilderNode.create();

        @Specialization
        Object select(RubyArray array, RubyProc block,
                @Cached ArrayEachIteratorNode iteratorNode,
                @Cached InlinedIntValueProfile arraySizeProfile) {
            BuilderState builderState = arrayBuilder.start(arraySizeProfile.profile(this, array.size));
            State iterationState = new State(builderState, 0, block);

            iteratorNode.execute(this, array, iterationState, 0, this);

            int selectedSize = iterationState.selectedSize;
            return createArray(arrayBuilder.finish(builderState, selectedSize), selectedSize);
        }

        @Override
        public void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object stateObject, Object element,
                int index, BooleanCastNode booleanCastNode) {
            final State state = (State) stateObject;

            if (booleanCastNode.execute(node, yieldNode.yield(node, state.block, element))) {
                arrayBuilder.appendValue(state.builderState, state.selectedSize, element);
                state.selectedSize++;
            }
        }

    }

    @CoreMethod(names = "shift", raiseIfFrozenSelf = true, optional = 1, lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class ShiftNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeShift(RubyArray array, Object n);

        // No n, just shift 1 element and return it

        @Specialization(guards = "isEmptyArray(array)")
        @ReportPolymorphism.Exclude
        Object shiftEmpty(RubyArray array, NotProvided n) {
            return nil;
        }

        @Specialization(guards = "!isEmptyArray(array)", limit = "storageStrategyLimit()")
        Object shiftOther(RubyArray array, NotProvided n,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            final int size = array.size;
            final Object value = stores.read(store, 0);
            stores.clear(store, 0, 1);
            array.setStoreAndSize(stores.extractRange(store, 1, size), size - 1);
            return value;
        }

        // n given, shift the first n elements and return them as an Array

        @Specialization(guards = "n < 0")
        Object shiftNegative(RubyArray array, int n) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorNegativeArraySize(this));
        }

        @Specialization(guards = "n == 0")
        Object shiftZero(RubyArray array, int n) {
            return createEmptyArray();
        }

        @Specialization(guards = { "n > 0", "isEmptyArray(array)" })
        Object shiftManyEmpty(RubyArray array, int n) {
            return createEmptyArray();
        }

        @Specialization(
                guards = { "n > 0", "!isEmptyArray(array)" },
                limit = "storageStrategyLimit()")
        Object shiftMany(RubyArray array, int n,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached ConditionProfile minProfile) {
            final int size = array.size;
            final int numShift = minProfile.profile(size < n) ? size : n;
            final Object result = stores.extractRangeAndUnshare(store, 0, numShift);
            final Object newStore = stores.extractRange(store, numShift, size);
            array.setStoreAndSize(newStore, size - numShift);
            return createArray(result, numShift);
        }

        @Specialization(guards = { "wasProvided(n)", "!isInteger(n)" })
        Object shiftNToInt(RubyArray array, Object n,
                @Cached ToIntNode toIntNode) {
            return executeShift(array, toIntNode.execute(n));
        }
    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends ArrayCoreMethodNode {
        @Specialization
        int size(RubyArray array,
                @Cached IntValueProfile arraySizeProfile) {
            return arraySizeProfile.profile(array.size);
        }
    }

    @CoreMethod(names = "sort", needsBlock = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class SortNode extends ArrayCoreMethodNode {

        @Specialization(guards = "isEmptyArray(array)")
        RubyArray sortEmpty(RubyArray array, Object unusedBlock) {
            return createEmptyArray();
        }

        @ExplodeLoop
        @Specialization(
                guards = { "!isEmptyArray(array)", "isSmall(array)" },
                limit = "storageStrategyLimit()")
        static RubyArray sortVeryShort(VirtualFrame frame, RubyArray array, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @CachedLibrary(limit = "1") @Exclusive ArrayStoreLibrary newStores,
                @Cached @Shared IntValueProfile arraySizeProfile,
                @Cached @Exclusive DispatchNode compareDispatchNode,
                @Cached CmpIntNode cmpIntNode,
                @Bind Node node) {
            final Object newStore = stores
                    .unsharedAllocator(store)
                    .allocate(getContext(node).getOptions().ARRAY_SMALL);
            final int size = arraySizeProfile.profile(array.size);

            // Copy with a exploded loop for PE

            for (int i = 0; i < getContext(node).getOptions().ARRAY_SMALL; i++) {
                if (i < size) {
                    newStores.write(newStore, i, stores.read(store, i));
                }
            }

            // Selection sort - written very carefully to allow PE

            for (int i = 0; i < getContext(node).getOptions().ARRAY_SMALL; i++) {
                if (i < size) {
                    for (int j = i + 1; j < getContext(node).getOptions().ARRAY_SMALL; j++) {
                        if (j < size) {
                            final Object a = newStores.read(newStore, i);
                            final Object b = newStores.read(newStore, j);
                            final Object comparisonResult = compareDispatchNode.call(b, "<=>", a);
                            if (cmpIntNode.executeCmpInt(node, comparisonResult, b, a) < 0) {
                                newStores.write(newStore, j, a);
                                newStores.write(newStore, i, b);
                            }
                        }
                    }
                }
            }

            return createArray(node, newStore, size);
        }

        @Specialization(
                guards = {
                        "!isEmptyArray(array)",
                        "!isSmall(array)",
                        "stores.isPrimitive(store)" },
                assumptions = {
                        "getLanguage().coreMethodAssumptions.integerCmpAssumption",
                        "getLanguage().coreMethodAssumptions.floatCmpAssumption" },
                limit = "storageStrategyLimit()")
        Object sortPrimitiveArrayNoBlock(RubyArray array, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @CachedLibrary(limit = "1") @Exclusive ArrayStoreLibrary mutableStores,
                @Cached @Shared IntValueProfile arraySizeProfile) {
            final int size = arraySizeProfile.profile(array.size);
            Object newStore = stores.unsharedAllocator(store).allocate(size);
            stores.copyContents(store, 0, newStore, 0, size);
            mutableStores.sort(newStore, size);
            return createArray(newStore, size);
        }

        @Specialization(
                guards = { "!isEmptyArray(array)", "!isSmall(array)" },
                limit = "storageStrategyLimit()")
        Object sortArrayWithoutBlock(RubyArray array, Nil block,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached @Shared DispatchNode fallbackNode) {
            return fallbackNode.call(array, "sort_fallback");
        }

        @Specialization(guards = "!isEmptyArray(array)")
        Object sortGenericWithBlock(RubyArray array, RubyProc block,
                @Cached @Shared DispatchNode fallbackNode) {
            return fallbackNode.callWithBlock(array, "sort_fallback", block);
        }

        protected boolean isSmall(RubyArray array) {
            return array.size <= getContext().getOptions().ARRAY_SMALL;
        }

    }

    @CoreMethod(names = "unshift", rest = true, raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class UnshiftNode extends CoreMethodArrayArgumentsNode {
        @Specialization(guards = "rest.length == 0")
        Object unshiftNothing(RubyArray array, Object[] rest) {
            return array;
        }

        @Specialization(guards = "rest.length != 0")
        Object unshift(RubyArray array, Object[] rest,
                @Cached ArrayPrepareForCopyNode resize,
                @Cached ArrayCopyCompatibleRangeNode moveElements,
                @Cached ArrayCopyCompatibleRangeNode copyUnshifted,
                @Cached ArrayTruncateNode truncate) {
            final int originalSize = array.size;
            final RubyArray prepended = createArray(rest);
            final int prependedSize = prepended.size;
            final int newSize = originalSize + prependedSize;

            final Object newStore = resize.execute(array, prepended, 0, newSize);
            moveElements.execute(newStore, newStore, prependedSize, 0, originalSize);
            copyUnshifted.execute(newStore, prepended.getStore(), 0, 0, prependedSize);
            truncate.execute(array, newSize);

            return array;
        }
    }

    @Primitive(name = "steal_array_storage")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StealArrayStorageNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "array == other")
        RubyArray stealStorageNoOp(RubyArray array, RubyArray other) {
            return array;
        }

        @Specialization(guards = "array != other")
        static RubyArray stealStorage(RubyArray array, RubyArray other,
                @CachedLibrary(limit = "storageStrategyLimit()") ArrayStoreLibrary stores,
                @Cached PropagateSharingNode propagateSharingNode,
                @Bind Node node) {
            propagateSharingNode.execute(node, array, other);

            final int size = other.size;
            final Object store = other.getStore();
            array.setStoreAndSize(store, size);
            other.setStoreAndSize(stores.initialStore(store), 0);

            return array;
        }

    }

    @Primitive(name = "array_zip")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class ZipNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "storageStrategyLimit()")
        RubyArray zipToPairs(RubyArray array, RubyArray other,
                @Bind("array.getStore()") Object a,
                @Bind("other.getStore()") Object b,
                @CachedLibrary("a") ArrayStoreLibrary aStores,
                @CachedLibrary("b") ArrayStoreLibrary bStores,
                @CachedLibrary(limit = "1") ArrayStoreLibrary pairs,
                @Cached ConditionProfile bNotSmallerProfile,
                @Cached IntValueProfile arraySizeProfile,
                @Cached LoopConditionProfile loopProfile) {

            final int zippedLength = arraySizeProfile.profile(array.size);
            final int bSize = other.size;

            final Object[] zipped = new Object[zippedLength];

            int n = 0;
            try {
                for (; loopProfile.inject(n < zippedLength); n++) {
                    if (bNotSmallerProfile.profile(n < bSize)) {
                        final Object pair = aStores.unsharedAllocateForNewStore(a, b, 2);
                        pairs.write(pair, 0, aStores.read(a, n));
                        pairs.write(pair, 1, bStores.read(b, n));
                        zipped[n] = createArray(pair, 2);
                    } else {
                        zipped[n] = createArray(new Object[]{ aStores.read(a, n), nil });
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, n);
            }

            return createArray(zipped, zippedLength);
        }

    }

    @Primitive(name = "array_store_to_native")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StoreToNativeNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "!stores.isNative(store)", limit = "storageStrategyLimit()")
        RubyArray storeToNative(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached IntValueProfile arraySizeProfile) {
            final int size = arraySizeProfile.profile(array.size);
            Pointer pointer = Pointer.mallocAutoRelease(getLanguage(), getContext(), size * Pointer.SIZE);
            Object newStore = new NativeArrayStorage(pointer, size);
            stores.copyContents(store, 0, newStore, 0, size);
            array.setStore(newStore);
            return array;
        }

        @Specialization(guards = "stores.isNative(store)", limit = "storageStrategyLimit()")
        RubyArray storeIsNative(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return array;
        }
    }

    @Primitive(name = "array_store_address")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StoreAddressNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "stores.isNative(store)", limit = "storageStrategyLimit()")
        long storeIsNative(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            NativeArrayStorage storage = (NativeArrayStorage) store;
            return storage.getAddress();
        }

        /** See {@link RubyDynamicObject#asPointer} **/
        @Specialization(guards = "!stores.isNative(store)", limit = "storageStrategyLimit()")
        Object storeNotNative(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return DispatchNode.MISSING; // for UnsupportedMessageException
        }
    }

    @Primitive(name = "array_store_native?")
    @ImportStatic(ArrayGuards.class)
    public abstract static class IsStoreNativeNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "storageStrategyLimit()")
        boolean isStoreNative(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return stores.isNative(store);
        }

        @Specialization(guards = "!isRubyArray(array)")
        boolean isStoreNativeNonArray(Object array) {
            return false;
        }
    }

    @Primitive(name = "array_mark_store")
    @ImportStatic(ArrayGuards.class)
    public abstract static class MarkNativeStoreNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object markNativeStore(RubyArray array) {
            Object store = array.getStore();
            if (store instanceof NativeArrayStorage) {
                ((NativeArrayStorage) store).preserveMembers();
            }
            return nil;
        }
    }

    @Primitive(name = "array_flatten_helper", lowerFixnum = 2)
    public abstract static class FlattenHelperNode extends PrimitiveArrayArgumentsNode {

        static final class Entry {
            final RubyArray array;
            final int index;

            private Entry(RubyArray array, int index) {
                this.array = array;
                this.index = index;
            }
        }

        @Specialization(guards = "!canContainObject.execute(array)", limit = "1")
        boolean flattenHelperPrimitive(RubyArray array, RubyArray out, int maxLevels,
                @Cached @Exclusive ArrayAppendManyNode concat,
                @Cached @Exclusive ArrayCanContainObjectNode canContainObject) {
            concat.executeAppendMany(out, array);
            return false;
        }

        @Specialization(replaces = "flattenHelperPrimitive")
        boolean flattenHelper(RubyArray array, RubyArray out, int maxLevels,
                @Cached @Exclusive ArrayCanContainObjectNode canContainObject,
                @Cached @Exclusive ArrayAppendManyNode concat,
                @Cached AtNode at,
                @Cached DispatchNode convert,
                @Cached ArrayAppendOneNode append) {

            boolean modified = false;
            final EconomicSet<RubyArray> visited = EconomicSet.create(Equivalence.IDENTITY);
            final SimpleStack<Entry> workStack = new SimpleStack<>();
            workStack.push(new Entry(array, 0));

            while (!workStack.isEmpty()) {
                final Entry e = workStack.pop();

                if (e.index == 0) {
                    if (!canContainObject.execute(e.array)) {
                        concat.executeAppendMany(out, e.array);
                        continue;
                    } else if (contains(visited, e.array)) {
                        throw new RaiseException(
                                getContext(),
                                coreExceptions().argumentError("tried to flatten recursive array", this));
                    } else if (maxLevels == workStack.size()) {
                        concat.executeAppendMany(out, e.array);
                        continue;
                    }
                    add(visited, e.array);
                }

                int i = e.index;
                for (; i < e.array.size; ++i) {
                    final Object obj = at.executeAt(e.array, i);
                    final Object converted = convert.call(
                            coreLibrary().truffleTypeModule,
                            "rb_check_convert_type",
                            obj,
                            coreLibrary().arrayClass,
                            coreSymbols().TO_ARY);
                    if (converted == nil) {
                        append.executeAppendOne(out, obj);
                    } else {
                        modified = true;
                        workStack.push(new Entry(e.array, i + 1));
                        workStack.push(new Entry((RubyArray) converted, 0));
                        break;
                    }
                }
                if (i == e.array.size) {
                    remove(visited, e.array);
                }
            }

            return modified;
        }

        @TruffleBoundary
        private static boolean contains(EconomicSet<RubyArray> set, RubyArray array) {
            return set.contains(array);
        }

        @TruffleBoundary
        private static void remove(EconomicSet<RubyArray> set, RubyArray array) {
            set.remove(array);
        }

        @TruffleBoundary
        private static void add(EconomicSet<RubyArray> set, RubyArray array) {
            set.add(array);
        }
    }

    @Primitive(name = "array_can_contain_object?")
    @ImportStatic(ArrayGuards.class)
    public abstract static class ArrayCanContainObjectNode extends PrimitiveArrayArgumentsNode {

        @NeverDefault
        public static ArrayCanContainObjectNode create() {
            return ArrayNodesFactory.ArrayCanContainObjectNodeFactory.create(null);
        }

        public abstract boolean execute(RubyArray array);

        @Specialization(limit = "storageStrategyLimit()")
        boolean array(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return !stores.isPrimitive(store);
        }

        @Specialization(guards = "!isRubyArray(array)")
        boolean other(Object array) {
            return true;
        }

    }
}
