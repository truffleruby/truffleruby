/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
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
 ***** END LICENSE BLOCK *****/
package org.truffleruby.core.string;

import static com.oracle.truffle.api.strings.TruffleString.CodeRange.ASCII;
import static com.oracle.truffle.api.strings.TruffleString.CodeRange.VALID;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CreateBackwardCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleString.FromByteArrayNode;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import org.graalvm.collections.Pair;
import org.graalvm.shadowed.org.jcodings.Config;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.IntHolder;
import org.graalvm.shadowed.org.jcodings.ascii.AsciiTables;
import org.graalvm.shadowed.org.jcodings.constants.CharacterType;
import org.graalvm.shadowed.org.jcodings.specific.ASCIIEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF8Encoding;
import org.graalvm.shadowed.org.jcodings.util.IntHash;
import org.truffleruby.RubyContext;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.collections.IntHashMap;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.utils.Utils;

public final class StringSupport {
    public static final int TRANS_SIZE = 256;

    // We don't know how many characters the case map operation will produce and it requires a non-exposed
    // minimum buffer size for its internal operations. We create a buffer larger than expected to avoid
    // exceeding the buffer size.
    private static final int CASE_MAP_BUFFER_SIZE = 32;

    /** codeRange==null means unknown. recoverIfBroken=false so can return negative values. */
    private static int characterLength(Encoding encoding, TruffleString.CodeRange codeRange, byte[] bytes,
            int byteOffset, int byteEnd) {
        assert byteOffset >= 0 && byteOffset < byteEnd && byteEnd <= bytes.length;

        if (codeRange == null) {
            return preciseLength(encoding, bytes, byteOffset, byteEnd);
        }

        switch (codeRange) {
            case ASCII:
                return 1;
            case VALID:
                return characterLengthValid(encoding, bytes, byteOffset, byteEnd);
            case BROKEN:
                return preciseLength(encoding, bytes, byteOffset, byteEnd);
            default:
                throw Utils.unsupportedOperation("unknown code range value: ", codeRange);
        }
    }

    /** recoverIfBroken=false so can return negative values */
    public static int characterLength(RubyEncoding encoding, byte[] bytes, int byteOffset, int byteEnd) {
        assert byteOffset >= 0 && byteOffset < byteEnd && byteEnd <= bytes.length;
        return preciseLength(encoding.jcoding, bytes, byteOffset, byteEnd);
    }

    private static int characterLengthValid(Encoding encoding, byte[] bytes, int byteOffset, int byteEnd) {
        if (encoding.isUTF8()) {
            return utf8CharWidth(bytes[byteOffset]);
        } else if (encoding.isAsciiCompatible()) {
            if (bytes[byteOffset] >= 0) {
                return 1;
            } else {
                return encLength(encoding, bytes, byteOffset, byteEnd);
            }
        } else if (encoding.isFixedWidth()) {
            final int width = encoding.minLength();
            assert (byteEnd - byteOffset) >= width;
            return width;
        } else {
            return encLength(encoding, bytes, byteOffset, byteEnd);
        }
    }

    public static int utf8CharWidth(byte b) {
        if (b >= 0) {
            return 1;
        } else {
            switch (b & 0xf0) {
                case 0xe0:
                    return 3;
                case 0xf0:
                    return 4;
                default:
                    return 2;
            }
        }
    }


    /** This method returns the byte length of the first encountered character in `bytes`, starting at offset `p` and
     * ending at byte position `e`. The `Encoding` implementation will perform character validation and return a
     * negative number if the byte sequence does not correspond to a valid character. Otherwise, the byte length of the
     * character is returned. See the docs for `Encoding#length` for more details.
     *
     * It is up to the caller to check if the return value is negative. In practice, it is expected this method is only
     * called when the caller knows the code range of the byte sequence is either `CR_7BIT` or `CR_VALID`, in which case
     * no check on the return values is necessary -- it will always be positive.
     *
     * Corresponding MRI method: rb_enc_fast_mbclen */
    @TruffleBoundary
    private static int encLength(Encoding enc, byte[] bytes, int p, int e) {
        return enc.length(bytes, p, e);
    }

    /** This method functions like `StringSupport.encLength`, but differs when an invalid character is encountered
     * (i.e., a negative byte length). In such cases, it attempts to perform a limited form of error recovery. It checks
     * the `Encoding`'s minimum length to see if it's small enough to fit within the range [end - p]. If it is, the
     * method pretends a character with a byte length equal to the `Encoding`'s minimum length was discovered and that
     * byte length is returned. If the minimum character length can't fit in the range, this method pretends a character
     * with a byte length corresponding to the size of the range was encountered and the range size is returned. If a
     * valid character was encountered, its byte length is returned just as would be the case with
     * `StringSupport.encLength`. Consequently, this method never returns a negative value.
     *
     * Ruby allows Strings with a `CR_BROKEN` code range to propagate through to an end user, who may call methods on
     * that String. This variant of getting a character's length is designed is intended to be used in such cases. E.g.,
     * if calling `String#each_char` on a String that is `CR_BROKEN`, returning negative values for the character length
     * would break iteration. In such cases, Ruby just pretends broken byte sequences have some arbitrary, but
     * deterministic, positive byte length.
     *
     * Corresponding MRI method: rb_enc_mbclen */
    public static int length(Encoding enc, byte[] bytes, int p, int end) {
        int n = encLength(enc, bytes, p, end);
        if (MBCLEN_CHARFOUND_P(n) && MBCLEN_CHARFOUND_LEN(n) <= end - p) {
            return MBCLEN_CHARFOUND_LEN(n);
        }
        int min = enc.minLength();
        return min <= end - p ? min : end - p;
    }

    /** This method functions like `StringSupport.encLength`, but differs when a character sequence is too short. In
     * such cases, it examines the return value from `Encoding#length` and if exceeds the length of the byte sequence,
     * it returns the number of bytes required to make the character valid, but negated. Since the value is negated, the
     * caller can distinguish between good character lengths ad bad ones by checking the sign of the value.
     *
     * It is intended to be called when then code range of the byte sequence is unknown. In such cases, it cannot be
     * trusted like `StringSupport.encLength`. Nor is it safe to recover invalid byte sequences as is done with
     * `StringSupport.length`.
     *
     * Corresponding MRI method: rb_enc_precise_mbclen */
    private static int preciseLength(Encoding enc, byte[] bytes, int p, int end) {
        if (p >= end) {
            return MBCLEN_NEEDMORE(1);
        }
        int n = encLength(enc, bytes, p, end);
        if (n > end - p) {
            return MBCLEN_NEEDMORE(n - (end - p));
        }
        return n;
    }

    // MBCLEN_NEEDMORE_P, ONIGENC_MBCLEN_NEEDMORE_P
    public static boolean MBCLEN_NEEDMORE_P(int r) {
        return r < -1;
    }

    // MBCLEN_NEEDMORE_LEN, ONIGENC_MBCLEN_NEEDMORE_LEN
    public static int MBCLEN_NEEDMORE_LEN(int r) {
        return -1 - r;
    }

    // MBCLEN_NEEDMORE, ONIGENC_MBCLEN_NEEDMORE
    public static int MBCLEN_NEEDMORE(int n) {
        return -1 - n;
    }

    // MBCLEN_INVALID_P, ONIGENC_MBCLEN_INVALID_P
    public static boolean MBCLEN_INVALID_P(int r) {
        return r == -1;
    }

    // MBCLEN_CHARFOUND_LEN, ONIGENC_MBCLEN_CHARFOUND_LEN
    public static int MBCLEN_CHARFOUND_LEN(int r) {
        assert MBCLEN_CHARFOUND_P(r);
        return r;
    }

    // MBCLEN_CHARFOUND_P, ONIGENC_MBCLEN_CHARFOUND_P
    public static boolean MBCLEN_CHARFOUND_P(int r) {
        return 0 < r;
    }

