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

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.Nil;
import org.truffleruby.platform.FFMSupport;

/** An executable wrapper around a native function called through an FFM downcall, replacing the previously NFI-bound
 * functions for C extension downcalls (the rb_tr_setjmp_wrapper_* functions and rb_tr_init).
 *
 * <p>
 * Arguments are coerced by carrier: L accepts longs and pointer-like objects (notably {@link ValueWrapper}, allocating
 * a handle via toNative/asPointer like the NFI path did), I ints and D doubles.
 *
 * <p>
 * After the native call returns, the pending C extension exception (captured at an FFM upcall boundary while the native
 * code was running, see {@link CExtUpcallRuntime#reportException}) is rethrown, replacing the Truffle NFI
 * exceptionCheck mechanism. */
@ExportLibrary(InteropLibrary.class)
public final class FFMNativeFunction implements TruffleObject {

    private final RubyContext context;
    private final RubyLanguage language;
    private final long function;
    private final String carrierSignature;
    private final MethodHandle downcallHandle;

    @TruffleBoundary
    public FFMNativeFunction(RubyContext context, RubyLanguage language, long function, String carrierSignature) {
        this.context = context;
        this.language = language;
        this.function = function;
        this.carrierSignature = carrierSignature;
        this.downcallHandle = FFMSupport.createDowncallHandle(carrierSignature);
    }

    public long getFunctionAddress() {
        return function;
    }

    @Override
    public String toString() {
        return "FFMNativeFunction(0x" + Long.toHexString(function) + ", " + carrierSignature + ")";
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    protected Object execute(Object[] arguments) throws ArityException {
        final int firstArgument = carrierSignature.indexOf('(') + 1;
        final int arity = carrierSignature.length() - firstArgument - 1;
        if (arguments.length != arity) {
            throw ArityException.create(arity, arity, arguments.length);
        }

        final List<Object> invokeArguments = new ArrayList<>(1 + arity);
        invokeArguments.add(function);
        for (int i = 0; i < arity; i++) {
            invokeArguments.add(coerceArgument(carrierSignature.charAt(firstArgument + i), arguments[i]));
        }

        final Object result;
        try {
            result = downcallHandle.invokeWithArguments(invokeArguments);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }

        CExtFFMLayer.checkPendingException(context, language.getCurrentFiber());

        if (carrierSignature.charAt(0) == 'V') {
            return Nil.INSTANCE;
        } else {
            return result;
        }
    }

    private static Object coerceArgument(char carrier, Object argument) {
        switch (carrier) {
            case 'L' -> {
                if (argument instanceof Long longValue) {
                    return longValue;
                } else if (argument instanceof Integer intValue) {
                    return (long) intValue;
                } else if (argument == Nil.INSTANCE) {
                    return 0L;
                } else {
                    final InteropLibrary interop = InteropLibrary.getUncached();
                    try {
                        interop.toNative(argument);
                        return interop.asPointer(argument);
                    } catch (Throwable t) {
                        throw CompilerDirectives.shouldNotReachHere(t);
                    }
                }
            }
            case 'I' -> {
                if (argument instanceof Integer intValue) {
                    return intValue;
                } else if (argument instanceof Long longValue) {
                    return Math.toIntExact(longValue);
                } else {
                    throw CompilerDirectives.shouldNotReachHere("cannot coerce to int: " + argument);
                }
            }
            case 'D' -> {
                if (argument instanceof Double doubleValue) {
                    return doubleValue;
                } else {
                    throw CompilerDirectives.shouldNotReachHere("cannot coerce to double: " + argument);
                }
            }
            default -> throw CompilerDirectives.shouldNotReachHere("unsupported carrier " + carrier);
        }
    }

}
