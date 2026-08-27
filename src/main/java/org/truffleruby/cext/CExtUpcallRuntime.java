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

/** The runtime support for {@link CExtUpcallTargets}: argument and result conversions between native carriers and Ruby
 * objects, the dispatch to {@code Truffle::CExt} methods, and the exception boundary. Every upcall runs through these
 * methods; nothing may throw from {@link #reportException(Throwable)} as no Java exception can propagate through native
 * frames.
 *
 * <p>
 * Carrier conventions (see tool/cext-upcalls.rb): VALUE handles and native pointers are longs, C strings are read from
 * native memory, IDs are converted to Symbols. */
public interface CExtUpcallRuntime {

    /** Convert a VALUE handle (carrier V) to the Ruby object */
    Object unwrap(long handle);

    /** Convert an ID (carrier ID) to a Ruby Symbol */
    Object idToSymbol(long id);

    /** Read a NUL-terminated native string (carrier S) */
    Object readString(long address);

    /** A native pointer argument (carrier P), passed to Ruby as a pointer object */
    Object pointerArg(long address);

    /** A native function pointer argument (carrier F), passed to Ruby as a pointer object */
    Object functionArg(long address);

    /** A native VALUE[] (carrier A): read {@code count} handles at {@code address} and unwrap them into a Ruby Array */
    Object valueArray(long address, long count);

    /** Call the Truffle::CExt method {@code name} with the given arguments */
    Object dispatchCExt(String name, Object... arguments);

    /** Call the method {@code name} on {@code receiver} with the given arguments (generic invoke upcalls) */
    Object dispatchMethod(Object receiver, Object name, Object... arguments);

    /** Wrap a Ruby object result (carrier V) and return its VALUE handle */
    long toValueHandle(Object object);

    /** Convert an already-wrapped VALUE result (carrier W) to its handle */
    long wrappedToHandle(Object wrapper);

    /** Convert a Ruby Symbol result to an ID (carrier ID return) */
    long toID(Object symbol);

    int toInt(Object object);

    /** Ruby true/false result (carrier B) as 1/0 */
    int toBooleanInt(Object object);

    long toLong(Object object);

    double toDouble(Object object);

    /** Convert a Ruby pointer-like result (carrier P/F) to a native address */
    long toPointer(Object object);

    /** Store the exception as the pending C extension exception for the current thread and set the native pending
     * exception flag, so the native caller longjmps to the innermost setjmp wrapper. Must not throw. */
    void reportException(Throwable throwable);

}
