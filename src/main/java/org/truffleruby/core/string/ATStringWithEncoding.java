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
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.strings.AbstractTruffleString;

/** AbstractTruffleString with RubyEncoding */
public final class ATStringWithEncoding extends TStringWithEncodingBase {

    public ATStringWithEncoding(AbstractTruffleString tstring, RubyEncoding encoding) {
        super(tstring, encoding);
    }

    public ATStringWithEncoding(Node node, RubyStringLibrary stringLib, Object string) {
        super(stringLib.getTString(node, string), stringLib.getEncoding(node, string));
    }

    @Override
    public TStringWithEncoding asImmutable() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return new TStringWithEncoding(tstring.asTruffleStringUncached(encoding.tencoding), encoding);
    }

}
