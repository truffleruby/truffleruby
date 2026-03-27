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
package org.truffleruby.core.format.read.array;

import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.convert.ToStringNode;
import org.truffleruby.core.format.convert.ToStringNodeGen;
import org.truffleruby.core.format.read.SourceNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@NodeChild(value = "source", type = SourceNode.class)
@ImportStatic(ArrayGuards.class)
public abstract class ReadStringNode extends FormatNode {

    private final boolean convertNumbersToStrings;
    private final String conversionMethod;
    private final boolean inspectOnConversionFailure;
    private final boolean specialClassBehaviour;

    @Child private ToStringNode toStringNode;

    public ReadStringNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure) {
        this(convertNumbersToStrings, conversionMethod, inspectOnConversionFailure, false);
    }

    public ReadStringNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure,
            boolean specialClassBehaviour) {
        this.convertNumbersToStrings = convertNumbersToStrings;
        this.conversionMethod = conversionMethod;
        this.inspectOnConversionFailure = inspectOnConversionFailure;
        this.specialClassBehaviour = specialClassBehaviour;
    }

    @Specialization(limit = "storageStrategyLimit()")
    Object read(VirtualFrame frame, Object source,
            @CachedLibrary("source") ArrayStoreLibrary sources) {
        return readAndConvert(sources.read(source, advanceSourcePosition(frame)));
    }

    private Object readAndConvert(Object value) {
        if (toStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStringNode = insert(ToStringNodeGen.create(
                    convertNumbersToStrings,
                    conversionMethod,
                    inspectOnConversionFailure,
                    specialClassBehaviour,
                    null));
        }

        return toStringNode.executeToString(value);
    }

}
