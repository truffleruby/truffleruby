/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.time;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.ErrnoErrorNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.TStringBuilder;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.time.RubyDateFormatter.Token;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.IsFrozenNode;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@CoreModule(value = "Time", isClass = true)
public abstract class TimeNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        private static final ZonedDateTime ZERO = ZonedDateTime.ofInstant(Instant.EPOCH, GetTimeZoneNode.UTC);

        @Specialization
        RubyTime allocate(RubyClass rubyClass) {
            final RubyTime instance = new RubyTime(rubyClass, getLanguage().timeShape, ZERO, nil, 0, false, false);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyTime initializeCopy(RubyTime self, RubyTime from) {
            self.dateTime = from.dateTime;
            self.offset = from.offset;
            self.zone = from.zone;
            self.relativeOffset = from.relativeOffset;
            self.isUtc = from.isUtc;
            return self;
        }
    }

    @Primitive(name = "time_localtime")
    public abstract static class LocalTimeNode extends PrimitiveArrayArgumentsNode {

        @Child private GetTimeZoneNode getTimeZoneNode = GetTimeZoneNodeGen.create();

        @Specialization
        RubyTime localtime(RubyTime time, Nil offset,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final TimeZoneAndName timeZoneAndName = getTimeZoneNode.executeGetTimeZone();
            final ZonedDateTime newDateTime = withZone(time.dateTime, timeZoneAndName.getZone());
            final String shortZoneName = timeZoneAndName.getName(newDateTime);
            final RubyString zone = createString(fromJavaStringNode, shortZoneName, Encodings.UTF_8);

            time.isUtc = false;
            time.relativeOffset = false;
            time.zone = zone;
            time.dateTime = newDateTime;

            return time;
        }

        @Specialization
        RubyTime localtime(RubyTime time, long offset) {
            final ZoneId zone = getDateTimeZone((int) offset);
            final ZonedDateTime dateTime = withZone(time.dateTime, zone);

            time.isUtc = false;
            time.relativeOffset = true;
            time.zone = nil;
            time.dateTime = dateTime;

            return time;
        }

        @TruffleBoundary
        public ZoneId getDateTimeZone(int offset) {
            try {
                return ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(offset));
            } catch (DateTimeException e) {
                throw new RaiseException(
                        getContext(),
                        getContext().getCoreExceptions().argumentError(e.getMessage(), this));
            }
        }

        @TruffleBoundary
        private ZonedDateTime withZone(ZonedDateTime dateTime, ZoneId zone) {
            return dateTime.withZoneSameInstant(zone);
        }

    }

    @Primitive(name = "time_utctime")
    public abstract static class UtcTimeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyTime utc(RubyTime time) {
            time.isUtc = true;
            time.relativeOffset = false;
            time.zone = coreStrings().UTC.createInstance(getContext());
            time.dateTime = inUTC(time.dateTime);

            return time;
        }

        @TruffleBoundary
        private ZonedDateTime inUTC(ZonedDateTime dateTime) {
            return dateTime.withZoneSameInstant(GetTimeZoneNode.UTC);
        }
    }


    @Primitive(name = "time_add")
    public abstract static class TimeAddNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyTime add(RubyTime time, long seconds, long nanoSeconds) {
            final ZonedDateTime dateTime = time.dateTime;
            time.dateTime = dateTime.plusSeconds(seconds).plusNanos(nanoSeconds);
            return time;
        }
    }

    @CoreMethod(names = { "gmtime", "utc" })
    public abstract static class GmTimeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyTime gmtime(RubyTime time,
                @Cached InlinedBranchProfile errorProfile,
                @Cached InlinedBranchProfile notModifiedProfile,
                @Cached IsFrozenNode isFrozenNode) {

            if (time.isUtc) {
                notModifiedProfile.enter(this);
                return time;
            }

            if (isFrozenNode.execute(time)) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().frozenError(time, this));
            }

            final ZonedDateTime dateTime = time.dateTime;

            time.isUtc = true;
            time.relativeOffset = false;
            time.zone = coreStrings().UTC.createInstance(getContext());
            time.dateTime = inUTC(dateTime);

            return time;
        }

        @TruffleBoundary
        private ZonedDateTime inUTC(ZonedDateTime dateTime) {
            return dateTime.withZoneSameInstant(GetTimeZoneNode.UTC);
        }

    }

    @Primitive(name = "time_now")
    public abstract static class TimeNowNode extends PrimitiveArrayArgumentsNode {

        @Child private GetTimeZoneNode getTimeZoneNode = GetTimeZoneNodeGen.create();
        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @Specialization
        RubyTime timeNow(RubyClass timeClass) {
            final TimeZoneAndName zoneAndName = getTimeZoneNode.executeGetTimeZone();
            final ZonedDateTime dt = now(zoneAndName.getZone());
            final String shortZoneName = zoneAndName.getName(dt);
            final RubyString zone = createString(fromJavaStringNode, shortZoneName, Encodings.UTF_8);
            final RubyTime instance = new RubyTime(timeClass, getLanguage().timeShape, dt, zone, nil, false, false);
            AllocationTracing.trace(instance, this);
            return instance;

        }

        @TruffleBoundary
        private ZonedDateTime now(ZoneId timeZone) {
            return ZonedDateTime.now(timeZone);
        }

    }

    @Primitive(name = "time_at", lowerFixnum = 2)
    public abstract static class TimeAtPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private GetTimeZoneNode getTimeZoneNode = GetTimeZoneNodeGen.create();
        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @Specialization
        RubyTime timeAt(RubyClass timeClass, long seconds, int nanoseconds) {
            final TimeZoneAndName zoneAndName = getTimeZoneNode.executeGetTimeZone();
            final ZonedDateTime dateTime = getDateTime(seconds, nanoseconds, zoneAndName.getZone());
            final String shortZoneName = zoneAndName.getName(dateTime);
            final RubyString zone = createString(fromJavaStringNode, shortZoneName, Encodings.UTF_8);

            final Shape shape = getLanguage().timeShape;
            final RubyTime instance = new RubyTime(timeClass, shape, dateTime, zone, nil, false, false);
            AllocationTracing.trace(instance, this);
            return instance;
        }

        @Specialization
        RubyTime timeAt(RubyClass timeClass, RubyBignum seconds, int nanoseconds) {
            throw outOfRange(seconds);
        }

        @TruffleBoundary
        private ZonedDateTime getDateTime(long seconds, int nanoseconds, ZoneId timeZone) {
            try {
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanoseconds), timeZone);
            } catch (DateTimeException e) {
                throw outOfRange(seconds);
            }
        }

        @TruffleBoundary
        private RaiseException outOfRange(Object seconds) {
            String message = StringUtils
                    .format("UNIX epoch + %s seconds out of range for Time (java.time limitation)", seconds);
            return new RaiseException(getContext(), coreExceptions().rangeError(message, this));
        }
    }

    @CoreMethod(names = { "to_i", "tv_sec" })
    public abstract static class TimeSecondsSinceEpochNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        long timeSeconds(RubyTime time) {
            return time.dateTime.toInstant().getEpochSecond();
        }
    }

    @CoreMethod(names = { "usec", "tv_usec" })
    public abstract static class TimeMicroSecondsNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeUSec(RubyTime time) {
            return time.dateTime.getNano() / 1000;
        }
    }

    @CoreMethod(names = { "nsec", "tv_nsec" })
    public abstract static class TimeNanoSecondsNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeNSec(RubyTime time) {
            return time.dateTime.getNano();
        }
    }

    @Primitive(name = "time_set_nseconds", lowerFixnum = 1)
    public abstract static class TimeSetNSecondsPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        long timeSetNSeconds(RubyTime time, int nanoseconds) {
            final ZonedDateTime dateTime = time.dateTime;
            time.dateTime = dateTime.plusNanos(nanoseconds - dateTime.getNano());
            return nanoseconds;
        }
    }

    @CoreMethod(names = { "utc_offset", "gmt_offset", "gmtoff" })
    public abstract static class TimeUTCOffsetNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeUTCOffset(RubyTime time) {
            return time.dateTime.getOffset().getTotalSeconds();
        }
    }

    @CoreMethod(names = "sec")
    public abstract static class TimeSecNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeSec(RubyTime time) {
            return time.dateTime.getSecond();
        }
    }

    @CoreMethod(names = "min")
    public abstract static class TimeMinNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeMin(RubyTime time) {
            return time.dateTime.getMinute();
        }
    }

    @CoreMethod(names = "hour")
    public abstract static class TimeHourNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeHour(RubyTime time) {
            return time.dateTime.getHour();
        }
    }

    @CoreMethod(names = { "day", "mday" })
    public abstract static class TimeDayNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeDay(RubyTime time) {
            return time.dateTime.getDayOfMonth();
        }
    }

    @CoreMethod(names = { "mon", "month" })
    public abstract static class TimeMonthNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeMonth(RubyTime time) {
            return time.dateTime.getMonthValue();
        }
    }

    @CoreMethod(names = "year")
    public abstract static class TimeYearNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        int timeYear(RubyTime time) {
            return time.dateTime.getYear();
        }
    }

    @CoreMethod(names = "wday")
    public abstract static class TimeWeekDayNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int timeWeekDay(RubyTime time) {
            int wday = time.dateTime.getDayOfWeek().getValue();
            if (wday == 7) {
                wday = 0;
            }
            return wday;
        }
    }

    @CoreMethod(names = "yday")
    public abstract static class TimeYearDayNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int timeYeayDay(RubyTime time) {
            return time.dateTime.getDayOfYear();
        }
    }

    @CoreMethod(names = { "dst?", "isdst" })
    public abstract static class TimeIsDSTNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        boolean timeIsDST(RubyTime time) {
            final ZonedDateTime dateTime = time.dateTime;
            return dateTime.getZone().getRules().isDaylightSavings(dateTime.toInstant());
        }
    }

    @CoreMethod(names = { "utc?", "gmt?" })
    public abstract static class IsUTCNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isUTC(RubyTime time) {
            return time.isUtc;
        }
    }

    @Primitive(name = "time_zone")
    public abstract static class TimeZoneNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object timeZone(RubyTime time) {
            return time.zone;
        }
    }

    @Primitive(name = "time_set_zone")
    public abstract static class TimeSetZoneNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object timeSetZone(RubyTime time, Object zone) {
            time.zone = zone;
            return zone;
        }

    }

    @Primitive(name = "time_strftime")
    @ReportPolymorphism // inline cache
    @ImportStatic(StringOperations.class)
    public abstract static class TimeStrftimePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = "equalNode.execute(node, libFormat, format, cachedFormat, cachedEncoding)",
                limit = "getLanguage().options.TIME_FORMAT_CACHE")
        static RubyString timeStrftimeCached(RubyTime time, Object format,
                @Cached @Exclusive RubyStringLibrary libFormat,
                @Cached("asTruffleStringUncached(format)") TruffleString cachedFormat,
                @Cached("libFormat.getEncoding($node, format)") RubyEncoding cachedEncoding,
                @Cached(value = "compilePattern(cachedFormat, cachedEncoding)", dimensions = 1) Token[] pattern,
                @Cached StringHelperNodes.EqualSameEncodingNode equalNode,
                @Cached("formatCanBeFast(pattern)") boolean canUseFast,
                @Cached InlinedConditionProfile yearIsFastProfile,
                @Cached @Shared TruffleString.ConcatNode concatNode,
                @Cached @Shared TruffleString.FromLongNode fromLongNode,
                @Cached @Shared TruffleString.CodePointLengthNode codePointLengthNode,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared ErrnoErrorNode errnoErrorNode,
                @Bind Node node) {
            if (canUseFast && yearIsFastProfile.profile(node, yearIsFast(time))) {
                var tstring = RubyDateFormatter.formatToTStringFast(pattern, time.dateTime, concatNode, fromLongNode,
                        codePointLengthNode);
                return createString(node, tstring, Encodings.UTF_8);
            } else {
                final TStringBuilder tstringBuilder = formatTime(node, time, pattern, errnoErrorNode);
                return createString(node, tstringBuilder.toTStringUnsafe(fromByteArrayNode), cachedEncoding);
            }
        }

        @TruffleBoundary
        @Specialization(replaces = "timeStrftimeCached")
        RubyString timeStrftime(RubyTime time, Object format,
                @Cached @Exclusive RubyStringLibrary libFormat,
                @Cached @Shared TruffleString.ConcatNode concatNode,
                @Cached @Shared TruffleString.FromLongNode fromLongNode,
                @Cached @Shared TruffleString.CodePointLengthNode codePointLengthNode,
                @Cached @Shared TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached @Shared ErrnoErrorNode errnoErrorNode) {
            final RubyEncoding rubyEncoding = libFormat.getEncoding(this, format);
            final Token[] pattern = compilePattern(libFormat.getTString(this, format), rubyEncoding);
            if (formatCanBeFast(pattern) && yearIsFast(time)) {
                var tstring = RubyDateFormatter.formatToTStringFast(pattern, time.dateTime, concatNode, fromLongNode,
                        codePointLengthNode);
                return createString(tstring, Encodings.UTF_8);
            } else {
                final TStringBuilder tstringBuilder = formatTime(this, time, pattern, errnoErrorNode);
                return createString(tstringBuilder.toTStringUnsafe(fromByteArrayNode), rubyEncoding);
            }
        }

        protected boolean formatCanBeFast(Token[] pattern) {
            return RubyDateFormatter.formatCanBeFast(pattern);
        }

        protected static boolean yearIsFast(RubyTime time) {
            // See formatCanBeFast
            final int year = time.dateTime.getYear();
            return year >= 1000 && year <= 9999;
        }

        protected Token[] compilePattern(AbstractTruffleString format, RubyEncoding encoding) {
            return RubyDateFormatter.compilePattern(format, encoding, false, getContext(), this);
        }

        // Optimised for the default Logger::Formatter time format: "%Y-%m-%dT%H:%M:%S.%6N "

        private static TStringBuilder formatTime(Node node, RubyTime time, Token[] pattern,
                ErrnoErrorNode errnoErrorNode) {
            return RubyDateFormatter.formatToTStringBuilder(
                    pattern,
                    time.dateTime,
                    time.zone,
                    time.isUtc,
                    getContext(node),
                    getLanguage(node),
                    node,
                    errnoErrorNode);
        }

    }

    @Primitive(
            name = "time_s_from_array",
            lowerFixnum = {
                    1 /* sec */,
                    2 /* min */,
                    3 /* hour */,
                    4 /* mday */,
                    5 /* month */,
                    6 /* year */,
                    7 /* nsec */,
                    8 /* isdst */ })
    public abstract static class TimeSFromArrayPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child private GetTimeZoneNode getTimeZoneNode = GetTimeZoneNodeGen.create();
        @Child private TruffleString.FromJavaStringNode fromJavaStringNode;

        @Specialization(guards = "(isutc || !isRubyDynamicObject(utcoffset)) || isNil(utcoffset)")
        @TruffleBoundary
        RubyTime timeSFromArray(
                RubyClass timeClass,
                int sec,
                int min,
                int hour,
                int mday,
                int month,
                int year,
                int nsec,
                int isdst,
                boolean isutc,
                Object utcoffset) {
            final RubyLanguage language = getLanguage();

            if (nsec < 0 || nsec > 999_999_999 ||
                    sec < 0 || sec > 60 || // MRI accepts sec=60, whether it is a leap second or not
                    min < 0 || min > 59 ||
                    hour < 0 || hour > 23 ||
                    mday < 1 || mday > 31 ||
                    month < 1 || month > 12) {
                throw new RaiseException(getContext(), coreExceptions().argumentErrorOutOfRange(this));
            }

            final ZoneId zone;
            final boolean relativeOffset;
            Object zoneToStore = nil;
            TimeZoneAndName envZone = null;

            if (isutc) {
                zone = GetTimeZoneNode.UTC;
                relativeOffset = false;
                zoneToStore = language.coreStrings.UTC.createInstance(getContext());
            } else if (utcoffset == nil) {
                if (fromJavaStringNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    fromJavaStringNode = insert(TruffleString.FromJavaStringNode.create());
                }

                envZone = getTimeZoneNode.executeGetTimeZone();
                zone = envZone.getZone();
                relativeOffset = false;
            } else if (utcoffset instanceof Integer || utcoffset instanceof Long) {
                final int offset = ((Number) utcoffset).intValue();
                zone = getZoneOffset(offset);
                relativeOffset = true;
                zoneToStore = nil;
            } else {
                throw new UnsupportedOperationException(
                        StringUtils.format("%s %s %s %s", isdst, isutc, utcoffset, utcoffset.getClass()));
            }

            // java.time does not allow a sec value > 59. However, MRI allows a sec value of 60
            // to support leap seconds. In the case that leap seconds either aren't supported by
            // the underlying timezone or system (they seem to not be POSIX-compliant), MRI still
            // admits a sec value of 60 and just carries values forward to minutes, hours, day, etc.
            // To match MRI's behavior in that case, we build the java.time object with a sec value
            // of 0 and after it's constructed add 60 seconds to the time object, allowing the object
            // to carry values forward as necessary. This approach does not work in the cases where
            // MRI would properly handle a leap second, as java.time does not honor leap seconds.
            // Notably, this occurs when using non-standard timezone like TZ=right/UTC and specifying
            // a real leap second instant (a sec value of 60 is only valid on certain dates in certain
            // years at certain times). In these cases, MRI returns a time with HH:MM:60.
            final boolean sixtySeconds = sec == 60;
            if (sixtySeconds) {
                sec = 0;
            }

            ZonedDateTime dt;
            try {
                dt = ZonedDateTime.of(year, month, mday, hour, min, sec, nsec, zone);
            } catch (DateTimeException e) {
                // Time.new(1999, 2, 31) is legal and should return 1999-03-03
                dt = ZonedDateTime.of(year, 1, 1, hour, min, sec, nsec, zone).plusMonths(month - 1).plusDays(mday - 1);
            }

            if (sixtySeconds) {
                dt = dt.plusSeconds(60);
            }

            if (isdst == 0) {
                dt = dt.withLaterOffsetAtOverlap();
            } else if (isdst == 1) {
                dt = dt.withEarlierOffsetAtOverlap();
            }

            if (envZone != null) {
                final String shortZoneName = envZone.getName(dt);
                zoneToStore = createString(fromJavaStringNode, shortZoneName, Encodings.UTF_8);
            }

            final Shape shape = getLanguage().timeShape;
            final RubyTime instance = new RubyTime(timeClass, shape, dt, zoneToStore, utcoffset, relativeOffset, isutc);
            AllocationTracing.trace(instance, this);
            return instance;
        }

        private ZoneOffset getZoneOffset(int offset) {
            try {
                return ZoneOffset.ofTotalSeconds(offset);
            } catch (DateTimeException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
        }

    }

}
