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
package org.truffleruby.stdlib;

import java.util.Set;

import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.regexp.MatchDataNodes.ValuesNode;
import org.truffleruby.core.regexp.RubyMatchData;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.AllocationTracing.AllocationTrace;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule("Truffle::ObjSpace")
public abstract class ObjSpaceNodes {

    @CoreMethod(names = "memsize_of", onSingleton = true, required = 1)
    public abstract static class MemsizeOfNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int memsizeOfArray(RubyArray object) {
            return memsizeOfObject(object) + object.size;
        }

        @Specialization
        int memsizeOfHash(RubyHash object) {
            return memsizeOfObject(object) + object.size;
        }

        @Specialization
        int memsizeOfString(RubyString object,
                @Cached @Exclusive RubyStringLibrary libString) {
            return memsizeOfObject(object) + libString.byteLength(this, object);
        }

        @Specialization
        int memsizeOfString(ImmutableRubyString object,
                @Cached @Exclusive RubyStringLibrary libString) {
            return 1 + libString.byteLength(this, object);
        }

        @Specialization
        int memsizeOfMatchData(RubyMatchData object,
                @Cached @Exclusive ValuesNode matchDataValues) {
            return memsizeOfObject(object) + matchDataValues.execute(object).length;
        }

        @Specialization(
                guards = {
                        "!isRubyArray(object)",
                        "!isRubyHash(object)",
                        "isNotRubyString(object)",
                        "!isRubyMatchData(object)" })
        int memsizeOfObject(RubyDynamicObject object) {
            return 1 + object.getShape().getPropertyCount();
        }

        @Specialization(guards = "!isRubyDynamicObject(object)")
        int memsize(Object object) {
            return 0;
        }
    }

    @CoreMethod(names = "adjacent_objects", onSingleton = true, required = 1)
    public abstract static class AdjacentObjectsNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyArray adjacentObjects(RubyDynamicObject object) {
            final Set<Object> objects = ObjectGraph.getAdjacentObjects(object);
            return createArray(objects.toArray());
        }

        @Fallback
        Object adjacentObjectsPrimitive(Object object) {
            return nil;
        }
    }

    @CoreMethod(names = "root_objects", onSingleton = true)
    public abstract static class RootObjectsNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyArray rootObjects() {
            final Set<Object> objects = ObjectGraph
                    .stopAndGetRootObjects("ObjectSpace.reachable_objects_from_root", getContext(), this);
            return createArray(objects.toArray());
        }
    }

    @CoreMethod(names = "trace_allocations_start", onSingleton = true)
    public abstract static class TraceAllocationsStartNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object traceAllocationsStart() {
            getContext().getObjectSpaceManager().traceAllocationsStart(getLanguage());
            return nil;
        }
    }

    @CoreMethod(names = "trace_allocations_stop", onSingleton = true)
    public abstract static class TraceAllocationsStopNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object traceAllocationsStop() {
            getContext().getObjectSpaceManager().traceAllocationsStop(getLanguage());
            return nil;
        }
    }

    @CoreMethod(names = "trace_allocations_clear", onSingleton = true)
    public abstract static class TraceAllocationsClearNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object traceAllocationsClear() {
            getContext().getObjectSpaceManager().traceAllocationsClear();
            return nil;
        }
    }

    @Primitive(name = "allocation_class_path")
    public abstract static class AllocationClassPathNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object allocationInfo(RubyDynamicObject object,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            AllocationTrace trace = getAllocationTrace(getContext(), object);
            if (trace == null) {
                return nil;
            } else {
                final String className = trace.className;
                if (className.isEmpty()) {
                    return nil;
                } else {
                    return createString(fromJavaStringNode, className, Encodings.UTF_8);
                }
            }
        }

        @Fallback
        Object allocationInfoImmutable(Object object) {
            return nil;
        }
    }

    @Primitive(name = "allocation_generation")
    public abstract static class AllocationGenerationNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object allocationInfo(RubyDynamicObject object) {
            AllocationTrace trace = getAllocationTrace(getContext(), object);
            if (trace == null) {
                return nil;
            } else {
                return trace.gcGeneration;
            }
        }

        @Fallback
        Object allocationGenerationImmutable(Object object) {
            return nil;
        }
    }

    @Primitive(name = "allocation_method_id")
    public abstract static class AllocationMethodIDNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object allocationInfo(RubyDynamicObject object) {
            AllocationTrace trace = getAllocationTrace(getContext(), object);
            if (trace == null) {
                return nil;
            } else {
                final String allocatingMethod = trace.allocatingMethod;
                if (SharedMethodInfo.isModuleBody(allocatingMethod)) { // <top (required)> or <main> are hidden in MRI
                    return nil;
                } else if (allocatingMethod.equals("__allocate__")) { // The allocator function is hidden in MRI
                    return getLanguage().coreSymbols.NEW;
                } else {
                    return getLanguage().getSymbol(allocatingMethod);
                }
            }
        }

        @Fallback
        Object allocationMethodIdImmutable(Object object) {
            return nil;
        }
    }

    @Primitive(name = "allocation_sourcefile")
    public abstract static class AllocationSourceFileNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object allocationInfo(RubyDynamicObject object,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            AllocationTrace trace = getAllocationTrace(getContext(), object);
            if (trace == null) {
                return nil;
            } else {
                final String sourcePath = RubyLanguage.getPath(trace.allocatingSourceSection.getSource());
                return createString(fromJavaStringNode, sourcePath, Encodings.UTF_8);
            }
        }

        @Fallback
        Object allocationSourcefileImmutable(Object object) {
            return nil;
        }
    }

    @Primitive(name = "allocation_sourceline")
    public abstract static class AllocationSourceLineNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object allocationInfo(RubyDynamicObject object) {
            AllocationTrace trace = getAllocationTrace(getContext(), object);
            if (trace == null) {
                return nil;
            } else {
                return getLanguage().getStartLineAdjusted(trace.allocatingSourceSection);
            }
        }

        @Fallback
        Object allocationSourcelineImmutable(Object object) {
            return nil;
        }
    }

    @TruffleBoundary
    private static AllocationTrace getAllocationTrace(RubyContext context, RubyDynamicObject object) {
        final AllocationTrace trace = (AllocationTrace) DynamicObjectLibrary
                .getUncached()
                .getOrDefault(object, Layouts.ALLOCATION_TRACE_IDENTIFIER, null);
        if (trace != null && trace.tracingGeneration == context.getObjectSpaceManager().getTracingGeneration()) {
            return trace;
        } else {
            return null;
        }
    }

}
