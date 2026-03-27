/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.constant;

import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.WarnNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

public final class WarnAlreadyInitializedNode extends RubyBaseNode {

    @Child private WarnNode warnNode = new WarnNode();

    public boolean shouldWarn() {
        return warnNode.shouldWarn();
    }

    @TruffleBoundary
    public void warnAlreadyInitialized(RubyModule module, String name, SourceSection sourceSection,
            SourceSection previousSourceSection) {
        assert shouldWarn();
        final String constName = ModuleOperations.constantNameNoLeadingColon(getContext(), module, name);
        warnNode.warningMessage(sourceSection, "already initialized constant " + constName);
        if (previousSourceSection != null) {
            warnNode.warningMessage(previousSourceSection, "previous definition of " + name + " was here");
        }
    }

}
