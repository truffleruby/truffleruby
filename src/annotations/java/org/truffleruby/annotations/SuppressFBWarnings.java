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
package org.truffleruby.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Used to suppress <a href="http://findbugs.sourceforge.net">SpotBugs</a> warnings. */
@Retention(RetentionPolicy.CLASS)
public @interface SuppressFBWarnings {
    /** The set of SpotBugs <a href="https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html">warnings</a> that
     * are suppressed for the annotated element. The value can be a bug category, kind or pattern. */
    String[] value();
}