    @CompilationFinal(dimensions = 1) private static final byte[] NON_ASCII_NEEDLE = { (byte) 0b1111_1111 };
    @CompilationFinal(dimensions = 1) private static final byte[] NON_ASCII_MASK = { 0b0111_1111 };

    // MRI: search_nonascii
    /** NOTE: this returns a logical offset, not the offset in the byteArray. */
    public static int searchNonAscii(InternalByteArray byteArray, int start) {
        final int offset = byteArray.getOffset();
        return searchNonAscii(byteArray.getArray(), offset + start, byteArray.getEnd()) - offset;
    }

    // MRI: search_nonascii
    public static int searchNonAscii(byte[] bytes, int p, int end) {
        return com.oracle.truffle.api.ArrayUtils.indexOfWithOrMask(bytes, p, end - p, NON_ASCII_NEEDLE, NON_ASCII_MASK);
    }

    // MRI: rb_enc_strlen / enc_strlen
    @TruffleBoundary
    public static int strLength(RubyEncoding encoding, byte[] bytes, int p, int e) {
        var tstring = FromByteArrayNode.getUncached().execute(bytes, p, e - p, encoding.tencoding, false);
        return tstring.codePointLengthUncached(encoding.tencoding);
    }

    public static int codePoint(Encoding enc, byte[] bytes, int p, int end, Node node) {
        return codePoint(enc, null, bytes, p, end, node);
    }

