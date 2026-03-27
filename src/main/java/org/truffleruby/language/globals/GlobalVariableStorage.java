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
package org.truffleruby.language.globals;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.RubyContext;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class GlobalVariableStorage {

    public static final Object UNSET_VALUE = NotProvided.INSTANCE;

    private final CyclicAssumption unchangedAssumption = new CyclicAssumption("global variable unchanged");
    private int changes = 0;

    // This really means @CompilationFinal for compilation and volatile in interpreter
    @CompilationFinal private volatile boolean assumeConstant = true;

    private volatile Object value;

    private final RubyProc getter;
    private final RubyProc setter;
    private final RubyProc isDefined;

    public GlobalVariableStorage(RubyProc getter, RubyProc setter, RubyProc isDefined) {
        this(UNSET_VALUE, getter, setter, isDefined);
    }

    GlobalVariableStorage(Object value, RubyProc getter, RubyProc setter, RubyProc isDefined) {
        assert ((getter == null) == (setter == null)) && ((getter == null) == (isDefined == null));
        assert value != null;

        this.value = value;
        this.getter = getter;
        this.setter = setter;
        this.isDefined = isDefined;
    }

    @NeverDefault
    public Object getValue() {
        Object currentValue = value;
        return currentValue == UNSET_VALUE ? Nil.INSTANCE : currentValue;
    }

    @NeverDefault
    public Object getRawValue() {
        return value;
    }

    public boolean isDefined() {
        return value != UNSET_VALUE;
    }

    public boolean isSimple() {
        return !hasHooks();
    }

    public boolean hasHooks() {
        return getter != null;
    }

    public RubyProc getGetter() {
        return getter;
    }

    public RubyProc getSetter() {
        return setter;
    }

    public RubyProc getIsDefined() {
        return isDefined;
    }

    public Assumption getUnchangedAssumption() {
        return unchangedAssumption.getAssumption();
    }

    @Idempotent
    public boolean isAssumeConstant() {
        return assumeConstant;
    }

    public void setValueInternal(Object value) {
        assert value != null;
        this.value = value;
    }

    @TruffleBoundary
    public void updateAssumeConstant(RubyContext context) {
        synchronized (this) {
            if (!assumeConstant) {
                // Compiled code didn't see that we do not assumeConstant anymore
                return;
            }

            // <= because the initial assignment is counted as an invalidation
            if (changes <= context.getOptions().GLOBAL_VARIABLE_MAX_INVALIDATIONS) {
                changes++;
                unchangedAssumption.invalidate();
            } else {
                assumeConstant = false;
                unchangedAssumption.getAssumption().invalidate();
            }
        }
    }

    @TruffleBoundary
    public void noLongerAssumeConstant() {
        synchronized (this) {
            assumeConstant = false;
            unchangedAssumption.getAssumption().invalidate();
        }
    }

}
