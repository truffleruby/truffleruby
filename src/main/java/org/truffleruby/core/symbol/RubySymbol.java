/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.ImmutableRubyString;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.language.ImmutableRubyObjectNotCopyable;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.parser.IdentifierType;
import org.truffleruby.parser.Identifiers;

import java.util.Objects;

@ExportLibrary(InteropLibrary.class)
public final class RubySymbol extends ImmutableRubyObjectNotCopyable implements TruffleObject {

    public static final int UNASSIGNED_ID = -1;

    private static final int CLASS_SALT = 92021474; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

    public static final RubySymbol[] EMPTY_ARRAY = new RubySymbol[0];

    public final RubyEncoding encoding;
    private final String string;
    public final TruffleString tstring;
    private final int javaStringHashCode;
    private final long id;
    private ImmutableRubyString name;
    private final IdentifierType type;

    private volatile RootCallTarget callTargetNoRefinements = null;

    RubySymbol(String string, TruffleString tstring, RubyEncoding encoding, long id) {
        assert tstring.isManaged();
        assert tstring.isCompatibleToUncached(encoding.tencoding);
        this.encoding = Objects.requireNonNull(encoding);
        this.string = Objects.requireNonNull(string);
        this.tstring = Objects.requireNonNull(tstring);
        this.javaStringHashCode = string.hashCode();
        this.id = id;
        this.type = Identifiers.stringToType(string);
    }

    RubySymbol(String string, TruffleString tstring, RubyEncoding encoding) {
        this(string, tstring, encoding, UNASSIGNED_ID);
    }

    public long getId() {
        return id;
    }

    public String getString() {
        return string;
    }

    public IdentifierType getType() {
        return this.type;
    }

    @TruffleBoundary
    public RootCallTarget getCallTargetNoRefinements(RubyLanguage language) {
        if (callTargetNoRefinements == null) {
            synchronized (this) {
                if (callTargetNoRefinements == null) {
                    callTargetNoRefinements = SymbolNodes.ToProcNode
                            .createCallTarget(language, this, DeclarationContext.NO_REFINEMENTS);
                }
            }
        }

        return callTargetNoRefinements;
    }

    public long computeHashCode(Hashing hashing) {
        return hashing.hash(CLASS_SALT, javaStringHashCode);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return ":" + string;
    }

    public ImmutableRubyString getName(RubyLanguage language) {
        if (name == null) {
            name = language.getImmutableString(tstring, encoding);
        }
        return name;
    }

    // region InteropLibrary messages
    @Override
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().symbolClass;
    }

    // region String messages
    @ExportMessage
    public boolean isString() {
        return true;
    }

    @ExportMessage
    public String asString() {
        return string;
    }
    // endregion
    // endregion

}
