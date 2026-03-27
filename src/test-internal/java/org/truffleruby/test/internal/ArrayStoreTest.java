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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.truffleruby.test.internal.RubyTest.assertThrows;

import org.junit.Test;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.DelegatedArrayStorage;
import org.truffleruby.core.array.library.SharedArrayStorage;

public class ArrayStoreTest {

    @Test
    public void delegatedArrayLibraryOnlyAcceptsSpecificStore() {
        var intDelegated = new DelegatedArrayStorage(new int[]{ 42 }, 0, 1);
        var longDelegated = new DelegatedArrayStorage(new long[]{ 1 }, 0, 1);
        var library = ArrayStoreLibrary.create(intDelegated);
        RubyTest.adopt(library);

        assertTrue(library.accepts(intDelegated));
        assertFalse(library.accepts(longDelegated));

        assertEquals(42, library.read(intDelegated, 0));

        var e = assertThrows(() -> library.read(longDelegated, 0), AssertionError.class);
        assertTrue(e.getMessage().contains("Library does not accept given receiver"));
    }

    @Test
    public void sharedArrayLibraryOnlyAcceptsSpecificStore() {
        var intShared = new SharedArrayStorage(new int[]{ 42 });
        var longShared = new SharedArrayStorage(new long[]{ 1 });
        var library = ArrayStoreLibrary.create(intShared);
        RubyTest.adopt(library);

        assertTrue(library.accepts(intShared));
        assertFalse(library.accepts(longShared));

        assertEquals(42, library.read(intShared, 0));

        var e = assertThrows(() -> library.read(longShared, 0), AssertionError.class);
        assertTrue(e.getMessage().contains("Library does not accept given receiver"));
    }

    @Test
    public void zeroLengthGeneralisesToInteger() {
        Object store = ArrayStoreLibrary.initialStorage(false);
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        int newValue = 7;
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), int[].class);
    }

    @Test
    public void zeroLengthGeneralisesToLong() {
        Object store = ArrayStoreLibrary.initialStorage(false);
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        long newValue = 7;
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), long[].class);
    }

    @Test
    public void zeroLengthGeneralisesToDouble() {
        Object store = ArrayStoreLibrary.initialStorage(false);
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        double newValue = 7.2;
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), double[].class);
    }

    @Test
    public void zeroLengthGeneralisesToObject() {
        Object store = ArrayStoreLibrary.initialStorage(false);
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        Object newValue = new Object();
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), Object[].class);
    }

    @Test
    public void intArrayAcceptsInt() {
        int[] store = new int[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        int newValue = 1;
        assertTrue(stores.acceptsValue(store, newValue));
    }

    @Test
    public void intArrayGeneralisesToLong() {
        int[] store = new int[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        long newValue = 1L << 33;
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), long[].class);
    }

    @Test
    public void intArrayGeneralisesToObject() {
        int[] store = new int[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        Object newValue = new Object();
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), Object[].class);
    }

    @Test
    public void longArrayAcceptsLong() {
        long[] store = new long[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        long newValue = 1;
        assertTrue(stores.acceptsValue(store, newValue));
    }

    @Test
    public void longArrayGeneralisesToObject() {
        long[] store = new long[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        Object newValue = new Object();
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), Object[].class);
    }

    @Test
    public void doubleArrayAcceptsDouble() {
        double[] store = new double[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        double newValue = 1;
        assertTrue(stores.acceptsValue(store, newValue));
    }

    @Test
    public void doubleArrayGeneralisesToObject() {
        double[] store = new double[1];
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();

        Object newValue = new Object();
        assertFalse(stores.acceptsValue(store, newValue));
        Object newStore = stores.generalizeForValue(store, newValue).allocate(1);
        assertEquals(newStore.getClass(), Object[].class);
    }

    @Test
    public void extractRangeOnArrayReturnsDelegatedStorage() {
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();
        assertEquals(stores.extractRange(new int[10], 0, 10).getClass(), DelegatedArrayStorage.class);
        assertEquals(stores.extractRange(new long[10], 0, 10).getClass(), DelegatedArrayStorage.class);
        assertEquals(stores.extractRange(new double[10], 0, 10).getClass(), DelegatedArrayStorage.class);
        assertEquals(stores.extractRange(new Object[10], 0, 10).getClass(), DelegatedArrayStorage.class);
    }

    @Test
    public void extractRangeOnZeroLengthArrayReturnssZeroLengthArray() {
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();
        assertEquals(stores.extractRange(ArrayStoreLibrary.initialStorage(false), 0, 0),
                ArrayStoreLibrary.initialStorage(false));
    }
}
