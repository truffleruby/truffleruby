# frozen_string_literal: true

# Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class Exception
  def ==(other)
    other.instance_of?(Primitive.class(self)) &&
      message == other.message &&
      backtrace == other.backtrace
  end

  def message
    self.to_s
  end

  def to_s
    msg = Truffle::ExceptionOperations.compute_message(self)
    return Primitive.class(self).to_s if Primitive.nil?(msg)
    msg
  end

  def set_backtrace(bt)
    case bt
    when Array
      if bt.all? { |s| Primitive.is_a?(s, String) }
        Primitive.exception_set_custom_backtrace(self, bt)
      elsif bt.all? { |s| Primitive.is_a?(s, Thread::Backtrace::Location) }
        Primitive.exception_set_backtrace_locations(self, bt.dup)
        bt
      else
        raise TypeError, 'backtrace must be an Array of String or an Array of Thread::Backtrace::Location'
      end
    when String
      Primitive.exception_set_custom_backtrace(self, [bt])
    when nil
      Primitive.exception_set_custom_backtrace(self, nil)
    else
      raise TypeError, 'backtrace must be an Array of String or an Array of Thread::Backtrace::Location'
    end
  end

  def inspect
    s = self.to_s
    if s.empty?
      Primitive.class(self).name
    else
      "#<#{Primitive.class(self).name}: #{s}>"
    end
  end

  def full_message(**options)
    Truffle::ExceptionOperations.full_message(self, **options)
  end

  def detailed_message(highlight: nil, **options)
    unless Primitive.true?(highlight) || Primitive.false?(highlight) || Primitive.nil?(highlight)
      raise ArgumentError, "expected true of false as highlight: #{highlight}"
    end

    Truffle::ExceptionOperations.detailed_message(self, highlight)
  end

  class << self
    alias_method :exception, :new
  end

  def exception(message = nil)
    # As strange as this may seem, this is actually the protocol that CRuby implements
    if message and !Primitive.equal?(message, self)
      copy = clone # note: rb_obj_clone() in CRuby
      Primitive.exception_set_message copy, message
      copy
    else
      self
    end
  end

  def self.to_tty?
    # Whether $stderr refers to the original STDERR and STDERR is a tty.
    # When using polyglot stdio, we cannot know and assume false.
    Primitive.equal?($stderr, STDERR) && !STDERR.closed? &&
      (!Truffle::Boot.get_option('polyglot-stdio') && STDERR.tty?)
  end
end

class ScriptError < Exception
end

class StandardError < Exception
end

class SignalException < Exception
end

class NoMemoryError < Exception
end

class ZeroDivisionError < StandardError
end

class ArgumentError < StandardError
end

class UncaughtThrowError < ArgumentError
  attr_reader :tag
  attr_reader :value

  def initialize(tag, value, *rest)
    @tag = tag
    @value = value
    super(*rest)
  end

  def to_s
    sprintf(Primitive.exception_message(self), @tag)
  end
end

class FrozenError < RuntimeError
  def initialize(*args, receiver: undefined)
    super(*args)
    Primitive.frozen_error_set_receiver self, receiver unless Primitive.undefined?(receiver)
  end
end

class IndexError < StandardError
end

class StopIteration < IndexError
end

class RangeError < StandardError
end

class FloatDomainError < RangeError
end

class LocalJumpError < StandardError
end

class NameError < StandardError

  def initialize(*args, receiver: undefined)
    name = args.size > 1 ? args.pop : nil
    super(*args)
    Primitive.name_error_set_name self, name
    Primitive.name_error_set_receiver self, receiver unless Primitive.undefined?(receiver)
  end
end

class NoMethodError < NameError

  def initialize(*arguments, receiver: undefined)
    args = arguments.size > 2 ? arguments.pop : nil
    super(*arguments, receiver: receiver) # TODO BJF Jul 24, 2016 Need to handle NoMethodError.new(1,2,3,4)
    Primitive.no_method_error_set_args self, args
  end
end

class RuntimeError < StandardError
end

class SecurityError < Exception
end

class ThreadError < StandardError
end

class FiberError < StandardError
end

class TypeError < StandardError
end

class FloatDomainError < RangeError
end

class RegexpError < StandardError
end

class LoadError < ScriptError
  attr_accessor :path

  class InvalidExtensionError < LoadError
  end

  class MRIExtensionError < InvalidExtensionError
  end
end

class NotImplementedError < ScriptError
end

class Interrupt < SignalException
  def initialize(message = nil)
    super(Signal.list['INT'], message)
  end
end

class IOError < StandardError
end

class EOFError < IOError
end

class LocalJumpError < StandardError
end

