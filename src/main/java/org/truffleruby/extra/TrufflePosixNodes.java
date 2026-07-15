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
package org.truffleruby.extra;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.time.GetTimeZoneNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

@CoreModule(value = "Truffle::POSIX", isClass = true)
public abstract class TrufflePosixNodes {

    private static final class PanamaLabs {
        private static final MethodHandle OF_ADDRESS = createOfAddress();
        private static final MethodHandle LABS = createLabsHandle();

        @CompilationFinal private static long labsAddress;

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static long invoke(long value) {
            try {
                return (long) LABS.invokeExact(getLabsAddress(), value);
            } catch (Throwable e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static long getLabsAddress() {
            long address = labsAddress;
            if (address == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                address = createLabsAddress();
                labsAddress = address;
            }
            return address;
        }

        @TruffleBoundary
        private static long createLabsAddress() {
            return Linker.nativeLinker().defaultLookup().find("labs")
                    .orElseThrow(() -> new UnsupportedOperationException("labs is not available")).address();
        }

        @TruffleBoundary
        private static MethodHandle createOfAddress() {
            try {
                return MethodHandles.lookup().findStatic(MemorySegment.class, "ofAddress",
                        MethodType.methodType(MemorySegment.class, long.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        @SuppressWarnings("restricted")
        private static MethodHandle createLabsHandle() {
            final FunctionDescriptor descriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
            final MethodType methodType = descriptor.toMethodType().insertParameterTypes(0, long.class);
            return MethodHandles.filterArguments(Linker.nativeLinker().downcallHandle(descriptor), 0, OF_ADDRESS)
                    .asType(methodType);
        }
    }

    @TruffleBoundary
    private static void invalidateENV(String name) {
        if (name.equals("TZ")) {
            GetTimeZoneNode.invalidateTZ();
        }
    }

    @Primitive(name = "posix_invalidate_env")
    public abstract static class InvalidateEnvNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "libEnvVar.isRubyString(this, envVar)", limit = "1")
        Object invalidate(Object envVar,
                @Cached RubyStringLibrary libEnvVar) {
            invalidateENV(StringOperations.getJavaString(envVar));
            return envVar;
        }

    }

    @CoreMethod(names = "panama_labs", onSingleton = true, required = 1, lowerFixnum = 1)
    public abstract static class PanamaLabsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long labs(long value) {
            return PanamaLabs.invoke(value);
        }

    }

}
