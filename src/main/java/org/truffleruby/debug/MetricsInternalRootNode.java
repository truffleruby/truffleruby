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
package org.truffleruby.debug;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.backtrace.InternalRootNode;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

final class MetricsInternalRootNode extends RubyBaseRootNode implements InternalRootNode {

    private static final SourceSection REQUIRE_METRICS_SOURCE_SECTION = Source
            .newBuilder(TruffleRuby.LANGUAGE_ID, "", "(metrics)")
            .build()
            .createUnavailableSection();

    private final String name;

    @Child private RubyNode body;

    public MetricsInternalRootNode(RubyLanguage language, String name, RubyNode body) {
        super(language, language.EMPTY_DECLARATION_DESCRIPTOR, REQUIRE_METRICS_SOURCE_SECTION);
        assert body != null;

        this.name = name;
        this.body = body;

        body.unsafeSetSourceSection(getSourceSection());
        body.unsafeSetIsCall();
        body.unsafeSetIsRoot();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

}
