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
package org.truffleruby.core.format.rbsprintf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.rbsprintf.RBSprintfConfig.FormatArgumentType;

public final class RBSprintfSimpleParser {

    private final char[] source;

    public RBSprintfSimpleParser(char[] source) {
        this.source = source;
    }

    public List<RBSprintfConfig> parse() {
        List<RBSprintfConfig> configs = new ArrayList<>();
        ArgType argType = ArgType.NONE;

        final int end = source.length;

        for (int i = 0; i < end;) {

            // Add literal bytes up to the first %
            int literalEnd = i;
            while (literalEnd < end && source[literalEnd] != '%') {
                literalEnd++;
            }
            final int literalLength = literalEnd - i;
            if (literalLength > 0) {
                RBSprintfConfig config = new RBSprintfConfig();
                config.setLiteral(true);
                final char[] literalBytes = new char[literalLength];
                System.arraycopy(source, i, literalBytes, 0, literalLength);
                config.setLiteralBytes(charsToBytes(literalBytes));
                configs.add(config);
            }
            if (literalEnd >= end) {
                break; // format string ends with a literal
            }

            i = literalEnd + 1; // skip first %

            RBSprintfConfig config = new RBSprintfConfig();
            configs.add(config);

            boolean finished = false;
            boolean argTypeSet = false;

            while (!finished) {
                char p = i >= this.source.length ? '\0' : this.source[i];

                switch (p) {
                    case ' ':
                        config.checkForFlags();
                        config.setHasSpace(true);
                        i++;
                        break;
                    case '#':
                        config.checkForFlags();
                        config.setFsharp(true);
                        i++;
                        break;
                    case '+':
                        config.checkForFlags();
                        config.setPlus(true);
                        i++;
                        break;
                    case '-':
                        config.checkForFlags();
                        config.setMinus(true);
                        i++;
                        break;
                    case '\'':
                        config.checkForFlags();
                        config.setSeparator(true);
                        i++;
                        break;
                    case '0':
                        config.checkForFlags();
                        config.setZero(true);
                        i++;
                        break;
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        LookAheadResult r = getNum(i, end);
                        i = r.getNextI();
                        if (valueAt(i) != null && valueAt(i) == '$') {
                            if (config.getAbsoluteArgumentIndex() != null) {
                                throw new InvalidFormatException("value given twice - " + r.getNumber() + "$");
                            }
                            checkPosArg(argType, r.getNumber());
                            argType = ArgType.NUMBERED;
                            argTypeSet = true;
                            config.setAbsoluteArgumentIndex(r.getNumber());
                            i++;
                            break;
                        }

                        config.checkForWidth();
                        config.setWidth(r.getNumber());
                        break;
                    case '*':
                        config.checkForWidth();
                        if (config.hasFormatArgumentType()) {
                            throw new InvalidFormatException("width given after length or type");
                        }

                        LookAheadResult numberDollarWidth = getNumberDollar(i + 1, end);
                        if (numberDollarWidth.getNumber() != null) {
                            config.setArgWidth(true);
                            config.setWidth(numberDollarWidth.getNumber());
                            checkPosArg(argType, numberDollarWidth.getNumber());
                            argType = ArgType.NUMBERED;
                            i = numberDollarWidth.getNextI();
                        } else {
                            checkNextArg(argType, 1); // TODO index next args
                            argType = ArgType.UNNUMBERED;
                            config.setWidthStar(true);
                            i++;
                        }
                        break;
                    case '.':
                        if (config.hasFormatArgumentType()) {
                            throw new InvalidFormatException("precision given after length or type");
                        }
                        if (config.hasPrecision()) {
                            throw new InvalidFormatException("precision given twice");
                        }
                        config.setPrecisionVisited(true);
                        if (valueAt(i + 1) != null && valueAt(i + 1) == '*') {
                            LookAheadResult numberDollar = getNumberDollar(i + 2, end);
                            if (numberDollar.getNumber() != null) {
                                config.setPrecision(numberDollar.getNumber());
                                config.setPrecisionArg(true);
                                checkPosArg(argType, numberDollar.getNumber());
                                argType = ArgType.NUMBERED;
                                i = numberDollar.getNextI();
                            } else {
                                checkNextArg(argType, 1); // TODO idx
                                argType = ArgType.UNNUMBERED;
                                config.setPrecisionStar(true);
                                i += 2;
                            }
                            break;
                        }

                        LookAheadResult re = getNum(i + 1, end);
                        config.setPrecision(re.getNumber());
                        i = re.getNextI();
                        break;
                    case 'h': {
                        config.checkForFormatArgumentType();
                        int j = i + 1;
                        char next = j >= this.source.length ? '\0' : this.source[j];
                        if (next == 'h') {
                            config.setFormatArgumentType(FormatArgumentType.CHAR);
                            i++;
                        } else {
                            config.setFormatArgumentType(FormatArgumentType.SHORT);
                        }
                        i++;
                        break;
                    }
                    case 'l': {
                        config.checkForFormatArgumentType();
                        int j = i + 1;
                        char next = j >= this.source.length ? '\0' : this.source[j];
                        if (next == 'l') {
                            config.setFormatArgumentType(FormatArgumentType.LONGLONG);
                            i++;
                        } else {
                            config.setFormatArgumentType(FormatArgumentType.LONG);
                        }
                        i++;
                        break;
                    }
                    case 'L':
                        config.checkForFormatArgumentType();
                        config.setFormatArgumentType(FormatArgumentType.LONGDOUBLE);
                        i++;
                        break;
                    case 'z':
                        config.checkForFormatArgumentType();
                        config.setFormatArgumentType(FormatArgumentType.SIZE_T);
                        i++;
                        break;
                    case 'j':
                        config.checkForFormatArgumentType();
                        config.setFormatArgumentType(FormatArgumentType.INTMAX_T);
                        i++;
                        break;
                    case 't':
                        config.checkForFormatArgumentType();
                        config.setFormatArgumentType(FormatArgumentType.PTRDIFF_T);
                        i++;
                        break;
                    case '%':
                        if (config.hasFlags()) {
                            throw new InvalidFormatException("invalid format character - %");
                        }
                        config.setLiteral(true);
                        config.setLiteralBytes(new byte[]{ (byte) '%' });
                        if (p == '%') {
                            i++;
                        }
                        finished = true;
                        break;
                    case 'c':
                    case 's':
                        config.checkForFormatArgumentType();
                        if (p == 'c') {
                            config.setFormatArgumentType(FormatArgumentType.CHAR);
                        } else {
                            config.setFormatArgumentType(FormatArgumentType.STRING);
                        }
                        config.setFormatType(RBSprintfConfig.FormatType.OTHER);
                        config.setFormat(p);
                        i++;
                        if (!argTypeSet) { // Speculative
                            checkNextArg(argType, 1);
                            argType = ArgType.UNNUMBERED;
                        }
                        finished = true;
                        break;
                    case 'p':
                        config.checkForFormatArgumentType();
                        config.setFormatArgumentType(FormatArgumentType.POINTER);
                        config.setFormatType(RBSprintfConfig.FormatType.POINTER);
                        config.setFormat(p);
                        i++;
                        if (!argTypeSet) { // Speculative
                            checkNextArg(argType, 1);
                            argType = ArgType.UNNUMBERED;
                        }
                        finished = true;
                        break;
                    case 'i':
                        // This is a ruby value if it's followed by a
                        // marker and has the right width, otherwise
                        // we'll treat it as a normal integer case.
                        if (i + 1 < this.source.length && this.source[i + 1] == '\u000B' &&
                                config.getFormatArgumentType() == FormatArgumentType.LONG) {
                            config.setFormatType(RBSprintfConfig.FormatType.RUBY_VALUE);
                            config.setFormatArgumentType(RBSprintfConfig.FormatArgumentType.VALUE);
                            i += 2;
                        } else {
                            config.setFormatType(RBSprintfConfig.FormatType.INTEGER);
                            i++;
                        }
                        if (!config.hasFormatArgumentType()) {
                            config.setFormatArgumentType(FormatArgumentType.INT);
                        }
                        config.setFormat(p);
                        if (!argTypeSet) { // Speculative
                            checkNextArg(argType, 1);
                            argType = ArgType.UNNUMBERED;
                        }
                        finished = true;
                        break;
                    case 'd':
                    case 'o':
                    case 'x':
                    case 'X':
                    case 'u':
                        if (!config.hasFormatArgumentType()) {
                            config.setFormatArgumentType(FormatArgumentType.INT);
                        }
                        if (!argTypeSet) {
                            checkNextArg(argType, 1); // TODO idx correctly
                            argType = ArgType.UNNUMBERED;
                        }
                        config.setFormatType(RBSprintfConfig.FormatType.INTEGER);
                        config.setFormat(p);
                        finished = true;
                        i++;
                        break;
                    case 'g':
                    case 'G':
                    case 'e':
                    case 'E':
                    case 'a':
                    case 'A':
                    case 'f':
                    case 'F':
                        if (!config.hasFormatArgumentType()) {
                            config.setFormatArgumentType(FormatArgumentType.DOUBLE);
                        }
                        if (!argTypeSet) {
                            checkNextArg(argType, 1);
                            argType = ArgType.UNNUMBERED;
                        }
                        config.setFormatType(RBSprintfConfig.FormatType.FLOAT);
                        config.setFormat(p);
                        finished = true;
                        i++;
                        break;
                    default:
                        throw new InvalidFormatException("malformed format string - %" + p);
                }
            }
        }

        return normalizeArgumentTypes(configs);
    }

