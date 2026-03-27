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
package org.truffleruby.core.array;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.SharedArrayStorage;

public abstract class ArrayHelpers {

    private static boolean assertValidElements(Object store, int size) {
        return !(store instanceof Object[]) || ArrayUtils.assertValidElements((Object[]) store, 0, size);
    }

    public static RubyArray createArray(RubyContext context, RubyLanguage language, Object store, int size) {
        assert !(store instanceof Object[]) || store.getClass() == Object[].class;
        assert !(store instanceof SharedArrayStorage);
        assert assertValidElements(store, size);
        return new RubyArray(context.getCoreLibrary().arrayClass, language.arrayShape, store, size);
    }

    public static RubyArray createArray(RubyContext context, RubyLanguage language, int[] store) {
        return createArray(context, language, store, store.length);
    }

    public static RubyArray createArray(RubyContext context, RubyLanguage language, long[] store) {
        return createArray(context, language, store, store.length);
    }

    public static RubyArray createArray(RubyContext context, RubyLanguage language, Object[] store) {
        assert store.getClass() == Object[].class;
        return createArray(context, language, store, store.length);
    }

    public static RubyArray createEmptyArray(RubyContext context, RubyLanguage language) {
        return new RubyArray(
                context.getCoreLibrary().arrayClass,
                language.arrayShape,
                ArrayStoreLibrary.initialStorage(false),
                0);
    }

    /** Returns a Java array of the narrowest possible type holding {@code object}. */
    public static Object specializedJavaArrayOf(ArrayBuilderNode builder, Object object) {
        final ArrayBuilderNode.BuilderState state = builder.start(1);
        builder.appendValue(state, 0, object);
        return builder.finish(state, 1);
    }

    /** Returns a Java array of the narrowest possible type holding the {@code objects}. */
    public static Object specializedJavaArrayOf(ArrayBuilderNode builder, Object... objects) {
        final ArrayBuilderNode.BuilderState state = builder.start(objects.length);
        for (Object object : objects) {
            builder.appendValue(state, 0, object);
        }
        return builder.finish(state, objects.length);
    }

    /** Returns a Ruby array backed by a store of the narrowest possible type, holding {@code object}. */
    public static RubyArray specializedRubyArrayOf(RubyContext context, RubyLanguage language, ArrayBuilderNode builder,
            Object object) {
        return createArray(context, language, specializedJavaArrayOf(builder, object), 1);
    }

    /** Returns a Ruby array backed by a store of the narrowest possible type, holding the {@code objects}. */
    public static RubyArray specializedRubyArrayOf(RubyContext context, RubyLanguage language, ArrayBuilderNode builder,
            Object... objects) {
        return createArray(context, language, specializedJavaArrayOf(builder, objects), objects.length);
    }

}
