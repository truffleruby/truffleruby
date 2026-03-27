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
package org.truffleruby.core.support;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToLongNode;
import org.truffleruby.core.cast.ToRubyIntegerNode;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.kernel.KernelNodes.ToSNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.LazyDispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.FreezeNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;

/** All nodes in this class should be Primitive for efficiency (avoiding an extra call and constant lookup) */
@CoreModule("Truffle::Type")
public abstract class TypeNodes {

    @Primitive(name = "is_a?", isPublic = true)
    public abstract static class IsAPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isA(Object object, RubyModule module,
                @Cached IsANode isANode) {
            return isANode.executeIsA(object, module);
        }
    }

    @Primitive(name = "respond_to?")
    public abstract static class RespondToPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean respondTo(Object object, RubySymbol name, boolean includePrivate,
                @Cached KernelNodes.RespondToNode respondToNode) {
            // Do not pass a frame here, we want to ignore refinements and not need to read the caller frame
            return respondToNode.executeDoesRespondTo(object, name, includePrivate);
        }
    }

    @Primitive(name = "class", isPublic = true)
    public abstract static class ClassPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyClass objectClass(Object object,
                @Cached LogicalClassNode logicalClassNode) {
            return logicalClassNode.execute(object);
        }
    }

    @Primitive(name = "metaclass")
    public abstract static class MetaClassPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyClass metaClass(Object object,
                @Cached MetaClassNode metaClassNode) {
            return metaClassNode.execute(this, object);
        }
    }

    @Primitive(name = "singleton_class", isPublic = true)
    public abstract static class SingletonClassPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyClass singletonClass(Object object,
                @Cached SingletonClassNode singletonClassNode) {
            return singletonClassNode.execute(object);
        }
    }

    @Primitive(name = "equal?")
    public abstract static class EqualPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean equal(Object a, Object b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            return referenceEqualNode.execute(this, a, b);
        }
    }

    public abstract static class ObjectFreezeNode extends RubyBaseNode {

        public abstract Object execute(Object self);

        @Specialization(guards = "!isRubyDynamicObject(self)")
        Object freeze(Object self,
                @Cached @Exclusive FreezeNode freezeNode) {
            freezeNode.execute(this, self);
            return self;
        }

        @Specialization
        static Object freezeSingletonObject(RubyDynamicObject self,
                @Cached @Exclusive FreezeNode freezeNode,
                @Cached MetaClassNode metaClassNode,
                @Cached InlinedConditionProfile singletonClassUnfrozenProfile,
                @Cached IsFrozenNode isFrozenMetaClassNode,
                @Cached @Exclusive FreezeNode freezeMetaClasNode,
                @Bind Node node) {
            RubyClass metaClass = metaClassNode.execute(node, self);
            if (metaClass.isSingleton) {
                if (singletonClassUnfrozenProfile.profile(node,
                        !RubyGuards.isSingletonClass(self) && !isFrozenMetaClassNode.execute(metaClass))) {
                    freezeMetaClasNode.execute(node, metaClass);
                }
            }

            freezeNode.execute(node, self);
            return self;
        }
    }

    @Primitive(name = "freeze")
    public abstract static class FreezePrimitive extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object freeze(Object self,
                @Cached ObjectFreezeNode objectFreezeNode) {
            return objectFreezeNode.execute(self);
        }
    }

    @Primitive(name = "frozen?")
    public abstract static class IsFrozenPrimitive extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isFrozen(Object self,
                @Cached IsFrozenNode isFrozenNode) {
            return isFrozenNode.execute(self);
        }
    }

    @Primitive(name = "immediate_value?")
    public abstract static class IsImmediateValueNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean doBoolean(boolean value) {
            return true;
        }

        @Specialization
        boolean doInt(int value) {
            return true;
        }

        @Specialization
        boolean doLong(long value) {
            return true;
        }

        @Specialization
        boolean doFloat(double value) {
            return true;
        }

        @Specialization
        boolean doSymbol(RubySymbol value) {
            return true;
        }

        @Specialization
        boolean doNil(Nil value) {
            return true;
        }

        @Fallback
        boolean doFallback(Object value) {
            return false;
        }

    }

    @Primitive(name = "nil?", isPublic = true)
    public abstract static class IsNilNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean isNil(Object value) {
            return value == nil;
        }
    }

    @Primitive(name = "boolean_or_nil?")
    public abstract static class IsBooleanOrNilNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean bool(boolean value) {
            return true;
        }

        @Specialization
        boolean nil(Nil value) {
            return true;
        }

        @Fallback
        boolean other(Object value) {
            return false;
        }
    }

    @Primitive(name = "true?")
    public abstract static class IsTrue extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean bool(boolean value) {
            return value;
        }

        @Fallback
        boolean other(Object value) {
            return false;
        }
    }

    @Primitive(name = "false?")
    public abstract static class IsFalse extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean bool(boolean value) {
            return !value;
        }

        @Fallback
        boolean other(Object value) {
            return false;
        }
    }

    @Primitive(name = "object_ivars")
    public abstract static class ObjectInstanceVariablesNode extends PrimitiveArrayArgumentsNode {

        @NeverDefault
        public static ObjectInstanceVariablesNode create() {
            return TypeNodesFactory.ObjectInstanceVariablesNodeFactory.create(null);
        }

        public abstract RubyArray executeGetIVars(Object self);

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        static RubyArray instanceVariables(RubyDynamicObject object,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary,
                @Cached InlinedConditionProfile noPropertiesProfile,
                @Bind Node node) {
            var shape = objectLibrary.getShape(object);

            if (noPropertiesProfile.profile(node, shape.getPropertyCount() == 0)) {
                return createEmptyArray(node);
            }

            return createIVarNameArray(node, objectLibrary.getKeyArray(object));
        }

        @Specialization(guards = "!isRubyDynamicObject(object)")
        RubyArray instanceVariablesNotDynamic(Object object) {
            return createEmptyArray();
        }

        @TruffleBoundary
        private static RubyArray createIVarNameArray(Node node, Object[] keys) {
            final List<String> names = new ArrayList<>(keys.length);

            for (Object name : keys) {
                if (name instanceof String) {
                    names.add((String) name);
                }
            }

            final int size = names.size();
            final Object[] nameSymbols = new Object[size];
            for (int i = 0; i < size; i++) {
                nameSymbols[i] = getSymbol(node, names.get(i));
            }

            return createArray(node, nameSymbols);
        }

    }

    @Primitive(name = "object_ivar_defined?")
    public abstract static class ObjectIVarIsDefinedNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        boolean ivarIsDefined(RubyDynamicObject object, RubySymbol name,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            return objectLibrary.containsKey(object, name.getString());
        }

        @Fallback
        boolean ivarIsDefinedNonDynamic(Object object, Object name) {
            return false;
        }

    }

    @Primitive(name = "object_ivar_get")
    public abstract static class ObjectIVarGetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        Object ivarGet(RubyDynamicObject object, RubySymbol name,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            return objectLibrary.getOrDefault(object, name.getString(), nil);
        }
    }

    @Primitive(name = "object_ivar_set")
    public abstract static class ObjectIVarSetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object ivarSet(RubyDynamicObject object, RubySymbol name, Object value,
                @Cached WriteObjectFieldNode writeNode) {
            writeNode.execute(this, object, name.getString(), value);
            return value;
        }
    }

    // Those primitives store the key as either a HiddenKey or a Ruby Symbol, so they have
    // a different namespace than normal ivars which use java.lang.String.

    @Primitive(name = "object_hidden_var_create")
    public abstract static class ObjectHiddenVarCreateNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        HiddenKey createHiddenVar(RubySymbol identifier) {
            return new HiddenKey(identifier.getString());
        }
    }

    @Primitive(name = "object_hidden_var_defined?")
    public abstract static class ObjectHiddenVarDefinedNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        boolean objectHiddenVarDefined(RubyDynamicObject object, Object identifier,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            return objectLibrary.containsKey(object, identifier);
        }

        @Fallback
        boolean immutable(Object object, Object identifier) {
            return false;
        }
    }

    @Primitive(name = "object_hidden_var_get")
    public abstract static class ObjectHiddenVarGetNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        Object objectHiddenVarGet(RubyDynamicObject object, Object identifier,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            return objectLibrary.getOrDefault(object, identifier, nil);
        }

        @Fallback
        Object immutable(Object object, Object identifier) {
            return nil;
        }
    }

    @Primitive(name = "object_hidden_var_set")
    public abstract static class ObjectHiddenVarSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object objectHiddenVarSet(RubyDynamicObject object, Object identifier, Object value,
                @Cached WriteObjectFieldNode writeNode) {
            writeNode.execute(this, object, identifier, value);
            return value;
        }
    }

    @Primitive(name = "rb_any_to_s")
    public abstract static class ObjectToSNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyString toS(Object obj,
                @Cached ToSNode kernelToSNode) {
            return kernelToSNode.execute(obj);
        }
    }

    @Primitive(name = "module_name")
    public abstract static class ModuleNameNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object moduleName(RubyModule module) {
            return module.fields.getRubyStringName();
        }

    }

    @Primitive(name = "rb_num2long")
    public abstract static class RbNum2LongPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        long numToLong(Object value,
                @Cached ToLongNode toLongNode) {
            return toLongNode.execute(this, value);
        }
    }

    @Primitive(name = "rb_num2int")
    public abstract static class RbNum2IntPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int numToInt(Object value,
                @Cached ToIntNode toIntNode) {
            return toIntNode.execute(value);
        }
    }

    // MRI: rb_to_int().
    @Primitive(name = "convert_with_to_int", isPublic = true)
    public abstract static class RbToIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object toRubyInteger(Object value,
                @Cached ToRubyIntegerNode toRubyInteger) {
            return toRubyInteger.execute(this, value);
        }
    }

    // MRI: StringValue()
    @Primitive(name = "convert_with_to_str", isPublic = true)
    public abstract static class ConvertToStrNode extends PrimitiveArrayArgumentsNode {
        @Specialization(guards = "strings.isRubyString(this, object)", limit = "1")
        Object convert(Object object,
                @Cached RubyStringLibrary strings) {
            return object;
        }

        @Fallback
        Object convert(Object object,
                @Cached DispatchNode fallback) {
            return fallback.call(coreLibrary().truffleTypeModule, "rb_convert_type_fallback",
                    object, coreLibrary().stringClass, coreSymbols().TO_STR);
        }
    }

    @Primitive(name = "convert_with_to_ary", isPublic = true)
    public abstract static class ConvertToAryNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object convert(RubyArray object) {
            return object;
        }

        @Fallback
        Object convert(Object object,
                @Cached DispatchNode fallback) {
            return fallback.call(coreLibrary().truffleTypeModule, "rb_convert_type_fallback",
                    object, coreLibrary().arrayClass, coreSymbols().TO_ARY);
        }
    }

    @Primitive(name = "convert_with_to_hash", isPublic = true)
    public abstract static class ConvertToHashNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object convert(RubyHash object) {
            return object;
        }

        @Fallback
        Object convert(Object object,
                @Cached DispatchNode fallback) {
            return fallback.call(coreLibrary().truffleTypeModule, "rb_convert_type_fallback",
                    object, coreLibrary().hashClass, coreSymbols().TO_HASH);
        }
    }

    // The generic one, prefers one of the specific above if available. MRI: rb_convert_type()
    @Primitive(name = "convert_type", isPublic = true)
    public abstract static class ConvertTypeNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object convert(Object object, RubyClass klass, RubySymbol coercionMethod,
                @Cached IsANode isANode,
                @Cached LazyDispatchNode fallback) {
            if (isANode.executeIsA(object, klass)) {
                return object;
            } else {
                return fallback.get(this).call(coreLibrary().truffleTypeModule, "rb_convert_type_fallback",
                        object, klass, coercionMethod);
            }
        }
    }

    @Primitive(name = "check_frozen")
    public abstract static class TypeCheckFrozenNode extends PrimitiveArrayArgumentsNode {

        @NeverDefault
        public static TypeCheckFrozenNode create(RubyNode rubyNode) {
            return TypeNodesFactory.TypeCheckFrozenNodeFactory.create(new RubyNode[]{ rubyNode });
        }

        @Specialization
        Object check(Object value,
                @Cached CheckFrozenNode checkFrozenNode) {
            checkFrozenNode.execute(this, value);

            return value;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CheckFrozenNode extends RubyBaseNode {

        public abstract void execute(Node node, Object object);

        @Specialization
        static void check(Node node, Object value,
                // IsFrozenNode can't be inlined since known bug (GR-45912)
                @Cached(inline = false) IsFrozenNode isFrozenNode,
                @Cached InlinedBranchProfile errorProfile) {

            if (isFrozenNode.execute(value)) {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).frozenError(value, node));
            }
        }
    }


    @Primitive(name = "check_mutable_string")
    public abstract static class CheckMutableStringNode extends PrimitiveArrayArgumentsNode {

        public static CheckMutableStringNode create(RubyNode node) {
            return TypeNodesFactory.CheckMutableStringNodeFactory.create(new RubyNode[]{ node });
        }

        @Specialization
        Object check(RubyString value,
                @Cached InlinedBranchProfile errorProfile) {
            if (value.locked) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(),
                        coreExceptions().runtimeError("can't modify string; temporarily locked", this));
            } else if (value.frozen) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().frozenError(value, this));
            }
            return value;
        }

        @Specialization
        Object checkImmutable(ImmutableRubyString value) {
            throw new RaiseException(getContext(), coreExceptions().frozenError(value, this));
        }

    }

    @Primitive(name = "check_real?")
    public abstract static class CheckRealNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean check(int value) {
            return true;
        }

        @Specialization
        boolean check(long value) {
            return true;
        }

        @Specialization
        boolean check(double value) {
            return true;
        }

        @Fallback
        static boolean other(Object value,
                @Cached IsANode isANode,
                @Cached InlinedConditionProfile numericProfile,
                @Cached DispatchNode isRealNode,
                @Cached BooleanCastNode booleanCastNode,
                @Bind Node node) {
            return numericProfile.profile(node, isANode.executeIsA(value, coreLibrary(node).numericClass)) &&
                    booleanCastNode.execute(node, isRealNode.call(value, "real?"));
        }
    }

    @Primitive(name = "undefined?")
    public abstract static class IsUndefinedNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isUndefined(Object value) {
            return value == NotProvided.INSTANCE;
        }
    }

    @Primitive(name = "as_boolean", isPublic = true)
    public abstract static class AsBooleanNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean asBoolean(Object value,
                @Cached BooleanCastNode booleanCastNode) {
            return booleanCastNode.execute(this, value);
        }
    }

}
