/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Contains code modified from JRuby's RubyConverter.java
 */
package org.truffleruby.core.encoding;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.Ptr;
import org.graalvm.shadowed.org.jcodings.transcode.EConv;
import org.graalvm.shadowed.org.jcodings.transcode.EConvFlags;
import org.graalvm.shadowed.org.jcodings.transcode.EConvResult;
import org.graalvm.shadowed.org.jcodings.transcode.Transcoder;
import org.graalvm.shadowed.org.jcodings.transcode.TranscoderDB;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.TStringBuilder;
import org.truffleruby.core.string.EncodingUtils;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.RubyNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "Encoding::Converter", isClass = true)
public abstract class EncodingConverterNodes {

    @NonStandard
    @CoreMethod(
            names = "initialize_jcodings",
            required = 2,
            optional = 1,
            lowerFixnum = 3,
            visibility = Visibility.PRIVATE)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object initialize(RubyEncodingConverter self, RubyEncoding source, RubyEncoding destination, int options) {
            // Adapted from RubyConverter - see attribution there
            //
            // This method should only be called after the Encoding::Converter instance has already been initialized
            // by Rubinius.  Rubinius will do the heavy lifting of parsing the options hash and setting the `@options`
            // ivar to the resulting int for EConv flags.

            Encoding sourceEncoding = Encodings.getTranscodingJCoding(source);
            Encoding destinationEncoding = Encodings.getTranscodingJCoding(destination);

            final EConv econv = TranscoderDB
                    .open(sourceEncoding.getName(), destinationEncoding.getName(), toJCodingFlags(options));

            if (econv == null) {
                return nil;
            }

            econv.sourceEncoding = sourceEncoding;
            econv.destinationEncoding = destinationEncoding;

            self.econv = econv;

            // There are N-1 edges connecting N encodings on the path from source -> destination.
            // We need to include every encoding along the path in the return value.
            Object[] ret = new Object[econv.numTranscoders + 1];

            int retIndex = 0;
            for (int i = 0; i < econv.numTranscoders; i++) {
                final Transcoder transcoder = econv.elements[i].transcoding.transcoder;

                if (EncodingUtils.DECORATOR_P(transcoder.getSource(), transcoder.getDestination())) {
                    continue;
                }

                final byte[] segmentSource = transcoder.getSource();
                ret[retIndex++] = getSymbol(
                        StringUtils.toUpperCase(new String(segmentSource, StandardCharsets.US_ASCII)));
            }

            final int retSize = retIndex + 1;
            if (retSize != ret.length) {
                // The decorated entry really isn't part of the transcoding path, but jcodings treats it as if it were,
                // so we need to reduce the returned array size accordingly.
                ret = ArrayUtils.extractRange(ret, 0, retSize);
            }

            final byte[] destinationName = destination.getNameByteArray();
            ret[retIndex] = getSymbol(StringUtils.toUpperCase(new String(destinationName, StandardCharsets.US_ASCII)));

            return createArray(ret);
        }

        /** We and JCodings process Encoding::Converter options flags differently. We split the processing between
         * initial setup and the replacement value setup, whereas JCodings handles them all during initial setup. We
         * figure out what flags JCodings additionally expects to be set and set them to satisfy EConv. */
        private int toJCodingFlags(int flags) {
            if ((flags & EConvFlags.XML_TEXT_DECORATOR) != 0) {
                flags |= EConvFlags.UNDEF_HEX_CHARREF;
            }

            if ((flags & EConvFlags.XML_ATTR_CONTENT_DECORATOR) != 0) {
                flags |= EConvFlags.UNDEF_HEX_CHARREF;
            }

            return flags;
        }

    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyEncodingConverter allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().encodingConverterShape;
            final RubyEncodingConverter instance = new RubyEncodingConverter(rubyClass, shape, null);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @Primitive(name = "encoding_transcoders_from_encoding")
    public abstract static class TranscodersFromEncodingNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object search(RubySymbol source) {
            final Set<String> transcoders = TranscodingManager.allDirectTranscoderPaths.get(source.getString());
            if (transcoders == null) {
                return nil;
            }

            final Object[] destinations = new Object[transcoders.size()];
            int i = 0;

            for (String transcoder : transcoders) {
                destinations[i++] = getSymbol(transcoder);
            }

