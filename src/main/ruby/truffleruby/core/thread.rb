# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
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

# Copyright (c) 2011, Evan Phoenix
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
# * Neither the name of the Evan Phoenix nor the names of its contributors
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

class Thread

  # Class methods

  def self.exit
    Thread.current.kill
  end

  def self.kill(thread)
    thread.kill
  end

  def self.stop
    sleep
    nil
  end

  MUTEX_FOR_THREAD_EXCLUSIVE = Mutex.new

  def self.exclusive
    MUTEX_FOR_THREAD_EXCLUSIVE.synchronize { yield }
  end

  def self.handle_interrupt(config, &block)
    unless Primitive.is_a?(config, Hash)
      raise ArgumentError, 'unknown mask signature'
    end
    timing = config.first[1]
    config.each_value do |v|
      raise ArgumentError, 'inconsistent timings not yet supported' unless v == timing
    end
    current.__send__ :handle_interrupt, timing, &block
  end

  def self.pending_interrupt?
    current.pending_interrupt?
  end

  # Already set in CoreLibrary, but for clarity also defined here
  @abort_on_exception = false
  @report_on_exception = true
  @ignore_deadlock = false

  class << self
    attr_accessor :abort_on_exception, :report_on_exception, :ignore_deadlock

    def new(...)
      thread = Primitive.thread_allocate(self)
      thread.send(:initialize, ...)
      unless Primitive.thread_initialized?(thread)
        raise ThreadError, "uninitialized thread - check '#{Primitive.class(thread)}#initialize'"
      end
      thread
    end

    def start(...)
      raise ArgumentError, 'tried to create Proc object without a block' unless block_given?

      thread = Primitive.thread_allocate(self)
      Primitive.thread_initialize(thread)
      thread
    end
    alias_method :fork, :start
  end

  # Instance methods

  def initialize(...)
    Kernel.raise ThreadError, 'must be called with a block' unless block_given?
    if Primitive.thread_initialized?(self)
      Kernel.raise ThreadError, 'already initialized thread'
    end

    # Inherit fiber storage from the current fiber to new thread's root fiber.
    # We must set storage before `thread_initialize` to avoid a race condition.
    # The new thread starts running immediately after `thread_initialize` returns.
    current_storage = Primitive.fiber_get_storage(Fiber.current)
    unless Primitive.nil?(current_storage)
      inherited_storage = current_storage.dup
      root_fiber = Primitive.thread_get_root_fiber(self)
      Primitive.fiber_set_storage(root_fiber, inherited_storage)
    end

    Primitive.thread_initialize(self)
  end

  def freeze
    TruffleRuby.synchronized(self) { Primitive.thread_local_variables(self).freeze }
    super
  end

  def name=(val)
    unless Primitive.nil? val
      val = Truffle::Type.check_null_safe(StringValue(val))
      Kernel.raise ArgumentError, "ASCII incompatible encoding #{val.encoding.name}" unless val.encoding.ascii_compatible?
      # TODO BJF Aug 27, 2016 Need to rb_str_new_frozen the val here and SET_ANOTHER_THREAD_NAME
    end
    Primitive.thread_set_name self, val
    val
  end

  # Java goes from 1 to 10 (default 5), Ruby from -3 to 3 (default 0)
  # Use Array instead of Hash as Hash needs re-hashing with context pre-initialization.
  #                         -3 -2 -1  0  1  2   3
  PRIORITIES_RUBY_TO_JAVA = [1, 2, 4, 5, 7, 8, 10]
  #                           1   2   3   4  5  6  7  8  9 10
  PRIORITIES_JAVA_TO_RUBY = [-3, -2, -1, -1, 0, 1, 1, 2, 2, 3]

  def priority
    java_priority = Primitive.thread_get_priority self
    PRIORITIES_JAVA_TO_RUBY[java_priority-1]
  end

  def priority=(priority)
    Kernel.raise TypeError, 'priority must be an Integer' unless Primitive.is_a?(priority, Integer)
    priority = -3 if priority < -3
    priority = 3 if priority > 3
    java_priority = PRIORITIES_RUBY_TO_JAVA[priority+3]
    Primitive.thread_set_priority self, java_priority
    priority
  end

  def inspect
    loc = Primitive.thread_source_location(self)
    "#{super.delete_suffix('>')} #{loc} #{status || 'dead'}>"
  end
  alias_method :to_s, :inspect

  def raise(exc = undefined, msg = undefined, ctx = nil)
    return nil unless alive?

    exc = Truffle::ExceptionOperations.build_exception_for_raise(exc, msg)

    exc.set_backtrace(ctx) if ctx
    Primitive.exception_capture_backtrace(exc, 1) unless Truffle::ExceptionOperations.backtrace?(exc)

    Truffle::ExceptionOperations.show_exception_for_debug(exc, 1) if $DEBUG

    if self == Thread.current
      Primitive.vm_raise_exception exc
    else
      Primitive.thread_raise self, exc
    end
  end

  def abort_on_exception
    Primitive.thread_get_abort_on_exception(self)
  end

  def abort_on_exception=(val)
    Primitive.thread_set_abort_on_exception(self, Primitive.as_boolean(val))
  end

  def report_on_exception
    Primitive.thread_get_report_on_exception(self)
  end

  def report_on_exception=(val)
    Primitive.thread_set_report_on_exception(self, Primitive.as_boolean(val))
  end

  # Fiber-local variables

  def fetch(name, default = undefined)
    Primitive.warn_block_supersedes_default_value_argument if !Primitive.undefined?(default) && block_given?

    key = Truffle::Type.coerce_to_symbol(name)
    locals = Primitive.thread_get_fiber_locals self
    if Primitive.object_ivar_defined? locals, key
      return Primitive.object_ivar_get locals, key
    end

    if block_given?
      yield key
    elsif Primitive.undefined?(default)
      Kernel.raise KeyError.new("key not found: #{key.inspect}", receiver: self, key: key)
    else
      default
    end
  end

  def [](name)
    var = Truffle::Type.coerce_to_symbol(name)
    locals = Primitive.thread_get_fiber_locals self
    Primitive.object_ivar_get locals, var
  end

  def []=(name, value)
    if frozen?
      raise FrozenError, "can't modify frozen thread locals"
    end

    var = Truffle::Type.coerce_to_symbol(name)
    locals = Primitive.thread_get_fiber_locals self
    Primitive.object_ivar_set locals, var, value
  end

  def key?(name)
    var = Truffle::Type.coerce_to_symbol(name)
    locals = Primitive.thread_get_fiber_locals self
    Primitive.object_ivar_defined? locals, var
  end

  def keys
    locals = Primitive.thread_get_fiber_locals self
    locals.instance_variables
  end

  # Thread-local variables

  def thread_variable_get(name)
    var = Truffle::Type.coerce_to_symbol(name)
    TruffleRuby.synchronized(self) { Primitive.thread_local_variables(self)[var] }
  end

  def thread_variable_set(name, value)
    if frozen?
      Kernel.raise FrozenError, "can't modify frozen thread locals"
    end

    var = Truffle::Type.coerce_to_symbol(name)
    TruffleRuby.synchronized(self) do
      if Primitive.nil?(value)
        Primitive.thread_local_variables(self).delete(var)
      else
        Primitive.thread_local_variables(self)[var] = value
      end
    end
  end

  def thread_variable?(name)
    var = Truffle::Type.coerce_to_symbol(name)
    TruffleRuby.synchronized(self) { Primitive.thread_local_variables(self).key? var }
  end

  def thread_variables
    TruffleRuby.synchronized(self) { Primitive.thread_local_variables(self).keys }
  end

  def backtrace(omit = 0, length = undefined)
    omit, length = Truffle::KernelOperations.normalize_backtrace_args(omit, length)
    Primitive.thread_backtrace(self, omit, length)
  end

  def backtrace_locations(omit = 0, length = undefined)
    omit, length = Truffle::KernelOperations.normalize_backtrace_args(omit, length)
    Primitive.thread_backtrace_locations(self, omit, length)
  end
