/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.method;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.ArgumentDescriptorUtils;
import org.truffleruby.language.arguments.ReadRestArgumentNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.CanBindMethodToModuleNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.parser.ArgumentDescriptor;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

// Keep the methods in alphabetic order to be consistent with MethodNodes
@CoreModule(value = "UnboundMethod", isClass = true)
public abstract class UnboundMethodNodes {

    @CoreMethod(names = { "==", "eql?" }, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean equal(RubyUnboundMethod self, RubyUnboundMethod other) {
            return MethodNodes.areInternalMethodEqual(self.method, other.method);
        }

        @Specialization(guards = "!isRubyUnboundMethod(other)")
        boolean equal(RubyUnboundMethod self, Object other) {
            return false;
        }

    }

    @CoreMethod(names = "arity")
    public abstract static class ArityNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int arity(RubyUnboundMethod unboundMethod) {
            return unboundMethod.method.getArityNumber();
        }

    }

    @CoreMethod(names = "bind", required = 1, split = Split.ALWAYS)
    public abstract static class BindNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyMethod bind(RubyUnboundMethod unboundMethod, Object object,
                @Cached MetaClassNode metaClassNode,
                @Cached CanBindMethodToModuleNode canBindMethodToModuleNode,
                @Cached InlinedBranchProfile errorProfile) {
            final RubyClass objectMetaClass = metaClassNode.execute(this, object);

            if (!canBindMethodToModuleNode
                    .executeCanBindMethodToModule(this, unboundMethod.method, objectMetaClass)) {
                errorProfile.enter(this);
                final RubyModule declaringModule = unboundMethod.method.getDeclaringModule();
                if (RubyGuards.isSingletonClass(declaringModule)) {
                    throw new RaiseException(getContext(), coreExceptions().typeError(
                            "singleton method called for a different object",
                            this));
                } else {
                    throw new RaiseException(getContext(), coreExceptions().typeError(
                            Utils.concat(
                                    "bind argument must be an instance of ",
                                    declaringModule.fields.getName()),
                            this));
                }
            }
            final RubyMethod instance = new RubyMethod(
                    coreLibrary().methodClass,
                    getLanguage().methodShape,
                    object,
                    unboundMethod.method);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long hash(RubyUnboundMethod rubyMethod) {
            final InternalMethod method = rubyMethod.method;
            long h = getContext().getHashing(this).start(method.getDeclaringModule().hashCode());
            h = Hashing.update(h, MethodNodes.hashInternalMethod(method));
            return Hashing.end(h);
        }

    }

    @CoreMethod(names = "name")
    public abstract static class NameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol name(RubyUnboundMethod unboundMethod) {
            return getSymbol(unboundMethod.method.getName());
        }

    }

    @CoreMethod(names = "original_name")
    public abstract static class OriginalNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol originalName(RubyUnboundMethod unboundMethod,
                @Cached ToSymbolNode toSymbolNode) {
            String originalName = unboundMethod.method.getOriginalName();
            return toSymbolNode.execute(this, originalName);
        }
    }

    @CoreMethod(names = "owner")
    public abstract static class OwnerNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule owner(RubyUnboundMethod unboundMethod) {
            return unboundMethod.method.getOwner();
        }

    }

    @CoreMethod(names = "parameters")
    public abstract static class ParametersNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray parameters(RubyUnboundMethod method) {
            final ArgumentDescriptor[] argsDesc = method.method
                    .getSharedMethodInfo()
                    .getArgumentDescriptors();

            return ArgumentDescriptorUtils.argumentDescriptorsToParameters(getLanguage(), getContext(), argsDesc, true);
        }

    }

    @CoreMethod(names = "private?")
    public abstract static class IsPrivateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isPrivate(RubyUnboundMethod unboundMethod) {
            return unboundMethod.method.isPrivate();
        }
    }

    @CoreMethod(names = "protected?")
    public abstract static class IsProtectedNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isProtected(RubyUnboundMethod unboundMethod) {
            return unboundMethod.method.isProtected();
        }
    }

    @CoreMethod(names = "public?")
    public abstract static class IsPublicNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isPublic(RubyUnboundMethod unboundMethod) {
            return unboundMethod.method.isPublic();
        }
    }

    @CoreMethod(names = "source_location")
    public abstract static class SourceLocationNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object sourceLocation(RubyUnboundMethod unboundMethod,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var sourceSection = unboundMethod.method.getSharedMethodInfo().getSourceSection();
            return getLanguage().rubySourceLocation(sourceSection, fromJavaStringNode, this);
        }
    }

    @CoreMethod(names = "super_method")
    public abstract static class SuperMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object superMethod(RubyUnboundMethod unboundMethod) {
            InternalMethod internalMethod = unboundMethod.method;
            RubyModule origin = unboundMethod.origin;
            MethodLookupResult superMethod = ModuleOperations.lookupSuperMethod(internalMethod, origin);
            if (!superMethod.isDefined()) {
                return nil;
            } else {
                final RubyUnboundMethod instance = new RubyUnboundMethod(
                        coreLibrary().unboundMethodClass,
                        getLanguage().unboundMethodShape,
                        superMethod.getMethod().getDeclaringModule(),
                        superMethod.getMethod());
                AllocationTracing.trace(instance, this);
                return instance;
            }
        }

    }

    @Primitive(name = "unbound_method_ruby2_keywords")
    public abstract static class MethodRuby2KeywordsNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object ruby2Keywords(RubyUnboundMethod unboundMethod) {
            final InternalMethod method = unboundMethod.method;
            return ruby2Keywords(method.getSharedMethodInfo(), method.getCallTarget());
        }

        @TruffleBoundary
        public static Object ruby2Keywords(SharedMethodInfo sharedMethodInfo, RootCallTarget callTarget) {
            final Arity arity = sharedMethodInfo.getArity();
            if (!arity.hasRest() || arity.acceptsKeywords()) {
                return nil;
            }

            ReadRestArgumentNode readRestArgumentNode = NodeUtil.findFirstNodeInstance(callTarget.getRootNode(),
                    ReadRestArgumentNode.class);
            if (readRestArgumentNode != null) {
                readRestArgumentNode.markKeywordHashWithFlag();
                return true;
            } else {
                return false;
            }
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }

    }

}
