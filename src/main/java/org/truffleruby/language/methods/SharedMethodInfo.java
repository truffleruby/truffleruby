/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.support.DetailedInspectingSupport;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.parser.ArgumentDescriptor;

import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.parser.OpenModule;
import org.truffleruby.parser.ParserContext;

import java.util.Arrays;

/** SharedMethodInfo represents static information from the parser for either a method definition or a block like its
 * name, SourceSection, etc. Such information is always "original" since it comes from the source as opposed to
 * "aliased" (e.g. the aliased name of a method). In contrast, {@link InternalMethod} are runtime objects containing
 * properties that change for a method. */
public final class SharedMethodInfo implements DetailedInspectingSupport {

    private final SourceSection sourceSection;
    /** LexicalScope if it can be determined statically at parse time, otherwise null */
    private final LexicalScope staticLexicalScope;
    private final Arity arity;
    /** The original name of the method (of the surrounding method if this is a block). Does not change when aliased.
     * Looks like "foo". */
    private final String methodName;
    /** The "static" name of this method at parse time, such as "M::C#foo", "M::C.foo", "<module:Inner>", "block (2
     * levels) in M::C.foo" or "block (2 levels) in <module:Inner>". */
    private final String parseName;
    /** The runtime name, derived from the Module#name of the declaring module owning this method. When multiple modules
     * with different names report owning this method, this falls back to the parseName instead. Used for tools and
     * backtraces. */
    private String runtimeName = null;
    private String descriptiveNameAndSource = null;

    private final int blockDepth;
    /** The SharedMethodInfo corresponding to the surrounding method: null for methods, non-null for blocks */
    private final SharedMethodInfo methodSharedMethodInfo;
    /** Extra information. If blockDepth > 0 then it is the name of the method containing this block. */
    private final String notes;
    private final ArgumentDescriptor[] argumentDescriptors;

    public static SharedMethodInfo forMethod(
            SourceSection sourceSection,
            LexicalScope staticLexicalScope,
            Arity arity,
            String methodName,
            String parseName,
            String notes,
            ArgumentDescriptor[] argumentDescriptors) {
        return new SharedMethodInfo(sourceSection, staticLexicalScope, arity, methodName, parseName, notes, 0, null,
                argumentDescriptors);
    }

    public static SharedMethodInfo forBlock(
            SourceSection sourceSection,
            LexicalScope staticLexicalScope,
            Arity arity,
            String methodName,
            String parseName,
            String notes,
            int blockDepth,
            SharedMethodInfo methodSharedMethodInfo,
            ArgumentDescriptor[] argumentDescriptors) {
        return new SharedMethodInfo(sourceSection, staticLexicalScope, arity, methodName, parseName, notes, blockDepth,
                methodSharedMethodInfo, argumentDescriptors);
    }

    private SharedMethodInfo(
            SourceSection sourceSection,
            LexicalScope staticLexicalScope,
            Arity arity,
            String methodName,
            String parseName,
            String notes,
            int blockDepth,
            SharedMethodInfo methodSharedMethodInfo,
            ArgumentDescriptor[] argumentDescriptors) {
        assert (methodSharedMethodInfo == null) == (blockDepth == 0);
        this.sourceSection = sourceSection;
        this.staticLexicalScope = staticLexicalScope;
        this.arity = arity;
        this.methodName = methodName;
        this.parseName = parseName;
        this.notes = notes;
        this.blockDepth = blockDepth;
        this.methodSharedMethodInfo = methodSharedMethodInfo;
        this.argumentDescriptors = argumentDescriptors;
    }

    public SharedMethodInfo forDefineMethod(RubyModule declaringModule, String methodName, RubyProc proc) {
        // no longer a block
        return forMethod(
                sourceSection,
                staticLexicalScope,
                proc.arity,
                methodName,
                moduleAndMethodNameIfModuleIsFullyNamed(declaringModule, methodName, proc.arity),
                null,
                proc.argumentDescriptors);
    }

