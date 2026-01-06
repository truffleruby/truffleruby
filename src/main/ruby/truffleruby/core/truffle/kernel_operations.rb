# frozen_string_literal: true

# Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module KernelOperations
    @loading_rubygems = false
    class << self
      attr_writer :loading_rubygems
      def loading_rubygems?
        @loading_rubygems
      end
    end

    def self.to_enum_with_size(enum, method, size_method)
      enum.to_enum(method) { enum.send(size_method) }
    end

    def self.convert_duration_to_nanoseconds(duration)
      unless duration.respond_to?(:divmod)
        raise TypeError, "can't convert #{Primitive.class(duration)} into time interval"
      end
      a, b = duration.divmod(1)
      ((a + b) * 1_000_000_000)
    end

    def self.define_hooked_variable(name, getter, setter, defined = proc { 'global-variable' })
      define_hooked_variable_with_is_defined(name, getter, setter, defined)
    end

    def self.define_read_only_global(name, getter)
      setter = -> n, _ { raise NameError, "#{n} is a read-only variable" }
      define_hooked_variable(name, getter, setter)
    end

    FEATURE_LOADING_LOCK = Object.new
    LOAD_PATH = Truffle::VersionedArray.new(FEATURE_LOADING_LOCK)
    LOADED_FEATURES = Truffle::VersionedArray.new(FEATURE_LOADING_LOCK)

    # Always provided features: ruby --disable-gems -e 'puts $"'
    LOADED_FEATURES << 'ruby2_keywords.rb'

    define_read_only_global(:$LOAD_PATH, -> { LOAD_PATH })
    define_read_only_global(:$LOADED_FEATURES, -> { LOADED_FEATURES })

    alias $: $LOAD_PATH
    alias $-I $LOAD_PATH
    alias $" $LOADED_FEATURES

    def $LOAD_PATH.resolve_feature_path(file_name)
      path = Truffle::Type.coerce_to_path(file_name)
      status, path, ext = Truffle::FeatureLoader.find_feature_or_file(path, false)

      return nil if status == :not_found
      [ext, path]
    end

    # The runtime needs to access these values, so we want them to be set in the variable storage.
    Primitive.global_variable_set :$LOAD_PATH, LOAD_PATH
    Primitive.global_variable_set :$LOADED_FEATURES, LOADED_FEATURES

    define_read_only_global(:$*, -> { ARGV })

    define_read_only_global(:$-a, -> { Truffle::Boot.get_option 'split-loop' })
    define_read_only_global(:$-l, -> { Truffle::Boot.get_option 'chomp-loop' })
    define_read_only_global(:$-p, -> { Truffle::Boot.get_option 'print-loop' })

    define_hooked_variable(
      :$/,
      -> { Primitive.global_variable_get :$/ },
      -> name, v {
        if v && !Primitive.is_a?(v, String)
          raise TypeError, "value of #{name} must be String"
        end
        Primitive.global_variable_set :$/, v
      })

    $/ = "\n".freeze

    define_hooked_variable(
      :$\,
      -> { Primitive.global_variable_get :$\ },
      -> name, v {
        if v && !Primitive.is_a?(v, String)
          raise TypeError, "value of #{name} must be String"
        end
        Primitive.global_variable_set :$\, v
      })

    $\ = nil

    Truffle::Boot.delay do
      if Truffle::Boot.get_option 'chomp-loop'
        $\ = $/
      end
    end

    alias $-0 $/

    define_hooked_variable(
      :'$,',
      -> { Primitive.global_variable_get :$, },
      -> name, v {
        if v && !Primitive.is_a?(v, String)
          raise TypeError, "value of #{name} must be String"
        end
        warn "'#{name}' is deprecated", uplevel: 1 if !Primitive.nil?(v) && Warning[:deprecated]
        Primitive.global_variable_set :$,, v
      })

    define_read_only_global(:'$-W',
      -> {
        case $VERBOSE
        when nil
          0
        when false
          1
        when true
          2
        else
          nil
        end
      })


    $, = nil # It should be defined by the time boot has finished.

    define_hooked_variable(
      :$=,
      -> {
        warn 'variable $= is no longer effective', uplevel: 1 if Warning[:deprecated]
        Primitive.global_variable_get :$=
      },
      -> _, v {
        warn 'variable $= is no longer effective', uplevel: 1 if Warning[:deprecated]
        Primitive.global_variable_set :$=, v
      })

    Primitive.global_variable_set :$=, false

    define_hooked_variable(
      :$VERBOSE,
      -> { Primitive.global_variable_get :$VERBOSE },
      -> _, v {
        v = Primitive.nil?(v) ? nil : Primitive.as_boolean(v)
        Primitive.global_variable_set :$VERBOSE, v
      })

    Truffle::Boot.redo do
      $DEBUG = Truffle::Boot.get_option_or_default('debug', false)
      $VERBOSE = case Truffle::Boot.get_option_or_default('verbose', false)
                 when :true
                   true
                 when :false
                   false
                 when :nil
                   nil
                 end
    end

    alias $-d $DEBUG
    alias $-v $VERBOSE
    alias $-w $VERBOSE

    define_hooked_variable(
      :$stdout,
      -> { Primitive.global_variable_get :$stdout },
      -> name, v {
        raise TypeError, "#{name} must have write method, #{Primitive.class(v)} given" unless v.respond_to?(:write)
        Primitive.global_variable_set :$stdout, v
      })

    alias $> $stdout

    define_hooked_variable(
      :$stderr,
      -> { Primitive.global_variable_get :$stderr },
      -> name, v {
        raise TypeError, "#{name} must have a write method, #{Primitive.class(v)} given" unless v.respond_to?(:write)
        Primitive.global_variable_set :$stderr, v
      })

    define_hooked_variable(
      :"$;",
      -> { Primitive.global_variable_get :"$;" },
      -> _, v {
        warn "'$;' is deprecated", uplevel: 1 if !Primitive.nil?(v) && Warning[:deprecated]
        Primitive.global_variable_set :"$;", v
      })

    $; = nil

    def self.load_error(name)
      load_error = LoadError.new("cannot load such file -- #{name}")
      load_error.path = name
      load_error
    end

    def self.check_last_line(line)
      unless Primitive.is_a? line, String
        raise TypeError, "$_ value need to be String (#{Truffle::ExceptionOperations.to_class_name(line)} given)"
      end
      line
    end

    # Will throw an exception if the arguments are invalid, and potentially convert a range to [omit, length] format.
    def self.normalize_backtrace_args(omit, length)
      if Primitive.is_a?(length, Integer) && length < 0
        raise ArgumentError, "negative size (#{length})"
      end
      if Primitive.is_a?(omit, Range)
        range = omit
        if Primitive.nil? range.begin
          omit = 0
        else
          omit = Primitive.rb_to_int(range.begin)
        end
        unless Primitive.nil? range.end
          end_index = Primitive.rb_to_int(range.end)
          if end_index < 0
            length = end_index
          else
            end_index += (range.exclude_end? ? 0 : 1)
            length = omit > end_index ? 0 : end_index - omit
          end
        end
      end
      [omit, length]
    end

    KERNEL_FROZEN = Kernel.instance_method(:frozen?)
    private_constant :KERNEL_FROZEN

    # Returns whether the value is frozen, even if the value's class does not include `Kernel`.
    def self.value_frozen?(value)
      KERNEL_FROZEN.bind(value).call
    end

    # To get the class even if the value's class does not include `Kernel`, use `Primitive.class`.
  end
end
