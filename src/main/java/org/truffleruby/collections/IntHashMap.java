/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) the JRuby contributors
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.collections;

import org.truffleruby.annotations.SuppressFBWarnings;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public final class IntHashMap<V> {

    private Entry<V>[] table;
    private int count;

    private int threshold;

    private final float loadFactor;

    public static final class Entry<V> {
        final int hash;
        final int key;
        V value;
        Entry<V> next;

        protected Entry(int hash, int key, V value, Entry<V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public int getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

    public IntHashMap(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public IntHashMap(int initialCapacity, float loadFactor) {
        super();
        if (initialCapacity == 0) {
            initialCapacity = 1;
        }

        this.loadFactor = loadFactor;
        this.threshold = (int) (initialCapacity * loadFactor);
        this.table = new Entry[initialCapacity];
    }

    public V get(int key) {
        Entry<V>[] tab = table;
        int hash = key;
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<V> e = tab[index]; e != null; e = e.next) {
            if (e.hash == hash) {
                return e.value;
            }
        }
        return null;
    }

    protected void rehash() {
        int oldCapacity = table.length;
        Entry<V>[] oldMap = table;

        int newCapacity = oldCapacity * 2 + 1;
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Entry<V>[] newMap = new Entry[newCapacity];

        threshold = (int) (newCapacity * loadFactor);
        table = newMap;

        for (int i = oldCapacity; i-- > 0;) {
            for (Entry<V> old = oldMap[i]; old != null;) {
                Entry<V> e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = newMap[index];
                newMap[index] = e;
            }
        }
    }

    Entry<V> getEntry(int key) {
        Entry<V>[] tab = table;
        int hash = key;
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<V> e = tab[index]; e != null; e = e.next) {
            if (e.hash == hash) {
                return e;
            }
        }
        return null;
    }

    public V put(int key, V value) {
        // Makes sure the key is not already in the hashtable.
        Entry<V>[] tab = table;
        int hash = key;
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<V> e = tab[index]; e != null; e = e.next) {
            if (e.hash == hash) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }

        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = table;
            index = (hash & 0x7FFFFFFF) % tab.length;
        }

        // Creates the new entry.
        Entry<V> e = new Entry<>(hash, key, value, tab[index]);
        tab[index] = e;
        count++;
        return null;
    }

    public synchronized void clear() {
        Entry<V>[] tab = table;
        for (int index = tab.length; --index >= 0;) {
            tab[index] = null;
        }
        count = 0;
    }

    private abstract class HashIterator<T> implements Iterator<T> {
        Entry<V> next; // next entry to return
        int index; // current slot

        HashIterator() {
            Entry<V>[] t = table;
            int i = t.length;
            Entry<V> n = null;
            if (count != 0) { // advance to first entry
                while (i > 0 && (n = t[--i]) == null) {
                }
            }
            next = n;
            index = i;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @SuppressFBWarnings("SA_FIELD_SELF_ASSIGNMENT")
        Entry<V> nextEntry() {
            Entry<V> e = next;
            if (e == null) {
                throw new NoSuchElementException();
            }
            Entry<V> n = e.next;
            Entry<V>[] t = table;
            int i = index;
            while (n == null && i > 0) {
                n = t[--i];
            }
            index = i;
            next = n;
            return e;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private final class EntryIterator extends HashIterator<Entry<V>> {
        @Override
        public Entry<V> next() {
            return nextEntry();
        }
    }

    Iterator<Entry<V>> newEntryIterator() {
        return new EntryIterator();
    }

    private transient Set<Entry<V>> entrySet = null;

    public Set<Entry<V>> entrySet() {
        Set<Entry<V>> es = entrySet;
        return (es != null ? es : (entrySet = new EntrySet()));
    }

    private final class EntrySet extends AbstractSet<Entry<V>> {

        @Override
        public Iterator<Entry<V>> iterator() {
            return newEntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Entry<V> e = (Entry<V>) o;
            Entry<V> candidate = getEntry(e.key);
            return candidate != null && candidate.equals(e);
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return IntHashMap.this.count;
        }

        @Override
        public void clear() {
            IntHashMap.this.clear();
        }
    }

    @Override
    public String toString() {
        Iterator<Entry<V>> i = entrySet().iterator();
        if (!i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<V> e = i.next();
            V value = e.getValue();
            sb.append(e.getKey());
            sb.append('=');
            sb.append(value == this ? "(this IntHashMap)" : value);
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(", ");
        }
    }

}
