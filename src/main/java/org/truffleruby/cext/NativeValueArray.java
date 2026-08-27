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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import org.truffleruby.RubyContext;
import org.truffleruby.extra.ffi.Pointer;

/** A native VALUE[] (an address and a length), from the A carrier of C extension upcalls (see tool/cext-upcalls.rb).
 * Reading an element returns the raw VALUE handle as a long, like the native memory contains; consumers unwrap the
 * handles with {@code UnwrapNode} (usually through {@code UnwrapNode.UnwrapCArrayNode}). */
@ExportLibrary(InteropLibrary.class)
public final class NativeValueArray implements TruffleObject {

    private final Pointer pointer;
    private final int size;

    public NativeValueArray(RubyContext context, long address, int size) {
        this.pointer = new Pointer(context, address, size * 8L);
        this.size = size;
    }

    @ExportMessage
    protected boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    protected long getArraySize() {
        return size;
    }

    @ExportMessage
    protected boolean isArrayElementReadable(long index) {
        return index >= 0 && index < size;
    }

    @ExportMessage
    @TruffleBoundary
    protected Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            throw InvalidArrayIndexException.create(index);
        }
        return pointer.readLong(index * 8L);
    }

}
