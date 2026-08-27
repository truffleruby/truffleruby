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
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.CoreSymbols;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.language.Nil;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.platform.FFMSupport;
import org.truffleruby.platform.NativeLibrary;

/** The FFM-based layer connecting native C extension code and Java/Ruby: creates the native-to-Java upcall stubs for
 * {@link CExtUpcallTargets} and implements their runtime support ({@link CExtUpcallRuntime}).
 *
 * <p>
 * Upcall stubs are allocated in {@link Arena#global()}: native code (including leaked threads and atexit handlers) may
 * hold the function pointers for the lifetime of the process, and libtruffleruby and C extensions are loaded with
 * RTLD_GLOBAL anyway. As a consequence only one Ruby context per process can load C extension support. */
public final class CExtFFMLayer implements CExtUpcallRuntime {

    /** The layer of the single live Ruby context which currently has C extension support loaded: the upcall stubs, the
     * C globals filled by rb_tr_init() and the RTLD_GLOBAL symbols are process-wide. Once that context is disposed, a
     * new context can load C extension support again: it runs rb_tr_init() again with fresh upcall stubs and constant
     * handles, overwriting the C globals. The stubs of disposed contexts are intentionally leaked, as they are
     * allocated in Arena.global(). */
    private static final AtomicReference<CExtFFMLayer> ACTIVE_LAYER = new AtomicReference<>();

    /** rb_tr_init(void** upcalls, const VALUE* constants) */
    private static final MethodHandle INIT = FFMSupport.createDowncallHandle("V(LL)");
    /** int* rb_tr_pending_exception_address(void) */
    private static final MethodHandle PENDING_EXCEPTION_ADDRESS = FFMSupport.createDowncallHandle("L()");

    private final RubyContext context;
    private final RubyLanguage language;
    private final CExtUpcallTargets targets;
    private long pendingExceptionAddressFunction;

    public CExtFFMLayer(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
        this.targets = new CExtUpcallTargets(this);
    }

