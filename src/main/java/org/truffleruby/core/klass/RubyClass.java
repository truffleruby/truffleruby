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
package org.truffleruby.core.klass;

import java.util.Arrays;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.ConcurrentWeakSet;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.debug.SingleElementArray;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.Shape;

import static org.truffleruby.language.RubyBaseNode.nil;

@ExportLibrary(InteropLibrary.class)
public final class RubyClass extends RubyModule implements ObjectGraphNode {

    private static final RubyClass[] EMPTY_CLASS_ARRAY = new RubyClass[0];

    public final boolean isSingleton;
    /** If this is an object's metaclass, then nonSingletonClass is the logical class of the object. */
    public final RubyClass nonSingletonClass;
    /** Ruby object which this class is a metaclass/singleton class of */
    public final RubyDynamicObject attached;
    /* a RubyClass or nil for BasicObject */
    public final Object superclass;
    @CompilationFinal(dimensions = 1) public final RubyClass[] ancestorClasses;
    public final ConcurrentWeakSet<RubyClass> directNonSingletonSubclasses;
    /** Depth from BasicObject (= 0) in the inheritance hierarchy. */
    public final int depth;

    @TruffleBoundary
    public RubyClass(
            RubyClass classClass,
            RubyLanguage language,
            SourceSection sourceSection,
            RubyModule lexicalParent,
            String givenBaseName,
            boolean isSingleton,
            RubyDynamicObject attached,
            Object superclass) {
        super(classClass, language.classShape, language, sourceSection, lexicalParent, givenBaseName);
        assert !isSingleton || givenBaseName == null;
        assert isSingleton == (attached != null);
        this.isSingleton = isSingleton;
        this.attached = attached;
        this.nonSingletonClass = computeNonSingletonClass(isSingleton, superclass);
        this.directNonSingletonSubclasses = new ConcurrentWeakSet<>();

        if (superclass instanceof RubyClass) {
            // superclass should be set after "full name"
            this.superclass = superclass;
            this.ancestorClasses = computeAncestorClasses((RubyClass) superclass);
            this.depth = ((RubyClass) superclass).depth + 1;
            fields.setSuperClass((RubyClass) superclass);

            if (!isSingleton) {
                ((RubyClass) superclass).directNonSingletonSubclasses.add(this);
            }
        } else { // BasicObject (nil superclass)
            assert superclass == nil;
            assert givenBaseName == "BasicObject";
            this.superclass = superclass;
            this.ancestorClasses = EMPTY_CLASS_ARRAY;
            this.depth = 0;
        }

        fields.afterConstructed();
    }

    /** Special constructor to build the 'Class' RubyClass itself. */
    RubyClass(RubyLanguage language, Shape classShape) {
        super(language, classShape, "constructor only for the class Class");
        this.isSingleton = false;
        this.attached = null;
        this.nonSingletonClass = this;
        this.directNonSingletonSubclasses = new ConcurrentWeakSet<>();

        RubyClass basicObjectClass = ClassNodes.createBootClass(language, this, nil, "BasicObject");
        RubyClass objectClass = ClassNodes.createBootClass(language, this, basicObjectClass, "Object");
        RubyClass moduleClass = ClassNodes.createBootClass(language, this, objectClass, "Module");

        RubyClass superclass = moduleClass;
        this.superclass = superclass;
        this.ancestorClasses = computeAncestorClasses(superclass);
        this.depth = superclass.depth + 1;
        fields.setSuperClass(superclass);

        fields.afterConstructed();
    }

    private RubyClass computeNonSingletonClass(boolean isSingleton, Object superclassObject) {
        if (isSingleton) {
            RubyClass superclass = ((RubyClass) superclassObject);
            if (superclass.isSingleton) {
                return superclass.nonSingletonClass;
            } else {
                return superclass;
            }
        } else {
            return this;
        }
    }

    private RubyClass[] computeAncestorClasses(RubyClass superclass) {
        final RubyClass[] superAncestors = superclass.ancestorClasses;
        final RubyClass[] ancestors = Arrays.copyOf(superAncestors, superAncestors.length + 1);
        ancestors[superAncestors.length] = superclass;
        return ancestors;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        super.getAdjacentObjects(reachable);
        ObjectGraph.addProperty(reachable, attached);
        ObjectGraph.addProperty(reachable, superclass);
    }

    // region MetaObject
    @ExportMessage
    public boolean hasMetaParents() {
        return true;
    }

    @ExportMessage
    public Object getMetaParents() {
        return new SingleElementArray(superclass);
    }
    // endregion

}
