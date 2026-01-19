/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.RubyContext;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.ImmutableStrings;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.ImmutableRubyObjectNotCopyable;
import org.truffleruby.core.string.TStringConstants;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import java.util.Objects;
import java.util.Set;

@ExportLibrary(InteropLibrary.class)
public final class RubyEncoding extends ImmutableRubyObjectNotCopyable
        implements ObjectGraphNode, Comparable<RubyEncoding> {

    /** Do NOT use {@link Encoding#toString()} or {@link Encoding#getName()} as they are incorrect for dummy encodings.
     * Instead, use {@link RubyEncoding#toString()} and {@link #getNameByteArray()}. */
    public final Encoding jcoding;
    public final TruffleString.Encoding tencoding;
    public final ImmutableRubyString name;
    public final byte[] nameByteArray;
    public final int index;

    // Copy these properties here for faster access and to make the fields final (most of these fields are not final in JCodings)
    public final boolean isDummy;
    public final boolean isAsciiCompatible;
    public final boolean isFixedWidth;
    public final boolean isSingleByte;
    public final boolean isUnicode;

    public RubyEncoding(Encoding jcoding, ImmutableRubyString name, int index) {
        assert index >= 0;
        assert name.getEncodingUncached() == Encodings.US_ASCII;
        this.jcoding = Objects.requireNonNull(jcoding);

        var tencoding = TruffleString.Encoding.fromJCodingName(jcoding.toString());
        this.tencoding = Objects.requireNonNull(tencoding);
        this.name = Objects.requireNonNull(name);
        this.nameByteArray = name.tstring.copyToByteArrayUncached(TruffleString.Encoding.US_ASCII);
        this.index = index;

        this.isDummy = jcoding.isDummy();
        this.isAsciiCompatible = jcoding.isAsciiCompatible();
        this.isFixedWidth = jcoding.isFixedWidth();
        this.isSingleByte = jcoding.isSingleByte();
        this.isUnicode = jcoding.isUnicode();
    }

    // Special constructor to define US-ASCII encoding which is used for RubyEncoding names
    public RubyEncoding(int index) {
        this.jcoding = Objects.requireNonNull(USASCIIEncoding.INSTANCE);
        this.tencoding = Objects.requireNonNull(TruffleString.Encoding.US_ASCII);
        this.name = Objects.requireNonNull(
                ImmutableStrings.createAndCacheLater(TStringConstants.US_ASCII, this));
        this.nameByteArray = name.tstring.copyToByteArrayUncached(TruffleString.Encoding.US_ASCII);
        this.index = index;

        var jcoding = this.jcoding;
        this.isDummy = jcoding.isDummy();
        this.isAsciiCompatible = jcoding.isAsciiCompatible();
        this.isFixedWidth = jcoding.isFixedWidth();
        this.isSingleByte = jcoding.isSingleByte();
        this.isUnicode = jcoding.isUnicode();
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, name);
    }

    @Override
    public String toString() {
        return name.toString();
    }

    public byte[] getNameByteArray() {
        return nameByteArray;
    }

    // region InteropLibrary messages
    @ExportMessage
    protected Object toDisplayString(boolean allowSideEffects,
            @Cached DispatchNode dispatchNode,
            @Cached KernelNodes.ToSNode kernelToSNode) {
        if (allowSideEffects) {
            return dispatchNode.call(this, "inspect");
        } else {
            return kernelToSNode.execute(this);
        }
    }

    @ExportMessage
    protected boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    protected RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().encodingClass;
    }
    // endregion

    @Override
    public int compareTo(RubyEncoding o) {
        if (index != o.index) {
            return index - o.index;
        } else {
            return name.tstring.compareBytesUncached(o.name.tstring, Encodings.US_ASCII.tencoding);
        }
    }
}
