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
package org.truffleruby.test.internal;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayBuilderNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.ArrayBuilderNode.BuilderState;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.Nil;

public class ArrayBuilderTest {

    private Context context;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    public void emptyBuilderTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(0);
            assertEquals(ArrayStoreLibrary.initialStorage(false), builder.finish(state, 0));
        });
    }

    @Test
    public void arrayBuilderAppendNothingTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            assertEquals(ArrayStoreLibrary.initialStorage(false), builder.finish(state, 0));
        });
    }

    @Test
    public void arrayBuilderAppendIntTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            for (int i = 0; i < 10; i++) {
                builder.appendValue(state, i, i);
            }
            assertEquals(int[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendLongTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            for (int i = 0; i < 10; i++) {
                builder.appendValue(state, i, ((long) i) << 33);
            }
            assertEquals(long[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendDoubleTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            for (int i = 0; i < 10; i++) {
                builder.appendValue(state, i, i * 0.0d);
            }
            assertEquals(double[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendObjectTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            for (int i = 0; i < 10; i++) {
                builder.appendValue(state, i, new Object());
            }
            assertEquals(Object[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendGrowTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            for (int i = 0; i < 12; i++) {
                builder.appendValue(state, i, Nil.INSTANCE);
            }
            Object[] result = (Object[]) builder.finish(state, 12);
            for (int i = 0; i < 12; i++) {
                Object e = result[i];
                assertEquals(Nil.INSTANCE, e);
            }
        });
    }

    @Test
    public void arrayBuilderAppendEmptyArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    ArrayStoreLibrary.initialStorage(false),
                    0);
            builder.appendArray(state, 0, otherStore);
            assertEquals(ArrayStoreLibrary.initialStorage(false), builder.finish(state, 0));
        });
    }

    @Test
    public void arrayBuilderAppendIntArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    new int[10],
                    10);
            builder.appendArray(state, 0, otherStore);
            assertEquals(int[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendLongArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    new long[10],
                    10);
            builder.appendArray(state, 0, otherStore);
            assertEquals(long[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendDoubleArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    new double[10],
                    10);
            builder.appendArray(state, 0, otherStore);
            assertEquals(double[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendObjectArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    new Object[10],
                    10);
            builder.appendArray(state, 0, otherStore);
            assertEquals(Object[].class, builder.finish(state, 10).getClass());
        });
    }

    @Test
    public void arrayBuilderAppendGrowArrayTest() {
        testInContext(() -> {
            ArrayBuilderNode builder = createBuilder();
            BuilderState state = builder.start(10);
            Object[] array = new Object[6];
            Arrays.fill(array, Nil.INSTANCE);
            RubyArray otherStore = new RubyArray(
                    RubyLanguage.getCurrentContext().getCoreLibrary().arrayClass,
                    RubyLanguage.getCurrentLanguage().arrayShape,
                    array,
                    array.length);
            builder.appendArray(state, 0, otherStore);
            builder.appendArray(state, 6, otherStore);
            Object[] result = (Object[]) builder.finish(state, 12);
            for (int i = 0; i < 12; i++) {
                Object e = result[i];
                assertEquals(Nil.INSTANCE, e);
            }
        });
    }

    private ArrayBuilderNode createBuilder() {
        return RubyTest.adopt(ArrayBuilderNode.create());
    }

    private void testInContext(Runnable test) {
        context.enter();
        try {
            test.run();
        } finally {
            context.leave();
        }
    }

    @Before
    public void before() {
        context = RubyTest.setupContext(Context.newBuilder()).out(out).err(err).build();
        context.eval("ruby", ":init");
    }

    @After
    public void dispose() {
        if (context != null) {
            context.close();
        }
    }

}
