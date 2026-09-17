/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Iterator;

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
import com.oracle.truffle.api.object.DynamicObject;
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

    /** A worklist of objects to share. Objects which do not need sharing are filtered out on
     * {@link ShareQueue#add(Object)}, without allocating a Set of adjacent objects per visited object (already-shared
     * objects are skipped when popped, thanks to the shared Shape flag set by
     * {@link SharedObjects#share(RubyDynamicObject)}, so no visited Set is needed either). Implements Set so it can be
     * passed to {@link ObjectGraph} methods. */
    private static final class ShareQueue extends AbstractSet<Object> {
        final ArrayDeque<RubyDynamicObject> queue = new ArrayDeque<>();

        @Override
        public boolean add(Object value) {
            if (value instanceof RubyDynamicObject object && !isShared(object)) {
                queue.push(object);
                return true;
            }
            return false;
        }

        @Override
        public Iterator<Object> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }
    }

    private static void shareContextRoots(RubyLanguage language, RubyContext context) {
        final ShareQueue queue = new ShareQueue();

        // Share global variables (including new ones)
        for (Object object : context.getCoreLibrary().globalVariables.objectGraphValues()) {
            queue.add(object);
        }

        // Share the native configuration
        for (Object object : context.getNativeConfiguration().objectGraphValues()) {
            queue.add(object);
        }

        // Share all named modules and constants
        queue.add(context.getCoreLibrary().objectClass);

        // Share all threads since they are accessible via Thread.list
        for (RubyThread thread : context.getThreadManager().iterateThreads()) {
            queue.add(thread);
        }

        long t0 = System.currentTimeMillis();
        shareObjects(queue);
        if (language.options.SHARED_OBJECTS_DEBUG) {
            RubyLanguage.LOGGER.info("sharing roots took " + (System.currentTimeMillis() - t0) + " ms");
        }
    }

    public static void shareBlockAndArguments(RubyLanguage language, RubyProc block, Object[] args, String info) {
        if (language.options.SHARED_OBJECTS_DEBUG) {
            RubyLanguage.LOGGER.info("sharing block and arguments of " + info);
        }

        final ShareQueue queue = new ShareQueue();
        ObjectGraph.getObjectsInFrame(block.declarationFrame, queue);
        ObjectGraph.addProperty(queue, args);

        shareObjects(queue);
    }

    private static void shareObjects(ShareQueue queue) {
        final ArrayDeque<RubyDynamicObject> deque = queue.queue;
        RubyDynamicObject object;
        while ((object = deque.poll()) != null) {
            if (share(object)) {
                ObjectGraph.getAdjacentObjects(object, queue);
            }
        }
    }

    @TruffleBoundary
    private static void shareObject(RubyDynamicObject value) {
        final ShareQueue queue = new ShareQueue();
        queue.add(value);
        shareObjects(queue);
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
        DynamicObject.UpdateShapeNode.getUncached().execute(object);
        DynamicObject.MarkSharedNode.getUncached().execute(object);

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
            DynamicObject.UpdateShapeNode.getUncached().execute(classVariables);
            DynamicObject.MarkSharedNode.getUncached().execute(classVariables);
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
        final ShareQueue queue = new ShareQueue();
        ObjectGraph.getAdjacentObjects(object, queue);
        shareObjects(queue);
    }

}
