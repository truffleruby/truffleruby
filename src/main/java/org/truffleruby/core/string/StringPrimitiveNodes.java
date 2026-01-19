/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
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

import static com.oracle.truffle.api.strings.TruffleString.CodeRange.ASCII;
import static com.oracle.truffle.api.strings.TruffleString.CodeRange.BROKEN;
import static com.oracle.truffle.api.strings.TruffleString.CodeRange.VALID;
import static org.truffleruby.core.string.TStringConstants.EMPTY_BINARY;
import static org.truffleruby.core.string.StringSupport.MBCLEN_CHARFOUND_LEN;
import static org.truffleruby.core.string.StringSupport.MBCLEN_CHARFOUND_P;
import static org.truffleruby.core.string.StringSupport.MBCLEN_INVALID_P;
import static org.truffleruby.core.string.StringSupport.MBCLEN_NEEDMORE_P;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleString.GetByteCodeRangeNode;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import org.graalvm.shadowed.org.jcodings.Config;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.ascii.AsciiTables;
import org.graalvm.shadowed.org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.encoding.EncodingNodes.CheckStringEncodingNode;
import org.truffleruby.core.encoding.EncodingNodes.NegotiateCompatibleStringEncodingNode;
import org.truffleruby.core.encoding.IsCharacterHeadNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.unpack.ArrayResult;
import org.truffleruby.core.format.unpack.UnpackCompiler;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringHelperNodes.SingleByteOptimizableNode;
import org.truffleruby.core.support.RubyByteArray;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.DeferredRaiseException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreModule(value = "StringPrimitives", isClass = true)
public abstract class StringPrimitiveNodes {

