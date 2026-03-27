/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.Set;

import org.truffleruby.RubyContext;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

public final class RubyConstant implements ObjectGraphNode {

    public static final RubyConstant[] EMPTY_ARRAY = new RubyConstant[0];

    private final RubyModule declaringModule;
    private final String name;
    private final Object value;
    private final boolean isPrivate;
    private final boolean isDeprecated;

    private final AutoloadConstant autoloadConstant;
    /** A autoload constant can become "undefined" after the autoload loads the file but the constant is not defined by
     * the file */
    private final boolean undefined;

    private final SourceSection sourceSection;

    public RubyConstant(
            RubyModule declaringModule,
            String name,
            Object value,
            boolean isPrivate,
            boolean autoload,
            boolean isDeprecated,
            SourceSection sourceSection) {
        this(
                declaringModule,
                name,
                value,
                isPrivate,
                autoload ? new AutoloadConstant(value) : null,
                false,
                isDeprecated,
                sourceSection);
    }

    private RubyConstant(
            RubyModule declaringModule,
            String name,
            Object value,
            boolean isPrivate,
            AutoloadConstant autoloadConstant,
            boolean undefined,
            boolean isDeprecated,
            SourceSection sourceSection) {
        assert !undefined || autoloadConstant == null : "undefined and autoload are exclusive";

        this.declaringModule = declaringModule;
        this.name = name;
        this.value = value;
        this.isPrivate = isPrivate;
        this.isDeprecated = isDeprecated;
        this.autoloadConstant = autoloadConstant;
        this.undefined = undefined;
        this.sourceSection = sourceSection;
    }

    public RubyModule getDeclaringModule() {
        return declaringModule;
    }

    public String getName() {
        return name;
    }

    public boolean hasValue() {
        return !isAutoload() && !undefined;
    }

    public Object getValue() {
        assert hasValue();
        return value;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public RubyConstant withPrivate(boolean isPrivate) {
        if (isPrivate == this.isPrivate) {
            return this;
        } else {
            return new RubyConstant(
                    declaringModule,
                    name,
                    value,
                    isPrivate,
                    autoloadConstant,
                    undefined,
                    isDeprecated,
                    sourceSection);
        }
    }

    public RubyConstant withDeprecated() {
        if (this.isDeprecated()) {
            return this;
        } else {
            return new RubyConstant(
                    declaringModule,
                    name,
                    value,
                    isPrivate,
                    autoloadConstant,
                    undefined,
                    true,
                    sourceSection);
        }
    }

    public RubyConstant undefined() {
        assert isAutoload();
        return new RubyConstant(declaringModule, name, null, isPrivate, null, true, isDeprecated, sourceSection);
    }

    @TruffleBoundary
    public boolean isVisibleTo(RubyContext context, LexicalScope lexicalScope, RubyModule module) {
        assert lexicalScope == null || lexicalScope.getLiveModule() == module;

        if (!isPrivate) {
            return true;
        }

        // Look in lexical scope
        if (lexicalScope != null) {
            while (lexicalScope != context.getRootLexicalScope()) {
                if (lexicalScope.getLiveModule() == declaringModule) {
                    return true;
                }
                lexicalScope = lexicalScope.getParent();
            }
        }

        // Look in ancestors
        if (module instanceof RubyClass) {
            for (RubyModule included : module.fields.ancestors()) {
                if (included != module && included == declaringModule) {
                    return true;
                }
            }
        }

        // Allow Object constants if looking with lexical scope.
        if (lexicalScope != null && context.getCoreLibrary().objectClass == declaringModule) {
            return true;
        }

        return false;
    }

    public boolean isUndefined() {
        return undefined;
    }

    public boolean isAutoload() {
        return autoloadConstant != null;
    }

    public AutoloadConstant getAutoloadConstant() {
        return autoloadConstant;
    }

    @Override
    public void getAdjacentObjects(Set<Object> adjacent) {
        if (ObjectGraph.isRubyObject(value)) {
            adjacent.add(value);
        }
    }

    @Override
    public String toString() {
        return getDeclaringModule().fields.getName() + "::" + getName();
    }

}
