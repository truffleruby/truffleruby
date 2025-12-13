/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.extra.ffi;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.TStringConstants;
import org.truffleruby.core.support.RubyByteArray;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.WriteObjectFieldNode;

@CoreModule(value = "Truffle::FFI::Pointer", isClass = true)
public abstract class PointerNodes {

    public static final BigInteger TWO_POW_64 = BigInteger.valueOf(1).shiftLeft(64);

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CheckNullPointerNode extends RubyBaseNode {

        public abstract void execute(Node node, Pointer ptr);

        @Specialization
        static void checkNull(Node node, Pointer ptr,
                @Cached InlinedBranchProfile nullPointerProfile) {
            if (ptr.isNull()) {
                nullPointerProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        getContext(node).getCoreExceptions().ffiNullPointerError(
                                "invalid memory access at address=0x0",
                                node));
            }

        }

    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPointer allocate(RubyClass pointerClass) {
            final Shape shape = getLanguage().truffleFFIPointerShape;
            final RubyPointer instance = new RubyPointer(pointerClass, shape, Pointer.getNullPointer(getContext()));
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @Primitive(name = "pointer_find_type_size")
    public abstract static class PointerFindTypeSizePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        int findTypeSize(RubySymbol type) {
            final String typeString = type.getString();
            final int size = typeSize(typeString);
            if (size > 0) {
                return size;
            } else {
                final Object typedef = getContext()
                        .getTruffleNFI()
                        .resolveTypeRaw(getContext().getNativeConfiguration(), typeString);
                final int typedefSize = typeSize(StringOperations.getJavaString(typedef));
                assert typedefSize > 0 : typedef;
                return typedefSize;
            }
        }

        private static int typeSize(String type) {
            switch (type) {
                case "char":
                case "uchar":
                case "bool":
                    return 1;
                case "short":
                case "ushort":
                    return 2;
                case "int":
                case "uint":
                case "int32":
                case "uint32":
                case "float":
                    return 4;
                case "long":
                case "ulong":
                case "int64":
                case "uint64":
                case "long_long":
                case "ulong_long":
                case "double":
                case "pointer":
                    return 8;
                default:
                    return -1;
            }
        }

    }

