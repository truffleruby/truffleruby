/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.cext;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.platform.FFMSupport;
import org.truffleruby.platform.NativeLibrary;

/** The FFM-based layer connecting native C extension code and Java/Ruby: creates the native-to-Java upcall stubs for
 * {@link CExtUpcallTargets} and implements their runtime support. Each upcall executes through a lazily-created
 * {@link CExtUpcallRootNode} CallTarget so the conversions and dispatch partial-evaluate.
 *
 * <p>
 * Upcall stubs are allocated in {@link Arena#global()}: native code (including leaked threads and atexit handlers) may
 * hold the function pointers for the lifetime of the process, and libtruffleruby and C extensions are loaded with
 * RTLD_GLOBAL anyway. As a consequence only one Ruby context at a time can load C extension support. */
public final class CExtFFMLayer {

    /** The layer of the single live Ruby context which currently has C extension support loaded: the upcall stubs, the
     * C globals filled by rb_tr_init() and the RTLD_GLOBAL symbols are process-wide. Once that context is disposed, a
     * new context can load C extension support again: it runs rb_tr_init() again with the same upcall stubs (created
     * once per process) and fresh constant handles, overwriting the C globals. Guarded by the CExtFFMLayer.class
     * monitor together with CExtUpcallTargets' runtime, so deactivating a disposed layer cannot overwrite the runtime
     * of a successor context which activated concurrently. */
    private static CExtFFMLayer activeLayer;

    /** The upcall stub addresses, created once per process in Arena.global() */
    private static long[] upcallStubs;

    /** rb_tr_init(void** upcalls, const VALUE* constants) */
    private static final MethodHandle INIT = FFMSupport.createDowncallHandle("V(LL)");
    /** int* rb_tr_pending_exception_address(void) */
    private static final MethodHandle PENDING_EXCEPTION_ADDRESS = FFMSupport.createDowncallHandle("L()");

    private final RubyContext context;
    private final RubyLanguage language;
    private final AtomicReferenceArray<CallTarget> upcallCallTargets;
    private final ConcurrentHashMap<Long, String> methodNames = new ConcurrentHashMap<>();
    private long pendingExceptionAddressFunction;

    public CExtFFMLayer(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
        this.upcallCallTargets = new AtomicReferenceArray<>(CExtUpcallTargets.UPCALLS.length / 6);
    }

    @TruffleBoundary
    public void initialize(NativeLibrary library, Object[] constants) {
        synchronized (CExtFFMLayer.class) {
            if (activeLayer != null) {
                throw new RaiseException(context, context.getCoreExceptions().runtimeError(
                        "C extension support can only be loaded in a single Ruby context at a time in a process, " +
                                "because the FFM upcall stubs and RTLD_GLOBAL symbols are process-wide",
                        (Node) null));
            }
            if (upcallStubs == null) {
                final String[] upcallsArray = CExtUpcallTargets.UPCALLS;
                final long[] stubs = new long[upcallsArray.length / 6];
                for (int i = 0; i < stubs.length; i++) {
                    stubs[i] = createUpcallStub(upcallsArray[i * 6], upcallsArray[i * 6 + 1]);
                }
                upcallStubs = stubs;
            }
            activeLayer = this;
            CExtUpcallTargets.setRuntime(this);
        }

        try {
            initLibTruffleRuby(library, constants);
        } catch (Throwable t) {
            // Roll back, so this context or another one can try to load C extension support again
            deactivate(this);
            throw t;
        }
    }

