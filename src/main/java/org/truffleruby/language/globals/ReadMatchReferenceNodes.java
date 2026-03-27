/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.globals;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ReadMatchReferenceNodes extends RubyContextSourceNode {

    public static final class ReadNthMatchNode extends RubyContextSourceNode {
        @Child private RubyNode readMatchNode;
        @Child private DispatchNode getIndexNode;
        private final int index;

        private final ConditionProfile matchNilProfile = ConditionProfile.create();

        public ReadNthMatchNode(RubyNode readMatchNode, int index) {
            this.readMatchNode = readMatchNode;
            this.index = index;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object match = readMatchNode.execute(frame);

            if (matchNilProfile.profile(match == nil)) {
                return nil;
            }

            return callGetIndex(frame, match, index);
        }

        private Object callGetIndex(VirtualFrame frame, Object match, int index) {
            if (getIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIndexNode = insert(DispatchNode.create());
            }
            return getIndexNode.call(match, "[]", index);
        }

        @Override
        public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
            if (execute(frame) == nil) {
                return nil;
            } else {
                return FrozenStrings.GLOBAL_VARIABLE;
            }
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = new ReadNthMatchNode(
                    readMatchNode.cloneUninitialized(),
                    index);
            return copy.copyFlags(this);
        }

    }

    public static final class SetNamedVariablesMatchNode extends RubyContextSourceNode {
        @Child private RubyNode matchDataNode;
        @Child private RubyNode readMatchNode;
        @Children private final RubyNode[] setters;
        @Children private final RubyNode[] nilSetters;

        private final ConditionProfile matchNilProfile = ConditionProfile.create();

        public SetNamedVariablesMatchNode(
                RubyNode matchDataNode,
                RubyNode readMatchNode,
                RubyNode[] setters,
                RubyNode[] nilSetters) {
            this.matchDataNode = matchDataNode;
            this.readMatchNode = readMatchNode;
            this.setters = setters;
            this.nilSetters = nilSetters;

        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object matchResult = matchDataNode.execute(frame);
            Object match = readMatchNode.execute(frame);
            if (matchNilProfile.profile(match == nil)) {
                setNamedForNil(frame);
            } else {
                setNamedForNonNil(frame);
            }
            return matchResult;
        }

        @ExplodeLoop
        private void setNamedForNonNil(VirtualFrame frame) {
            for (int n = 0; n < setters.length; n++) {
                setters[n].execute(frame);
            }
        }

        @ExplodeLoop
        private void setNamedForNil(VirtualFrame frame) {
            for (int n = 0; n < setters.length; n++) {
                nilSetters[n].execute(frame);
            }
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = new SetNamedVariablesMatchNode(
                    matchDataNode.cloneUninitialized(),
                    readMatchNode.cloneUninitialized(),
                    cloneUninitialized(setters),
                    cloneUninitialized(nilSetters));
            return copy.copyFlags(this);
        }

    }
}
