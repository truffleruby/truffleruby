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
package org.truffleruby.core.module;

import java.lang.invoke.MethodHandles;
import java.util.Set;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.objects.SingletonClassNode;

@ExportLibrary(InteropLibrary.class)
public class RubyModule extends RubyDynamicObject implements ObjectGraphNode {

    public static final RubyModule[] EMPTY_ARRAY = new RubyModule[0];
    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public final ModuleFields fields;

    // Same number of inline fields as DynamicObjectBasic
    @DynamicField private long primitive1;
    @DynamicField private long primitive2;
    @DynamicField private long primitive3;
    @DynamicField private Object object1;
    @DynamicField private Object object2;
    @DynamicField private Object object3;
    @DynamicField private Object object4;

    @SuppressWarnings("this-escape")
    public RubyModule(
            RubyClass rubyClass,
            Shape shape,
            RubyLanguage language,
            SourceSection sourceSection,
            RubyModule lexicalParent,
            String givenBaseName) {
        super(rubyClass, shape);
        this.fields = new ModuleFields(language, sourceSection, lexicalParent, givenBaseName, this);
    }

    @SuppressWarnings("this-escape")
    protected RubyModule(RubyLanguage language, Shape classShape, String constructorOnlyForClassClass) {
        super(classShape, constructorOnlyForClassClass);
        this.fields = new ModuleFields(language, null, null, "Class", this);
    }

    public String getName() {
        return fields.getName();
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return fields.getName();
    }

    @TruffleBoundary
    public void addMethodConsiderNameVisibility(RubyContext context, InternalMethod method, Visibility visibility,
            Node currentNode) {
        if (ModuleOperations.isMethodPrivateFromName(method.getName())) {
            visibility = Visibility.PRIVATE;
        }

        addMethodIgnoreNameVisibility(context, method, visibility, currentNode);
    }

    @TruffleBoundary
    public void addMethodIgnoreNameVisibility(RubyContext context, InternalMethod method, Visibility visibility,
            Node currentNode) {
        if (visibility == Visibility.MODULE_FUNCTION) {
            fields.addMethod(context, currentNode, method.withVisibility(Visibility.PRIVATE));
            final RubyClass singletonClass = SingletonClassNode.getUncached().execute(this);
            singletonClass.fields.addMethod(
                    context,
                    currentNode,
                    method.withDeclaringModule(singletonClass).withOwner(singletonClass)
                            .withVisibility(Visibility.PUBLIC));
        } else {
            fields.addMethod(context, currentNode, method.withVisibility(visibility));
        }
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, fields);
    }

    // region MetaObject
    @ExportMessage
    public boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    public String getMetaQualifiedName() {
        return fields.getName();
    }

    @ExportMessage
    public String getMetaSimpleName() {
        return fields.getSimpleName();
    }

    @ExportMessage
    public boolean isMetaInstance(Object instance,
            @Cached IsANode isANode) {
        return isANode.executeIsA(instance, this);
    }
    // endregion

    // region SourceLocation
    @ExportMessage
    public boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage
    public SourceSection getSourceLocation() {
        SourceSection sourceSection = fields.getSourceSection();
        if (sourceSection != null) {
            return sourceSection;
        } else {
            return CoreLibrary.UNAVAILABLE_SOURCE_SECTION;
        }
    }
    // endregion

}
