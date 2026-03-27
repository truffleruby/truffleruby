/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.time;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyDynamicObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@ExportLibrary(InteropLibrary.class)
public final class RubyTime extends RubyDynamicObject {

    ZonedDateTime dateTime;
    Object offset;
    Object zone;
    boolean relativeOffset;
    boolean isUtc;

    public RubyTime(
            RubyClass rubyClass,
            Shape shape,
            ZonedDateTime dateTime,
            Object zone,
            Object offset,
            boolean relativeOffset,
            boolean isUtc) {
        super(rubyClass, shape);
        assert zone instanceof RubyString || zone == Nil.INSTANCE;
        this.dateTime = dateTime;
        this.offset = offset;
        this.zone = zone;
        this.relativeOffset = relativeOffset;
        this.isUtc = isUtc;
    }

    // region Date and Time messages
    @TruffleBoundary
    @ExportMessage
    public Instant asInstant() {
        return dateTime.toInstant();
    }

    @ExportMessage
    public boolean isDate() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    public LocalDate asDate() {
        return dateTime.toLocalDate();
    }

    @ExportMessage
    public boolean isTime() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    public LocalTime asTime() {
        return dateTime.toLocalTime();
    }

    @ExportMessage
    public boolean isTimeZone() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    public ZoneId asTimeZone() {
        return dateTime.getZone();
    }
    // endregion

}
