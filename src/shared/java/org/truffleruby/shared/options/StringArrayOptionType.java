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
package org.truffleruby.shared.options;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.options.OptionType;

public class StringArrayOptionType {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    public static final OptionType<String[]> INSTANCE = new OptionType<>(
            "String[]",
            StringArrayOptionType::parseStringArray);

    private enum State {
        STRING_OF_STRING,
        WITHIN_STRING,
        ESCAPE
    }

    private static String[] parseStringArray(String string) {
        final List<String> values = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();
        State state = State.STRING_OF_STRING;

        for (int n = 0; n < string.length(); n++) {
            if (state == State.STRING_OF_STRING) {
                builder.setLength(0);
                state = State.WITHIN_STRING;
            }

            switch (state) {
                case WITHIN_STRING:
                    switch (string.charAt(n)) {
                        case ',':
                            values.add(builder.toString());
                            state = State.STRING_OF_STRING;
                            break;
                        case '\\':
                            state = State.ESCAPE;
                            break;
                        default:
                            builder.append(string.charAt(n));
                            break;
                    }
                    break;

                case ESCAPE:
                    state = State.WITHIN_STRING;
                    if (string.charAt(n) == ',') {
                        builder.append(string.charAt(n));
                    } else {
                        builder.append('\\');
                        builder.append(string.charAt(n));
                    }
                    break;
            }
        }

        switch (state) {
            case WITHIN_STRING:
                values.add(builder.toString());
                break;
            case ESCAPE:
                throw new IllegalArgumentException();
        }

        return values.toArray(EMPTY_STRING_ARRAY);
    }

    public static String append(String currentValues, String newElement) {
        final String escaped = escape(newElement);

        if (currentValues.isEmpty()) {
            return escaped;
        } else {
            return currentValues + ',' + escaped;
        }
    }

    public static String escape(String string) {
        return string.replaceAll(",", "\\\\,");
    }

}
