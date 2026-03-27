/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import org.ruby_lang.prism.Nodes;
import org.truffleruby.RubyLanguage;
import org.truffleruby.RubyLanguage.RubySourceOptions;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.ReturnID;


/** Translator environment, unique per parse/translation. This must be immutable to be correct for lazy translation, as
 * then multiple threads might lazy translate methods of the same file in parallel. */
public final class ParseEnvironment {

    public final RubyLanguage language;
    public final RubySource rubySource;
    public final Source source;
    /** Used to compute line numbers */
    public Nodes.Source yarpSource;
    public final ParserContext parserContext;
    private final boolean coverageEnabled;
    public final Node currentNode;

    private final boolean inCore;
    private final boolean canUsePrivatePrimitives;

    // Set once after parsing and before translating
    public Boolean allowTruffleRubyPrimitives = null;

    private int tempIndex = 0;

    public ParseEnvironment(
            RubyLanguage language,
            RubySource rubySource,
            ParserContext parserContext,
            Node currentNode) {
        this.language = language;
        this.rubySource = rubySource;
        this.source = rubySource.getSource();
        this.parserContext = parserContext;
        this.currentNode = currentNode;

        this.inCore = RubyLanguage.isCoreSource(source);
        boolean canUsePrivatePrimitives = false;
        if (!inCore) {
            String path = RubyLanguage.getPath(source);
            for (String prefix : language.allowPrivatePrimitivesPrefixes) {
                if (path.startsWith(prefix)) {
                    canUsePrivatePrimitives = true;
                    break;
                }
            }
        }
        this.canUsePrivatePrimitives = inCore || canUsePrivatePrimitives;

        this.coverageEnabled = rubySource.getSource().getOptions(language).get(RubySourceOptions.Coverage);
    }

    @Override
    public String toString() {
        return RubyLanguage.getPath(source);
    }

    public boolean inCore() {
        return inCore;
    }

    public boolean canUsePrimitives() {
        assert allowTruffleRubyPrimitives != null;
        return inCore() || allowTruffleRubyPrimitives;
    }

    public boolean canUsePrivatePrimitives() {
        return canUsePrivatePrimitives;
    }

    /** Returns false if the AST is shared */
    public boolean isCoverageEnabled() {
        return coverageEnabled;
    }

    @SuppressFBWarnings("ISC_INSTANTIATE_STATIC_CLASS")
    public ReturnID allocateReturnID() {
        return new ReturnID();
    }

    @SuppressFBWarnings("ISC_INSTANTIATE_STATIC_CLASS")
    public BreakID allocateBreakID() {
        return new BreakID();
    }

    public String allocateLocalTemp(String indicator) {
        return "%" + indicator + "_" + tempIndex++;
    }

}
