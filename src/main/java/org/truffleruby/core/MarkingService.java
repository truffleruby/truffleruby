/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import java.util.ArrayList;

import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.cext.CapturedException;
import org.truffleruby.cext.ValueWrapper;
import org.truffleruby.core.array.ArrayUtils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/** Class to provide GC marking and other facilities to keep objects alive for native extensions.
 *
 * Native extensions expect objects on the stack to be kept alive even when they have been stored in native structures
 * on the stack (e.g. pg keeps the VALUE of a ruby array in a structure on the stack, and places other objects in that
 * array to keep them alive). They also expect structs in objects with custom mark functions to keep marked objects
 * alive.
 *
 * Since we are not running on a VM that allows us to add custom mark functions to our garbage collector we keep objects
 * alive in 2 ways. Any object converted to a native handle can be kept alive by executing a
 * {@link MarkingServiceNodes.KeepAliveNode}. This will add the object to two lists, a list of all objects converted to
 * native during this call to a C extension function which will be popped when we return to Ruby code, and a fixed-sized
 * list of objects converted to native handles. When the latter of these two lists is full all mark functions will be
 * run the next time an object is added.
 *
 * Marker references only keep a week reference to their owning object to ensure they don't themselves stop the object
 * from being garbage collected. */
public final class MarkingService {

    public static interface MarkerAction {
        public abstract void mark(Object owner);
    }

    protected static final class ExtensionCallStackEntry {

        private final ExtensionCallStackEntry previous;
        ValueWrapper preservedObject;
        @SuppressFBWarnings("UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR") ArrayList<ValueWrapper> preservedObjectList;
        private final boolean keywordsGiven;
        private Object specialVariables;
        private final Object block;
        private CapturedException capturedException;
        private ValueWrapper markOnExitObject;
        private ArrayList<ValueWrapper> markOnExitObjects;
        private Object[] marks = null;
        private int marksIndex = 0;

        private ExtensionCallStackEntry(
                ExtensionCallStackEntry previous,
                boolean keywordsGiven,
                Object specialVariables,
                Object block) {
            this.previous = previous;
            this.keywordsGiven = keywordsGiven;
            this.specialVariables = specialVariables;
            this.block = block;
            this.capturedException = null;
        }
    }

    public static final class ExtensionCallStack {

        ExtensionCallStackEntry current;

        public ExtensionCallStack(Object specialVariables, Object block) {
            current = new ExtensionCallStackEntry(null, false, specialVariables, block);
        }

        public boolean hasKeptObjects() {
            return current.preservedObject != null;
        }

        public boolean hasSingleKeptObject() {
            return current.preservedObject != null && current.preservedObjectList == null;
        }

        public boolean isPreservedObjectListInitialized() {
            return current.preservedObjectList != null;
        }

        public void markOnExitObject(ValueWrapper value) {
            if (current.markOnExitObject == null) {
                current.markOnExitObject = value;
            } else if (current.markOnExitObject != value) {
                markOnExitObjectOnList(value);
            }
        }

        @TruffleBoundary
        private void markOnExitObjectOnList(ValueWrapper value) {
            if (current.markOnExitObjects == null) {
                current.markOnExitObjects = new ArrayList<>();
                current.markOnExitObjects.add(current.markOnExitObject);
            }
            current.markOnExitObjects.add(value);
        }

        public ArrayList<ValueWrapper> getMarkOnExitObjects() {
            assert current.previous != null;
            assert current.markOnExitObjects != null;

            return current.markOnExitObjects;
        }

        public boolean hasMarkObjects() {
            return current.markOnExitObject != null;
        }

        public boolean hasSingleMarkObject() {
            return current.markOnExitObject != null && current.markOnExitObjects == null;
        }

        public ValueWrapper getSingleMarkObject() {
            return current.markOnExitObject;
        }

        public void pop() {
            current = current.previous;
        }

        public void push(boolean keywordsGiven, Object specialVariables, Object block) {
            current = new ExtensionCallStackEntry(current, keywordsGiven, specialVariables, block);
        }

        public boolean areKeywordsGiven() {
            return current.keywordsGiven;
        }

        public Object getSpecialVariables() {
            return current.specialVariables;
        }

        public void setSpecialVariables(Object specialVariables) {
            current.specialVariables = specialVariables;
        }

        public CapturedException getException() {
            return current.capturedException;
        }

        public void setException(CapturedException capturedException) {
            current.capturedException = capturedException;
        }

        public Object getBlock() {
            return current.block;
        }
    }

    public void startMarking(ExtensionCallStack stack, Object[] oldMarks) {
        if (oldMarks == null) {
            stack.current.marks = ArrayUtils.EMPTY_ARRAY;
        } else {
            stack.current.marks = oldMarks;
        }
        stack.current.marksIndex = 0;
    }

    @TruffleBoundary
    public void addMark(ExtensionCallStack stack, Object obj) {
        if (stack.current.marks.length == stack.current.marksIndex) {
            Object[] oldMarks = stack.current.marks;
            stack.current.marks = new Object[Integer.max(oldMarks.length * 2, 1)];
            System.arraycopy(oldMarks, 0, stack.current.marks, 0, oldMarks.length);
        }
        stack.current.marks[stack.current.marksIndex] = obj;
        stack.current.marksIndex++;
    }

    @TruffleBoundary
    public Object[] finishMarking(ExtensionCallStack stack) {
        if (stack.current.marksIndex != stack.current.marks.length) {
            for (int i = stack.current.marksIndex; i < stack.current.marks.length; i++) {
                stack.current.marks[i] = null;
            }
        }
        Object[] result = stack.current.marks;
        stack.current.marks = null;
        return result;
    }

}
