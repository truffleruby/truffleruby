/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.dispatch.DispatchNode;

import java.util.concurrent.TimeUnit;

@GenerateInline
@GenerateCached(false)
public abstract class DurationToNanoSecondsNode extends RubyBaseNode {

    public abstract long execute(Node node, Object duration);

    @Specialization
    static long noDuration(NotProvided duration) {
        return Long.MAX_VALUE;
    }

    @Specialization
    static long duration(Node node, long duration,
            @Cached @Exclusive InlinedConditionProfile durationLessThanZeroProfile) { // @Exclusive to fix truffle-interpreted-performance warning
        return validate(node, TimeUnit.SECONDS.toNanos(duration), durationLessThanZeroProfile);
    }

    @Specialization
    static long duration(Node node, double duration,
            @Cached @Exclusive InlinedConditionProfile durationLessThanZeroProfile) {
        return validate(node, (long) (duration * 1e9), durationLessThanZeroProfile);
    }

    @Fallback
    static long duration(Node node, Object duration,
            @Cached(inline = false) DispatchNode durationToNanoSeconds,
            @Cached @Exclusive InlinedConditionProfile durationLessThanZeroProfile,
            @Cached ToLongNode toLongNode) {
        final Object nanoseconds = durationToNanoSeconds.call(
                coreLibrary(node).truffleKernelOperationsModule,
                "convert_duration_to_nanoseconds",
                duration);
        return validate(node, toLongNode.execute(node, nanoseconds), durationLessThanZeroProfile);
    }

    private static long validate(Node node, long durationInNanos, InlinedConditionProfile durationLessThanZeroProfile) {
        if (durationLessThanZeroProfile.profile(node, durationInNanos < 0)) {
            throw new RaiseException(getContext(node), coreExceptions(node).argumentErrorTimeIntervalPositive(node));
        }
        return durationInNanos;
    }
}
