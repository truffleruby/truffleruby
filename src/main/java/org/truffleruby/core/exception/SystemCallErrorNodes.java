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
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.annotations.Visibility;

import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "SystemCallError", isClass = true)
public abstract class SystemCallErrorNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySystemCallError allocateNameError(RubyClass rubyClass) {
            final Shape shape = getLanguage().systemCallErrorShape;
            final RubySystemCallError instance = new RubySystemCallError(rubyClass, shape, nil, null, nil, nil);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @CoreMethod(names = "errno")
    public abstract static class ErrnoNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object errno(RubySystemCallError self) {
            return self.errno;
        }

    }

    @Primitive(name = "exception_set_errno")
    public abstract static class ErrnoSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setErrno(RubySystemCallError error, Object errno) {
            error.errno = errno;
            return errno;
        }

    }

}
