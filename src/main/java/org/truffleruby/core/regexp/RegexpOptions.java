/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.core.string.KCode;
import org.truffleruby.parser.ReOptions;

public final class RegexpOptions implements Cloneable, Comparable<RegexpOptions> {

    public static final RegexpOptions NULL_OPTIONS = new RegexpOptions(KCode.NONE, true);

    public RegexpOptions() {
        this(KCode.NONE, true);
    }

    private RegexpOptions newWithFlag(int optionMask, boolean set) {
        int newOptions;
        if (set) {
            newOptions = options | optionMask;
        } else {
            newOptions = options & ~optionMask;
        }
        return new RegexpOptions(kcode, newOptions);
    }

    public RegexpOptions(KCode kcode, boolean isKCodeDefault) {
        this(kcode, KCODEDEFAULT);
    }

    public RegexpOptions(KCode kcode, int options) {
        this.kcode = kcode;
        this.options = options;

        assert kcode != null : "kcode must always be set to something";
    }

    public RegexpOptions(
            KCode kcode,
            boolean fixed,
            boolean once,
            boolean extended,
            boolean multiLine,
            boolean ignoreCase,
            boolean encodingNone,
            boolean kCodeDefault,
            boolean literal) {
        this.kcode = kcode;
        int options = 0;
        if (fixed) {
            options |= FIXED;
        }
        if (once) {
            options |= ONCE;
        }
        if (extended) {
            options |= EXTENDED;
        }
        if (multiLine) {
            options |= MULTILINE;
        }
        if (ignoreCase) {
            options |= IGNORECASE;
        }
        if (encodingNone) {
            options |= ENCODINGNONE;
        }
        if (kCodeDefault) {
            options |= KCODEDEFAULT;
        }
        if (literal) {
            options |= LITERAL;
        }
        this.options = options;

        assert kcode != null : "kcode must always be set to something";
    }

    public boolean isExtended() {
        return (options & EXTENDED) != 0;
    }

    public RegexpOptions setExtended(boolean extended) {
        return newWithFlag(EXTENDED, extended);
    }

    public boolean isIgnorecase() {
        return (options & IGNORECASE) != 0;
    }

    public RegexpOptions setIgnorecase(boolean ignorecase) {
        return newWithFlag(IGNORECASE, ignorecase);
    }

    public boolean isFixed() {
        return (options & FIXED) != 0;
    }

    public RegexpOptions setFixed(boolean fixed) {
        return newWithFlag(FIXED, fixed);
    }

    public boolean canAdaptEncoding() {
        return (options & (FIXED | ENCODINGNONE)) == 0;
    }

    public KCode getKCode() {
        return kcode;
    }

    /** This regexp has an explicit encoding flag or 'nesu' letter associated with it.
     * 
     * @param kcode to be set */
    public RegexpOptions setExplicitKCode(KCode kcode) {
        int newOptions = options & ~KCODEDEFAULT;
        return new RegexpOptions(kcode, newOptions);
    }

    private RegexpOptions setKCodeDefault(boolean kcodedefault) {
        return newWithFlag(KCODEDEFAULT, kcodedefault);
    }

    private KCode getExplicitKCode() {
        if (isKcodeDefault()) {
            return null;
        }

        return kcode;
    }

    /** Whether the kcode associated with this regexp is implicit (aka default) or is specified explicitly (via 'nesu'
     * syntax postscript or flags to Regexp.new. */
    public boolean isKcodeDefault() {
        return (options & KCODEDEFAULT) != 0;
    }

    public boolean isMultiline() {
        return (options & MULTILINE) != 0;
    }

    public RegexpOptions setMultiline(boolean multiline) {
        return newWithFlag(MULTILINE, multiline);
    }

    public boolean isOnce() {
        return (options & ONCE) != 0;
    }

    public RegexpOptions setOnce(boolean once) {
        return newWithFlag(ONCE, once);
    }

    public boolean isEncodingNone() {
        return (options & ENCODINGNONE) != 0;
    }

    public RegexpOptions setEncodingNone(boolean encodingNone) {
        return newWithFlag(ENCODINGNONE, encodingNone);
    }

    public boolean isLiteral() {
        return (options & LITERAL) != 0;
    }

    public RegexpOptions setLiteral(boolean literal) {
        return newWithFlag(LITERAL, literal);
    }

    public boolean isEmbeddable() {
        return isMultiline() && isIgnorecase() && isExtended();
    }

    // This relies on mutation. Separate into a setup and getEncoding method.
    public RegexpOptions setup() {
        KCode explicitKCode = getExplicitKCode();

        if (explicitKCode == null) {
            return this;
        } else if (explicitKCode == KCode.NONE) { // None will not set fixed
            return setEncodingNone(true);
        } else {
            return setFixed(true);
        }
    }