    public SharedMethodInfo convertMethodMissingToMethod(RubyModule declaringModule, String methodName) {
        var effectiveArity = arity.consumingFirstRequired();
        return forMethod(
                sourceSection,
                staticLexicalScope,
                effectiveArity,
                methodName,
                moduleAndMethodNameIfModuleIsFullyNamed(declaringModule, methodName, effectiveArity),
                notes,
                ArgumentDescriptor.ANY_UNNAMED);
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public LexicalScope getStaticLexicalScope() {
        assert staticLexicalScope != null;
        return staticLexicalScope;
    }

    public LexicalScope getStaticLexicalScopeOrNull() {
        return staticLexicalScope;
    }

    public Arity getArity() {
        return arity;
    }

    public ArgumentDescriptor[] getRawArgumentDescriptors() {
        return argumentDescriptors;
    }

    public ArgumentDescriptor[] getArgumentDescriptors() {
        return argumentDescriptors == null ? arity.toUnnamedArgumentDescriptors() : argumentDescriptors;
    }

    public boolean isMethod() {
        return blockDepth == 0;
    }

    public boolean isBlock() {
        return blockDepth > 0;
    }

    @TruffleBoundary
    public boolean isModuleBody() {
        boolean isModuleBody = arity == Arity.MODULE_BODY;
        assert !(isModuleBody && isBlock()) : this;
        assert isModuleBody == (isMethod() && isModuleBody(getMethodName()));
        return isModuleBody;
    }

    public static boolean isModuleBody(String name) {
        // Handles cases: <main> | <top (required)> | <module: | <class: | <singleton
        if (name.startsWith("<")) {
            assert name.equals(ParserContext.TOP_LEVEL_FIRST.getTopLevelName()) ||
                    name.equals(ParserContext.TOP_LEVEL.getTopLevelName()) ||
                    name.startsWith(OpenModule.MODULE.getPrefix()) ||
                    name.startsWith(OpenModule.CLASS.getPrefix()) ||
                    name.startsWith(OpenModule.SINGLETON_CLASS.getPrefix()) : name;
            return true;
        } else {
            return false;
        }
    }

    /** Returns the method name on its own. Can start with "<" like "<module:Inner>" for module bodies. */
    public String getMethodName() {
        return methodName;
    }

    public String getParseName() {
        return parseName;
    }

    public String getRuntimeName() {
        if (runtimeName != null) {
            return runtimeName;
        } else {
            if (isBlock()) {
                if (methodSharedMethodInfo.runtimeName != null) {
                    runtimeName = getBlockName(blockDepth, methodSharedMethodInfo.runtimeName);
                    return runtimeName;
                } else {
                    // The method does not have a runtime name yet, so in that case parseName is correct
                    return parseName;
                }
            } else {
                return parseName;
            }
        }
    }

    @TruffleBoundary
    public void setupRuntimeName(RubyModule module) {
        final String computedName = moduleAndMethodNameIfModuleIsFullyNamed(module, getMethodName(), arity);
        if (this.runtimeName == null) {
            this.runtimeName = computedName;
        } else if (computedName.equals(this.runtimeName)) {
            // OK
        } else {
            // Different module names, just use the parse name then
            this.runtimeName = parseName;
        }
    }

    /** See also {@link org.truffleruby.core.module.ModuleOperations#constantName}. Version without context which
     * returns "Object::A" for top-level constant A. */
    public static String moduleAndConstantName(RubyModule module, String constantName) {
        return module.fields.getName() + "::" + constantName;
    }

    /** Consider using {@link #moduleAndMethodNameIfModuleIsFullyNamed} instead */
    public static String moduleAndMethodName(RubyModule module, String methodName) {
        assert module != null && methodName != null;
        if (RubyGuards.isMetaClass(module)) {
            final RubyModule attached = (RubyModule) ((RubyClass) module).attached;
            return attached.getName() + "." + methodName;
        } else {
            return module.getName() + "#" + methodName;
        }
    }

    /** Returns "Module#method" if Module is fully named and "method" otherwise. This is the expected behavior for Ruby
     * backtraces. */
    public static String moduleAndMethodNameIfModuleIsFullyNamed(RubyModule module, String methodName, Arity arity) {
        assert module != null && methodName != null;

        if (arity == Arity.MODULE_BODY) {
            return methodName;
        }

        if (RubyGuards.isMetaClass(module)) {
            final RubyModule attached = (RubyModule) ((RubyClass) module).attached;
            if (attached.fields.hasFullName()) {
                return attached.getName() + "." + methodName;
            } else {
                return methodName;
            }
        } else {
            if (module.fields.hasFullName()) {
                return module.getName() + "#" + methodName;
            } else {
                return methodName;
            }
        }
    }

    public static String modulePathAndMethodName(String modulePath, String methodName, boolean onSingleton) {
        assert modulePath != null && methodName != null;
        if (onSingleton) {
            return modulePath + "." + methodName;
        } else {
            return modulePath + "#" + methodName;
        }
    }

    public static String getBlockName(int blockDepth, String methodName) {
        assert blockDepth > 0;
        if (blockDepth > 1) {
            return "block (" + blockDepth + " levels) in " + methodName;
        } else {
            return "block in " + methodName;
        }
    }

    public String getDescriptiveNameAndSource() {
        if (descriptiveNameAndSource == null) {
            String descriptiveName = parseName;
            if (hasNotes()) {
                if (descriptiveName.length() > 0) {
                    descriptiveName += " (" + notes + ")";
                } else {
                    descriptiveName += notes;
                }
            }

            if (!BacktraceFormatter.isAvailable(sourceSection)) {
                descriptiveNameAndSource = descriptiveName;
            } else {
                descriptiveNameAndSource = descriptiveName + " " + RubyLanguage.fileLineRange(sourceSection);
            }
        }

        return descriptiveNameAndSource;
    }

    public int getBlockDepth() {
        return blockDepth;
    }

    public SharedMethodInfo getMethodSharedMethodInfo() {
        return methodSharedMethodInfo;
    }

    private boolean hasNotes() {
        return notes != null && isMethod();
    }

    public String getNotes() {
        assert hasNotes();
        return notes;
    }

    @Override
    public String toString() {
        return getDescriptiveNameAndSource();
    }

    @Override
    public String toStringWithDetails() {
        final String string = Arrays.deepToString(argumentDescriptors);

        // skip sourceSection as far as it contains not accurate location details (provided by JRuby parser)
        return StringUtils.format(
                "SharedMethodInfo(staticLexicalScope = %s, arity = %s, methodName = %s, blockDepth = %s, parseName = %s, notes = %s, argumentDescriptors = %s)",
                staticLexicalScope, arity, methodName, blockDepth, parseName, notes, string);
    }

}
