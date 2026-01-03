# frozen_string_literal: true

# Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module RegexpOperations

    LAST_MATCH_SET = -> _, v, s {
      unless Primitive.nil?(v) || Primitive.is_a?(v, MatchData)
        raise TypeError, "wrong argument type #{Primitive.class(v)} (expected MatchData)"
      end
      Primitive.regexp_last_match_set(s, v)
    }

    # MRI: rb_reg_match_m/reg_match_pos
    def self.match(re, str, pos = 0)
      return nil unless str

      str = Primitive.is_a?(str, Symbol) ? str.to_s : StringValue(str)

      pos = pos < 0 ? pos + str.size : pos
      return nil if pos < 0 or pos > str.size
      pos = Primitive.character_index_to_byte_index(str, pos)

      Primitive.regexp_search(re, str, pos)
    end
    Primitive.always_split singleton_class, :match

    # MRI: rb_reg_match_p
    def self.match?(re, str, pos = 0)
      return false unless str

      str = Primitive.is_a?(str, Symbol) ? str.to_s : StringValue(str)

      pos = pos < 0 ? pos + str.size : pos
      return false if pos < 0 or pos > str.size
      pos = Primitive.character_index_to_byte_index(str, pos)

      Primitive.regexp_search?(re, str, pos)
    end
    Primitive.always_split singleton_class, :match?

    def self.match_from(re, str, pos)
      return nil unless str

      Primitive.regexp_search(re, str, pos)
    end
    Primitive.always_split singleton_class, :match_from

    Truffle::Boot.delay do
      COMPARE_ENGINES = Truffle::Boot.get_option('compare-regex-engines')
      USE_TRUFFLE_REGEX = Truffle::Boot.get_option('use-truffle-regex')

      if Truffle::Boot.get_option('regexp-instrument-creation') or Truffle::Boot.get_option('regexp-instrument-match')
        at_exit do
          Truffle::RegexpOperations.print_stats
        end
      end
    end

    def self.match_in_region_compare_engines(re, str, from, to, at_start, start, create_match_data)
      begin
        md1 = Primitive.regexp_match_in_region_tregex(re, str, from, to, at_start, start, create_match_data)
      rescue => e
        md1 = e
      end
      begin
        md2 = Primitive.regexp_match_in_region_joni(re, str, from, to, at_start, start, create_match_data)
      rescue => e
        md2 = e
      end
      if self.results_match?(md1, md2)
        self.return_match_data(md1)
      else
        $stderr.puts match_args_to_string(re, str, '?', from, to, at_start, start, 'gave')
        print_match_data(md1)
        $stderr.puts 'but we expected'
        print_match_data(md2)
        self.return_match_data(md2)
      end
    end

    def self.warn_fallback(re, str, enc, from, to, at_start, start)
      warn match_args_to_string(re, str, enc, from, to, at_start, start, 'cannot be run as a Truffle regexp and fell back to Joni'), uplevel: 1
    end

    def self.warn_fallback_regex(re, at_start, encoding)
      warn "Regexp #{re.inspect} at_start=#{at_start} encoding=#{encoding} cannot be compiled to a Truffle regexp and fell back to Joni", uplevel: 1
    end

    def self.warn_backtracking(re, at_start, encoding)
      warn "Regexp #{re.inspect} at_start=#{at_start} encoding=#{encoding} requires backtracking and will not match in linear time", uplevel: 1
    end

    def self.match_args_to_string(re, str, enc, from, to, at_start, start, suffix)
      "match_in_region(#{re.inspect}, #{str.inspect}@#{str.encoding}, #{from}, #{to}, #{at_start}, #{start}) enc=#{enc} #{suffix}"
    end

    def self.results_match?(md1, md2)
      if Primitive.nil?(md1)
        Primitive.nil?(md2)
      elsif Primitive.nil?(md2)
        false
      elsif Primitive.is_a?(md1, Exception)
        Primitive.class(md1) == Primitive.class(md2)
      elsif Primitive.is_a?(md2, Exception)
        false
      else
        if md1.size != md2.size
          return false
        end
        md1.size.times do |x|
          if md1.begin(x) != md2.begin(x) || md1.end(x) != md2.end(x)
            return false
          end
        end
        true
      end
    end

    def self.print_match_data(md)
      if Primitive.nil?(md)
        $stderr.puts '    NO MATCH'
      elsif Primitive.is_a?(md, Exception)
        $stderr.puts "    EXCEPTION - #{md}"
      else
        md.size.times do |x|
          $stderr.puts "    #{md.begin(x)} - #{md.end(x)}"
        end
        md.captures.each do |c|
          $stderr.puts "    #{c}"
        end
      end
    end

    def self.return_match_data(md)
      if Primitive.is_a?(md, Exception)
        raise md
      else
        md
      end
    end

    def self.compilation_stats_regexp(literal_regexps:)
      Hash[*regexp_compilation_stats_array(literal_regexps)]
    end

    def self.match_stats(joni_matches:)
      Hash[*match_stats_array(joni_matches)]
    end

    def self.print_stats
      output_format = Truffle::Boot.get_option('regexp-instrumentation-output-format')

      if output_format == :text
        puts '--------------------'
        puts 'Regular expression statistics'
        puts '--------------------'

        if Truffle::Boot.get_option('regexp-instrument-creation')
          puts '  Compilation (Literal)'
          print_stats_table compilation_stats_regexp(literal_regexps: true)
          puts '  --------------------'
          puts '  Compilation (Dynamic)'
          print_stats_table compilation_stats_regexp(literal_regexps: false)
          puts '  --------------------'
        end

        if Truffle::Boot.get_option('regexp-instrument-match')
          puts '  Matches (Joni)'
          print_stats_table match_stats(joni_matches: true)
          puts '  --------------------'
          puts '  Matches (TRegex)'
          print_stats_table match_stats(joni_matches: false)

          if Truffle::Boot.get_option('regexp-instrument-creation')
            puts '  --------------------'
            puts '  Unused Regexps'
            puts unused_regexps_array.map { |regexp| "  #{regexp}" }
          end

          puts '--------------------'
        end
      elsif output_format == :json
        ret = {}

        if Truffle::Boot.get_option('regexp-instrument-creation')
          ret[:regexps] = compiled_regexp_hash_array
        end

        if Truffle::Boot.get_option('regexp-instrument-match')
          ret[:matches] = matched_regexp_hash_array
        end

        # The values must be collected before requiring the JSON library, since the JSON library will load new regexps
        # to support its own operations. We don't want those to pollute the application profile.
        require 'json'
        puts JSON.pretty_generate(ret)
      else
        raise "Unsupported regexp output format: #{output_format}"
      end
    end

    def self.print_stats_table(table)
      return if table.empty?
      sorted = table.to_a.sort_by(&:last).reverse
      width = sorted.first.last.to_s.size
      sorted.each do |regexp, count|
        printf "    %#{width}d    %s\n", count, regexp.inspect
      end
    end

    def self.option_to_string(option)
      string = +''
      string << 'm' if (option & Regexp::MULTILINE) > 0
      string << 'i' if (option & Regexp::IGNORECASE) > 0
      string << 'x' if (option & Regexp::EXTENDED) > 0
      string
    end

    def self.collapsing?(match)
      Primitive.match_data_byte_begin(match, 0) == Primitive.match_data_byte_end(match, 0)
    end

    def self.pre_match_from(match, idx)
      source = Primitive.match_data_get_source(match)
      match_byte_begin = Primitive.match_data_byte_begin(match, 0)
      return source.byteslice(0, 0) if match_byte_begin == 0

      nd = match_byte_begin - 1
      source.byteslice(idx, nd-idx+1)
    end
  end
end
