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
package org.truffleruby.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Primitive {

    String name();

    /** Try to lower argument <code>i</code> (starting at 0) to an int if its value is a long. The argument at 0 is
     * usually the <code>receiver</code>. */
    int[] lowerFixnum() default {};

    /** Raise an error if any of the arguments with a given index is frozen. Indexation is same as for
     * {@link #lowerFixnum()}. */
    int[] raiseIfFrozen() default {};

    /** Raise an error if the string's temporary lock is set to true or if it is frozen. */
    int[] raiseIfNotMutable() default {};

    /** Use these names in Ruby core methods stubs, ignore argument names in Java specializations. */
    String[] argumentNames() default {};

    /** Whether this Primitive can be used outside the TruffleRuby core library. These Primitives must be documented in
     * doc/contributor/primitives.md. Because they are used externally they must be treated as public API and e.g. not
     * be removed or renamed. */
    boolean isPublic() default false;
}