    private void initLibTruffleRuby(NativeLibrary library, Object[] constants) {
        long initFunction = library.lookupSymbol("rb_tr_init");
        if (initFunction == 0) {
            throw CompilerDirectives.shouldNotReachHere("rb_tr_init not found in " + library.getPath());
        }
        pendingExceptionAddressFunction = library.lookupSymbol("rb_tr_pending_exception_address");
        if (pendingExceptionAddressFunction == 0) {
            throw CompilerDirectives.shouldNotReachHere("rb_tr_pending_exception_address not found");
        }

        final int upcallsCount = CExtUpcallTargets.UPCALLS.length / 6;
        // rb_tr_init() copies both arrays into C globals, so they can be freed after the call
        try (Pointer upcalls = Pointer.malloc(context, upcallsCount * 8L);
                Pointer constantHandles = Pointer.malloc(context, constants.length * 8L)) {
            for (int i = 0; i < upcallsCount; i++) {
                upcalls.writeLong(i * 8L, upcallStubs[i]);
            }
            for (int i = 0; i < constants.length; i++) {
                constantHandles.writeLong(i * 8L, toValueHandle(constants[i]));
            }
            try {
                INIT.invokeExact(initFunction, upcalls.getAddress(), constantHandles.getAddress());
            } catch (Throwable t) {
                throw CompilerDirectives.shouldNotReachHere(t);
            }
        }
    }

