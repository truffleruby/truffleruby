/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import java.util.Arrays;
import java.util.Iterator;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import dev.truffleruby.shadowed.org.joni.MultiRegion;
import dev.truffleruby.shadowed.org.joni.NameEntry;
import dev.truffleruby.shadowed.org.joni.Regex;
import dev.truffleruby.shadowed.org.joni.exception.ValueException;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.range.RangeNodes;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes.SingleByteOptimizableNode;
import org.truffleruby.core.string.StringSupport;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "MatchData", isClass = true)
public abstract class MatchDataNodes {

    private static int getGroupBound(InteropLibrary interop, RubyMatchData matchData, String member, int group) {
        try {
            final int offset = (int) interop.invokeMember(matchData.tRegexResult, member, group);
            assert offset >= 0 || offset == RubyMatchData.MISSING;
            return offset;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static int getStart(Node node, RubyMatchData matchData, int index, InlinedConditionProfile lazyProfile,
            InteropLibrary interop) {
        int start = matchData.region.getBeg(index);
        if (lazyProfile.profile(node, start == RubyMatchData.LAZY)) {
            return matchData.region.setBeg(index, getGroupBound(interop, matchData, "getStart", index));
        } else {
            return start;
        }
    }

    private static int getEnd(Node node, RubyMatchData matchData, int index, InlinedConditionProfile lazyProfile,
            InteropLibrary interop) {
        int end = matchData.region.getEnd(index);
        if (lazyProfile.profile(node, end == RubyMatchData.LAZY)) {
            return matchData.region.setEnd(index, getGroupBound(interop, matchData, "getEnd", index));
        } else {
            return end;
        }
    }

    private static void forceLazyMatchData(RubyMatchData matchData, InteropLibrary interop) {
        for (int i = 0; i < matchData.region.getNumRegs(); i++) {
            getStart(null, matchData, i, InlinedConditionProfile.getUncached(), interop);
            getEnd(null, matchData, i, InlinedConditionProfile.getUncached(), interop);
        }
    }

    @TruffleBoundary
    private static MultiRegion getCharOffsetsManyRegs(RubyMatchData matchData, AbstractTruffleString source,
            RubyEncoding encoding) {
        // Taken from org.jruby.RubyMatchData

        assert !encoding.isSingleByte : "Should be checked by callers";

        final MultiRegion regs = matchData.region;
        int numRegs = regs.getNumRegs();

        if (matchData.tRegexResult != null) {
            forceLazyMatchData(matchData, InteropLibrary.getUncached(matchData.tRegexResult));
        }

        final MultiRegion charOffsets = new MultiRegion(numRegs);

        final Pair[] pairs = new Pair[numRegs * 2];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new Pair();
        }

        int numPos = 0;
        for (int i = 0; i < numRegs; i++) {
            if (regs.getBeg(i) != RubyMatchData.MISSING) {
                pairs[numPos++].bytePos = regs.getBeg(i);
                pairs[numPos++].bytePos = regs.getEnd(i);
            }
        }

        updatePairs(source, encoding, pairs);

        Pair key = new Pair();
        for (int i = 0; i < regs.getNumRegs(); i++) {
            if (regs.getBeg(i) == RubyMatchData.MISSING) {
                charOffsets.setBeg(i, RubyMatchData.MISSING);
                charOffsets.setEnd(i, RubyMatchData.MISSING);
            } else {
                key.bytePos = regs.getBeg(i);
                charOffsets.setBeg(i, pairs[Arrays.binarySearch(pairs, key)].charPos);
                key.bytePos = regs.getEnd(i);
                charOffsets.setEnd(i, pairs[Arrays.binarySearch(pairs, key)].charPos);
            }
        }

        return charOffsets;
    }

    @TruffleBoundary
    private static void updatePairs(AbstractTruffleString source, RubyEncoding encoding, Pair[] pairs) {
        // Taken from org.jruby.RubyMatchData
        Arrays.sort(pairs);

        var byteArray = source.getInternalByteArrayUncached(encoding.tencoding);
        byte[] bytes = byteArray.getArray();
        int p = byteArray.getOffset();
        int s = p;
        int c = 0;

        for (Pair pair : pairs) {
            int q = s + pair.bytePos;
            c += StringSupport.strLength(encoding, bytes, p, q);
            pair.charPos = c;
            p = q;
        }
    }

    @TruffleBoundary
    private static MultiRegion createCharOffsets(RubyMatchData matchData, AbstractTruffleString source,
            RubyEncoding encoding) {
        final MultiRegion charOffsets = getCharOffsetsManyRegs(matchData, source, encoding);
        matchData.charOffsets = charOffsets;
        return charOffsets;
    }

    private static MultiRegion getCharOffsets(RubyMatchData matchData, AbstractTruffleString source,
            RubyEncoding encoding) {
        final MultiRegion charOffsets = matchData.charOffsets;
        if (charOffsets != null) {
            return charOffsets;
        } else {
            return createCharOffsets(matchData, source, encoding);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class FixupMatchDataStartNode extends RubyBaseNode {

        public abstract void execute(Node node, RubyMatchData matchData, int start);

        @Specialization
        static void fixupMatchData(Node node, RubyMatchData matchData, int start,
                @Cached InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop) {
            assert start != 0 : "should be checked by caller";
            if (lazyProfile.profile(node, matchData.tRegexResult != null)) {
                forceLazyMatchData(matchData, interop);
            }
            fixupMatchDataForStart(matchData, start);
        }

        @TruffleBoundary
        private static void fixupMatchDataForStart(RubyMatchData matchData, int start) {
            assert start != 0;
            MultiRegion regs = matchData.region;
            for (int i = 0; i < regs.getNumRegs(); i++) {
                assert regs.getBeg(i) != RubyMatchData.LAZY && regs
                        .getEnd(i) != RubyMatchData.LAZY : "Group bounds must be computed before fixupMatchDataForStart()";
                if (regs.getBeg(i) >= 0) {
                    regs.setBeg(i, regs.getBeg(i) + start);
                    regs.setEnd(i, regs.getEnd(i) + start);
                }
            }
        }
    }

    @Primitive(name = "matchdata_create_single_group", lowerFixnum = { 2, 3 }, isPublic = true)
    public abstract static class MatchDataCreateSingleGroupNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyMatchData create(Object regexp, Object string, int start, int end) {
            final MultiRegion region = new MultiRegion(start, end);
            RubyMatchData matchData = new RubyMatchData(
                    coreLibrary().matchDataClass,
                    getLanguage().matchDataShape,
                    regexp,
                    string,
                    region);
            AllocationTracing.trace(matchData, this);
            return matchData;
        }

    }

    @ReportPolymorphism // inline cache for Symbol
    public abstract static class GetIndexNode extends RubyBaseNode {

        @Child private RegexpNode regexpNode;
        @Child private ValuesNode getValuesNode = ValuesNode.create();

        protected abstract Object execute(RubyMatchData matchData, Object index, Object length);

        protected abstract Object executeGetIndex(Object matchData, int index, NotProvided length);

        @Specialization
        Object getIndex(RubyMatchData matchData, int index, NotProvided length,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached @Shared InlinedConditionProfile normalizedIndexProfile,
                @Cached @Exclusive InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached @Shared InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") @Shared InteropLibrary libInterop,
                @Cached @Exclusive InlinedConditionProfile hasValueProfile,
                @Cached TruffleString.SubstringByteIndexNode substringNode) {

            final MultiRegion region = matchData.region;
            if (normalizedIndexProfile.profile(this, index < 0)) {
                index += region.getNumRegs();
            }

            if (indexOutOfBoundsProfile.profile(this, index < 0 || index >= region.getNumRegs())) {
                return nil;
            } else {
                final int start = getStart(this, matchData, index, lazyProfile, libInterop);
                final int end = getEnd(this, matchData, index, lazyProfile, libInterop);

                if (hasValueProfile.profile(this, start >= 0 && end >= 0)) {
                    final Object source = matchData.source;
                    return createSubString(substringNode, strings, source, start, end - start);
                } else {
                    return nil;
                }
            }
        }

        @Specialization
        Object getIndex(RubyMatchData matchData, int index, int length,
                @Cached @Exclusive InlinedConditionProfile negativeLengthProfile,
                @Cached @Shared InlinedConditionProfile normalizedIndexProfile,
                @Cached @Exclusive InlinedConditionProfile negativeIndexProfile,
                @Cached @Exclusive InlinedConditionProfile tooLargeIndexProfile,
                @Cached @Exclusive InlinedConditionProfile tooLargeTotalProfile) {
            final Object[] values = getValuesNode.execute(matchData);

            if (negativeLengthProfile.profile(this, length < 0)) {
                return nil;
            }

            if (normalizedIndexProfile.profile(this, index < 0)) {
                index += values.length;

                if (negativeIndexProfile.profile(this, index < 0)) {
                    return nil;
                }
            }

            if (tooLargeIndexProfile.profile(this, index > values.length)) {
                return nil;
            }

            int endIndex = index + length;
            if (tooLargeTotalProfile.profile(this, endIndex > values.length)) {
                endIndex = values.length;
            }

            final Object[] store = Arrays.copyOfRange(values, index, endIndex);

            return createArray(store);
        }

        @Specialization(
                guards = {
                        "nameEntry != null",
                        "getRegexp(matchData) == cachedRegexp",
                        "symbol == cachedSymbol" },
                limit = "getDefaultCacheLimit()")
        Object getIndexSymbolKnownRegexp(RubyMatchData matchData, RubySymbol symbol, NotProvided length,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("getRegexp(matchData)") RubyRegexp cachedRegexp,
                @Cached("findNameEntry(cachedRegexp, cachedSymbol)") NameEntry nameEntry,
                @Cached("numBackRefs(nameEntry)") int backRefs,
                @Cached("backRefIndex(nameEntry)") int backRefIndex,
                @Cached @Shared InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") @Shared InteropLibrary libInterop) {
            if (backRefs == 1) {
                return executeGetIndex(matchData, backRefIndex, NotProvided.INSTANCE);
            } else {
                final int i = getBackRef(matchData, cachedRegexp, cachedSymbol.tstring, cachedSymbol.encoding,
                        lazyProfile, libInterop);
                return executeGetIndex(matchData, i, NotProvided.INSTANCE);
            }
        }

        @Specialization(replaces = "getIndexSymbolKnownRegexp")
        Object getIndexSymbol(RubyMatchData matchData, RubySymbol symbol, NotProvided length,
                @Cached @Shared InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") @Shared InteropLibrary libInterop) {
            return executeGetIndex(
                    matchData,
                    getBackRef(matchData, getRegexp(matchData), symbol.tstring, symbol.encoding, lazyProfile,
                            libInterop),
                    NotProvided.INSTANCE);
        }

        @Specialization(guards = "libIndex.isRubyString(this, index)", limit = "1")
        Object getIndexString(RubyMatchData matchData, Object index, NotProvided length,
                @Cached @Exclusive RubyStringLibrary libIndex,
                @Cached @Shared InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") @Shared InteropLibrary libInterop) {
            return executeGetIndex(
                    matchData,
                    getBackRef(matchData, getRegexp(matchData), libIndex.getTString(this, index),
                            libIndex.getEncoding(this, index),
                            lazyProfile, libInterop),
                    NotProvided.INSTANCE);
        }

        @Specialization(
                guards = {
                        "!isInteger(index)",
                        "!isRubySymbol(index)",
                        "isNotRubyString(index)",
                        "!isRubyRange(index)" })
        Object getIndexCoerce(RubyMatchData matchData, Object index, NotProvided length,
                @Cached ToIntNode toIntNode) {
            return executeGetIndex(matchData, toIntNode.execute(index), NotProvided.INSTANCE);
        }

        @Specialization(guards = "isRubyRange(range)")
        Object getIndexRange(RubyMatchData matchData, Object range, NotProvided other,
                @Cached RangeNodes.NormalizedStartLengthNode startLengthNode,
                @Cached @Exclusive InlinedConditionProfile negativeStart) {
            final Object[] values = getValuesNode.execute(matchData);
            final int[] startLength = startLengthNode.execute(range, values.length);

            int start = startLength[0];
            int length = Math.max(startLength[1], 0); // negative length leads to returning an empty array

            if (negativeStart.profile(this, start < 0)) {
                return Nil.INSTANCE;
            }

            int end = Math.min(start + length, values.length);
            return createArray(Arrays.copyOfRange(values, start, end));
        }

        @TruffleBoundary
        protected static NameEntry findNameEntry(RubyRegexp regexp, RubySymbol symbol) {
            Regex regex = regexp.regex;

            if (regex.numberOfNames() > 0) {
                var byteArray = symbol.tstring.getInternalByteArrayUncached(symbol.encoding.tencoding);

                for (Iterator<NameEntry> i = regex.namedBackrefIterator(); i.hasNext();) {
                    final NameEntry e = i.next();

                    int nameLen = e.nameEnd - e.nameP;
                    if (nameLen == byteArray.getLength() && ArrayUtils.regionEquals(byteArray.getArray(),
                            byteArray.getOffset(), e.name, e.nameP, byteArray.getLength())) {
                        return e;
                    }
                }
            }
            return null;
        }

        protected RubyRegexp getRegexp(RubyMatchData matchData) {
            if (regexpNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                regexpNode = insert(RegexpNode.create());
            }
            return regexpNode.executeGetRegexp(matchData);
        }

        private int getBackRef(RubyMatchData matchData, RubyRegexp regexp, AbstractTruffleString name, RubyEncoding enc,
                InlinedConditionProfile lazyProfile, InteropLibrary libInterop) {
            if (lazyProfile.profile(this, matchData.tRegexResult != null)) {
                // force the calculation of lazy capture group results before invoking nameToBackrefNumber()
                forceLazyMatchData(matchData, libInterop);
            }
            return nameToBackrefNumber(matchData, regexp, name, enc);
        }

        @TruffleBoundary
        private int nameToBackrefNumber(RubyMatchData matchData, RubyRegexp regexp, AbstractTruffleString name,
                RubyEncoding enc) {
            var byteArray = name.getInternalByteArrayUncached(enc.tencoding);
            try {
                return regexp.regex.nameToBackrefNumber(
                        byteArray.getArray(),
                        byteArray.getOffset(),
                        byteArray.getEnd(),
                        matchData.region);
            } catch (ValueException e) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().indexError(
                                StringUtils
                                        .format("undefined group name reference: %s", name.toJavaStringUncached()),
                                this));
            }
        }

        @TruffleBoundary
        protected static int numBackRefs(NameEntry nameEntry) {
            return nameEntry == null ? 0 : nameEntry.getBackRefs().length;
        }

        @TruffleBoundary
        protected static int backRefIndex(NameEntry nameEntry) {
            return nameEntry == null ? 0 : nameEntry.getBackRefs()[0];
        }
    }

