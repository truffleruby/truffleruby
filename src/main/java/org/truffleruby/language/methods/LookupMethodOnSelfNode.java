/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.objects.MetaClassNode;

@GenerateUncached
public abstract class LookupMethodOnSelfNode extends RubyBaseNode {

    @NeverDefault
    public static LookupMethodOnSelfNode create() {
        return LookupMethodOnSelfNodeGen.create();
    }

    public InternalMethod lookupProtected(Frame frame, Object self, String name) {
        return execute(frame, self, name, DispatchConfiguration.PROTECTED);
    }

    public InternalMethod lookupIgnoringVisibility(Frame frame, Object self, String name) {
        return execute(frame, self, name, DispatchConfiguration.PRIVATE);
    }

    public abstract InternalMethod execute(Frame frame, Object self, String name, DispatchConfiguration config);

    @Specialization
    InternalMethod doLookup(Frame frame, Object self, String name, DispatchConfiguration config,
            @Cached MetaClassNode metaClassNode,
            @Cached LookupMethodNode lookupMethod) {
        final RubyClass metaclass = metaClassNode.execute(this, self);
        return lookupMethod.execute(frame, metaclass, name, config);
    }
}
