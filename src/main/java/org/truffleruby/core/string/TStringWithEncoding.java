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
package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import org.truffleruby.core.encoding.RubyEncoding;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;

/** TruffleString with RubyEncoding */
public final class TStringWithEncoding extends TStringWithEncodingBase {

    public final TruffleString tstring;

    public TStringWithEncoding(TruffleString tstring, RubyEncoding encoding) {
        super(tstring, encoding);
        this.tstring = tstring;
    }

    public TStringWithEncoding(
            AsTruffleStringNode asTruffleStringNode,
            AbstractTruffleString tstring,
            RubyEncoding encoding) {
        this(asTruffleStringNode.execute(tstring, encoding.tencoding), encoding);
    }

    @Override
    public TStringWithEncoding asImmutable() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return this;
    }

    @Override
    public TStringWithEncoding forceEncoding(RubyEncoding newEncoding) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        if (encoding == newEncoding) {
            return this;
        } else {
            return super.forceEncoding(newEncoding);
        }
    }

}
