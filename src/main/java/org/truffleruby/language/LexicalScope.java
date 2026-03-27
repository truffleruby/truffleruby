/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.core.module.RubyModule;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/** Instances of this class represent the Ruby lexical scope for constants, which is only changed by `class Name`,
 * `module Name` and `class << expr`. Other lexical scope features such as refinement and the default definee are
 * handled in {@link org.truffleruby.language.methods.DeclarationContext}. */
public final class LexicalScope {

    public static final LexicalScope NONE = null;

    /** Not null to allow using with @Specialization */
    public static final LexicalScope IGNORE = new LexicalScope(null, null);

    private final LexicalScope parent;

    public final boolean ignoreForClassVariablesLookup;

    @CompilationFinal private volatile RubyModule liveModule;

    public LexicalScope(LexicalScope parent, RubyModule liveModule) {
        this(parent, liveModule, false);
    }

    public LexicalScope(LexicalScope parent, RubyModule liveModule, boolean ignoreForClassVariablesLookup) {
        this.parent = parent;
        this.liveModule = liveModule;
        this.ignoreForClassVariablesLookup = ignoreForClassVariablesLookup;
    }

    public LexicalScope(LexicalScope parent) {
        this(parent, null);
    }

    public LexicalScope getParent() {
        return parent;
    }

    public RubyModule getLiveModule() {
        return liveModule;
    }

    public void unsafeSetLiveModule(RubyModule liveModule) {
        final RubyModule previous = this.liveModule;
        if (previous == null) {
            this.liveModule = liveModule;
        } else if (previous == liveModule) {
            // Can be the same module for code like 2.times { module M } (at the toplevel)
        } else {
            throw CompilerDirectives.shouldNotReachHere(
                    "Overriding module for static LexicalScope, old=" + moduleToDebugString(previous) +
                            ", new=" + moduleToDebugString(liveModule));
        }
    }

    private static String moduleToDebugString(RubyModule module) {
        return module + "@" + Integer.toHexString(module.hashCode());
    }

    @Override
    public String toString() {
        return " :: " + liveModule + (parent == null ? "" : parent.toString());
    }


}
