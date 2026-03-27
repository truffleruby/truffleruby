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
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.collections.PEBiFunction;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.hash.HashGuards;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.EmptyHashStore;
import org.truffleruby.core.hash.library.HashStoreLibrary;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.methods.Arity;

/** Read a single keyword argument or execute its default value expression if missing */
@ImportStatic(HashGuards.class)
@SuppressWarnings("all")
public abstract class ReadKeywordArgumentNode extends RubyContextSourceNode implements PEBiFunction {

    private final RubySymbol name;

    @Child private RubyNode defaultValue;
    @Child private ReadUserKeywordsHashNode readUserKeywordsHashNode;

    public static ReadKeywordArgumentNode create(RubySymbol name, RubyNode defaultValue) {
        return ReadKeywordArgumentNodeGen.create(name, defaultValue);
    }

    protected ReadKeywordArgumentNode(RubySymbol name, RubyNode defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.readUserKeywordsHashNode = new ReadUserKeywordsHashNode();
    }

    public RubySymbol getName() {
        return name;
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        return execute(frame, readUserKeywordsHashNode.execute(frame));
    }

    public abstract Object execute(VirtualFrame frame, RubyHash hash);


    @Specialization(guards = "isNull(hash)")
    Object nullHash(VirtualFrame frame, RubyHash hash) {
        return getDefaultValue().execute(frame);
    }

    @Specialization(guards = "!isNull(hash)", limit = "hashStrategyLimit()")
    Object lookupKeywordInHash(VirtualFrame frame, RubyHash hash,
            @CachedLibrary("getHashStore(hash)") HashStoreLibrary hashes) {
        return hashes.lookupOrDefault(hash.store, frame, hash, name, this);
    }

    // Workaround for Truffle where the library expression is tried before the guard, resulting in a NPE if
    // hash is null. The guard will fail afterwards anyway, so return a valid store in that case.
    protected Object getHashStore(RubyHash hash) {
        return hash == null ? EmptyHashStore.NULL_HASH_STORE : hash.store;
    }

    // Workaround for ECJ to not warn about dead code in generated code
    static boolean isNull(RubyHash hash) {
        return hash == null;
    }

    @Override
    public Object accept(Frame frame, Object hash, Object key) {
        // This only works if the library is always cached and does not reach the limit.
        // Since this node is never uncached, and the limit is >= number of strategies, it should hold.
        final VirtualFrame virtualFrame = (VirtualFrame) frame;
        return getDefaultValue().execute(virtualFrame);
    }

    RubyNode getDefaultValue() {
        if (defaultValue == null) {
            // This isn't a true default value - it's a marker to say there isn't one.
            // This actually makes sense; the semantic action of executing this node is to report an error,
            // and we do the same thing.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RubyRootNode rootNode = (RubyRootNode) getRootNode();
            Arity arity = rootNode.getSharedMethodInfo().getArity();
            defaultValue = insert(new MissingKeywordArgumentNode(getLanguage(), arity));
        }

        return defaultValue;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ReadKeywordArgumentNode.create(name, cloneUninitialized(defaultValue));
        return copy.copyFlags(this);
    }

}
