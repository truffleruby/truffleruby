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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.CantConvertException;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.Nil;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;

@NodeChild("value")
public abstract class ToLongNode extends FormatNode {

    protected final boolean errorIfNeedsConversion;

    public static ToLongNode create(boolean errorIfNeedsConversion) {
        return ToLongNodeGen.create(errorIfNeedsConversion, null);
    }

    public ToLongNode(boolean errorIfNeedsConversion) {
        this.errorIfNeedsConversion = errorIfNeedsConversion;
    }

    public abstract long executeToLong(VirtualFrame frame, Object object);

    @Specialization
    long toLong(boolean object) {
        throw new NoImplicitConversionException(object, "Integer");
    }

    @Specialization
    long toLong(int object) {
        return object;
    }

    @Specialization
    long toLong(long object) {
        return object;
    }

    @Specialization
    long toLong(RubyBignum object) {
        // A truncated value is exactly what we want
        return BigIntegerOps.longValue(object);
    }

    @Specialization
    long toLongNil(Nil nil) {
        throw new NoImplicitConversionException(nil, "Integer");
    }

    @Specialization(
            guards = { "errorIfNeedsConversion", "!isBoolean(object)", "!isRubyInteger(object)", "!isNil(object)" })
    long toLong(VirtualFrame frame, Object object) {
        throw new CantConvertException("can't convert Object to Integer");
    }

    @Specialization(
            guards = { "!errorIfNeedsConversion", "!isBoolean(object)", "!isRubyInteger(object)", "!isNil(object)" })
    static long toLong(VirtualFrame frame, Object object,
            @Cached DispatchNode toIntNode,
            @Cached("create(true)") ToLongNode redoNode,
            @Cached InlinedBranchProfile noConversionAvailable,
            @Bind Node node) {

        Object result = toIntNode.call(PRIVATE_RETURN_MISSING, object, "to_int");
        if (result == DispatchNode.MISSING) {
            noConversionAvailable.enter(node);
            throw new CantConvertException("can't convert Object to Integer");
        }
        return redoNode.executeToLong(frame, result);
    }
}
