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
 * Some of the code for parsing timezones in this class is transposed
 * from org.jruby.RubyTime and licensed under the same
 * EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 *
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Joey Gibson <joey@joeygibson.com>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2006 Ola Bini <ola.bini@ki.se>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2009 Joseph LaFata <joe@quibb.org>
 */
package org.truffleruby.core.time;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public abstract class GetTimeZoneNode extends RubyBaseNode {

    public static final ZoneId UTC = ZoneId.of("UTC");

    protected static final CyclicAssumption TZ_UNCHANGED = new CyclicAssumption("ENV['TZ'] is unmodified");

    public static void invalidateTZ() {
        TZ_UNCHANGED.invalidate();
    }

    @Child private DispatchNode lookupEnvNode = DispatchNode.create();

    public abstract TimeZoneAndName executeGetTimeZone();

    @Specialization(assumptions = "TZ_UNCHANGED.getAssumption()")
    TimeZoneAndName getTimeZone(
            @Cached("getTZ()") Object tzValue,
            @Cached("getTimeZone(tzValue)") TimeZoneAndName zone) {
        return zone;
    }

    @NeverDefault
    protected Object getTZ() {
        if (getContext().getOptions().NATIVE_PLATFORM) {
            return lookupEnvNode.call(coreLibrary().getENV(), "[]", FrozenStrings.TZ);
        } else {
            return nil;
        }
    }

    @NeverDefault
    @TruffleBoundary
    protected TimeZoneAndName getTimeZone(Object tz) {
        String tzString = "";
        if (RubyStringLibrary.isRubyStringUncached(tz)) {
            tzString = StringOperations.getJavaString(tz);
        }

        if (tz == nil) {
            // $TZ is not set, use the system timezone
            return new TimeZoneAndName(getContext().getEnv().getTimeZone());
        } else if (RubyStringLibrary.isRubyStringUncached(tz)) {
            return parse(tzString);
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static final Map<String, String> LONG_TZNAME = map(
            "MET",
            "CET", // JRUBY-2759
            "ROC",
            "Asia/Taipei", // Republic of China
            "WET",
            "Europe/Lisbon" // Western European Time
    );

    private static Map<String, String> map(String... keyValues) {
        HashMap<String, String> map = new HashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length;) {
            map.put(keyValues[i++], keyValues[i++]);
        }
        return map;
    }

    private static final Pattern TZ_PATTERN = Pattern.compile("([a-zA-Z]{3,}+)([\\+-]?)(\\d+)(?::(\\d+))?(?::(\\d+))?");

    private TimeZoneAndName parse(String zone) {
        final Matcher tzMatcher = TZ_PATTERN.matcher(zone);
        if (tzMatcher.matches()) {
            String name = tzMatcher.group(1);
            String sign = tzMatcher.group(2);
            String hours = tzMatcher.group(3);
            String minutes = tzMatcher.group(4);
            String seconds = tzMatcher.group(5);

            // Sign is reversed in legacy TZ notation
            return getTimeZoneFromHHMM(name, sign.equals("-"), hours, minutes, seconds);
        } else {
            final String expandedZone = expandZoneName(zone);

            ZoneId zoneID;
            try {
                zoneID = ZoneId.of(expandedZone);
            } catch (DateTimeException | IllegalArgumentException e) {
                zoneID = UTC;
            }
            return new TimeZoneAndName(zoneID);
        }
    }

    private String expandZoneName(String zone) {
        final String upZone = zone.toUpperCase(Locale.ENGLISH);
        if (LONG_TZNAME.containsKey(upZone)) {
            return LONG_TZNAME.get(upZone);
        } else if (upZone.equals("UTC") || upZone.equals("GMT")) {
            // MRI behavior: With TZ equal to "GMT" or "UTC", Time.now
            // is *NOT* considered as a proper GMT/UTC time:
            //   ENV['TZ']="GMT"; Time.now.gmt? #=> false
            //   ENV['TZ']="UTC"; Time.now.utc? #=> false
            // Hence, we need to adjust for that.
            return "Etc/" + upZone;
        }

        return zone;
    }

    private TimeZoneAndName getTimeZoneFromHHMM(String name, boolean positive, String hours, String minutes,
            String seconds) {
        final int h = Integer.parseInt(hours);
        final int m = minutes != null ? Integer.parseInt(minutes) : 0;
        final int s = seconds != null ? Integer.parseInt(seconds) : 0;

        if (h > 23 || m > 59) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("utc_offset out of range", this));
        }

        final int offset = (positive ? +1 : -1) * ((h * 3600) + m * 60 + s);

        final ZoneOffset zoneOffset;
        try {
            zoneOffset = ZoneOffset.ofTotalSeconds(offset);
        } catch (DateTimeException e) {
            throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
        }

        return new TimeZoneAndName(zoneOffset, name);
    }

}
