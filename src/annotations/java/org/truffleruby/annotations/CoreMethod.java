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
package org.truffleruby.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoreMethod {

    String[] names();

    Visibility visibility() default Visibility.PUBLIC;

    /** Defines the method on the singleton class. {@link #needsSelf() needsSelf} is always false. See
     * {@link #constructor() constructor} if you need self. */
    boolean onSingleton() default false;

    /** Like {@link #onSingleton() onSingleton} but with {@link #needsSelf() needsSelf} always true. */
    boolean constructor() default false;

    /** Defines the method as public on the singleton class and as a private instance method. {@link #needsSelf()
     * needsSelf} is always false as it could be either a module or any receiver. Only use when it is required to be
     * both a singleton method and instance method. */
    boolean isModuleFunction() default false;

    /** When set to true, this core method is always AST-inlined, there is no call in between, and it's always passed
     * the caller frame. The node must subclass AlwaysInlinedMethodNode and use @GenerateUncached. */
    boolean alwaysInlined() default false;

    boolean needsSelf() default true;

    int required() default 0;

    int optional() default 0;

    /** Returns all remaining arguments. If keyword arguments are given they will simply be the last argument as a
     * RubyHash, and the arguments descriptor will tell if it was kwargs or positional, as always. */
    boolean rest() default false;

    /** Passes the optional block as an argument to the node. Also causes {@link Split#DEFAULT} to be resolved to
     * {@link Split#ALWAYS}, unless {@link #split()} is set to a non-default value, since if a block is passed and the
     * block is called it is necessary to split for the best performance. Splitting eagerly instead of lazily via the
     * heuristic avoids an extra copy and works better with {@code RubyRootNode#getParentFrameDescriptor()} when the
     * possible loop in this core method does many iterations on the first method call as it then preserves the
     * single-caller chain from the beginning, instead of breaking it until split and executed again for the first and
     * second call sites. {@link Split#NEVER} should be used if this method never calls the block but just stores it. */
    boolean needsBlock() default false;

    /** Try to lower argument <code>i</code> (starting at 0) to an int if its value is a long. The 0 is reserved for
     * <code>self</code>. If {@link #needsSelf() needsSelf} is false then there is no 0 argument explicitly passed.
     * Therefore the remaining arguments start at 1. */
    int[] lowerFixnum() default {};

    /** Raise an error if self is frozen. */
    boolean raiseIfFrozenSelf() default false;

    /** Raise an error if the string/self's temporary lock is set to true or if it is frozen. */
    boolean raiseIfNotMutableSelf() default false;

    boolean returnsEnumeratorIfNoBlock() default false;

    /** Method to call to determine the size of the returned Enumerator. Implies {@link #returnsEnumeratorIfNoBlock()}
     * . */
    String enumeratorSize() default "";

    /** Use these names in Ruby core methods stubs, ignore argument names in Java specializations. */
    String[] argumentNames() default {};

    Split split() default Split.DEFAULT;

}
