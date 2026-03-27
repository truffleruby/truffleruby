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
package org.truffleruby.extra;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.objects.AllocationTracing;

import java.util.concurrent.atomic.AtomicReference;

@CoreModule(value = "TruffleRuby::AtomicReference", isClass = true)
public abstract class AtomicReferenceNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyAtomicReference allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().atomicReferenceShape;
            final RubyAtomicReference instance = new RubyAtomicReference(rubyClass, shape, new AtomicReference<>(nil));
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "initialize", optional = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyAtomicReference initializeNoValue(RubyAtomicReference self, NotProvided value) {
            return self;
        }

        @Specialization
        RubyAtomicReference initializeNil(RubyAtomicReference self, Nil value) {
            return self;
        }

        @Specialization(guards = { "!isNil(value)", "wasProvided(value)" })
        RubyAtomicReference initializeWithValue(RubyAtomicReference self, Object value) {
            self.value.set(value);
            return self;
        }

    }

    @CoreMethod(names = "get")
    public abstract static class GetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object get(RubyAtomicReference self) {
            return self.value.get();
        }
    }

    @CoreMethod(names = "set", required = 1)
    public abstract static class SetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object set(RubyAtomicReference self, Object value) {
            self.value.set(value);
            return value;
        }
    }

    @CoreMethod(names = "get_and_set", required = 1)
    public abstract static class GetAndSetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object getAndSet(RubyAtomicReference self, Object value) {
            return self.value.getAndSet(value);
        }
    }

    @CoreMethod(names = "compare_and_set_reference", required = 2, visibility = Visibility.PRIVATE)
    public abstract static class CompareAndSetReferenceNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isPrimitive(expectedValue)")
        boolean compareAndSetPrimitive(RubyAtomicReference self, Object expectedValue, Object newValue,
                @Cached ReferenceEqualNode equalNode) {
            while (true) {
                final Object currentValue = self.value.get();

                if (RubyGuards.isPrimitive(currentValue) &&
                        equalNode.execute(this, expectedValue, currentValue)) {
                    if (self.value.compareAndSet(currentValue, newValue)) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }

        @Specialization(guards = "!isPrimitive(expectedValue)")
        boolean compareAndSetReference(RubyAtomicReference self, Object expectedValue, Object newValue) {
            return self.value.compareAndSet(expectedValue, newValue);
        }

    }

}
