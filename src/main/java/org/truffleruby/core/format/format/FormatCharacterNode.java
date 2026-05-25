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
package org.truffleruby.core.format.format;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromCodePointNode;
import com.oracle.truffle.api.strings.TruffleString.ForceEncodingNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.convert.ToStrNode;
import org.truffleruby.core.format.convert.ToStrNodeGen;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class FormatCharacterNode extends FormatNode {

    private final RubyEncoding encoding;

    @Child private ToStrNode toStrNode;
    @Child private FromCodePointNode fromCodePointNode;
    @Child private ForceEncodingNode forceEncodingNode;

    public FormatCharacterNode(RubyEncoding encoding) {
        this.encoding = encoding;
    }

    @Specialization
    RubyString format(Object value,
            @Cached ToIntNode toIntNode,
            @Cached RubyStringLibrary strings) {
        final TruffleString character = coerce(value, strings, toIntNode);
        return createString(character, encoding);
    }

    @TruffleBoundary
    protected TruffleString coerce(Object value, RubyStringLibrary strings, ToIntNode toIntNode) {
        final TruffleString character;

        Object stringArgument;
        try {
            stringArgument = toStrNode().execute(value);
        } catch (NoImplicitConversionException e) {
            stringArgument = null;
        }

        if (stringArgument == null || RubyGuards.isNil(stringArgument)) {
            final int codepointArgument = toIntNode.execute(value);
            character = fromCodePointNode().execute(codepointArgument, encoding.tencoding);

            if (character == null) {
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().charRangeError(codepointArgument, this));
            }
        } else if (strings.isRubyString(this, stringArgument)) {
            /* This implementation follows the CRuby approach. CRuby ignores encoding of argument and interprets binary
             * representation of a character as if it's in the format sequence's encoding. */
            final AbstractTruffleString originalCharacter = strings.getTString(this, stringArgument);
            character = forceEncodingNode().execute(originalCharacter,
                    strings.getTEncoding(this, stringArgument), encoding.tencoding);
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
        return character;
    }

    private ToStrNode toStrNode() {
        if (toStrNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStrNode = insert(ToStrNodeGen.create(null));
        }

        return toStrNode;
    }

    private FromCodePointNode fromCodePointNode() {
        if (fromCodePointNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fromCodePointNode = insert(FromCodePointNode.create());
        }

        return fromCodePointNode;
    }

    private ForceEncodingNode forceEncodingNode() {
        if (forceEncodingNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            forceEncodingNode = insert(ForceEncodingNode.create());
        }

        return forceEncodingNode;
    }

}
