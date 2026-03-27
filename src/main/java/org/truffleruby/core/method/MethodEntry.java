/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.method;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.PerformanceWarningNode;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.Assumption;
import org.truffleruby.language.methods.SharedMethodInfo;

import java.util.Objects;

public final class MethodEntry {

    public static final String CORE_METHOD_IS_NOT_OVERRIDDEN = "core method is not overridden:";

    private final Assumption assumption;
    private final InternalMethod method;

    public MethodEntry(InternalMethod method) {
        this(method, Assumption.create("method is not overridden:"));
    }

    public MethodEntry(InternalMethod method, Assumption assumption) {
        assert method != null;
        this.assumption = Objects.requireNonNull(assumption);
        this.method = method;
    }

    public MethodEntry() {
        this.assumption = Assumption.create("method is not defined:");
        this.method = null;
    }

    public MethodEntry withNewAssumption() {
        if (method != null) {
            return new MethodEntry(method);
        } else {
            return new MethodEntry();
        }
    }

    public Assumption getAssumption() {
        return assumption;
    }

    public InternalMethod getMethod() {
        return method;
    }

    public void invalidate(RubyContext context, RubyModule module, String methodName, Node node) {
        assumption.invalidate(SharedMethodInfo.moduleAndMethodName(module, methodName));

        if (assumption.getName() == CORE_METHOD_IS_NOT_OVERRIDDEN) {
            PerformanceWarningNode.warn(context,
                    StringUtils.format("Redefining '%s#%s' disables interpreter and JIT optimizations",
                            module.getName(),
                            methodName),
                    node);
        }

    }

}
