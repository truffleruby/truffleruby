/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.profiles.IntValueProfile;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

public final class SplatToArgsNode extends RubyBaseNode {

    @Child ArrayStoreLibrary stores;
    final IntValueProfile splatSizeProfile = IntValueProfile.create();

    public SplatToArgsNode(RubyLanguage language) {
        stores = ArrayStoreLibrary.createDispatched(language);
    }

    public Object[] execute(Object receiver, RubyArray splatted) {
        int size = splatSizeProfile.profile(splatted.size);
        Object store = splatted.getStore();
        final Object[] newArgs = RubyArguments.allocate(size);
        RubyArguments.setSelf(newArgs, receiver);
        stores.copyContents(store, 0, newArgs, RubyArguments.RUNTIME_ARGUMENT_COUNT, size);
        return newArgs;
    }
}
