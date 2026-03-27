/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import org.truffleruby.collections.WeakValueCache;
import org.truffleruby.core.string.StringUtils;

import java.util.Arrays;

public final class KeywordArgumentsDescriptorManager {

    private final WeakValueCache<Key, KeywordArgumentsDescriptor> CANONICAL_KEYWORD_DESCRIPTORS = new WeakValueCache<>();

    public KeywordArgumentsDescriptorManager() {
        CANONICAL_KEYWORD_DESCRIPTORS.put(new Key(StringUtils.EMPTY_STRING_ARRAY), KeywordArgumentsDescriptor.EMPTY);
    }

    public KeywordArgumentsDescriptor getArgumentsDescriptor(String[] keywords) {
        final Key key = new Key(keywords);
        final KeywordArgumentsDescriptor descriptor = new KeywordArgumentsDescriptor(keywords);
        return CANONICAL_KEYWORD_DESCRIPTORS.addInCacheIfAbsent(key, descriptor);
    }

    public static final class Key {

        private final String[] keywords;

        public Key(String[] keywords) {
            this.keywords = keywords;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (other == null || other.getClass() != getClass()) {
                return false;
            }

            final Key otherKey = (Key) other;
            return Arrays.equals(keywords, otherKey.keywords);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(keywords);
        }

    }

}
