/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.hash.library.BucketsHashStore;
import org.truffleruby.core.hash.library.CompactHashStore;
import org.truffleruby.core.hash.library.ConcurrentHashStore;
import org.truffleruby.core.hash.library.EmptyHashStore;
import org.truffleruby.core.hash.library.PackedHashStoreLibrary;
import org.truffleruby.core.hash.library.PackedHashStoreLibraryFactory;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class HashLiteralNode extends RubyContextSourceNodeCustomExecuteVoid {

    @Children protected final RubyNode[] keyValues;

    protected HashLiteralNode(RubyNode[] keyValues) {
        assert keyValues.length % 2 == 0;
        this.keyValues = keyValues;
    }

    protected int getNumberOfEntries() {
        return keyValues.length >> 1;
    }

    public static HashLiteralNode create(RubyNode[] keyValues, RubyLanguage language) {
        if (language.options.CONCURRENT_HASH_ALWAYS) {
            return new ConcurrentHashStore.ConcurrentHashLiteralNode(keyValues, language);
        }

        if (keyValues.length == 0) {
            return new EmptyHashStore.EmptyHashLiteralNode();
        } else if (keyValues.length <= PackedHashStoreLibrary.MAX_ENTRIES * 2) {
            return PackedHashStoreLibraryFactory.SmallHashLiteralNodeGen.create(keyValues);
        } else {
            return language.options.BIG_HASH_STRATEGY_IS_BUCKETS
                    ? new BucketsHashStore.BucketHashLiteralNode(keyValues)
                    : new CompactHashStore.CompactHashLiteralNode(keyValues);
        }
    }

    @ExplodeLoop
    @Override
    public final Nil executeVoid(VirtualFrame frame) {
        for (RubyNode child : keyValues) {
            child.executeVoid(frame);
        }
        return nil;
    }
}
