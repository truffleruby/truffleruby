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
package org.truffleruby.language.arguments;

import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class ReadUserKeywordsHashNode extends RubyBaseNode {

    private final ConditionProfile keywordArgumentsProfile = ConditionProfile.create();

    public RubyHash execute(VirtualFrame frame) {
        final ArgumentsDescriptor descriptor = RubyArguments.getDescriptor(frame);
        if (keywordArgumentsProfile.profile(descriptor instanceof KeywordArgumentsDescriptor)) {
            final RubyHash keywords = (RubyHash) RubyArguments.getLastArgument(frame);
            assert !keywords.empty();
            assert assertHashMatchesDescriptor(keywords, (KeywordArgumentsDescriptor) descriptor);
            return keywords;
        } else {
            return null;
        }
    }

    /** Verify that all keywords the descriptor claims should be in the hash, are in fact in the hash. **/
    private boolean assertHashMatchesDescriptor(RubyHash hash, KeywordArgumentsDescriptor descriptor) {
        final HashStoreLibrary hashStoreLibrary = HashStoreLibrary.getUncached(hash);

        for (String keyword : descriptor.getKeywords()) {
            final RubySymbol symbol = getSymbol(keyword);
            final Object value = hashStoreLibrary.lookupOrDefault(hash.store, null, hash, symbol, (f, h, k) -> null);
            assert value != null : "descriptor claims " + keyword +
                    " was passed as a keyword argument but it's not in the hash";
        }
        return true;
    }

}
