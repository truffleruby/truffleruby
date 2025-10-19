/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.SharedArrayStorage;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.ConcurrentHashStore;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.language.objects.classvariables.ClassVariableStorage;

public final class SharedObjects {

    private final RubyContext context;
    // No need for volatile since we change this before starting the 2nd Thread
    private boolean sharing = false;

    public SharedObjects(RubyContext context) {
        this.context = context;
    }

    public boolean isSharing() {
        return sharing;
    }

    public void startSharing(RubyLanguage language, String reason) {
        if (!sharing) {
            sharing = true;
            if (language.options.SHARED_OBJECTS_DEBUG) {
                RubyLanguage.LOGGER.info("starting sharing due to " + reason);
            }
            shareContextRoots(language, context);
        }
    }

    private static void shareContextRoots(RubyLanguage language, RubyContext context) {
        final Deque<Object> stack = new ArrayDeque<>();

        // Share global variables (including new ones)
        for (Object object : context.getCoreLibrary().globalVariables.objectGraphValues()) {
            stack.push(object);
        }

        // Share the native configuration
        for (Object object : context.getNativeConfiguration().objectGraphValues()) {
            stack.push(object);
        }

        // Share all named modules and constants
        stack.push(context.getCoreLibrary().objectClass);

        // Share all threads since they are accessible via Thread.list
        for (RubyThread thread : context.getThreadManager().iterateThreads()) {
            stack.push(thread);
        }

        long t0 = System.currentTimeMillis();
        shareObjects(stack);
        if (language.options.SHARED_OBJECTS_DEBUG) {
            RubyLanguage.LOGGER.info("sharing roots took " + (System.currentTimeMillis() - t0) + " ms");
        }
    }

    public static void shareBlockAndArguments(RubyLanguage language, RubyProc block, Object[] args, String info) {
        if (language.options.SHARED_OBJECTS_DEBUG) {
            RubyLanguage.LOGGER.info("sharing block and arguments of " + info);
        }

        final Set<Object> objects = ObjectGraph.newObjectSet();
        ObjectGraph.getObjectsInFrame(block.declarationFrame, objects);
        ObjectGraph.addProperty(objects, args);

        final Deque<Object> stack = new ArrayDeque<>(objects);
        shareObjects(stack);
    }

    private static void shareObjects(Deque<Object> stack) {
        while (!stack.isEmpty()) {
            final Object object = stack.pop();
            assert ObjectGraph.isRubyObject(object) : object;

            if (object instanceof RubyDynamicObject) {
                if (share((RubyDynamicObject) object)) {
                    stack.addAll(ObjectGraph.getAdjacentObjects((RubyDynamicObject) object));
                }
            }
        }
    }

    @TruffleBoundary
    private static void shareObject(RubyDynamicObject value) {
        final Deque<Object> stack = new ArrayDeque<>();
        stack.add(value);
        shareObjects(stack);
    }

    /** Callers of this should be careful, this method will return true for RubySymbol even if the
     * SHARED_OBJECTS_ENABLED option is false. */
    public static boolean isShared(Object object) {
        return object instanceof ImmutableRubyObject ||
                (object instanceof RubyDynamicObject && isShared((RubyDynamicObject) object));
    }

    public static boolean isShared(RubyDynamicObject object) {
        return object.getShape().isShared();
    }

    public static boolean assertPropagateSharing(RubyDynamicObject source, Object value) {
        if (isShared(source) && value instanceof RubyDynamicObject object) {
            return isShared(object);
        } else {
            return true;
        }
    }

    public static void writeBarrier(RubyLanguage language, Object value) {
        if (language.options.SHARED_OBJECTS_ENABLED && value instanceof RubyDynamicObject object && !isShared(object)) {
            shareObject(object);
            assert !(value instanceof RubyArray array) || validateArray(array);
        }
    }

    private static boolean validateArray(RubyArray value) {
        return ((SharedArrayStorage) value.getStore()).allElementsShared(value.size);
    }

    public static void propagate(RubyLanguage language, RubyDynamicObject source, Object value) {
        if (isShared(source)) {
            writeBarrier(language, value);
        }
    }

    private static boolean share(RubyDynamicObject object) {
        if (isShared(object)) {
            return false;
        }

        // GR-49349: we must updateShape() before markShared()
        DynamicObjectLibrary.getUncached().updateShape(object);
        DynamicObjectLibrary.getUncached().markShared(object);

        onShareHook(object);
        return true;
    }

    public static void onShareHook(RubyDynamicObject object) {
        // The object Shape is already marked as shared before the hook is run,
        // which is necessary to avoid infinite recursion if the object
        // references itself directly or indirectly.
        assert object.getShape().isShared();

        if (object instanceof RubyModule) {
            // We want to share ClassVariableStorage but not expose it to ObjectSpace.reachable_objects_from
            final ClassVariableStorage classVariables = ((RubyModule) object).fields.getClassVariables();
            // GR-49349: we must updateShape() before markShared()
            DynamicObjectLibrary.getUncached().updateShape(classVariables);
            DynamicObjectLibrary.getUncached().markShared(classVariables);
        } else if (object instanceof RubyArray array) {
            array.setStore(ArrayStoreLibrary.getUncached().makeShared(array.getStore(), array.size));
        } else if (object instanceof RubyHash hash) {
            ConcurrentHashStore.convertFromOtherStrategy(hash);
        }
    }

    @TruffleBoundary
    public static void shareInternalFields(RubyDynamicObject object) {
        onShareHook(object);
        // This will also share user fields, but that's OK
        final Deque<Object> stack = new ArrayDeque<>(ObjectGraph.getAdjacentObjects(object));
        shareObjects(stack);
    }

}
