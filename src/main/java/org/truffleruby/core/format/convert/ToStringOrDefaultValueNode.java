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
package org.truffleruby.core.format.convert;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.language.Nil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild("value")
public abstract class ToStringOrDefaultValueNode extends FormatNode {

    protected final boolean convertNumbersToStrings;
    private final String conversionMethod;
    private final boolean inspectOnConversionFailure;
    private final Object valueOnNil;
    protected final boolean specialClassBehaviour;

    @Child private ToStringNode toStringNode;

    public ToStringOrDefaultValueNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure,
            Object valueOnNil) {
        this(convertNumbersToStrings, conversionMethod, inspectOnConversionFailure, valueOnNil, false);
    }

    public ToStringOrDefaultValueNode(
            boolean convertNumbersToStrings,
            String conversionMethod,
            boolean inspectOnConversionFailure,
            Object valueOnNil,
            boolean specialClassBehaviour) {
        this.convertNumbersToStrings = convertNumbersToStrings;
        this.conversionMethod = conversionMethod;
        this.inspectOnConversionFailure = inspectOnConversionFailure;

        this.valueOnNil = valueOnNil;
        this.specialClassBehaviour = specialClassBehaviour;
    }

    public abstract Object executeToString(Object object);

    @Specialization
    Object toStringNil(Nil nil) {
        return valueOnNil;
    }

    @Specialization(guards = "!isNil(value)")
    Object toString(Object value) {
        if (toStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStringNode = insert(ToStringNodeGen.create(convertNumbersToStrings, conversionMethod,
                    inspectOnConversionFailure, specialClassBehaviour, null));
        }

        return toStringNode.executeToString(value);
    }

}
