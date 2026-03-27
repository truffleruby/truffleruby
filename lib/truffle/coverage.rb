# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Coverage
  def self.supported?(mode)
    Truffle::Type.rb_check_type(mode, Symbol)
    mode == :lines
  end

  def self.start(modes = Truffle::UNDEFINED)
    if Truffle::Coverage.enabled?
      raise 'coverage measurement is already setup'
    end

    if modes == :all || Primitive.undefined?(modes)
      options = {}
    else
      options = Primitive.convert_with_to_hash(modes)
    end

    if options[:lines] && options[:oneshot_lines]
      raise 'cannot enable lines and oneshot_lines simultaneously'
    end

    @default_mode = Primitive.undefined?(modes)
    Truffle::Coverage.enable

    nil
  end

  def self.result(**options)
    if options.empty?
      stop = true
      clear = true
    else
      stop = options[:stop]
      clear = options[:clear]
    end

    if stop && !clear
      warn 'stop implies clear', uplevel: 1
    end

    result = peek_result

    # TODO: There should be a difference between :stop and :clear in a way they affect counters.
    #       :stop means to remove all the counters at all, :clear - to set 0 values only.
    #       Now we remove counters in both cases.
    Truffle::Coverage.disable if stop || clear
    Truffle::Coverage.enable if !stop && clear

    # By default provides only lines coverage measurement.
    # If some mode was specified explicitly then return counters per mode separately (e.g. for :lines, :branches, etc).
    # Support only :lines for now.
    if !@default_mode
      result.transform_values! do |lines|
        { lines: lines }
      end
    end

    result
  end

  def self.running?
    Truffle::Coverage.enabled?
  end

  def self.peek_result
    Truffle::Coverage.result_array.to_h
  end

end
