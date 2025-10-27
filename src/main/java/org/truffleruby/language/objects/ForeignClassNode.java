/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.dispatch.DispatchNode;

import java.util.ArrayList;

@ReportPolymorphism // inline cache
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class ForeignClassNode extends RubyBaseNode {

    // Specs for traits are in
    // spec/truffle/interop/polyglot/class_spec.rb
    // spec/truffle/interop/special_forms_spec.rb
    // spec/truffle/interop/polyglot/foreign_*_spec.rb
    // spec/truffle/interop/foreign_inspect_to_s_spec.rb
    /** Based on the types and traits of {@link InteropLibrary} */
    public enum Trait {
        // First in the ancestors
        HASH("Hash"), // must be before Array
        ARRAY("Array"), // must be before Iterable
        EXCEPTION("Exception"),
        EXECUTABLE("Executable"),
        INSTANTIABLE("Instantiable"),
        ITERATOR("Iterator"), // must be before Iterable
        ITERABLE("Iterable"),
        META_OBJECT("MetaObject"),
        NULL("Null"),
        NUMBER("Number"),
        POINTER("Pointer"),
        STRING("String");
        // Last in the ancestors

        public static final Trait[] VALUES = Trait.values();
        public static final int COMBINATIONS = 1 << VALUES.length;

        final String name;
        final int bit;

        Trait(String name) {
            this.name = name;
            this.bit = 1 << ordinal();
        }

        boolean isSet(int traits) {
            return (traits & bit) != 0;
        }
    }

    public abstract RubyClass execute(Node node, Object value);

    @Specialization(guards = "getTraits(object, interop) == cachedTraits", limit = "getInteropCacheLimit()")
    static RubyClass cached(Node node, Object object,
            @CachedLibrary("object") InteropLibrary interop,
            @Cached("getTraits(object, interop)") int cachedTraits) {
        assert RubyGuards.isForeignObject(object);
        return classForTraits(node, object, cachedTraits);
    }

    @Specialization(replaces = "cached", limit = "getInteropCacheLimit()")
    static RubyClass uncached(Node node, Object object,
            @CachedLibrary("object") InteropLibrary interop) {
        assert RubyGuards.isForeignObject(object);
        return classForTraits(node, object, getTraits(object, interop));
    }

    protected static int getTraits(Object object, InteropLibrary interop) {
        return (interop.hasHashEntries(object) ? Trait.HASH.bit : 0) +
                (interop.hasArrayElements(object) ? Trait.ARRAY.bit : 0) +
                (interop.isException(object) ? Trait.EXCEPTION.bit : 0) +
                (interop.isExecutable(object) ? Trait.EXECUTABLE.bit : 0) +
                (interop.isInstantiable(object) ? Trait.INSTANTIABLE.bit : 0) +
                (interop.isIterator(object) ? Trait.ITERATOR.bit : 0) +
                (interop.hasIterator(object) ? Trait.ITERABLE.bit : 0) +
                (interop.isMetaObject(object) ? Trait.META_OBJECT.bit : 0) +
                (interop.isNull(object) ? Trait.NULL.bit : 0) +
                (interop.isNumber(object) ? Trait.NUMBER.bit : 0) +
                (interop.isPointer(object) ? Trait.POINTER.bit : 0) +
                (interop.isString(object) ? Trait.STRING.bit : 0);
    }

    private static RubyClass classForTraits(Node node, Object object, int traits) {
        return resolvePolyglotForeignClass(node, object, traits);
//        RubyClass rubyClass = coreLibrary(node).polyglotForeignClasses[traits];
//        if (rubyClass == null) {
//            CompilerDirectives.transferToInterpreterAndInvalidate();
//            rubyClass = resolvePolyglotForeignClass(node, object, traits);
//            coreLibrary(node).polyglotForeignClasses[traits] = rubyClass;
//        }
//        return rubyClass;
    }

    private static RubyClass resolvePolyglotForeignClass(Node node, Object object, int traits) {
        final ArrayList<Object> args = new ArrayList<>();
        args.add(object);
        for (Trait trait : Trait.VALUES) {
            if (trait.isSet(traits)) {
                args.add(getSymbol(node, trait.name));
            }
        }
        final Object[] traitSymbols = args.toArray();
        return (RubyClass) DispatchNode.getUncached().call(coreLibrary(node).truffleInteropOperationsModule,
                "resolve_polyglot_class", traitSymbols);
    }
}