    private static void checkNextArg(ArgType argType, int nextArgumentIndex) {
        switch (argType) {
            case NUMBERED:
                throw new InvalidFormatException("unnumbered(" + nextArgumentIndex + ") mixed with numbered");
            case NAMED:
                throw new InvalidFormatException("unnumbered(" + nextArgumentIndex + ") mixed with named");
        }
    }

    private static void checkPosArg(ArgType posarg, int nextArgumentIndex) {
        if (posarg == ArgType.UNNUMBERED) {
            throw new InvalidFormatException("numbered(" + nextArgumentIndex + ") after unnumbered(" + posarg + ")");
        }
        if (posarg == ArgType.NAMED) {
            throw new InvalidFormatException("numbered(" + nextArgumentIndex + ") after named");
        }
        if (nextArgumentIndex < 1) {
            throw new InvalidFormatException("invalid index - " + nextArgumentIndex + "$");
        }
    }

    private enum ArgType {
        NONE,
        NUMBERED,
        UNNUMBERED,
        NAMED
    }

    public LookAheadResult getNum(int startI, int end) {
        StringBuilder sb = new StringBuilder();

        int moreChars = 0;
        for (int i = startI; i < end; i++) {
            char nextChar = source[i];
            if (!isDigit(nextChar)) {
                break;
            } else {
                sb.append(nextChar);
                moreChars += 1;
            }
        }

        final int nextI = startI + moreChars;

        if (nextI >= end) {
            throw new InvalidFormatException("malformed format string - %%*[0-9]");
        }

        Integer result;
        if (sb.length() > 0) {
            try {
                result = Integer.parseInt(sb.toString());
            } catch (NumberFormatException nfe) {
                throw new InvalidFormatException("precision too big");
            }
        } else {
            result = null;
        }
        return new LookAheadResult(result, nextI);
    }

