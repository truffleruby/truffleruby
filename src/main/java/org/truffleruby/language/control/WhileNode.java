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
package org.truffleruby.language.control;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;

public final class WhileNode extends RubyContextSourceNode {

    @Child private LoopNode loopNode;

    public WhileNode(RepeatingNode repeatingNode) {
        loopNode = Truffle.getRuntime().createLoopNode(repeatingNode);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        loopNode.execute(frame);
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var repeatingNode = (WhileRepeatingBaseNode) loopNode.getRepeatingNode();
        var copy = new WhileNode(repeatingNode.cloneUninitialized());
        return copy.copyFlags(this);
    }

    private abstract static class WhileRepeatingBaseNode extends RubyBaseNode implements RepeatingNode {

        @Child protected RubyNode condition;
        @Child protected RubyNode body;

        public WhileRepeatingBaseNode(RubyNode condition, RubyNode body) {
            this.condition = condition;
            this.body = body;
        }

        protected abstract boolean execute(VirtualFrame frame);

        @Override
        public final boolean executeRepeating(VirtualFrame frame) {
            return execute(frame);
        }

        @Override
        public final Object executeRepeatingWithValue(VirtualFrame frame) {
            if (executeRepeating(frame)) {
                return CONTINUE_LOOP_STATUS;
            } else {
                return BREAK_LOOP_STATUS;
            }
        }

        @Override
        public String toString() {
            return "while loop at " + RubyLanguage.fileLineRange(getEncapsulatingSourceSection());
        }

        public abstract WhileRepeatingBaseNode cloneUninitialized();
    }

    public abstract static class WhileRepeatingNode extends WhileRepeatingBaseNode {

        public WhileRepeatingNode(RubyNode condition, RubyNode body) {
            super(condition, body);
        }

        @Specialization
        boolean doRepeating(VirtualFrame frame,
                @Cached BooleanCastNode booleanCastNode,
                @Cached InlinedBranchProfile redoUsed,
                @Cached InlinedBranchProfile nextUsed) {
            var conditionAsBoolean = booleanCastNode.execute(this, condition.execute(frame));
            if (!conditionAsBoolean) {
                return false;
            }

            while (true) { // for redo
                try {
                    body.executeVoid(frame);
                    return true;
                } catch (NextException e) {
                    nextUsed.enter(this);
                    return true;
                } catch (RedoException e) {
                    // Just continue in the while(true) loop.
                    redoUsed.enter(this);
                    TruffleSafepoint.poll(this);
                }
            }
        }

        @Override
        public WhileRepeatingBaseNode cloneUninitialized() {
            return WhileNodeFactory.WhileRepeatingNodeGen.create(
                    condition.cloneUninitialized(),
                    body.cloneUninitialized());
        }

    }

    public abstract static class DoWhileRepeatingNode extends WhileRepeatingBaseNode {

        public DoWhileRepeatingNode(RubyNode condition, RubyNode body) {
            super(condition, body);
        }

        @Specialization
        boolean doRepeating(VirtualFrame frame,
                @Cached BooleanCastNode booleanCastNode,
                @Cached InlinedBranchProfile redoUsed,
                @Cached InlinedBranchProfile nextUsed) {
            try {
                body.executeVoid(frame);
            } catch (NextException e) {
                nextUsed.enter(this);
            } catch (RedoException e) {
                // Just continue to next iteration without executing the condition.
                redoUsed.enter(this);
                return true;
            }

            return booleanCastNode.execute(this, condition.execute(frame));
        }

        @Override
        public WhileRepeatingBaseNode cloneUninitialized() {
            return WhileNodeFactory.DoWhileRepeatingNodeGen.create(
                    condition.cloneUninitialized(),
                    body.cloneUninitialized());
        }

    }

}
