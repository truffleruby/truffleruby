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

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.range.RubyIntOrLongRange;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NoImplicitCastsToLong;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

// Specializations are ordered by their frequency on railsbench using --engine.SpecializationStatistics
@GenerateInline
@GenerateCached(false)
@GenerateUncached
@TypeSystemReference(NoImplicitCastsToLong.class)
public abstract class ImmutableClassNode extends RubyBaseNode {

    public final RubyClass execute(Node node, Object value) {
        return execute(node, value, coreLibrary(node));
    }

    protected abstract RubyClass execute(Node node, Object value, CoreLibrary coreLibrary);

    @Specialization
    static RubyClass metaClassInt(int value, CoreLibrary coreLibrary) {
        return coreLibrary.integerClass;
    }

    @Specialization
    static RubyClass metaClassImmutableString(ImmutableRubyString value, CoreLibrary coreLibrary) {
        return coreLibrary.stringClass;
    }

    @Specialization
    static RubyClass metaClassSymbol(RubySymbol value, CoreLibrary coreLibrary) {
        return coreLibrary.symbolClass;
    }

    @Specialization
    static RubyClass metaClassNil(Nil value, CoreLibrary coreLibrary) {
        return coreLibrary.nilClass;
    }

    @Specialization(guards = "value")
    static RubyClass metaClassTrue(boolean value, CoreLibrary coreLibrary) {
        return coreLibrary.trueClass;
    }

    @Specialization(guards = "!value")
    static RubyClass metaClassFalse(boolean value, CoreLibrary coreLibrary) {
        return coreLibrary.falseClass;
    }

    @Specialization
    static RubyClass metaClassLong(long value, CoreLibrary coreLibrary) {
        return coreLibrary.integerClass;
    }

    @Specialization
    static RubyClass metaClassEncoding(RubyEncoding value, CoreLibrary coreLibrary) {
        return coreLibrary.encodingClass;
    }

    @Specialization
    static RubyClass metaClassRegexp(RubyRegexp value, CoreLibrary coreLibrary) {
        return coreLibrary.regexpClass;
    }

    @Specialization
    static RubyClass metaClassRange(RubyIntOrLongRange value, CoreLibrary coreLibrary) {
        return coreLibrary.rangeClass;
    }

    @Specialization
    static RubyClass metaClassDouble(double value, CoreLibrary coreLibrary) {
        return coreLibrary.floatClass;
    }

    @Specialization
    static RubyClass metaClassBignum(RubyBignum value, CoreLibrary coreLibrary) {
        return coreLibrary.integerClass;
    }

}