    // compatibleEncoding is RubyEncoding or Nil in this node
    @Primitive(name = "string_cmp")
    public abstract static class CompareNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "first.isEmpty() || second.isEmpty()")
        int empty(Object a, Object b, RubyEncoding compatibleEncoding,
                @Cached @Shared RubyStringLibrary libA,
                @Cached @Shared RubyStringLibrary libB,
                @Bind("libA.getTString($node, a)") AbstractTruffleString first,
                @Bind("libB.getTString($node, b)") AbstractTruffleString second,
                @Cached @Exclusive InlinedConditionProfile bothEmpty) {
            if (bothEmpty.profile(this, first.isEmpty() && second.isEmpty())) {
                return 0;
            } else {
                return first.isEmpty() ? -1 : 1;
            }
        }

        @Specialization(guards = { "!first.isEmpty()", "!second.isEmpty()" })
        int compatible(Object a, Object b, RubyEncoding compatibleEncoding,
                @Cached @Shared RubyStringLibrary libA,
                @Cached @Shared RubyStringLibrary libB,
                @Bind("libA.getTString($node, a)") AbstractTruffleString first,
                @Bind("libB.getTString($node, b)") AbstractTruffleString second,
                @Cached @Shared InlinedConditionProfile sameStringProfile,
                @Cached @Shared TruffleString.CompareBytesNode compareBytesNode,
                @Cached @Shared InlinedConditionProfile equalProfile,
                @Cached @Shared InlinedConditionProfile positiveProfile) {
            if (sameStringProfile.profile(this, first == second)) {
                return 0;
            }

            int result = compareBytesNode.execute(first, second, compatibleEncoding.tencoding);
            if (equalProfile.profile(this, result == 0)) {
                return 0;
            } else {
                return positiveProfile.profile(this, result > 0) ? 1 : -1;
            }
        }

        @Specialization
        int notCompatible(Object a, Object b, Nil compatibleEncoding,
                @Cached @Shared RubyStringLibrary libA,
                @Cached @Shared RubyStringLibrary libB,
                @Cached @Shared InlinedConditionProfile sameStringProfile,
                @Cached @Shared TruffleString.CompareBytesNode compareBytesNode,
                @Cached TruffleString.ForceEncodingNode forceEncoding1Node,
                @Cached TruffleString.ForceEncodingNode forceEncoding2Node,
                @Cached @Shared InlinedConditionProfile equalProfile,
                @Cached @Shared InlinedConditionProfile positiveProfile,
                @Cached @Exclusive InlinedConditionProfile encodingIndexGreaterThanProfile) {
            var first = libA.getTString(this, a);
            var firstEncoding = libA.getEncoding(this, a);
            var second = libB.getTString(this, b);
            var secondEncoding = libB.getEncoding(this, b);

            if (sameStringProfile.profile(this, first == second)) {
                return 0;
            }

            // Compare as binary as CRuby compares bytes regardless of the encodings
            var firstBinary = forceEncoding1Node.execute(first, firstEncoding.tencoding, Encodings.BINARY.tencoding);
            var secondBinary = forceEncoding2Node.execute(second, secondEncoding.tencoding, Encodings.BINARY.tencoding);
            int result = compareBytesNode.execute(firstBinary, secondBinary, Encodings.BINARY.tencoding);

            if (equalProfile.profile(this, result == 0)) {
                if (encodingIndexGreaterThanProfile.profile(this, firstEncoding.index > secondEncoding.index)) {
                    return 1;
                } else {
                    return -1;
                }
            }

            return positiveProfile.profile(this, result > 0) ? 1 : -1;
        }

    }

    @Primitive(name = "dup_as_string_instance")
    public abstract static class StringDupAsStringInstanceNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyString dupAsStringInstance(Object string,
                @Cached RubyStringLibrary strings,
                @Cached AsTruffleStringNode asTruffleStringNode) {
            final RubyEncoding encoding = strings.getEncoding(this, string);
            return createStringCopy(asTruffleStringNode, strings.getTString(this, string), encoding);
        }
    }

    @Primitive(name = "string_casecmp")
    public abstract static class StringCaseCmpNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object caseCmp(Object string, Object other,
                @Cached ToStrNode toStrNode,
                @Cached CaseCmpNode caseCmpNode) {
            final var otherAsString = toStrNode.execute(this, other);
            return caseCmpNode.execute(this, string, otherAsString);
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

    /** Returns true if the first bytes in string are equal to the bytes in prefix. */
    @Primitive(name = "string_start_with?")
    public abstract static class StartWithNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean startWithBytes(Object string, Object prefix, RubyEncoding enc,
                @Cached TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                @Cached RubyStringLibrary strings,
                @Cached RubyStringLibrary stringsSuffix) {

            var stringTString = strings.getTString(this, string);
            var stringEncoding = strings.getTEncoding(this, string);
            final int stringByteLength = stringTString.byteLength(stringEncoding);

            var prefixTString = stringsSuffix.getTString(this, prefix);
            var prefixEncoding = stringsSuffix.getTEncoding(this, prefix);
            final int prefixByteLength = prefixTString.byteLength(prefixEncoding);

            if (stringByteLength < prefixByteLength) {
                return false;
            }

            // See truffle-string.md, section Encodings Compatibility
            if (prefixByteLength == 0) {
                return true;
            }

            return regionEqualByteIndexNode.execute(stringTString, 0, prefixTString, 0, prefixByteLength,
                    enc.tencoding);
        }

    }

    /** Returns true if the last bytes in string are equal to the bytes in suffix. */
    @Primitive(name = "string_end_with?")
    public abstract static class EndWithNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean endWithBytes(Object string, Object suffix, RubyEncoding enc,
                @Cached IsCharacterHeadNode isCharacterHeadNode,
                @Cached TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                @Cached RubyStringLibrary strings,
                @Cached RubyStringLibrary stringsSuffix,
                @Cached InlinedConditionProfile isCharacterHeadProfile) {

            var stringTString = strings.getTString(this, string);
            var stringEncoding = strings.getEncoding(this, string);
            final int stringByteLength = stringTString.byteLength(stringEncoding.tencoding);

            var suffixTString = stringsSuffix.getTString(this, suffix);
            var suffixEncoding = stringsSuffix.getTEncoding(this, suffix);
            final int suffixByteLength = suffixTString.byteLength(suffixEncoding);

            if (stringByteLength < suffixByteLength) {
                return false;
            }

            // See truffle-string.md, section Encodings Compatibility
            if (suffixByteLength == 0) {
                return true;
            }

            final int offset = stringByteLength - suffixByteLength;

            if (isCharacterHeadProfile.profile(this,
                    !isCharacterHeadNode.execute(stringEncoding, stringTString, offset))) {
                return false;
            }

            return regionEqualByteIndexNode.execute(stringTString, offset, suffixTString, 0, suffixByteLength,
                    enc.tencoding);
        }

    }

    @Primitive(name = "string_downcase!", raiseIfNotMutable = 0, lowerFixnum = 1)
    @ImportStatic({ StringGuards.class, Config.class })
    public abstract static class StringDowncaseBangPrimitiveNode extends PrimitiveArrayArgumentsNode {

        private final ConditionProfile dummyEncodingProfile = ConditionProfile.create();

        @Specialization(
                guards = "!isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object downcaseAsciiCodePoints(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("createUpperToLower()") StringHelperNodes.InvertAsciiCaseNode invertAsciiCaseNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(
                guards = "isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object downcaseMultiByteComplex(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached GetByteCodeRangeNode codeRangeNode,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Cached InlinedConditionProfile modifiedProfile,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);

            // TODO (nirvdrum 24-Jun-22): Make the byte array builder copy-on-write so we don't eagerly clone the source byte array.
            var builder = ByteArrayBuilder.create(byteArray);

            var cr = codeRangeNode.execute(string.tstring, encoding.tencoding);
            final boolean modified = StringSupport
                    .downcaseMultiByteComplex(encoding.jcoding, cr, builder, caseMappingOptions, this);

            if (modifiedProfile.profile(this, modified)) {
                string.setTString(fromByteArrayNode.execute(builder.getBytes(), encoding.tencoding, false));
                return string;
            } else {
                return nil;
            }
        }

    }

    @Primitive(name = "string_initialize")
    public abstract static class InitializeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyString initializeJavaString(RubyString string, String from, RubyEncoding encoding,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var tstring = fromJavaStringNode.execute(from, encoding.tencoding);
            string.setTString(tstring, encoding);
            return string;
        }

        @Specialization
        RubyString initializeJavaStringNoEncoding(RubyString string, String from, Nil encoding) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().argumentError(
                            "String.new(javaString) needs to be called with an Encoding like String.new(javaString, encoding: someEncoding)",
                            this));
        }

        @Specialization(guards = "stringsFrom.isRubyString(this, from)", limit = "1")
        RubyString initialize(RubyString string, Object from, Object encoding,
                @Cached @Exclusive RubyStringLibrary stringsFrom) {
            string.setTString(stringsFrom.getTString(this, from), stringsFrom.getEncoding(this, from));
            return string;
        }

        @Specialization(guards = { "isNotRubyString(from)", "!isString(from)" })
        static RubyString initialize(VirtualFrame frame, RubyString string, Object from, Object encoding,
                @Cached @Exclusive RubyStringLibrary stringLibrary,
                @Cached ToStrNode toStrNode,
                @Bind Node node) {
            final Object stringFrom = toStrNode.execute(node, from);
            string.setTString(stringLibrary.getTString(node, stringFrom), stringLibrary.getEncoding(node, stringFrom));
            return string;
        }

    }

    @Primitive(name = "string_get_coderange")
    public abstract static class GetCodeRangeAsIntNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        int getCodeRange(Object string,
                @Cached RubyStringLibrary strings,
                @Cached GetByteCodeRangeNode codeRangeNode) {
            final var tstring = strings.getTString(this, string);

            var codeRange = codeRangeNode.execute(tstring, strings.getTEncoding(this, string));
            if (codeRange == ASCII) {
                return 1;
            } else if (codeRange == VALID) {
                return 2;
            } else {
                assert codeRange == BROKEN;
                return 3;
            }
        }
    }

    @Primitive(name = "string_replace", raiseIfNotMutable = 0)
    public abstract static class StringReplaceNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object replaceNode(RubyString string, Object other,
                @Cached ToStrNode toStrNode,
                @Cached ReplaceNode replaceNode) {
            final var otherAsString = toStrNode.execute(this, other);
            return replaceNode.execute(this, string, otherAsString);
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

    @Primitive(name = "string_scrub")
    @ImportStatic(StringGuards.class)
    public abstract static class ScrubNode extends PrimitiveArrayArgumentsNode {

        @Child GetByteCodeRangeNode codeRangeNode = GetByteCodeRangeNode.create();
        @Child private TruffleString.ConcatNode concatNode = TruffleString.ConcatNode.create();
        @Child private TruffleString.GetInternalByteArrayNode byteArrayNode = TruffleString.GetInternalByteArrayNode
                .create();
        @Child TruffleString.SubstringByteIndexNode substringNode = TruffleString.SubstringByteIndexNode.create();

        @Specialization(
                guards = { "isBrokenCodeRange(tstring, encoding, codeRangeNode)", "isAsciiCompatible(encoding)" })
        RubyString scrubAsciiCompat(Object string, RubyProc block,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Exclusive TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Bind("strings.getTString($node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding($node, string)") RubyEncoding encoding,
                @Cached @Shared CallBlockNode yieldNode) {
            final Encoding enc = encoding.jcoding;
            var tencoding = encoding.tencoding;
            TruffleString buf = EMPTY_BINARY;

            var byteArray = byteArrayNode.execute(tstring, tencoding);
            final int e = tstring.byteLength(tencoding);

            int p = 0;
            int p1 = p;

            p = StringSupport.searchNonAscii(byteArray, p);
            if (p < 0) {
                p = e;
            }
            while (p < e) {
                int clen = byteLengthOfCodePointNode.execute(tstring, p, tencoding,
                        ErrorHandling.RETURN_NEGATIVE);
                if (MBCLEN_NEEDMORE_P(clen)) {
                    break;
                } else if (MBCLEN_CHARFOUND_P(clen)) {
                    p += MBCLEN_CHARFOUND_LEN(clen);
                } else if (MBCLEN_INVALID_P(clen)) {
                    // p1~p: valid ascii/multibyte chars
                    // p ~e: invalid bytes + unknown bytes
                    clen = enc.maxLength();
                    if (p > p1) {
                        buf = concatNode.execute(buf,
                                substringNode.execute(tstring, p1, p - p1, tencoding, true),
                                tencoding, true);
                    }

                    if (e - p < clen) {
                        clen = e - p;
                    }
                    if (clen <= 2) {
                        clen = 1;
                    } else {
                        clen--;
                        for (; clen > 1; clen--) {
                            var subTString = substringNode.execute(tstring, p, clen, tencoding, true);
                            int clen2 = byteLengthOfCodePointNode.execute(subTString, 0, tencoding,
                                    ErrorHandling.RETURN_NEGATIVE);
                            if (MBCLEN_NEEDMORE_P(clen2)) {
                                break;
                            }
                        }
                    }
                    Object repl = yieldNode.yield(this, block,
                            createSubString(substringNode, tstring, encoding, p, clen));
                    buf = concatNode.execute(buf, strings.getTString(this, repl), tencoding, true);
                    p += clen;
                    p1 = p;
                    p = StringSupport.searchNonAscii(byteArray, p);
                    if (p < 0) {
                        p = e;
                        break;
                    }
                }
            }

            if (p1 < p) {
                buf = concatNode.execute(buf,
                        substringNode.execute(tstring, p1, p - p1, tencoding, true), tencoding,
                        true);
            }

            if (p < e) {
                Object repl = yieldNode.yield(this, block,
                        createSubString(substringNode, tstring, encoding, p, e - p));
                buf = concatNode.execute(buf, strings.getTString(this, repl), tencoding, true);
            }

            return createString(buf, encoding);
        }

        @Specialization(
                guards = {
                        "isBrokenCodeRange(tstring, encoding, codeRangeNode)",
                        "!isAsciiCompatible(encoding)" })
        RubyString scrubAsciiIncompatible(Object string, RubyProc block,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Exclusive TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Bind("strings.getTString($node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding($node, string)") RubyEncoding encoding,
                @Cached @Shared CallBlockNode yieldNode) {
            final Encoding enc = encoding.jcoding;
            var tencoding = encoding.tencoding;
            TruffleString buf = EMPTY_BINARY;

            final int e = tstring.byteLength(tencoding);
            int p = 0;
            int p1 = p;
            final int mbminlen = enc.minLength();

            while (p < e) {
                int clen = byteLengthOfCodePointNode.execute(tstring, p, tencoding, ErrorHandling.RETURN_NEGATIVE);
                if (MBCLEN_NEEDMORE_P(clen)) {
                    break;
                } else if (MBCLEN_CHARFOUND_P(clen)) {
                    p += MBCLEN_CHARFOUND_LEN(clen);
                } else if (MBCLEN_INVALID_P(clen)) {
                    final int q = p;
                    clen = enc.maxLength();

                    if (p > p1) {
                        buf = concatNode.execute(buf,
                                substringNode.execute(tstring, p1, p - p1, tencoding, true),
                                tencoding, true);
                    }

                    if (e - p < clen) {
                        clen = e - p;
                    }
                    if (clen <= mbminlen * 2) {
                        clen = mbminlen;
                    } else {
                        clen -= mbminlen;
                        for (; clen > mbminlen; clen -= mbminlen) {
                            var subTString = substringNode.execute(tstring, q, clen, tencoding, true);
                            int clen2 = byteLengthOfCodePointNode.execute(subTString, 0, tencoding,
                                    ErrorHandling.RETURN_NEGATIVE);
                            if (MBCLEN_NEEDMORE_P(clen2)) {
                                break;
                            }
                        }
                    }

                    RubyString repl = (RubyString) yieldNode.yield(this, block,
                            createSubString(substringNode, tstring, encoding, p, clen));
                    buf = concatNode.execute(buf, repl.tstring, tencoding, true);
                    p += clen;
                    p1 = p;
                }
            }

            if (p1 < p) {
                buf = concatNode.execute(buf, substringNode.execute(tstring, p1, p - p1, tencoding, true),
                        tencoding,
                        true);
            }

            if (p < e) {
                RubyString repl = (RubyString) yieldNode.yield(this, block,
                        createSubString(substringNode, tstring, encoding, p, e - p));
                buf = concatNode.execute(buf, repl.tstring, tencoding, true);
            }

            return createString(buf, encoding);
        }

    }

    @Primitive(name = "string_swapcase!", raiseIfNotMutable = 0, lowerFixnum = 1)
    @ImportStatic({ StringGuards.class, Config.class })
    public abstract static class StringSwapcaseBangPrimitiveNode extends PrimitiveArrayArgumentsNode {

        private final ConditionProfile dummyEncodingProfile = ConditionProfile.create();

        @Specialization(
                guards = "!isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object swapcaseAsciiCodePoints(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("createSwapCase()") StringHelperNodes.InvertAsciiCaseNode invertAsciiCaseNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(
                guards = "isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object swapcaseMultiByteComplex(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached GetByteCodeRangeNode codeRangeNode,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Cached InlinedConditionProfile modifiedProfile,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            // Taken from org.jruby.RubyString#swapcase_bang19.
            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);

            // TODO (nirvdrum 24-Jun-22): Make the byte array builder copy-on-write so we don't eagerly clone the source byte array.
            var builder = ByteArrayBuilder.create(byteArray);

            var cr = codeRangeNode.execute(string.tstring, encoding.tencoding);
            final boolean modified = StringSupport
                    .swapCaseMultiByteComplex(encoding.jcoding, cr, builder, caseMappingOptions, this);

            if (modifiedProfile.profile(this, modified)) {
                string.setTString(fromByteArrayNode.execute(builder.getBytes(), encoding.tencoding, false));
                return string;
            } else {
                return nil;
            }
        }
    }

    @Primitive(name = "string_to_symbol")
    @ImportStatic({ StringGuards.class, StringOperations.class })
    public abstract static class ToSymNode extends PrimitiveArrayArgumentsNode {

        @Child GetByteCodeRangeNode codeRangeNode = GetByteCodeRangeNode.create();

        @Specialization(
                guards = {
                        "!isBrokenCodeRange(tstring, encoding, codeRangeNode)",
                        "equalNode.execute(node, tstring, encoding, cachedTString, cachedEncoding)",
                        "preserveSymbol == cachedPreserveSymbol" },
                limit = "getDefaultCacheLimit()")
        static RubySymbol toSymCached(Object string, boolean preserveSymbol,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached("asTruffleStringUncached(string)") TruffleString cachedTString,
                @Cached("strings.getEncoding(node, string)") RubyEncoding cachedEncoding,
                @Cached("preserveSymbol") boolean cachedPreserveSymbol,
                @Cached("getSymbol(cachedTString, cachedEncoding, cachedPreserveSymbol)") RubySymbol cachedSymbol,
                @Cached StringHelperNodes.EqualSameEncodingNode equalNode,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            return cachedSymbol;
        }

        @Specialization(guards = "!isBrokenCodeRange(tstring, encoding, codeRangeNode)", replaces = "toSymCached",
                limit = "1")
        static RubySymbol toSym(Object string, boolean preserveSymbol,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            return getSymbol(node, strings.getTString(node, string), strings.getEncoding(node, string), preserveSymbol);
        }

        @Specialization(guards = "isBrokenCodeRange(tstring, encoding, codeRangeNode)", limit = "1")
        static RubySymbol toSymBroken(Object string, boolean preserveSymbol,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            throw new RaiseException(getContext(node), coreExceptions(node).encodingError(string, encoding, node));
        }

    }

    @Primitive(name = "string_unpack")
    @ReportPolymorphism // inline cache, CallTarget cache
    @ImportStatic(StringOperations.class)
    public abstract static class UnpackPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "equalNode.execute(libFormat, format, cachedFormat, cachedEncoding)", limit = "1")
        RubyArray unpackCached(Object string, Object format, Object offsetObject,
                @Cached @Shared InlinedBranchProfile exceptionProfile,
                @Cached @Shared InlinedBranchProfile negativeOffsetProfile,
                @Cached @Shared InlinedBranchProfile tooLargeOffsetProfile,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libFormat,
                @Cached("asTruffleStringUncached(format)") TruffleString cachedFormat,
                @Cached("libFormat.getEncoding($node, format)") RubyEncoding cachedEncoding,
                @Cached("create(compileFormat($node, getJavaString(format)))") DirectCallNode callUnpackNode,
                @Cached StringHelperNodes.EqualNode equalNode,
                @Cached @Shared StringHelperNodes.StringGetAssociatedNode stringGetAssociatedNode,
                @Cached @Shared TruffleString.GetInternalByteArrayNode byteArrayNode) {
            var byteArray = byteArrayNode.execute(libString.getTString(this, string),
                    libString.getTEncoding(this, string));

            final ArrayResult result;
            final int offset = (offsetObject == NotProvided.INSTANCE) ? 0 : (int) offsetObject;

            if (offset < 0) {
                negativeOffsetProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().argumentError(
                                "offset can't be negative", this, null));
            }

            if (offset > byteArray.getLength()) {
                tooLargeOffsetProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().argumentError(
                                "offset outside of string", this, null));
            }

            try {
                result = (ArrayResult) callUnpackNode.call(
                        new Object[]{
                                byteArray.getArray(),
                                byteArray.getEnd(),
                                byteArray.getOffset() + offset,
                                stringGetAssociatedNode.execute(this, string) }); // TODO impl associated for ImmutableRubyString
            } catch (FormatException e) {
                exceptionProfile.enter(this);
                throw FormatExceptionTranslator.translate(getContext(), this, e);
            }

            return finishUnpack(this, result);
        }

        @Specialization(guards = "libFormat.isRubyString(this, format)", replaces = "unpackCached")
        static RubyArray unpackUncached(Object string, Object format, Object offsetObject,
                @Cached @Shared InlinedBranchProfile exceptionProfile,
                @Cached @Shared InlinedBranchProfile negativeOffsetProfile,
                @Cached @Shared InlinedBranchProfile tooLargeOffsetProfile,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libFormat,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached IndirectCallNode callUnpackNode,
                @Cached @Shared StringHelperNodes.StringGetAssociatedNode stringGetAssociatedNode,
                @Cached @Shared TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Bind Node node) {
            var byteArray = byteArrayNode.execute(libString.getTString(node, string),
                    libString.getTEncoding(node, string));

            final ArrayResult result;
            final int offset = (offsetObject == NotProvided.INSTANCE) ? 0 : (int) offsetObject;

            if (offset < 0) {
                negativeOffsetProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        getContext(node).getCoreExceptions().argumentError(
                                "offset can't be negative", node, null));
            }

            if (offset > byteArray.getLength()) {
                tooLargeOffsetProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        getContext(node).getCoreExceptions().argumentError(
                                "offset outside of string", node, null));
            }

            try {
                result = (ArrayResult) callUnpackNode.call(
                        compileFormat(node, toJavaStringNode.execute(node, format)),
                        new Object[]{
                                byteArray.getArray(),
                                byteArray.getEnd(),
                                byteArray.getOffset() + offset,
                                stringGetAssociatedNode.execute(node, string) });
            } catch (FormatException e) {
                exceptionProfile.enter(node);
                throw FormatExceptionTranslator.translate(getContext(node), node, e);
            }

            return finishUnpack(node, result);
        }

        private static RubyArray finishUnpack(Node node, ArrayResult result) {
            return createArray(node, result.getOutput(), result.getOutputLength());
        }

        @TruffleBoundary
        protected static RootCallTarget compileFormat(Node node, String format) {
            try {
                return new UnpackCompiler(getLanguage(node), node).compile(format);
            } catch (DeferredRaiseException dre) {
                throw dre.getException(getContext(node));
            }
        }

        protected int getCacheLimit() {
            return getLanguage().options.UNPACK_CACHE;
        }

    }

    @Primitive(name = "string_upcase!", raiseIfNotMutable = 0, lowerFixnum = 1)
    @ImportStatic({ StringGuards.class, Config.class })
    public abstract static class StringUpcaseBangPrimitiveNode extends PrimitiveArrayArgumentsNode {

        private final ConditionProfile dummyEncodingProfile = ConditionProfile.create();

        @Specialization(
                guards = "!isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object upcaseAsciiCodePoints(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("createLowerToUpper()") StringHelperNodes.InvertAsciiCaseNode invertAsciiCaseNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            return invertAsciiCaseNode.executeInvert(string);
        }

        @Specialization(
                guards = "isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object upcaseMultiByteComplex(RubyString string, int caseMappingOptions,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached GetByteCodeRangeNode codeRangeNode,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Cached InlinedConditionProfile modifiedProfile,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var tencoding = encoding.tencoding;

            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);

            // TODO (nirvdrum 24-Jun-22): Make the byte array builder copy-on-write so we don't eagerly clone the source byte array.
            var builder = ByteArrayBuilder.create(byteArray);

            final boolean modified = StringSupport
                    .upcaseMultiByteComplex(encoding.jcoding,
                            codeRangeNode.execute(string.tstring, tencoding),
                            builder, caseMappingOptions, this);
            if (modifiedProfile.profile(this, modified)) {
                string.setTString(fromByteArrayNode.execute(builder.getBytes(), tencoding, false));
                return string;
            } else {
                return nil;
            }
        }

    }

    @Primitive(name = "string_capitalize!", raiseIfNotMutable = 0, lowerFixnum = 1)
    @ImportStatic({ StringGuards.class, Config.class })
    public abstract static class StringCapitalizeBangPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private GetByteCodeRangeNode codeRangeNode;
        @Child private TruffleString.CopyToByteArrayNode copyToByteArrayNode;
        @Child private TruffleString.FromByteArrayNode fromByteArrayNode;
        private final ConditionProfile dummyEncodingProfile = ConditionProfile.create();
        private final ConditionProfile emptyStringProfile = ConditionProfile.create();

        @Specialization(
                guards = "!isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object capitalizeAsciiCodePoints(RubyString string, int caseMappingOptions,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached("createUpperToLower()") StringHelperNodes.InvertAsciiCaseHelperNode invertAsciiCaseNode,
                @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
                @Cached TruffleStringIterator.NextNode nextNode,
                @Cached @Exclusive InlinedConditionProfile firstCharIsLowerProfile,
                @Cached @Exclusive InlinedConditionProfile modifiedProfile,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            var tencoding = encoding.tencoding;

            if (emptyStringProfile.profile(tstring.isEmpty())) {
                return nil;
            }

            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            byte[] bytes = null;

            var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);
            int firstCodePoint = nextNode.execute(iterator, tencoding);
            if (firstCharIsLowerProfile.profile(this, StringSupport.isAsciiLowercase(firstCodePoint))) {
                bytes = copyByteArray(tstring, tencoding);
                bytes[0] ^= 0x20;
            }

            bytes = invertAsciiCaseNode.executeInvert(string, iterator, bytes);

            if (modifiedProfile.profile(this, bytes != null)) {
                string.setTString(makeTString(bytes, tencoding));
                return string;
            } else {
                return nil;
            }
        }

        @Specialization(
                guards = "isComplexCaseMapping(this, tstring, encoding, caseMappingOptions, singleByteOptimizableNode)")
        Object capitalizeMultiByteComplex(RubyString string, int caseMappingOptions,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Exclusive InlinedConditionProfile modifiedProfile,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode,
                @Bind("string.tstring") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {

            if (dummyEncodingProfile.profile(encoding.isDummy)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().encodingCompatibilityErrorIncompatibleWithOperation(encoding, this));
            }

            if (emptyStringProfile.profile(tstring.isEmpty())) {
                return nil;
            }

            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);

            // TODO (nirvdrum 26-May-22): Make the byte array builder copy-on-write so we don't eagerly clone the source byte array.
            var builder = ByteArrayBuilder.create(byteArray);

            var cr = getCodeRange(tstring, encoding.tencoding);
            final boolean modified = StringSupport
                    .capitalizeMultiByteComplex(encoding.jcoding, cr, builder, caseMappingOptions, this);

            if (modifiedProfile.profile(this, modified)) {
                string.setTString(makeTString(builder.getUnsafeBytes(), encoding.tencoding));
                return string;
            } else {
                return nil;
            }
        }

        private byte[] copyByteArray(AbstractTruffleString string, TruffleString.Encoding encoding) {
            if (copyToByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                copyToByteArrayNode = insert(TruffleString.CopyToByteArrayNode.create());
            }

            return copyToByteArrayNode.execute(string, encoding);
        }

        private TruffleString.CodeRange getCodeRange(AbstractTruffleString string, TruffleString.Encoding encoding) {
            if (codeRangeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codeRangeNode = insert(GetByteCodeRangeNode.create());
            }

            return codeRangeNode.execute(string, encoding);
        }

        private AbstractTruffleString makeTString(byte[] bytes, TruffleString.Encoding encoding) {
            if (fromByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromByteArrayNode = insert(TruffleString.FromByteArrayNode.create());
            }

            return fromByteArrayNode.execute(bytes, 0, bytes.length, encoding, false);
        }
    }

    @Primitive(name = "character_printable?", lowerFixnum = 0)
    public abstract static class CharacterPrintablePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isCharacterPrintable(int codepoint, RubyEncoding encoding,
                @Cached InlinedConditionProfile asciiPrintableProfile) {
            assert codepoint >= 0;

            if (asciiPrintableProfile
                    .profile(this, encoding.isAsciiCompatible && StringSupport.isAscii(codepoint))) {
                return StringSupport.isAsciiPrintable(codepoint);
            } else {
                return isMBCPrintable(encoding.jcoding, codepoint);
            }
        }

        @TruffleBoundary
        protected boolean isMBCPrintable(Encoding encoding, int codePoint) {
            return encoding.isPrint(codePoint);
        }
    }

    @Primitive(name = "string_append")
    public abstract static class StringAppendPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @NeverDefault
        public static StringAppendPrimitiveNode create() {
            return StringPrimitiveNodesFactory.StringAppendPrimitiveNodeFactory.create(null);
        }

        public abstract RubyString executeStringAppend(RubyString string, Object other);

        @Specialization
        RubyString stringAppend(RubyString string, Object other,
                @Cached StringHelperNodes.StringAppendNode stringAppendNode) {
            final RubyString result = stringAppendNode.executeStringAppend(this, string, other);
            string.setTString(result.tstring, result.getEncodingUnprofiled());
            return string;
        }

    }

    @Primitive(name = "string_awk_split", lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class StringAwkSplitPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child GetByteCodeRangeNode codeRangeNode = GetByteCodeRangeNode.create();

        private static final int SUBSTRING_CREATED = -1;
        private static final int DEFAULT_SPLIT_VALUES_SIZE = 10;

        // Inlined profiles/nodes are @Exclusive to fix truffle-interpreted-performance warning

        @Specialization(guards = "is7Bit(tstring, encoding, codeRangeNode)", limit = "1")
        static Object stringAwkSplitAsciiOnly(Object string, int limit, Object block,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached @Exclusive InlinedConditionProfile executeBlockProfile,
                @Cached @Exclusive InlinedConditionProfile growArrayProfile,
                @Cached @Exclusive InlinedConditionProfile trailingSubstringProfile,
                @Cached @Exclusive InlinedConditionProfile trailingEmptyStringProfile,
                @Cached TruffleString.MaterializeNode materializeNode,
                @Cached TruffleString.ReadByteNode readByteNode,
                @Cached @Exclusive CallBlockNode yieldNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringNode,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            int retSize = limit > 0 && limit < DEFAULT_SPLIT_VALUES_SIZE ? limit : DEFAULT_SPLIT_VALUES_SIZE;
            Object[] ret = new Object[retSize];
            int storeIndex = 0;

            int byteLength = tstring.byteLength(encoding.tencoding);
            materializeNode.execute(tstring, encoding.tencoding);

            int substringStart = 0;
            boolean findingSubstringEnd = false;

            int i = 0;
            try {
                for (; loopProfile.inject(i < byteLength); i++) {
                    if (StringSupport.isAsciiSpace(readByteNode.execute(tstring, i, encoding.tencoding))) {
                        if (findingSubstringEnd) {
                            findingSubstringEnd = false;

                            final RubyString substring = createSubString(node, substringNode, tstring, encoding,
                                    substringStart, i - substringStart);
                            ret = addSubstring(
                                    node,
                                    yieldNode,
                                    ret,
                                    storeIndex++,
                                    substring,
                                    block,
                                    executeBlockProfile,
                                    growArrayProfile);
                            substringStart = SUBSTRING_CREATED;
                        }
                    } else {
                        if (!findingSubstringEnd) {
                            substringStart = i;
                            findingSubstringEnd = true;

                            if (storeIndex == limit - 1) {
                                break;
                            }
                        }
                    }

                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, i);
            }

            if (trailingSubstringProfile.profile(node, findingSubstringEnd)) {
                final RubyString substring = createSubString(node, substringNode, tstring, encoding, substringStart,
                        byteLength - substringStart);
                ret = addSubstring(node, yieldNode, ret, storeIndex++, substring, block, executeBlockProfile,
                        growArrayProfile);
            }

            if (trailingEmptyStringProfile.profile(node, (limit < 0 || storeIndex < limit) &&
                    StringSupport.isAsciiSpace(readByteNode.execute(tstring, byteLength - 1, encoding.tencoding)))) {
                final RubyString substring = createSubString(node, substringNode, tstring, encoding, byteLength - 1, 0);
                ret = addSubstring(node, yieldNode, ret, storeIndex++, substring, block, executeBlockProfile,
                        growArrayProfile);
            }

            if (block == nil) {
                return createArray(node, ret, storeIndex);
            } else {
                return string;
            }
        }

        @Specialization(guards = "isValid(tstring, encoding, codeRangeNode)", limit = "1")
        static Object stringAwkSplit(Object string, int limit, Object block,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached @Exclusive InlinedConditionProfile executeBlockProfile,
                @Cached @Exclusive InlinedConditionProfile growArrayProfile,
                @Cached @Exclusive InlinedConditionProfile trailingSubstringProfile,
                @Cached CreateCodePointIteratorNode createCodePointIteratorNode,
                @Cached TruffleStringIterator.NextNode nextNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringNode,
                @Cached @Exclusive InlinedLoopConditionProfile loopProfile,
                @Cached @Exclusive CallBlockNode yieldNode,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            int retSize = limit > 0 && limit < DEFAULT_SPLIT_VALUES_SIZE ? limit : DEFAULT_SPLIT_VALUES_SIZE;
            Object[] ret = new Object[retSize];
            int storeIndex = 0;

            final boolean limitPositive = limit > 0;
            int i = limit > 0 ? 1 : 0;

            var tencoding = encoding.tencoding;
            final int len = tstring.byteLength(tencoding);

            var iterator = createCodePointIteratorNode.execute(tstring, tencoding, ErrorHandling.RETURN_NEGATIVE);

            boolean skip = true;
            int e = 0, b = 0, iterations = 0;
            try {
                while (loopProfile.inject(node, iterator.hasNext())) {
                    int c = nextNode.execute(iterator, tencoding);
                    int p = iterator.getByteIndex();
                    iterations++;

                    if (skip) {
                        if (StringSupport.isAsciiSpace(c)) {
                            b = p;
                        } else {
                            e = p;
                            skip = false;
                            if (limitPositive && limit <= i) {
                                break;
                            }
                        }
                    } else {
                        if (StringSupport.isAsciiSpace(c)) {
                            var substring = createSubString(node, substringNode, tstring, encoding, b, e - b);
                            ret = addSubstring(
                                    node,
                                    yieldNode,
                                    ret,
                                    storeIndex++,
                                    substring,
                                    block,
                                    executeBlockProfile,
                                    growArrayProfile);
                            skip = true;
                            b = p;
                            if (limitPositive) {
                                i++;
                            }
                        } else {
                            e = p;
                        }
                    }
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, iterations);
            }

            if (trailingSubstringProfile.profile(node, len > 0 && (limitPositive || len > b || limit < 0))) {
                var substring = createSubString(node, substringNode, tstring, encoding, b, len - b);
                ret = addSubstring(node, yieldNode, ret, storeIndex++, substring, block, executeBlockProfile,
                        growArrayProfile);
            }

            if (block == nil) {
                return createArray(node, ret, storeIndex);
            } else {
                return string;
            }
        }

        @Specialization(guards = "isBrokenCodeRange(tstring, encoding, codeRangeNode)", limit = "1")
        static Object broken(Object string, int limit, Object block,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            throw new RaiseException(getContext(node),
                    coreExceptions(node).argumentErrorInvalidByteSequence(encoding, node));
        }

        private static Object[] addSubstring(Node node, CallBlockNode yieldNode, Object[] store, int index,
                RubyString substring, Object block, InlinedConditionProfile executeBlockProfile,
                InlinedConditionProfile growArrayProfile) {
            if (executeBlockProfile.profile(node, block != nil)) {
                yieldNode.yield(node, (RubyProc) block, substring);
            } else {
                if (growArrayProfile.profile(node, index < store.length)) {
                    store[index] = substring;
                } else {
                    store = ArrayUtils.grow(store, store.length * 2);
                    store[index] = substring;
                }
            }

            return store;
        }

    }

    @Primitive(name = "string_byte_substring", lowerFixnum = { 1, 2 })
    public abstract static class StringByteSubstringPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object stringByteSubstring(Object string, int index, NotProvided length,
                @Cached @Exclusive InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.SubstringByteIndexNode substringNode,
                @Cached @Shared StringHelperNodes.NormalizeIndexNode normalizeIndexNode) {
            var tString = libString.getTString(this, string);
            var encoding = libString.getEncoding(this, string);
            final int stringByteLength = tString.byteLength(encoding.tencoding);
            final int normalizedIndex = normalizeIndexNode.executeNormalize(this, index, stringByteLength);

            if (indexOutOfBoundsProfile.profile(this, normalizedIndex < 0 || normalizedIndex >= stringByteLength)) {
                return nil;
            }

            return createSubString(substringNode, tString, encoding, normalizedIndex, 1);
        }

        @Specialization
        Object stringByteSubstring(Object string, int index, int length,
                @Cached @Exclusive InlinedConditionProfile negativeLengthProfile,
                @Cached @Exclusive InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached @Exclusive InlinedConditionProfile lengthTooLongProfile,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.SubstringByteIndexNode substringNode,
                @Cached @Shared StringHelperNodes.NormalizeIndexNode normalizeIndexNode) {
            if (negativeLengthProfile.profile(this, length < 0)) {
                return nil;
            }

            var tString = libString.getTString(this, string);
            var encoding = libString.getEncoding(this, string);
            final int stringByteLength = tString.byteLength(encoding.tencoding);
            final int normalizedIndex = normalizeIndexNode.executeNormalize(this, index, stringByteLength);

            if (indexOutOfBoundsProfile.profile(this, normalizedIndex < 0 || normalizedIndex > stringByteLength)) {
                return nil;
            }

            if (lengthTooLongProfile.profile(this, normalizedIndex + length > stringByteLength)) {
                length = stringByteLength - normalizedIndex;
            }

            return createSubString(substringNode, tString, encoding, normalizedIndex, length);
        }

        @Fallback
        Object stringByteSubstring(Object string, Object range, Object length) {
            return FAILURE;
        }

    }

    /** Like {@code string.byteslice(byteIndex)} but returns nil if the character is broken. */
    @Primitive(name = "string_chr_at", lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class StringChrAtPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = "indexOutOfBounds(strings.byteLength(node, string), byteIndex)", limit = "1")
        static Object stringChrAtOutOfBounds(Object string, int byteIndex,
                @Cached @Exclusive RubyStringLibrary strings,
                @Bind Node node) {
            return nil;
        }

        @Specialization(
                guards = {
                        "!indexOutOfBounds(tstring.byteLength(encoding.tencoding), byteIndex)",
                        "is7Bit(tstring, encoding, codeRangeNode)" },
                limit = "1")
        static Object stringChrAtSingleByte(Object string, int byteIndex,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached @Shared TruffleString.GetByteCodeRangeNode codeRangeNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringByteIndexNode,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            return createSubString(node, substringByteIndexNode, tstring, encoding, byteIndex, 1);
        }

        @Specialization(
                guards = {
                        "!indexOutOfBounds(tstring.byteLength(encoding.tencoding), byteIndex)",
                        "!is7Bit(tstring, encoding, codeRangeNode)" },
                limit = "1")
        static Object stringChrAt(Object string, int byteIndex,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary strings,
                @Cached @Shared TruffleString.GetByteCodeRangeNode codeRangeNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringByteIndexNode,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached InlinedConditionProfile brokenProfile,
                @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
                @Bind("strings.getEncoding(node, string)") RubyEncoding encoding) {
            final int clen = byteLengthOfCodePointNode.execute(tstring, byteIndex, encoding.tencoding,
                    ErrorHandling.RETURN_NEGATIVE);

            if (brokenProfile.profile(node, !StringSupport.MBCLEN_CHARFOUND_P(clen))) {
                return nil;
            }

            assert byteIndex + clen <= tstring.byteLength(encoding.tencoding);

            return createSubString(node, substringByteIndexNode, tstring, encoding, byteIndex, clen);
        }

        protected static boolean indexOutOfBounds(int byteLength, int byteIndex) {
            return byteIndex < 0 || byteIndex >= byteLength;
        }

    }

    @Primitive(name = "string_escape")
    public abstract static class StringEscapePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyString string_escape(Object string,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);
            final TruffleString escaped = rbStrEscape(tstring, encoding, byteArray);
            return createString(escaped, Encodings.US_ASCII);
        }

        // MRI: rb_str_escape
        @TruffleBoundary
        private static TruffleString rbStrEscape(AbstractTruffleString tstring, RubyEncoding encoding,
                InternalByteArray byteArray) {
            var tencoding = encoding.tencoding;

            TStringBuilder result = new TStringBuilder();
            boolean unicode_p = encoding.isUnicode;
            boolean asciicompat = encoding.isAsciiCompatible;
            var iterator = CreateCodePointIteratorNode.getUncached().execute(tstring, tencoding,
                    ErrorHandling.RETURN_NEGATIVE);

            while (iterator.hasNext()) {
                final int p = iterator.getByteIndex();
                int c = iterator.nextUncached(tencoding);

                if (c == -1) {
                    int n = iterator.getByteIndex() - p;
                    for (int i = 0; i < n; i++) {
                        result.append(StringUtils.formatASCIIBytes("\\x%02X", (long) (byteArray.get(p + i) & 0377)));
                    }
                    continue;
                }

                final int cc;
                switch (c) {
                    case '\n':
                        cc = 'n';
                        break;
                    case '\r':
                        cc = 'r';
                        break;
                    case '\t':
                        cc = 't';
                        break;
                    case '\f':
                        cc = 'f';
                        break;
                    case '\013':
                        cc = 'v';
                        break;
                    case '\010':
                        cc = 'b';
                        break;
                    case '\007':
                        cc = 'a';
                        break;
                    case 033:
                        cc = 'e';
                        break;
                    default:
                        cc = 0;
                        break;
                }

                if (cc != 0) {
                    result.append('\\');
                    result.append((byte) cc);
                } else if (asciicompat && Encoding.isAscii(c) && (c < 0x7F && c > 31 /* ISPRINT(c) */)) {
                    result.append(byteArray, p, p - iterator.getByteIndex());
                } else {
                    if (unicode_p && (c & 0xFFFFFFFFL) < 0x7F && Encoding.isAscii(c) &&
                            ASCIIEncoding.INSTANCE.isPrint(c)) {
                        result.append(StringUtils.formatASCIIBytes("%c", (char) (c & 0xFFFFFFFFL)));
                    } else {
                        result.append(StringUtils.formatASCIIBytes(escapedCharFormat(c, unicode_p), c & 0xFFFFFFFFL));
                    }

                }
            }

            result.setEncoding(Encodings.US_ASCII);
            return result.toTString(); // CodeRange.CR_7BIT
        }

        private static String escapedCharFormat(int c, boolean isUnicode) {
            String format;
            // c comparisons must be unsigned 32-bit
            if (isUnicode) {

                if ((c & 0xFFFFFFFFL) < 0x7F && Encoding.isAscii(c) && ASCIIEncoding.INSTANCE.isPrint(c)) {
                    throw new UnsupportedOperationException();
                } else if (c < 0x10000) {
                    format = "\\u%04X";
                } else {
                    format = "\\u{%X}";
                }
            } else {
                if ((c & 0xFFFFFFFFL) < 0x100) {
                    format = "\\x%02X";
                } else {
                    format = "\\x{%X}";
                }
            }
            return format;
        }

    }

    @Primitive(name = "string_find_character", lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class StringFindCharacterNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "offset < 0")
        Object stringFindCharacterNegativeOffset(Object string, int offset) {
            return nil;
        }

        @Specialization(guards = "offsetTooLarge(strings.byteLength(this, string), offset)")
        Object stringFindCharacterOffsetTooLarge(Object string, int offset,
                @Cached @Shared RubyStringLibrary strings) {
            return nil;
        }

        @Specialization(
                guards = {
                        "offset >= 0",
                        "!offsetTooLarge(strings.byteLength(this, string), offset)",
                        "isSingleByteOptimizable(this, strings.getTString(this, string), strings.getEncoding(this, string), singleByteOptimizableNode)" })
        Object stringFindCharacterSingleByte(Object string, int offset,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringNode) {
            // Taken from Rubinius's String::find_character.
            return createSubString(substringNode, strings, string, offset, 1);
        }

        @Specialization(
                guards = {
                        "offset >= 0",
                        "!offsetTooLarge(strings.byteLength(this, string), offset)",
                        "!isSingleByteOptimizable(this, strings.getTString(this, string), strings.getEncoding(this, string), singleByteOptimizableNode)" })
        Object stringFindCharacter(Object string, int offset,
                @Cached @Shared RubyStringLibrary strings,
                @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode substringNode) {
            // Taken from Rubinius's String::find_character.
            var tstring = strings.getTString(this, string);
            var tencoding = strings.getTEncoding(this, string);

            int clen = byteLengthOfCodePointNode.execute(tstring, offset, tencoding, ErrorHandling.BEST_EFFORT);
            return createSubString(substringNode, strings, string, offset, clen);
        }

        protected static boolean offsetTooLarge(int byteLength, int offset) {
            return offset >= byteLength;
        }

    }

    @Primitive(name = "string_from_codepoint", lowerFixnum = 0)
    public abstract static class StringFromCodepointPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isSimple(code, encoding)")
        RubyString stringFromCodepointSimple(int code, RubyEncoding encoding,
                @Cached InlinedConditionProfile isUTF8Profile,
                @Cached InlinedConditionProfile isUSAsciiProfile,
                @Cached InlinedConditionProfile isAscii8BitProfile,
                @Cached @Exclusive TruffleString.FromCodePointNode fromCodePointNode) {
            final TruffleString tstring;
            if (isUTF8Profile.profile(this, encoding == Encodings.UTF_8)) {
                tstring = TStringConstants.UTF8_SINGLE_BYTE[code];
            } else if (isUSAsciiProfile.profile(this, encoding == Encodings.US_ASCII)) {
                tstring = TStringConstants.US_ASCII_SINGLE_BYTE[code];
            } else if (isAscii8BitProfile.profile(this, encoding == Encodings.BINARY)) {
                tstring = TStringConstants.BINARY_SINGLE_BYTE[code];
            } else {
                tstring = fromCodePointNode.execute(code, encoding.tencoding, false);
                assert tstring != null;
            }

            return createString(tstring, encoding);
        }

        @Specialization(guards = "!isSimple(code, encoding)")
        RubyString stringFromCodepoint(int code, RubyEncoding encoding,
                @Cached @Shared TruffleString.FromCodePointNode fromCodePointNode,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            var tstring = fromCodePointNode.execute(code, encoding.tencoding, false);
            if (tstring == null) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().rangeError(code, this));
            }

            return createString(tstring, encoding);
        }

        @Specialization(guards = "isCodepoint(code)")
        RubyString stringFromLongCodepoint(long code, RubyEncoding encoding,
                @Cached @Shared TruffleString.FromCodePointNode fromCodePointNode,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            var tstring = fromCodePointNode.execute((int) code, encoding.tencoding, false);
            if (tstring == null) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().rangeError(code, this));
            }

            return createString(tstring, encoding);
        }

        @Specialization(guards = "!isCodepoint(code)")
        RubyString tooBig(long code, RubyEncoding encoding) {
            throw new RaiseException(getContext(), coreExceptions().rangeError(code, this));
        }

        protected boolean isCodepoint(long code) {
            // Fits in an unsigned int
            return code >= 0 && code < (1L << 32);
        }

        protected boolean isSimple(int codepoint, RubyEncoding encoding) {
            return (encoding.isAsciiCompatible && codepoint >= 0x00 && codepoint < 0x80) ||
                    (encoding == Encodings.BINARY && codepoint >= 0x00 && codepoint <= 0xFF);
        }
    }

    @Primitive(name = "string_to_f")
    public abstract static class StringToFPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object stringToF(Object string,
                @Cached RubyStringLibrary strings) {
            var tstring = strings.getTString(this, string);
            var encoding = strings.getEncoding(this, string);
            if (tstring.isEmpty()) {
                return nil;
            }

            final String javaString = StringOperations.getJavaString(string);
            if (javaString.startsWith("0x")) {
                try {
                    return Double.parseDouble(javaString);
                } catch (NumberFormatException e) {
                    // Try falling back to this implementation if the first fails, neither 100% complete
                    final Object result = ConvertBytes.bytesToInum(
                            getContext(),
                            this,
                            tstring,
                            encoding,
                            16,
                            true);
                    if (result instanceof Integer) {
                        return ((Integer) result).doubleValue();
                    } else if (result instanceof Long) {
                        return ((Long) result).doubleValue();
                    } else if (result instanceof Double) {
                        return result;
                    } else {
                        return nil;
                    }
                }
            }
            try {
                return new DoubleConverter().parse(tstring, encoding, true, true);
            } catch (NumberFormatException e) {
                return nil;
            }
        }

    }

    @Primitive(name = "find_string", lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public abstract static class StringIndexPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "patternTString.isEmpty()", limit = "1")
        static int stringIndexEmptyPattern(Object rubyString, Object rubyPattern, int byteOffset,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary libPattern,
                @Bind("libPattern.getTString(node, rubyPattern)") AbstractTruffleString patternTString) {
            assert byteOffset >= 0;
            return byteOffset;
        }

        @Specialization(guards = "!patternTString.isEmpty()", limit = "1")
        static Object findStringByteIndex(Object rubyString, Object rubyPattern, int byteOffset,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary libString,
                @Cached @Exclusive RubyStringLibrary libPattern,
                @Cached CheckStringEncodingNode checkEncodingNode,
                @Cached TruffleString.ByteIndexOfStringNode indexOfStringNode,
                @Cached InlinedConditionProfile offsetTooLargeProfile,
                @Cached InlinedConditionProfile notFoundProfile,
                @Bind("libPattern.getTString(node, rubyPattern)") AbstractTruffleString patternTString) {
            assert byteOffset >= 0;

            var string = libString.getTString(node, rubyString);
            var encoding = libString.getEncoding(node, rubyString);
            var patternEncoding = libPattern.getEncoding(node, rubyPattern);

            var compatibleEncoding = checkEncodingNode.execute(node, string, encoding, patternTString, patternEncoding);

            int stringByteLength = string.byteLength(encoding.tencoding);

            if (offsetTooLargeProfile.profile(node, byteOffset >= stringByteLength)) {
                return nil;
            }

            int patternByteIndex = indexOfStringNode.execute(string, patternTString, byteOffset, stringByteLength,
                    compatibleEncoding.tencoding);

            if (notFoundProfile.profile(node, patternByteIndex < 0)) {
                return nil;
            }

            return patternByteIndex;
        }

    }

    @Primitive(name = "byte_index_to_character_index", lowerFixnum = 1)
    public abstract static class StringByteCharacterIndexNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        int byteIndexToCodePointIndex(Object string, int byteIndex,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.ByteIndexToCodePointIndexNode byteIndexToCodePointIndexNode,
                @Bind("libString.getTString($node, string)") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding) {
            return byteIndexToCodePointIndexNode.execute(tstring, 0, byteIndex, encoding.tencoding);
        }
    }

    // Named 'string_byte_index' in Rubinius.
    @Primitive(name = "character_index_to_byte_index", lowerFixnum = 1)
    public abstract static class StringByteIndexFromCharIndexNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object byteIndexFromCharIndex(Object string, int characterIndex,
                @Cached TruffleString.CodePointIndexToByteIndexNode codePointIndexToByteIndexNode,
                @Cached RubyStringLibrary libString) {
            return codePointIndexToByteIndexNode.execute(libString.getTString(this, string), 0, characterIndex,
                    libString.getTEncoding(this, string));
        }
    }

    /** Search pattern in string starting after offset characters, and return a character index or nil */
    @Primitive(name = "string_character_index", lowerFixnum = 3)
    public abstract static class StringCharacterIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "singleByteOptimizableNode.execute(this, string, stringEncoding)")
        Object singleByteOptimizable(
                Object rubyString, Object rubyPattern, RubyEncoding compatibleEncoding, int codePointOffset,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libPattern,
                @Bind("libString.getTString($node, rubyString)") AbstractTruffleString string,
                @Bind("libString.getEncoding($node, rubyString)") RubyEncoding stringEncoding,
                @Bind("libPattern.getTString($node, rubyPattern)") AbstractTruffleString pattern,
                @Bind("libPattern.getEncoding($node, rubyPattern)") RubyEncoding patternEncoding,
                @Cached TruffleString.ByteIndexOfStringNode byteIndexOfStringNode,
                @Cached @Shared InlinedConditionProfile foundProfile) {

            assert codePointOffset >= 0;

            // When single-byte optimizable, the byte length and the codepoint length are the same.
            int stringByteLength = string.byteLength(stringEncoding.tencoding);

            assert codePointOffset + pattern.byteLength(
                    patternEncoding.tencoding) <= stringByteLength : "already checked in the caller, String#index";

            int found = byteIndexOfStringNode.execute(string, pattern, codePointOffset, stringByteLength,
                    compatibleEncoding.tencoding);

            if (foundProfile.profile(this, found >= 0)) {
                return found;
            }

            return nil;
        }

        @Specialization(guards = "!singleByteOptimizableNode.execute(this, string, stringEncoding)")
        Object multiByte(Object rubyString, Object rubyPattern, RubyEncoding compatibleEncoding, int codePointOffset,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared RubyStringLibrary libPattern,
                @Bind("libString.getTString($node, rubyString)") AbstractTruffleString string,
                @Bind("libString.getEncoding($node, rubyString)") RubyEncoding stringEncoding,
                @Bind("libPattern.getTString($node, rubyPattern)") AbstractTruffleString pattern,
                @Bind("libPattern.getEncoding($node, rubyPattern)") RubyEncoding patternEncoding,
                @Cached CodePointLengthNode codePointLengthNode,
                @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                @Cached @Shared InlinedConditionProfile foundProfile) {

            assert codePointOffset >= 0;
            assert codePointOffset + pattern.codePointLengthUncached(patternEncoding.tencoding) <= string
                    .codePointLengthUncached(stringEncoding.tencoding) : "already checked in the caller, String#index";

            int stringCodePointLength = codePointLengthNode.execute(string, stringEncoding.tencoding);
            int found = indexOfStringNode.execute(string, pattern, codePointOffset, stringCodePointLength,
                    compatibleEncoding.tencoding);

            if (foundProfile.profile(this, found >= 0)) {
                return found;
            }

            return nil;
        }
    }

    /** Search pattern in string starting after offset bytes, and return a byte index or nil */
    @Primitive(name = "string_byte_index", lowerFixnum = 3)
    public abstract static class StringByteIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object stringByteIndex(Object rubyString, Object rubyPattern, RubyEncoding compatibleEncoding, int byteOffset,
                @Cached RubyStringLibrary libString,
                @Cached RubyStringLibrary libPattern,
                @Cached TruffleString.ByteIndexOfStringNode byteIndexOfStringNode,
                @Cached InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached InlinedConditionProfile foundProfile) {
            assert byteOffset >= 0;

            var string = libString.getTString(this, rubyString);
            int stringByteLength = libString.byteLength(this, rubyString);

            var pattern = libPattern.getTString(this, rubyPattern);
            int patternByteLength = libPattern.byteLength(this, rubyPattern);

            if (indexOutOfBoundsProfile.profile(this, byteOffset + patternByteLength > stringByteLength)) {
                return nil;
            }

            int found = byteIndexOfStringNode.execute(string, pattern, byteOffset, stringByteLength,
                    compatibleEncoding.tencoding);
            if (foundProfile.profile(this, found >= 0)) {
                return found;
            }

            return nil;
        }
    }

    /** Search pattern in string starting at offset bytes backwards, and return a byte index or nil */
    @Primitive(name = "string_byte_reverse_index", lowerFixnum = 3)
    public abstract static class StringByteReverseIndexNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object stringByteIndex(Object rubyString, Object rubyPattern, RubyEncoding compatibleEncoding, int byteOffset,
                @Cached RubyStringLibrary libString,
                @Cached RubyStringLibrary libPattern,
                @Cached TruffleString.LastByteIndexOfStringNode lastByteIndexOfStringNode,
                @Cached InlinedConditionProfile indexOutOfBoundsProfile,
                @Cached InlinedConditionProfile foundProfile) {
            assert byteOffset >= 0;

            var string = libString.getTString(this, rubyString);
            int stringByteLength = libString.byteLength(this, rubyString);

            var pattern = libPattern.getTString(this, rubyPattern);
            int patternByteLength = libPattern.byteLength(this, rubyPattern);

            if (indexOutOfBoundsProfile.profile(this, patternByteLength > stringByteLength)) {
                return nil;
            }

            int found = lastByteIndexOfStringNode.execute(string, pattern, byteOffset, 0,
                    compatibleEncoding.tencoding);
            if (foundProfile.profile(this, found >= 0)) {
                return found;
            }

            return nil;
        }
    }

    // Port of Rubinius's String::previous_byte_index.
    //
    // This method takes a byte index, finds the corresponding character the byte index belongs to, and then returns
    // the byte index marking the start of the previous character in the string.
    @Primitive(name = "string_previous_byte_index", lowerFixnum = 1)
    @ImportStatic(StringGuards.class)
    public abstract static class StringPreviousByteIndexNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "index < 0")
        Object negativeIndex(Object string, int index) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative index given", this));
        }

        @Specialization(guards = "index == 0")
        Object zeroIndex(Object string, int index) {
            return nil;
        }

        @Specialization(guards = {
                "index > 0",
                "isSingleByteOptimizable(this, strings.getTString(this, string), strings.getEncoding(this, string), singleByteOptimizableNode)" })
        int singleByteOptimizable(Object string, int index,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode) {
            return index - 1;
        }

        @Specialization(guards = {
                "index > 0",
                "!isSingleByteOptimizable(this, strings.getTString(this, string), strings.getEncoding(this, string), singleByteOptimizableNode)",
                "isFixedWidthEncoding(strings.getEncoding(this, string))" })
        int fixedWidthEncoding(Object string, int index,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached InlinedConditionProfile firstCharacterProfile) {
            final Encoding encoding = strings.getEncoding(this, string).jcoding;

            // TODO (nirvdrum 11-Apr-16) Determine whether we need to be bug-for-bug compatible with Rubinius.
            // Implement a bug in Rubinius. We already special-case the index == 0 by returning nil. For all indices
            // corresponding to a given character, we treat them uniformly. However, for the first character, we only
            // return nil if the index is 0. If any other index into the first character is encountered, we return 0.
            // It seems unlikely this will ever be encountered in practice, but it's here for completeness.
            if (firstCharacterProfile.profile(this, index < encoding.maxLength())) {
                return 0;
            }

            return (index / encoding.maxLength() - 1) * encoding.maxLength();
        }

        @Specialization(guards = {
                "index > 0",
                "!isSingleByteOptimizable(this, strings.getTString(this, string), strings.getEncoding(this, string), singleByteOptimizableNode)",
                "!isFixedWidthEncoding(strings.getEncoding(this, string))" })
        @TruffleBoundary
        Object other(Object string, int index,
                @Cached @Shared RubyStringLibrary strings,
                @Cached @Shared SingleByteOptimizableNode singleByteOptimizableNode,
                @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
            var encoding = strings.getEncoding(this, string);
            var byteArray = byteArrayNode.execute(strings.getTString(this, string), encoding.tencoding);
            final int p = byteArray.getOffset();
            final int end = byteArray.getEnd();

            final int b = encoding.jcoding.prevCharHead(byteArray.getArray(), p, p + index, end);

            if (b == -1) {
                return nil;
            }

            return b - p;
        }

    }

    @Primitive(name = "find_string_reverse", lowerFixnum = 2)
    @ImportStatic(StringGuards.class)
    public abstract static class StringRindexPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object stringRindex(Object rubyString, Object rubyPattern, int byteOffset,
                @Cached RubyStringLibrary libPattern,
                @Cached RubyStringLibrary libString,
                @Cached CheckStringEncodingNode checkEncodingNode,
                @Cached TruffleString.LastByteIndexOfStringNode lastByteIndexOfStringNode,
                @Cached InlinedBranchProfile startOutOfBoundsProfile,
                @Cached InlinedBranchProfile startTooCloseToEndProfile,
                @Cached InlinedBranchProfile noMatchProfile) {
            assert byteOffset >= 0;

            var string = libString.getTString(this, rubyString);
            var stringEncoding = libString.getEncoding(this, rubyString);
            int stringByteLength = string.byteLength(stringEncoding.tencoding);

            var pattern = libPattern.getTString(this, rubyPattern);
            var patternEncoding = libPattern.getEncoding(this, rubyPattern);
            int patternByteLength = pattern.byteLength(patternEncoding.tencoding);

            // Throw an exception if the encodings are not compatible.
            var compatibleEncoding = checkEncodingNode.execute(this, string, stringEncoding, pattern, patternEncoding);

            int normalizedStart = byteOffset;

            if (normalizedStart >= stringByteLength) {
                startOutOfBoundsProfile.enter(this);
                normalizedStart = stringByteLength - 1;
            }

            if (stringByteLength - normalizedStart < patternByteLength) {
                startTooCloseToEndProfile.enter(this);
                normalizedStart = stringByteLength - patternByteLength;
            }

            int result = lastByteIndexOfStringNode.execute(string, pattern, normalizedStart + patternByteLength, 0,
                    compatibleEncoding.tencoding);

            if (result < 0) {
                noMatchProfile.enter(this);
                return nil;
            }

            return result;
        }
    }

    @Primitive(name = "string_splice", lowerFixnum = { 2, 3 })
    @ImportStatic(StringGuards.class)
    public abstract static class StringSplicePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "spliceByteIndex == 0")
        Object splicePrepend(
                RubyString string, Object other, int spliceByteIndex, int byteCountToReplace, RubyEncoding rubyEncoding,
                @Cached @Exclusive RubyStringLibrary libString,
                @Cached @Exclusive RubyStringLibrary libOther,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode prependSubstringNode,
                @Cached @Shared TruffleString.ConcatNode concatNode) {
            var original = string.tstring;
            var originalTEncoding = libString.getTEncoding(this, string);
            var left = libOther.getTString(this, other);
            var right = prependSubstringNode.execute(original, byteCountToReplace,
                    original.byteLength(originalTEncoding) - byteCountToReplace, originalTEncoding, true);

            var prependResult = concatNode.execute(left, right, rubyEncoding.tencoding, true);
            string.setTString(prependResult, rubyEncoding);

            return string;
        }

        @Specialization(guards = "spliceByteIndex == byteLength", limit = "1")
        static Object spliceAppend(
                RubyString string, Object other, int spliceByteIndex, int byteCountToReplace, RubyEncoding rubyEncoding,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary libString,
                @Cached @Exclusive RubyStringLibrary libOther,
                @Cached @Shared TruffleString.ConcatNode concatNode,
                @Bind("libString.byteLength(node, string)") int byteLength) {
            var left = string.tstring;
            var right = libOther.getTString(node, other);

            var concatResult = concatNode.execute(left, right, rubyEncoding.tencoding, true);
            string.setTString(concatResult, rubyEncoding);

            return string;
        }

        @Specialization(guards = { "spliceByteIndex != 0", "spliceByteIndex != byteLength" }, limit = "1")
        static RubyString splice(
                RubyString string, Object other, int spliceByteIndex, int byteCountToReplace, RubyEncoding rubyEncoding,
                @Cached @Exclusive RubyStringLibrary libString,
                @Cached @Exclusive RubyStringLibrary libOther,
                @Cached InlinedConditionProfile insertStringIsEmptyProfile,
                @Cached InlinedConditionProfile splitRightIsEmptyProfile,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode leftSubstringNode,
                @Cached @Exclusive TruffleString.SubstringByteIndexNode rightSubstringNode,
                @Cached @Shared TruffleString.ConcatNode concatNode,
                @Cached TruffleString.ForceEncodingNode forceEncodingNode,
                @Bind("libString.byteLength($node, string)") int byteLength,
                @Bind Node node) {
            var sourceTEncoding = libString.getTEncoding(node, string);
            var resultTEncoding = rubyEncoding.tencoding;
            var source = string.tstring;
            var insert = libOther.getTString(node, other);
            final int rightSideStartingIndex = spliceByteIndex + byteCountToReplace;

            var splitLeft = leftSubstringNode.execute(source, 0, spliceByteIndex, sourceTEncoding, true);
            var splitRight = rightSubstringNode.execute(source, rightSideStartingIndex,
                    source.byteLength(sourceTEncoding) - rightSideStartingIndex, sourceTEncoding, true);

            final TruffleString joinedLeft; // always in resultTEncoding
            if (insertStringIsEmptyProfile.profile(node, insert.isEmpty())) {
                joinedLeft = forceEncodingNode.execute(splitLeft, sourceTEncoding, resultTEncoding);
            } else {
                joinedLeft = concatNode.execute(splitLeft, insert, resultTEncoding, true);
            }

            final TruffleString joinedRight; // always in resultTEncoding
            if (splitRightIsEmptyProfile.profile(node, splitRight.isEmpty())) {
                joinedRight = joinedLeft;
            } else {
                joinedRight = concatNode.execute(joinedLeft, splitRight, resultTEncoding, true);
            }

            string.setTString(joinedRight, rubyEncoding);
            return string;
        }
    }

    @Primitive(name = "string_to_inum", lowerFixnum = 1)
    public abstract static class StringToInumPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "base == 10")
        Object base10(Object string, int base, boolean strict, boolean raiseOnError,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.ParseLongNode parseLongNode,
                @Cached @Shared InlinedBranchProfile notLazyLongProfile,
                @Cached @Shared InlinedBranchProfile exceptionProfile) {
            var tstring = libString.getTString(this, string);
            try {
                return parseLongNode.execute(tstring, 10);
            } catch (TruffleString.NumberFormatException e) {
                notLazyLongProfile.enter(this);
                var encoding = libString.getEncoding(this, string);
                return bytesToInum(tstring, encoding, base, strict, raiseOnError, exceptionProfile);
            }
        }

        @Specialization(guards = "base == 0")
        Object base0(Object string, int base, boolean strict, boolean raiseOnError,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared TruffleString.ParseLongNode parseLongNode,
                @Cached TruffleString.CodePointAtByteIndexNode codePointNode,
                @Cached InlinedConditionProfile notEmptyProfile,
                @Cached @Shared InlinedBranchProfile notLazyLongProfile,
                @Cached @Shared InlinedBranchProfile exceptionProfile) {
            var tstring = libString.getTString(this, string);
            var enc = libString.getEncoding(this, string);
            var tenc = enc.tencoding;
            var len = tstring.byteLength(tenc);

            if (notEmptyProfile.profile(this, enc.isAsciiCompatible && len >= 1)) {
                int first = codePointNode.execute(tstring, 0, tenc, ErrorHandling.RETURN_NEGATIVE);
                int second;
                if ((first >= '1' && first <= '9') || (len >= 2 && (first == '-' || first == '+') &&
                        (second = codePointNode.execute(tstring, 1, tenc, ErrorHandling.RETURN_NEGATIVE)) >= '1' &&
                        second <= '9')) {
                    try {
                        return parseLongNode.execute(tstring, 10);
                    } catch (TruffleString.NumberFormatException e) {
                        notLazyLongProfile.enter(this);
                    }
                }
            }

            var encoding = libString.getEncoding(this, string);
            return bytesToInum(tstring, encoding, base, strict, raiseOnError, exceptionProfile);
        }

        @Specialization(guards = { "base != 10", "base != 0" })
        Object otherBase(Object string, int base, boolean strict, boolean raiseOnError,
                @Cached @Shared RubyStringLibrary libString,
                @Cached @Shared InlinedBranchProfile exceptionProfile) {
            var tstring = libString.getTString(this, string);
            var encoding = libString.getEncoding(this, string);
            return bytesToInum(tstring, encoding, base, strict, raiseOnError, exceptionProfile);
        }

        private Object bytesToInum(AbstractTruffleString tstring, RubyEncoding encoding, int base,
                boolean strict, boolean raiseOnError, InlinedBranchProfile exceptionProfile) {
            try {
                return ConvertBytes.bytesToInum(
                        getContext(),
                        this,
                        tstring,
                        encoding,
                        base,
                        strict);
            } catch (RaiseException e) {
                exceptionProfile.enter(this);
                if (!raiseOnError) {
                    return nil;
                }
                throw e;
            }
        }
    }

    /** The semantics of this primitive are such that the original string's byte[] should be extended without
     * negotiating the encoding. */
    @Primitive(name = "string_byte_append")
    public abstract static class StringByteAppendPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization(guards = "libOther.isRubyString(node, other)", limit = "1")
        static RubyString stringByteAppend(RubyString string, Object other,
                @Bind Node node,
                @Cached RubyStringLibrary libString,
                @Cached RubyStringLibrary libOther,
                @Cached TruffleString.ConcatNode concatNode,
                @Cached TruffleString.ForceEncodingNode forceEncodingNode) {

            var leftEncoding = libString.getEncoding(node, string);
            var left = string.tstring;
            var right = forceEncodingNode.execute(libOther.getTString(node, other), libOther.getTEncoding(node, other),
                    leftEncoding.tencoding);
            string.setTString(concatNode.execute(left, right, leftEncoding.tencoding, true), leftEncoding);
            return string;
        }
    }

    /** The semantics of this primitive are such that the LHS string must be BINARY and then the result is BINARY as
     * well, and the RHS bytes are just appended to the LHS bytes, without encoding negotiation (which could cause the
     * LHS encoding to change). */
    @Primitive(name = "string_binary_append")
    public abstract static class StringBinaryAppendNode extends PrimitiveArrayArgumentsNode {
        @Specialization(guards = "libOther.isRubyString(node, other)", limit = "1")
        static RubyString stringBinaryAppend(RubyString string, Object other,
                @Bind Node node,
                @Cached RubyStringLibrary libString,
                @Cached RubyStringLibrary libOther,
                @Cached TruffleString.ConcatNode concatNode,
                @Cached TruffleString.ForceEncodingNode forceEncodingNode) {
            if (libString.getEncoding(node, string) != Encodings.BINARY) {
                throw CompilerDirectives.shouldNotReachHere("LHS String must be BINARY");
            }
            var left = string.tstring;
            var right = forceEncodingNode.execute(libOther.getTString(node, other), libOther.getTEncoding(node, other),
                    Encodings.BINARY.tencoding);
            string.setTString(concatNode.execute(left, right, Encodings.BINARY.tencoding, true), Encodings.BINARY);
            return string;
        }
    }

    @Primitive(name = "string_substring", lowerFixnum = { 1, 2 })
    @ImportStatic(StringGuards.class)
    public abstract static class StringSubstringPrimitiveNode extends PrimitiveArrayArgumentsNode {

        public abstract Object execute(Object string, int codePointOffset, int codePointLength);

        @Specialization
        Object stringSubstringGeneric(Object string, int codePointOffset, int codePointLength,
                @Cached RubyStringLibrary libString,
                @Bind("libString.getTString($node, string)") AbstractTruffleString tstring,
                @Bind("libString.getEncoding($node, string)") RubyEncoding encoding,
                @Cached StringHelperNodes.NormalizeIndexNode normalizeIndexNode,
                @Cached CodePointLengthNode codePointLengthNode,
                @Cached TruffleString.SubstringNode substringNode,
                @Cached InlinedConditionProfile negativeIndexProfile,
                @Cached InlinedConditionProfile tooLargeTotalProfile,
                @Cached InlinedConditionProfile triviallyOutOfBoundsProfile) {
            int stringCodePointLength = codePointLengthNode.execute(tstring, encoding.tencoding);
            if (triviallyOutOfBoundsProfile.profile(this,
                    codePointLength < 0 || codePointOffset > stringCodePointLength)) {
                return nil;
            }

            int normalizedCodePointOffset = normalizeIndexNode.executeNormalize(this, codePointOffset,
                    stringCodePointLength);
            if (negativeIndexProfile.profile(this, normalizedCodePointOffset < 0)) {
                return nil;
            }

            int normalizedCodePointLength = codePointLength;
            if (tooLargeTotalProfile
                    .profile(this, normalizedCodePointOffset + normalizedCodePointLength > stringCodePointLength)) {
                normalizedCodePointLength = stringCodePointLength - normalizedCodePointOffset;
            }

            return createSubString(substringNode, tstring, encoding, normalizedCodePointOffset,
                    normalizedCodePointLength);
        }

    }

    @Primitive(name = "string_from_bytearray", lowerFixnum = { 1, 2 })
    public abstract static class StringFromByteArrayPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyString stringFromByteArray(RubyByteArray byteArray, int start, int count, RubyEncoding rubyEncoding,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final byte[] bytes = byteArray.bytes;
            final byte[] array = ArrayUtils.extractRange(bytes, start, start + count);

            return createString(fromByteArrayNode, array, rubyEncoding);
        }

    }

    @Primitive(name = "string_to_null_terminated_byte_array")
    public abstract static class StringToNullTerminatedByteArrayNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "libString.isRubyString(node, string)", limit = "1")
        static Object toByteArray(Object string,
                @Bind Node node,
                @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                @Cached RubyStringLibrary libString) {
            final var encoding = libString.getEncoding(node, string);
            final var tstring = libString.getTString(node, string);
            final int bytesToCopy = tstring.byteLength(encoding.tencoding);
            final var bytesWithNull = new byte[bytesToCopy + 1];

            // NOTE: we always need one copy here, as native code could modify the passed byte[]
            copyToByteArrayNode.execute(tstring, 0,
                    bytesWithNull, 0, bytesToCopy, encoding.tencoding);

            return getContext(node).getEnv().asGuestValue(bytesWithNull);
        }

        @Specialization
        Object emptyString(Nil string) {
            return getContext().getEnv().asGuestValue(null);
        }

    }

    @Primitive(name = "string_interned?")
    public abstract static class IsInternedNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isInterned(ImmutableRubyString string) {
            return true;
        }

        @Specialization
        boolean isInterned(RubyString string) {
            return false;
        }
    }

    @Primitive(name = "string_intern")
    public abstract static class InternNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        ImmutableRubyString internString(RubyString string,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
            var encoding = libString.getEncoding(this, string);
            var byteArray = getInternalByteArrayNode.execute(string.tstring, encoding.tencoding);
            return getLanguage().getImmutableString(byteArray,
                    TStringUtils.hasImmutableInternalByteArray(string.tstring), encoding);
        }
    }

    @Primitive(name = "string_truncate", lowerFixnum = 1)
    public abstract static class TruncateNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "newByteLength < 0")
        RubyString truncateLengthNegative(RubyString string, int newByteLength) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().argumentError(formatNegativeError(newByteLength), this));
        }

        @TruffleBoundary
        @Specialization(guards = { "newByteLength >= 0", "newByteLength > byteLength" })
        RubyString truncateLengthTooLong(RubyString string, int newByteLength,
                @Cached @Shared RubyStringLibrary libString,
                @Bind("libString.byteLength($node, string)") int byteLength) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().argumentError(formatTooLongError(newByteLength, string), this));
        }

        @Specialization(guards = { "newByteLength >= 0", "newByteLength <= byteLength" })
        RubyString tuncate(RubyString string, int newByteLength,
                @Cached @Shared RubyStringLibrary libString,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Bind("libString.byteLength($node, string)") int byteLength) {
            var tencoding = libString.getTEncoding(this, string);
            string.setTString(substringNode.execute(string.tstring, 0, newByteLength, tencoding, true));
            return string;
        }

        @TruffleBoundary
        private String formatNegativeError(int count) {
            return StringUtils.format("Invalid byte count: %d is negative", count);
        }

        @TruffleBoundary
        private String formatTooLongError(int count, RubyString string) {
            return StringUtils
                    .format("Invalid byte count: %d exceeds string size of %d bytes", count,
                            string.byteLengthUncached());
        }

    }

    @Primitive(name = "string_is_character_head?", lowerFixnum = 2)
    public abstract static class IsCharacterHeadPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isCharacterHead(RubyEncoding enc, Object string, int byteOffset,
                @Cached RubyStringLibrary libString,
                @Cached IsCharacterHeadNode isCharacterHeadNode) {
            var tstring = libString.getTString(this, string);
            return isCharacterHeadNode.execute(enc, tstring, byteOffset);
        }
    }

}
