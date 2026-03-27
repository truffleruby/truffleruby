/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.convert;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;

import java.nio.charset.StandardCharsets;

import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.TStringConstants;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

@NodeChild("value")
public abstract class ToStringNode extends FormatNode {

    protected final boolean convertNumbersToStrings;
    private final String conversionMethod;
    private final boolean inspectOnConversionFailure;
    protected final boolean specialClassBehaviour;

    @Child private DispatchNode toStrNode;
    @Child private DispatchNode toSNode;
    @Child private KernelNodes.ToSNode inspectNode;

    public ToStringNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure) {
        this(convertNumbersToStrings, conversionMethod, inspectOnConversionFailure, false);
    }

    public ToStringNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure,
            boolean specialClassBehaviour) {
        this.convertNumbersToStrings = convertNumbersToStrings;
        this.conversionMethod = conversionMethod;
        this.inspectOnConversionFailure = inspectOnConversionFailure;
        this.specialClassBehaviour = specialClassBehaviour;
    }

    public abstract Object executeToString(Object object);

    @Specialization(guards = "convertNumbersToStrings")
    RubyString toString(long value,
            @Cached TruffleString.FromLongNode fromLongNode) {
        var tstring = fromLongNode.execute(value, Encodings.US_ASCII.tencoding, true);
        return createString(tstring, Encodings.US_ASCII);
    }

    @TruffleBoundary
    @Specialization(guards = "convertNumbersToStrings")
    RubyString toString(double value,
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        return createString(fromJavaStringNode, Double.toString(value), Encodings.US_ASCII);
    }

    @TruffleBoundary
    @Specialization(guards = "specialClassBehaviour")
    Object toStringSpecialClass(RubyClass rubyClass,
            @Cached @Shared RubyStringLibrary libString) {
        if (rubyClass == getContext().getCoreLibrary().trueClass) {
            return createString(TStringConstants.TRUE, Encodings.US_ASCII);
        } else if (rubyClass == getContext().getCoreLibrary().falseClass) {
            return createString(TStringConstants.FALSE, Encodings.US_ASCII);
        } else if (rubyClass == getContext().getCoreLibrary().nilClass) {
            return createString(TStringConstants.NIL, Encodings.US_ASCII);
        } else {
            return toString(rubyClass, libString);
        }
    }

    @Specialization(guards = "argLibString.isRubyString(this, string)", limit = "1")
    Object toStringString(Object string,
            @Cached @Shared RubyStringLibrary libString,
            @Cached @Exclusive RubyStringLibrary argLibString) {
        if ("inspect".equals(conversionMethod)) {
            final Object value = getToStrNode().call(PRIVATE_RETURN_MISSING, string, conversionMethod);

            if (libString.isRubyString(this, value)) {
                return value;
            } else {
                throw new NoImplicitConversionException(string, "String");
            }
        }
        return string;
    }

    @Specialization
    Object toString(RubyArray array,
            @Cached @Shared RubyStringLibrary libString) {
        if (toSNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toSNode = insert(DispatchNode.create());
        }

        final Object value = toSNode.call(PRIVATE_RETURN_MISSING, array, "to_s");

        if (libString.isRubyString(this, value)) {
            return value;
        } else {
            throw new NoImplicitConversionException(array, "String");
        }
    }

    @Specialization(
            guards = { "isNotRubyString(object)", "!isRubyArray(object)", "!isForeignObject(object)" })
    Object toString(Object object,
            @Cached @Shared RubyStringLibrary libString) {
        final Object value = getToStrNode().call(PRIVATE_RETURN_MISSING, object, conversionMethod);

        if (libString.isRubyString(this, value)) {
            return value;
        }

        if (inspectOnConversionFailure) {
            if (inspectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                inspectNode = insert(KernelNodes.ToSNode.create());
            }

            return inspectNode.execute(object);
        } else {
            throw new NoImplicitConversionException(object, "String");
        }
    }

    @TruffleBoundary
    @Specialization(guards = "isForeignObject(object)")
    RubyString toStringForeign(Object object,
            @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        return createString(fromByteArrayNode,
                object.toString().getBytes(StandardCharsets.UTF_8),
                Encodings.UTF_8);
    }

    private DispatchNode getToStrNode() {
        if (toStrNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStrNode = insert(DispatchNode.create());
        }
        return toStrNode;
    }

}
