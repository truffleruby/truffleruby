/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.adapters;

import java.io.OutputStream;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.support.RubyIO;
import org.truffleruby.language.dispatch.DispatchNode;

public final class OutputStreamAdapter extends OutputStream {

    private final RubyContext context;
    private final RubyLanguage language;
    private final RubyIO object;
    private final RubyEncoding encoding;

    public OutputStreamAdapter(RubyContext context, RubyLanguage language, RubyIO object, RubyEncoding encoding) {
        this.context = context;
        this.language = language;
        this.object = object;
        this.encoding = encoding;
    }

    @Override
    public void write(int bite) {
        DispatchNode.getUncached().call(
                object,
                "write",
                new RubyString(
                        context.getCoreLibrary().stringClass,
                        language.stringShape,
                        false,
                        TStringUtils.fromByteArray(new byte[]{ (byte) bite }, encoding),
                        encoding));
    }

}
