/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.CoreExceptions;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.core.string.CoreStrings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.CoreSymbols;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.AllocationTracing;

import java.math.BigInteger;

/** Base of all Ruby nodes */
@GenerateInline(value = false, inherit = true)
@TypeSystemReference(RubyTypes.class)
@ImportStatic(RubyGuards.class)
public abstract class RubyBaseNode extends Node {

    public static final Object[] EMPTY_ARGUMENTS = ArrayUtils.EMPTY_ARRAY;

    public static final Nil nil = Nil.INSTANCE;

    public static final int MAX_EXPLODE_SIZE = 16;

    @Idempotent
    protected boolean isSingleContext() {
        return getLanguage().singleContext;
    }

    protected static Object nilToNull(Object value) {
        return value == nil ? null : value;
    }

    protected static Object nullToNil(Object value) {
        return value == null ? nil : value;
    }

    /** Variants for {@link com.oracle.truffle.api.library.Library}. The node argument should typically be {@code node}
     * with {@code @Bind Node node} */
    public static void profileAndReportLoopCount(Node node, LoopConditionProfile loopProfile, int count) {
        // Checkstyle: stop
        loopProfile.profileCounted(count);
        LoopNode.reportLoopCount(node, count);
        // Checkstyle: resume
    }

    protected void profileAndReportLoopCount(LoopConditionProfile loopProfile, int count) {
        profileAndReportLoopCount(this, loopProfile, count);
    }

    public static void profileAndReportLoopCount(Node node, InlinedLoopConditionProfile loopProfile, int count) {
        // Checkstyle: stop
        loopProfile.profileCounted(node, count);
        LoopNode.reportLoopCount(node, count);
        // Checkstyle: resume
    }

    protected void profileAndReportLoopCount(LoopConditionProfile loopProfile, long count) {
        // Checkstyle: stop
        loopProfile.profileCounted(count);
        reportLongLoopCount(count);
        // Checkstyle: resume
    }

    protected static void profileAndReportLoopCount(Node node, InlinedLoopConditionProfile loopProfile, long count) {
        // Checkstyle: stop
        loopProfile.profileCounted(node, count);
        reportLongLoopCount(node, count);
        // Checkstyle: resume
    }

