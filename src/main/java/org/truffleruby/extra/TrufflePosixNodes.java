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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.nodes.Node;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadStatus;
import org.truffleruby.core.time.GetTimeZoneNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.Nil;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.shared.Platform;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "Truffle::POSIX", isClass = true)
public abstract class TrufflePosixNodes {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle OF_ADDRESS = createOfAddress();
    /* Keep the downcall handles and invoke helpers shared at the class level. Some signatures are used by multiple
     * primitives, such as signed/unsigned return variants and blocking/non-blocking variants, so duplicating them in
     * each primitive would add code without improving the call shape. */
    private static final MethodHandle HANDLE_I = createDowncallHandle("I()");
    private static final MethodHandle HANDLE_L = createDowncallHandle("L()");
    private static final MethodHandle HANDLE_I_I = createDowncallHandle("I(I)");
    private static final MethodHandle HANDLE_I_L = createDowncallHandle("I(L)");
    private static final MethodHandle HANDLE_L_I = createDowncallHandle("L(I)");
    private static final MethodHandle HANDLE_L_L = createDowncallHandle("L(L)");
    private static final MethodHandle HANDLE_S_I = createDowncallHandle("S(I)");
    private static final MethodHandle HANDLE_S_L = createDowncallHandle("S(L)");
    private static final MethodHandle HANDLE_S_S = createDowncallHandle("S(S)");
    private static final MethodHandle HANDLE_V_L = createDowncallHandle("V(L)");
    private static final MethodHandle HANDLE_I_II = createDowncallHandle("I(II)");
    private static final MethodHandle HANDLE_I_IL = createDowncallHandle("I(IL)");
    private static final MethodHandle HANDLE_I_IS = createDowncallHandle("I(IS)");
    private static final MethodHandle HANDLE_I_LI = createDowncallHandle("I(LI)");
    private static final MethodHandle HANDLE_I_LIS = createDowncallHandle("I(LIS)");
    private static final MethodHandle HANDLE_I_LL = createDowncallHandle("I(LL)");
    private static final MethodHandle HANDLE_I_LS = createDowncallHandle("I(LS)");
    private static final MethodHandle HANDLE_L_LL = createDowncallHandle("L(LL)");
    private static final MethodHandle HANDLE_V_LL = createDowncallHandle("V(LL)");
    private static final MethodHandle HANDLE_I_III = createDowncallHandle("I(III)");
    private static final MethodHandle HANDLE_I_IIL = createDowncallHandle("I(IIL)");
    private static final MethodHandle HANDLE_I_ILI = createDowncallHandle("I(ILI)");
    private static final MethodHandle HANDLE_I_ILL = createDowncallHandle("I(ILL)");
    private static final MethodHandle HANDLE_I_LII = createDowncallHandle("I(LII)");
    private static final MethodHandle HANDLE_I_LLI = createDowncallHandle("I(LLI)");
    private static final MethodHandle HANDLE_I_LLL = createDowncallHandle("I(LLL)");
    private static final MethodHandle HANDLE_L_ILI = createDowncallHandle("L(ILI)");
    private static final MethodHandle HANDLE_L_ILL = createDowncallHandle("L(ILL)");
    private static final MethodHandle HANDLE_L_LLL = createDowncallHandle("L(LLL)");
    private static final MethodHandle HANDLE_S_ILI = createDowncallHandle("S(ILI)");
    private static final MethodHandle HANDLE_I_ILLI = createDowncallHandle("I(ILLI)");
    private static final MethodHandle HANDLE_L_ILLL = createDowncallHandle("L(ILLL)");
    private static final MethodHandle HANDLE_I_LIIIL = createDowncallHandle("I(LIIIL)");
    private static final MethodHandle HANDLE_I_LLILI = createDowncallHandle("I(LLILI)");
    private static final MethodHandle HANDLE_L_LLIIIL = createDowncallHandle("L(LLIIIL)");
    private static final MethodHandle HANDLE_I_LLLILIIL = createDowncallHandle("I(LLLILIIL)");

    // Lazily initialized for Native Image
    private static SymbolLookup libtruffleposixLookup;

