/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.object.Shape;
import org.graalvm.shadowed.org.jcodings.transcode.EConv;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

public final class RubyEncodingConverter extends RubyDynamicObject {

    EConv econv;

    public RubyEncodingConverter(RubyClass rubyClass, Shape shape, EConv econv) {
        super(rubyClass, shape);
        this.econv = econv;
    }

}
