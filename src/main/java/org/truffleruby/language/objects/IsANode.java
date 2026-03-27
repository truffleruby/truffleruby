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
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/* Splitting: inline cache, much faster when `module` is not a Class.
 * Also useful to split Ruby methods which do something like `if obj.is_a?(Foo) then foo else bar end`
 * such as NoBorderImagePadded#index used by NoBorderImage#[] in the image-demo benchmarks. */
@GenerateUncached
@ReportPolymorphism // see comment above
public abstract class IsANode extends RubyBaseNode {

    @NeverDefault
    public static IsANode create() {
        return IsANodeGen.create();
    }

    public static IsANode getUncached() {
        return IsANodeGen.getUncached();
    }

    public abstract boolean executeIsA(Object self, RubyModule module);

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "metaClassNode.execute(this, self) == cachedMetaClass",
                    "module == cachedModule" },
            assumptions = "getHierarchyUnmodifiedAssumption(cachedModule)",
            limit = "getCacheLimit()")
    boolean isAMetaClassCached(Object self, RubyModule module,
            @Cached @Shared MetaClassNode metaClassNode,
            @Cached("metaClassNode.execute($node, self)") RubyClass cachedMetaClass,
            @Cached("module") RubyModule cachedModule,
            @Cached("isA(cachedMetaClass, cachedModule)") boolean result) {
        return result;
    }

    /** If we are checking if an object.is_a?(SomeClass), then no matter hierarchy changes the superclass chain never
     * changes. The superclass is always set when creating the RubyClass on TruffleRuby, i.e. it cannot be set later by
     * Class#initialize. */
    public Assumption getHierarchyUnmodifiedAssumption(RubyModule module) {
        if (module instanceof RubyClass) {
            return Assumption.ALWAYS_VALID;
        }
        return module.fields.getHierarchyUnmodifiedAssumption();
    }

    @Specialization(
            guards = { "isSingleContext()", "klass == cachedClass" },
            replaces = "isAMetaClassCached",
            limit = "getCacheLimit()")
    boolean isAClassCached(Object self, RubyClass klass,
            @Cached @Shared MetaClassNode metaClassNode,
            @Cached @Shared InlinedConditionProfile isMetaClass,
            @Cached("klass") RubyClass cachedClass) {
        return isAClassUncached(self, klass, metaClassNode, isMetaClass);
    }

    @Specialization(replaces = "isAClassCached")
    boolean isAClassUncached(Object self, RubyClass klass,
            @Cached @Shared MetaClassNode metaClassNode,
            @Cached @Shared InlinedConditionProfile isMetaClass) {
        final RubyClass metaclass = metaClassNode.execute(this, self);

        if (isMetaClass.profile(this, metaclass == klass)) {
            return true;
        }

        assert metaclass.ancestorClasses != null;

        final int depth = klass.depth;
        if (depth < metaclass.depth) { // < and not <= as we checked for equality above
            return metaclass.ancestorClasses[depth] == klass;
        }

        return false;
    }

    @Specialization(guards = "!isRubyClass(module)", replaces = "isAMetaClassCached")
    boolean isAUncached(Object self, RubyModule module,
            @Cached @Shared MetaClassNode metaClassNode) {
        return isA(metaClassNode.execute(this, self), module);
    }

    @TruffleBoundary
    protected boolean isA(RubyClass metaClass, RubyModule module) {
        return ModuleOperations.assignableTo(metaClass, module);
    }

    protected int getCacheLimit() {
        return getLanguage().options.IS_A_CACHE;
    }

}