    @CoreMethod(names = "[]", required = 1, optional = 1, lowerFixnum = { 1, 2 })
    public abstract static class GetIndexCoreMethodNode extends CoreMethodArrayArgumentsNode {
        public static GetIndexCoreMethodNode create(RubyNode... nodes) {
            return MatchDataNodesFactory.GetIndexCoreMethodNodeFactory.create(nodes);
        }

        @Specialization
        Object getIndex(RubyMatchData matchData, Object index, Object maybeLength,
                @Cached GetIndexNode getIndexNode) {
            return getIndexNode.execute(matchData, index, maybeLength);
        }
    }

    public static final class GetFixedNameMatchNode extends RubyContextSourceNode {

        @Child RubyNode readMatchNode;
        private final RubySymbol symbol;
        @Child GetIndexNode getIndexNode = MatchDataNodesFactory.GetIndexNodeGen.create();

        public GetFixedNameMatchNode(RubyNode readMatchNode, RubySymbol symbol) {
            this.readMatchNode = readMatchNode;
            this.symbol = symbol;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            RubyMatchData matchData = (RubyMatchData) readMatchNode.execute(frame);
            return getIndexNode.execute(matchData, symbol, NotProvided.INSTANCE);
        }

        @Override
        public RubyNode cloneUninitialized() {
            return new GetFixedNameMatchNode(readMatchNode.cloneUninitialized(), symbol);
        }
    }