    protected static void reportLongLoopCount(Node node, long count) {
        assert count >= 0L;
        // Checkstyle: stop
        LoopNode.reportLoopCount(node, count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
        // Checkstyle: resume
    }

    protected void reportLongLoopCount(long count) {
        assert count >= 0L;
        // Checkstyle: stop
        LoopNode.reportLoopCount(this, count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
        // Checkstyle: resume
    }

    /** This is very rarely necessary, use {@code @Bind Node node} instead, which always returns an adopted Node. */
    protected static Node getAdoptedNode(Node node) {
        boolean adoptable = node != null && node.isAdoptable();
        CompilerAsserts.partialEvaluationConstant(adoptable);
        if (adoptable) {
            return node;
        } else {
            return EncapsulatingNodeReference.getCurrent().get();
        }
    }

    protected final RubyLanguage getLanguage() {
        return getLanguage(this);
    }

    protected static RubyLanguage getLanguage(Node node) {
        return RubyLanguage.get(node);
    }

    protected final RubyContext getContext() {
        return getContext(this);
    }

    protected static RubyContext getContext(Node node) {
        return RubyContext.get(node);
    }

    // Helpers methods for terseness. They are `final` so we ensure they are not needlessly redeclared in subclasses.

    protected final RubyEncoding getLocaleEncoding() {
        return getContext().getEncodingManager().getLocaleEncoding();
    }

    protected static RubyBignum createBignum(BigInteger value) {
        return BignumOperations.createBignum(value);
    }

    protected final RubySymbol getSymbol(String name) {
        return getSymbol(this, name);
    }

    protected static RubySymbol getSymbol(Node node, String name) {
        return getLanguage(node).getSymbol(name);
    }

    protected final RubySymbol getSymbol(AbstractTruffleString name, RubyEncoding encoding) {
        return getSymbol(this, name, encoding);
    }

    protected static RubySymbol getSymbol(Node node, AbstractTruffleString name, RubyEncoding encoding) {
        return getLanguage(node).getSymbol(name, encoding);
    }

    protected final RubySymbol getSymbol(AbstractTruffleString name, RubyEncoding encoding, boolean preserveSymbol) {
        return getLanguage().getSymbol(name, encoding, preserveSymbol);
    }

    protected static RubySymbol getSymbol(Node node, AbstractTruffleString name, RubyEncoding encoding,
            boolean preserveSymbol) {
        return getLanguage(node).getSymbol(name, encoding, preserveSymbol);
    }

    protected final CoreStrings coreStrings() {
        return coreStrings(this);
    }

    protected static CoreStrings coreStrings(Node node) {
        return getLanguage(node).coreStrings;
    }

    protected final CoreSymbols coreSymbols() {
        return coreSymbols(this);
    }

    protected static CoreSymbols coreSymbols(Node node) {
        return getLanguage(node).coreSymbols;
    }

    protected final RubyArray createArray(Object store, int size) {
        return createArray(this, store, size);
    }

    protected static RubyArray createArray(Node node, Object store, int size) {
        return ArrayHelpers.createArray(getContext(node), getLanguage(node), store, size);
    }

    protected final RubyArray createArray(int[] store) {
        return createArray(this, store);
    }

    protected static RubyArray createArray(Node node, int[] store) {
        return ArrayHelpers.createArray(getContext(node), getLanguage(node), store);
    }

    protected final RubyArray createArray(long[] store) {
        return createArray(this, store);
    }

    protected static RubyArray createArray(Node node, long[] store) {
        return ArrayHelpers.createArray(getContext(node), getLanguage(node), store);
    }

    protected final RubyArray createArray(Object[] store) {
        return createArray(this, store);
    }

    public static RubyArray createArray(Node node, Object[] store) {
        return ArrayHelpers.createArray(getContext(node), getLanguage(node), store);
    }

    protected final RubyArray createEmptyArray() {
        return createEmptyArray(this);
    }

    protected static RubyArray createEmptyArray(Node node) {
        return ArrayHelpers.createEmptyArray(getContext(node), getLanguage(node));
    }

    protected final RubyString createString(TruffleString tstring, RubyEncoding encoding) {
        return createString(this, tstring, encoding);
    }

    protected static RubyString createString(Node node, TruffleString tstring, RubyEncoding encoding) {
        final RubyString instance = new RubyString(
                coreLibrary(node).stringClass,
                getLanguage(node).stringShape,
                false,
                tstring,
                encoding);
        AllocationTracing.trace(instance, node);
        return instance;
    }

    protected final RubyString createStringCopy(TruffleString.AsTruffleStringNode asTruffleStringNode,
            AbstractTruffleString tstring, RubyEncoding encoding) {
        return createStringCopy(this, asTruffleStringNode, tstring, encoding);
    }

    protected static RubyString createStringCopy(Node node, TruffleString.AsTruffleStringNode asTruffleStringNode,
            AbstractTruffleString tstring, RubyEncoding encoding) {
        final TruffleString copy = asTruffleStringNode.execute(tstring, encoding.tencoding);
        final RubyString instance = new RubyString(
                coreLibrary(node).stringClass,
                getLanguage(node).stringShape,
                false,
                copy,
                encoding);
        AllocationTracing.trace(instance, node);
        return instance;
    }

    protected final RubyString createMutableString(MutableTruffleString tstring, RubyEncoding encoding) {
        final RubyString instance = new RubyString(
                coreLibrary().stringClass,
                getLanguage().stringShape,
                false,
                tstring,
                encoding);
        AllocationTracing.trace(instance, this);
        return instance;
    }

    protected final RubyString createString(TStringWithEncoding tStringWithEncoding) {
        return createString(this, tStringWithEncoding);
    }

    protected static RubyString createString(Node node, TStringWithEncoding tStringWithEncoding) {
        return createString(node, tStringWithEncoding.tstring, tStringWithEncoding.encoding);
    }

    protected final RubyString createString(TruffleString.FromByteArrayNode fromByteArrayNode, byte[] bytes,
            RubyEncoding encoding) {
        return createString(this, fromByteArrayNode, bytes, encoding);
    }

    protected static RubyString createString(Node node, TruffleString.FromByteArrayNode fromByteArrayNode, byte[] bytes,
            RubyEncoding encoding) {
        var tstring = fromByteArrayNode.execute(bytes, encoding.tencoding, false);
        return createString(node, tstring, encoding);
    }

    protected final RubyString createString(TruffleString.FromJavaStringNode fromJavaStringNode, String javaString,
            RubyEncoding encoding) {
        return createString(this, fromJavaStringNode, javaString, encoding);
    }

    public static RubyString createString(Node node, TruffleString.FromJavaStringNode fromJavaStringNode,
            String javaString, RubyEncoding encoding) {
        var tstring = fromJavaStringNode.execute(javaString, encoding.tencoding);
        return createString(node, tstring, encoding);
    }

    protected final RubyString createSubString(TruffleString.SubstringByteIndexNode substringNode,
            RubyStringLibrary strings, Object source, int byteOffset, int byteLength) {
        return createSubString(substringNode, strings.getTString(this, source), strings.getEncoding(this, source),
                byteOffset,
                byteLength);
    }

    protected final RubyString createSubString(TruffleString.SubstringByteIndexNode substringNode,
            AbstractTruffleString tstring, RubyEncoding encoding, int byteOffset, int byteLength) {
        return createSubString(this, substringNode, tstring, encoding, byteOffset, byteLength);
    }

    protected static RubyString createSubString(Node node, TruffleString.SubstringByteIndexNode substringNode,
            AbstractTruffleString tstring, RubyEncoding encoding, int byteOffset, int byteLength) {
        final TruffleString substring = substringNode.execute(tstring, byteOffset, byteLength, encoding.tencoding,
                true);
        return createString(node, substring, encoding);
    }

    protected final RubyString createSubString(TruffleString.SubstringNode substringNode,
            AbstractTruffleString tstring, RubyEncoding encoding, int codePointOffset, int codePointLength) {
        final TruffleString substring = substringNode.execute(tstring, codePointOffset, codePointLength,
                encoding.tencoding,
                true);
        return createString(substring, encoding);
    }

    protected final CoreLibrary coreLibrary() {
        return coreLibrary(this);
    }

    protected static CoreLibrary coreLibrary(Node node) {
        return getContext(node).getCoreLibrary();
    }

    protected final CoreExceptions coreExceptions() {
        return coreExceptions(this);
    }

    protected static CoreExceptions coreExceptions(Node node) {
        return getContext(node).getCoreExceptions();
    }

    protected final int getDefaultCacheLimit() {
        return getLanguage().options.DEFAULT_CACHE;
    }

    protected final int getDynamicObjectCacheLimit() {
        return getLanguage().options.INSTANCE_VARIABLE_CACHE;
    }

    protected final int getInteropCacheLimit() {
        return getLanguage().options.METHOD_LOOKUP_CACHE;
    }

}
