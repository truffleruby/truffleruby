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
package org.truffleruby.language.objects;

import org.truffleruby.core.klass.ClassNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/** Defines a new class or returns an existing one found by name in a parent lexical scope. */
public final class DefineClassNode extends RubyContextSourceNode {

    private final String name;

    @Child private RubyNode superClassNode;
    @Child private RubyNode lexicalParentModule;
    @Child private LookupForExistingModuleNode lookupForExistingModuleNode;
    @Child private DispatchNode inheritedNode;

    private final ConditionProfile needToDefineProfile = ConditionProfile.create();
    private final ConditionProfile noSuperClassSupplied = ConditionProfile.create();
    private final BranchProfile errorProfile = BranchProfile.create();

    public DefineClassNode(
            String name,
            RubyNode lexicalParent,
            RubyNode superClass) {
        this.name = name;
        this.lexicalParentModule = lexicalParent;
        this.superClassNode = superClass;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object lexicalParentObject = lexicalParentModule.execute(frame);

        if (!(lexicalParentObject instanceof RubyModule)) {
            errorProfile.enter();
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeErrorIsNotA(lexicalParentObject, "module", this));
        }

        final RubyModule lexicalParentModule = (RubyModule) lexicalParentObject;
        final RubyClass suppliedSuperClass = executeSuperClass(frame);
        final Object existing = lookupForExistingModule(frame, name, lexicalParentModule);

        final RubyClass definedClass;

        if (needToDefineProfile.profile(existing == null)) {
            final RubyClass superClass;
            if (noSuperClassSupplied.profile(suppliedSuperClass == null)) {
                superClass = getContext().getCoreLibrary().objectClass;
            } else {
                superClass = suppliedSuperClass;
            }
            definedClass = ClassNodes.createInitializedRubyClass(
                    getContext(),
                    getEncapsulatingSourceSection(),
                    lexicalParentModule,
                    superClass,
                    name,
                    this);
            callInherited(frame, superClass, definedClass);
        } else {
            if (!(existing instanceof RubyClass)) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().typeErrorIsNotA(existing, "class", this));
            }

            definedClass = (RubyClass) existing;

            final Object currentSuperClass = definedClass.superclass;
            if (suppliedSuperClass != null && currentSuperClass != suppliedSuperClass) { // bug-compat with MRI https://bugs.ruby-lang.org/issues/12367
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().superclassMismatch(
                        definedClass.fields.getName(),
                        this));
            }
        }

        return definedClass;
    }

    private RubyClass executeSuperClass(VirtualFrame frame) {
        if (superClassNode == null) {
            return null;
        }
        final Object superClassObject = superClassNode.execute(frame);

        if (!(superClassObject instanceof RubyClass)) {
            errorProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().typeError("superclass must be a Class", this));
        }

        final RubyClass superClass = (RubyClass) superClassObject;

        if (superClass.isSingleton) {
            errorProfile.enter();
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeError("can't make subclass of virtual class", this));
        }

        return superClass;
    }

    private void callInherited(VirtualFrame frame, RubyClass superClass, RubyClass childClass) {
        if (inheritedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            inheritedNode = insert(DispatchNode.create());
        }
        inheritedNode.call(superClass, "inherited", childClass);
    }

    private Object lookupForExistingModule(VirtualFrame frame, String name, RubyModule lexicalParent) {
        if (lookupForExistingModuleNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupForExistingModuleNode = insert(new LookupForExistingModuleNode());
        }
        return lookupForExistingModuleNode.lookupForExistingModule(frame, name, lexicalParent);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new DefineClassNode(
                name,
                lexicalParentModule.cloneUninitialized(),
                cloneUninitialized(superClassNode));
        return copy.copyFlags(this);
    }

}