    @TruffleBoundary
    public void initialize(NativeLibrary library, Object[] constants) {
        if (!ACTIVE_LAYER.compareAndSet(null, this)) {
            throw new RaiseException(context, context.getCoreExceptions().runtimeError(
                    "C extension support can only be loaded in a single Ruby context at a time in a process, " +
                            "because the FFM upcall stubs and RTLD_GLOBAL symbols are process-wide",
                    (Node) null));
        }

        long initFunction = library.lookupSymbol("rb_tr_init");
        if (initFunction == 0) {
            throw CompilerDirectives.shouldNotReachHere("rb_tr_init not found in " + library.getPath());
        }
        pendingExceptionAddressFunction = library.lookupSymbol("rb_tr_pending_exception_address");
        if (pendingExceptionAddressFunction == 0) {
            throw CompilerDirectives.shouldNotReachHere("rb_tr_pending_exception_address not found");
        }

        final String[] upcallsArray = CExtUpcallTargets.UPCALLS;
        final int upcallsCount = upcallsArray.length / 2;
        // rb_tr_init() copies both arrays into C globals, so they can be freed after the call
        try (Pointer upcalls = Pointer.malloc(context, upcallsCount * 8L);
                Pointer constantHandles = Pointer.malloc(context, constants.length * 8L)) {
            for (int i = 0; i < upcallsCount; i++) {
                upcalls.writeLong(i * 8L, createUpcallStub(upcallsArray[i * 2], upcallsArray[i * 2 + 1]));
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
    private long createUpcallStub(String methodName, String carrierSignature) {
        /* IMPORTANT: for fast Native Image direct upcalls, the MethodHandle must be exactly a direct findVirtual handle
         * with only the receiver bound via bindTo() - no asType()/filterArguments()/other adaptation - otherwise SVM
         * silently falls back to the slow generic upcall stub. See FFMSupport#createUpcallStub. */
        final MethodHandle methodHandle;
        try {
            methodHandle = MethodHandles.lookup()
                    .findVirtual(CExtUpcallTargets.class, methodName, methodTypeFor(carrierSignature))
                    .bindTo(targets);
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

    // region CExtUpcallRuntime

    @Override
    public Object unwrap(long handle) {
        return UnwrapNode.UnwrapNativeNode.executeUncached(handle);
    }

    @Override
    @TruffleBoundary
    public Object idToSymbol(long id) {
        if (CoreSymbols.isStaticSymbol(id)) {
            return language.coreSymbols.STATIC_SYMBOLS[CoreSymbols.idToIndex(id)];
        } else {
            return UnwrapNode.UnwrapNativeNode.executeUncached(id);
        }
    }

    @Override
    @TruffleBoundary
    public Object readString(long address) {
        final Pointer pointer = new Pointer(context, address);
        final byte[] bytes = pointer.readZeroTerminatedByteArray(context, InteropLibrary.getUncached(), 0);
        return StringOperations.createUTF8String(context, language, TStringUtils.fromByteArray(bytes, Encodings.UTF_8));
    }

    @TruffleBoundary
    private RubyPointer newRubyPointer(long address) {
        return new RubyPointer(
                context.getCoreLibrary().truffleFFIPointerClass,
                language.truffleFFIPointerShape,
                new Pointer(context, address));
    }

    @Override
    public Object pointerArg(long address) {
        return address == 0 ? Nil.INSTANCE : newRubyPointer(address);
    }

    @Override
    public Object functionArg(long address) {
        return address == 0 ? Nil.INSTANCE : newRubyPointer(address);
    }

    @Override
    @TruffleBoundary
    public Object valueArray(long address, long count) {
        return new NativeValueArray(context, address, (int) count);
    }

    @Override
    @TruffleBoundary
    public Object dispatchCExt(String name, Object... arguments) {
        return DispatchNode.getUncached().call(DispatchConfiguration.PRIVATE,
                context.getCoreLibrary().truffleCExtModule, name, arguments);
    }

    @Override
    @TruffleBoundary
    public Object dispatchMethod(Object receiver, Object name, Object... arguments) {
        final String methodName;
        try {
            methodName = InteropLibrary.getUncached().asString(name);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
        return DispatchNode.getUncached().call(DispatchConfiguration.PRIVATE, receiver, methodName, arguments);
    }

    @Override
    @TruffleBoundary
    public long toValueHandle(Object object) {
        final ValueWrapper wrapper = WrapNodeGen.getUncached().execute(object);
        return wrapperToHandle(wrapper);
    }

    @Override
    @TruffleBoundary
    public long wrappedToHandle(Object wrapper) {
        if (wrapper instanceof Long longValue) {
            // Already a VALUE handle, e.g. returned by a setjmp wrapper downcall
            return longValue;
        }
        return wrapperToHandle((ValueWrapper) wrapper);
    }

    private static long wrapperToHandle(ValueWrapper wrapper) {
        final InteropLibrary interop = InteropLibrary.getUncached();
        try {
            interop.toNative(wrapper);
            return interop.asPointer(wrapper);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    @TruffleBoundary
    public long toID(Object symbol) {
        final RubySymbol rubySymbol = (RubySymbol) symbol;
        if (rubySymbol.getId() != RubySymbol.UNASSIGNED_ID) {
            return rubySymbol.getId();
        } else {
            return toValueHandle(rubySymbol);
        }
    }

    @Override
    @TruffleBoundary
    public int toInt(Object object) {
        try {
            return InteropLibrary.getUncached().asInt(object);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    @TruffleBoundary
    public int toBooleanInt(Object object) {
        try {
            return InteropLibrary.getUncached().asBoolean(object) ? 1 : 0;
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    @TruffleBoundary
    public long toLong(Object object) {
        try {
            return InteropLibrary.getUncached().asLong(object);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    @TruffleBoundary
    public double toDouble(Object object) {
        try {
            return InteropLibrary.getUncached().asDouble(object);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    @TruffleBoundary
    public long toPointer(Object object) {
        if (object == Nil.INSTANCE) {
            return 0;
        } else if (object instanceof Long longValue) {
            return longValue;
        } else if (object instanceof Integer intValue) {
            return intValue;
        }
        final InteropLibrary interop = InteropLibrary.getUncached();
        try {
            interop.toNative(object);
            return interop.asPointer(object);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
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

    /** Called when the context owning this layer is disposed, so a new context can load C extension support */
    public static void deactivate(CExtFFMLayer layer) {
        ACTIVE_LAYER.compareAndSet(layer, null);
    }

    // endregion

}
