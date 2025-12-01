/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import dev.truffleruby.shadowed.org.joni.WarnCallback;
import org.truffleruby.parser.RubyWarnings;

public final class RegexWarnCallback implements WarnCallback {

    private final RubyWarnings warnings;

    public RegexWarnCallback() {
        this.warnings = new RubyWarnings();
    }

    @Override
    public void warn(String message) {
        warnings.warning(message);
    }

}