    public LookAheadResult getNumberDollar(int startI, int end) {
        LookAheadResult lar = getNum(startI, end);
        Integer result = null;
        int newI = startI;
        if (lar.getNumber() != null) {
            final int nextI = lar.getNextI();
            if (valueAt(nextI) != null && valueAt(nextI) == '$') {
                result = lar.getNumber();
                newI = nextI + 1;
                if (result < 1) {
                    throw new InvalidFormatException("invalid index - " + result + "$");
                }
            }
        }
        return new LookAheadResult(result, newI);
    }

    public static final class LookAheadResult {
        private Integer number;
        private int nextI;

        public LookAheadResult(Integer number, int nextI) {
            this.number = number;
            this.nextI = nextI;
        }

        public Integer getNumber() {
            return number;
        }

        public int getNextI() {
            return nextI;
        }
    }

    public static boolean isDigit(char c) {
        return c == '0' || c == '1' || c == '2' || c == '3' || c == '4' || c == '5' || c == '6' || c == '7' ||
                c == '8' || c == '9';
    }

    public Character valueAt(int index) {
        assert index >= 0;
        if (index < this.source.length) {
            return this.source[index];
        } else {
            return null;
        }
    }

    private static byte[] charsToBytes(char[] chars) {
        final byte[] bytes = new byte[chars.length];

        for (int n = 0; n < chars.length; n++) {
            bytes[n] = (byte) chars[n];
        }

        return bytes;
    }

    private List<RBSprintfConfig> normalizeArgumentTypes(List<RBSprintfConfig> configs) {
        // We want to check for any uses of RUBY_VALUEs conflicting with uses of the raw numerical representation.
        RBSprintfConfig[] inPosition = new RBSprintfConfig[configs.size()];
        HashSet<RBSprintfConfig> conflicts = new HashSet<>();
        int pos = 0;
        for (var config : configs) {
            int typePos;
            if (config.getAbsoluteArgumentIndex() != null) {
                typePos = config.getAbsoluteArgumentIndex() - 1; // Parameters are 1 indexed, but our array is 0 indexed.
            } else {
                typePos = pos++;
            }
            if (inPosition[typePos] == null) {
                inPosition[typePos] = config;
            } else {
                if (config.getFormatArgumentType() == FormatArgumentType.VALUE) {
                    inPosition[typePos] = config;
                }
                conflicts.add(config);
                conflicts.add(inPosition[typePos]);
            }
        }
        // If we found any conflicts then change them
        // This can only happen if all the configs have absolute argument positions.
        if (conflicts.size() > 0) {
            for (var config : inPosition) {
                if (config.getFormatArgumentType() == FormatArgumentType.VALUE &&
                        conflicts.contains(config)) {
                    boolean typeConflict = false;
                    ArrayList<RBSprintfConfig> toFix = new ArrayList<>();
                    for (var conflict : conflicts) {
                        if (Objects.equals(conflict.getAbsoluteArgumentIndex(), config.getAbsoluteArgumentIndex())) {
                            toFix.add(conflict);
                            typeConflict |= conflict.getFormatArgumentType() != config.getFormatArgumentType();
                        }
                    }
                    if (typeConflict) {
                        for (var fixConfig : toFix) {
                            fixConfig.setFormatArgumentType(FormatArgumentType.LONG);
                        }
                    }
                }
            }
            return configs;
        } else {
            return configs;
        }
    }
}