            return createArray(destinations);
        }

    }

    @Primitive(name = "encoding_converter_primitive_convert", lowerFixnum = { 3, 4, 5 })
    public abstract static class PrimitiveConvertNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object encodingConverterPrimitiveConvert(
                RubyEncodingConverter encodingConverter,
                RubyString source,
                RubyString target,
                int offset,
                int size,
                int options,
                @Cached RubyStringLibrary libString,
                @Cached DispatchNode destinationEncodingNode,
                @Cached TruffleString.SubstringByteIndexNode substringNode,
                @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
            // Taken from org.jruby.RubyConverter#primitive_convert.

            var tencoding = libString.getTEncoding(this, source);
            var tstring = source.tstring;

            final TStringBuilder outBytes = TStringBuilder.create(target);

            final Ptr inPtr = new Ptr();
            final Ptr outPtr = new Ptr();

            final EConv ec = encodingConverter.econv;

            final boolean changeOffset = (offset == 0);
            final boolean growOutputBuffer = (size == -1);

            if (size == -1) {
                int minSize = 16; // in MRI, this is RSTRING_EMBED_LEN_MAX
                size = Math.max(minSize, source.byteLengthUncached());
            }

            while (true) {
                if (changeOffset) {
                    offset = outBytes.getLength();
                }

                if (outBytes.getLength() < offset) {
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().argumentError("output offset too big", this));
                }

                long outputByteEnd = (long) offset + size;

                if (outputByteEnd > Integer.MAX_VALUE) {
                    // overflow check
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().argumentError("output offset + bytesize too big", this));
                }

                outBytes.unsafeEnsureSpace((int) outputByteEnd);

                var sourceBytes = getInternalByteArrayNode.execute(tstring, tencoding);

                inPtr.p = sourceBytes.getOffset();
                outPtr.p = offset;
                EConvResult res = ec.convert(
                        sourceBytes.getArray(), inPtr, sourceBytes.getEnd(),
                        outBytes.getUnsafeBytes(), outPtr, outPtr.p + size,
                        options);

                outBytes.setLength(outPtr.p);

                int inputOffset = inPtr.p - sourceBytes.getOffset();
                tstring = substringNode.execute(source.tstring, inputOffset, source.byteLengthUncached() - inputOffset,
                        tencoding, true);
                source.setTString(tstring);

                if (growOutputBuffer && res == EConvResult.DestinationBufferFull) {
                    if (Integer.MAX_VALUE / 2 < size) {
                        throw new RaiseException(
                                getContext(),
                                coreExceptions().argumentError("too long conversion result", this));
                    }
                    size *= 2;
                    continue;
                }

                if (ec.destinationEncoding != null) {
                    outBytes.setEncoding(Encodings.getBuiltInEncoding(ec.destinationEncoding));
                }

                var destinationEncoding = (RubyEncoding) destinationEncodingNode.call(encodingConverter,
                        "destination_encoding");
                target.setTString(outBytes.toTString(), destinationEncoding);

                return getSymbol(res.symbolicName());
            }
        }
    }

    @CoreMethod(names = "putback", optional = 1, lowerFixnum = 1)
    public abstract static class EncodingConverterPutbackNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

        @Specialization
        RubyString encodingConverterPutback(RubyEncodingConverter encodingConverter, int maxBytes,
                @Cached @Shared DispatchNode sourceEncodingNode) {
            // Taken from org.jruby.RubyConverter#putback.

            final EConv ec = encodingConverter.econv;
            final int putbackable = ec.putbackable();

            return putback(encodingConverter, putbackable < maxBytes ? putbackable : maxBytes, sourceEncodingNode);
        }

        @Specialization
        RubyString encodingConverterPutback(RubyEncodingConverter encodingConverter, NotProvided maxBytes,
                @Cached @Shared DispatchNode sourceEncodingNode) {
            // Taken from org.jruby.RubyConverter#putback.

            final EConv ec = encodingConverter.econv;

            return putback(encodingConverter, ec.putbackable(), sourceEncodingNode);
        }

        private RubyString putback(RubyEncodingConverter encodingConverter, int n, DispatchNode sourceEncodingNode) {

            // Taken from org.jruby.RubyConverter#putback.

            final EConv ec = encodingConverter.econv;

            final byte[] bytes = new byte[n];
            ec.putback(bytes, 0, n);

            var sourceEncoding = (RubyEncoding) sourceEncodingNode.call(encodingConverter, "source_encoding");
            return createString(fromByteArrayNode, bytes, sourceEncoding);
        }
    }

    @Primitive(name = "encoding_converter_last_error")
    public abstract static class EncodingConverterLastErrorNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object encodingConverterLastError(RubyEncodingConverter encodingConverter,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final EConv ec = encodingConverter.econv;
            final EConv.LastError lastError = ec.lastError;

            if (lastError.getResult() != EConvResult.InvalidByteSequence &&
                    lastError.getResult() != EConvResult.IncompleteInput &&
                    lastError.getResult() != EConvResult.UndefinedConversion) {
                return nil;
            }

            final boolean readAgain = lastError.getReadAgainLength() != 0;
            final int size = readAgain ? 5 : 4;
            final Object[] store = new Object[size];

            store[0] = eConvResultToSymbol(lastError.getResult());
            store[1] = createString(fromByteArrayNode, lastError.getSource(), Encodings.BINARY);
            store[2] = createString(fromByteArrayNode, lastError.getDestination(), Encodings.BINARY);
            var errorTString = TStringBuilder.create(
                    lastError.getErrorBytes(),
                    lastError.getErrorBytesP(),
                    lastError.getErrorBytesLength()).toTStringUnsafe(fromByteArrayNode);
            store[3] = createString(errorTString, Encodings.BINARY);

            if (readAgain) {
                var readAgainTString = TStringBuilder.create(
                        lastError.getErrorBytes(),
                        lastError.getErrorBytesP() + lastError.getErrorBytesLength(),
                        lastError.getReadAgainLength()).toTStringUnsafe(fromByteArrayNode);
                store[4] = createString(readAgainTString, Encodings.BINARY);
            }

            return createArray(store);
        }

        private RubySymbol eConvResultToSymbol(EConvResult result) {
            switch (result) {
                case InvalidByteSequence:
                    return getSymbol("invalid_byte_sequence");
                case UndefinedConversion:
                    return getSymbol("undefined_conversion");
                case DestinationBufferFull:
                    return getSymbol("destination_buffer_full");
                case SourceBufferEmpty:
                    return getSymbol("source_buffer_empty");
                case Finished:
                    return getSymbol("finished");
                case AfterOutput:
                    return getSymbol("after_output");
                case IncompleteInput:
                    return getSymbol("incomplete_input");
            }

            throw new UnsupportedOperationException(StringUtils.format("Unknown EConv result: %s", result));
        }

    }

    @CoreMethod(names = "primitive_errinfo")
    public abstract static class EncodingConverterErrinfoNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray encodingConverterLastError(RubyEncodingConverter encodingConverter,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final EConv ec = encodingConverter.econv;
            final EConv.LastError lastError = ec.lastError;

            final Object[] ret = { getSymbol(lastError.getResult().symbolicName()), nil, nil, nil, nil };

            if (lastError.getSource() != null) {
                ret[1] = createString(fromByteArrayNode, lastError.getSource(), Encodings.BINARY);
            }

            if (lastError.getDestination() != null) {
                ret[2] = createString(fromByteArrayNode, lastError.getDestination(), Encodings.BINARY);
            }

            if (lastError.getErrorBytes() != null) {
                var errorTString = TStringBuilder.create(
                        lastError.getErrorBytes(),
                        lastError.getErrorBytesP(),
                        lastError.getErrorBytesLength()).toTStringUnsafe(fromByteArrayNode);
                ret[3] = createString(errorTString, Encodings.BINARY);

                var readAgainTString = TStringBuilder.create(
                        lastError.getErrorBytes(),
                        lastError.getErrorBytesP() + lastError.getErrorBytesLength(),
                        lastError.getReadAgainLength()).toTStringUnsafe(fromByteArrayNode);
                ret[4] = createString(readAgainTString, Encodings.BINARY);
            }

            return createArray(ret);
        }

    }

    @CoreMethod(names = "replacement")
    public abstract static class EncodingConverterReplacementNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

        @TruffleBoundary
        @Specialization
        RubyString getReplacement(RubyEncodingConverter encodingConverter) {
            final EConv ec = encodingConverter.econv;

            final int ret = ec.makeReplacement();
            if (ret == -1) {
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().encodingUndefinedConversionError(this));
            }

            final byte[] bytes = ArrayUtils.extractRange(ec.replacementString, 0, ec.replacementLength);
            final String encodingName = new String(ec.replacementEncoding, StandardCharsets.US_ASCII);
            final RubyEncoding encoding = getContext().getEncodingManager().getRubyEncoding(encodingName);

            return createString(fromByteArrayNode, bytes, encoding);
        }

    }

    @CoreMethod(names = "replacement=", required = 1)
    @NodeChild(value = "encodingConverter", type = RubyNode.class)
    @NodeChild(value = "replacement", type = RubyBaseNodeWithExecute.class)
    public abstract static class EncodingConverterSetReplacementNode extends CoreMethodNode {

        @Specialization(guards = "libReplacement.isRubyString(this, replacement)", limit = "1")
        static Object setReplacement(RubyEncodingConverter encodingConverter, Object replacement,
                @Cached InlinedBranchProfile errorProfile,
                @Cached TruffleString.GetInternalByteArrayNode bytesNode,
                @Cached RubyStringLibrary libReplacement,
                @Cached ToStrNode toStrNode,
                @Bind Node node) {
            final var replacementAsString = toStrNode.execute(node, replacement);
            var tstring = libReplacement.getTString(node, replacementAsString);
            var encoding = libReplacement.getEncoding(node, replacementAsString);

            final InternalByteArray byteArray = bytesNode.execute(tstring, encoding.tencoding);
            int ret = setReplacement(encodingConverter.econv, byteArray.getArray(), byteArray.getOffset(),
                    byteArray.getLength(), encoding.getNameByteArray());

            if (ret == -1) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        getContext(node).getCoreExceptions().encodingUndefinedConversionError(node));
            }

            return replacementAsString;
        }

        @TruffleBoundary
        private static int setReplacement(EConv ec, byte[] bytes, int offset, int len, byte[] encodingName) {
            return ec.setReplacement(bytes, offset, len, encodingName);
        }

    }

}
