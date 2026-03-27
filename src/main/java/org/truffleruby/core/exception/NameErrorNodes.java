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
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "NameError", isClass = true)
public abstract class NameErrorNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyNameError allocateNameError(RubyClass rubyClass) {
            final Shape shape = getLanguage().nameErrorShape;
            final RubyNameError instance = new RubyNameError(rubyClass, shape, nil, null, nil, null, nil);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "name")
    public abstract static class NameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object name(RubyNameError self) {
            return self.name;
        }

    }

    @CoreMethod(names = "receiver")
    public abstract static class ReceiverNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object receiver(RubyNameError self) {
            final Object receiver = self.receiver;

            // TODO BJF July 21, 2016 Implement name error in message field

            if (receiver == null) {
                throw new RaiseException(getContext(), coreExceptions().argumentErrorNoReceiver(this));
            }
            return receiver;
        }

    }

    @Primitive(name = "name_error_set_name")
    public abstract static class NameSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setName(RubyNameError error, Object name) {
            error.name = name;
            return name;
        }

    }

    @Primitive(name = "name_error_set_receiver")
    public abstract static class ReceiverSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setReceiver(RubyNameError error, Object receiver) {
            error.receiver = receiver;
            return receiver;
        }

    }


}
