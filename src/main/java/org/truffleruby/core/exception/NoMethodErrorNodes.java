/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;

import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "NoMethodError", isClass = true)
public abstract class NoMethodErrorNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyNoMethodError allocateNoMethodError(RubyClass rubyClass) {
            final Shape shape = getLanguage().noMethodErrorShape;
            final RubyNoMethodError instance = new RubyNoMethodError(rubyClass, shape, nil, null, nil, null, nil, nil);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "args")
    public abstract static class ArgsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object args(RubyNoMethodError self) {
            return self.args;
        }

    }

    @Primitive(name = "no_method_error_set_args")
    public abstract static class ArgsSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setArgs(RubyNoMethodError error, Object args) {
            assert args == Nil.INSTANCE || args instanceof RubyArray;
            error.args = args;
            return args;
        }

    }


}
