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
package org.truffleruby.platform;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/** Shared infrastructure for calling native code (downcalls) and creating native function pointers for Java code
 * (upcalls) with the Java FFM API (java.lang.foreign).
 *
 * <p>
 * Signatures are expressed as carrier signature strings like {@code "I(LLILI)"}: the return carrier followed by the
 * argument carriers in parentheses. Carriers are single characters: {@code B} = byte, {@code S} = short, {@code I} =
 * int, {@code L} = long (also used for all native pointers), {@code F} = float, {@code D} = double, and {@code V} =
 * void (return only). Pointers are always passed as plain {@code long} values ({@link ValueLayout#JAVA_LONG}, never
 * {@code ADDRESS}), so no {@link MemorySegment} ever crosses the native boundary and all handles are primitive-only,
 * which is required for Native Image direct upcalls. */
public final class FFMSupport {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle OF_ADDRESS = createOfAddress();

    private FFMSupport() {
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

    public static MemoryLayout carrierLayout(char carrier) {
        return switch (carrier) {
            case 'B' -> ValueLayout.JAVA_BYTE;
            case 'S' -> ValueLayout.JAVA_SHORT;
            case 'I' -> ValueLayout.JAVA_INT;
            case 'L' -> ValueLayout.JAVA_LONG;
            case 'F' -> ValueLayout.JAVA_FLOAT;
            case 'D' -> ValueLayout.JAVA_DOUBLE;
            default -> throw CompilerDirectives.shouldNotReachHere("unsupported native carrier " + carrier);
        };
    }

    public static FunctionDescriptor functionDescriptor(String carrierSignature) {
        int firstArgument = carrierSignature.indexOf('(') + 1;
        int arity = carrierSignature.length() - firstArgument - 1;
        MemoryLayout[] argumentLayouts = new MemoryLayout[arity];
        for (int i = 0; i < arity; i++) {
            argumentLayouts[i] = carrierLayout(carrierSignature.charAt(firstArgument + i));
        }

        char returnCarrier = carrierSignature.charAt(0);
        return returnCarrier == 'V'
                ? FunctionDescriptor.ofVoid(argumentLayouts)
                : FunctionDescriptor.of(carrierLayout(returnCarrier), argumentLayouts);
    }

    /** Creates a downcall MethodHandle for the given carrier signature, without binding it to a specific native
     * function: the returned handle takes the native function pointer as a leading {@code long} argument, so a single
     * handle (stored in a static final field for constant-folding) serves every native function of that signature. */
    @TruffleBoundary
    @SuppressWarnings("restricted")
    public static MethodHandle createDowncallHandle(String carrierSignature) {
        FunctionDescriptor descriptor = functionDescriptor(carrierSignature);
        MethodHandle downcallHandle = LINKER.downcallHandle(descriptor);
        MethodHandle methodHandle = MethodHandles.filterArguments(downcallHandle, 0, OF_ADDRESS);
        MethodType methodType = descriptor.toMethodType().insertParameterTypes(0, long.class); // the function pointer
        return methodHandle.asType(methodType);
    }

    /** Creates a native function pointer (upcall stub) calling the given Java MethodHandle, and returns its address.
     * The stub is allocated in the given Arena and remains valid until the Arena is closed.
     *
     * <p>
     * <strong>IMPORTANT - Native Image direct upcalls:</strong> for the stub to use the fast "direct upcall" path on
     * Native Image, {@code target} must be <em>exactly</em> one of:
     * <ul>
     * <li>a direct handle from {@code MethodHandles.Lookup#findStatic}, with nothing bound, or</li>
     * <li>a direct handle from {@code findVirtual}/{@code findSpecial} with exactly the receiver bound via
     * {@code MethodHandle#bindTo(receiver)}.</li>
     * </ul>
     * Any other adaptation ({@code asType}, {@code filterArguments}, {@code insertArguments}, wrapping lambdas, etc.)
     * prevents SVM's {@code ForeignFunctionsRuntime#patchForDirectUpcall} from recognizing the target and silently
     * falls back to the much slower generic upcall stub. The target method and its exact native signature must also be
     * registered as a {@code foreign.directUpcalls} entry in the Native Image reachability metadata. All parameter
     * types and the return type of the target method must be primitives. */
    @TruffleBoundary
    @SuppressWarnings("restricted")
    public static long createUpcallStub(MethodHandle target, String carrierSignature, Arena arena) {
        FunctionDescriptor descriptor = functionDescriptor(carrierSignature);
        return LINKER.upcallStub(target, descriptor, arena).address();
    }

}
