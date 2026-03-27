/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.support;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.extra.ffi.PointerNodes;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "Truffle::ByteArray", isClass = true)
public abstract class ByteArrayNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyByteArray allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().byteArrayShape;
            final RubyByteArray instance = new RubyByteArray(rubyClass, shape,
                    org.truffleruby.core.array.ArrayUtils.EMPTY_BYTES);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @CoreMethod(names = "initialize", required = 1, lowerFixnum = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyByteArray initialize(RubyByteArray byteArray, int size) {
            final byte[] bytes = new byte[size];
            byteArray.bytes = bytes;
            return byteArray;
        }

    }

    @CoreMethod(names = "[]", required = 1, lowerFixnum = 1)
    public abstract static class GetByteNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int getByte(RubyByteArray byteArray, int index) {
            return byteArray.bytes[index] & 0xff;
        }

    }

    @CoreMethod(names = "prepend", required = 1)
    public abstract static class PrependNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "strings.isRubyString(node, string)", limit = "1")
        static RubyByteArray prepend(RubyByteArray byteArray, Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            final byte[] bytes = byteArray.bytes;

            var tstring = strings.getTString(node, string);
            var encoding = strings.getTEncoding(node, string);

            final int prependLength = tstring.byteLength(encoding);
            final int originalLength = bytes.length;
            final int newLength = prependLength + originalLength;
            final byte[] prependedBytes = new byte[newLength];

            copyToByteArrayNode.execute(tstring, 0, prependedBytes, 0, prependLength, encoding);
            System.arraycopy(bytes, 0, prependedBytes, prependLength, originalLength);

            final RubyByteArray instance = new RubyByteArray(
                    coreLibrary(node).byteArrayClass,
                    getLanguage(node).byteArrayShape,
                    prependedBytes);

            AllocationTracing.trace(instance, node);
            return instance;
        }

    }

    @CoreMethod(names = "[]=", required = 2, lowerFixnum = { 1, 2 })
    public abstract static class SetByteNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int setByte(RubyByteArray byteArray, int index, int value,
                @Cached InlinedBranchProfile errorProfile) {
            final byte[] bytes = byteArray.bytes;
            if (index < 0 || index >= bytes.length) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().indexError("index out of bounds", this));
            }

            bytes[index] = (byte) value;
            return value;
        }

    }

    @CoreMethod(names = "fill", required = 4, lowerFixnum = { 1, 3, 4 })
    public abstract static class FillNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object fillFromString(RubyByteArray destByteArray, int dstStart, RubyString source, int srcStart, int length,
                @Cached RubyStringLibrary libString,
                @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            var tstring = source.tstring;
            var encoding = libString.getTEncoding(this, source);
            copyToByteArrayNode.execute(tstring, srcStart, destByteArray.bytes, dstStart, length, encoding);
            return source;
        }

        @Specialization
        Object fillFromPointer(RubyByteArray byteArray, int dstStart, RubyPointer source, int srcStart, int length,
                @Cached PointerNodes.CheckNullPointerNode checkNullPointerNode) {
            assert length > 0;

            final Pointer ptr = source.pointer;
            final byte[] bytes = byteArray.bytes;

            checkNullPointerNode.execute(this, ptr);

            ptr.readBytes(srcStart, bytes, dstStart, length);
            return source;
        }

    }

    @Primitive(name = "bytearray_locate", lowerFixnum = { 2, 3 })
    public abstract static class LocateNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isSingleBytePattern(patternTString, patternEncoding)")
        Object getByteSingleByte(RubyByteArray byteArray, Object pattern, int start, int length,
                @Cached TruffleString.ReadByteNode readByteNode,
                @Cached InlinedBranchProfile tooSmallStartProfile,
                @Cached InlinedBranchProfile tooLargeStartProfile,
                @Cached @Shared RubyStringLibrary libPattern,
                @Bind("libPattern.getTString($node, pattern)") AbstractTruffleString patternTString,
                @Bind("libPattern.getTEncoding($node, pattern)") TruffleString.Encoding patternEncoding) {

            byte[] bytes = byteArray.bytes;
            int searchByte = readByteNode.execute(patternTString, 0, patternEncoding);

            if (start >= length) {
                tooLargeStartProfile.enter(this);
                return nil;
            }

            if (start < 0) {
                tooSmallStartProfile.enter(this);
                start = 0;
            }

            final int index = ArrayUtils.indexOf(bytes, start, length, (byte) searchByte);

            return index == -1 ? nil : index + 1;
        }

        @Specialization(guards = "!isSingleBytePattern(patternTString, patternEncoding)")
        Object getByte(RubyByteArray byteArray, Object pattern, int start, int length,
                @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                @Cached InlinedConditionProfile noCopyProfile,
                @Cached InlinedConditionProfile notFoundProfile,
                @Cached @Shared RubyStringLibrary libPattern,
                @Bind("libPattern.getTString($node, pattern)") AbstractTruffleString patternTString,
                @Bind("libPattern.getTEncoding($node, pattern)") TruffleString.Encoding patternEncoding) {
            // TODO (nirvdrum 09-June-2022): Copying the byte array here is wasteful, but ArrayUtils.indexOfWithOrMask does not accept an offset or length for the needle.
            // Another possibility would be to create a MutableTruffleString for the RubyByteArray and use ByteIndexOfStringNode, but that would force computation of the coderange of the byte[]
            final byte[] patternBytes = TStringUtils.getBytesOrCopy(this, patternTString, patternEncoding,
                    getInternalByteArrayNode, noCopyProfile);

            final int index = ArrayUtils.indexOfWithOrMask(byteArray.bytes, start, length, patternBytes, null);

            if (notFoundProfile.profile(this, index == -1)) {
                return nil;
            } else {
                return index + codePointLengthNode.execute(patternTString, patternEncoding);
            }
        }

        protected boolean isSingleBytePattern(AbstractTruffleString string, TruffleString.Encoding encoding) {
            return string.byteLength(encoding) == 1;
        }
    }

}
