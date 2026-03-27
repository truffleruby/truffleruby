/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Contains code modified from JRuby's RubyComparable.java
 *
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2006 Thomas E Enebo <enebo@acm.org>
 */

package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.LogicalClassNode;

/** This is a port of MRI's rb_cmpint, as taken from RubyComparable and broken out into specialized nodes. */
@GenerateInline
@GenerateCached(false)
public abstract class CmpIntNode extends RubyBaseNode {

    public abstract int executeCmpInt(Node node, Object cmpResult, Object a, Object b);

    @Specialization
    static int cmpInt(int value, Object receiver, Object other) {
        return value;
    }

    @Specialization
    static int cmpLong(long value, Object receiver, Object other) {
        if (value > 0) {
            return 1;
        }

        if (value < 0) {
            return -1;
        }

        return 0;
    }

    @Specialization
    static int cmpBignum(RubyBignum value, Object receiver, Object other) {
        return value.value.signum();
    }

    @Specialization
    static int cmpNil(Node node, Nil nil, Object receiver, Object other) {
        throw new RaiseException(getContext(node),
                coreExceptions(node).argumentError(formatMessage(receiver, other), node));
    }

    @TruffleBoundary
    private static String formatMessage(Object receiver, Object other) {
        return StringUtils.format(
                "comparison of %s with %s failed",
                LogicalClassNode.getUncached().execute(receiver).fields.getName(),
                LogicalClassNode.getUncached().execute(other).fields.getName());
    }

    @Specialization(guards = { "!isRubyInteger(value)", "!isNil(value)" })
    static int cmpObject(Node node, Object value, Object receiver, Object other,
            @Cached(inline = false) DispatchNode gtNode,
            @Cached(inline = false) DispatchNode ltNode,
            @Cached BooleanCastNode gtCastNode,
            @Cached BooleanCastNode ltCastNode) {

        if (gtCastNode.execute(node, gtNode.call(value, ">", 0))) {
            return 1;
        }

        if (ltCastNode.execute(node, ltNode.call(value, "<", 0))) {
            return -1;
        }

        return 0;
    }
}
