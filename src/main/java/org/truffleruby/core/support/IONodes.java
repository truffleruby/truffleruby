/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is transliterated from C++ code in Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2007, 2008 Ola Bini <ola@ologix.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.core.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.AllocationTracing;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "IO", isClass = true)
public abstract class IONodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyIO allocate(RubyClass rubyClass) {
            final RubyIO instance = new RubyIO(rubyClass, getLanguage().ioShape, RubyIO.CLOSED_FD);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @Primitive(name = "io_fd")
    public abstract static class IOFDNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int fd(RubyIO io) {
            return io.getDescriptor();
        }
    }

    @Primitive(name = "io_set_fd", lowerFixnum = 1)
    public abstract static class IOSetFDNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyIO fd(RubyIO io, int fd) {
            io.setDescriptor(fd);
            return io;
        }
    }

    @Primitive(name = "file_fnmatch", lowerFixnum = 2)
    public abstract static class FileFNMatchPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "stringsPattern.isRubyString(node, pattern)", "stringsPath.isRubyString(node, path)" },
                limit = "1")
        static boolean fnmatch(Object pattern, Object path, int flags,
                @Bind Node node,
                @Cached RubyStringLibrary stringsPattern,
                @Cached RubyStringLibrary stringsPath,
                @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayPatternNode,
                @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayPathNode) {
            var patternByteArray = getInternalByteArrayPatternNode.execute(stringsPattern.getTString(node, pattern),
                    stringsPattern.getTEncoding(node, pattern));
            var pathByteArray = getInternalByteArrayPathNode.execute(stringsPath.getTString(node, path),
                    stringsPath.getTEncoding(node, path));

            return fnmatch(
                    patternByteArray.getArray(),
                    patternByteArray.getOffset(),
                    patternByteArray.getEnd(),
                    pathByteArray.getArray(),
                    pathByteArray.getOffset(),
                    pathByteArray.getEnd(),
                    flags) != FNM_NOMATCH;
        }

        private static final int FNM_NOESCAPE = 0x01;
        private static final int FNM_PATHNAME = 0x02;
        private static final int FNM_DOTMATCH = 0x04;
        private static final int FNM_CASEFOLD = 0x08;

        public static final int FNM_NOMATCH = 1;

        private static boolean isdirsep(char c) {
            return c == '/';
        }

        private static boolean isdirsep(byte c) {
            return isdirsep((char) (c & 0xFF));
        }

        private static int rb_path_next(byte[] _s, int s, int send) {
            while (s < send && !isdirsep(_s[s])) {
                s++;
            }
            return s;
        }

        @SuppressWarnings("fallthrough")
        private static int fnmatch_helper(byte[] bytes, int pstart, int pend, byte[] string, int sstart, int send,
                int flags) {
            char test;
            int s = sstart;
            int pat = pstart;
            boolean escape = (flags & FNM_NOESCAPE) == 0;
            boolean pathname = (flags & FNM_PATHNAME) != 0;
            boolean period = (flags & FNM_DOTMATCH) == 0;
            boolean nocase = (flags & FNM_CASEFOLD) != 0;

            while (pat < pend) {
                char c = (char) (bytes[pat++] & 0xFF);
                switch (c) {
                    case '?':
                        if (s >= send || (pathname && isdirsep(string[s])) ||
                                (period && string[s] == '.' &&
                                        (s == sstart || (pathname && isdirsep(string[s - 1]))))) {
                            return FNM_NOMATCH;
                        }
                        s++;
                        break;
                    case '*':
                        while (pat < pend && (c = (char) (bytes[pat++] & 0xFF)) == '*') {
                        }
                        if (s < send &&
                                (period && string[s] == '.' &&
                                        (s == sstart || (pathname && isdirsep(string[s - 1]))))) {
                            return FNM_NOMATCH;
                        }
                        if (pat > pend || (pat == pend && c == '*')) {
                            if (pathname && rb_path_next(string, s, send) < send) {
                                return FNM_NOMATCH;
                            } else {
                                return 0;
                            }
                        } else if ((pathname && isdirsep(c))) {
                            s = rb_path_next(string, s, send);
                            if (s < send) {
                                s++;
                                break;
                            }
                            return FNM_NOMATCH;
                        }
                        test = (char) (escape && c == '\\' && pat < pend ? (bytes[pat] & 0xFF) : c);
                        test = Character.toLowerCase(test);
                        pat--;
                        while (s < send) {
                            if ((c == '?' || c == '[' || Character.toLowerCase((char) string[s]) == test) &&
                                    fnmatch(bytes, pat, pend, string, s, send, flags | FNM_DOTMATCH) == 0) {
                                return 0;
                            } else if ((pathname && isdirsep(string[s]))) {
                                break;
                            }
                            s++;
                        }
                        return FNM_NOMATCH;
                    case '[':
                        if (s >= send || (pathname && isdirsep(string[s]) ||
                                (period && string[s] == '.' &&
                                        (s == sstart || (pathname && isdirsep(string[s - 1])))))) {
                            return FNM_NOMATCH;
                        }
                        pat = range(bytes, pat, pend, (char) (string[s] & 0xFF), flags);
                        if (pat == -1) {
                            return FNM_NOMATCH;
                        }
                        s++;
                        break;
                    case '\\':
                        if (escape) {
                            if (pat >= pend) {
                                c = '\\';
                            } else {
                                c = (char) (bytes[pat++] & 0xFF);
                            }
                        }
                    default:
                        if (s >= send) {
                            return FNM_NOMATCH;
                        }
                        if (nocase) {
                            if (Character.toLowerCase(c) != Character.toLowerCase((char) string[s])) {
                                return FNM_NOMATCH;
                            }

                        } else {
                            if (c != (char) (string[s] & 0xFF)) {
                                return FNM_NOMATCH;
                            }
                        }
                        s++;
                        break;
                }
            }
            return s >= send ? 0 : FNM_NOMATCH;
        }

        @TruffleBoundary
        public static int fnmatch(
                byte[] bytes, int pstart, int pend,
                byte[] string, int sstart, int send, int flags) {

            // This method handles '**/' patterns and delegates to
            // fnmatch_helper for the main work.

            boolean period = (flags & FNM_DOTMATCH) == 0;
            boolean pathname = (flags & FNM_PATHNAME) != 0;

            int pat_pos = pstart;
            int str_pos = sstart;
            int ptmp = -1;
            int stmp = -1;

            if (pathname) {
                while (true) {
                    if (isDoubleStarAndSlash(bytes, pat_pos)) {
                        do {
                            pat_pos += 3;
                        } while (isDoubleStarAndSlash(bytes, pat_pos));
                        ptmp = pat_pos;
                        stmp = str_pos;
                    }

                    int patSlashIdx = nextSlashIndex(bytes, pat_pos, pend);
                    int strSlashIdx = nextSlashIndex(string, str_pos, send);

                    if (fnmatch_helper(
                            bytes,
                            pat_pos,
                            patSlashIdx,
                            string,
                            str_pos,
                            strSlashIdx,
                            flags) == 0) {
                        if (patSlashIdx < pend && strSlashIdx < send) {
                            pat_pos = ++patSlashIdx;
                            str_pos = ++strSlashIdx;
                            continue;
                        }
                        if (patSlashIdx == pend && strSlashIdx == send) {
                            return 0;
                        }
                    }
                    /* failed : try next recursion */
                    if (ptmp != -1 && stmp != -1 && !(period && stmp < string.length && string[stmp] == '.')) {
                        stmp = nextSlashIndex(string, stmp, send);
                        if (stmp < send) {
                            pat_pos = ptmp;
                            stmp++;
                            str_pos = stmp;
                            continue;
                        }
                    }
                    return FNM_NOMATCH;
                }
            } else {
                return fnmatch_helper(bytes, pstart, pend, string, sstart, send, flags);
            }

        }

        // are we at '**/'
        private static boolean isDoubleStarAndSlash(byte[] bytes, int pos) {
            if ((bytes.length - pos) <= 2) {
                return false; // not enough bytes
            }

            return bytes[pos] == '*' && bytes[pos + 1] == '*' && bytes[pos + 2] == '/';
        }

        // Look for slash, starting from 'start' position, until 'end'.
        private static int nextSlashIndex(byte[] bytes, int start, int end) {
            int idx = start;
            while (idx < end && idx < bytes.length && bytes[idx] != '/') {
                idx++;
            }
            return idx;
        }

        private static int range(byte[] _pat, int pat, int pend, char test, int flags) {
            boolean not;
            boolean ok = false;
            boolean nocase = (flags & FNM_CASEFOLD) != 0;
            boolean escape = (flags & FNM_NOESCAPE) == 0;

            not = _pat[pat] == '!' || _pat[pat] == '^';
            if (not) {
                pat++;
            }

            if (nocase) {
                test = Character.toLowerCase(test);
            }

            while (_pat[pat] != ']') {
                char cstart, cend;
                if (escape && _pat[pat] == '\\') {
                    pat++;
                }
                if (pat >= pend) {
                    return -1;
                }
                cstart = cend = (char) (_pat[pat++] & 0xFF);
                if (pat < (pend - 1) && _pat[pat] == '-' && _pat[pat + 1] != ']') {
                    pat++;
                    if (escape && _pat[pat] == '\\') {
                        pat++;
                    }
                    if (pat >= pend) {
                        return -1;
                    }

                    cend = (char) (_pat[pat++] & 0xFF);
                }

                if (nocase) {
                    if (Character.toLowerCase(cstart) <= test && test <= Character.toLowerCase(cend)) {
                        ok = true;
                    }
                } else {
                    if (cstart <= test && test <= cend) {
                        ok = true;
                    }
                }
                if (pat >= pend) {
                    return -1;
                }
            }

            return ok == not ? -1 : pat + 1;
        }

    }

    @NonStandard
    @CoreMethod(names = "ensure_open", visibility = Visibility.PRIVATE)
    public abstract static class IOEnsureOpenPrimitiveNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object ensureOpen(RubyIO io,
                @Cached InlinedBranchProfile errorProfile) {
            final int fd = io.getDescriptor();
            if (fd == RubyIO.CLOSED_FD) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().ioError("closed stream", this));
            } else {
                assert fd >= 0;
                return nil;
            }
        }

    }

    @Primitive(name = "io_read_polyglot", lowerFixnum = 0)
    public abstract static class IOReadPolyglotNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object read(int length,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final InputStream stream = getContext().getEnv().in();
            final byte[] buffer = new byte[length];
            final int bytesRead = getContext().getThreadManager().runUntilResult(this, () -> {
                try {
                    return stream.read(buffer, 0, length);
                } catch (IOException e) {
                    throw new RaiseException(getContext(), coreExceptions().ioError(e, this));
                }
            });

            if (bytesRead < 0) {
                return nil;
            }

            final byte[] bytes;
            if (bytesRead == buffer.length) {
                bytes = buffer;
            } else {
                bytes = Arrays.copyOf(buffer, bytesRead);
            }

            return createString(fromByteArrayNode, bytes, Encodings.BINARY);
        }

    }

    @Primitive(name = "io_write_polyglot", lowerFixnum = 0)
    public abstract static class IOWritePolyglotNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "strings.isRubyString(this, string)", limit = "1")
        int write(int fd, Object string,
                @Cached RubyStringLibrary strings) {
            final OutputStream stream;

            switch (fd) {
                case 1:
                    stream = getContext().getEnv().out();
                    break;
                case 2:
                    stream = getContext().getEnv().err();
                    break;
                default:
                    // already checked in the caller
                    throw CompilerDirectives.shouldNotReachHere();
            }

            var byteArray = strings.getTString(this, string)
                    .getInternalByteArrayUncached(strings.getTEncoding(this, string));

            getContext().getThreadManager().runUntilResult(this, () -> {
                try {
                    stream.write(byteArray.getArray(), byteArray.getOffset(), byteArray.getLength());
                } catch (IOException e) {
                    throw new RaiseException(getContext(), coreExceptions().ioError(e, this));
                }
                return BlockingAction.SUCCESS;
            });

            return byteArray.getLength();
        }

    }

    @Primitive(name = "io_thread_buffer_allocate")
    public abstract static class IOThreadBufferAllocateNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyPointer getThreadBuffer(long size,
                @Cached InlinedConditionProfile sizeProfile) {
            RubyThread thread = getLanguage().getCurrentThread();
            final RubyPointer instance = new RubyPointer(
                    coreLibrary().truffleFFIPointerClass,
                    getLanguage().truffleFFIPointerShape,
                    getBuffer(this, getContext(), thread, size, sizeProfile));
            AllocationTracing.trace(instance, this);
            return instance;
        }

        public static Pointer getBuffer(Node node, RubyContext context, RubyThread rubyThread, long size,
                InlinedConditionProfile sizeProfile) {
            return rubyThread.getIoBuffer(context).allocate(node, context, rubyThread, size, sizeProfile);
        }
    }

    @Primitive(name = "io_thread_buffer_free")
    public abstract static class IOThreadBufferFreeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object getThreadBuffer(RubyPointer pointer,
                @Cached InlinedConditionProfile freeProfile) {
            RubyThread thread = getLanguage().getCurrentThread();
            thread.getIoBuffer(getContext()).free(this, thread, pointer.pointer, freeProfile);
            return nil;
        }
    }

}