class SyntaxError < ScriptError
  def initialize(*args)
    if args.empty?
      args << 'compile error'
    end
    super(*args)
  end
end

class SystemExit < Exception
  def initialize(status = true, message = nil)
    case status
    when true
      status = Process::EXIT_SUCCESS
    when false
      status = Process::EXIT_FAILURE
    else
      converted = Truffle::Type.rb_check_to_integer(status, :to_int)
      if Primitive.nil?(converted)
        message = status
        status = Process::EXIT_SUCCESS
      else
        status = converted
      end
    end

    message ? super(message) : super()

    Primitive.system_exit_set_status(self, status)
  end

  def success?
    status == Process::EXIT_SUCCESS
  end
end


class SystemCallError < StandardError

  # We use .new here because when errno is set, we attempt to
  # lookup and return a subclass of SystemCallError, specifically,
  # one of the Errno subclasses.
  def self.new(*args)
    # This method is used 2 completely different ways. One is when it's called
    # on SystemCallError, in which case it tries to construct a Errno subclass
    # or makes a generic instead of itself.
    #
    # Otherwise it's called on a Errno subclass and just helps setup
    # a instance of the subclass
    if Primitive.equal?(self, SystemCallError)
      case args.size
      when 1
        if Primitive.is_a?(args.first, Integer)
          errno = args.first
          message = nil
        else
          errno = nil
          message = args.first
          message = StringValue(message) unless Primitive.nil?(message)
        end
        location = nil
      when 2
        message, errno = args
        message = StringValue(message) unless Primitive.nil? message
        location = nil
      when 3
        message, errno, location = args
      else
        raise ArgumentError, "wrong number of arguments (#{args.size} for 1..3)"
      end

      # If it corresponds to a known Errno class, create and return it now
      if errno
        errno = Primitive.rb_num2long(errno)
        error = Primitive.exception_errno_error(self, message, errno, location)
        return error unless Primitive.nil? error
      end
      super(message, errno, location)
    else
      case args.size
      when 0
        message = nil
        location = nil
      when 1
        message = StringValue(args.first)
        location = nil
      when 2
        message, location = args
      else
        raise ArgumentError, "wrong number of arguments (#{args.size} for 0..2)"
      end

      if defined?(self::Errno) && Primitive.is_a?(self::Errno, Integer)
        error = Primitive.exception_errno_error(self, message, self::Errno, location)
        if error && Primitive.equal?(Primitive.class(error), self)
          return error
        end
      end

      super(*args)
    end
  end

  # Must do this here because we have a unique new and otherwise .exception will
  # call Exception.new because of the alias in Exception.
  class << self
    alias_method :exception, :new
  end

  # Use splat args here so that arity returns -1 to match MRI.
  def initialize(*args)
    message, errno, location = args
    Primitive.exception_set_errno self, errno

    msg = +'unknown error'
    msg << " @ #{StringValue(location)}" if location
    msg << " - #{StringValue(message)}" if message
    super(msg)
  end
end

class KeyError < IndexError

  attr_reader :receiver, :key

  def initialize(message = nil, receiver: nil, key: nil)
    @receiver = receiver
    @key = key
    super(message)
  end
end

class SignalException < Exception
  alias_method :signm, :message
  attr_reader :signo

  def initialize(sig, message = undefined)
    signo = Truffle::Type.rb_check_to_integer(sig, :to_int)
    if Primitive.nil? signo
      raise ArgumentError, 'wrong number of arguments (given 2, expected 1)' unless Primitive.undefined?(message)
      if Primitive.is_a?(sig, Symbol)
        sig = sig.to_s
      else
        sig_converted = Truffle::Type.rb_check_convert_type sig, String, :to_str
        raise ArgumentError, "bad signal type #{Primitive.class(sig).name}" if Primitive.nil? sig_converted
        sig = sig_converted
      end
      signal_name = sig
      if signal_name.start_with?('SIG')
        signal_name = signal_name[3..-1]
      end
      signo = Signal::Names[signal_name]
      if Primitive.nil? signo
        raise ArgumentError, "invalid signal name SIG#{sig}"
      end
      name_with_prefix = 'SIG%s' % signal_name
    else
      if signo < 0 || signo > Signal::NSIG
        raise ArgumentError, "invalid signal number (#{signo})"
      end
      name_with_prefix = if Primitive.undefined?(message)
                           name = Signal::Numbers[signo]
                           if Primitive.nil? name
                             'SIG%d' % signo
                           else
                             'SIG%s' % name
                           end
                         else
                           message
                         end
    end
    @signo = signo
    super(name_with_prefix)
  end
end

class StopIteration
  attr_accessor :result
  private :result=
end
