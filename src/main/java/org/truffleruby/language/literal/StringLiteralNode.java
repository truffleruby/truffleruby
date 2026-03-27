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
package org.truffleruby.language.literal;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class StringLiteralNode extends RubyContextSourceNode {

    private final TruffleString tstring;
    private final RubyEncoding encoding;

    public StringLiteralNode(TruffleString tstring, RubyEncoding encoding) {
        this.tstring = tstring;
        this.encoding = encoding;
    }

    @Override
    public RubyString execute(VirtualFrame frame) {
        return createString(tstring, encoding);
    }

    public TruffleString getTString() {
        return tstring;
    }

    public RubyEncoding getEncoding() {
        return encoding;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new StringLiteralNode(tstring, encoding);
        return copy.copyFlags(this);
    }

}
