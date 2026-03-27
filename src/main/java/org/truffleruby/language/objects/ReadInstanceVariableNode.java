/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.locals.ReadFrameSlotNode;

public final class ReadInstanceVariableNode extends RubyContextSourceNode {

    private final String name;

    @Child private ReadFrameSlotNode readSelfSlotNode;
    @Child private DynamicObjectLibrary objectLibrary;

    private final ConditionProfile objectProfile = ConditionProfile.create();

    public ReadInstanceVariableNode(String name) {
        this.name = name;
        this.readSelfSlotNode = SelfNode.createReadSelfFrameSlotNode();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object self = SelfNode.readSelf(frame, readSelfSlotNode);

        if (objectProfile.profile(self instanceof RubyDynamicObject)) {
            final DynamicObjectLibrary objectLibrary = getObjectLibrary();
            final RubyDynamicObject dynamicObject = (RubyDynamicObject) self;
            return objectLibrary.getOrDefault(dynamicObject, name, nil);
        } else {
            return nil;
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final Object self = SelfNode.readSelf(frame, readSelfSlotNode);

        if (objectProfile.profile(self instanceof RubyDynamicObject)) {
            final DynamicObjectLibrary objectLibrary = getObjectLibrary();
            final RubyDynamicObject dynamicObject = (RubyDynamicObject) self;
            if (objectLibrary.containsKey(dynamicObject, name)) {
                return FrozenStrings.INSTANCE_VARIABLE;
            } else {
                return nil;
            }
        } else {
            return false;
        }
    }

    private DynamicObjectLibrary getObjectLibrary() {
        if (objectLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            objectLibrary = insert(
                    DynamicObjectLibrary
                            .getFactory()
                            .createDispatched(getLanguage().options.INSTANCE_VARIABLE_CACHE));
        }
        return objectLibrary;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadInstanceVariableNode(name);
        return copy.copyFlags(this);
    }

}