end

class ThreadGroup
  def initialize
    @enclosed = false
  end

  def enclose
    @enclosed = true
  end

  def enclosed?
    @enclosed
  end

  def add(thread)
    raise ThreadError, "can't move to the frozen thread group" if self.frozen?
    raise ThreadError, "can't move to the enclosed thread group" if self.enclosed?

    from_tg = thread.group
    return nil unless from_tg
    raise ThreadError, "can't move from the frozen thread group" if from_tg.frozen?
    raise ThreadError, "can't move from the enclosed thread group" if from_tg.enclosed?

    Primitive.thread_set_group thread, self
    self
  end

  def list
    Thread.list.select { |th| th.group == self }
  end

  Default = ThreadGroup.new
end

Primitive.thread_set_group Thread.current, ThreadGroup::Default

class Thread::Backtrace
  def self.limit
    Primitive.exception_backtrace_limit
  end
end

class Thread::Backtrace::Location
  def inspect
    to_s.inspect
  end
end

class ConditionVariable

  def wait(mutex, timeout = nil)
    if timeout
      raise ArgumentError, 'Timeout must be positive' if timeout < 0
      timeout = timeout * 1_000_000_000
      timeout = Primitive.rb_num2long(timeout)
    end

    if defined?(::Mutex_m) && Primitive.is_a?(mutex, ::Mutex_m)
      raw_mutex = mutex.instance_variable_get(:@_mutex)
    else
      raw_mutex = mutex
    end

    raise ArgumentError, "#{mutex} must be a Mutex or Mutex_m" unless Primitive.is_a?(raw_mutex, Mutex)

    Primitive.condition_variable_wait(self, raw_mutex, timeout)
  end

  def marshal_dump
    raise TypeError, "can't dump #{Primitive.class(self)}"
  end
end

Truffle::KernelOperations.define_read_only_global(:$!, -> { Primitive.fiber_get_exception })
Truffle::KernelOperations.define_read_only_global(:$?, -> { Primitive.thread_get_return_code })

Truffle::KernelOperations.define_hooked_variable(
  :$@,
  -> { $!.backtrace if $! },
  -> _, value { raise ArgumentError, '$! not set' unless $!
                $!.set_backtrace value }
)