    public Encoding getEncoding() {
        KCode explicitKCode = getExplicitKCode();

        if (explicitKCode == null) {
            return null;
        } else if (explicitKCode == KCode.NONE) { // None will not set fixed
            return ASCIIEncoding.INSTANCE;
        } else {
            return explicitKCode.getEncoding();
        }
    }

    /** This int value is meant to only be used when dealing directly with the joni regular expression library. It
     * differs from embeddedOptions in that it only contains bit values which Joni cares about. */
    public int toJoniOptions() {
        int options = 0;
        // Note: once is not an option that is pertinent to Joni so we exclude it.
        if (isMultiline()) {
            options |= ReOptions.RE_OPTION_MULTILINE;
        }
        if (isIgnorecase()) {
            options |= ReOptions.RE_OPTION_IGNORECASE;
        }
        if (isExtended()) {
            options |= ReOptions.RE_OPTION_EXTENDED;
        }
        return options;
    }

    /** This int value is used by Regex#options */
    public int toOptions() {
        int options = 0;
        if (isMultiline()) {
            options |= ReOptions.RE_OPTION_MULTILINE;
        }
        if (isIgnorecase()) {
            options |= ReOptions.RE_OPTION_IGNORECASE;
        }
        if (isExtended()) {
            options |= ReOptions.RE_OPTION_EXTENDED;
        }
        if (isFixed()) {
            options |= ReOptions.RE_FIXED;
        }
        if (isEncodingNone()) {
            options |= ReOptions.RE_NONE;
        }
        return options;
    }

    public static RegexpOptions fromEmbeddedOptions(int embeddedOptions) {
        return fromJoniOptions(embeddedOptions)
                .setKCodeDefault((embeddedOptions & ReOptions.RE_DEFAULT) != 0)
                .setLiteral((embeddedOptions & ReOptions.RE_LITERAL) != 0)
                .setEncodingNone((embeddedOptions & ReOptions.RE_NONE) != 0);
    }

    public static RegexpOptions fromJoniOptions(int joniOptions) {
        return new RegexpOptions()
                .setMultiline((joniOptions & ReOptions.RE_OPTION_MULTILINE) != 0)
                .setIgnorecase((joniOptions & ReOptions.RE_OPTION_IGNORECASE) != 0)
                .setExtended((joniOptions & ReOptions.RE_OPTION_EXTENDED) != 0)
                .setFixed((joniOptions & ReOptions.RE_FIXED) != 0)
                .setOnce((joniOptions & ReOptions.RE_OPTION_ONCE) != 0);
    }

    public RegexpOptions withoutOnce() {
        return setOnce(false);
    }

    public String toOptionsString() {
        StringBuilder flags = new StringBuilder(3);
        if (isMultiline()) {
            flags.append('m');
        }
        if (isIgnorecase()) {
            flags.append('i');
        }
        if (isExtended()) {
            flags.append('x');
        }
        return flags.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + (kcode != null ? kcode.hashCode() : 0);
        hash = 11 * hash + (isFixed() ? 1 : 0);
        hash = 11 * hash + (isOnce() ? 1 : 0);
        hash = 11 * hash + (isExtended() ? 1 : 0);
        hash = 11 * hash + (isMultiline() ? 1 : 0);
        hash = 11 * hash + (isIgnorecase() ? 1 : 0);
        hash = 11 * hash + (isEncodingNone() ? 1 : 0);
        hash = 11 * hash + (isKcodeDefault() ? 1 : 0);
        hash = 11 * hash + (isLiteral() ? 1 : 0);
        return hash;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException cnse) {
            throw new RuntimeException(cnse);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RegexpOptions)) {
            return false;
        }

        RegexpOptions o = (RegexpOptions) other;
        return options == o.options && kcode == o.kcode;
    }

    @Override
    public String toString() {
        return "RegexpOptions(kcode: " + kcode +
                (isEncodingNone() ? ", encodingNone" : "") +
                (isExtended() ? ", extended" : "") +
                (isFixed() ? ", fixed" : "") +
                (isIgnorecase() ? ", ignorecase" : "") +
                (isKcodeDefault() ? ", kcodeDefault" : "") +
                (isLiteral() ? ", literal" : "") +
                (isMultiline() ? ", multiline" : "") +
                (isOnce() ? ", once" : "") +
                ")";
    }

    @Override
    public int compareTo(RegexpOptions o) {
        if (options - o.options != 0) {
            return options - o.options;
        } else {
            return kcode.ordinal() - o.kcode.ordinal();
        }
    }

    private final KCode kcode;
    private final int options;

    private static final int FIXED = 1;
    private static final int ONCE = 1 << 1;
    private static final int EXTENDED = 1 << 2;
    private static final int MULTILINE = 1 << 3;
    private static final int IGNORECASE = 1 << 4;
    private static final int ENCODINGNONE = 1 << 5;
    private static final int KCODEDEFAULT = 1 << 6;
    private static final int LITERAL = 1 << 7;
}
