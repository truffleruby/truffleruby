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
package org.truffleruby.core.time;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class TimeZoneAndName {

    private static final DateTimeFormatter SHORT_ZONE_NAME_FORMATTER = new DateTimeFormatterBuilder()
            .appendZoneText(TextStyle.SHORT)
            .toFormatter(Locale.ENGLISH);

    private final ZoneId zone;

    /** {@code name} is non-null if and only if the timezone is just a fixed offset (a ZoneOffset). In that case, the
     * timezone name is captured from $TZ such as "PST" when TZ=PST8:00:00.
     *
     * Otherwise (e.g., TZ=Europe/Vienna or TZ is unset), the timezone name is computed later in getName(), as the
     * timezone name depends on the date (e.g. CET vs CEST). */
    private final String name;

    public TimeZoneAndName(ZoneId zone) {
        assert !(zone instanceof ZoneOffset);
        this.zone = zone;
        this.name = null;
    }

    public TimeZoneAndName(ZoneOffset zone, String name) {
        assert name != null;
        this.zone = zone;
        this.name = name;
    }

    public ZoneId getZone() {
        return zone;
    }

    @TruffleBoundary
    public String getName(ZonedDateTime dateTime) {
        if (name != null) {
            return name;
        } else {
            return dateTime.format(SHORT_ZONE_NAME_FORMATTER);
        }
    }
}