    @TruffleBoundary
    private static long createUpcallStub(String methodName, String carrierSignature) {
        /* IMPORTANT: for fast Native Image direct upcalls, the MethodHandle must be a direct method handle matching the
         * registered handle shape - here a findStatic handle (findVirtual + bindTo(receiver) would also work), with no
         * asType()/filterArguments()/other adaptation - otherwise SVM silently falls back to the slow generic upcall
         * stub. See FFMSupport#createUpcallStub. */
        final MethodHandle methodHandle;
        try {
            methodHandle = MethodHandles.lookup()
                    .findStatic(CExtUpcallTargets.class, methodName, methodTypeFor(carrierSignature));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return FFMSupport.createUpcallStub(methodHandle, carrierSignature, Arena.global());
    }

    private static MethodType methodTypeFor(String carrierSignature) {
        int firstArgument = carrierSignature.indexOf('(') + 1;
        int arity = carrierSignature.length() - firstArgument - 1;
        Class<?>[] parameters = new Class<?>[arity];
        for (int i = 0; i < arity; i++) {
            parameters[i] = carrierType(carrierSignature.charAt(firstArgument + i));
        }
        char returnCarrier = carrierSignature.charAt(0);
        Class<?> returnType = returnCarrier == 'V' ? void.class : carrierType(returnCarrier);
        return MethodType.methodType(returnType, parameters);
    }

    private static Class<?> carrierType(char carrier) {
        return switch (carrier) {
            case 'I' -> int.class;
            case 'L' -> long.class;
            case 'D' -> double.class;
            default -> throw CompilerDirectives.shouldNotReachHere("unsupported carrier " + carrier);
        };
    }

    @TruffleBoundary
    private long toValueHandle(Object object) {
        final ValueWrapper wrapper = WrapNodeGen.getUncached().execute(object);
        final InteropLibrary interop = InteropLibrary.getUncached();
        try {
            interop.toNative(wrapper);
            return interop.asPointer(wrapper);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    // region Upcall runtime, called by CExtUpcallTargets

    /** Execute the upcall at the given index in CExtUpcallTargets order, with the raw boxed primitive arguments. The
     * argument and result conversions happen in {@link CExtUpcallRootNode} with cached nodes; the result is the boxed
     * return carrier value ({@code Long} for handles and pointers, {@code Integer}, {@code Double}). */
    public Object upcall(int index, Object... arguments) {
        CallTarget callTarget = upcallCallTargets.get(index);
        if (callTarget == null) {
            callTarget = createUpcallCallTarget(index);
        }
        /* Pass null as the location instead of using CallTarget.call(Object...), which would read the encapsulating
         * node: that node belongs to some unrelated Ruby node up the stack (e.g. an uncached block call) and would be
         * recorded as the caller node of the upcall, confusing stack walking: the Ruby frame which made the downcall
         * would get that unrelated node as its FrameInstance#getCallNode() instead of null, breaking e.g.
         * rb_debug_inspector_open(). */
        return callTarget.call(null, arguments);
    }

    @TruffleBoundary
    private CallTarget createUpcallCallTarget(int index) {
        final var spec = CExtUpcallRootNode.UpcallSpec.parse(
                CExtUpcallTargets.UPCALLS[index * 6 + 2], CExtUpcallTargets.UPCALLS[index * 6 + 3],
                CExtUpcallTargets.UPCALLS[index * 6 + 4], CExtUpcallTargets.UPCALLS[index * 6 + 5]);
        final String name = CExtUpcallTargets.UPCALLS[index * 6];
        final CallTarget callTarget = new CExtUpcallRootNode(language, this, name, spec).getCallTarget();
        return upcallCallTargets.compareAndExchange(index, null, callTarget) == null
                ? callTarget
                : upcallCallTargets.get(index);
    }

    /** Whether the current thread is entered in the Ruby context, i.e. ruby_native_thread_p(). Unlike upcalls, this
     * must work on native threads not entered in the context and cannot throw. */
    @TruffleBoundary
    public int isRubyThread() {
        return context.getEnv().getContext().isEntered() ? 1 : 0;
    }

    /** Store the exception as the pending C extension exception for the current thread and set the native pending
     * exception flag, so the native caller longjmps to the innermost setjmp wrapper and the exception is rethrown when
     * the downcall returns. Must not throw, as no Java exception can propagate through native frames. */
    @TruffleBoundary
    public void reportException(Throwable throwable) {
        try {
            final RubyFiber fiber = language.getCurrentFiber();
            fiber.pendingCExtException = throwable;
            /* The flag is a __thread C variable and with virtual thread fibers (--experimental-options
             * --virtual-thread-fibers) the fiber can change native (carrier) thread between downcalls, so the address
             * cannot be cached across downcalls. The native thread cannot change during a downcall (native frames pin
             * virtual threads), so the address stays valid until the current downcall returns to Java. */
            final long flagAddress = invokePendingExceptionAddress();
            fiber.cextPendingExceptionFlagAddress = flagAddress;
            new Pointer(context, flagAddress).writeInt(0, 1);
        } catch (Throwable t) {
            // Nothing must escape to the native caller
            t.printStackTrace();
        }
    }

    // endregion

    /** The method name of a generic invoke upcall, from a C string literal address (cached by address) */
    @TruffleBoundary
    public String readMethodName(long address) {
        final String cached = methodNames.get(address);
        if (cached != null) {
            return cached;
        }
        final String name = readJavaString(address);
        methodNames.putIfAbsent(address, name);
        return name;
    }

    @TruffleBoundary
    private String readJavaString(long address) {
        return new String(readZeroTerminatedByteArray(address), StandardCharsets.UTF_8);
    }

    private byte[] readZeroTerminatedByteArray(long address) {
        final Pointer pointer = new Pointer(context, address);
        return pointer.readZeroTerminatedByteArray(context, 0);
    }

    private long invokePendingExceptionAddress() {
        try {
            return (long) PENDING_EXCEPTION_ADDRESS.invokeExact(pendingExceptionAddressFunction);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    /** Called when a Ruby -> native downcall returns: rethrow the exception captured at an upcall boundary, if any */
    public static void checkPendingException(RubyContext context, RubyFiber fiber) {
        final Throwable pending = fiber.pendingCExtException;
        if (pending != null) {
            fiber.pendingCExtException = null;
            // Clear the native flag; the address was set when the pending exception was reported
            clearPendingExceptionFlag(context, fiber);
            throw sneakyThrow(pending);
        }
    }

    @TruffleBoundary
    private static void clearPendingExceptionFlag(RubyContext context, RubyFiber fiber) {
        new Pointer(context, fiber.cextPendingExceptionFlagAddress).writeInt(0, 0);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    /** Called when the context owning this layer is disposed, so a new context can load C extension support and the
     * disposed context is not kept alive by the process-wide upcall stubs */
    public static void deactivate(CExtFFMLayer layer) {
        synchronized (CExtFFMLayer.class) {
            /* The check ensures a layer which never activated (or was already deactivated) does not overwrite the
             * runtime of a successor context which activated in the meantime. */
            if (activeLayer == layer) {
                activeLayer = null;
                CExtUpcallTargets.setRuntime(null);
            }
        }
    }

}
