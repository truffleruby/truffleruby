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
package org.truffleruby.core.klass;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.InitializeClassNode;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.language.RubyBaseNode.nil;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;

@CoreModule(value = "Class", isClass = true)
public abstract class ClassNodes {

    /** Special constructor for class Class */
    @TruffleBoundary
    public static RubyClass createClassClassAndBootClasses(RubyLanguage language) {
        final RubyClass rubyClass = new RubyClass(language, language.classShape);

        assert rubyClass.getLogicalClass() == rubyClass;
        assert rubyClass.getMetaClass() == rubyClass;

        return rubyClass;
    }

    /** This constructor supports initialization and solves boot-order problems and should not normally be used from
     * outside this class. */
    @TruffleBoundary
    public static RubyClass createBootClass(RubyLanguage language, RubyClass classClass, Object superclass,
            String name) {
        return new RubyClass(classClass, language, null, null, name, false, null, superclass);
    }

    @TruffleBoundary
    public static RubyClass createSingletonClassOfObject(RubyContext context, SourceSection sourceSection,
            RubyClass superclass, RubyDynamicObject attached) {
        assert attached != null;
        final RubyClass rubyClass = createRubyClass(
                context,
                sourceSection,
                getClassClass(superclass),
                null,
                superclass,
                null,
                true,
                attached,
                null);
        return ensureItHasSingletonClassCreated(context, rubyClass);
    }

    @TruffleBoundary
    public static RubyClass createInitializedRubyClass(RubyContext context, SourceSection sourceSection,
            RubyModule lexicalParent, RubyClass superclass, String name, Node currentNode) {
        assert superclass != null;
        final RubyClass rubyClass = createRubyClass(
                context,
                sourceSection,
                getClassClass(superclass),
                lexicalParent,
                superclass,
                name,
                false,
                null,
                currentNode);
        return ensureItHasSingletonClassCreated(context, rubyClass);
    }

    @TruffleBoundary
    private static RubyClass createRubyClass(RubyContext context,
            SourceSection sourceSection,
            RubyClass classClass,
            RubyModule lexicalParent,
            RubyClass superclass,
            String name,
            boolean isSingleton,
            RubyDynamicObject attached,
            Node currentNode) {
        assert superclass != null;
        final RubyClass rubyClass = new RubyClass(
                classClass,
                context.getLanguageSlow(),
                sourceSection,
                lexicalParent,
                name,
                isSingleton,
                attached,
                superclass);

        if (lexicalParent != null) {
            lexicalParent.fields.setConstant(context, currentNode, name, rubyClass);
        }

        return rubyClass;
    }

    @TruffleBoundary
    public static void initialize(RubyContext context, RubyClass rubyClass) {
        assert !rubyClass.isSingleton : "Singleton classes can only be created internally";

        ensureItHasSingletonClassCreated(context, rubyClass);
    }

    /** We also need to create the singleton class of any class exposed to the user for proper lookup and consistency.
     * This is not so intuitive, but basically it follows the rule of: "every Class object exposed to the user must have
     * a singleton class", i.e., only (singleton) classes not exposed to the user can have their own singleton class
     * created lazily. An example is `class K; def self.foo; end; end; sc = k.new.singleton_class`. `sc` there must have
     * its singleton class created, otherwise `Primitive.class_of(sc).ancestors` would be `[Class, Module, Object,
     * Kernel, BasicObject]` and `sc.foo` would not find method foo (which is defined on `#<Class:K>`). With `sc` having
     * the singleton class as soon as `sc` is exposed to the user, then it's fine, and
     * `Primitive.class_of(sc).ancestors` is `[#<Class:#<Class:#<K:0xc8>>>, #<Class:K>, #<Class:Object>,
     * #<Class:BasicObject>, Class, Module, Object, Kernel, BasicObject]`. See rb_singleton_class() documentation in
     * MRI. In theory, it might be possible to do this lazily in `MetaClassNode` but it's unlikely not a good
     * performance trade-off (add a check at every usage vs do it eagerly and no check). As an anecdote a single
     * ruby/spec fails when not calling this in {@link #createSingletonClassOfObject}, maybe there is an opportunity?
     * TODO (eregon, 23 March 2023): CRuby (3.1) seems to deal with this better, `RBASIC_CLASS(sc).ancestors` is
     * `[#<Class:K>, #<Class:Object>, #<Class:BasicObject>, Class, Module, Object, Kernel, BasicObject]` so it points to
     * the superclass (which is a singleton class) and then somehow knows this is not this class's singleton class
     * (maybe by comparing `attached`), so on `sc.singleton_class` it actually creates it. */
    private static RubyClass ensureItHasSingletonClassCreated(RubyContext context, RubyClass rubyClass) {
        getLazyCreatedSingletonClass(context, rubyClass);
        return rubyClass;
    }

    @TruffleBoundary
    public static RubyClass getSingletonClassOfClass(RubyContext context, RubyClass rubyClass) {
        // We also need to create the singleton class of a singleton class for proper lookup and consistency.
        // See rb_singleton_class() documentation in MRI.
        return ensureItHasSingletonClassCreated(context, getLazyCreatedSingletonClass(context, rubyClass));
    }

    public static RubyClass getSingletonClassOfClassOrNull(RubyContext context, RubyClass rubyClass) {
        RubyClass metaClass = rubyClass.getMetaClass();
        if (metaClass.isSingleton) {
            return ensureItHasSingletonClassCreated(context, metaClass);
        } else {
            return null;
        }
    }

