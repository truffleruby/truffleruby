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
package org.truffleruby.language.constants;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.interop.ForeignToRubyNode;
import org.truffleruby.interop.ForeignToRubyNodeGen;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;

/** Read a literal constant on a given module: MOD::CONST. If the receiver is a foreign object, this reads the member
 * named like the constant instead, so that e.g. constants of a Ruby module from another context can be read. */
public final class ReadConstantNode extends RubyContextSourceNode {

    private final String name;
    private final BranchProfile notModuleProfile = BranchProfile.create();

    @Child private RubyNode moduleNode;
    @Child private LookupConstantNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode;
    @Child private InteropLibrary interopLibrary;
    @Child private ForeignToRubyNode foreignToRubyNode;

    public ReadConstantNode(RubyNode moduleNode, String name) {
        this.name = name;
        this.moduleNode = moduleNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object moduleObject = moduleNode.execute(frame);
        if (moduleObject instanceof RubyModule module) {
            return lookupAndGetConstant(module);
        } else {
            notModuleProfile.enter();
            return readForeignMember(checkForeign(moduleObject));
        }
    }

    private Object lookupAndGetConstant(RubyModule module) {
        return getGetConstantNode()
                .lookupAndResolveConstant(LexicalScope.IGNORE, module, name, getLookupConstantNode(), true);
    }

    public Object getConstant(RubyModule module, RubyConstant constant) {
        return getGetConstantNode()
                .executeGetConstant(LexicalScope.IGNORE, module, name, constant, getLookupConstantNode(), true);
    }

    private GetConstantNode getGetConstantNode() {
        if (getConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getConstantNode = insert(GetConstantNode.create());
        }
        return getConstantNode;
    }

    private LookupConstantNode getLookupConstantNode() {
        if (lookupConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupConstantNode = insert(LookupConstantNode.create(false, false));
        }
        return lookupConstantNode;
    }

    /** Evaluate the module part of the constant read. */
    public RubyModule evaluateModule(VirtualFrame frame) {
        return checkModule(moduleNode.execute(frame));
    }

    /** Whether the module part of this constant read is undefined, without attempting to evaluate it. */
    public boolean isModuleTriviallyUndefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return moduleNode.isDefined(frame, language, context) == nil;
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        if (isModuleTriviallyUndefined(frame, language, context)) {
            return nil;
        }
        try {
            final Object moduleObject = moduleNode.execute(frame);
            if (moduleObject instanceof RubyModule module) {
                final RubyConstant constant = getConstantIfDefined(module);
                return constant == null ? nil : FrozenStrings.CONSTANT;
            } else if (RubyGuards.isForeignObject(moduleObject)) {
                return getInteropLibrary().isMemberReadable(moduleObject, name) ? FrozenStrings.CONSTANT : nil;
            } else {
                return nil;
            }
        } catch (RaiseException e) {
            return nil; // MRI swallows all exceptions in defined? (https://bugs.ruby-lang.org/issues/5786)
        }
    }

    /** Given the module, returns the constant, it it is defined. Otherwise returns {@code null}. */
    public RubyConstant getConstantIfDefined(RubyModule module) {
        final RubyConstant constant;
        try {
            constant = getLookupConstantNode().lookupConstant(this, LexicalScope.IGNORE, module, name, true);
        } catch (RaiseException e) {
            if (e.getException().getLogicalClass() == coreLibrary().nameErrorClass) {
                // private constant
                return null;
            }
            throw e;
        }

        if (ModuleOperations.isConstantDefined(constant)) {
            return constant;
        } else {
            return null;
        }
    }

    public RubyNode makeWriteNode(RubyNode rhs) {
        return new WriteConstantNode(name, NodeUtil.cloneNode(moduleNode), rhs);
    }

    private RubyModule checkModule(Object module) {
        if (module instanceof RubyModule) {
            return ((RubyModule) module);
        } else {
            notModuleProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().typeErrorIsNotAClassModule(module, this));
        }
    }

    private Object checkForeign(Object object) {
        if (RubyGuards.isForeignObject(object)) {
            return object;
        } else {
            throw new RaiseException(getContext(), coreExceptions().typeErrorIsNotAClassModule(object, this));
        }
    }

    /** foreign_object::NAME sends readMember(foreign_object, "NAME") */
    private Object readForeignMember(Object foreign) {
        final Object value;
        try {
            value = getInteropLibrary().readMember(foreign, name);
        } catch (InteropException e) {
            throw TranslateInteropExceptionNode.executeUncached(e);
        }
        return getForeignToRubyNode().executeCached(value);
    }

    private InteropLibrary getInteropLibrary() {
        if (interopLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            interopLibrary = insert(InteropLibrary.getFactory().createDispatched(getInteropCacheLimit()));
        }
        return interopLibrary;
    }

    private ForeignToRubyNode getForeignToRubyNode() {
        if (foreignToRubyNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            foreignToRubyNode = insert(ForeignToRubyNodeGen.create());
        }
        return foreignToRubyNode;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadConstantNode(
                moduleNode.cloneUninitialized(),
                name);
        return copy.copyFlags(this);
    }

}
