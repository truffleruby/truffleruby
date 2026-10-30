/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.tracepoint;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayToObjectArrayNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.yield.CallBlockNode;

@CoreModule(value = "TracePoint", isClass = true)
public abstract class TracePointNodes {

    @TruffleBoundary
    public static boolean isEnabled(TracePointObject tracePointOrEvent) {
        return tracePointOrEvent.getTracePoint().events[0].isEnabled();
    }

    @TruffleBoundary
    public static boolean enableEvents(RubyContext context, RubyLanguage language,
            TracePointObject tracePointOrEvent) {
        final RubyTracePoint owner = tracePointOrEvent.getTracePoint();
        for (TracePointEvent event : owner.events) {
            if (!event.enable(context, language, owner)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    public static boolean disableEvents(RubyContext context, TracePointObject tracePointOrEvent) {
        final RubyTracePoint owner = tracePointOrEvent.getTracePoint();
        for (TracePointEvent event : owner.events) {
            if (!event.disable(context, owner)) {
                return false;
            }
        }
        return true;
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyTracePoint allocate(RubyClass rubyClass) {
            final RubyTracePoint instance = new RubyTracePoint(rubyClass, getLanguage().tracePointShape, null, null);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @Primitive(name = "tracepoint_initialize")
    public abstract static class InitializeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyTracePoint initialize(RubyTracePoint tracePoint, RubyArray eventsArray, RubyProc block,
                @Cached ArrayToObjectArrayNode arrayToObjectArrayNode) {
            final Object[] eventSymbols = arrayToObjectArrayNode.executeToObjectArray(eventsArray);

            final TracePointEvent[] events = new TracePointEvent[eventSymbols.length];
            for (int i = 0; i < eventSymbols.length; i++) {
                events[i] = createEvents(getLanguage(), (RubySymbol) eventSymbols[i]);
            }

            tracePoint.events = events;
            tracePoint.proc = block;
            return tracePoint;
        }

        @TruffleBoundary
        private TracePointEvent createEvents(RubyLanguage language, RubySymbol eventSymbol) {
            if (eventSymbol == language.coreSymbols.LINE) {
                return new TracePointEvent.Instrumented(TraceManager.LineTag.class, eventSymbol);
            } else if (eventSymbol == language.coreSymbols.CLASS ||
                    eventSymbol == language.coreSymbols.END ||
                    eventSymbol == language.coreSymbols.FIBER_SWITCH ||
                    eventSymbol == language.coreSymbols.THREAD_BEGIN ||
                    eventSymbol == language.coreSymbols.THREAD_END ||
                    eventSymbol == language.coreSymbols.NEVER) {
                return new TracePointEvent.SlowPath(eventSymbol);
            } else if (eventSymbol == language.coreSymbols.CALL) {
                return new TracePointEvent.Instrumented(TraceManager.CallTag.class, eventSymbol);
            } else if (eventSymbol == language.coreSymbols.C_CALL) {
                return new TracePointEvent.Instrumented(TraceManager.CCallTag.class, eventSymbol);
            } else if (eventSymbol == language.coreSymbols.RETURN) {
                return new TracePointEvent.Instrumented(TraceManager.ReturnTag.class, eventSymbol);
            } else {
                throw new UnsupportedOperationException(eventSymbol.getString());
            }
        }

    }

    @CoreMethod(names = "enable", needsBlock = true)
    public abstract static class EnableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean enable(TracePointObject tracePoint, Nil block) {
            boolean setupDone = enableEvents(getContext(), getLanguage(), tracePoint);
            return !setupDone;
        }

        @Specialization
        Object enable(TracePointObject tracePoint, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            final boolean setupDone = enableEvents(getContext(), getLanguage(), tracePoint);
            try {
                return yieldNode.yield(this, block);
            } finally {
                if (setupDone) {
                    disableEvents(getContext(), tracePoint);
                }
            }
        }
    }

    @CoreMethod(names = "disable", needsBlock = true)
    public abstract static class DisableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object disable(TracePointObject tracePoint, Nil block) {
            return disableEvents(getContext(), tracePoint);
        }

        @Specialization
        Object disable(TracePointObject tracePoint, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            final boolean wasEnabled = disableEvents(getContext(), tracePoint);
            try {
                return yieldNode.yield(this, block);
            } finally {
                if (wasEnabled) {
                    enableEvents(getContext(), getLanguage(), tracePoint);
                }
            }
        }
    }

    @CoreMethod(names = "enabled?")
    public abstract static class EnabledNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean enabled(TracePointObject tracePoint) {
            return isEnabled(tracePoint);
        }
    }

    private abstract static class TracePointCoreNode extends CoreMethodArrayArgumentsNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        /** The event data is only accessible on the object yielded to the TracePoint block, while the block runs */
        protected RubyTracePointEvent activeEvent(TracePointObject tracePointOrEvent) {
            if (tracePointOrEvent instanceof RubyTracePointEvent tracePointEvent && tracePointEvent.active) {
                return tracePointEvent;
            } else {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().runtimeError("access from outside", this));
            }
        }

    }

    @Primitive(name = "trace_point_owner")
    public abstract static class OwnerNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyTracePoint owner(TracePointObject tracePointOrEvent) {
            return tracePointOrEvent.getTracePoint();
        }
    }

    @Primitive(name = "trace_point_active_event?")
    public abstract static class ActiveEventNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean activeEvent(TracePointObject tracePointOrEvent) {
            return tracePointOrEvent instanceof RubyTracePointEvent tracePointEvent && tracePointEvent.active;
        }
    }

    @CoreMethod(names = "event")
    public abstract static class EventNode extends TracePointCoreNode {
        @Specialization
        RubySymbol event(TracePointObject tracePoint) {
            return activeEvent(tracePoint).event;
        }
    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends TracePointCoreNode {
        @Specialization
        Object path(TracePointObject tracePoint) {
            return activeEvent(tracePoint).path;
        }
    }

    @CoreMethod(names = "lineno")
    public abstract static class LineNode extends TracePointCoreNode {
        @Specialization
        int line(TracePointObject tracePoint) {
            return activeEvent(tracePoint).line;
        }
    }

    @CoreMethod(names = "binding")
    public abstract static class BindingNode extends TracePointCoreNode {
        @Specialization
        Object binding(TracePointObject tracePoint,
                @Cached InlinedConditionProfile bindingCreatedProfile,
                @Cached InlinedConditionProfile noFrameProfile) {
            final RubyTracePointEvent tracePointEvent = activeEvent(tracePoint);
            // Created lazily, so events whose block does not need the Binding do not allocate it
            if (bindingCreatedProfile.profile(this, tracePointEvent.binding == null)) {
                if (noFrameProfile.profile(this, tracePointEvent.frame == null)) {
                    tracePointEvent.binding = nil;
                } else {
                    tracePointEvent.binding = BindingNodes.createBinding(getContext(), getLanguage(),
                            tracePointEvent.frame, tracePointEvent.sourceSection);
                }
            }
            return tracePointEvent.binding;
        }
    }

    @CoreMethod(names = "method_id")
    public abstract static class MethodIDNode extends TracePointCoreNode {
        @Specialization
        Object methodId(TracePointObject tracePoint) {
            final InternalMethod method = activeEvent(tracePoint).method;
            if (method == null || method.getSharedMethodInfo().isModuleBody()) {
                return nil; // like CRuby, module bodies and the top-level have no method
            }
            return getSymbol(method.getName());
        }
    }

    @CoreMethod(names = "defined_class")
    public abstract static class DefinedClassNode extends TracePointCoreNode {
        @Specialization
        Object definedClass(TracePointObject tracePoint) {
            final InternalMethod method = activeEvent(tracePoint).method;
            if (method == null || method.getSharedMethodInfo().isModuleBody()) {
                return nil; // like CRuby, module bodies and the top-level have no method
            }
            return method.getDeclaringModule();
        }
    }

    @CoreMethod(names = "self")
    public abstract static class SelfNode extends TracePointCoreNode {
        @Specialization
        Object self(TracePointObject tracePoint) {
            return activeEvent(tracePoint).self;
        }
    }

    @CoreMethod(names = "return_value")
    public abstract static class ReturnValueNode extends TracePointCoreNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        @Specialization
        Object returnValue(TracePointObject tracePoint) {
            final RubyTracePointEvent tracePointEvent = activeEvent(tracePoint);
            if (tracePointEvent.event != coreSymbols().RETURN) {
                errorProfile.enter();
                throw new RaiseException(getContext(),
                        coreExceptions().runtimeError("not supported by this event", this));
            }
            return tracePointEvent.returnValue;
        }
    }

}