    private static RubyClass getLazyCreatedSingletonClass(RubyContext context, RubyClass rubyClass) {
        synchronized (rubyClass) {
            RubyClass metaClass = rubyClass.getMetaClass();
            if (metaClass.isSingleton) {
                return metaClass;
            }

            return createSingletonClass(context, rubyClass);
        }
    }

    @TruffleBoundary
    private static RubyClass createSingletonClass(RubyContext context, RubyClass rubyClass) {
        final RubyClass singletonSuperclass;
        final Object superclass = rubyClass.superclass;
        if (superclass == nil) {
            singletonSuperclass = rubyClass.getLogicalClass();
        } else {
            singletonSuperclass = getLazyCreatedSingletonClass(context, (RubyClass) superclass);
        }

        RubyClass metaClass = ClassNodes.createRubyClass(
                context,
                rubyClass.fields.getSourceSection(),
                getClassClass(rubyClass),
                null,
                singletonSuperclass,
                null,
                true,
                rubyClass,
                null);
        SharedObjects.propagate(context.getLanguageSlow(), rubyClass, metaClass);
        rubyClass.setMetaClass(metaClass);

        return rubyClass.getMetaClass();
    }

    /** The same as {@link CoreLibrary#classClass} but available while executing the CoreLibrary constructor */
    private static RubyClass getClassClass(RubyClass rubyClass) {
        return rubyClass.getLogicalClass();
    }

    @Primitive(name = "class_new")
    public abstract static class NewClassNode extends PrimitiveArrayArgumentsNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        RubyClass newClass(RubyClass superclass, boolean callInherited, Object maybeBlock,
                @Cached InitializeClassNode initializeClassNode) {
            if (superclass.isSingleton) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().typeErrorSubclassSingletonClass(this));
            }
            if (superclass == coreLibrary().classClass) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().typeErrorSubclassClass(this));
            }

            final RubyClass newRubyClass = new RubyClass(
                    coreLibrary().classClass,
                    getLanguage(),
                    getEncapsulatingSourceSection(),
                    null,
                    null,
                    false,
                    null,
                    superclass);

            initializeClassNode.executeInitialize(newRubyClass, superclass, callInherited, maybeBlock);

            return newRubyClass;
        }

        @Specialization(guards = "!isRubyClass(superclass)")
        RubyClass newClass(Object superclass, boolean callInherited, Object maybeBlock) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorSuperclassMustBeClass(this));
        }
    }

    /** #allocate should only be defined as an instance method of Class (Class#allocate), which is required for
     * compatibility. __allocate__ is our version of the "allocation function" as defined by rb_define_alloc_func() in
     * MRI to define how to create instances of specific classes. */
    @CoreMethod(names = "allocate")
    public abstract static class AllocateInstanceNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "!rubyClass.isSingleton")
        Object newInstance(RubyClass rubyClass,
                @Cached DispatchNode allocateNode) {
            return allocateNode.call(rubyClass, "__allocate__");
        }

        @Specialization(guards = "rubyClass.isSingleton")
        RubyClass newSingletonInstance(RubyClass rubyClass) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().typeErrorCantCreateInstanceOfSingletonClass(this));
        }
    }

    @CoreMethod(names = "attached_object")
    public abstract static class AttachedObjectNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "rubyClass.isSingleton")
        Object attachedObject(RubyClass rubyClass) {
            return rubyClass.attached;
        }

        @Specialization(guards = "!rubyClass.isSingleton")
        Object attachedObjectOfNonSingletonClass(RubyClass rubyClass) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().typeErrorNotASingletonClass(this, rubyClass));
        }
    }

    // Worth always splitting to have monomorphic #__allocate__ and #initialize,
    // Worth always inlining as the field accesses and initializations are optimized when the allocation is visible,
    // and a non-inlined call to #__allocate__ would allocate the arguments Object[] which is about the same number of
    // nodes as the object allocation. Also avoids many frame and Object[] allocations when creating a new object.
    @GenerateUncached
    @CoreMethod(names = "new", rest = true, alwaysInlined = true)
    public abstract static class NewNode extends AlwaysInlinedMethodNode {
        @Specialization(guards = "!rubyClass.isSingleton")
        Object newInstance(Frame callerFrame, RubyClass rubyClass, Object[] rubyArgs, RootCallTarget target,
                @Cached DispatchNode allocateNode,
                @Cached DispatchNode initializeNode) {
            final Object instance = allocateNode.call(rubyClass, "__allocate__");
            initializeNode.execute(null, instance, "initialize", RubyArguments.repack(rubyArgs, instance), PRIVATE);
            return instance;
        }

        @Specialization(guards = "rubyClass.isSingleton")
        RubyClass newSingletonInstance(
                Frame callerFrame, RubyClass rubyClass, Object[] rubyArgs, RootCallTarget target) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().typeErrorCantCreateInstanceOfSingletonClass(this));
        }
    }

    @CoreMethod(names = "initialize", optional = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object initialize(RubyClass rubyClass, Object maybeSuperclass) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().typeErrorAlreadyInitializedClass(this));
        }
    }

    @CoreMethod(names = "inherited", needsSelf = false, required = 1, visibility = Visibility.PRIVATE)
    public abstract static class InheritedNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object inherited(Object subclass) {
            return nil;
        }
    }

    @CoreMethod(names = "subclasses")
    public abstract static class SubclassesNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyArray subclasses(RubyClass rubyClass) {
            return createArray(rubyClass.directNonSingletonSubclasses.toArray());
        }
    }

    @CoreMethod(names = "superclass")
    public abstract static class SuperClassNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object getSuperClass(RubyClass rubyClass) {
            return rubyClass.superclass;
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }
    }
}
