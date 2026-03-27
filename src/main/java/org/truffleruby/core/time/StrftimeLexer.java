/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.time;

import org.truffleruby.core.time.RubyDateFormatter.Token;

public final class StrftimeLexer {

    private final String input;
    private final int length;
    private int n = 0;

    public StrftimeLexer(String input) {
        this.input = input;
        this.length = input.length();
    }

    public Token directive(char c) {
        Token token;
        if ((token = Token.format(c)) != null) {
            return token;
        } else {
            return Token.special(c);
        }
    }

    public Token formatter(String flags, String widthString) {
        int width = 0;
        if (widthString != null) {
            width = Integer.parseInt(widthString);
        }

        return Token.formatter(new RubyTimeOutputFormatter(flags, width));
    }

    private char current() {
        return input.charAt(n);
    }

    private char peek() {
        if (n + 1 >= length) {
            return '\0';
        } else {
            return input.charAt(n + 1);
        }
    }

    private boolean consume(char c) {
        if (current() == c) {
            n++;
            return true;
        } else {
            return false;
        }
    }

    private Token next = null;

    public Token yylex() {
        final Token nextToken = next;
        if (nextToken != null) {
            next = null;
            return nextToken;
        }

        if (n >= length) {
            return null;
        }

        if (consume('%')) {
            Token token;
            if ((token = parseLiteralPercent()) != null) {
                return token;
            } else if ((token = parseSimpleDirective()) != null) {
                return token;
            } else if ((token = parseComplexDirective()) != null) {
                return token;
            } else {
                // Invalid modifier/directive, interpret literally
                return Token.str("%");
            }
        } else {
            return parseUnknown();
        }
    }

    private Token parseLiteralPercent() {
        if (consume('%')) {
            return Token.str("%");
        }
        return null;
    }

    private Token parseSimpleDirective() {
        return parseConversion();
    }

    private Token parseComplexDirective() {
        int from = n;
        String flags;
        String width;
        Token directive;

        if ((flags = parseFlags()) != null) {
            width = parseWidth();
            if ((directive = parseConversion()) != null) {
                next = directive;
                return formatter(flags, width);
            }
        } else if ((width = parseWidth()) != null) {
            if ((directive = parseConversion()) != null) {
                next = directive;
                return formatter("", width);
            }
        }

        n = from;
        return null;
    }

    private String parseFlags() {
        int from = n;
        if (n < length && parseFlag()) {
            n++;
            while (n < length && parseFlag()) {
                n++;
            }
            return input.substring(from, n);
        } else {
            return null;
        }
    }

    private boolean parseFlag() {
        switch (current()) {
            case '-':
            case '_':
            case '0':
            case '#':
            case '^':
                return true;

            default:
                return false;
        }
    }

    private String parseWidth() {
        int from = n;
        if ('1' <= current() && current() <= '9') {
            n++;
            while (n < length && '0' <= current() && current() <= '9') {
                n++;
            }
            return input.substring(from, n);
        } else {
            return null;
        }
    }

    private Token parseUnknown() {
        final int from = n;
        while (n < length && current() != '%') {
            n++;
        }
        return Token.str(input.substring(from, n));
    }

    private Token parseConversion() {
        char c = current();

        // Directive [+AaBbCcDdeFGgHhIjkLlMmNnPpQRrSsTtUuVvWwXxYyZ]
        switch (c) {
            case '+':
            case 'A':
            case 'a':
            case 'B':
            case 'b':
            case 'C':
            case 'c':
            case 'D':
            case 'd':
            case 'e':
            case 'F':
            case 'G':
            case 'g':
            case 'H':
            case 'h':
            case 'I':
            case 'j':
            case 'k':
            case 'L':
            case 'l':
            case 'M':
            case 'm':
            case 'N':
            case 'n':
            case 'P':
            case 'p':
            case 'Q':
            case 'R':
            case 'r':
            case 'S':
            case 's':
            case 'T':
            case 't':
            case 'U':
            case 'u':
            case 'V':
            case 'v':
            case 'W':
            case 'w':
            case 'X':
            case 'x':
            case 'Y':
            case 'y':
            case 'Z':
                n++;
                return directive(c);

            // Ignored modifiers, from MRI strftime.c
            case 'E':
                final char afterE = peek();
                switch (afterE) {
                    case 'C':
                    case 'c':
                    case 'X':
                    case 'x':
                    case 'Y':
                    case 'y':
                        n += 2;
                        return directive(afterE);
                    default:
                        return null;
                }
            case 'O':
                final char afterO = peek();
                switch (afterO) {
                    case 'd':
                    case 'e':
                    case 'H':
                    case 'k':
                    case 'I':
                    case 'l':
                    case 'M':
                    case 'm':
                    case 'S':
                    case 'U':
                    case 'u':
                    case 'V':
                    case 'W':
                    case 'w':
                    case 'y':
                        n += 2;
                        return directive(afterO);
                    default:
                        return null;
                }

                // Zone
            case 'z':
                n++;
                return Token.zoneOffsetColons(0);
            case ':':
                int from = n;
                n++;
                if (consume(':')) {
                    if (consume(':')) {
                        if (consume('z')) {
                            return Token.zoneOffsetColons(3);
                        } else {
                            n = from;
                            return null;
                        }
                    } else if (consume('z')) {
                        return Token.zoneOffsetColons(2);
                    } else {
                        n = from;
                        return null;
                    }
                } else if (consume('z')) {
                    return Token.zoneOffsetColons(1);
                } else {
                    n = from;
                    return null;
                }

            default:
                return null;
        }
    }

}
