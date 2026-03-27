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
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.array.ArrayToObjectArrayNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.yield.CallBlockNode;

@CoreModule(value = "TracePoint", isClass = true)
public abstract class TracePointNodes {

    @TruffleBoundary
    public static boolean isEnabled(RubyTracePoint tracePoint) {
        return tracePoint.events[0].hasEventBinding();
    }

    @TruffleBoundary
    public static boolean createEventBindings(RubyContext context, RubyLanguage language, RubyTracePoint tracePoint) {
        final TracePointEvent[] events = tracePoint.events;
        for (TracePointEvent event : events) {
            if (!event.setupEventBinding(context, language, tracePoint)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    public static boolean disposeEventBindings(RubyTracePoint tracePoint) {
        final TracePointEvent[] events = tracePoint.events;
        for (TracePointEvent event : events) {
            if (!event.diposeEventBinding()) {
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
                return new TracePointEvent(TraceManager.LineTag.class, eventSymbol);
            } else if (eventSymbol == language.coreSymbols.CLASS) {
                return new TracePointEvent(TraceManager.ClassTag.class, eventSymbol);
            } else if (eventSymbol == language.coreSymbols.NEVER) {
                return new TracePointEvent(TraceManager.NeverTag.class, eventSymbol);
            } else {
                throw new UnsupportedOperationException(eventSymbol.getString());
            }
        }

    }

    @CoreMethod(names = "enable", needsBlock = true)
    public abstract static class EnableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean enable(RubyTracePoint tracePoint, Nil block) {
            boolean setupDone = createEventBindings(getContext(), getLanguage(), tracePoint);
            return !setupDone;
        }

        @Specialization
        Object enable(RubyTracePoint tracePoint, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            final boolean setupDone = createEventBindings(getContext(), getLanguage(), tracePoint);
            try {
                return yieldNode.yield(this, block);
            } finally {
                if (setupDone) {
                    disposeEventBindings(tracePoint);
                }
            }
        }
    }

    @CoreMethod(names = "disable", needsBlock = true)
    public abstract static class DisableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object disable(RubyTracePoint tracePoint, Nil block) {
            return disposeEventBindings(tracePoint);
        }

        @Specialization
        Object disable(RubyTracePoint tracePoint, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            final boolean wasEnabled = disposeEventBindings(tracePoint);
            try {
                return yieldNode.yield(this, block);
            } finally {
                if (wasEnabled) {
                    createEventBindings(getContext(), getLanguage(), tracePoint);
                }
            }
        }
    }

    @CoreMethod(names = "enabled?")
    public abstract static class EnabledNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean enabled(RubyTracePoint tracePoint) {
            return isEnabled(tracePoint);
        }
    }

    private abstract static class TracePointCoreNode extends CoreMethodArrayArgumentsNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        protected TracePointState getTracePointState() {
            final TracePointState state = getLanguage().getCurrentThread().tracePointState;
            if (!state.insideProc) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().runtimeError("access from outside", this));
            }
            return state;
        }

    }

    @Primitive(name = "trace_point_inside_proc?")
    public abstract static class InsideProcNode extends PrimitiveNode {
        @Specialization
        Object insideProc() {
            final TracePointState state = getLanguage().getCurrentThread().tracePointState;
            return state.insideProc;
        }
    }

    @CoreMethod(names = "event")
    public abstract static class EventNode extends TracePointCoreNode {
        @Specialization
        RubySymbol event(RubyTracePoint tracePoint) {
            return getTracePointState().event;
        }
    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends TracePointCoreNode {
        @Specialization
        RubyString path(RubyTracePoint tracePoint) {
            return getTracePointState().path;
        }
    }

    @CoreMethod(names = "lineno")
    public abstract static class LineNode extends TracePointCoreNode {
        @Specialization
        int line(RubyTracePoint tracePoint) {
            return getTracePointState().line;
        }
    }

    @CoreMethod(names = "binding")
    public abstract static class BindingNode extends TracePointCoreNode {
        @Specialization
        RubyBinding binding(RubyTracePoint tracePoint) {
            return getTracePointState().binding;
        }
    }

    @CoreMethod(names = "method_id")
    public abstract static class MethodIDNode extends TracePointCoreNode {
        @Specialization
        RubySymbol methodId(RubyTracePoint tracePoint) {
            final RubyBinding binding = getTracePointState().binding;
            final InternalMethod method = RubyArguments.getMethod(binding.getFrame());
            return getSymbol(method.getName());
        }
    }

    @CoreMethod(names = "self")
    public abstract static class SelfNode extends TracePointCoreNode {
        @Specialization
        Object self(RubyTracePoint tracePoint) {
            final RubyBinding binding = getTracePointState().binding;
            return RubyArguments.getSelf(binding.getFrame());
        }
    }

}