    @TruffleBoundary
    static Object resolveFunction(RubyLanguage language, RubyContext context, Node currentNode, RubySymbol nativeName) {
        Pointer.checkNativeAccess(context);

        if (context.isPreInitializing()) {
            throw new RaiseException(context,
                    context.getCoreExceptions().runtimeError("loading library while pre-initializing", currentNode));
        }

        var libtruffleposix = lookupLibtruffleposix(language);
        long address = libtruffleposix.find(nativeName.getString()).map(MemorySegment::address).orElse(0L);
        if (address == 0) {
            return Nil.INSTANCE;
        }
        return address;
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

    private static int getErrno(RubyFiber currentFiber) {
        return currentFiber.posixErrnoPointer.readInt(0);
    }

    private static void setErrno(RubyFiber currentFiber, int errno) {
        currentFiber.posixErrnoPointer.writeInt(0, errno);
    }

    @TruffleBoundary
    @SuppressWarnings("restricted")
    private static SymbolLookup lookupLibtruffleposix(RubyLanguage language) {
        if (libtruffleposixLookup == null) {
            String path = language.getRubyHome() + "/lib/cext/libtruffleposix" + Platform.LIB_SUFFIX;
            libtruffleposixLookup = SymbolLookup.libraryLookup(Path.of(path), Arena.global());
        }
        return libtruffleposixLookup;
    }

    @TruffleBoundary
    @SuppressWarnings("restricted")
    private static MethodHandle createDowncallHandle(String carrierSignature) {
        int firstArgument = carrierSignature.indexOf('(') + 1;
        int arity = carrierSignature.length() - firstArgument - 1;
        MemoryLayout[] argumentLayouts = new MemoryLayout[1 + arity];
        argumentLayouts[0] = ValueLayout.JAVA_LONG; // the errno pointer
        for (int i = 0; i < arity; i++) {
            char carrier = carrierSignature.charAt(firstArgument + i);
            argumentLayouts[1 + i] = carrierLayout(carrier);
        }

        char returnCarrier = carrierSignature.charAt(0);
        FunctionDescriptor descriptor = returnCarrier == 'V'
                ? FunctionDescriptor.ofVoid(argumentLayouts)
                : FunctionDescriptor.of(carrierLayout(returnCarrier), argumentLayouts);

        MethodHandle downcallHandle = LINKER.downcallHandle(descriptor);
        MethodHandle methodHandle = MethodHandles.filterArguments(downcallHandle, 0, OF_ADDRESS);
        MethodType methodType = descriptor.toMethodType().insertParameterTypes(0, long.class); // the function pointer
        return methodHandle.asType(methodType);
    }

    private static MemoryLayout carrierLayout(char carrier) {
        return switch (carrier) {
            case 'B' -> ValueLayout.JAVA_BYTE;
            case 'S' -> ValueLayout.JAVA_SHORT;
            case 'I' -> ValueLayout.JAVA_INT;
            case 'L' -> ValueLayout.JAVA_LONG;
            default -> throw CompilerDirectives.shouldNotReachHere("unsupported native carrier " + carrier);
        };
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI(long function, long errnoPointer) {
        try {
            return (int) HANDLE_I.invokeExact(function, errnoPointer);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL(long function, long errnoPointer) {
        try {
            return (long) HANDLE_L.invokeExact(function, errnoPointer);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_I(long function, long errnoPointer, int a) {
        try {
            return (int) HANDLE_I_I.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_L(long function, long errnoPointer, long a) {
        try {
            return (int) HANDLE_I_L.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_I(long function, long errnoPointer, int a) {
        try {
            return (long) HANDLE_L_I.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_L(long function, long errnoPointer, long a) {
        try {
            return (long) HANDLE_L_L.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static short invokeS_I(long function, long errnoPointer, int a) {
        try {
            return (short) HANDLE_S_I.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static short invokeS_L(long function, long errnoPointer, long a) {
        try {
            return (short) HANDLE_S_L.invokeExact(function, errnoPointer, a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static short invokeS_S(long function, long errnoPointer, int a) {
        try {
            return (short) HANDLE_S_S.invokeExact(function, errnoPointer, (short) a);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static Object invokeV_L(long function, long errnoPointer, long a) {
        try {
            HANDLE_V_L.invokeExact(function, errnoPointer, a);
            return Nil.INSTANCE;
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_II(long function, long errnoPointer, int a, int b) {
        try {
            return (int) HANDLE_I_II.invokeExact(function, errnoPointer, a, b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_IL(long function, long errnoPointer, int a, long b) {
        try {
            return (int) HANDLE_I_IL.invokeExact(function, errnoPointer, a, b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_IS(long function, long errnoPointer, int a, int b) {
        try {
            return (int) HANDLE_I_IS.invokeExact(function, errnoPointer, a, (short) b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LI(long function, long errnoPointer, long a, int b) {
        try {
            return (int) HANDLE_I_LI.invokeExact(function, errnoPointer, a, b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LIS(long function, long errnoPointer, long a, int b, int c) {
        try {
            return (int) HANDLE_I_LIS.invokeExact(function, errnoPointer, a, b, (short) c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LL(long function, long errnoPointer, long a, long b) {
        try {
            return (int) HANDLE_I_LL.invokeExact(function, errnoPointer, a, b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LS(long function, long errnoPointer, long a, int b) {
        try {
            return (int) HANDLE_I_LS.invokeExact(function, errnoPointer, a, (short) b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_LL(long function, long errnoPointer, long a, long b) {
        try {
            return (long) HANDLE_L_LL.invokeExact(function, errnoPointer, a, b);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static Object invokeV_LL(long function, long errnoPointer, long a, long b) {
        try {
            HANDLE_V_LL.invokeExact(function, errnoPointer, a, b);
            return Nil.INSTANCE;
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_III(long function, long errnoPointer, int a, int b, int c) {
        try {
            return (int) HANDLE_I_III.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_IIL(long function, long errnoPointer, int a, int b, long c) {
        try {
            return (int) HANDLE_I_IIL.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_ILI(long function, long errnoPointer, int a, long b, int c) {
        try {
            return (int) HANDLE_I_ILI.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_ILL(long function, long errnoPointer, int a, long b, long c) {
        try {
            return (int) HANDLE_I_ILL.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LII(long function, long errnoPointer, long a, int b, int c) {
        try {
            return (int) HANDLE_I_LII.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LLI(long function, long errnoPointer, long a, long b, int c) {
        try {
            return (int) HANDLE_I_LLI.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LLL(long function, long errnoPointer, long a, long b, long c) {
        try {
            return (int) HANDLE_I_LLL.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_ILI(long function, long errnoPointer, int a, long b, int c) {
        try {
            return (long) HANDLE_L_ILI.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_ILL(long function, long errnoPointer, int a, long b, long c) {
        try {
            return (long) HANDLE_L_ILL.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_LLL(long function, long errnoPointer, long a, long b, long c) {
        try {
            return (long) HANDLE_L_LLL.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static short invokeS_ILI(long function, long errnoPointer, int a, long b, int c) {
        try {
            return (short) HANDLE_S_ILI.invokeExact(function, errnoPointer, a, b, c);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_ILLI(long function, long errnoPointer, int a, long b, long c, int d) {
        try {
            return (int) HANDLE_I_ILLI.invokeExact(function, errnoPointer, a, b, c, d);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_ILLL(long function, long errnoPointer, int a, long b, long c, long d) {
        try {
            return (long) HANDLE_L_ILLL.invokeExact(function, errnoPointer, a, b, c, d);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LIIIL(long function, long errnoPointer, long a, int b, int c, int d, long e) {
        try {
            return (int) HANDLE_I_LIIIL.invokeExact(function, errnoPointer, a, b, c, d, e);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LLILI(long function, long errnoPointer, long a, long b, int c, long d, int e) {
        try {
            return (int) HANDLE_I_LLILI.invokeExact(function, errnoPointer, a, b, c, d, e);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static long invokeL_LLIIIL(long function, long errnoPointer, long a, long b, int c, int d, int e, long f) {
        try {
            return (long) HANDLE_L_LLIIIL.invokeExact(function, errnoPointer, a, b, c, d, e, f);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    private static int invokeI_LLLILIIL(long function, long errnoPointer, long a, long b, long c, int d, long e, int f,
            int g, long h) {
        try {
            return (int) HANDLE_I_LLLILIIL.invokeExact(function, errnoPointer, a, b, c, d, e, f, g, h);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @ImportStatic(CoreLibrary.class)
    private abstract static class InvokeNode extends PrimitiveArrayArgumentsNode {

        long getErrnoPointer() {
            return getLanguage().getCurrentFiber().posixErrnoAddress;
        }
    }

    private abstract static class PosixBlockingNode<T extends PosixBlockingState> extends InvokeNode
            implements TruffleSafepoint.CompiledInterruptibleFunction<T, Object> {

        final Object invokeBlocking(T state) {
            Interrupter nativeCallInterrupter = getContext().getThreadManager().getNativeCallInterrupter();
            return TruffleSafepoint.getCurrent().setBlockedFunction(this, nativeCallInterrupter, this, state, null,
                    null);
        }

        @Override
        public final Object apply(T state) {
            CompilerAsserts.partialEvaluationConstant(this);
            RubyThread thread = state.rubyThread;
            ThreadStatus status = thread.status;
            thread.status = ThreadStatus.SLEEP;
            try {
                return applyBlocking(state);
            } finally {
                thread.status = status;
            }
        }

        abstract Object applyBlocking(T state);
    }

    @ValueType
    private abstract static class PosixBlockingState {
        final long function;
        final long errnoPointer;
        final RubyThread rubyThread;

        PosixBlockingState(long function, long errnoPointer, RubyThread rubyThread) {
            this.function = function;
            this.errnoPointer = errnoPointer;
            this.rubyThread = rubyThread;
        }
    }

    @ValueType
    private static final class BlockingStateI_II extends PosixBlockingState {
        final int a;
        final int b;

        BlockingStateI_II(long function, long errnoPointer, RubyThread rubyThread, int a, int b) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
        }
    }

    @ValueType
    private static final class BlockingStateI_III extends PosixBlockingState {
        final int a;
        final int b;
        final int c;

        BlockingStateI_III(long function, long errnoPointer, RubyThread rubyThread, int a, int b, int c) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @ValueType
    private static final class BlockingStateI_IIL extends PosixBlockingState {
        final int a;
        final int b;
        final long c;

        BlockingStateI_IIL(long function, long errnoPointer, RubyThread rubyThread, int a, int b, long c) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @ValueType
    private static final class BlockingStateI_LII extends PosixBlockingState {
        final long a;
        final int b;
        final int c;

        BlockingStateI_LII(long function, long errnoPointer, RubyThread rubyThread, long a, int b, int c) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @ValueType
    private static final class BlockingStateI_LLI extends PosixBlockingState {
        final long a;
        final long b;
        final int c;

        BlockingStateI_LLI(long function, long errnoPointer, RubyThread rubyThread, long a, long b, int c) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @ValueType
    private static final class BlockingStateL_ILL extends PosixBlockingState {
        final int a;
        final long b;
        final long c;

        BlockingStateL_ILL(long function, long errnoPointer, RubyThread rubyThread, int a, long b, long c) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @ValueType
    private static final class BlockingStateL_ILLL extends PosixBlockingState {
        final int a;
        final long b;
        final long c;
        final long d;

        BlockingStateL_ILLL(long function, long errnoPointer, RubyThread rubyThread, int a, long b, long c, long d) {
            super(function, errnoPointer, rubyThread);
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
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

        @TruffleBoundary
        private static void invalidateENV(String name) {
            if (name.equals("TZ")) {
                GetTimeZoneNode.invalidateTZ();
            }
        }
    }

    @Primitive(name = "posix_errno")
    public abstract static class ErrnoNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        int errno() {
            return getErrno(getLanguage().getCurrentFiber());
        }
    }

    @Primitive(name = "posix_errno_set", lowerFixnum = 0)
    public abstract static class ErrnoSetNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        int errno(int value) {
            setErrno(getLanguage().getCurrentFiber(), value);
            return value;
        }
    }

    @Primitive(name = "posix_resolve")
    public abstract static class ResolveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object resolve(RubySymbol nativeName) {
            return resolveFunction(getLanguage(), getContext(), this, nativeName);
        }
    }

    @Primitive(name = "posix_invoke_i")
    public abstract static class InvokeINode extends InvokeNode {
        @Specialization
        int invoke(long function) {
            return invokeI(function, getErrnoPointer());
        }
    }

    @Primitive(name = "posix_invoke_I")
    public abstract static class InvokeUnsignedINode extends InvokeNode {
        @Specialization
        long invoke(long function) {
            return Integer.toUnsignedLong(invokeI(function, getErrnoPointer()));
        }
    }

    @Primitive(name = "posix_invoke_l")
    public abstract static class InvokeLNode extends InvokeNode {
        @Specialization
        long invoke(long function) {
            return invokeL(function, getErrnoPointer());
        }
    }

    @Primitive(name = "posix_invoke_i_i", lowerFixnum = 1)
    public abstract static class InvokeI_INode extends InvokeNode {
        @Specialization
        int invoke(long function, int a) {
            return invokeI_I(function, getErrnoPointer(), a);
        }
    }

    @Primitive(name = "posix_invoke_I_i", lowerFixnum = 1)
    public abstract static class InvokeUnsignedI_INode extends InvokeNode {
        @Specialization
        long invoke(long function, int a) {
            return Integer.toUnsignedLong(invokeI_I(function, getErrnoPointer(), a));
        }
    }

    @Primitive(name = "posix_invoke_I_I", lowerFixnum = 1)
    public abstract static class InvokeUnsignedI_UnsignedINode extends InvokeNode {
        @Specialization
        long invokeInt(long function, int a) {
            return Integer.toUnsignedLong(invokeI_I(function, getErrnoPointer(), a));
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(a)", replaces = "invokeInt")
        long invoke(long function, long a) {
            return Integer.toUnsignedLong(invokeI_I(function, getErrnoPointer(), (int) a));
        }
    }

    @Primitive(name = "posix_invoke_i_l")
    public abstract static class InvokeI_LNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a) {
            return invokeI_L(function, getErrnoPointer(), a);
        }
    }

    @Primitive(name = "posix_invoke_i_I", lowerFixnum = 1)
    public abstract static class InvokeI_UnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a) {
            return invokeI_I(function, getErrnoPointer(), a);
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(a)", replaces = "invokeInt")
        int invoke(long function, long a) {
            return invokeI_I(function, getErrnoPointer(), (int) a);
        }
    }

    @Primitive(name = "posix_invoke_I_l")
    public abstract static class InvokeUnsignedI_LNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a) {
            return Integer.toUnsignedLong(invokeI_L(function, getErrnoPointer(), a));
        }
    }

    @Primitive(name = "posix_invoke_I_L")
    public abstract static class InvokeUnsignedI_UnsignedLNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a) {
            return Integer.toUnsignedLong(invokeI_L(function, getErrnoPointer(), a));
        }

        @Specialization
        long invoke(long function, RubyBignum a) {
            return Integer.toUnsignedLong(invokeI_L(function, getErrnoPointer(), a.toUnsignedLong()));
        }
    }

    @Primitive(name = "posix_invoke_l_i", lowerFixnum = 1)
    public abstract static class InvokeL_INode extends InvokeNode {
        @Specialization
        long invoke(long function, int a) {
            return invokeL_I(function, getErrnoPointer(), a);
        }
    }

    @Primitive(name = "posix_invoke_l_l")
    public abstract static class InvokeL_LNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a) {
            return invokeL_L(function, getErrnoPointer(), a);
        }
    }

    @Primitive(name = "posix_invoke_S_i", lowerFixnum = 1)
    public abstract static class InvokeUnsignedS_INode extends InvokeNode {
        @Specialization
        int invoke(long function, int a) {
            return Short.toUnsignedInt(invokeS_I(function, getErrnoPointer(), a));
        }
    }

    @Primitive(name = "posix_invoke_S_l")
    public abstract static class InvokeUnsignedS_LNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a) {
            return Short.toUnsignedInt(invokeS_L(function, getErrnoPointer(), a));
        }
    }

    @Primitive(name = "posix_invoke_S_s", lowerFixnum = 1)
    public abstract static class InvokeUnsignedS_SNode extends InvokeNode {
        @Specialization
        int invoke(long function, int a) {
            return Short.toUnsignedInt(invokeS_S(function, getErrnoPointer(), a));
        }
    }

    @Primitive(name = "posix_invoke_v_l")
    public abstract static class InvokeV_LNode extends InvokeNode {
        @Specialization
        Object invoke(long function, long a) {
            return invokeV_L(function, getErrnoPointer(), a);
        }
    }

    @Primitive(name = "posix_invoke_i_ii", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_IINode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, int b) {
            return invokeI_II(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_iI", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_IUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a, int b) {
            return invokeI_II(function, getErrnoPointer(), a, b);
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(b)", replaces = "invokeInt")
        int invoke(long function, int a, long b) {
            return invokeI_II(function, getErrnoPointer(), a, (int) b);
        }
    }

    @Primitive(name = "posix_invoke_i_II", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_UnsignedIUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a, int b) {
            return invokeI_II(function, getErrnoPointer(), a, b);
        }

        @Specialization(guards = { "fitsIntoUnsignedInteger(a)", "fitsIntoUnsignedInteger(b)" }, replaces = "invokeInt")
        int invoke(long function, long a, long b) {
            return invokeI_II(function, getErrnoPointer(), (int) a, (int) b);
        }
    }

    @Primitive(name = "posix_invoke_i_il", lowerFixnum = 1)
    public abstract static class InvokeI_ILNode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, long b) {
            return invokeI_IL(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_is", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_ISNode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, int b) {
            return invokeI_IS(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_li", lowerFixnum = 2)
    public abstract static class InvokeI_LINode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, int b) {
            return invokeI_LI(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_lI", lowerFixnum = 2)
    public abstract static class InvokeI_LUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, long a, int b) {
            return invokeI_LI(function, getErrnoPointer(), a, b);
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(b)", replaces = "invokeInt")
        int invoke(long function, long a, long b) {
            return invokeI_LI(function, getErrnoPointer(), a, (int) b);
        }
    }

    @Primitive(name = "posix_invoke_i_lis", lowerFixnum = { 2, 3 })
    public abstract static class InvokeI_LISNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, int b, int c) {
            return invokeI_LIS(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_ll")
    public abstract static class InvokeI_LLNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b) {
            return invokeI_LL(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_Ll")
    public abstract static class InvokeI_UnsignedLLNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b) {
            return invokeI_LL(function, getErrnoPointer(), a, b);
        }

        @Specialization
        int invoke(long function, RubyBignum a, long b) {
            return invokeI_LL(function, getErrnoPointer(), a.toUnsignedLong(), b);
        }
    }

    @Primitive(name = "posix_invoke_i_lL")
    public abstract static class InvokeI_LUnsignedLNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b) {
            return invokeI_LL(function, getErrnoPointer(), a, b);
        }

        @Specialization
        int invoke(long function, long a, RubyBignum b) {
            return invokeI_LL(function, getErrnoPointer(), a, b.toUnsignedLong());
        }
    }

    @Primitive(name = "posix_invoke_i_ls", lowerFixnum = 2)
    public abstract static class InvokeI_LSNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, int b) {
            return invokeI_LS(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_l_ll")
    public abstract static class InvokeL_LLNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a, long b) {
            return invokeL_LL(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_l_lL")
    public abstract static class InvokeL_LUnsignedLNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a, long b) {
            return invokeL_LL(function, getErrnoPointer(), a, b);
        }

        @Specialization
        long invoke(long function, long a, RubyBignum b) {
            return invokeL_LL(function, getErrnoPointer(), a, b.toUnsignedLong());
        }
    }

    @Primitive(name = "posix_invoke_v_ll")
    public abstract static class InvokeV_LLNode extends InvokeNode {
        @Specialization
        Object invoke(long function, long a, long b) {
            return invokeV_LL(function, getErrnoPointer(), a, b);
        }
    }

    @Primitive(name = "posix_invoke_i_iii", lowerFixnum = { 1, 2, 3 })
    public abstract static class InvokeI_IIINode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, int b, int c) {
            return invokeI_III(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_iIi", lowerFixnum = { 1, 2, 3 })
    public abstract static class InvokeI_IUnsignedIINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a, int b, int c) {
            return invokeI_III(function, getErrnoPointer(), a, b, c);
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(b)", replaces = "invokeInt")
        int invoke(long function, int a, long b, int c) {
            return invokeI_III(function, getErrnoPointer(), a, (int) b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_iII", lowerFixnum = { 1, 2, 3 })
    public abstract static class InvokeI_IUnsignedIUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a, int b, int c) {
            return invokeI_III(function, getErrnoPointer(), a, b, c);
        }

        @Specialization(guards = { "fitsIntoUnsignedInteger(b)", "fitsIntoUnsignedInteger(c)" }, replaces = "invokeInt")
        int invoke(long function, int a, long b, long c) {
            return invokeI_III(function, getErrnoPointer(), a, (int) b, (int) c);
        }
    }

    @Primitive(name = "posix_invoke_i_III", lowerFixnum = { 1, 2, 3 })
    public abstract static class InvokeI_UnsignedIUnsignedIUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, int a, int b, int c) {
            return invokeI_III(function, getErrnoPointer(), a, b, c);
        }

        @Specialization(
                guards = { "fitsIntoUnsignedInteger(a)", "fitsIntoUnsignedInteger(b)", "fitsIntoUnsignedInteger(c)" },
                replaces = "invokeInt")
        int invoke(long function, long a, long b, long c) {
            return invokeI_III(function, getErrnoPointer(), (int) a, (int) b, (int) c);
        }
    }

    @Primitive(name = "posix_invoke_I_ili", lowerFixnum = { 1, 3 })
    public abstract static class InvokeUnsignedI_ILINode extends InvokeNode {
        @Specialization
        long invoke(long function, int a, long b, int c) {
            return Integer.toUnsignedLong(invokeI_ILI(function, getErrnoPointer(), a, b, c));
        }
    }

    @Primitive(name = "posix_invoke_i_ill", lowerFixnum = 1)
    public abstract static class InvokeI_ILLNode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, long b, long c) {
            return invokeI_ILL(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_iLl", lowerFixnum = 1)
    public abstract static class InvokeI_IUnsignedLLNode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, long b, long c) {
            return invokeI_ILL(function, getErrnoPointer(), a, b, c);
        }

        @Specialization
        int invoke(long function, int a, RubyBignum b, long c) {
            return invokeI_ILL(function, getErrnoPointer(), a, b.toUnsignedLong(), c);
        }
    }

    @Primitive(name = "posix_invoke_i_lii", lowerFixnum = { 2, 3 })
    public abstract static class InvokeI_LIINode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, int b, int c) {
            return invokeI_LII(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_liI", lowerFixnum = { 2, 3 })
    public abstract static class InvokeI_LIUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, long a, int b, int c) {
            return invokeI_LII(function, getErrnoPointer(), a, b, c);
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(c)", replaces = "invokeInt")
        int invoke(long function, long a, int b, long c) {
            return invokeI_LII(function, getErrnoPointer(), a, b, (int) c);
        }
    }

    @Primitive(name = "posix_invoke_i_lII", lowerFixnum = { 2, 3 })
    public abstract static class InvokeI_LUnsignedIUnsignedINode extends InvokeNode {
        @Specialization
        int invokeInt(long function, long a, int b, int c) {
            return invokeI_LII(function, getErrnoPointer(), a, b, c);
        }

        @Specialization(guards = { "fitsIntoUnsignedInteger(b)", "fitsIntoUnsignedInteger(c)" }, replaces = "invokeInt")
        int invoke(long function, long a, long b, long c) {
            return invokeI_LII(function, getErrnoPointer(), a, (int) b, (int) c);
        }
    }

    @Primitive(name = "posix_invoke_i_lli", lowerFixnum = 3)
    public abstract static class InvokeI_LLINode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b, int c) {
            return invokeI_LLI(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_i_lLi", lowerFixnum = 3)
    public abstract static class InvokeI_LUnsignedLINode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b, int c) {
            return invokeI_LLI(function, getErrnoPointer(), a, b, c);
        }

        @Specialization
        int invoke(long function, long a, RubyBignum b, int c) {
            return invokeI_LLI(function, getErrnoPointer(), a, b.toUnsignedLong(), c);
        }
    }

    @Primitive(name = "posix_invoke_i_lll")
    public abstract static class InvokeI_LLLNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b, long c) {
            return invokeI_LLL(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_l_ili", lowerFixnum = { 1, 3 })
    public abstract static class InvokeL_ILINode extends InvokeNode {
        @Specialization
        long invoke(long function, int a, long b, int c) {
            return invokeL_ILI(function, getErrnoPointer(), a, b, c);
        }
    }

    @Primitive(name = "posix_invoke_l_llL")
    public abstract static class InvokeL_LLUnsignedLNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a, long b, long c) {
            return invokeL_LLL(function, getErrnoPointer(), a, b, c);
        }

        @Specialization
        long invoke(long function, long a, long b, RubyBignum c) {
            return invokeL_LLL(function, getErrnoPointer(), a, b, c.toUnsignedLong());
        }
    }

    @Primitive(name = "posix_invoke_S_ili", lowerFixnum = { 1, 3 })
    public abstract static class InvokeUnsignedS_ILINode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, long b, int c) {
            return Short.toUnsignedInt(invokeS_ILI(function, getErrnoPointer(), a, b, c));
        }
    }

    @Primitive(name = "posix_invoke_i_illi", lowerFixnum = { 1, 4 })
    public abstract static class InvokeI_ILLINode extends InvokeNode {
        @Specialization
        int invoke(long function, int a, long b, long c, int d) {
            return invokeI_ILLI(function, getErrnoPointer(), a, b, c, d);
        }
    }

    @Primitive(name = "posix_invoke_i_liiil", lowerFixnum = { 2, 3, 4 })
    public abstract static class InvokeI_LIIILNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, int b, int c, int d, long e) {
            return invokeI_LIIIL(function, getErrnoPointer(), a, b, c, d, e);
        }
    }

    @Primitive(name = "posix_invoke_i_llili", lowerFixnum = { 3, 5 })
    public abstract static class InvokeI_LLILINode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b, int c, long d, int e) {
            return invokeI_LLILI(function, getErrnoPointer(), a, b, c, d, e);
        }
    }

    @Primitive(name = "posix_invoke_l_lLiiil", lowerFixnum = { 3, 4, 5 })
    public abstract static class InvokeL_LUnsignedLIIILNode extends InvokeNode {
        @Specialization
        long invoke(long function, long a, long b, int c, int d, int e, long f) {
            return invokeL_LLIIIL(function, getErrnoPointer(), a, b, c, d, e, f);
        }

        @Specialization
        long invoke(long function, long a, RubyBignum b, int c, int d, int e, long f) {
            return invokeL_LLIIIL(function, getErrnoPointer(), a, b.toUnsignedLong(), c, d, e, f);
        }
    }

    @Primitive(name = "posix_invoke_i_llliliil", lowerFixnum = { 4, 6, 7 })
    public abstract static class InvokeI_LLLILIILNode extends InvokeNode {
        @Specialization
        int invoke(long function, long a, long b, long c, int d, long e, int f, int g, long h) {
            return invokeI_LLLILIIL(function, getErrnoPointer(), a, b, c, d, e, f, g, h);
        }
    }

    @Primitive(name = "posix_invoke_i_ii_blocking", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_IIBlockingNode extends PosixBlockingNode<BlockingStateI_II> {
        @Specialization
        int invoke(long function, int a, int b) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(
                    new BlockingStateI_II(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b));
        }

        @Override
        Object applyBlocking(BlockingStateI_II state) {
            return invokeI_II(state.function, state.errnoPointer, state.a, state.b);
        }
    }

    @Primitive(name = "posix_invoke_i_iii_blocking", lowerFixnum = { 1, 2, 3 })
    public abstract static class InvokeI_IIIBlockingNode extends PosixBlockingNode<BlockingStateI_III> {
        @Specialization
        int invoke(long function, int a, int b, int c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(
                    new BlockingStateI_III(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b, c));
        }

        @Override
        Object applyBlocking(BlockingStateI_III state) {
            return invokeI_III(state.function, state.errnoPointer, state.a, state.b, state.c);
        }
    }

    @Primitive(name = "posix_invoke_i_iil_blocking", lowerFixnum = { 1, 2 })
    public abstract static class InvokeI_IILBlockingNode extends PosixBlockingNode<BlockingStateI_IIL> {
        @Specialization
        int invoke(long function, int a, int b, long c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(
                    new BlockingStateI_IIL(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b, c));
        }

        @Override
        Object applyBlocking(BlockingStateI_IIL state) {
            return invokeI_IIL(state.function, state.errnoPointer, state.a, state.b, state.c);
        }
    }

    @Primitive(name = "posix_invoke_i_lIi_blocking", lowerFixnum = { 2, 3 })
    public abstract static class InvokeI_LUnsignedIIBlockingNode extends PosixBlockingNode<BlockingStateI_LII> {
        @Specialization
        int invokeInt(long function, long a, int b, int c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(
                    new BlockingStateI_LII(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b, c));
        }

        @Specialization(guards = "fitsIntoUnsignedInteger(b)", replaces = "invokeInt")
        int invoke(long function, long a, long b, int c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(new BlockingStateI_LII(function, currentFiber.posixErrnoAddress,
                    currentFiber.rubyThread, a, (int) b, c));
        }

        @Override
        Object applyBlocking(BlockingStateI_LII state) {
            return invokeI_LII(state.function, state.errnoPointer, state.a, state.b, state.c);
        }
    }

    @Primitive(name = "posix_invoke_i_lLi_blocking", lowerFixnum = 3)
    public abstract static class InvokeI_LUnsignedLIBlockingNode extends PosixBlockingNode<BlockingStateI_LLI> {
        @Specialization
        int invoke(long function, long a, long b, int c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(
                    new BlockingStateI_LLI(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b, c));
        }

        @Specialization
        int invoke(long function, long a, RubyBignum b, int c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (int) invokeBlocking(new BlockingStateI_LLI(function, currentFiber.posixErrnoAddress,
                    currentFiber.rubyThread, a, b.toUnsignedLong(), c));
        }

        @Override
        Object applyBlocking(BlockingStateI_LLI state) {
            return invokeI_LLI(state.function, state.errnoPointer, state.a, state.b, state.c);
        }
    }

    @Primitive(name = "posix_invoke_l_ilL_blocking", lowerFixnum = 1)
    public abstract static class InvokeL_ILUnsignedLBlockingNode extends PosixBlockingNode<BlockingStateL_ILL> {
        @Specialization
        long invoke(long function, int a, long b, long c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (long) invokeBlocking(
                    new BlockingStateL_ILL(function, currentFiber.posixErrnoAddress, currentFiber.rubyThread, a, b, c));
        }

        @Specialization
        long invoke(long function, int a, long b, RubyBignum c) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (long) invokeBlocking(new BlockingStateL_ILL(function, currentFiber.posixErrnoAddress,
                    currentFiber.rubyThread, a, b, c.toUnsignedLong()));
        }

        @Override
        Object applyBlocking(BlockingStateL_ILL state) {
            return invokeL_ILL(state.function, state.errnoPointer, state.a, state.b, state.c);
        }
    }

    @Primitive(name = "posix_invoke_l_ilLl_blocking", lowerFixnum = 1)
    public abstract static class InvokeL_ILUnsignedLLBlockingNode extends PosixBlockingNode<BlockingStateL_ILLL> {
        @Specialization
        long invoke(long function, int a, long b, long c, long d) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (long) invokeBlocking(new BlockingStateL_ILLL(function, currentFiber.posixErrnoAddress,
                    currentFiber.rubyThread, a, b, c, d));
        }

        @Specialization
        long invoke(long function, int a, long b, RubyBignum c, long d) {
            RubyFiber currentFiber = getLanguage().getCurrentFiber();
            return (long) invokeBlocking(new BlockingStateL_ILLL(function, currentFiber.posixErrnoAddress,
                    currentFiber.rubyThread, a, b, c.toUnsignedLong(), d));
        }

        @Override
        Object applyBlocking(BlockingStateL_ILLL state) {
            return invokeL_ILLL(state.function, state.errnoPointer, state.a, state.b, state.c,
                    state.d);
        }
    }

}
