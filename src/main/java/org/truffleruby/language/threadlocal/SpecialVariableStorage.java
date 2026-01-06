/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.threadlocal;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.parser.FrameDescriptorInfo;

@ExportLibrary(InteropLibrary.class)
public final class SpecialVariableStorage implements TruffleObject {

    // This slot is not needed for all frames and could be lazily added via auxiliary slots,
    // but that would add a lot of complexity and significantly increase the footprint of frames which do need it.
    public static final int SLOT_INDEX = 1;
    public static final String SLOT_NAME = Layouts.TEMP_PREFIX + "$~_";
    public static final String ASSUMPTION_NAME = "does not need SpecialVariableStorage";

    public static Object get(Frame frame) {
        assert hasSpecialVariableStorageSlot(frame);
        return frame.getObject(SLOT_INDEX);
    }

    public static void set(Frame frame, SpecialVariableStorage variables) {
        assert hasSpecialVariableStorageSlot(frame);
        frame.setObject(SLOT_INDEX, variables);
    }

    public static Assumption getAssumption(FrameDescriptor descriptor) {
        return FrameDescriptorInfo.of(descriptor).getSpecialVariableAssumption();
    }

    public static boolean isSpecialVariableAssumption(Assumption assumption) {
        return assumption.getName() == ASSUMPTION_NAME;
    }

    public static boolean hasSpecialVariableStorageSlot(Frame frame) {
        assert RubyArguments.getDeclarationFrame(frame) == null;
        return hasSpecialVariableStorageSlot(frame.getFrameDescriptor());
    }

    private static boolean hasSpecialVariableStorageSlot(FrameDescriptor descriptor) {
        assert SLOT_INDEX < descriptor.getNumberOfSlots();
        assert descriptor.getSlotName(SLOT_INDEX) == SLOT_NAME;
        return isSpecialVariableAssumption(getAssumption(descriptor));
    }

    /** $~ */
    private ThreadAndFrameLocalStorage lastMatch;
    /** $_ */
    private ThreadAndFrameLocalStorage lastLine;

    public Object getLastMatch(Node node, InlinedConditionProfile unsetProfile,
            InlinedConditionProfile sameThreadProfile) {
        if (unsetProfile.profile(node, lastMatch == null)) {
            return Nil.INSTANCE;
        } else {
            return lastMatch.get(node, sameThreadProfile);
        }
    }

    public void setLastMatch(Node node, Object value, RubyContext context, InlinedConditionProfile unsetProfile,
            InlinedConditionProfile sameThreadProfile) {
        if (unsetProfile.profile(node, lastMatch == null)) {
            lastMatch = new ThreadAndFrameLocalStorage(context);
        }
        lastMatch.set(node, value, sameThreadProfile);
    }

    public Object getLastLine(Node node, InlinedConditionProfile unsetProfile,
            InlinedConditionProfile sameThreadProfilew) {
        if (unsetProfile.profile(node, lastLine == null)) {
            return Nil.INSTANCE;
        } else {
            return lastLine.get(node, sameThreadProfilew);
        }
    }

    public void setLastLine(Node node, Object value, RubyContext context, InlinedConditionProfile unsetProfile,
            InlinedConditionProfile sameThreadProfile) {
        if (unsetProfile.profile(node, lastLine == null)) {
            lastLine = new ThreadAndFrameLocalStorage(context);
        }
        lastLine.set(node, value, sameThreadProfile);
    }

    @TruffleBoundary
    @ExportMessage
    protected String toDisplayString(boolean allowSideEffects) {
        final InteropLibrary interop = InteropLibrary.getUncached();
        final InlinedConditionProfile profile = InlinedConditionProfile.getUncached();
        String result = "SpecialVariableStorage($~: ";
        if (lastMatch != null) {
            try {
                result += interop.asString(interop.toDisplayString(lastMatch.get(null, profile), allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw TranslateInteropExceptionNode.executeUncached(e);
            }
        } else {
            result += "nil";
        }

        result += ", $_: ";
        if (lastLine != null) {
            try {
                result += interop.asString(interop.toDisplayString(lastLine.get(null, profile), allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw TranslateInteropExceptionNode.executeUncached(e);
            }
        } else {
            result += "nil";
        }
        return result + ")";
    }

    @Override
    public String toString() {
        return toDisplayString(false);
    }

}
