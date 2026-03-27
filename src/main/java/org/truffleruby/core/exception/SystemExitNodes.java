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
package org.truffleruby.core.exception;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.objects.AllocationTracing;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.Shape;

@CoreModule(value = "SystemExit", isClass = true)
public abstract class SystemExitNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySystemExit allocateSytemExit(RubyClass rubyClass) {
            final Shape shape = getLanguage().systemExitShape;
            final RubySystemExit instance = new RubySystemExit(rubyClass, shape, nil, null, nil, 0);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "status")
    public abstract static class StatusNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int status(RubySystemExit self) {
            return self.exitStatus;
        }

    }

    @Primitive(name = "system_exit_set_status", lowerFixnum = 1)
    public abstract static class StatusSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setStatus(RubySystemExit error, int exitStatus) {
            error.exitStatus = exitStatus;
            return exitStatus;
        }

    }

}