    @Primitive(name = "match_data_begin", lowerFixnum = 1)
    public abstract static class BeginNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "inBounds(matchData, index)")
        Object begin(RubyMatchData matchData, int index,
                @Cached InlinedConditionProfile lazyProfile,
                @Cached InlinedConditionProfile negativeBeginProfile,
                @Cached InlinedConditionProfile multiByteCharacterProfile,
                @Cached SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached RubyStringLibrary strings,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop) {
            final int begin = getStart(this, matchData, index, lazyProfile, interop);

            if (negativeBeginProfile.profile(this, begin < 0)) {
                return nil;
            }

            var matchDataSource = strings.getTString(this, matchData.source);
            var encoding = strings.getEncoding(this, matchData.source);

            if (multiByteCharacterProfile.profile(this,
                    !singleByteOptimizableNode.execute(this, matchDataSource, encoding))) {
                return getCharOffsets(matchData, matchDataSource, encoding).getBeg(index);
            }

            return begin;
        }

        @TruffleBoundary
        @Specialization(guards = "!inBounds(matchData, index)")
        Object beginError(RubyMatchData matchData, int index) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().indexError(StringUtils.format("index %d out of matches", index), this));
        }

        protected boolean inBounds(RubyMatchData matchData, int index) {
            return index >= 0 && index < matchData.region.getNumRegs();
        }
    }


    public abstract static class ValuesNode extends RubyBaseNode {

        @NeverDefault
        public static ValuesNode create() {
            return MatchDataNodesFactory.ValuesNodeGen.create();
        }

        public abstract Object[] execute(RubyMatchData matchData);

        @Specialization
        Object[] getValues(RubyMatchData matchData,
                @Cached RubyStringLibrary strings,
                @Cached InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop,
                @Cached InlinedConditionProfile hasValueProfile,
                @Cached InlinedLoopConditionProfile loopProfile,
                @Cached TruffleString.SubstringByteIndexNode substringNode) {
            final Object source = matchData.source;
            final MultiRegion region = matchData.region;
            final Object[] values = new Object[region.getNumRegs()];

            int n = 0;
            try {
                for (; loopProfile.inject(this, n < region.getNumRegs()); n++) {
                    final int start = getStart(this, matchData, n, lazyProfile, interop);
                    final int end = getEnd(this, matchData, n, lazyProfile, interop);

                    if (hasValueProfile.profile(this, start >= 0 && end >= 0)) {
                        values[n] = createSubString(substringNode, strings, source, start, end - start);
                    } else {
                        values[n] = nil;
                    }

                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(this, loopProfile, n);
            }

            return values;
        }

    }

    @Primitive(name = "match_data_end", lowerFixnum = 1)
    public abstract static class EndNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "inBounds(matchData, index)")
        Object end(RubyMatchData matchData, int index,
                @Cached InlinedConditionProfile lazyProfile,
                @Cached InlinedConditionProfile negativeEndProfile,
                @Cached InlinedConditionProfile multiByteCharacterProfile,
                @Cached SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached RubyStringLibrary strings,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop) {
            final int end = getEnd(this, matchData, index, lazyProfile, interop);

            if (negativeEndProfile.profile(this, end < 0)) {
                return nil;
            }

            var matchDataSource = strings.getTString(this, matchData.source);
            var encoding = strings.getEncoding(this, matchData.source);

            if (multiByteCharacterProfile.profile(this,
                    !singleByteOptimizableNode.execute(this, matchDataSource, encoding))) {
                return getCharOffsets(matchData, matchDataSource, encoding).getEnd(index);
            }

            return end;
        }

        @TruffleBoundary
        @Specialization(guards = "!inBounds(matchData, index)")
        Object endError(RubyMatchData matchData, int index) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().indexError(StringUtils.format("index %d out of matches", index), this));
        }

        protected boolean inBounds(RubyMatchData matchData, int index) {
            return index >= 0 && index < matchData.region.getNumRegs();
        }
    }

    @Primitive(name = "match_data_byte_begin", lowerFixnum = 1, isPublic = true)
    public abstract static class ByteBeginNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "inBounds(matchData, index)")
        Object byteBegin(RubyMatchData matchData, int index,
                @Cached InlinedConditionProfile lazyProfile,
                @Cached InlinedConditionProfile negativeBeginProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop) {
            final int begin = getStart(this, matchData, index, lazyProfile, interop);

            if (negativeBeginProfile.profile(this, begin < 0)) {
                return nil;
            } else {
                return begin;
            }
        }

        @Specialization(guards = "!inBounds(matchData, index)")
        Object byteBeginError(RubyMatchData matchData, int index) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().indexError(StringUtils.format("index %d out of matches", index), this));
        }

        protected boolean inBounds(RubyMatchData matchData, int index) {
            return index >= 0 && index < matchData.region.getNumRegs();
        }
    }

    @Primitive(name = "match_data_byte_end", lowerFixnum = 1, isPublic = true)
    public abstract static class ByteEndNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "inBounds(matchData, index)")
        Object byteEnd(RubyMatchData matchData, int index,
                @Cached InlinedConditionProfile lazyProfile,
                @Cached InlinedConditionProfile negativeEndProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop) {
            final int end = getEnd(this, matchData, index, lazyProfile, interop);

            if (negativeEndProfile.profile(this, end < 0)) {
                return nil;
            } else {
                return end;
            }
        }

        @Specialization(guards = "!inBounds(matchData, index)")
        Object byteEndError(RubyMatchData matchData, int index) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().indexError(StringUtils.format("index %d out of matches", index), this));
        }

        protected boolean inBounds(RubyMatchData matchData, int index) {
            return index >= 0 && index < matchData.region.getNumRegs();
        }
    }

    @CoreMethod(names = { "length", "size" })
    public abstract static class LengthNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int length(RubyMatchData matchData) {
            return matchData.region.getNumRegs();
        }

    }

    @CoreMethod(names = "pre_match")
    public abstract static class PreMatchNode extends CoreMethodArrayArgumentsNode {

        public abstract RubyString execute(RubyMatchData matchData);

        @Specialization
        RubyString preMatch(RubyMatchData matchData,
                @Cached InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.SubstringByteIndexNode substringNode) {
            Object source = matchData.source;
            final int length = getStart(this, matchData, 0, lazyProfile, interop);
            return createSubString(substringNode, strings, source, 0, length);
        }
    }

    @CoreMethod(names = "post_match")
    public abstract static class PostMatchNode extends CoreMethodArrayArgumentsNode {

        public abstract RubyString execute(RubyMatchData matchData);

        @Specialization
        RubyString postMatch(RubyMatchData matchData,
                @Cached InlinedConditionProfile lazyProfile,
                @CachedLibrary(limit = "getInteropCacheLimit()") InteropLibrary interop,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.SubstringByteIndexNode substringNode) {
            Object source = matchData.source;
            var tstring = strings.getTString(this, source);
            var encoding = strings.getEncoding(this, source);
            final int start = getEnd(this, matchData, 0, lazyProfile, interop);
            int length = tstring.byteLength(encoding.tencoding) - start;
            return createSubString(substringNode, tstring, encoding, start, length);
        }
    }

    @CoreMethod(names = "to_a")
    public abstract static class ToANode extends CoreMethodArrayArgumentsNode {

        @Child ValuesNode valuesNode = ValuesNode.create();

        @Specialization
        RubyArray toA(RubyMatchData matchData) {
            Object[] objects = valuesNode.execute(matchData);
            return createArray(objects);
        }
    }

    @CoreMethod(names = "regexp")
    public abstract static class RegexpNode extends CoreMethodArrayArgumentsNode {

        public static RegexpNode create() {
            return MatchDataNodesFactory.RegexpNodeFactory.create(null);
        }

        public abstract RubyRegexp executeGetRegexp(RubyMatchData matchData);

        @Specialization
        RubyRegexp regexp(RubyMatchData matchData,
                @Cached InlinedConditionProfile profile,
                @Cached DispatchNode stringToRegexp) {
            final Object value = matchData.regexp;

            if (profile.profile(this, value instanceof RubyRegexp)) {
                return (RubyRegexp) value;
            } else {
                final RubyRegexp regexp = (RubyRegexp) stringToRegexp.call(
                        coreLibrary().truffleTypeModule,
                        "coerce_to_regexp",
                        value,
                        true);
                matchData.regexp = regexp;
                return regexp;
            }
        }

    }

    // Defined only so that #initialize_copy works for #dup and #clone.
    // MatchData.allocate is undefined, see regexp.rb.
    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class InternalAllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyMatchData allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().matchDataShape;
            RubyMatchData matchData = new RubyMatchData(rubyClass, shape, null, null, null);
            AllocationTracing.trace(matchData, this);
            return matchData;
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1, raiseIfFrozenSelf = true)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyMatchData initializeCopy(RubyMatchData self, RubyMatchData from,
                @Cached InlinedConditionProfile copyFromSelfProfile) {
            if (copyFromSelfProfile.profile(this, self == from)) {
                return self;
            }

            self.source = from.source;
            self.regexp = from.regexp;
            self.region = from.region;
            self.charOffsets = from.charOffsets;
            self.tRegexResult = from.tRegexResult;
            return self;
        }

        @Specialization(guards = "!isRubyMatchData(from)")
        RubyMatchData initializeCopy(RubyMatchData self, Object from) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeError("initialize_copy should take same class object", this));
        }
    }

    @Primitive(name = "match_data_get_source")
    public abstract static class GetSourceNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object getSource(RubyMatchData matchData) {
            return matchData.source;
        }
    }

    public static final class Pair implements Comparable<Pair> {
        int bytePos, charPos;

        @Override
        public int compareTo(Pair pair) {
            return bytePos - pair.bytePos;
        }
    }

}