    @TruffleBoundary
    public static int codePoint(Encoding enc, TruffleString.CodeRange codeRange, byte[] bytes, int p, int end,
            Node node) {
        if (p >= end) {
            final RubyContext context = RubyContext.get(node);
            throw new RaiseException(context, context.getCoreExceptions().argumentError("empty string", node));
        }
        int cl = characterLength(enc, codeRange, bytes, p, end);
        if (cl <= 0) {
            final RubyContext context = RubyContext.get(node);
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError("invalid byte sequence in " + enc, node));
        }
        return enc.mbcToCode(bytes, p, end);
    }

    /** Returns a negative value for invalid code points, callers should check for that unless they can guarantee the
     * code point is valid. */
    @TruffleBoundary
    public static int codeLength(Encoding enc, int c) {
        return enc.codeToMbcLength(c);
    }

    @TruffleBoundary
    public static int mbcToCode(Encoding encoding, byte[] bytes, int p, int end) {
        return encoding.mbcToCode(bytes, p, end);
    }

    public static int offset(int start, int end, int charEnd) {
        return charEnd == -1 ? end - start : Math.min(end, charEnd) - start;
    }

    public static int scanHex(byte[] bytes, int p, int len) {
        return scanHex(bytes, p, len, ASCIIEncoding.INSTANCE);
    }

    @TruffleBoundary
    public static int scanHex(byte[] bytes, int p, int len, Encoding enc) {
        int v = 0;
        int c;
        while (len-- > 0 && enc.isXDigit(c = bytes[p++] & 0xff)) {
            v = (v << 4) + enc.xdigitVal(c);
        }
        return v;
    }

    public static int hexLength(byte[] bytes, int p, int len) {
        return hexLength(bytes, p, len, ASCIIEncoding.INSTANCE);
    }

    @TruffleBoundary
    public static int hexLength(byte[] bytes, int p, int len, Encoding enc) {
        int hlen = 0;
        while (len-- > 0 && enc.isXDigit(bytes[p++] & 0xff)) {
            hlen++;
        }
        return hlen;
    }

    public static int scanOct(byte[] bytes, int p, int len) {
        return scanOct(bytes, p, len, ASCIIEncoding.INSTANCE);
    }

    @TruffleBoundary
    public static int scanOct(byte[] bytes, int p, int len, Encoding enc) {
        int v = 0;
        int c;
        while (len-- > 0 && enc.isDigit(c = bytes[p++] & 0xff) && c < '8') {
            v = (v << 3) + Encoding.digitVal(c);
        }
        return v;
    }

    public static int octLength(byte[] bytes, int p, int len) {
        return octLength(bytes, p, len, ASCIIEncoding.INSTANCE);
    }

    @TruffleBoundary
    public static int octLength(byte[] bytes, int p, int len, Encoding enc) {
        int olen = 0;
        int c;
        while (len-- > 0 && enc.isDigit(c = bytes[p++] & 0xff) && c < '8') {
            olen++;
        }
        return olen;
    }

    public static String escapedCharFormat(int c, boolean isUnicode) {
        String format;
        // c comparisons must be unsigned 32-bit
        if (isUnicode) {

            if ((c & 0xFFFFFFFFL) < 0x7F && Encoding.isAscii(c) && ASCIIEncoding.INSTANCE.isPrint(c)) {
                format = "%c";
            } else if (c < 0x10000) {
                format = "\\u%04X";
            } else {
                format = "\\u{%X}";
            }
        } else {
            if ((c & 0xFFFFFFFFL) < 0x100) {
                format = "\\x%02X";
            } else {
                format = "\\x{%X}";
            }
        }
        return format;
    }

    /** rb_str_count */
    @TruffleBoundary
    public static int strCount(InternalByteArray byteArray, TruffleString.CodeRange codeRange, boolean[] table,
            TrTables tables, Encoding enc, Node node) {
        final byte[] bytes = byteArray.getArray();
        int p = byteArray.getOffset();
        final int end = byteArray.getEnd();
        final boolean asciiCompat = enc.isAsciiCompatible();

        int count = 0;
        while (p < end) {
            int c;
            if (asciiCompat && (c = bytes[p] & 0xff) < 0x80) {
                if (table[c]) {
                    count++;
                }
                p++;
            } else {
                c = codePoint(enc, codeRange, bytes, p, end, node);
                int cl = codeLength(enc, c);
                if (trFind(c, table, tables)) {
                    count++;
                }
                p += cl;
            }
        }

        return count;
    }

    public static char[] bytesToChars(InternalByteArray byteArray) {
        final int byteLength = byteArray.getLength();
        final char[] chars = new char[byteLength];

        for (int n = 0; n < byteLength; n++) {
            chars[n] = (char) byteArray.get(n);
        }

        return chars;
    }

    // rb_enc_ascget
    private static int encAscget(byte[] pBytes, int p, int e, int[] len, Encoding enc,
            TruffleString.CodeRange codeRange) {
        int c;
        int l;

        if (e <= p) {
            return -1;
        }

        if (EncodingUtils.encAsciicompat(enc)) {
            c = pBytes[p] & 0xFF;
            if (!Encoding.isAscii((byte) c)) {
                return -1;
            }
            if (len != null) {
                len[0] = 1;
            }
            return c;
        }
        l = characterLength(enc, codeRange, pBytes, p, e);
        if (!MBCLEN_CHARFOUND_P(l)) {
            return -1;
        }
        c = enc.mbcToCode(pBytes, p, e);
        if (!Encoding.isAscii(c)) {
            return -1;
        }
        if (len != null) {
            len[0] = l;
        }
        return c;
    }

    // rb_enc_codepoint_len
    @TruffleBoundary
    private static int encCodepointLength(byte[] pBytes, int p, int e, int[] len_p, Encoding enc,
            TruffleString.CodeRange codeRange, Node node) {
        int r;
        if (e <= p) {
            final RubyContext context = RubyContext.get(node);
            throw new RaiseException(context, context.getCoreExceptions().argumentError("empty string", node));
        }
        r = characterLength(enc, codeRange, pBytes, p, e);
        if (!MBCLEN_CHARFOUND_P(r)) {
            final RubyContext context = RubyContext.get(node);
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError("invalid byte sequence in " + enc, node));
        }
        if (len_p != null) {
            len_p[0] = MBCLEN_CHARFOUND_LEN(r);
        }
        return codePoint(enc, codeRange, pBytes, p, e, node);
    }

    /** rb_str_tr / rb_str_tr_bang */
    public static final class TR {
        public TR(AbstractTruffleString string, RubyEncoding encoding) {
            var bytes = string.getInternalByteArrayUncached(encoding.tencoding);
            p = bytes.getOffset();
            pend = bytes.getEnd();
            buf = bytes.getArray();
            now = max = 0;
            gen = false;
        }

        final byte[] buf;
        int p, pend, now, max;
        boolean gen;
    }

    /** tr_setup_table */
    public static final class TrTables {
        IntHashMap<Object> del, noDel; // used as ~ Set
    }

    private static final Object DUMMY_VALUE = "";

    @TruffleBoundary
    public static TrTables trSetupTable(AbstractTruffleString str, RubyEncoding encoding, boolean[] stable,
            TrTables tables, boolean first, Encoding enc, Node node) {
        int i, l[] = { 0 };
        final boolean cflag;

        final TR tr = new TR(str, encoding);

        var codeRange = str.getByteCodeRangeUncached(encoding.tencoding);
        if (str.byteLength(encoding.tencoding) > 1 &&
                encAscget(tr.buf, tr.p, tr.pend, l, enc, codeRange) == '^') {
            cflag = true;
            tr.p += l[0];
        } else {
            cflag = false;
        }

        if (first) {
            for (i = 0; i < TRANS_SIZE; i++) {
                stable[i] = true;
            }
            stable[TRANS_SIZE] = cflag;
        } else if (stable[TRANS_SIZE] && !cflag) {
            stable[TRANS_SIZE] = false;
        }

        if (tables == null) {
            tables = new TrTables();
        }

        byte[] buf = null; // lazy initialized
        IntHashMap<Object> table = null, ptable = null;

        int c;
        while ((c = trNext(tr, enc, codeRange, node)) != -1) {
            if (c < TRANS_SIZE) {
                if (buf == null) { // initialize buf
                    buf = new byte[TRANS_SIZE];
                    for (i = 0; i < TRANS_SIZE; i++) {
                        buf[i] = (byte) (cflag ? 1 : 0);
                    }
                }
                // update the buff at [c] :
                buf[c & 0xff] = (byte) (cflag ? 0 : 1);
            } else {
                if (table == null && (first || tables.del != null || stable[TRANS_SIZE])) {
                    if (cflag) {
                        ptable = tables.noDel;
                        table = ptable != null ? ptable : new IntHashMap<>(8);
                        tables.noDel = table;
                    } else {
                        table = new IntHashMap<>(8);
                        ptable = tables.del;
                        tables.del = table;
                    }
                }

                if (table != null) {
                    final int key = c;
                    if (ptable == null) {
                        table.put(key, DUMMY_VALUE);
                    } else {
                        if (cflag) {
                            table.put(key, DUMMY_VALUE);
                        } else {
                            final boolean val = ptable.get(key) != null;
                            table.put(key, val ? DUMMY_VALUE : null);
                        }
                    }
                }
            }
        }
        if (buf != null) {
            for (i = 0; i < TRANS_SIZE; i++) {
                stable[i] = stable[i] && buf[i] != 0;
            }
        } else {
            for (i = 0; i < TRANS_SIZE; i++) {
                stable[i] = stable[i] && cflag;
            }
        }

        if (table == null && !cflag) {
            tables.del = null;
        }

        return tables;
    }

    public static boolean trFind(final int c, final boolean[] table, final TrTables tables) {
        if (c < TRANS_SIZE) {
            return table[c];
        }

        final IntHashMap<Object> del = tables.del, noDel = tables.noDel;

        if (del != null) {
            if (del.get(c) != null &&
                    (noDel == null || noDel.get(c) == null)) {
                return true;
            }
        } else if (noDel != null && noDel.get(c) != null) {
            return false;
        }

        return table[TRANS_SIZE];
    }

    @TruffleBoundary
    public static int trNext(TR tr, Encoding enc, TruffleString.CodeRange codeRange, Node node) {
        for (;;) {
            if (!tr.gen) {
                return trNext_nextpart(tr, enc, codeRange, node);
            }

            while (enc.codeToMbcLength(++tr.now) <= 0) {
                if (tr.now == tr.max) {
                    tr.gen = false;
                    return trNext_nextpart(tr, enc, codeRange, node);
                }
            }
            if (tr.now < tr.max) {
                return tr.now;
            } else {
                tr.gen = false;
                return tr.max;
            }
        }
    }

    private static int trNext_nextpart(TR tr, Encoding enc, TruffleString.CodeRange codeRange, Node node) {
        final int[] n = { 0 };

        if (tr.p == tr.pend) {
            return -1;
        }
        if (encAscget(tr.buf, tr.p, tr.pend, n, enc, codeRange) == '\\' && tr.p + n[0] < tr.pend) {
            tr.p += n[0];
        }
        tr.now = encCodepointLength(tr.buf, tr.p, tr.pend, n, enc, codeRange, node);
        tr.p += n[0];
        if (encAscget(tr.buf, tr.p, tr.pend, n, enc, codeRange) == '-' && tr.p + n[0] < tr.pend) {
            tr.p += n[0];
            if (tr.p < tr.pend) {
                int c = encCodepointLength(tr.buf, tr.p, tr.pend, n, enc, codeRange, node);
                tr.p += n[0];
                if (tr.now > c) {
                    final RubyContext context = RubyContext.get(node);
                    if (tr.now < 0x80 && c < 0x80) {
                        final String message = "invalid range \"" + (char) tr.now + '-' + (char) c +
                                "\" in string transliteration";
                        throw new RaiseException(context, context.getCoreExceptions().argumentError(message, node));
                    }

                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().argumentError("invalid range in string transliteration", node));
                }
                tr.gen = true;
                tr.max = c;
            }
        }
        return tr.now;
    }

    public enum NeighborChar {
        NOT_CHAR,
        FOUND,
        WRAPPED
    }

    // MRI: str_succ
    @TruffleBoundary
    public static TStringBuilder succCommon(RubyString original, Node node) {
        byte carry[] = new byte[Config.ENC_CODE_TO_MBC_MAXLEN];
        int carryP = 0;
        carry[0] = 1;
        int carryLen = 1;

        final RubyEncoding encoding = original.getEncodingUncached();
        final Encoding enc = encoding.jcoding;
        TStringBuilder valueCopy = TStringBuilder.create(original);
        int p = 0;
        int end = p + valueCopy.getLength();
        int s = end;
        byte[] bytes = valueCopy.getUnsafeBytes();

        NeighborChar neighbor = NeighborChar.FOUND;
        int lastAlnum = -1;
        boolean alnumSeen = false;
        while ((s = enc.prevCharHead(bytes, p, s, end)) != -1) {
            if (neighbor == NeighborChar.NOT_CHAR && lastAlnum != -1) {
                ASCIIEncoding ascii = ASCIIEncoding.INSTANCE;
                if (ascii.isAlpha(bytes[lastAlnum] & 0xff)
                        ? ascii.isDigit(bytes[s] & 0xff)
                        : ascii.isDigit(bytes[lastAlnum] & 0xff) ? ascii.isAlpha(bytes[s] & 0xff) : false) {
                    s = lastAlnum;
                    break;
                }
            }

            int cl = characterLength(encoding, bytes, s, end);
            if (cl <= 0) {
                continue;
            }
            switch (neighbor = succAlnumChar(encoding, bytes, s, cl, carry, 0, node)) {
                case NOT_CHAR:
                    continue;
                case FOUND:
                    return valueCopy;
                case WRAPPED:
                    lastAlnum = s;
            }
            alnumSeen = true;
            carryP = s - p;
            carryLen = cl;
        }

        if (!alnumSeen) {
            s = end;
            while ((s = enc.prevCharHead(bytes, p, s, end)) != -1) {
                int cl = characterLength(encoding, bytes, s, end);
                if (cl <= 0) {
                    continue;
                }
                neighbor = succChar(encoding, bytes, s, cl, node);
                if (neighbor == NeighborChar.FOUND) {
                    return valueCopy;
                }
                if (characterLength(encoding, bytes, s, s + 1) != cl) {
                    succChar(encoding, bytes, s, cl, node); /* wrapped to \0...\0. search next valid char. */
                }
                if (!encoding.isAsciiCompatible) {
                    System.arraycopy(bytes, s, carry, 0, cl);
                    carryLen = cl;
                }
                carryP = s - p;
            }
        }
        valueCopy.unsafeEnsureSpace(valueCopy.getLength() + carryLen);
        s = carryP;
        System.arraycopy(
                valueCopy.getUnsafeBytes(),
                s,
                valueCopy.getUnsafeBytes(),
                s + carryLen,
                valueCopy.getLength() - carryP);
        System.arraycopy(carry, 0, valueCopy.getUnsafeBytes(), s, carryLen);
        valueCopy.setLength(valueCopy.getLength() + carryLen);
        return valueCopy;
    }

    // MRI: enc_succ_char
    public static NeighborChar succChar(RubyEncoding encoding, byte[] bytes, int p, int len, Node node) {
        Encoding enc = encoding.jcoding;
        int l;
        if (enc.minLength() > 1) {
            /* wchar, trivial case */
            int r = characterLength(encoding, bytes, p, p + len), c;
            if (!MBCLEN_CHARFOUND_P(r)) {
                return NeighborChar.NOT_CHAR;
            }
            c = codePoint(enc, bytes, p, p + len, node) + 1;
            l = codeLength(enc, c);
            if (l == 0) {
                return NeighborChar.NOT_CHAR;
            }
            if (l != len) {
                return NeighborChar.WRAPPED;
            }
            enc.codeToMbc(c, bytes, p);
            r = characterLength(encoding, bytes, p, p + len);
            if (!MBCLEN_CHARFOUND_P(r)) {
                return NeighborChar.NOT_CHAR;
            }
            return NeighborChar.FOUND;
        }

        while (true) {
            int i = len - 1;
            for (; i >= 0 && bytes[p + i] == (byte) 0xff; i--) {
                bytes[p + i] = 0;
            }
            if (i < 0) {
                return NeighborChar.WRAPPED;
            }
            bytes[p + i] = (byte) ((bytes[p + i] & 0xff) + 1);
            l = characterLength(encoding, bytes, p, p + len);
            if (MBCLEN_CHARFOUND_P(l)) {
                l = MBCLEN_CHARFOUND_LEN(l);
                if (l == len) {
                    return NeighborChar.FOUND;
                } else {
                    int start = p + l;
                    int end = start + (len - l);
                    Arrays.fill(bytes, start, end, (byte) 0xff);
                }
            }
            if (MBCLEN_INVALID_P(l) && i < len - 1) {
                int len2;
                int l2;
                for (len2 = len - 1; 0 < len2; len2--) {
                    l2 = characterLength(encoding, bytes, p, p + len2);
                    if (!MBCLEN_INVALID_P(l2)) {
                        break;
                    }
                }
                int start = p + len2 + 1;
                int end = start + len - (len2 + 1);
                Arrays.fill(bytes, start, end, (byte) 0xff);
            }
        }
    }

    // MRI: enc_succ_alnum_char
    private static NeighborChar succAlnumChar(RubyEncoding encoding, byte[] bytes, int p, int len, byte[] carry,
            int carryP, Node node) {
        Encoding enc = encoding.jcoding;
        byte save[] = new byte[Config.ENC_CODE_TO_MBC_MAXLEN];
        int c = enc.mbcToCode(bytes, p, p + len);

        final int cType;
        if (enc.isDigit(c)) {
            cType = CharacterType.DIGIT;
        } else if (enc.isAlpha(c)) {
            cType = CharacterType.ALPHA;
        } else {
            return NeighborChar.NOT_CHAR;
        }

        System.arraycopy(bytes, p, save, 0, len);
        NeighborChar ret = succChar(encoding, bytes, p, len, node);
        if (ret == NeighborChar.FOUND) {
            c = enc.mbcToCode(bytes, p, p + len);
            if (enc.isCodeCType(c, cType)) {
                return NeighborChar.FOUND;
            }
        }

        System.arraycopy(save, 0, bytes, p, len);
        int range = 1;

        while (true) {
            System.arraycopy(bytes, p, save, 0, len);
            ret = predChar(encoding, bytes, p, len, node);
            if (ret == NeighborChar.FOUND) {
                c = enc.mbcToCode(bytes, p, p + len);
                if (!enc.isCodeCType(c, cType)) {
                    System.arraycopy(save, 0, bytes, p, len);
                    break;
                }
            } else {
                System.arraycopy(save, 0, bytes, p, len);
                break;
            }
            range++;
        }

        if (range == 1) {
            return NeighborChar.NOT_CHAR;
        }

        if (cType != CharacterType.DIGIT) {
            System.arraycopy(bytes, p, carry, carryP, len);
            return NeighborChar.WRAPPED;
        }

        System.arraycopy(bytes, p, carry, carryP, len);
        succChar(encoding, carry, carryP, len, node);
        return NeighborChar.WRAPPED;
    }

    private static NeighborChar predChar(RubyEncoding encoding, byte[] bytes, int p, int len, Node node) {
        Encoding enc = encoding.jcoding;
        int l;
        if (enc.minLength() > 1) {
            /* wchar, trivial case */
            int r = characterLength(encoding, bytes, p, p + len), c;
            if (!MBCLEN_CHARFOUND_P(r)) {
                return NeighborChar.NOT_CHAR;
            }
            c = codePoint(enc, bytes, p, p + len, node);
            if (c == 0) {
                return NeighborChar.NOT_CHAR;
            }
            --c;
            l = codeLength(enc, c);
            if (l == 0) {
                return NeighborChar.NOT_CHAR;
            }
            if (l != len) {
                return NeighborChar.WRAPPED;
            }
            enc.codeToMbc(c, bytes, p);
            r = characterLength(encoding, bytes, p, p + len);
            if (!MBCLEN_CHARFOUND_P(r)) {
                return NeighborChar.NOT_CHAR;
            }
            return NeighborChar.FOUND;
        }
        while (true) {
            int i = len - 1;
            for (; i >= 0 && bytes[p + i] == 0; i--) {
                bytes[p + i] = (byte) 0xff;
            }
            if (i < 0) {
                return NeighborChar.WRAPPED;
            }
            bytes[p + i] = (byte) ((bytes[p + i] & 0xff) - 1);
            l = characterLength(encoding, bytes, p, p + len);
            if (MBCLEN_CHARFOUND_P(l)) {
                l = MBCLEN_CHARFOUND_LEN(l);
                if (l == len) {
                    return NeighborChar.FOUND;
                } else {
                    int start = p + l;
                    int end = start + (len - l);
                    Arrays.fill(bytes, start, end, (byte) 0x0);
                }
            }
            if (!MBCLEN_CHARFOUND_P(l) && i < len - 1) {
                int len2;
                int l2;
                for (len2 = len - 1; 0 < len2; len2--) {
                    l2 = characterLength(encoding, bytes, p, p + len2);
                    if (!MBCLEN_INVALID_P(l2)) {
                        break;
                    }
                }
                int start = p + len2 + 1;
                int end = start + (len - (len2 + 1));
                Arrays.fill(bytes, start, end, (byte) 0);
            }
        }
    }

    /** rb_str_delete_bang */
    @TruffleBoundary
    public static TruffleString delete_bangCommon19(ATStringWithEncoding rubyString, boolean[] squeeze, TrTables tables,
            RubyEncoding encoding, Node node) {
        Encoding enc = encoding.jcoding;
        int s = 0;
        int t = s;
        int send = s + rubyString.byteLength();
        byte[] bytes = rubyString.getBytesCopy();
        boolean modified = false;
        boolean asciiCompatible = encoding.isAsciiCompatible;
        var cr = asciiCompatible ? ASCII : VALID;
        while (s < send) {
            int c;
            if (asciiCompatible && Encoding.isAscii(c = bytes[s] & 0xff)) {
                if (squeeze[c]) {
                    modified = true;
                } else {
                    if (t != s) {
                        bytes[t] = (byte) c;
                    }
                    t++;
                }
                s++;
            } else {
                c = codePoint(enc, rubyString.getCodeRange(), bytes, s, send, node);
                int cl = codeLength(enc, c);
                if (trFind(c, squeeze, tables)) {
                    modified = true;
                } else {
                    if (t != s) {
                        enc.codeToMbc(c, bytes, t);
                    }
                    t += cl;
                    if (cr == ASCII) {
                        cr = VALID;
                    }
                }
                s += cl;
            }
        }

        return modified
                ? TStringUtils.fromByteArray(ArrayUtils.extractRange(bytes, 0, t), encoding)
                /* cr */ : null;
    }

    /** rb_str_tr / rb_str_tr_bang */

    private static TruffleString.CodeRange CHECK_IF_ASCII(int c, TruffleString.CodeRange currentCodeRange) {
        if (currentCodeRange == ASCII && !Encoding.isAscii(c)) {
            return VALID;
        }

        return currentCodeRange;
    }

    @TruffleBoundary
    public static TruffleString trTransHelper(ATStringWithEncoding self, ATStringWithEncoding srcStr,
            ATStringWithEncoding replStr, Encoding e1, RubyEncoding rubyEncoding,
            boolean sflag, Node node) {
        // This method does not handle the cases where either srcStr or replStr are empty.  It is the responsibility
        // of the caller to take the appropriate action in those cases.

        final Encoding enc = rubyEncoding.jcoding;
        var cr = self.getCodeRange();

        final StringSupport.TR trSrc = new StringSupport.TR(srcStr.tstring, srcStr.encoding);
        boolean cflag = false;
        int[] l = { 0 };

        if (srcStr.byteLength() > 1 &&
                encAscget(trSrc.buf, trSrc.p, trSrc.pend, l, enc, srcStr.getCodeRange()) == '^' &&
                trSrc.p + 1 < trSrc.pend) {
            cflag = true;
            trSrc.p++;
        }

        int c, c0, last = 0;
        final int[] trans = new int[StringSupport.TRANS_SIZE];
        final StringSupport.TR trRepl = new StringSupport.TR(replStr.tstring, replStr.encoding);
        boolean modified = false;
        IntHash<Integer> hash = null;
        boolean singlebyte = self.isSingleByteOptimizable();

        if (cflag) {
            for (int i = 0; i < StringSupport.TRANS_SIZE; i++) {
                trans[i] = 1;
            }

            while ((c = StringSupport.trNext(trSrc, enc, srcStr.getCodeRange(), node)) != -1) {
                if (c < StringSupport.TRANS_SIZE) {
                    trans[c] = -1;
                } else {
                    if (hash == null) {
                        hash = new IntHash<>();
                    }
                    hash.put(c, 1); // QTRUE
                }
            }
            while ((c = StringSupport.trNext(trRepl, enc, replStr.getCodeRange(), node)) != -1) {
                /* retrieve last replacer */
            }
            last = trRepl.now;
            for (int i = 0; i < StringSupport.TRANS_SIZE; i++) {
                if (trans[i] != -1) {
                    trans[i] = last;
                }
            }
        } else {
            for (int i = 0; i < StringSupport.TRANS_SIZE; i++) {
                trans[i] = -1;
            }

            while ((c = StringSupport.trNext(trSrc, enc, srcStr.getCodeRange(), node)) != -1) {
                int r = StringSupport.trNext(trRepl, enc, replStr.getCodeRange(), node);
                if (r == -1) {
                    r = trRepl.now;
                }
                if (c < StringSupport.TRANS_SIZE) {
                    trans[c] = r;
                    if (codeLength(enc, r) != 1) {
                        singlebyte = false;
                    }
                } else {
                    if (hash == null) {
                        hash = new IntHash<>();
                    }
                    hash.put(c, r);
                }
            }
        }

        if (cr == VALID && rubyEncoding.isAsciiCompatible) {
            cr = ASCII;
        }

        int s = 0;
        int send = self.byteLength();

        final TruffleString ret;
        if (sflag) {
            byte[] sbytes = self.getBytesOrCopy();
            int clen, tlen;
            int max = self.byteLength();
            int save = -1;
            byte[] buf = new byte[max];
            int t = 0;
            while (s < send) {
                boolean mayModify = false;
                c0 = c = codePoint(e1, sbytes, s, send, node);
                clen = codeLength(e1, c);
                tlen = enc == e1 ? clen : codeLength(enc, c);
                s += clen;

                if (c < TRANS_SIZE) {
                    c = trCode(c, trans, hash, cflag, last, false);
                } else if (hash != null) {
                    Integer tmp = hash.get(c);
                    if (tmp == null) {
                        if (cflag) {
                            c = last;
                        } else {
                            c = -1;
                        }
                    } else if (cflag) {
                        c = -1;
                    } else {
                        c = tmp;
                    }
                } else {
                    c = -1;
                }

                if (c != -1) {
                    if (save == c) {
                        cr = CHECK_IF_ASCII(c, cr);
                        continue;
                    }
                    save = c;
                    tlen = codeLength(enc, c);
                    modified = true;
                } else {
                    save = -1;
                    c = c0;
                    if (enc != e1) {
                        mayModify = true;
                    }
                }

                while (t + tlen >= max) {
                    max *= 2;
                    buf = Arrays.copyOf(buf, max);
                }
                enc.codeToMbc(c, buf, t);
                // MRI does not check s < send again because their null terminator can still be compared
                if (mayModify && (s >= send || !ArrayUtils.regionEquals(sbytes, s, buf, t, tlen))) {
                    modified = true;
                }
                cr = CHECK_IF_ASCII(c, cr);
                t += tlen;
            }

            ret = TStringUtils.fromByteArray(ArrayUtils.extractRange(buf, 0, t), rubyEncoding); // cr
        } else if (rubyEncoding.isSingleByte || (singlebyte && hash == null)) {
            byte[] sbytes = self.getBytesCopy();
            while (s < send) {
                c = sbytes[s] & 0xff;
                if (trans[c] != -1) {
                    if (!cflag) {
                        c = trans[c];
                        sbytes[s] = (byte) c;
                    } else {
                        sbytes[s] = (byte) last;
                    }
                    modified = true;
                }
                cr = CHECK_IF_ASCII(c, cr);
                s++;
            }

            ret = TStringUtils.fromByteArray(sbytes, rubyEncoding); // cr
        } else {
            byte[] sbytes = self.getBytesOrCopy();
            int clen, tlen, max = (int) (self.byteLength() * 1.2);
            byte[] buf = new byte[max];
            int t = 0;

            while (s < send) {
                boolean mayModify = false;
                c0 = c = codePoint(e1, sbytes, s, send, node);
                clen = codeLength(e1, c);
                tlen = enc == e1 ? clen : codeLength(enc, c);

                if (c < TRANS_SIZE) {
                    c = trans[c];
                } else if (hash != null) {
                    Integer tmp = hash.get(c);
                    if (tmp == null) {
                        if (cflag) {
                            c = last;
                        } else {
                            c = -1;
                        }
                    } else if (cflag) {
                        c = -1;
                    } else {
                        c = tmp;
                    }
                } else {
                    c = cflag ? last : -1;
                }
                if (c != -1) {
                    tlen = codeLength(enc, c);
                    modified = true;
                } else {
                    c = c0;
                    if (enc != e1) {
                        mayModify = true;
                    }
                }
                while (t + tlen >= max) {
                    max <<= 1;
                    buf = Arrays.copyOf(buf, max);
                }
                // headius: I don't see how s and t could ever be the same, since they refer to different buffers
                //                if (s != t) {
                enc.codeToMbc(c, buf, t);
                if (mayModify && !ArrayUtils.regionEquals(sbytes, s, buf, t, tlen)) {
                    modified = true;
                }
                //                }

                cr = CHECK_IF_ASCII(c, cr);
                s += clen;
                t += tlen;
            }

            ret = TStringUtils.fromByteArray(ArrayUtils.extractRange(buf, 0, t), rubyEncoding); // cr
        }

        if (modified) {
            return ret;
        }

        return null;
    }

    private static int trCode(int c, int[] trans, IntHash<Integer> hash, boolean cflag, int last, boolean set) {
        if (c < StringSupport.TRANS_SIZE) {
            return trans[c];
        } else if (hash != null) {
            Integer tmp = hash.get(c);
            if (tmp == null) {
                return cflag ? last : -1;
            } else {
                return cflag ? -1 : tmp;
            }
        } else {
            return cflag && set ? last : -1;
        }
    }

    @TruffleBoundary
    public static int multiByteCasecmp(RubyEncoding enc, AbstractTruffleString selfTString,
            TruffleString.Encoding selfEncoding, AbstractTruffleString otherTString,
            TruffleString.Encoding otherEncoding) {
        var selfIterator = CreateCodePointIteratorNode.getUncached().execute(selfTString, selfEncoding,
                ErrorHandling.RETURN_NEGATIVE);
        var otherIterator = CreateCodePointIteratorNode.getUncached().execute(otherTString, otherEncoding,
                ErrorHandling.RETURN_NEGATIVE);

        while (selfIterator.hasNext() && otherIterator.hasNext()) {
            final int selfPos = selfIterator.getByteIndex();
            final int c = selfIterator.nextUncached(selfEncoding);

            final int otherPos = otherIterator.getByteIndex();
            final int oc = otherIterator.nextUncached(otherEncoding);

            if (enc.isAsciiCompatible && (c >= 0 && Encoding.isAscii(c)) && (oc >= 0 && Encoding.isAscii(oc))) {
                byte uc = AsciiTables.ToUpperCaseTable[c];
                byte uoc = AsciiTables.ToUpperCaseTable[oc];
                if (uc != uoc) {
                    return uc < uoc ? -1 : 1;
                }
            } else {
                final int cl = selfIterator.getByteIndex() - selfPos;
                final int ocl = otherIterator.getByteIndex() - otherPos;

                // TODO: opt for 2 and 3 ?
                int ret = caseCmp(selfTString, selfEncoding, otherTString, otherEncoding, selfPos, otherPos,
                        Math.min(cl, ocl));
                if (ret != 0) {
                    return ret < 0 ? -1 : 1;
                }
                if (cl != ocl) {
                    return cl < ocl ? -1 : 1;
                }
            }

        }

        if (!selfIterator.hasNext() && !otherIterator.hasNext()) {
            return 0;
        }
        return selfIterator.hasNext() ? 1 : -1;
    }

    private static int caseCmp(AbstractTruffleString a, TruffleString.Encoding aEncoding,
            AbstractTruffleString b, TruffleString.Encoding bEncoding, int aPos, int bPos, int len) {
        int i = 0;
        while (i < len && a.readByteUncached(aPos + i, aEncoding) == b.readByteUncached(bPos + i, bEncoding)) {
            i++;
        }
        if (i < len) {
            return a.readByteUncached(aPos + i, aEncoding) > b.readByteUncached(bPos + i,
                    bEncoding) ? 1 : -1;
        }
        return 0;
    }

    public static boolean singleByteSqueeze(TStringBuilder value, boolean squeeze[]) {
        int s = 0;
        int t = s;
        int send = s + value.getLength();
        final byte[] bytes = value.getUnsafeBytes();
        int save = -1;

        while (s < send) {
            int c = bytes[s++] & 0xff;
            if (c != save || !squeeze[c]) {
                bytes[t++] = (byte) (save = c);
            }
        }

        if (t != value.getLength()) { // modified
            value.setLength(t);
            return true;
        }

        return false;
    }

    @TruffleBoundary
    public static boolean multiByteSqueeze(TStringBuilder value, TruffleString.CodeRange originalCodeRange,
            boolean[] squeeze, TrTables tables, Encoding enc, boolean isArg, Node node) {
        int s = 0;
        int t = s;
        int send = s + value.getLength();
        byte[] bytes = value.getUnsafeBytes();
        int save = -1;
        int c;

        while (s < send) {
            if (enc.isAsciiCompatible() && (c = bytes[s] & 0xff) < 0x80) {
                if (c != save || (isArg && !squeeze[c])) {
                    bytes[t++] = (byte) (save = c);
                }
                s++;
            } else {
                c = codePoint(enc, originalCodeRange, bytes, s, send, node);
                int cl = codeLength(enc, c);
                if (c != save || (isArg && !trFind(c, squeeze, tables))) {
                    if (t != s) {
                        enc.codeToMbc(c, bytes, t);
                    }
                    save = c;
                    t += cl;
                }
                s += cl;
            }
        }

        if (t != value.getLength()) { // modified
            value.setLength(t);
            return true;
        }

        return false;
    }

    public static int singleByteRstripEndIndex(InternalByteArray byteArray, boolean[] squeeze) {
        final int len = byteArray.getLength();
        if (len == 0) {
            return -1;
        }

        final int lastByte = byteArray.get(len - 1) & 0xff;
        if (squeeze != null ? !squeeze[lastByte] : !isAsciiSpaceOrNull(lastByte)) {
            return -1;
        }

        int i = len - 2;
        while (i >= 0) {
            final int b = byteArray.get(i) & 0xff;
            if (squeeze != null ? !squeeze[b] : !isAsciiSpaceOrNull(b)) {
                return i + 1;
            }
            i--;
        }

        return 0;
    }

    @TruffleBoundary
    public static int multiByteRstripEndIndex(AbstractTruffleString tstring, RubyEncoding encoding,
            boolean[] squeeze, TrTables tables, Node node) {
        final var tencoding = encoding.tencoding;
        var iterator = CreateBackwardCodePointIteratorNode.getUncached().execute(tstring, tencoding,
                ErrorHandling.RETURN_NEGATIVE);
        int codePoint = TruffleStringIterator.PreviousNode.getUncached().execute(iterator, tencoding);

        if (codePoint == -1) {
            throw new RaiseException(RubyContext.get(node),
                    RubyContext.get(node).getCoreExceptions().argumentErrorInvalidByteSequence(encoding, node));
        }

        boolean matches = squeeze != null ? trFind(codePoint, squeeze, tables) : isAsciiSpaceOrNull(codePoint);
        if (!matches) {
            return -1;
        }

        while (iterator.hasPrevious()) {
            int byteIndex = iterator.getByteIndex();
            codePoint = TruffleStringIterator.PreviousNode.getUncached().execute(iterator, tencoding);

            if (codePoint == -1) {
                throw new RaiseException(RubyContext.get(node),
                        RubyContext.get(node).getCoreExceptions().argumentErrorInvalidByteSequence(encoding, node));
            }

            matches = squeeze != null ? trFind(codePoint, squeeze, tables) : isAsciiSpaceOrNull(codePoint);
            if (!matches) {
                return byteIndex;
            }
        }

        return 0;
    }

    // region Case Mapping Methods

    @TruffleBoundary
    private static int caseMapChar(int codePoint, Encoding enc, byte[] stringBytes, int stringByteOffset,
            ByteArrayBuilder builder, IntHolder flags, byte[] workBuffer) {
        final IntHolder fromP = new IntHolder();
        fromP.value = stringByteOffset;

        final int clen = enc.codeToMbcLength(codePoint);

        final int newByteLength = enc
                .caseMap(flags, stringBytes, fromP, fromP.value + clen, workBuffer, 0, workBuffer.length);

        if (clen == newByteLength) {
            System.arraycopy(workBuffer, 0, stringBytes, stringByteOffset, newByteLength);
        } else {
            final int tailLength = stringBytes.length - (stringByteOffset + clen);
            final int newBufferLength = stringByteOffset + newByteLength + tailLength;
            final byte[] newBuffer = Arrays.copyOf(stringBytes, newBufferLength);

            System.arraycopy(workBuffer, 0, newBuffer, stringByteOffset, newByteLength);
            System.arraycopy(
                    stringBytes,
                    stringByteOffset + clen,
                    newBuffer,
                    stringByteOffset + newByteLength,
                    tailLength);

            builder.unsafeReplace(newBuffer, newBufferLength);
        }

        return newByteLength;
    }

    @TruffleBoundary
    public static boolean swapCaseMultiByteComplex(Encoding enc, TruffleString.CodeRange originalCodeRange,
            ByteArrayBuilder builder, int caseMappingOptions, Node node) {
        byte[] buf = new byte[CASE_MAP_BUFFER_SIZE];

        final IntHolder flagP = new IntHolder();
        flagP.value = caseMappingOptions | Config.CASE_UPCASE | Config.CASE_DOWNCASE;

        boolean modified = false;
        int s = 0;
        byte[] bytes = builder.getUnsafeBytes();

        while (s < bytes.length) {
            int c = codePoint(enc, originalCodeRange, bytes, s, bytes.length, node);
            if (enc.isUpper(c) || enc.isLower(c)) {
                s += caseMapChar(c, enc, bytes, s, builder, flagP, buf);
                modified = true;

                if (bytes != builder.getUnsafeBytes()) {
                    bytes = builder.getUnsafeBytes();
                }
            } else {
                s += codeLength(enc, c);
            }
        }

        return modified;
    }

    @TruffleBoundary
    public static boolean downcaseMultiByteComplex(Encoding enc, TruffleString.CodeRange originalCodeRange,
            ByteArrayBuilder builder, int caseMappingOptions, Node node) {
        byte[] buf = new byte[CASE_MAP_BUFFER_SIZE];

        final IntHolder flagP = new IntHolder();
        flagP.value = caseMappingOptions | Config.CASE_DOWNCASE;

        final boolean isFold = (caseMappingOptions & Config.CASE_FOLD) != 0;
        final boolean isTurkic = (caseMappingOptions & Config.CASE_FOLD_TURKISH_AZERI) != 0;

        boolean modified = false;
        int s = 0;
        byte[] bytes = builder.getUnsafeBytes();

        while (s < bytes.length) {
            if (!isTurkic && enc.isAsciiCompatible() && isAsciiUppercase(bytes[s])) {
                bytes[s] ^= 0x20;
                modified = true;
                s++;
            } else {
                final int c = codePoint(enc, originalCodeRange, bytes, s, bytes.length, node);

                if (isFold || enc.isUpper(c)) {
                    s += caseMapChar(c, enc, bytes, s, builder, flagP, buf);
                    modified = true;

                    if (bytes != builder.getUnsafeBytes()) {
                        bytes = builder.getUnsafeBytes();
                    }
                } else {
                    s += codeLength(enc, c);
                }
            }
        }

        return modified;
    }

    @TruffleBoundary
    public static boolean upcaseMultiByteComplex(Encoding enc, TruffleString.CodeRange originalCodeRange,
            ByteArrayBuilder builder, int caseMappingOptions, Node node) {
        byte[] buf = new byte[CASE_MAP_BUFFER_SIZE];

        final IntHolder flagP = new IntHolder();
        flagP.value = caseMappingOptions | Config.CASE_UPCASE;

        final boolean isTurkic = (caseMappingOptions & Config.CASE_FOLD_TURKISH_AZERI) != 0;
        boolean modified = false;
        int s = 0;
        byte[] bytes = builder.getUnsafeBytes();

        while (s < bytes.length) {
            if (!isTurkic && enc.isAsciiCompatible() && isAsciiLowercase(bytes[s])) {
                bytes[s] ^= 0x20;
                modified = true;
                s++;
            } else {
                final int c = codePoint(enc, originalCodeRange, bytes, s, bytes.length, node);

                if (enc.isLower(c)) {
                    s += caseMapChar(c, enc, bytes, s, builder, flagP, buf);
                    modified = true;

                    if (bytes != builder.getUnsafeBytes()) {
                        bytes = builder.getUnsafeBytes();
                    }
                } else {
                    s += codeLength(enc, c);
                }
            }
        }

        return modified;
    }

    @TruffleBoundary
    public static boolean capitalizeMultiByteComplex(Encoding enc, TruffleString.CodeRange originalCodeRange,
            ByteArrayBuilder builder,
            int caseMappingOptions, Node node) {
        byte[] buf = new byte[CASE_MAP_BUFFER_SIZE];

        final IntHolder flagP = new IntHolder();
        flagP.value = caseMappingOptions | Config.CASE_UPCASE | Config.CASE_TITLECASE;

        final boolean isTurkic = (caseMappingOptions & Config.CASE_FOLD_TURKISH_AZERI) != 0;
        boolean modified = false;
        int s = 0;
        byte[] bytes = builder.getUnsafeBytes();
        boolean upcasing = true;

        while (s < bytes.length) {
            if (!isTurkic && enc.isAsciiCompatible() &&
                    ((upcasing && isAsciiLowercase(bytes[s])) || (!upcasing && isAsciiUppercase(bytes[s])))) {
                bytes[s] ^= 0x20;
                modified = true;
                s++;
            } else {
                final int c = codePoint(enc, originalCodeRange, bytes, s, bytes.length, node);

                if ((upcasing && enc.isLower(c)) || (!upcasing && enc.isUpper(c))) {
                    s += caseMapChar(c, enc, bytes, s, builder, flagP, buf);
                    modified = true;

                    if (bytes != builder.getUnsafeBytes()) {
                        bytes = builder.getUnsafeBytes();
                    }
                } else {
                    s += codeLength(enc, c);
                }
            }

            if (upcasing) {
                upcasing = false;
                flagP.value = caseMappingOptions | Config.CASE_DOWNCASE;
            }
        }

        return modified;
    }

    //endregion
    //region Predicates

    /** Like {@link Encoding#isAscii(int)} but correct */
    public static boolean isAscii(int c) {
        return c >= 0 && c < 128;
    }

    public static boolean isAsciiLowercase(int c) {
        return c >= 'a' && c <= 'z';
    }

    public static boolean isAsciiLowercase(byte c) {
        return c >= 'a' && c <= 'z';
    }

    public static boolean isAsciiUppercase(int c) {
        return c >= 'A' && c <= 'Z';
    }

    public static boolean isAsciiUppercase(byte c) {
        return c >= 'A' && c <= 'Z';
    }

    /** MRI: ISSPACE() and rb_isspace() True for ' ', \t, \n, \v, \f, and \r */
    public static boolean isAsciiSpace(int c) {
        return c == ' ' || ('\t' <= c && c <= '\r');
    }

    static boolean isAsciiSpaceOrNull(int c) {
        return c == 0 || isAsciiSpace(c);
    }

    public static boolean isAsciiPrintable(int c) {
        return c >= ' ' && c <= '~';
    }

    //endregion
    //region undump helpers

    private static final byte[] FORCE_ENCODING_BYTES = StringOperations.encodeAsciiBytes(".force_encoding(\"");
    private static final byte[] HEXDIGIT = StringOperations.encodeAsciiBytes("0123456789abcdef0123456789ABCDEF");
    private static final String INVALID_FORMAT_MESSAGE = "invalid dumped string; not wrapped with '\"' nor '\"...\".force_encoding(\"...\")' form";

    @TruffleBoundary
    public static Pair<TStringBuilder, RubyEncoding> undump(ATStringWithEncoding tstring, RubyEncoding encoding,
            RubyContext context,
            Node currentNode) {
        var byteArray = tstring.getInternalByteArray();
        byte[] bytes = byteArray.getArray();
        int start = byteArray.getOffset();
        final int end = byteArray.getEnd();
        RubyEncoding resultEncoding = encoding;
        Encoding[] enc = { encoding.jcoding };
        boolean[] utf8 = { false };
        boolean[] binary = { false };
        TStringBuilder undumped = new TStringBuilder();
        undumped.setEncoding(encoding);

        var cr = tstring.getCodeRange();
        if (cr != ASCII) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError("non-ASCII character detected", currentNode));
        }

        if (ArrayUtils.memchr(bytes, start, byteArray.getLength(), (byte) '\0') != -1) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError("string contains null byte", currentNode));
        }
        if (end - start < 2) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
        }
        if (bytes[start] != '"') {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
        }
        /* strip '"' at the start */
        start++;

        for (;;) {
            if (start >= end) {
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().runtimeError("unterminated dumped string", currentNode));
            }

            if (bytes[start] == '"') {
                /* epilogue */
                start++;
                if (start == end) {
                    /* ascii compatible dumped string */
                    break;
                } else {
                    int size;

                    if (utf8[0]) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(
                                        "dumped string contained Unicode escape but used force_encoding",
                                        currentNode));
                    }

                    size = FORCE_ENCODING_BYTES.length;
                    if (end - start <= size) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
                    }
                    if (!ArrayUtils.regionEquals(bytes, start, FORCE_ENCODING_BYTES, 0, size)) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
                    }
                    start += size;

                    int encname = start;
                    start = ArrayUtils.memchr(bytes, start, end - start, (byte) '"');
                    size = start - encname;
                    if (start == -1) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
                    }
                    if (end - start != 2) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
                    }
                    if (bytes[start] != '"' || bytes[start + 1] != ')') {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(INVALID_FORMAT_MESSAGE, currentNode));
                    }
                    String encnameString = TStringUtils.bytesToJavaStringOrThrow(bytes, encname, size, encoding);
                    RubyEncoding enc2 = context.getEncodingManager().getRubyEncoding(encnameString);
                    if (enc2 == null) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError(
                                        "dumped string has unknown encoding name",
                                        currentNode));
                    }
                    undumped.setEncoding(enc2);
                    resultEncoding = enc2;
                }
                break;
            }

            if (bytes[start] == '\\') {
                start++;
                if (start >= end) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError("invalid escape", currentNode));
                }
                final Pair<Integer, RubyEncoding> undumpAfterBackslashResult = undumpAfterBackslash(
                        undumped,
                        resultEncoding,
                        bytes,
                        start,
                        end,
                        enc,
                        utf8,
                        binary,
                        context,
                        currentNode);
                start = undumpAfterBackslashResult.getLeft();
                resultEncoding = undumpAfterBackslashResult.getRight();
            } else {
                undumped.append(bytes, start++, 1);
            }
        }

        return Pair.create(undumped, resultEncoding);
    }

    private static Pair<Integer, RubyEncoding> undumpAfterBackslash(TStringBuilder out, RubyEncoding encoding,
            byte[] bytes, int start, int end, Encoding[] enc,
            boolean[] utf8, boolean[] binary, RubyContext context, Node currentNode) {
        long c;
        int codelen;
        int[] hexlen = { 0 };
        byte[] buf = new byte[6];
        RubyEncoding resultEncoding = encoding;

        switch (bytes[start]) {
            case '\\':
            case '"':
            case '#':
                out.append(bytes, start, 1); /* cat itself */
                start++;
                break;
            case 'n':
            case 'r':
            case 't':
            case 'f':
            case 'v':
            case 'b':
            case 'a':
            case 'e':
                buf[0] = unescapeAscii(bytes[start]);
                out.append(buf, 0, 1);
                start++;
                break;
            case 'u':
                if (binary[0]) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError(
                                    "hex escape and Unicode escape are mixed",
                                    currentNode));
                }
                utf8[0] = true;
                if (++start >= end) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError("invalid Unicode escape", currentNode));
                }
                if (enc[0] != UTF8Encoding.INSTANCE) {
                    enc[0] = UTF8Encoding.INSTANCE;
                    out.setEncoding(Encodings.UTF_8);
                    resultEncoding = Encodings.UTF_8;
                }
                if (bytes[start] == '{') { /* handle u{...} form */
                    start++;
                    for (;;) {
                        if (start >= end) {
                            throw new RaiseException(
                                    context,
                                    context.getCoreExceptions().runtimeError(
                                            "unterminated Unicode escape",
                                            currentNode));
                        }
                        if (bytes[start] == '}') {
                            start++;
                            break;
                        }
                        if (Character.isSpaceChar(bytes[start])) {
                            start++;
                            continue;
                        }
                        c = scanHex(bytes, start, end - start, hexlen);
                        if (hexlen[0] == 0 || hexlen[0] > 6) {
                            throw new RaiseException(
                                    context,
                                    context.getCoreExceptions().runtimeError("invalid Unicode escape", currentNode));
                        }
                        if (c > 0x10ffff) {
                            throw new RaiseException(
                                    context,
                                    context.getCoreExceptions().runtimeError(
                                            "invalid Unicode codepoint (too large)",
                                            currentNode));
                        }
                        if (0xd800 <= c && c <= 0xdfff) {
                            throw new RaiseException(
                                    context,
                                    context.getCoreExceptions().runtimeError("invalid Unicode codepoint", currentNode));
                        }
                        codelen = enc[0].codeToMbc((int) c, buf, 0);
                        out.append(buf, 0, codelen);
                        start += hexlen[0];
                    }
                } else { /* handle uXXXX form */
                    c = scanHex(bytes, start, 4, hexlen);
                    if (hexlen[0] != 4) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError("invalid Unicode escape", currentNode));
                    }
                    if (0xd800 <= c && c <= 0xdfff) {
                        throw new RaiseException(
                                context,
                                context.getCoreExceptions().runtimeError("invalid Unicode codepoint", currentNode));
                    }
                    codelen = enc[0].codeToMbc((int) c, buf, 0);
                    out.append(buf, 0, codelen);
                    start += hexlen[0];
                }
                break;
            case 'x':
                if (utf8[0]) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError(
                                    "hex escape and Unicode escape are mixed",
                                    currentNode));
                }
                binary[0] = true;
                if (++start >= end) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError("invalid hex escape", currentNode));
                }
                buf[0] = (byte) scanHex(bytes, start, 2, hexlen);
                if (hexlen[0] != 2) {
                    throw new RaiseException(
                            context,
                            context.getCoreExceptions().runtimeError("invalid hex escape", currentNode));
                }
                out.append(buf, 0, 1);
                start += hexlen[0];
                break;
            default:
                out.append(bytes, start - 1, 2);
                start++;
        }

        return Pair.create(start, resultEncoding);
    }

    private static long scanHex(byte[] bytes, int start, int len, int[] retlen) {
        int s = start;
        long retval = 0;
        int tmp;

        while ((len--) > 0 && s < bytes.length &&
                (tmp = ArrayUtils.memchr(HEXDIGIT, 0, HEXDIGIT.length, bytes[s])) != -1) {
            retval <<= 4;
            retval |= tmp & 15;
            s++;
        }
        retlen[0] = (s - start); /* less than len */
        return retval;
    }

    private static byte unescapeAscii(byte c) {
        switch (c) {
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'f':
                return '\f';
            case 'v':
                return '\13';
            case 'b':
                return '\010';
            case 'a':
                return '\007';
            case 'e':
                return 033;
            default:
                // not reached
                return -1;
        }
    }
    // endregion
}
