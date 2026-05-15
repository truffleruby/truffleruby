# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module ExceptionOperations
    def self.make_exception(exc = undefined, msg = undefined, backtrace = nil)
      if Primitive.undefined? exc
        return nil
      elsif Primitive.undefined? msg
        converted = Truffle::Type.rb_check_convert_type(exc, String, :to_str)
        return RuntimeError.new(converted) unless Primitive.nil?(converted)
        exc = call_exception(exc)
      else
        exc = call_exception(exc, msg)
      end
      exc.set_backtrace(backtrace) if backtrace
      exc
    end

    def self.build_exception_for_raise(exc = undefined, msg = undefined, backtrace = nil, cause: undefined, **kwargs)
      cause_given = !Primitive.undefined?(cause)
      if Primitive.undefined?(exc) && cause_given
        raise ArgumentError, 'only cause is given with no arguments'
      end

      if Primitive.undefined?(msg) && !kwargs.empty?
        msg = kwargs
      end

      if Primitive.undefined? exc
        exc = ::RuntimeError.exception ''
      elsif exc.respond_to? :exception
        if Primitive.undefined? msg
          exc = exc.exception
        else
          exc = exc.exception msg
        end

        exception_object_expected! unless Primitive.is_a?(exc, ::Exception)
      elsif Primitive.is_a?(exc, ::String) && Primitive.undefined?(msg)
        exc = ::RuntimeError.exception exc
      else
        exception_class_object_expected!
      end

      # Handle backtrace
      exc.set_backtrace(backtrace) if backtrace

      # Handle cause
      if cause_given
        unless Primitive.is_a?(cause, ::Exception) || Primitive.nil?(cause)
          exception_object_expected!
        end
      else
        if Primitive.nil?(exc.cause) && !Primitive.exception_used_as_a_cause?(exc)
          cause = $!
        else
          cause = nil
        end
      end

      if !Primitive.nil?(cause) && (cause_given || Primitive.nil?(exc.cause)) && !Primitive.equal?(cause, exc)
        if circular_cause?(cause, exc)
          raise ArgumentError, 'circular causes'
        end

        Primitive.exception_set_cause exc, cause
        Primitive.exception_used_as_a_cause!(cause)
      end

      exc
    end

    def self.prepare_before_raise_exception(exc)
      # Set internal backtrace if not already set
      Primitive.exception_capture_backtrace(exc, 1) unless Primitive.exception_backtrace?(exc)

      # Print exception if $DEBUG
      show_exception_for_debug(exc, 1) if $DEBUG

      exc
    end

    def self.raise_exception_in_target_fiber_or_thread(exc)
      exc = build_exception_for_raise(exc, cause: nil)
      exc = prepare_before_raise_exception(exc)
      Primitive.vm_raise_exception exc
    end

    def self.circular_cause?(cause, exception)
      while cause && !Primitive.equal?(cause, exception)
        cause = cause.cause
      end

      !Primitive.nil?(cause)
    end

    def self.call_exception(exc, *args)
      res = Truffle::Type.check_funcall(exc, :exception, args)
      exception_class_object_expected! if Primitive.undefined?(res)
      exception_object_expected! unless Primitive.is_a?(res, Exception)
      res
    end

    # Avoid using #raise here to prevent infinite recursion
    def self.exception_class_object_expected!
      exc = ::TypeError.new('exception class/object expected')
      Primitive.exception_capture_backtrace(exc, 1)
      show_exception_for_debug(exc, 2) if $DEBUG
      Primitive.vm_raise_exception exc
    end

    # Avoid using #raise here to prevent infinite recursion
    def self.exception_object_expected!
      exc = ::TypeError.new('exception object expected')
      Primitive.exception_capture_backtrace(exc, 1)
      show_exception_for_debug(exc, 2) if $DEBUG
      Primitive.vm_raise_exception exc
    end

    def self.show_exception_for_debug(exc, uplevel)
      STDERR.puts "Exception: '#{Primitive.class(exc)}' at #{caller(uplevel + 1, 1)[0]} - #{exc.message}\n"
    end

    def self.class_name(receiver)
      Primitive.class(receiver).name
    end

    # MRI: name_err_mesg_to_str
    def self.receiver_string(receiver)
      ret = begin
        case receiver
        when true, false
          receiver.to_s
        when nil
          'nil'
        when Class
          name = receiver.name || receiver.to_s
          "class #{name}"
        when Module
          name = receiver.name || receiver.to_s
          "module #{name}"
        else
          klass = Primitive.metaclass(receiver)

          unless klass.singleton_class?
            class_name = klass.name || klass.to_s
            "an instance of #{class_name}"
          end
          # otherwise fall through to rb_any_to_s
        end
      rescue Exception # rubocop:disable Lint/RescueException
        nil
      end
      ret = Primitive.rb_any_to_s(receiver) unless ret
      ret
    end

    # MRI: inspect_frozen_obj
    def self.inspect_frozen_object(object)
      string = nil

      return '...' if Truffle::ThreadOperations.detect_recursion object do
        string = Truffle::Type.rb_inspect(object)
      end

      string
    end

    # default implementation of Exception#detailed_message hook
    def self.detailed_message(exception, highlight)
      message = Primitive.convert_with_to_str(exception.message.to_s)

      exception_class = Primitive.class(exception)
      class_name = exception_class.to_s

      if Primitive.is_a?(exception, Polyglot::ForeignException) and
          Truffle::Interop.has_meta_object?(exception)
        class_name = "#{class_name}: #{Truffle::Interop.meta_qualified_name Truffle::Interop.meta_object(exception)}"
      end

      if message.empty?
        message = Primitive.equal?(exception_class, RuntimeError) ? 'unhandled exception' : class_name
        message = "\e[1;4m#{message}\e[m" if highlight
        return message
      end

      anonymous_class = Primitive.module_anonymous?(Primitive.class(exception))

      if highlight
        highlighted_class_string = !anonymous_class ? " (\e[1;4m#{class_name}\e[m\e[1m)" : ''

        if message.include?("\n")
          first = true
          result = +''

          message.each_line do |line|
            if first
              first = false
              result << "\e[1m#{line.chomp}#{highlighted_class_string}\e[m"
            else
              result << "\n\e[1m#{line.chomp}\e[m"
            end
          end

          result
        else
          "\e[1m#{message}#{highlighted_class_string}\e[m"
        end
      else
        class_string = !anonymous_class ? " (#{class_name})" : ''

        if i = message.index("\n")
          "#{message[0...i]}#{class_string}#{message[i..-1]}"
        else
          "#{message}#{class_string}"
        end
      end
    end

    # User can customise exception and override/undefine Exception#detailed_message method.
    # This way we need to handle corner cases when #detailed_message is undefined or
    # returns something other than String.
    def self.detailed_message_or_fallback(exception, options)
      unless Primitive.respond_to?(exception, :detailed_message, false)
        return detailed_message_fallback(exception, options)
      end

      detailed_message = exception.detailed_message(**options)
      detailed_message = Truffle::Type.rb_check_convert_type(detailed_message, String, :to_str)

      if !Primitive.nil?(detailed_message)
        detailed_message
      else
        detailed_message_fallback(exception, options)
      end
    end

    def self.detailed_message_fallback(exception, options)
      class_name = Primitive.class(exception).to_s

      if options[:highlight]
        "\e[1;4m#{class_name}\e[m\e[1m"
      else
        class_name
      end
    end

    def self.full_message(exception, **options)
      highlight = options[:highlight]
      highlight = if Primitive.nil?(highlight)
                    Exception.to_tty?
                  else
                    raise ArgumentError, "expected true of false as highlight: #{highlight}" unless Primitive.true?(highlight) || Primitive.false?(highlight)
                    !Primitive.false?(highlight)
                  end

      options[:highlight] = highlight

      order = options[:order]
      order = :top if Primitive.nil?(order)
      raise ArgumentError, "expected :top or :bottom as order: #{order}" unless Primitive.equal?(order, :top) || Primitive.equal?(order, :bottom)
      reverse = !Primitive.equal?(order, :top)

      result = ''.b
      bt = exception.backtrace || caller(2)

      if reverse
        traceback_msg = if highlight
                          "\e[1mTraceback\e[m (most recent call last):\n"
                        else
                          "Traceback (most recent call last):\n"
                        end

        result << traceback_msg
        append_causes(result, exception, {}.compare_by_identity, reverse, highlight, options)
        backtrace_message = backtrace_message(highlight, reverse, bt, exception, options)

        if backtrace_message.empty?
          result << detailed_message_or_fallback(exception, options)
        else
          result << backtrace_message
        end
      else
        backtrace_message = backtrace_message(highlight, reverse, bt, exception, options)

        if backtrace_message.empty?
          result << detailed_message_or_fallback(exception, options)
        else
          result << backtrace_message
        end

        append_causes(result, exception, {}.compare_by_identity, reverse, highlight, options)
      end

      result
    end

    def self.backtrace_message(highlight, reverse, bt, exc, options)
      message = detailed_message_or_fallback(exc, options)
      message = message.end_with?("\n") ? message : "#{message}\n"

      return '' if Primitive.nil?(bt) || bt.empty?
      limit = Primitive.exception_backtrace_limit
      limit = limit >= 0 && bt.size - 1 >= limit + 2 ? limit : -1
      result = if reverse
                 bt[1..limit].reverse.map do |l|
                   "\tfrom #{l}\n"
                 end.join
               else
                 "#{bt[0]}: #{message}" + bt[1..limit].map do |l|
                   "\tfrom #{l}\n"
                 end.join
               end
      result + (limit != -1 ? "\t ... #{bt.size - limit - 1} levels...\n" : '') + (reverse ? "#{bt[0]}: #{message}" : '')
    end

    def self.append_causes(str, err, causes, reverse, highlight, options)
      cause = err.cause
      if !Primitive.nil?(cause) && Primitive.is_a?(cause, Exception) && !causes.has_key?(cause)
        causes[cause] = true
        if reverse
          append_causes(str, cause, causes, reverse, highlight, options)
          backtrace_message = backtrace_message(highlight, reverse, cause.backtrace, cause, options)
          if backtrace_message.empty?
            str << detailed_message_or_fallback(cause, options)
          else
            str << backtrace_message
          end
        else
          backtrace_message = backtrace_message(highlight, reverse, cause.backtrace, cause, options)
          if backtrace_message.empty?
            str << detailed_message_or_fallback(cause, options)
          else
            str << backtrace_message
          end
          append_causes(str, cause, causes, reverse, highlight, options)
        end
      end
    end

    # Return user provided message if it was specified.
    # A message might be computed (and assigned) lazily in some cases (e.g. for NoMethodError).
    def self.compute_message(exception)
      message = Primitive.exception_message(exception)
      # mimic CRuby behaviour and explicitly convert a user provided message to String
      return message.to_s unless Primitive.nil?(message)

      formatter = Primitive.exception_formatter(exception)
      return nil if Primitive.nil?(formatter)

      message = formatter.call(exception)
      Primitive.exception_set_message(exception, message)
      message
    end

    def self.get_formatted_backtrace(exception)
      full_message(exception, highlight: nil, order: :top)
    end

    def self.comparison_error_message(x, y)
      y_classname = if Truffle::Type.is_special_const?(y)
                      y.inspect
                    else
                      Primitive.class(y)
                    end
      "comparison of #{Primitive.class(x)} with #{y_classname} failed"
    end

    NO_METHOD_ERROR = Proc.new do |exception|
      format("undefined method '%s' for %s", exception.name, receiver_string(exception.receiver))
    end

    NO_LOCAL_VARIABLE_OR_METHOD_ERROR = Proc.new do |exception|
      format("undefined local variable or method '%s' for %s", exception.name, receiver_string(exception.receiver))
    end

    PRIVATE_METHOD_ERROR = Proc.new do |exception|
      format("private method '%s' called for %s", exception.name, receiver_string(exception.receiver))
    end

    PROTECTED_METHOD_ERROR = Proc.new do |exception|
      format("protected method '%s' called for %s", exception.name, receiver_string(exception.receiver))
    end

    SUPER_METHOD_ERROR = Proc.new do |exception|
      format("super: no superclass method '%s'", exception.name)
    end

    def self.format_errno_error_message(errno_description, errno, extra_message, location)
      "#{errno_description || "Unknown error: #{errno}"}#{" @ #{location}" if location}#{" - #{extra_message}" if extra_message}"
    end
  end
end
