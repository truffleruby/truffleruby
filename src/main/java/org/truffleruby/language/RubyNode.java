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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.debug.RubyScope;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.stdlib.CoverageManager;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.debug.RubyScope.RECEIVER_MEMBER;

/** RubyNode has source, execute, and is instrument-able. If there is no need for source and instrument-able,
 * {@link RubyBaseNode} or {@link RubyBaseNodeWithExecute} should be used instead to save footprint. RubyNode does not
 * have any fields which would prevent using @GenerateUncached. It should never be subclassed directly, use
 * {@link RubyContextSourceNode} (cannot be uncached, but avoids the DSL generating 6 field accessors for each
 * subclass). SourceRubyNode is not defined since there was no use for it for now. */
@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public abstract class RubyNode extends RubyBaseNodeWithExecute implements InstrumentableNode {

    public static final RubyNode[] EMPTY_ARRAY = new RubyNode[0];

    private static final byte FLAG_NEWLINE = 0;       // 1<<0 = 1
    private static final byte FLAG_COVERAGE_LINE = 1; // 1<<1 = 2
    private static final byte FLAG_CALL = 2;          // 1<<2 = 4
    private static final byte FLAG_ROOT = 3;          // 1<<3 = 8

    protected static final int NO_SOURCE = -1;
    private static final int UNAVAILABLE_SOURCE_SECTION_LENGTH = -1;

    // Used when the return value of a node is ignored syntactically.
    // Returns Nil instead of void to force RubyNodeWrapper to call `delegateNode.executeVoid(frame)`, otherwise
    // it would call `delegateNode.execute(frame)` in `execute()` which is semantically incorrect for defined?().
    public abstract Nil executeVoid(VirtualFrame frame);

    // Declared abstract here so the instrumentation wrapper delegates it
    public abstract Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context);

    protected static Object defaultIsDefined(Node currentNode) {
        assert !(currentNode instanceof WrapperNode);
        return FrozenStrings.EXPRESSION;
    }

    // Source

    protected abstract int getSourceCharIndex();

    protected abstract void setSourceCharIndex(int sourceCharIndex);

    protected abstract int getSourceLength();

    protected abstract void setSourceLength(int sourceLength);

    public boolean hasSource() {
        return isAdoptable() && getSourceCharIndex() != NO_SOURCE;
    }

    public void unsafeSetSourceSection(SourceSection sourceSection) {
        assert !hasSource();

        if (sourceSection.isAvailable()) {
            unsafeSetSourceSection(sourceSection.getCharIndex(), sourceSection.getCharLength());
        } else {
            unsafeSetSourceSection(0, UNAVAILABLE_SOURCE_SECTION_LENGTH);
        }
    }

    public void unsafeSetSourceSection(int charIndex, int sourceLength) {
        assert !hasSource();

        // The only valid case for a (0,0) SourceSection is the RootNode SourceSection of eval("").
        // We handle that case specially by using an unavailable SourceSection, so then every (0,0) is a bug.
        assert !(sourceLength == 0 && charIndex == 0);

        setSourceCharIndex(charIndex);
        setSourceLength(sourceLength);
    }

    public RubyNode copySourceSection(RubyNode from) {
        if (from.hasSource()) {
            unsafeSetSourceSection(from.getSourceCharIndex(), from.getSourceLength());
        }
        return this;
    }

    @Override
    @TruffleBoundary
    public SourceSection getSourceSection() {
        if (!hasSource()) {
            return null;
        } else {
            final Source source = getSource();
            if (source == null) {
                return null;
            }

            int sourceLength = getSourceLength();
            if (sourceLength == UNAVAILABLE_SOURCE_SECTION_LENGTH) {
                return source.createUnavailableSection();
            } else {
                return source.createSection(getSourceCharIndex(), sourceLength);
            }
        }

    }

    private Source getSource() {
        final RootNode rootNode = getRootNode();

        if (rootNode == null) {
            return null;
        }

        final SourceSection sourceSection = rootNode.getSourceSection();

        if (sourceSection == null) {
            return null;
        }

        return sourceSection.getSource();
    }

    @Override
    public String toString() {
        return super.toString() + " at " + RubyLanguage.fileLineRange(getSourceSection());
    }

    // Instrumentation

    @Override
    public boolean isInstrumentable() {
        return hasSource();
    }

    protected abstract byte getFlags();

    protected abstract void setFlags(byte flags);

    private void setFlag(byte flag) {
        setFlags((byte) (getFlags() | 1 << flag));
    }

    public void unsafeSetIsNewLine() {
        setFlag(FLAG_NEWLINE);
    }

    public void unsafeSetIsCoverageLine() {
        setFlag(FLAG_COVERAGE_LINE);
    }

    public void unsafeSetIsCall() {
        setFlag(FLAG_CALL);
    }

    public void unsafeSetIsRoot() {
        setFlag(FLAG_ROOT);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        byte flags = getFlags();
        // NOTE: TraceManager.CallTag for set_trace_func 'call' event is in the callee like in MRI
        if (tag == TraceManager.CallTag.class) {
            return isTag(flags, FLAG_CALL);
        }

        if (tag == TraceManager.LineTag.class || tag == StandardTags.StatementTag.class) {
            return isTag(flags, FLAG_NEWLINE);
        }

        if (tag == CoverageManager.LineTag.class) {
            return isTag(flags, FLAG_COVERAGE_LINE);
        }

        if (tag == StandardTags.RootTag.class) {
            return isTag(flags, FLAG_ROOT);
        }

        return false;
    }

    private static boolean isTag(byte flags, byte flag) {
        return ((flags >> flag) & 1) == 1;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new RubyNodeWrapper(this, probe);
    }

    public static RubyNode unwrapNode(RubyNode node) {
        if (node instanceof WrapperNode wrapperNode) {
            return (RubyNode) wrapperNode.getDelegateNode();
        } else {
            return node;
        }
    }

    /** Return whether nodes following this one can ever be executed. In most cases this will be true, but some nodes
     * such as those representing a return or other control flow may wish to override this. */
    public boolean isContinuable() {
        return true;
    }

    /** Indicates whether this node can return a new version of itself combined with the next node, which may help with
     * analysis and optimisation. */
    public boolean canSubsumeFollowing() {
        return false;
    }

    /** Combine this node with the next node. Any node which returns true for {@link #canSubsumeFollowing} must override
     * this method. Any new node created in this method should use {@link #copySourceSection(RubyNode)}. */
    public RubyNode subsumeFollowing(RubyNode following) {
        throw new UnsupportedOperationException();
    }

    /** Return a possibly simplified version of this node. This is only called if the node is in tail position. Any new
     * node created in this method should use {@link #copySourceSection(RubyNode)}. */
    public RubyNode simplifyAsTailExpression() {
        return this;
    }


    // NodeLibrary

    @ExportMessage
    boolean accepts(
            @Cached(value = "this", adopt = false) RubyNode cachedNode) {
        return this == cachedNode;
    }

    @ExportMessage
    final boolean hasScope(Frame frame) {
        // hasScope == isAdoptable(), getParent() != null is a fast way to check if adoptable.
        return this.getParent() != null;
    }

    @ExportMessage
    final Object getScope(Frame frame, boolean nodeEnter,
            @Bind Node node) throws UnsupportedMessageException {
        if (hasScope(frame)) {
            return new RubyScope(RubyContext.get(node), RubyLanguage.get(node), frame.materialize(), this);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    final boolean hasReceiverMember(Frame frame) {
        return frame != null;
    }

    @ExportMessage
    final Object getReceiverMember(Frame frame) throws UnsupportedMessageException {
        if (frame == null) {
            throw UnsupportedMessageException.create();
        }
        return RECEIVER_MEMBER;
    }

    @ExportMessage
    boolean hasRootInstance(Frame frame) {
        return frame != null;
    }

    @ExportMessage
    Object getRootInstance(Frame frame,
            @Bind Node node) throws UnsupportedMessageException {
        if (frame == null) {
            throw UnsupportedMessageException.create();
        }
        final Object self = RubyArguments.getSelf(frame);
        final InternalMethod method = RubyArguments.getMethod(frame);
        return new RubyMethod(
                RubyContext.get(node).getCoreLibrary().methodClass,
                RubyLanguage.get(node).methodShape,
                self,
                method);
    }

    @Override
    public abstract RubyNode cloneUninitialized();

    public static RubyNode cloneUninitialized(RubyNode node) {
        return (node == null) ? null : node.cloneUninitialized();
    }

    protected static RubyNode[] cloneUninitialized(RubyNode[] nodes) {
        if (nodes == null) {
            return null;
        }

        RubyNode[] copies = new RubyNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copies[i] = nodes[i].cloneUninitialized();
        }
        return copies;
    }

}
