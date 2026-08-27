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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import org.truffleruby.core.string.StringUtils;
import org.truffleruby.debug.VariableNamesObject;

/** A native library loaded with dlopen(3) through FFM downcalls. Unlike {@code SymbolLookup#libraryLookup} this allows
 * controlling the RTLD flags, notably RTLD_GLOBAL which is needed so C extensions can resolve {@code rb_*} symbols from
 * libtruffleruby lazily. Libraries are never dlclose()'d: native code may retain function pointers into them for the
 * lifetime of the process.
 *
 * <p>
 * As an interop object, reading a member returns the address of that symbol as a long, or throws
 * {@code UnknownIdentifierException} if the symbol is not found. */
@ExportLibrary(InteropLibrary.class)
public final class NativeLibrary implements TruffleObject {

    private static final MethodHandle DLOPEN = FFMSupport.createDowncallHandle("L(LI)");
    private static final MethodHandle DLSYM = FFMSupport.createDowncallHandle("L(LL)");
    private static final MethodHandle DLERROR = FFMSupport.createDowncallHandle("L()");

    /** Function addresses, resolved lazily so image build time never looks up native symbols */
    private static long dlopenFunction;
    private static long dlsymFunction;
    private static long dlerrorFunction;

    private final String path;
    private final long handle;

    @TruffleBoundary
    public static NativeLibrary open(String path, int flags) {
        ensureFunctionsResolved();
        long handle;
        try (Arena arena = Arena.ofConfined()) {
            long pathPointer = arena.allocateFrom(path).address();
            handle = (long) DLOPEN.invokeExact(dlopenFunction, pathPointer, flags);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
        if (handle == 0) {
            throw new UnsatisfiedLinkError(dlerror());
        }
        return new NativeLibrary(path, handle);
    }

    @TruffleBoundary
    private static synchronized void ensureFunctionsResolved() {
        if (dlopenFunction == 0) {
            var defaultLookup = Linker.nativeLinker().defaultLookup();
            dlopenFunction = defaultLookup.find("dlopen").orElseThrow().address();
            dlsymFunction = defaultLookup.find("dlsym").orElseThrow().address();
            dlerrorFunction = defaultLookup.find("dlerror").orElseThrow().address();
        }
    }

    @TruffleBoundary
    @SuppressWarnings("restricted")
    private static String dlerror() {
        try {
            long message = (long) DLERROR.invokeExact(dlerrorFunction);
            if (message == 0) {
                return "dlerror() returned no error message";
            }
            return MemorySegment.ofAddress(message).reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    private NativeLibrary(String path, long handle) {
        this.path = path;
        this.handle = handle;
    }

    public String getPath() {
        return path;
    }

    /** Returns the address of the symbol, or 0 if not found */
    @TruffleBoundary
    public long lookupSymbol(String name) {
        try (Arena arena = Arena.ofConfined()) {
            long namePointer = arena.allocateFrom(name).address();
            return (long) DLSYM.invokeExact(dlsymFunction, handle, namePointer);
        } catch (Throwable t) {
            throw CompilerDirectives.shouldNotReachHere(t);
        }
    }

    @Override
    public String toString() {
        return "NativeLibrary(" + path + ")";
    }

    // region InteropLibrary messages
    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected Object getMembers(boolean includeInternal) {
        return new VariableNamesObject(StringUtils.EMPTY_STRING_ARRAY);
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMemberReadable(String member) {
        return lookupSymbol(member) != 0;
    }

    @ExportMessage
    @TruffleBoundary
    protected Object readMember(String member) throws UnknownIdentifierException {
        long address = lookupSymbol(member);
        if (address == 0) {
            throw UnknownIdentifierException.create(member);
        }
        return address;
    }
    // endregion

}
