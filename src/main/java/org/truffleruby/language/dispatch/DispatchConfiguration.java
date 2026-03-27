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
package org.truffleruby.language.dispatch;


public enum DispatchConfiguration {
    PUBLIC(false, true, MissingBehavior.CALL_METHOD_MISSING),
    PRIVATE(true, false, MissingBehavior.CALL_METHOD_MISSING),
    PROTECTED(false, false, MissingBehavior.CALL_METHOD_MISSING),
    PUBLIC_RETURN_MISSING(false, true, MissingBehavior.RETURN_MISSING),
    PRIVATE_RETURN_MISSING(true, false, MissingBehavior.RETURN_MISSING),
    PRIVATE_RETURN_MISSING_IGNORE_REFINEMENTS(true, false, MissingBehavior.RETURN_MISSING, true);

    public final boolean ignoreVisibility;
    public final boolean onlyLookupPublic;
    public final MissingBehavior missingBehavior;
    public final boolean ignoreRefinements;

    DispatchConfiguration(
            boolean ignoreVisibility,
            boolean onlyLookupPublic,
            MissingBehavior missingBehavior) {
        this(ignoreVisibility, onlyLookupPublic, missingBehavior, false);
    }

    DispatchConfiguration(
            boolean ignoreVisibility,
            boolean onlyLookupPublic,
            MissingBehavior missingBehavior,
            boolean ignoreRefinements) {
        this.ignoreVisibility = ignoreVisibility;
        this.onlyLookupPublic = onlyLookupPublic;
        this.missingBehavior = missingBehavior;
        this.ignoreRefinements = ignoreRefinements;
    }
}
