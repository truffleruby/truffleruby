/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.DenyReplace;
import org.truffleruby.core.kernel.KernelNodes.RespondToNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.methods.LookupMethodNodeGen;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.MetaClassNodeGen;

/** Determines if an objects "responds to" a method, meaning the method can be looked up and is
 * {@link InternalMethod#isDefined() defined} and {@link InternalMethod#isImplemented() implemented}.
 *
 * <p>
 * This does NOT call <code>respond_to_missing?</code> on the object, and as such is not a substitute for
 * {@link RespondToNode} which implements the Ruby <code>Object#respond_to?</code>, and should be used in almost all
 * cases, especially when implementing Ruby methods with Java nodes. */
public class InternalRespondToNode extends RubyBaseNode {

    // NOTE(norswap): cf. comment above static fields in DispatchNode to see why we need this field
    public static final DispatchConfiguration PUBLIC = DispatchConfiguration.PUBLIC;

    @NeverDefault
    public static InternalRespondToNode create(DispatchConfiguration config) {
        return new InternalRespondToNode(config);
    }

    @NeverDefault
    public static InternalRespondToNode create() {
        return new InternalRespondToNode(DispatchConfiguration.PRIVATE);
    }

    public static InternalRespondToNode getUncached(DispatchConfiguration config) {
        CompilerAsserts.neverPartOfCompilation("uncached");
        return Uncached.UNCACHED_NODES[config.ordinal()];
    }

    public static InternalRespondToNode getUncached() {
        return getUncached(DispatchConfiguration.PRIVATE);
    }

    public final DispatchConfiguration config;
    @Child protected MetaClassNode metaclassNode;
    @Child protected LookupMethodNode methodLookup;

    protected InternalRespondToNode(
            DispatchConfiguration config,
            MetaClassNode metaclassNode,
            LookupMethodNode methodLookup) {
        this.config = config;
        this.metaclassNode = metaclassNode;
        this.methodLookup = methodLookup;
    }

    protected InternalRespondToNode(DispatchConfiguration config) {
        this(config, MetaClassNode.create(), LookupMethodNode.create());
    }

    public boolean execute(Frame frame, Object receiver, String methodName) {
        return executeInternal(frame, receiver, methodName, config, metaclassNode, methodLookup);
    }

    protected static boolean executeInternal(Frame frame, Object receiver, String methodName,
            DispatchConfiguration config,
            MetaClassNode metaclassNode,
            LookupMethodNode methodLookup) {
        final RubyClass metaclass = metaclassNode.executeCached(receiver);
        final InternalMethod method = methodLookup.execute(frame, metaclass, methodName, config);
        return method != null && method.isDefined() && method.isImplemented();
    }

    @DenyReplace
    private static final class Uncached extends InternalRespondToNode {

        static final Uncached[] UNCACHED_NODES = new Uncached[DispatchConfiguration.values().length];
        static {
            for (DispatchConfiguration config : DispatchConfiguration.values()) {
                UNCACHED_NODES[config.ordinal()] = new Uncached(config);
            }
        }

        protected Uncached(DispatchConfiguration config) {
            super(config, null, null);
        }

        @Override
        public boolean execute(Frame frame, Object receiver, String methodName) {
            return executeInternal(frame, receiver, methodName,
                    config,
                    MetaClassNodeGen.getUncached(),
                    LookupMethodNodeGen.getUncached());
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }
}