    @CoreMethod(names = "address=", required = 1)
    public abstract static class PointerSetAddressNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long setAddress(RubyPointer pointer, long address) {
            pointer.pointer = new Pointer(getContext(), address);
            return address;
        }

    }

    @CoreMethod(names = { "address", "to_i" })
    public abstract static class PointerAddressNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long address(RubyPointer pointer) {
            return pointer.pointer.getAddress();
        }

    }

    @Primitive(name = "pointer_size")
    public abstract static class PointerSizeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long size(RubyPointer pointer) {
            return pointer.pointer.getSize();
        }

    }

    @CoreMethod(names = "total=", required = 1)
    public abstract static class PointerSetSizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long setSize(RubyPointer pointer, long size) {
            final Pointer old = pointer.pointer;
            pointer.pointer = new Pointer(getContext(), old.getAddress(), size);
            return size;
        }

    }

    @CoreMethod(names = "autorelease?")
    public abstract static class PointerIsAutoreleaseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isAutorelease(RubyPointer pointer) {
            return pointer.pointer.isAutorelease();
        }

    }

    @CoreMethod(names = "autorelease=", required = 1)
    public abstract static class PointerSetAutoreleaseNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "autorelease")
        boolean enableAutorelease(RubyPointer pointer, boolean autorelease) {
            pointer.pointer.enableAutorelease(getLanguage());
            return autorelease;
        }

        @Specialization(guards = "!autorelease")
        boolean disableAutorelease(RubyPointer pointer, boolean autorelease) {
            pointer.pointer.disableAutorelease();
            return autorelease;
        }

    }

    @Primitive(name = "pointer_malloc")
    public abstract static class PointerMallocPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyPointer malloc(RubyPointer pointer, long size) {
            pointer.pointer = Pointer.malloc(getContext(), size);
            return pointer;
        }

    }

    @CoreMethod(names = "free")
    public abstract static class PointerFreeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPointer free(RubyPointer pointer) {
            pointer.pointer.free();
            return pointer;
        }

    }

    @Primitive(name = "pointer_clear", lowerFixnum = 1)
    public abstract static class PointerClearNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyPointer clear(RubyPointer pointer, long length) {
            pointer.pointer.writeBytes(0, length, (byte) 0);
            return pointer;
        }

    }

    @Primitive(name = "pointer_copy_memory")
    public abstract static class PointerCopyMemoryNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object copyMemory(long to, long from, long size,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), to);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeBytes(0, new Pointer(getContext(), from), 0, size);
            return nil;
        }

    }

    // Special reads and writes

    @Primitive(name = "pointer_read_string_to_null")
    public abstract static class PointerReadStringToNullNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "limit == 0")
        RubyString readNullPointer(long address, long limit) {
            return createString(TStringConstants.EMPTY_BINARY, Encodings.BINARY);
        }

        @Specialization(guards = "limit != 0")
        RubyString readStringToNull(long address, long limit,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), interop, 0, limit);
            return createString(fromByteArrayNode, bytes, Encodings.BINARY);
        }

        @Specialization
        RubyString readStringToNull(long address, Nil limit,
                @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), interop, 0);
            return createString(fromByteArrayNode, bytes, Encodings.BINARY);
        }

    }

    @Primitive(name = "pointer_read_bytes_to_byte_array", lowerFixnum = { 1, 3 })
    public abstract static class PointerReadBytesToArrayNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object readBytes(RubyByteArray array, int arrayOffset, long address, int length,
                @Cached InlinedConditionProfile zeroProfile,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            if (zeroProfile.profile(this, length == 0)) {
                // No need to check the pointer address if we read nothing
                return nil;
            } else {
                checkNullPointerNode.execute(this, ptr);
                final byte[] bytes = array.bytes;
                ptr.readBytes(0, bytes, arrayOffset, length);
                return nil;
            }
        }

    }

    @Primitive(name = "pointer_read_bytes", lowerFixnum = 1)
    public abstract static class PointerReadBytesNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyString readBytes(long address, int length,
                @Cached InlinedConditionProfile zeroProfile,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            if (zeroProfile.profile(this, length == 0)) {
                // No need to check the pointer address if we read nothing
                return createString(TStringConstants.EMPTY_BINARY, Encodings.BINARY);
            } else {
                checkNullPointerNode.execute(this, ptr);
                final byte[] bytes = new byte[length];
                ptr.readBytes(0, bytes, 0, length);
                return createString(fromByteArrayNode, bytes, Encodings.BINARY);
            }
        }

    }

    @Primitive(name = "pointer_write_bytes", lowerFixnum = { 2, 3 })
    public abstract static class PointerWriteBytesNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        static Object writeBytes(long address, Object string, int index, int length,
                @Cached InlinedConditionProfile nonZeroProfile,
                @Cached TruffleString.CopyToNativeMemoryNode copyToNativeMemoryNode,
                @Cached RubyStringLibrary libString,
                @Cached CheckNullPointerNode checkNullPointerNode,
                @Bind Node node) {
            Pointer ptr = new Pointer(getContext(node), address);
            var tstring = libString.getTString(node, string);
            var encoding = libString.getTEncoding(node, string);

            assert index + length <= tstring.byteLength(encoding);

            if (nonZeroProfile.profile(node, length != 0)) {
                // No need to check the pointer address if we write nothing
                checkNullPointerNode.execute(node, ptr);

                copyToNativeMemoryNode.execute(tstring, index, ptr, 0, length, encoding);
            }

            return string;
        }

    }

    // Reads and writes of number types

    @Primitive(name = "pointer_read_char")
    public abstract static class PointerReadCharNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int readCharSigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readByte(0);
        }

    }

    @Primitive(name = "pointer_read_uchar")
    public abstract static class PointerReadUCharNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int readCharUnsigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return Byte.toUnsignedInt(ptr.readByte(0));
        }

    }

    @Primitive(name = "pointer_read_short")
    public abstract static class PointerReadShortNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int readShortSigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readShort(0);
        }
    }

    @Primitive(name = "pointer_read_ushort")
    public abstract static class PointerReadUShortNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int readShortUnsigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return Short.toUnsignedInt(ptr.readShort(0));
        }

    }

    @Primitive(name = "pointer_read_int")
    public abstract static class PointerReadIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int readIntSigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readInt(0);
        }

    }

    @Primitive(name = "pointer_read_uint")
    public abstract static class PointerReadUIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long readIntUnsigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return Integer.toUnsignedLong(ptr.readInt(0));
        }

    }

    @Primitive(name = "pointer_read_long")
    public abstract static class PointerReadLongNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        long readLongSigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readLong(0);
        }

    }

    @Primitive(name = "pointer_read_ulong")
    public abstract static class PointerReadULongNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object readLongUnsigned(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return readUnsignedLong(ptr, 0);
        }

        @TruffleBoundary
        private static Object readUnsignedLong(Pointer ptr, int offset) {
            long signedValue = ptr.readLong(offset);
            return BigIntegerOps.asUnsignedFixnumOrBignum(signedValue);
        }
    }

    @Primitive(name = "pointer_read_float")
    public abstract static class PointerReadFloatNode extends PrimitiveArrayArgumentsNode {

        // must return double so Ruby nodes can deal with it
        @Specialization
        double readFloat(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readFloat(0);
        }

    }

    @Primitive(name = "pointer_read_double")
    public abstract static class PointerReadDoubleNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        double readDouble(long address,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            return ptr.readDouble(0);
        }

    }

    @Primitive(name = "pointer_read_pointer")
    public abstract static class PointerReadPointerNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyPointer readPointer(long address,
                @Cached CheckNullPointerNode checkNullPointerNode,
                @Cached WriteObjectFieldNode writeTypeSize) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            final Pointer readPointer = ptr.readPointer(getContext(), 0);
            final RubyPointer instance = new RubyPointer(
                    coreLibrary().truffleFFIPointerClass,
                    getLanguage().truffleFFIPointerShape,
                    readPointer);
            AllocationTracing.trace(instance, this);

            // We don't call Truffle::FFI::Pointer#initialize so we manually set it instead
            writeTypeSize.execute(this, instance, "@type_size", 1);

            return instance;
        }

    }

    @Primitive(name = "pointer_write_char", lowerFixnum = 1)
    @ImportStatic(Byte.class)
    public abstract static class PointerWriteCharNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "MIN_VALUE <= value", "value <= MAX_VALUE" })
        Object writeChar(long address, int value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            byte byteValue = (byte) value;
            ptr.writeByte(0, byteValue);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_uchar", lowerFixnum = 1)
    @ImportStatic(Byte.class)
    public abstract static class PointerWriteUCharNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "0 <= value", "value <= MAX_VALUE" })
        Object writeChar(long address, int value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            byte byteValue = (byte) value;
            ptr.writeByte(0, byteValue);
            return nil;
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < 256" })
        Object writeUnsignedChar(long address, int value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            byte signed = (byte) value; // Same as value - 2^8
            ptr.writeByte(0, signed);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_short", lowerFixnum = 1)
    @ImportStatic(Short.class)
    public abstract static class PointerWriteShortNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "MIN_VALUE <= value", "value <= MAX_VALUE" })
        Object writeShort(long address, int value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            short shortValue = (short) value;
            ptr.writeShort(0, shortValue);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_ushort", lowerFixnum = 1)
    @ImportStatic(Short.class)
    public abstract static class PointerWriteUShortNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "0 <= value", "value <= MAX_VALUE" })
        Object writeShort(long address, int value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            short shortValue = (short) value;
            ptr.writeShort(0, shortValue);
            return nil;
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < 65536" })
        Object writeUnsignedSort(long address, int value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            short signed = (short) value; // Same as value - 2^16
            ptr.writeShort(0, signed);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_int", lowerFixnum = 1)
    public abstract static class PointerWriteIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object writeInt(long address, int value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeInt(0, value);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_uint", lowerFixnum = 1)
    @ImportStatic(Integer.class)
    public abstract static class PointerWriteUIntNode extends PrimitiveArrayArgumentsNode {

        static final long MAX_UNSIGNED_INT_PLUS_ONE = 1L << Integer.SIZE;

        @Specialization(guards = "value >= 0")
        Object writeInt(long address, int value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeInt(0, value);
            return nil;
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < MAX_UNSIGNED_INT_PLUS_ONE" })
        Object writeUnsignedInt(long address, long value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            int signed = (int) value; // Same as value - 2^32
            ptr.writeInt(0, signed);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_long")
    public abstract static class PointerWriteLongNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object writeLong(long address, long value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeLong(0, value);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_ulong")
    public abstract static class PointerWriteULongNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "value >= 0")
        Object writeLong(long address, long value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeLong(0, value);
            return nil;
        }

        @Specialization
        Object writeUnsignedLong(long address, RubyBignum value,
                @Cached @Shared CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            writeUnsignedLong(ptr, 0, value);
            return nil;
        }

        @TruffleBoundary
        private static void writeUnsignedLong(Pointer ptr, int offset, RubyBignum value) {
            BigInteger v = value.value;
            assert v.signum() >= 0;
            assert v.compareTo(TWO_POW_64) < 0;
            BigInteger signed = v.subtract(TWO_POW_64);
            ptr.writeLong(offset, signed.longValueExact());
        }

    }

    @Primitive(name = "pointer_write_float")
    public abstract static class PointerWriteFloatNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object writeFloat(long address, double value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeFloat(0, (float) value);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_double")
    public abstract static class PointerWriteDoubleNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object writeDouble(long address, double value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writeDouble(0, value);
            return nil;
        }

    }

    @Primitive(name = "pointer_write_pointer", lowerFixnum = 2)
    public abstract static class PointerWritePointerNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object writePointer(long address, long value,
                @Cached CheckNullPointerNode checkNullPointerNode) {
            final Pointer ptr = new Pointer(getContext(), address);
            checkNullPointerNode.execute(this, ptr);
            ptr.writePointer(0, value);
            return nil;
        }

    }

}
