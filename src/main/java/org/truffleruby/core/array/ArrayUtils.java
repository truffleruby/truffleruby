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
package org.truffleruby.core.array;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;

public abstract class ArrayUtils {

    public static final Object[] EMPTY_ARRAY = new Object[0];
    public static final byte[] EMPTY_BYTES = new byte[0];

    public static boolean assertValidElements(Object[] array, int start, int count) {
        assert start <= array.length;
        assert start + count <= array.length;
        for (int i = start; i < start + count; i++) {
            final Object element = array[i];
            assert element != null : nullElementAt(array, i);
            assert RubyGuards.assertIsValidRubyValue(element);
        }
        return true;
    }

    @TruffleBoundary
    private static String nullElementAt(Object[] array, int index) {
        return "null element in Object[] at index " + index + ": " + Arrays.toString(array);
    }

    /** Extracts part of an array into a newly allocated byte[] array. Does not perform safety checks on parameters.
     * 
     * @param source the source array whose values should be extracted
     * @param start the start index, must be >= 0 and <= source.length
     * @param end the end index (exclusive), must be >= 0 and <= source.length and >= start
     * @return a newly allocated array with the extracted elements and length (end - start) */
    public static byte[] extractRange(byte[] source, int start, int end) {
        assert assertExtractRangeArgs(source, start, end);
        int length = end - start;
        byte[] result = new byte[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }

    /** Extracts part of an array into a newly allocated int[] array. Does not perform safety checks on parameters.
     * 
     * @param source the source array whose values should be extracted
     * @param start the start index, must be >= 0 and <= source.length
     * @param end the end index (exclusive), must be >= 0 and <= source.length and >= start
     * @return a newly allocated array with the extracted elements and length (end - start) */
    public static int[] extractRange(int[] source, int start, int end) {
        assert assertExtractRangeArgs(source, start, end);
        int length = end - start;
        int[] result = new int[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }

    /** Extracts part of an array into a newly allocated long[] array. Does not perform safety checks on parameters.
     * 
     * @param source the source array whose values should be extracted
     * @param start the start index, must be >= 0 and <= source.length
     * @param end the end index (exclusive), must be >= 0 and <= source.length and >= start
     * @return a newly allocated array with the extracted elements and length (end - start) */
    public static long[] extractRange(long[] source, int start, int end) {
        assert assertExtractRangeArgs(source, start, end);
        int length = end - start;
        long[] result = new long[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }

    /** Extracts part of an array into a newly allocated double[] array. Does not perform safety checks on parameters.
     * 
     * @param source the source array whose values should be extracted
     * @param start the start index, must be >= 0 and <= source.length
     * @param end the end index (exclusive), must be >= 0 and <= source.length and >= start
     * @return a newly allocated array with the extracted elements and length (end - start) */
    public static double[] extractRange(double[] source, int start, int end) {
        assert assertExtractRangeArgs(source, start, end);
        int length = end - start;
        double[] result = new double[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }

    /** Extracts part of an array into a newly allocated Object[] array. Does not perform safety checks on parameters.
     * 
     * @param source the source array whose values should be extracted
     * @param start the start index, must be >= 0 and <= source.length
     * @param end the end index (exclusive), must be >= 0 and <= source.length and >= start
     * @return a newly allocated array with the extracted elements and length (end - start) */
    public static Object[] extractRange(Object[] source, int start, int end) {
        assert assertExtractRangeArgs(source, start, end);
        int length = end - start;
        Object[] result = new Object[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }

    private static boolean assertExtractRangeArgs(Object source, int start, int end) {
        assert source != null;
        assert start >= 0;
        assert start <= Array.getLength(source);
        assert end >= start;
        assert end <= Array.getLength(source);
        return true;
    }

    public static boolean contains(int[] array, int value) {
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }

        return false;
    }

    /** Compares by identity using Java {@code ==} */
    public static <T> boolean contains(T[] array, T value) {
        for (T element : array) {
            if (element == value) {
                return true;
            }
        }

        return false;
    }

    public static int capacity(RubyLanguage language, int current, int needed) {
        if (needed == 0) {
            return 0;
        }

        assert current < needed;

        if (needed < language.options.ARRAY_UNINITIALIZED_SIZE) {
            return language.options.ARRAY_UNINITIALIZED_SIZE;
        } else {
            final int newCapacity = current << 1;
            if (newCapacity >= needed) {
                return newCapacity;
            } else {
                return needed;
            }
        }
    }

    public static int capacityForOneMore(RubyLanguage language, int current) {
        if (current < language.options.ARRAY_UNINITIALIZED_SIZE) {
            return language.options.ARRAY_UNINITIALIZED_SIZE;
        } else {
            return current << 1;
        }
    }

    public static long capacityForOneMore(RubyLanguage language, long current) {
        if (current < language.options.ARRAY_UNINITIALIZED_SIZE) {
            return language.options.ARRAY_UNINITIALIZED_SIZE;
        } else {
            return current << 1;
        }
    }

    public static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static Object[] copyOf(Object[] array, int newLength) {
        final Object[] copy = new Object[newLength];
        System.arraycopy(array, 0, copy, 0, Math.min(array.length, newLength));
        return copy;
    }

    public static int[] grow(int[] array, int newLength) {
        assert newLength >= array.length;
        final int[] copy = new int[newLength];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static long[] grow(long[] array, int newLength) {
        assert newLength >= array.length;
        final long[] copy = new long[newLength];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static double[] grow(double[] array, int newLength) {
        assert newLength >= array.length;
        final double[] copy = new double[newLength];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static Object[] grow(Object[] array, int newLength) {
        assert newLength >= array.length;
        final Object[] copy = new Object[newLength];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static Object[] copy(Object[] array) {
        final Object[] copy = new Object[array.length];
        System.arraycopy(array, 0, copy, 0, array.length);
        return copy;
    }

    public static Object[] shift(Object[] array) {
        assert array.length > 0;
        return extractRange(array, 1, array.length);
    }

    public static Object[] unshift(Object[] array, Object element) {
        final Object[] newArray = new Object[1 + array.length];
        newArray[0] = element;
        arraycopy(array, 0, newArray, 1, array.length);
        return newArray;
    }

    public static Object[] append(Object[] array, Object element) {
        final Object[] newArray = grow(array, array.length + 1);
        newArray[array.length] = element;
        return newArray;
    }

    public static <T> T getLast(T[] array) {
        assert array.length >= 1;
        return array[array.length - 1];
    }

    public static void setLast(Object[] array, Object value) {
        assert array.length >= 1;
        array[array.length - 1] = value;
    }

    /** Like {@link java.util.Arrays#fill(Object[], int, int, Object)} but includes profiling. */
    public static void fill(Object[] array, int from, int to, Object value, Node node,
            LoopConditionProfile loopProfile) {
        assert from <= to;
        int i = from;
        try {
            for (; loopProfile.inject(i < to); i++) {
                array[i] = value;
                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i - from);
        }
    }

    public static boolean regionEquals(byte[] first, int firstStart, byte[] second, int secondStart, int size) {
        return com.oracle.truffle.api.ArrayUtils
                .regionEqualsWithOrMask(first, firstStart, second, secondStart, size, null);
    }

    public static int memchr(byte[] array, int start, int size, byte find) {
        return com.oracle.truffle.api.ArrayUtils.indexOf(array, start, start + size, find);
    }

    @TruffleBoundary
    public static void sort(Object[] elements, int length) {
        Arrays.sort(elements, 0, length);
    }

    @TruffleBoundary
    public static List<Object> asList(Object[] array) {
        return Arrays.asList(array);
    }

    /** Compares by identity using Java {@code ==} */
    @TruffleBoundary
    public static Object[] subtract(Object[] a, Object[] b) {
        var result = new ArrayList<>();
        for (Object element : a) {
            if (!contains(b, element)) {
                result.add(element);
            }
        }
        return result.toArray();
    }

    @TruffleBoundary
    public static <T, R> Object[] map(T[] array, Function<? super T, ? extends R> mapper) {
        var result = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = mapper.apply(array[i]);
        }
        return result;
    }

    @TruffleBoundary
    public static <T, R> Object[] map(Collection<T> list, Function<? super T, ? extends R> mapper) {
        var result = new Object[list.size()];
        var iterator = list.iterator();
        for (int i = 0; i < list.size(); i++) {
            result[i] = mapper.apply(iterator.next());
        }
        return result;
    }

}
