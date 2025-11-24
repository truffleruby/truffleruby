/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;

import java.util.List;

@ExportLibrary(InteropLibrary.class)
public final class RubyScope implements TruffleObject {

    public static final String RECEIVER_MEMBER = "self";

    public final MaterializedFrame frame;
    public final RubyBinding binding;
    public final RubyNode node;

    public RubyScope(RubyContext context, RubyLanguage language, MaterializedFrame frame, RubyNode node) {
        assert frame != null;
        this.frame = frame;
        this.binding = BindingNodes.createBinding(context, language, frame);
        this.node = node;
    }

    @ExportMessage
    Object toDisplayString(boolean allowSideEffects) {
        return hasScopeParent() ? "block" : "method";
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    boolean hasScopeParent() {
        return RubyArguments.getDeclarationFrame(frame) != null;
    }

    @ExportMessage
    Object getScopeParent(
            @Bind Node node) throws UnsupportedMessageException {
        final MaterializedFrame parentFrame = RubyArguments.getDeclarationFrame(frame);
        if (parentFrame != null) {
            // Do no set the node for parent scopes, as we don't know it and
            // to recognize them as Local/Closure and not Block scopes.
            final RubyLanguage language = RubyLanguage.get(node);
            final RubyContext context = RubyContext.get(node);
            return new RubyScope(context, language, parentFrame, null);
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    public boolean hasSourceLocation() {
        RootNode rootNode = node == null ? null : node.getRootNode();
        return rootNode != null && rootNode.getSourceSection() != null;
    }

    @TruffleBoundary
    @ExportMessage
    public SourceSection getSourceLocation() throws UnsupportedMessageException {
        RootNode rootNode = node == null ? null : node.getRootNode();
        if (rootNode == null) {
            throw UnsupportedMessageException.create();
        }
        final SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceSection;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return RubyLanguage.class;
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    static final class ReadMember {
        @Specialization(guards = "RECEIVER_MEMBER.equals(member)")
        static Object readSelf(RubyScope scope, String member) {
            return RubyArguments.getSelf(scope.frame);
        }

        @Specialization(guards = "!RECEIVER_MEMBER.equals(member)")
        static Object read(RubyScope scope, String member,
                @Cached @Exclusive BindingNodes.LocalVariableGetNode localVariableGetNode,
                @Bind Node node)
                throws UnknownIdentifierException {
            try {
                return localVariableGetNode.execute(node, scope.binding, member);
            } catch (RaiseException e) {
                throw UnknownIdentifierException.create(member);
            }
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected Object getMembers(boolean includeInternal) {
        List<String> members = BindingNodes.LocalVariablesNode.listLocalVariablesWithDuplicates(frame, RECEIVER_MEMBER);
        return new VariableNamesObject(members.toArray(StringUtils.EMPTY_STRING_ARRAY));
    }

    @ExportMessage
    static final class IsMemberReadable {
        @Specialization(guards = "RECEIVER_MEMBER.equals(member)")
        static boolean readSelf(RubyScope scope, String member) {
            return true;
        }

        @Specialization(guards = "!RECEIVER_MEMBER.equals(member)")
        static boolean isMemberReadable(RubyScope scope, String member,
                @Cached @Exclusive BindingNodes.HasLocalVariableNode hasLocalVariableNode,
                @Bind Node node) {
            return hasLocalVariableNode.execute(node, scope.binding, member);
        }
    }

    @ExportMessage
    static final class IsMemberModifiable {
        @Specialization(guards = "RECEIVER_MEMBER.equals(member)")
        static boolean readSelf(RubyScope scope, String member) {
            return false;
        }

        @Specialization(guards = "!RECEIVER_MEMBER.equals(member)")
        static boolean isMemberModifiable(RubyScope scope, String member,
                @Cached @Exclusive BindingNodes.HasLocalVariableNode hasLocalVariableNode,
                @Bind Node node) {
            return hasLocalVariableNode.execute(node, scope.binding, member);
        }
    }


    @ExportMessage
    static final class WriteMember {
        @Specialization
        static void writeMember(RubyScope scope, String member, Object value,
                @CachedLibrary("scope") InteropLibrary interopLibrary,
                @Cached BindingNodes.LocalVariableSetNode localVariableSetNode,
                @Bind Node node) throws UnknownIdentifierException {
            if (interopLibrary.isMemberModifiable(scope, member)) {
                localVariableSetNode.execute(node, scope.binding, member, value);
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member) {
        return false;
    }
}
