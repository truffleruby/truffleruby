# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module TimeOperations
    MonthValue = {
      'JAN' => 1, 'FEB' => 2, 'MAR' => 3, 'APR' => 4, 'MAY' => 5, 'JUN' => 6,
      'JUL' => 7, 'AUG' => 8, 'SEP' => 9, 'OCT' =>10, 'NOV' =>11, 'DEC' =>12
    }

    # Handles Time methods .utc, .gm. .local, .mktime dual signature:
    # - year, month, day, hour, min, sec, usec
    # - sec, min, hour, day, month, year, dummy, dummy, dummy, dummy
    def self.compose_dual_signature(time_class, utc_offset, p1, p2 = nil, p3 = nil, p4 = nil, p5 = nil, p6 = nil, p7 = nil,
                     yday = undefined, is_dst = undefined, tz = undefined)
      if Primitive.undefined?(tz)
        unless Primitive.undefined?(is_dst)
          raise ArgumentError, 'wrong number of arguments (9 for 1..8)'
        end

        year, month, mday, hour, min, sec, usec, is_dst = p1, p2, p3, p4, p5, p6, p7, nil
      else
        year, month, mday, hour, min, sec, usec, is_dst = p6, p5, p4, p3, p2, p1, 0, is_dst
      end

      nsec = nil
      if Primitive.is_a?(usec, String)
        nsec = usec.to_i * 1000
      elsif usec
        nsec = (usec * 1000).to_i
      end

      compose(time_class, utc_offset, year, month, mday, hour, min, sec, nsec, is_dst)
    end

    def self.compose(time_class, utc_offset, year, month, mday, hour, min, sec, nsec, is_dst)
      if Primitive.is_a?(month, String) or month.respond_to?(:to_str)
        month = Primitive.convert_with_to_str(month)
        month = MonthValue[month.upcase] || month.to_i

        raise ArgumentError, 'month argument out of range' unless month
      end

      year = year.to_i if Primitive.is_a?(year, String)
      mday = mday.to_i if Primitive.is_a?(mday, String)
      hour = hour.to_i if Primitive.is_a?(hour, String)
      min  = min.to_i  if Primitive.is_a?(min,  String)

      # Ensure all the user provided numeric values fit into the Java Integer type.
      # sec and nsec are handled separately.
      year  = Primitive.rb_num2int(year      )
      month = Primitive.rb_num2int(month || 1)
      mday  = Primitive.rb_num2int(mday  || 1)
      hour  = Primitive.rb_num2int(hour  || 0)
      min   = Primitive.rb_num2int(min   || 0)

      case utc_offset
      when :utc
        is_dst = nil
        is_utc = true
        utc_offset = nil
      when :local
        is_utc = false
        utc_offset = nil
      else
        is_dst = nil
        is_utc = false
      end

      if Primitive.is_a?(sec, String)
        sec = sec.to_i
      elsif nsec
        sec = Primitive.convert_with_to_int(sec || 0)
      else
        s = Truffle::Type.coerce_to_exact_num(sec || 0)

        sec       = s.to_i
        nsec_frac = s % 1.0

        if s < 0 && nsec_frac > 0
          sec -= 1
        end

        nsec = (nsec_frac * 1_000_000_000 + 0.5).to_i
      end

      nsec ||= 0
      dst_code = is_dst ? 1 : (Primitive.nil?(is_dst) ? -1 : 0)

      Primitive.time_s_from_array(time_class, sec, min, hour, mday, month, year, nsec, dst_code, is_utc, utc_offset)
    end

    # MRI: time_init_parse()
    def self.new_from_string(time_class, str, **options)
      raise ArgumentError, 'time string should have ASCII compatible encoding' unless str.encoding.ascii_compatible?

      # Fast path for well-formed strings.
      if /\A (?<year>\d{4,5})
             (?:
               - (?<month>\d{2})
               - (?<mday> \d{2})
               [ T] (?<hour> \d{2})
                  : (?<min>  \d{2})
                  : (?<sec>  \d{2})
                  (?:\. (?<subsec> \d+) )?
              (?:\s* (?<offset>\S+))?
             )?\z/x =~ str

        nsec = subsec[0...9].ljust(9, '0').to_i if subsec
        utc_offset = self.utc_offset_for_compose(offset || options[:in])
        return self.compose(time_class, utc_offset, year, month, mday, hour, min, sec, nsec, nil)
      end

      raise ArgumentError, "can't parse: #{str.inspect}"
    end

    def self.utc_offset_for_compose(utc_offset)
      if Primitive.nil?(utc_offset)
        :local
      elsif Time.send(:utc_offset_in_utc?, utc_offset)
        :utc
      else
        coerce_to_utc_offset(utc_offset)
      end
    end

    # When a timezone object is used (via its utc_to_local or local_to_utc methods)
    # the resulting time object gets its zone set to be the object
    # (but this isn't done when the zone is just an integer offset or string abbreviation).
    def self.set_zone_if_object(time, zone)
      return if Primitive.nil?(zone) || Primitive.is_a?(zone, Integer) || Primitive.is_a?(zone, String)

      Primitive.time_set_zone(time, zone)
    end

    def self.calculate_utc_offset_with_timezone_object(zone, conversion_method, time)
      if conversion_method == :local_to_utc && Primitive.respond_to?(zone, :local_to_utc, false)
        Primitive.assert time.utc?
        as_utc = zone.local_to_utc(time)
        offset = time.to_i - as_utc.to_i
      elsif conversion_method == :utc_to_local && Primitive.respond_to?(zone, :utc_to_local, false)
        time ||= Time.now
        as_local = zone.utc_to_local(time.getutc)
        offset = if Primitive.is_a?(as_local, Time)
                   as_local.to_i + as_local.utc_offset - time.to_i
                 else
                   as_local.to_i - time.to_i
                 end
      else
        return nil
      end

      validate_utc_offset(offset)
      offset
    end

    def self.coerce_to_utc_offset(offset)
      offset = String.try_convert(offset) || offset

      if Primitive.is_a? offset, String
        offset = coerce_string_to_utc_offset(offset)
      else
        offset = Truffle::Type.coerce_to_exact_num(offset)
      end

      if Primitive.is_a?(offset, Rational)
        offset = offset.round
      end

      validate_utc_offset(offset)
      offset
    end

    def self.validate_utc_offset(offset)
      if offset <= -86400 || offset >= 86400
        raise ArgumentError, 'utc_offset out of range'
      end
    end

    UTC_OFFSET_WITH_COLONS_PATTERN = /\A(?<sign>\+|-)(?<hours>\d\d)(?::(?<minutes>\d\d)(?::(?<seconds>\d\d))?)?\z/
    UTC_OFFSET_WITHOUT_COLONS_PATTERN = /\A(?<sign>\+|-)(?<hours>\d\d)(?:(?<minutes>\d\d)(?:(?<seconds>\d\d))?)?\z/
    UTC_OFFSET_PATTERN = /#{UTC_OFFSET_WITH_COLONS_PATTERN}|#{UTC_OFFSET_WITHOUT_COLONS_PATTERN}/

    def self.coerce_string_to_utc_offset(offset)
      unless offset.encoding.ascii_compatible?
        raise ArgumentError, '"+HH:MM", "-HH:MM", "UTC" or "A".."I","K".."Z" expected for utc_offset: ' + offset.inspect
      end

      if offset == 'UTC'
        offset = 0
      elsif offset.size == 1 && ('A'..'Z') === offset && offset != 'J'
        if offset == 'Z'
          offset = 0
        elsif offset < 'J' # skip J
          offset = (offset.ord - 'A'.ord + 1) * 3600 # ("A".."I") => 1, 2, ...
        elsif offset > 'J' && offset <= 'M'
          offset = (offset.ord - 'A'.ord) * 3600 # ("K".."M") => 10, 11, 12
        else
          offset = (offset.ord - 'N'.ord + 1) * -3600 # ("N"..Y) => -1, -2, ...
        end
      elsif (m = offset.match(UTC_OFFSET_PATTERN)) && m[:minutes].to_i < 60 && m[:seconds].to_i < 60
        # ignore hours - they are validated indirectly in #coerce_to_utc_offset
        offset = m[:hours].to_i*60*60 + m[:minutes].to_i*60 + m[:seconds].to_i
        offset = -offset if m[:sign] == '-'
      else
        raise ArgumentError, '"+HH:MM", "-HH:MM", "UTC" or "A".."I","K".."Z" expected for utc_offset: ' + offset
      end

      offset
    end
  end
end
