/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is transposed from org.jruby.RubyString
 * and String Support and licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1
 * used throughout.
 *
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2005 Tim Azzopardi <tim@tigerfive.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
 *
 * Some of the code in this class is transposed from org.jruby.util.ByteList,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 *
 * Copyright (C) 2007-2010 JRuby Community
 * Copyright (C) 2007 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2007 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
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
package org.truffleruby.core.string;

import static org.truffleruby.core.string.TStringConstants.EMPTY_BINARY;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleString.GetByteCodeRangeNode;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import org.graalvm.collections.Pair;
import org.graalvm.shadowed.org.jcodings.ascii.AsciiTables;
import org.graalvm.shadowed.org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToLongNode;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.encoding.EncodingNodes.CheckStringEncodingNode;
import org.truffleruby.core.encoding.EncodingNodes.NegotiateCompatibleStringEncodingNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.FixnumLowerNode;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.range.RangeNodes;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.StringHelperNodes.DeleteBangStringsNode;
import org.truffleruby.core.string.StringHelperNodes.SingleByteOptimizableNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.ReadCallerVariablesNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreModule(value = "String", isClass = true)
public abstract class StringNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class StringAllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString allocate(RubyClass rubyClass,
                @Cached AllocateNode allocateNode) {
            return allocateNode.execute(this, rubyClass);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AllocateNode extends RubyBaseNode {

        public abstract RubyString execute(Node node, RubyClass rubyClass);

        @Specialization
        static RubyString allocate(Node node, RubyClass rubyClass) {
            final RubyString string = new RubyString(
                    rubyClass,
                    getLanguage(node).stringShape,
                    false,
                    EMPTY_BINARY,
                    Encodings.BINARY);
            AllocationTracing.trace(string, node);
            return string;
        }
    }

    @CoreMethod(names = "encoding")
    public abstract static class EncodingNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyEncoding encoding(Object string,
                @Cached RubyStringLibrary libString) {
            return libString.getEncoding(this, string);
        }
    }

    @CoreMethod(names = "+", required = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class AddNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString add(Object string, Object other,
                @Cached ToStrNode toStrNode,
                @Cached StringHelperNodes.StringAppendNode stringAppendNode) {
            final var otherAsString = toStrNode.execute(this, other);
            return stringAppendNode.executeStringAppend(this, string, otherAsString);
        }
    }

    @CoreMethod(names = "*", required = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class StringMulNode extends CoreMethodArrayArgumentsNode {

        // Not ToIntNode, because this works with empty strings, and must throw a different error
        // for long values that don't fit in an int.
        @Specialization
        RubyString doMul(Object string, Object timesObject,
                @Cached FixnumLowerNode fixnumLowerNode,
                @Cached ToLongNode toLongNode,
                @Cached MulNode mulNode) {
            var times = fixnumLowerNode.execute(this, toLongNode.execute(this, timesObject));
            return mulNode.execute(this, string, times);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class MulNode extends RubyBaseNode {

        public abstract RubyString execute(Node node, Object String, Object times);

        @Specialization(guards = "times == 0")
        static RubyString multiplyZero(Node node, Object string, int times,
                @Cached @Shared RubyStringLibrary libString) {
            final RubyEncoding encoding = libString.getEncoding(node, string);
            return createString(node, encoding.tencoding.getEmpty(), encoding);
        }

        @Specialization(guards = "times < 0")
        static RubyString multiplyTimesNegative(Node node, Object string, long times) {
            throw new RaiseException(getContext(node), coreExceptions(node).argumentError("negative argument", node));
        }

        @Specialization(guards = { "times > 0", "!libString.getTString(this, string).isEmpty()" })
        static RubyString multiply(Node node, Object string, int times,
                @Cached InlinedBranchProfile tooBigProfile,
                @Cached @Shared RubyStringLibrary libString,
                @Cached(inline = false) TruffleString.RepeatNode repeatNode) {
            var tstring = libString.getTString(node, string);
            var encoding = libString.getEncoding(node, string);

            long longLength = (long) times * tstring.byteLength(encoding.tencoding);
            if (longLength > Integer.MAX_VALUE) {
                tooBigProfile.enter(node);
                throw tooBig(node);
            }

            return createString(node, repeatNode.execute(tstring, times, encoding.tencoding), encoding);
        }

        @Specialization(guards = { "times > 0", "libString.getTString(this, string).isEmpty()" })
        static RubyString multiplyEmpty(Node node, Object string, long times,
                @Cached @Shared RubyStringLibrary libString) {
            var encoding = libString.getEncoding(node, string);
            return createString(node, encoding.tencoding.getEmpty(), encoding);
        }

        @Specialization(guards = { "times > 0", "!libString.getTString(this, string).isEmpty()" })
        static RubyString multiplyNonEmpty(Node node, Object string, long times,
                @Cached @Shared RubyStringLibrary libString) {
            assert !CoreLibrary.fitsIntoInteger(times);
            throw tooBig(node);
        }

        private static RaiseException tooBig(Node node) {
            // MRI throws this error whenever the total size of the resulting string would exceed LONG_MAX.
            // In TruffleRuby, strings have max length Integer.MAX_VALUE.
            return new RaiseException(getContext(node), coreExceptions(node).argumentError("argument too big", node));
        }
    }

    @ImportStatic(RubyArguments.class)
    @GenerateUncached
    @CoreMethod(names = { "==", "===", "eql?" }, required = 1, alwaysInlined = true)
    public abstract static class EqualNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "libOther.isRubyString(this, other)", limit = "1")
        static boolean equalString(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached RubyStringLibrary libSelf,
                @Cached RubyStringLibrary libOther,
                @Cached NegotiateCompatibleStringEncodingNode negotiateCompatibleStringEncodingNode,
                @Cached StringHelperNodes.StringEqualInternalNode stringEqualInternalNode,
                @Bind("getArgument(rubyArgs, 0)") Object other,
                @Bind Node node) {
            var tstringSelf = libSelf.getTString(node, self);
            var encSelf = libSelf.getEncoding(node, self);
            var tstringOther = libOther.getTString(node, other);
            var encOther = libOther.getEncoding(node, other);
            var compatibleEncoding = negotiateCompatibleStringEncodingNode.execute(node, tstringSelf, encSelf,
                    tstringOther,
                    encOther);
            return stringEqualInternalNode.executeInternal(node, tstringSelf, tstringOther, compatibleEncoding);
        }

        @Specialization(guards = "isNotRubyString(other)")
        boolean equal(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached KernelNodes.RespondToNode respondToNode,
                @Cached DispatchNode objectEqualNode,
                @Cached BooleanCastNode booleanCastNode,
                @Bind("getArgument(rubyArgs, 0)") Object other) {
            if (respondToNode.executeDoesRespondTo(other, coreSymbols().TO_STR, false)) {
                return booleanCastNode.execute(this, objectEqualNode.call(other, "==", self));
            }

            return false;
        }

    }

    @CoreMethod(names = "<<", required = 1, raiseIfNotMutableSelf = true, split = Split.ALWAYS)
    @ImportStatic(StringGuards.class)
    public abstract static class StringConcatOneNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "libFirst.isRubyString(node, first)", limit = "1")
        static RubyString concat(RubyString string, Object first,
                @Bind Node node,
                @Cached StringPrimitiveNodes.StringAppendPrimitiveNode stringAppendNode,
                @Cached RubyStringLibrary libFirst) {
            return stringAppendNode.executeStringAppend(string, first);
        }

        @Specialization(guards = "isNotRubyString(first)")
        Object concatGeneric(RubyString string, Object first,
                @Cached DispatchNode callNode) {
            return callNode.call(coreLibrary().truffleStringOperationsModule, "concat_internal", string, first);
        }

    }

    @CoreMethod(names = "concat", optional = 1, rest = true, raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class StringConcatNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static StringConcatNode create() {
            return StringNodesFactory.StringConcatNodeFactory.create(null);
        }

        public abstract Object executeConcat(RubyString string, Object first, Object[] rest);

        @Specialization(guards = "rest.length == 0")
        RubyString concatZero(RubyString string, NotProvided first, Object[] rest) {
            return string;
        }

        @Specialization(guards = { "rest.length == 0", "libFirst.isRubyString(node, first)" }, limit = "1")
        static RubyString concat(RubyString string, Object first, Object[] rest,
                @Bind Node node,
                @Cached StringPrimitiveNodes.StringAppendPrimitiveNode stringAppendNode,
                @Cached @Exclusive RubyStringLibrary libFirst) {
            return stringAppendNode.executeStringAppend(string, first);
        }

        @Specialization(guards = { "rest.length == 0", "isNotRubyString(first)", "wasProvided(first)" })
        Object concatGeneric(RubyString string, Object first, Object[] rest,
                @Cached DispatchNode callNode) {
            return callNode.call(coreLibrary().truffleStringOperationsModule, "concat_internal", string, first);
        }

        @ExplodeLoop
        @Specialization(
                guards = {
                        "wasProvided(first)",
                        "rest.length > 0",
                        "rest.length == cachedLength",
                        "cachedLength <= MAX_EXPLODE_SIZE" },
                limit = "getDefaultCacheLimit()")
        Object concatMany(RubyString string, Object first, Object[] rest,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("rest.length") int cachedLength,
                @Cached @Shared StringConcatNode argConcatNode,
                @Cached @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared InlinedConditionProfile selfArgProfile) {
            var tstring = string.tstring;
            Object result = argConcatNode.executeConcat(string, first, EMPTY_ARGUMENTS);
            for (int i = 0; i < cachedLength; ++i) {
                Object arg = rest[i];
                final Object argOrCopy = selfArgProfile.profile(this, arg == string)
                        ? createStringCopy(asTruffleStringNode, tstring, libString.getEncoding(this, string))
                        : arg;
                result = argConcatNode.executeConcat(string, argOrCopy, EMPTY_ARGUMENTS);
            }
            return result;
        }

        /** Same implementation as {@link #concatMany}, safe for the use of {@code cachedLength} */
        @Specialization(guards = { "wasProvided(first)", "rest.length > 0" }, replaces = "concatMany")
        Object concatManyGeneral(RubyString string, Object first, Object[] rest,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared StringConcatNode argConcatNode,
                @Cached @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared InlinedConditionProfile selfArgProfile) {
            var tstring = string.tstring;
            Object result = argConcatNode.executeConcat(string, first, EMPTY_ARGUMENTS);
            for (Object arg : rest) {
                final Object argOrCopy = selfArgProfile.profile(this, arg == string)
                        ? createStringCopy(asTruffleStringNode, tstring, libString.getEncoding(this, string))
                        : arg;
                result = argConcatNode.executeConcat(string, argOrCopy, EMPTY_ARGUMENTS);
            }
            return result;
        }
    }

    @CoreMethod(
            names = { "[]", "slice" },
            required = 1,
            optional = 1,
            lowerFixnum = { 1, 2 },
            argumentNames = { "index_start_range_string_or_regexp", "length_capture" })
    public abstract static class GetIndexNode extends CoreMethodArrayArgumentsNode {
        //region Fields

        @Child private StringPrimitiveNodes.StringSubstringPrimitiveNode substringNode;
        @Child private ToLongNode toLongNode;
        @Child private CodePointLengthNode codePointLengthNode;
        private final BranchProfile outOfBounds = BranchProfile.create();

        // endregion
        // region GetIndex Specializations

        @Specialization
        Object getIndex(Object string, int index, NotProvided length,
                @Cached @Shared RubyStringLibrary strings) {
            return index == codePointLength(strings.getTString(this, string), strings.getEncoding(this, string)) // Check for the only difference from str[index, 1]
                    ? outOfBoundsNil()
                    : substring(string, index, 1);
        }

        @Specialization
        Object getIndex(Object string, long index, NotProvided length) {
            assert (int) index != index : "verified via lowerFixnum";
            return outOfBoundsNil();
        }

        @Specialization(
                guards = {
                        "!isRubyRange(index)",
                        "!isRubyRegexp(index)",
                        "isNotRubyString(index)" })
        Object getIndex(Object string, Object index, NotProvided length,
                @Cached @Shared ToLongNode toLongNode,
                @Cached @Shared RubyStringLibrary strings) {
            long indexLong = toLongNode.execute(this, index);
            int indexInt = (int) indexLong;
            return indexInt != indexLong
                    ? outOfBoundsNil()
                    : getIndex(string, indexInt, length, strings);
        }

        // endregion
        // region Two-Arg Slice Specializations

        @Specialization
        Object slice(Object string, int start, int length) {
            return substring(string, start, length);
        }

        @Specialization
        Object slice(Object string, long start, long length,
                @Cached @Shared InlinedBranchProfile negativeLengthProfile) {
            if (length < 0) {
                negativeLengthProfile.enter(this);
                return nil;
            }

            int lengthInt = (int) length;
            if (lengthInt != length) {
                lengthInt = Integer.MAX_VALUE; // go to end of string
            }

            final int startInt = (int) start;
            return startInt != start
                    ? outOfBoundsNil()
                    : substring(string, startInt, lengthInt);
        }

        @Specialization(guards = "wasProvided(length)")
        Object slice(Object string, long start, Object length,
                @Cached @Shared ToLongNode toLongNode,
                @Cached @Shared InlinedBranchProfile negativeLengthProfile) {
            return slice(string, start, toLongNode.execute(this, length), negativeLengthProfile);
        }

        @Specialization(
                guards = {
                        "!isRubyRange(start)",
                        "!isRubyRegexp(start)",
                        "isNotRubyString(start)",
                        "wasProvided(length)" })
        Object slice(Object string, Object start, Object length,
                @Cached @Shared ToLongNode toLongNode,
                @Cached @Shared InlinedBranchProfile negativeLengthProfile) {
            return slice(string, toLongNode.execute(this, start), toLongNode.execute(this, length),
                    negativeLengthProfile);
        }

        // endregion
        // region Range Slice Specializations

        @Specialization(guards = "isRubyRange(range)")
        Object sliceRange(Object string, Object range, NotProvided other,
                @Cached @Shared RubyStringLibrary strings,
                @Cached RangeNodes.NormalizedStartLengthNode startLengthNode,
                @Cached @Exclusive InlinedConditionProfile negativeStart) {
            final int stringLength = codePointLength(strings.getTString(this, string),
                    strings.getEncoding(this, string));
            final int[] startLength = startLengthNode.execute(range, stringLength);

            int start = startLength[0];
            int length = Math.max(startLength[1], 0); // negative length means an empty string should be returned

            if (negativeStart.profile(this, start < 0)) {
                return Nil.INSTANCE;
            }

            return substring(string, start, length);
        }

        // endregion
        // region Regexp Slice Specializations

        @Specialization
        static Object sliceCapture(VirtualFrame frame, Object string, RubyRegexp regexp, Object maybeCapture,
                @Cached @Exclusive DispatchNode callNode,
                @Cached ReadCallerVariablesNode readCallerVariablesNode,
                @Cached @Exclusive InlinedConditionProfile unsetProfile,
                @Cached @Exclusive InlinedConditionProfile sameThreadProfile,
                @Cached @Exclusive InlinedConditionProfile notMatchedProfile,
                @Cached @Exclusive InlinedConditionProfile captureSetProfile,
                @Bind Node node) {

            final Object capture = RubyGuards.wasProvided(maybeCapture) ? maybeCapture : 0;
            final Object matchStrPair = callNode.call(
                    getContext(node).getCoreLibrary().truffleStringOperationsModule,
                    "subpattern",
                    string,
                    regexp,
                    capture);

            final SpecialVariableStorage variables = readCallerVariablesNode.execute(frame);
            if (notMatchedProfile.profile(node, matchStrPair == nil)) {
                variables.setLastMatch(node, nil, getContext(node), unsetProfile, sameThreadProfile);
                return nil;
            } else {
                final Object[] array = (Object[]) ((RubyArray) matchStrPair).getStore();
                final Object matchData = array[0];
                final Object captureStringOrNil = array[1];
                variables.setLastMatch(node, matchData, getContext(node), unsetProfile, sameThreadProfile);
                if (captureSetProfile.profile(node, captureStringOrNil != nil)) {
                    return captureStringOrNil;
                } else {
                    return nil;
                }
            }
        }

        // endregion
        // region String Slice Specialization

        @Specialization(guards = "stringsMatchStr.isRubyString(this, matchStr)", limit = "1")
        static Object slice2(Object string, Object matchStr, NotProvided length,
                @Cached @Exclusive RubyStringLibrary stringsMatchStr,
                @Cached @Exclusive DispatchNode includeNode,
                @Cached BooleanCastNode booleanCastNode,
                @Cached AsTruffleStringNode asTruffleStringNode,
                @Bind Node node) {

            final Object included = includeNode.call(string, "include?", matchStr);

            if (booleanCastNode.execute(node, included)) {
                final RubyEncoding encoding = stringsMatchStr.getEncoding(node, matchStr);
                return createStringCopy(node, asTruffleStringNode, stringsMatchStr.getTString(node, matchStr),
                        encoding);
            }

            return nil;
        }

        // endregion
        // region Helpers

        private Object outOfBoundsNil() {
            outOfBounds.enter();
            return nil;
        }

        private Object substring(Object string, int start, int length) {
            if (substringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                substringNode = insert(StringPrimitiveNodesFactory.StringSubstringPrimitiveNodeFactory.create(null));
            }

            return substringNode.execute(string, start, length);
        }

        private int codePointLength(AbstractTruffleString string, RubyEncoding encoding) {
            if (codePointLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codePointLengthNode = insert(CodePointLengthNode.create());
            }

            return codePointLengthNode.execute(string, encoding.tencoding);
        }

        // endregion
    }

    @CoreMethod(names = "ascii_only?")
    public abstract static class ASCIIOnlyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean asciiOnly(Object string,
                @Cached GetByteCodeRangeNode codeRangeNode,
                @Cached RubyStringLibrary libString) {
            return StringGuards.is7Bit(libString.getTString(this, string), libString.getEncoding(this, string),
                    codeRangeNode);
        }

    }

    @CoreMethod(names = "bytesize")
    public abstract static class ByteSizeNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int byteSize(Object string,
                @Cached RubyStringLibrary libString) {
            return libString.byteLength(this, string);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CaseCmpNode extends RubyBaseNode {

        public abstract Object execute(Node node, Object string, Object other);

        @Specialization(
                guards = "bothSingleByteOptimizable(node, selfTString, selfEncoding, otherTString, otherEncoding, singleByteOptimizableNode)")
        static Object caseCmpSingleByte(Node node, Object string, Object other,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libOther,
                @Cached @Shared InlinedConditionProfile incompatibleEncodingProfile,
                @Cached @Shared InlinedConditionProfile sameProfile,
                @Cached @Shared NegotiateCompatibleStringEncodingNode negotiateCompatibleEncodingNode,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Bind("libString.getTString($node, string)") AbstractTruffleString selfTString,
                @Bind("libString.getEncoding($node, string)") RubyEncoding selfEncoding,
                @Bind("libOther.getTString($node, other)") AbstractTruffleString otherTString,
                @Bind("libOther.getEncoding($node, other)") RubyEncoding otherEncoding,
                @Cached(inline = false) TruffleString.GetInternalByteArrayNode byteArraySelfNode,
                @Cached(inline = false) TruffleString.GetInternalByteArrayNode byteArrayOtherNode) {
            // Taken from org.jruby.RubyString#casecmp19.

            final RubyEncoding encoding = negotiateCompatibleEncodingNode.execute(node, selfTString, selfEncoding,
                    otherTString, otherEncoding);
            if (incompatibleEncodingProfile.profile(node, encoding == null)) {
                return nil;
            }

            var selfByteArray = byteArraySelfNode.execute(selfTString, selfEncoding.tencoding);
            var otherByteArray = byteArrayOtherNode.execute(otherTString, otherEncoding.tencoding);

            if (sameProfile.profile(node, selfTString == otherTString)) {
                return 0;
            }

            return caseInsensitiveCmp(selfByteArray, otherByteArray);
        }

        @Specialization(
                guards = "!bothSingleByteOptimizable(node, selfTString, selfEncoding, otherTString, otherEncoding, singleByteOptimizableNode)")
        static Object caseCmp(Node node, Object string, Object other,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libOther,
                @Cached @Shared InlinedConditionProfile incompatibleEncodingProfile,
                @Cached @Shared InlinedConditionProfile sameProfile,
                @Cached @Shared NegotiateCompatibleStringEncodingNode negotiateCompatibleEncodingNode,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Bind("libString.getTString($node, string)") AbstractTruffleString selfTString,
                @Bind("libString.getEncoding($node, string)") RubyEncoding selfEncoding,
                @Bind("libOther.getTString($node, other)") AbstractTruffleString otherTString,
                @Bind("libOther.getEncoding($node, other)") RubyEncoding otherEncoding) {
            // Taken from org.jruby.RubyString#casecmp19

            final RubyEncoding encoding = negotiateCompatibleEncodingNode.execute(node, selfTString, selfEncoding,
                    otherTString, otherEncoding);

            if (incompatibleEncodingProfile.profile(node, encoding == null)) {
                return nil;
            }

            if (sameProfile.profile(node, selfTString == otherTString)) {
                return 0;
            }

            return StringSupport.multiByteCasecmp(encoding, selfTString, selfEncoding.tencoding, otherTString,
                    otherEncoding.tencoding);
        }

        protected static boolean bothSingleByteOptimizable(Node node, AbstractTruffleString string,
                RubyEncoding stringEncoding, AbstractTruffleString other, RubyEncoding otherEncoding,
                SingleByteOptimizableNode singleByteOptimizableNode) {
            return singleByteOptimizableNode.execute(node, string, stringEncoding) &&
                    singleByteOptimizableNode.execute(node, other, otherEncoding);
        }

        @TruffleBoundary
        private static int caseInsensitiveCmp(InternalByteArray value, InternalByteArray other) {
            // Taken from org.jruby.util.ByteList#caseInsensitiveCmp.
            final int size = value.getLength();
            final int len = Math.min(size, other.getLength());

            for (int offset = -1; ++offset < len;) {
                int myCharIgnoreCase = AsciiTables.ToLowerCaseTable[value.get(offset) & 0xff] & 0xff;
                int otherCharIgnoreCase = AsciiTables.ToLowerCaseTable[other.get(offset) & 0xff] & 0xff;
                if (myCharIgnoreCase < otherCharIgnoreCase) {
                    return -1;
                } else if (myCharIgnoreCase > otherCharIgnoreCase) {
                    return 1;
                }
            }

            return size == other.getLength() ? 0 : size == len ? -1 : 1;
        }
    }

    @CoreMethod(names = "count", rest = true)
    public abstract static class CountNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                guards = "args.length == size",
                limit = "getDefaultCacheLimit()")
        int count(Object string, Object[] args,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared StringHelperNodes.CountStringsNode countStringsNode,
                @Cached @Shared ToStrNode toStrNode,
                @Cached("args.length") int size) {
            final TStringWithEncoding[] tstringsWithEncs = argTStringsWithEncs(args, size, toStrNode, libString,
                    asTruffleStringNode);
            return countStringsNode.execute(string, tstringsWithEncs);
        }

        @Specialization(replaces = "count")
        int countSlow(Object string, Object[] args,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared StringHelperNodes.CountStringsNode countStringsNode,
                @Cached @Shared ToStrNode toStrNode) {
            final TStringWithEncoding[] tstringsWithEncs = argTStringsSlow(args, toStrNode, libString,
                    asTruffleStringNode);
            return countStringsNode.execute(string, tstringsWithEncs);
        }

        @ExplodeLoop
        protected TStringWithEncoding[] argTStringsWithEncs(Object[] args, int size, ToStrNode toStr,
                RubyStringLibrary libString, AsTruffleStringNode asTruffleStringNode) {
            final TStringWithEncoding[] strs = new TStringWithEncoding[args.length];
            for (int i = 0; i < size; i++) {
                final Object string = toStr.execute(this, args[i]);
                strs[i] = new TStringWithEncoding(
                        asTruffleStringNode,
                        libString.getTString(this, string),
                        libString.getEncoding(this, string));
            }
            return strs;
        }

        protected TStringWithEncoding[] argTStringsSlow(Object[] args, ToStrNode toStr, RubyStringLibrary libString,
                AsTruffleStringNode asTruffleStringNode) {
            final TStringWithEncoding[] strs = new TStringWithEncoding[args.length];
            for (int i = 0; i < args.length; i++) {
                final Object string = toStr.execute(this, args[i]);
                strs[i] = new TStringWithEncoding(
                        asTruffleStringNode,
                        libString.getTString(this, string),
                        libString.getEncoding(this, string));
            }
            return strs;
        }
    }

    @CoreMethod(names = "delete!", rest = true, raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class StringDeleteBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object deleteBang(RubyString string, Object[] args,
                @Cached DeleteBangNode deleteBangNode) {
            return deleteBangNode.execute(this, string, args);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class DeleteBangNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyString string, Object[] args);

        @Specialization(guards = "args.length == size", limit = "getDefaultCacheLimit()")
        static Object deleteBang(Node node, RubyString string, Object[] args,
                @Cached @Shared ToStrNode toStrNode,
                @Cached(inline = false) @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared DeleteBangStringsNode deleteBangStringsNode,
                @Cached @Shared RubyStringLibrary rubyStringLibrary,
                @Cached("args.length") int size) {
            final TStringWithEncoding[] tstringsWithEncs = argTStringsWithEncs(node, args, size, toStrNode,
                    asTruffleStringNode, rubyStringLibrary);
            return deleteBangStringsNode.execute(node, string, tstringsWithEncs);
        }

        @Specialization(replaces = "deleteBang")
        static Object deleteBangSlow(Node node, RubyString string, Object[] args,
                @Cached @Shared DeleteBangStringsNode deleteBangStringsNode,
                @Cached(inline = false) @Shared AsTruffleStringNode asTruffleStringNode,
                @Cached @Shared RubyStringLibrary rubyStringLibrary,
                @Cached @Shared ToStrNode toStrNode) {
            final TStringWithEncoding[] tstrings = argTStringsWithEncsSlow(node, args, toStrNode, asTruffleStringNode,
                    rubyStringLibrary);
            return deleteBangStringsNode.execute(node, string, tstrings);
        }

        @ExplodeLoop
        protected static TStringWithEncoding[] argTStringsWithEncs(Node node, Object[] args, int size, ToStrNode toStr,
                AsTruffleStringNode asTruffleStringNode, RubyStringLibrary rubyStringLibrary) {
            final TStringWithEncoding[] strs = new TStringWithEncoding[size];
            for (int i = 0; i < size; i++) {
                final Object string = toStr.execute(node, args[i]);
                strs[i] = new TStringWithEncoding(
                        asTruffleStringNode,
                        rubyStringLibrary.getTString(node, string),
                        rubyStringLibrary.getEncoding(node, string));
            }
            return strs;
        }

        protected static TStringWithEncoding[] argTStringsWithEncsSlow(Node node, Object[] args, ToStrNode toStr,
                AsTruffleStringNode asTruffleStringNode, RubyStringLibrary rubyStringLibrary) {
            final TStringWithEncoding[] strs = new TStringWithEncoding[args.length];
            for (int i = 0; i < args.length; i++) {
                final Object string = toStr.execute(node, args[i]);
                strs[i] = new TStringWithEncoding(
                        asTruffleStringNode,
                        rubyStringLibrary.getTString(node, string),
                        rubyStringLibrary.getEncoding(node, string));
            }
            return strs;
        }
    }

    @CoreMethod(names = "each_byte", needsBlock = true, enumeratorSize = "bytesize")
    public abstract static class EachByteNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static EachByteNode create() {
            return StringNodesFactory.EachByteNodeFactory.create(null);
        }

        public abstract Object execute(Object string, RubyProc block);

        // use separate specialization instances for getTString() in the loop
        @Specialization(guards = "strings.seen(this, string)", limit = "2")
        static Object eachByte(Object string, RubyProc block,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.MaterializeNode materializeNode,
                @Cached TruffleString.ReadByteNode readByteNode,
                @Cached CallBlockNode yieldNode,
                @Bind Node node) {
            var tstring = strings.getTString(node, string);
            var encoding = strings.getEncoding(node, string).tencoding;

            // String#each_byte reflects changes by the block to the string's bytes
            materializeNode.execute(tstring, encoding);
            for (int i = 0; i < tstring.byteLength(encoding); i++) {
                int singleByte = readByteNode.execute(tstring, i, encoding);
                yieldNode.yield(node, block, singleByte);

                tstring = strings.getTString(node, string);
                encoding = strings.getEncoding(node, string).tencoding;
            }

            return string;
        }
    }

    @CoreMethod(names = "bytes", needsBlock = true)
    public abstract static class StringBytesNode extends CoreMethodArrayArgumentsNode {

        // use separate specialization instances for getTString() in the loop
        @Specialization(guards = "strings.seen(node, string)", limit = "2")
        static RubyArray bytesWithoutBlock(Object string, Nil block,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.MaterializeNode materializeNode,
                @Cached TruffleString.ReadByteNode readByteNode) {
            var tstring = strings.getTString(node, string);
            var encoding = strings.getEncoding(node, string).tencoding;
            int arrayLength = tstring.byteLength(encoding);

            final int[] store = new int[arrayLength];

            materializeNode.execute(tstring, encoding);
            for (int i = 0; i < arrayLength; i++) {
                store[i] = readByteNode.execute(tstring, i, encoding);
            }

            return createArray(node, store);
        }

        @Specialization
        Object bytesWithBlock(Object string, RubyProc block,
                @Cached EachByteNode eachByteNode) {
            return eachByteNode.execute(string, block);
        }
    }

    @CoreMethod(names = "each_char", needsBlock = true, enumeratorSize = "size")
    public abstract static class EachCharNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static EachCharNode create() {
            return StringNodesFactory.EachCharNodeFactory.create(null);
        }

        public abstract Object execute(Object string, RubyProc block);

        @Specialization
        Object eachChar(Object string, RubyProc block,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached CallBlockNode yieldNode) {
            // Unlike String#each_byte, String#each_char does not make
            // modifications to the string visible to the rest of the iteration.
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            var tencoding = encoding.tencoding;
            final int byteLength = tstring.byteLength(tencoding);

            int clen;
            for (int i = 0; i < byteLength; i += clen) {
                clen = byteLengthOfCodePointNode.execute(tstring, i, tencoding);
                yieldNode.yield(this, block, createSubString(substringNode, tstring, encoding, i, clen));
            }

            return string;
        }
    }

    @CoreMethod(names = "chars", needsBlock = true)
    public abstract static class CharsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object charsWithoutBlock(Object string, Nil unusedBlock,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            // Unlike String#each_byte, String#chars does not make
            // modifications to the string visible to the rest of the iteration.
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            var tencoding = encoding.tencoding;
            final int byteLength = tstring.byteLength(tencoding);

            int codePointLength = codePointLengthNode.execute(tstring, tencoding);
            Object[] chars = new Object[codePointLength];

            int characterIndex = 0;
            int clen;
            for (int i = 0; i < byteLength; i += clen) {
                clen = byteLengthOfCodePointNode.execute(tstring, i, encoding.tencoding);
                chars[characterIndex++] = createSubString(substringNode, tstring, encoding, i, clen);
            }

            return createArray(chars);
        }

        @Specialization
        Object charsWithBlock(Object string, RubyProc block,
                @Cached EachCharNode eachCharNode) {
            return eachCharNode.execute(string, block);
        }
    }

    @CoreMethod(names = "each_codepoint", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(StringGuards.class)
    public abstract static class EachCodePointNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static EachCodePointNode create() {
            return StringNodesFactory.EachCodePointNodeFactory.create(null);
        }

        public abstract Object execute(Object string, RubyProc block);

        @Specialization
        Object eachCodePoint(Object string, RubyProc block,
                @Cached RubyStringLibrary strings,
                @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
                @Cached TruffleStringIterator.NextNode nextNode,
                @Cached InlinedBranchProfile invalidCodePointProfile,
                @Cached CallBlockNode yieldNode) {
            // Unlike String#each_byte, String#each_codepoint does not make
            // modifications to the string visible to the rest of the iteration.
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            var tencoding = encoding.tencoding;
            var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);

            while (iterator.hasNext()) {
                int codePoint = nextNode.execute(iterator, tencoding);

                if (codePoint == -1) {
                    invalidCodePointProfile.enter(this);
                    throw new RaiseException(getContext(),
                            coreExceptions().argumentErrorInvalidByteSequence(encoding, this));
                }

                yieldNode.yield(this, block, codePoint);
            }

            return string;
        }

    }

    @CoreMethod(names = "codepoints", needsBlock = true)
    @ImportStatic({ StringGuards.class })
    public abstract static class CodePointsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object codePointsWithoutBlock(Object string, Nil unusedBlock,
                @Cached RubyStringLibrary strings,
                @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
                @Cached TruffleStringIterator.NextNode nextNode,
                @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                @Cached InlinedBranchProfile invalidCodePointProfile) {
            // Unlike String#each_byte, String#codepoints does not make
            // modifications to the string visible to the rest of the iteration.
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            var tencoding = encoding.tencoding;

            int codePointLength = codePointLengthNode.execute(tstring, tencoding);
            int[] codePoints = new int[codePointLength];

            var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);

            int i = 0;
            while (iterator.hasNext()) {
                int codePoint = nextNode.execute(iterator, tencoding);

                if (codePoint == -1) {
                    invalidCodePointProfile.enter(this);
                    throw new RaiseException(getContext(),
                            coreExceptions().argumentErrorInvalidByteSequence(encoding, this));
                }

                codePoints[i++] = codePoint;
            }

            return createArray(codePoints);
        }

        @Specialization
        Object codePointsWithBlock(Object string, RubyProc block,
                @Cached EachCodePointNode eachCodePointNode) {
            return eachCodePointNode.execute(string, block);
        }

    }

    @ImportStatic(StringGuards.class)
    @CoreMethod(names = "force_encoding", required = 1, raiseIfNotMutableSelf = true)
    public abstract static class ForceEncodingNode extends CoreMethodArrayArgumentsNode {

        public abstract RubyString execute(Object string, Object other);

        protected abstract RubyString execute(Object string, RubyEncoding other);

        public static ForceEncodingNode create() {
            return StringNodesFactory.ForceEncodingNodeFactory.create(null);
        }

        @Specialization(guards = "string.getEncodingUnprofiled() == newEncoding")
        RubyString sameEncoding(RubyString string, RubyEncoding newEncoding) {
            return string;
        }

        @Specialization(guards = { "encoding != newEncoding", "tstring.isImmutable()" })
        RubyString immutable(RubyString string, RubyEncoding newEncoding,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary profileEncoding,
                @Cached TruffleString.ForceEncodingNode forceEncodingNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var newEncodingProfiled = profileEncoding.profileEncoding(this, newEncoding);
            var newTString = forceEncodingNode.execute(tstring, encoding.tencoding, newEncodingProfiled.tencoding);
            string.setTString(newTString, newEncodingProfiled);
            return string;
        }

        @Specialization(
                guards = { "encoding != newEncoding", "!tstring.isImmutable()", "!tstring.isNative()" })
        RubyString mutableManaged(RubyString string, RubyEncoding newEncoding,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary profileEncoding,
                @Cached MutableTruffleString.ForceEncodingNode forceEncodingNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var newEncodingProfiled = profileEncoding.profileEncoding(this, newEncoding);
            var newTString = forceEncodingNode.execute(tstring, encoding.tencoding, newEncodingProfiled.tencoding);
            string.setTString(newTString, newEncodingProfiled);
            return string;
        }

        @Specialization(
                guards = { "encoding != newEncoding", "!tstring.isImmutable()", "tstring.isNative()" })
        RubyString mutableNative(RubyString string, RubyEncoding newEncoding,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary profileEncoding,
                @Cached TruffleString.GetInternalNativePointerNode getInternalNativePointerNode,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var newEncodingProfiled = profileEncoding.profileEncoding(this, newEncoding);
            var currentEncoding = encoding.tencoding;
            var pointer = (Pointer) getInternalNativePointerNode.execute(tstring, currentEncoding);
            var byteLength = tstring.byteLength(currentEncoding);
            var newTString = fromNativePointerNode.execute(pointer, 0, byteLength, newEncodingProfiled.tencoding,
                    false);
            string.setTString(newTString, newEncodingProfiled);
            return string;
        }

        @Specialization(guards = "libEncoding.isRubyString(this, newEncoding)", limit = "1")
        static RubyString forceEncodingString(RubyString string, Object newEncoding,
                @Cached @Exclusive RubyStringLibrary libEncoding,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached InlinedBranchProfile errorProfile,
                @Cached @Exclusive ForceEncodingNode forceEncodingNode,
                @Bind Node node) {
            final String stringName = toJavaStringNode.execute(node, newEncoding);
            final RubyEncoding rubyEncoding = getContext(node).getEncodingManager().getRubyEncoding(stringName);

            if (rubyEncoding == null) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).argumentError(Utils.concat("unknown encoding name - ", stringName), node));
            }

            return forceEncodingNode.execute(string, rubyEncoding);
        }

        @Specialization(guards = { "!isRubyEncoding(newEncoding)", "isNotRubyString(newEncoding)" })
        static RubyString forceEncoding(RubyString string, Object newEncoding,
                @Cached ToStrNode toStrNode,
                @Cached @Exclusive ForceEncodingNode forceEncodingNode,
                @Bind Node node) {
            return forceEncodingNode.execute(string, toStrNode.execute(node, newEncoding));
        }
    }

    @CoreMethod(names = "getbyte", required = 1, lowerFixnum = 1)
    public abstract static class StringGetByteNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.ReadByteNode readByteNode = TruffleString.ReadByteNode.create();

        @Specialization
        Object getByte(Object string, int index,
                @Cached InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached RubyStringLibrary libString,
                @Cached StringHelperNodes.NormalizeIndexNode normalizeIndexNode) {
            var tstring = libString.getTString(this, string);
            var encoding = libString.getEncoding(this, string).tencoding;
            int byteLength = tstring.byteLength(encoding);

            final int normalizedIndex = normalizeIndexNode.executeNormalize(this, index, byteLength);

            if (indexOutOfBoundsProfile.profile(this, (normalizedIndex < 0) || (normalizedIndex >= byteLength))) {
                return nil;
            }

            return readByteNode.execute(tstring, normalizedIndex, encoding);
        }

    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        protected static final int CLASS_SALT = 54008340; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

        public static HashNode create() {
            return StringNodesFactory.HashNodeFactory.create(null);
        }

        public abstract long execute(Object string);

        @Specialization
        long hash(Object string,
                @Cached StringHelperNodes.HashStringNode hash) {
            return hash.execute(this, string);
        }
    }

    @CoreMethod(names = "initialize_copy", required = 1, raiseIfNotMutableSelf = true)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "areEqual(self, from)")
        Object initializeCopySelfIsSameAsFrom(RubyString self, Object from) {
            return self;
        }

        @Specialization(guards = {
                "stringsFrom.isRubyString(this, from)",
                "!areEqual(self, from)",
                "!tstring.isNative()",
                "tstring.isImmutable()" })
        Object initializeCopyImmutable(RubyString self, Object from,
                @Cached @Shared RubyStringLibrary stringsFrom,
                @Cached @Shared WriteObjectFieldNode writeAssociatedNode,
                @Cached @Shared StringHelperNodes.StringGetAssociatedNode stringGetAssociatedNode,
                @Bind("stringsFrom.getTString($node, from)") AbstractTruffleString tstring) {
            self.setTString(tstring, stringsFrom.getEncoding(this, from));

            final Object associated = stringGetAssociatedNode.execute(this, from);
            copyAssociated(self, associated, writeAssociatedNode);
            return self;
        }

        @Specialization(guards = {
                "stringsFrom.isRubyString(this, from)",
                "!areEqual(self, from)",
                "!tstring.isNative()",
                "tstring.isMutable()" })
        Object initializeCopyMutable(RubyString self, Object from,
                @Cached @Shared RubyStringLibrary stringsFrom,
                @Cached @Shared WriteObjectFieldNode writeAssociatedNode,
                @Cached @Shared StringHelperNodes.StringGetAssociatedNode stringGetAssociatedNode,
                @Cached MutableTruffleString.SubstringByteIndexNode copyMutableTruffleStringNode,
                @Bind("stringsFrom.getTString($node, from)") AbstractTruffleString tstring) {
            var encoding = stringsFrom.getEncoding(this, from);
            var tencoding = encoding.tencoding;
            int byteLength = tstring.byteLength(tencoding);
            // TODO (eregon, 2022): Should the copy be a MutableTruffleString too, or TruffleString with AsTruffleStringNode?
            MutableTruffleString copy = copyMutableTruffleStringNode.execute(tstring, 0, byteLength, tencoding);
            self.setTString(copy, encoding);

            final Object associated = stringGetAssociatedNode.execute(this, from);
            copyAssociated(self, associated, writeAssociatedNode);
            return self;
        }

        @Specialization(guards = { "!areEqual(self, from)", "tstring.isNative()" })
        Object initializeCopyNative(RubyString self, RubyString from,
                @Cached @Shared RubyStringLibrary stringsFrom,
                @Cached @Shared StringHelperNodes.StringGetAssociatedNode stringGetAssociatedNode,
                @Cached @Shared WriteObjectFieldNode writeAssociatedNode,
                @Cached TruffleString.GetInternalNativePointerNode getInternalNativePointerNode,
                @Cached MutableTruffleString.FromNativePointerNode fromNativePointerNode,
                @Bind("from.tstring") AbstractTruffleString tstring) {
            var encoding = stringsFrom.getEncoding(this, from);
            var tencoding = encoding.tencoding;
            final Pointer fromPointer = (Pointer) getInternalNativePointerNode.execute(tstring, tencoding);

            final Pointer newPointer = Pointer.mallocAutoRelease(getLanguage(), getContext(), fromPointer.getSize());
            newPointer.writeBytes(0, fromPointer, 0, fromPointer.getSize());

            // TODO (eregon, 2022): should we have the copy be native too, or rather take the opportunity of having to copy to be managed?
            assert tstring.isMutable();
            var copy = fromNativePointerNode.execute(newPointer, 0, tstring.byteLength(tencoding), tencoding, false);
            self.setTString(copy, encoding);

            final Object associated = stringGetAssociatedNode.execute(this, from);
            copyAssociated(self, associated, writeAssociatedNode);
            return self;
        }

        protected static boolean areEqual(Object one, Object two) {
            return one == two;
        }

        private void copyAssociated(RubyString self, Object associated, WriteObjectFieldNode writeAssociatedNode) {
            if (associated != null) {
                writeAssociatedNode.execute(this, self, Layouts.ASSOCIATED_IDENTIFIER, associated);
            }
        }
    }

    @CoreMethod(names = "lstrip!", raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class LstripBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "string.tstring.isEmpty()")
        Object lstripBangEmptyString(RubyString string) {
            return nil;
        }

        @Specialization(guards = "!string.tstring.isEmpty()")
        static Object lstripBangSingleByte(RubyString string,
                @Cached RubyStringLibrary libString,
                @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Cached TruffleStringIterator.NextNode nextNode,
                @Cached InlinedBranchProfile allWhitespaceProfile,
                @Cached InlinedBranchProfile nonSpaceCodePointProfile,
                @Cached InlinedBranchProfile badCodePointProfile,
                @Cached InlinedConditionProfile noopProfile,
                @Bind Node node) {
            var tstring = string.tstring;
            var encoding = libString.getEncoding(node, string);
            var tencoding = encoding.tencoding;

            var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);
            int codePoint = nextNode.execute(iterator, tencoding);

            // Check the first code point to see if it's broken. In the case of strings without leading spaces,
            // this check can avoid having to compile the while loop.
            if (codePoint == -1) {
                badCodePointProfile.enter(node);
                throw new RaiseException(getContext(node),
                        coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
            }

            // Check the first code point to see if it's a space. In the case of strings without leading spaces,
            // this check can avoid having to compile the while loop.
            if (noopProfile.profile(node, !StringSupport.isAsciiSpaceOrNull(codePoint))) {
                return nil;
            }

            while (iterator.hasNext()) {
                int byteIndex = iterator.getByteIndex();
                codePoint = nextNode.execute(iterator, tencoding);

                if (codePoint == -1) {
                    badCodePointProfile.enter(node);
                    throw new RaiseException(getContext(node),
                            coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
                }

                if (!StringSupport.isAsciiSpaceOrNull(codePoint)) {
                    nonSpaceCodePointProfile.enter(node);
                    string.setTString(makeSubstring(substringNode, tstring, tencoding, byteIndex));

                    return string;
                }
            }

            // If we've made it this far, the string must consist only of whitespace. Otherwise, we would have exited
            // early in the first code point check or in the iterator when the first non-space character was encountered.
            allWhitespaceProfile.enter(node);
            string.setTString(tencoding.getEmpty());

            return string;
        }

        private static AbstractTruffleString makeSubstring(TruffleString.SubstringByteIndexNode substringNode,
                AbstractTruffleString base, TruffleString.Encoding encoding,
                int byteOffset) {
            int substringByteLength = base.byteLength(encoding) - byteOffset;

            return substringNode.execute(base, byteOffset, substringByteLength, encoding, true);
        }

    }

    @CoreMethod(names = "ord")
    @ImportStatic(StringGuards.class)
    public abstract static class OrdNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "strings.getTString(this, string).isEmpty()")
        int ordEmpty(Object string,
                @Cached @Shared RubyStringLibrary strings) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("empty string", this));
        }

        @Specialization(guards = "!strings.getTString(this, string).isEmpty()")
        int ord(Object string,
                @Cached @Shared RubyStringLibrary strings,
                @Cached StringHelperNodes.GetCodePointNode getCodePointNode) {
            return getCodePointNode.executeGetCodePoint(this, strings.getTString(this, string),
                    strings.getEncoding(this, string),
                    0);
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReplaceNode extends RubyBaseNode {

        public abstract RubyString execute(Node node, RubyString string, Object other);

        @Specialization(guards = "string == other")
        static RubyString replaceStringIsSameAsOther(RubyString string, RubyString other) {
            return string;
        }

        @Specialization(guards = { "string != other" })
        static RubyString replace(Node node, RubyString string, RubyString other,
                @Cached @Exclusive RubyStringLibrary libOther,
                @Cached(inline = false) AsTruffleStringNode asTruffleStringNode) {
            var encoding = libOther.getEncoding(node, other);
            TruffleString immutableCopy = asTruffleStringNode.execute(other.tstring, encoding.tencoding);
            string.setTString(immutableCopy, encoding);
            return string;
        }

        @Specialization
        static RubyString replace(Node node, RubyString string, ImmutableRubyString other,
                @Cached @Exclusive RubyStringLibrary libOther) {
            string.setTString(other.tstring, libOther.getEncoding(node, other));
            return string;
        }
    }

    @CoreMethod(names = "rstrip!", raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class RstripBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "string.tstring.isEmpty()")
        Object rstripBangEmptyString(RubyString string) {
            return nil;
        }

        @Specialization(guards = "!string.tstring.isEmpty()")
        static Object rstripBangNonEmptyString(RubyString string,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.CreateBackwardCodePointIteratorNode createBackwardCodePointIteratorNode,
                @Cached TruffleStringIterator.PreviousNode previousNode,
                @Cached InlinedBranchProfile allWhitespaceProfile,
                @Cached InlinedBranchProfile nonSpaceCodePointProfile,
                @Cached InlinedBranchProfile badCodePointProfile,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Cached @Exclusive InlinedConditionProfile noopProfile,
                @Bind Node node) {
            var tstring = string.tstring;
            var encoding = libString.getEncoding(node, string);
            var tencoding = encoding.tencoding;

            var iterator = createBackwardCodePointIteratorNode.execute(tstring, tencoding,
                    ErrorHandling.RETURN_NEGATIVE);
            int codePoint = previousNode.execute(iterator, tencoding);

            // Check the last code point to see if it's broken. In the case of strings without trailing spaces,
            // this check can avoid having to compile the while loop.
            if (codePoint == -1) {
                badCodePointProfile.enter(node);
                throw new RaiseException(getContext(node),
                        coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
            }

            // Check the last code point to see if it's a space. In the case of strings without trailing spaces,
            // this check can avoid having to compile the while loop.
            if (noopProfile.profile(node, !StringSupport.isAsciiSpaceOrNull(codePoint))) {
                return nil;
            }

            while (iterator.hasPrevious()) {
                int byteIndex = iterator.getByteIndex();
                codePoint = previousNode.execute(iterator, tencoding);

                if (codePoint == -1) {
                    badCodePointProfile.enter(node);
                    throw new RaiseException(getContext(node),
                            coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
                }

                if (!StringSupport.isAsciiSpaceOrNull(codePoint)) {
                    nonSpaceCodePointProfile.enter(node);
                    string.setTString(makeSubstring(substringNode, tstring, tencoding, byteIndex));

                    return string;
                }
            }

            // If we've made it this far, the string must consist only of whitespace. Otherwise, we would have exited
            // early in the first code point check or in the iterator when the first non-space character was encountered.
            allWhitespaceProfile.enter(node);
            string.setTString(tencoding.getEmpty());

            return string;
        }

        private static AbstractTruffleString makeSubstring(TruffleString.SubstringByteIndexNode substringNode,
                AbstractTruffleString base, TruffleString.Encoding encoding,
                int byteEnd) {
            return substringNode.execute(base, 0, byteEnd, encoding, true);
        }

    }

    @CoreMethod(names = "dump")
    @ImportStatic(StringGuards.class)
    public abstract static class DumpNode extends CoreMethodArrayArgumentsNode {

        private static final byte[] FORCE_ENCODING_CALL_BYTES = StringOperations.encodeAsciiBytes(".force_encoding(\"");

        @TruffleBoundary
        @Specialization(guards = "isAsciiCompatible(libString.getEncoding(this, string))")
        RubyString dumpAsciiCompatible(Object string,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode) {
            ByteArrayBuilder outputBytes = dumpCommon(new ATStringWithEncoding(this, libString, string));

            return createString(fromByteArrayNode, outputBytes.getBytes(), libString.getEncoding(this, string));
        }

        @TruffleBoundary
        @Specialization(guards = "!isAsciiCompatible(libString.getEncoding(this, string))")
        RubyString dump(Object string,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode) {
            ByteArrayBuilder outputBytes = dumpCommon(new ATStringWithEncoding(this, libString, string));

            outputBytes.append(FORCE_ENCODING_CALL_BYTES);
            outputBytes.append(libString.getEncoding(this, string).getNameByteArray());
            outputBytes.append((byte) '"');
            outputBytes.append((byte) ')');

            return createString(fromByteArrayNode, outputBytes.getBytes(), Encodings.BINARY);
        }

        // Taken from org.jruby.RubyString#dump
        private ByteArrayBuilder dumpCommon(ATStringWithEncoding string) {
            ByteArrayBuilder buf = null;
            final var encoding = string.encoding;
            final var enc = encoding.jcoding;
            final var cr = string.getCodeRange();

            var byteArray = string.getInternalByteArray();
            final int offset = byteArray.getOffset();
            int p = offset;
            final int end = byteArray.getEnd();
            byte[] bytes = byteArray.getArray();

            int len = 2;
            while (p < end) {
                int c = bytes[p++] & 0xff;

                switch (c) {
                    case '"':
                    case '\\':
                    case '\n':
                    case '\r':
                    case '\t':
                    case '\f':
                    case '\013':
                    case '\010':
                    case '\007':
                    case '\033':
                        len += 2;
                        break;
                    case '#':
                        len += p < end && isEVStr(bytes[p] & 0xff) ? 2 : 1;
                        break;
                    default:
                        if (ASCIIEncoding.INSTANCE.isPrint(c)) {
                            len++;
                        } else {
                            if (enc.isUTF8()) {
                                int n = string.characterLength(p - 1 - offset) - 1;
                                if (n > 0) {
                                    if (buf == null) {
                                        buf = new ByteArrayBuilder();
                                    }
                                    int cc = StringSupport.codePoint(enc, cr, bytes, p - 1, end, this);
                                    buf.append(StringUtils.formatASCIIBytes("%x", cc));
                                    len += buf.getLength() + 4;
                                    buf.setLength(0);
                                    p += n;
                                    break;
                                }
                            }
                            len += 4;
                        }
                        break;
                }
            }

            if (!enc.isAsciiCompatible()) {
                len += FORCE_ENCODING_CALL_BYTES.length + encoding.getNameByteArray().length + "\")".length();
            }

            TStringBuilder outBytes = new TStringBuilder();
            outBytes.unsafeEnsureSpace(len);
            byte[] out = outBytes.getUnsafeBytes();
            int q = 0;
            p = offset;

            out[q++] = '"';
            while (p < end) {
                int c = bytes[p++] & 0xff;
                if (c == '"' || c == '\\') {
                    out[q++] = '\\';
                    out[q++] = (byte) c;
                } else if (c == '#') {
                    if (p < end && isEVStr(bytes[p] & 0xff)) {
                        out[q++] = '\\';
                    }
                    out[q++] = '#';
                } else if (c == '\n') {
                    out[q++] = '\\';
                    out[q++] = 'n';
                } else if (c == '\r') {
                    out[q++] = '\\';
                    out[q++] = 'r';
                } else if (c == '\t') {
                    out[q++] = '\\';
                    out[q++] = 't';
                } else if (c == '\f') {
                    out[q++] = '\\';
                    out[q++] = 'f';
                } else if (c == '\013') {
                    out[q++] = '\\';
                    out[q++] = 'v';
                } else if (c == '\010') {
                    out[q++] = '\\';
                    out[q++] = 'b';
                } else if (c == '\007') {
                    out[q++] = '\\';
                    out[q++] = 'a';
                } else if (c == '\033') {
                    out[q++] = '\\';
                    out[q++] = 'e';
                } else if (ASCIIEncoding.INSTANCE.isPrint(c)) {
                    out[q++] = (byte) c;
                } else {
                    out[q++] = '\\';
                    if (enc.isUTF8()) {
                        int n = string.characterLength(p - 1 - offset) - 1;
                        if (n > 0) {
                            int cc = StringSupport.codePoint(enc, cr, bytes, p - 1, end, this);
                            p += n;
                            outBytes.setLength(q);

                            String format = (cc <= 0xFFFF) ? "u%04X" : "u{%X}";
                            outBytes.append(StringUtils.formatASCIIBytes(format, cc));

                            q = outBytes.getLength();
                            continue;
                        }
                    }
                    outBytes.setLength(q);
                    outBytes.append(StringUtils.formatASCIIBytes("x%02X", c));
                    q = outBytes.getLength();
                }
            }
            out[q++] = '"';
            outBytes.setLength(q);
            assert out == outBytes.getUnsafeBytes(); // must not reallocate

            return outBytes;
        }

        private static boolean isEVStr(int c) {
            return c == '$' || c == '@' || c == '{';
        }

    }

    @CoreMethod(names = "undump")
    @ImportStatic(StringGuards.class)
    public abstract static class UndumpNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isAsciiCompatible(libString.getEncoding(this, string))")
        RubyString undumpAsciiCompatible(Object string,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared RubyStringLibrary libString) {
            // Taken from org.jruby.RubyString#undump
            var encoding = libString.getEncoding(this, string);
            Pair<TStringBuilder, RubyEncoding> outputBytesResult = StringSupport.undump(
                    new ATStringWithEncoding(libString.getTString(this, string), encoding),
                    encoding,
                    getContext(),
                    this);
            final RubyEncoding rubyEncoding = outputBytesResult.getRight();
            return createString(outputBytesResult.getLeft().toTStringUnsafe(fromByteArrayNode), rubyEncoding);
        }

        @Specialization(guards = "!isAsciiCompatible(libString.getEncoding(this, string))")
        RubyString undumpNonAsciiCompatible(Object string,
                @Cached @Shared RubyStringLibrary libString) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().encodingCompatibilityError(
                            Utils.concat("ASCII incompatible encoding: ", libString.getEncoding(this, string)),
                            this));
        }
    }

    @CoreMethod(names = "setbyte", required = 2, raiseIfNotMutableSelf = true, lowerFixnum = { 1, 2 })
    @ImportStatic(StringGuards.class)
    public abstract static class StringSetByteNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int doSetByte(RubyString string, Object indexObject, Object valueObject,
                @Cached ToIntNode toIntIndexNode,
                @Cached ToIntNode toIntValueNode,
                @Cached SetByteNode setByteNode) {
            int index = toIntIndexNode.execute(indexObject);
            int value = toIntValueNode.execute(valueObject);
            return setByteNode.execute(this, string, index, value);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetByteNode extends RubyBaseNode {

        public abstract int execute(Node node, RubyString string, int index, int value);

        @Specialization(guards = "tstring.isMutable()")
        static int mutable(Node node, RubyString string, int index, int value,
                @Cached @Shared StringHelperNodes.CheckIndexNode checkIndexNode,
                @Cached @Shared RubyStringLibrary libString,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Cached(inline = false) @Shared MutableTruffleString.WriteByteNode writeByteNode) {
            var tencoding = libString.getTEncoding(node, string);
            final int normalizedIndex = checkIndexNode.execute(node, index, tstring.byteLength(tencoding));

            writeByteNode.execute((MutableTruffleString) tstring, normalizedIndex, (byte) value, tencoding);
            return value;
        }

        @Specialization(guards = "!tstring.isMutable()")
        static int immutable(Node node, RubyString string, int index, int value,
                @Cached @Shared StringHelperNodes.CheckIndexNode checkIndexNode,
                @Cached @Shared RubyStringLibrary libString,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Cached(inline = false) MutableTruffleString.AsMutableTruffleStringNode asMutableTruffleStringNode,
                @Cached(inline = false) @Shared MutableTruffleString.WriteByteNode writeByteNode) {
            var tencoding = libString.getTEncoding(node, string);
            final int normalizedIndex = checkIndexNode.execute(node, index, tstring.byteLength(tencoding));

            MutableTruffleString mutableTString = asMutableTruffleStringNode.execute(tstring, tencoding);
            writeByteNode.execute(mutableTString, normalizedIndex, (byte) value, tencoding);
            string.setTString(mutableTString);
            return value;
        }
    }

    @CoreMethod(names = { "size", "length" })
    @ImportStatic(StringGuards.class)
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        public static SizeNode create() {
            return StringNodesFactory.SizeNodeFactory.create(null);
        }

        public abstract int execute(Object string);

        @Specialization
        int size(Object string,
                @Cached RubyStringLibrary libString,
                @Cached CodePointLengthNode codePointLengthNode) {
            return codePointLengthNode.execute(libString.getTString(this, string),
                    libString.getTEncoding(this, string));
        }

    }

    @CoreMethod(names = "squeeze!", rest = true, raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class SqueezeBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "string.tstring.isEmpty()")
        Object squeezeBangEmptyString(RubyString string, Object[] args) {
            return nil;
        }

        @TruffleBoundary
        @Specialization(guards = { "!string.tstring.isEmpty()", "noArguments(args)" })
        Object squeezeBangZeroArgs(RubyString string, Object[] args,
                @Cached SingleByteOptimizableNode singleByteOptimizableNode) {
            // Taken from org.jruby.RubyString#squeeze_bang19.

            final TStringBuilder buffer = TStringBuilder.create(string);

            final boolean[] squeeze = new boolean[StringSupport.TRANS_SIZE];
            for (int i = 0; i < StringSupport.TRANS_SIZE; i++) {
                squeeze[i] = true;
            }

            if (StringGuards.isSingleByteOptimizable(this, string.tstring, string.getEncodingUncached(),
                    singleByteOptimizableNode)) {
                if (!StringSupport.singleByteSqueeze(buffer, squeeze)) {
                    return nil;
                } else {
                    string.setTString(buffer.toTString(), buffer.getRubyEncoding());
                }
            } else {
                var codeRange = string.tstring
                        .getByteCodeRangeUncached(RubyStringLibrary.getUncached().getTEncoding(this, string));
                if (!StringSupport.multiByteSqueeze(buffer, codeRange, squeeze, null,
                        string.getEncodingUncached().jcoding, false, this)) {
                    return nil;
                } else {
                    string.setTString(buffer.toTString(), buffer.getRubyEncoding());
                }
            }

            return string;
        }

        @Specialization(guards = { "!string.tstring.isEmpty()", "!noArguments(args)" })
        static Object squeezeBang(RubyString string, Object[] args,
                @Cached CheckStringEncodingNode checkEncodingNode,
                @Cached ToStrNode toStrNode,
                @Bind Node node) {
            // Taken from org.jruby.RubyString#squeeze_bang19.

            final Object[] otherStrings = new Object[args.length];

            for (int i = 0; i < args.length; i++) {
                otherStrings[i] = toStrNode.execute(node, args[i]);
            }

            return performSqueezeBang(node, string, otherStrings, checkEncodingNode);
        }

        @TruffleBoundary
        private static Object performSqueezeBang(Node node, RubyString string, Object[] otherStrings,
                CheckStringEncodingNode checkEncodingNode) {

            final TStringBuilder buffer = TStringBuilder.create(string);

            Object otherStr = otherStrings[0];
            var otherTString = RubyStringLibrary.getUncached().getTString(node, otherStr);
            var otherEncoding = RubyStringLibrary.getUncached().getEncoding(node, otherStr);
            RubyEncoding enc = checkEncodingNode.execute(node, string.tstring, string.getEncodingUncached(),
                    otherTString, otherEncoding);
            final boolean squeeze[] = new boolean[StringSupport.TRANS_SIZE + 1];

            boolean singlebyte = TStringUtils.isSingleByteOptimizable(string.tstring, string.getEncodingUncached()) &&
                    TStringUtils.isSingleByteOptimizable(otherTString, otherEncoding);

            if (singlebyte && otherTString.byteLength(otherEncoding.tencoding) == 1 && otherStrings.length == 1) {
                squeeze[otherTString.readByteUncached(0, otherEncoding.tencoding)] = true;
                if (!StringSupport.singleByteSqueeze(buffer, squeeze)) {
                    return nil;
                } else {
                    string.setTString(buffer.toTString(), buffer.getRubyEncoding());
                    return string;
                }
            }

            StringSupport.TrTables tables = StringSupport
                    .trSetupTable(otherTString, otherEncoding, squeeze, null, true, enc.jcoding, node);

            for (int i = 1; i < otherStrings.length; i++) {
                otherStr = otherStrings[i];
                otherTString = RubyStringLibrary.getUncached().getTString(node, otherStr);
                otherEncoding = RubyStringLibrary.getUncached().getEncoding(node, otherStr);
                enc = checkEncodingNode.execute(node, string.tstring, string.getEncodingUncached(), otherTString,
                        otherEncoding);
                singlebyte = singlebyte && TStringUtils.isSingleByteOptimizable(otherTString, otherEncoding);
                tables = StringSupport.trSetupTable(otherTString, otherEncoding, squeeze, tables, false, enc.jcoding,
                        node);
            }

            if (singlebyte) {
                if (!StringSupport.singleByteSqueeze(buffer, squeeze)) {
                    return nil;
                } else {
                    string.setTString(buffer.toTString(), buffer.getRubyEncoding());
                }
            } else {
                var codeRange = string.tstring
                        .getByteCodeRangeUncached(RubyStringLibrary.getUncached().getTEncoding(node, string));
                if (!StringSupport.multiByteSqueeze(buffer, codeRange, squeeze, tables, enc.jcoding, true, node)) {
                    return nil;
                } else {
                    string.setTString(buffer.toTString(), buffer.getRubyEncoding());
                }
            }

            return string;
        }

    }

    @CoreMethod(names = "succ!", raiseIfNotMutableSelf = true)
    public abstract static class SuccBangNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyString succBang(RubyString string,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            if (!string.tstring.isEmpty()) {
                final TStringBuilder succBuilder = StringSupport.succCommon(string, this);
                string.setTString(
                        fromByteArrayNode.execute(succBuilder.getBytes(), libString.getTEncoding(this, string), false));
            }

            return string;
        }
    }

    // String#sum is in Java because without OSR we can't warm up the Rubinius implementation

    @CoreMethod(names = "sum", optional = 1)
    public abstract static class SumNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static SumNode create() {
            return StringNodesFactory.SumNodeFactory.create(null);
        }

        public abstract Object executeSum(Object string, Object bits);

        @Child private DispatchNode addNode = DispatchNode.create();
        @Child private TruffleString.GetInternalByteArrayNode byteArrayNode = TruffleString.GetInternalByteArrayNode
                .create();

        @Specialization
        Object sum(Object string, long bits,
                @Cached @Shared RubyStringLibrary strings) {
            // Copied from JRuby

            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string).tencoding;
            var byteArray = byteArrayNode.execute(tstring, encoding);

            var bytes = byteArray.getArray();
            int p = byteArray.getOffset();
            final int end = byteArray.getEnd();

            if (bits >= 8 * 8) { // long size * bits in byte
                Object sum = 0;
                while (p < end) {
                    sum = addNode.call(sum, "+", bytes[p++] & 0xff);
                }
                return sum;
            } else {
                long sum = 0;
                while (p < end) {
                    sum += bytes[p++] & 0xff;
                }
                return bits == 0 ? sum : sum & (1L << bits) - 1L;
            }
        }

        @Specialization
        Object sum(Object string, NotProvided bits,
                @Cached @Shared RubyStringLibrary strings) {
            return sum(string, 16, strings);
        }

        @Specialization(guards = { "!isImplicitLong(bits)", "wasProvided(bits)" })
        static Object sum(Object string, Object bits,
                @Cached ToLongNode toLongNode,
                @Cached SumNode sumNode,
                @Bind Node node) {
            return sumNode.executeSum(string, toLongNode.execute(node, bits));
        }

    }

    @CoreMethod(names = "to_f")
    public abstract static class ToFNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        double toF(Object string,
                @Cached RubyStringLibrary strings) {
            try {
                return convertToDouble(strings.getTString(this, string), strings.getEncoding(this, string));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @TruffleBoundary
        private double convertToDouble(AbstractTruffleString tstring, RubyEncoding encoding) {
            return new DoubleConverter().parse(tstring, encoding, false, true);
        }
    }

    @CoreMethod(names = { "to_s", "to_str" })
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        ImmutableRubyString toS(ImmutableRubyString string) {
            return string;
        }

        @Specialization(guards = "!isStringSubclass(string)")
        RubyString toS(RubyString string) {
            return string;
        }

        @Specialization(guards = "isStringSubclass(string)")
        RubyString toSOnSubclass(RubyString string,
                @Cached RubyStringLibrary libString,
                @Cached AsTruffleStringNode asTruffleStringNode) {
            return createStringCopy(asTruffleStringNode, string.tstring, libString.getEncoding(this, string));
        }

        public boolean isStringSubclass(RubyString string) {
            return string.getLogicalClass() != coreLibrary().stringClass;
        }
    }

    @CoreMethod(names = "reverse!", raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class ReverseBangNode extends CoreMethodArrayArgumentsNode {

        @Child CodePointLengthNode codePointLengthNode = CodePointLengthNode.create();
        @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

        @Specialization(guards = "reverseIsEqualToSelf(tstring, encoding, codePointLengthNode)")
        RubyString reverseNoOp(RubyString string,
                @Cached @Shared RubyStringLibrary libString,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            return string;
        }

        @Specialization(
                guards = {
                        "!reverseIsEqualToSelf(tstring, encoding, codePointLengthNode)",
                        "isSingleByteOptimizable(this, tstring, encoding, singleByteOptimizableNode)" })
        RubyString reverseSingleByteOptimizable(RubyString string,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var tencoding = encoding.tencoding;
            var byteArray = byteArrayNode.execute(tstring, tencoding);

            final int len = byteArray.getLength();
            final byte[] reversedBytes = new byte[len];

            for (int i = 0; i < len; i++) {
                reversedBytes[len - i - 1] = byteArray.get(i);
            }

            string.setTString(fromByteArrayNode.execute(reversedBytes, tencoding, false)); // codeRangeNode.execute(rope), codePointLengthNode.execute(rope)
            return string;
        }

        @Specialization(
                guards = {
                        "!reverseIsEqualToSelf(tstring, encoding, codePointLengthNode)",
                        "!isSingleByteOptimizable(this, tstring, encoding, singleByteOptimizableNode)" })
        RubyString reverse(RubyString string,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached @Shared TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            // Taken from org.jruby.RubyString#reverse!

            var tencoding = encoding.tencoding;
            var byteArray = byteArrayNode.execute(tstring, tencoding);

            var originalBytes = byteArray.getArray();
            int byteOffset = byteArray.getOffset();
            int p = byteOffset;
            final int len = byteArray.getLength();

            final int end = p + len;
            int op = len;
            final byte[] reversedBytes = new byte[len];

            while (p < end) {
                int cl = byteLengthOfCodePointNode.execute(tstring, p - byteOffset, tencoding);
                if (cl > 1 || (originalBytes[p] & 0x80) != 0) {
                    op -= cl;
                    System.arraycopy(originalBytes, p, reversedBytes, op, cl);
                    p += cl;
                } else {
                    reversedBytes[--op] = originalBytes[p++];
                }
            }

            string.setTString(fromByteArrayNode.execute(reversedBytes, tencoding, false)); // codeRangeNode.execute(rope), codePointLengthNode.execute(rope)
            return string;
        }

        public static boolean reverseIsEqualToSelf(AbstractTruffleString tstring, RubyEncoding encoding,
                CodePointLengthNode codePointLengthNode) {
            return codePointLengthNode.execute(tstring, encoding.tencoding) <= 1;
        }
    }

    @CoreMethod(names = "tr!", required = 2, raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class StringTrBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object trBang(RubyString self, Object fromStr, Object toStr,
                @Cached ToStrNode fromStrNode,
                @Cached ToStrNode toStrNode,
                @Cached TrBangNode trBangNode) {
            final var fromStrAsString = fromStrNode.execute(this, fromStr);
            final var toStrAsString = toStrNode.execute(this, toStr);
            return trBangNode.execute(this, self, fromStrAsString, toStrAsString);

        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class TrBangNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyString self, Object fromStr, Object toStr);

        @Specialization(guards = "self.tstring.isEmpty()")
        static Object trBangSelfEmpty(RubyString self, Object fromStr, Object toStr) {
            return nil;
        }

        @Specialization(
                guards = {
                        "!self.tstring.isEmpty()",
                        "libToStr.getTString(node, toStr).isEmpty()" },
                limit = "1")
        static Object trBangToEmpty(Node node, RubyString self, Object fromStr, Object toStr,
                @Cached DeleteBangNode deleteBangNode,
                @Cached @Exclusive RubyStringLibrary libToStr) {
            return deleteBangNode.execute(node, self, new Object[]{ fromStr });
        }

        @Specialization(
                guards = {
                        "libFromStr.isRubyString(node, fromStr)",
                        "!self.tstring.isEmpty()",
                        "!libToStr.getTString(node, toStr).isEmpty()" },
                limit = "1")
        static Object trBangNoEmpty(Node node, RubyString self, Object fromStr, Object toStr,
                @Cached CheckStringEncodingNode checkEncodingNode,
                @Cached @Exclusive RubyStringLibrary libFromStr,
                @Cached @Exclusive RubyStringLibrary libToStr) {
            return StringHelperNodes.trTransHelper(node, checkEncodingNode, self, libFromStr, fromStr, libToStr, toStr,
                    false);
        }
    }

    @CoreMethod(names = "tr_s!", required = 2, raiseIfNotMutableSelf = true)
    @ImportStatic(StringGuards.class)
    public abstract static class TrSBangNode extends CoreMethodArrayArgumentsNode {

        @Specialization(
                guards = { "self.tstring.isEmpty()" })
        Object trSBangEmpty(RubyString self, Object fromStr, Object toStr) {
            return nil;
        }

        @Specialization(
                guards = {
                        "libFromStr.isRubyString(this, fromStrAsString)",
                        "libToStr.isRubyString(this, toStrAsString)",
                        "!self.tstring.isEmpty()" },
                limit = "1")
        static Object trSBang(RubyString self, Object fromStr, Object toStr,
                @Cached ToStrNode fromStrNode,
                @Cached ToStrNode toStrNode,
                @Bind Node node,
                @Bind("fromStrNode.execute(node, fromStr)") Object fromStrAsString,
                @Bind("toStrNode.execute(node, toStr)") Object toStrAsString,
                @Cached CheckStringEncodingNode checkEncodingNode,
                @Cached DeleteBangNode deleteBangNode,
                @Cached RubyStringLibrary libFromStr,
                @Cached RubyStringLibrary libToStr) {
            if (libToStr.getTString(node, toStrAsString).isEmpty()) {
                return deleteBangNode.execute(node, self, new Object[]{ fromStrAsString });
            }

            return StringHelperNodes.trTransHelper(node, checkEncodingNode, self, libFromStr, fromStrAsString, libToStr,
                    toStrAsString,
                    true);
        }
    }

    @CoreMethod(names = "valid_encoding?")
    public abstract static class ValidEncodingQueryNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean validEncoding(Object string,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.IsValidNode isValidNode) {
            return isValidNode.execute(libString.getTString(this, string), libString.getTEncoding(this, string));
        }

    }

    @CoreMethod(names = "clear", raiseIfNotMutableSelf = true)
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyString clear(RubyString string,
                @Cached RubyStringLibrary libString) {
            string.setTString(libString.getTEncoding(this, string).getEmpty());
            return string;
        }
    }

    @CoreMethod(names = "empty?")
    public abstract static class EmptyNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean empty(Object self,
                @Cached RubyStringLibrary libString) {
            return libString.getTString(this, self).isEmpty();
        }
    }

}
