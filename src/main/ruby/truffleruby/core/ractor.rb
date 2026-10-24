# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Ractor is implemented on top of Thread: each Ractor runs its block in a new Thread,
# so Ractors run in parallel like Threads do on TruffleRuby.
# There is no isolation between Ractors: objects are shared and never copied or moved,
# Ractor.make_shareable returns its argument as is and Ractor.shareable? is always true.
# This is enough to run programs using the Ractor API to communicate between Ractors,
# but programs cannot rely on Ractor isolation on TruffleRuby.
class Ractor
  class Error < RuntimeError
  end

  class RemoteError < Error
    attr_reader :ractor

    def initialize(ractor)
      super('thrown by remote Ractor.')
      @ractor = ractor
    end
  end

  class ClosedError < StopIteration
  end

  class IsolationError < Error
  end

  class MovedError < Error
  end

  class UnsafeError < Error
  end

  # Guards the Ractor count, the termination state and the monitors of all Ractors,
  # and lets Ractor.select wait until a message is sent to a Port or a Ractor terminates.
  LOCK = Mutex.new
  CONDITION = ConditionVariable.new
  private_constant :LOCK, :CONDITION

  class Port
    def initialize
      @queue = Thread::Queue.new
    end

    def send(message, move: false)
      LOCK.synchronize do
        raise ClosedError, 'The port was already closed' if @queue.closed?
        @queue.push(message)
        CONDITION.broadcast
      end
      self
    end
    alias_method :<<, :send

    def receive
      message = @queue.pop
      # Thread::Queue#pop returns nil when the queue is closed and empty
      if Primitive.nil?(message) && @queue.closed?
        raise ClosedError, 'The port was already closed'
      end
      message
    end

    def close
      LOCK.synchronize do
        @queue.close
        CONDITION.broadcast
      end
      self
    end

    def closed?
      @queue.closed?
    end

    def inspect
      Primitive.rb_any_to_s(self)
    end

    # For Ractor.select
    attr_reader :queue
    private :queue
  end

  class << self
    def new(...)
      unless @experimental_warning_shown
        @experimental_warning_shown = true
        Kernel.warn('Ractor API is experimental and may change in future versions of Ruby.',
                    uplevel: 1, category: :experimental)
      end
      super
    end

    def current
      Primitive.object_hidden_var_get(Thread.current, Truffle::ThreadOperations::RACTOR_KEY) || @main
    end

    def main
      @main
    end

    def main?
      Primitive.equal?(current, @main)
    end

    def count
      LOCK.synchronize { @count }
    end

    def receive
      current.__send__(:receive)
    end
    alias_method :recv, :receive

    def [](key)
      current[key]
    end

    def []=(key, value)
      current[key] = value
    end

    def store_if_absent(key, &block)
      current.__send__(:store_if_absent, key, &block)
    end

    def make_shareable(object, copy: false)
      object
    end

    def shareable?(object)
      true
    end

    alias_method :shareable_proc, :proc
    alias_method :shareable_lambda, :lambda
    public :shareable_proc, :shareable_lambda

    def select(*ports)
      raise ArgumentError, 'specify at least one Ractor::Port or Ractor' if ports.empty?
      ports.each do |port|
        unless Primitive.is_a?(port, Port) || Primitive.is_a?(port, Ractor)
          raise ArgumentError, 'should be Ractor::Port or Ractor'
        end
      end

      LOCK.synchronize do
        while true
          ports.each do |port|
            if Primitive.is_a?(port, Ractor)
              return [port, port.__send__(:result)] if port.__send__(:terminated?)
            else
              queue = port.__send__(:queue)
              begin
                return [port, queue.pop(true)]
              rescue ThreadError # queue empty
                raise ClosedError, 'The port was already closed' if queue.closed?
              end
            end
          end
          CONDITION.wait(LOCK)
        end
      end
    end

    private

    # Returns the id of the new Ractor
    def started
      LOCK.synchronize do
        @count += 1
        @next_id += 1
      end
    end

    def finished
      LOCK.synchronize { @count -= 1 }
    end
  end

  attr_reader :name, :default_port

  def initialize(*args, name: nil, &block)
    raise ArgumentError, 'must be called with a block' unless block
    name = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(name)) unless Primitive.nil?(name)
    file, line = block.source_location
    setup(Ractor.__send__(:started), name, "#{file}:#{line}")
    @thread = Thread.new { run(args, block) }
  end

  def [](key)
    unless Primitive.equal?(self, Ractor.current)
      raise 'Cannot get ractor local storage for non-current ractor'
    end
    @storage[storage_key(key)]
  end

  def []=(key, value)
    unless Primitive.equal?(self, Ractor.current)
      raise 'Cannot set ractor local storage for non-current ractor'
    end
    @storage[storage_key(key)] = value
  end

  def send(message, move: false)
    @default_port.send(message)
    self
  end
  alias_method :<<, :send

  def close
    unless Primitive.equal?(self, Ractor.current)
      raise Error, 'closing port by other ractors is not allowed'
    end
    @default_port.close
  end

  # Returns false and sends the termination message to the port immediately if already terminated.
  def monitor(port)
    registered = LOCK.synchronize do
      @monitors << port unless @terminated
      !@terminated
    end
    notify(port) unless registered
    registered
  end

  def unmonitor(port)
    LOCK.synchronize { @monitors.delete(port) }
    self
  end

  def value
    @thread.join
    result
  end

  def join
    value
    self
  end

  def inspect
    status = @terminated ? 'terminated' : 'running'
    ["#<Ractor:##{@id}", @name, @source_location, "#{status}>"].compact.join(' ')
  end
  alias_method :to_s, :inspect

  private

  def setup(id, name, source_location, thread = nil)
    @id = id
    @name = name
    @source_location = source_location
    @thread = thread
    @default_port = Port.new
    @storage = {}
    @monitors = []
    @terminated = false
    @exception = nil
    @result = nil
  end

  # Runs in the Thread of this Ractor
  def run(args, block)
    Primitive.object_hidden_var_set(Thread.current, Truffle::ThreadOperations::RACTOR_KEY, self)
    @result = instance_exec(*args, &block)
  rescue Exception => e # rubocop:disable Lint/RescueException
    # SystemExit must not propagate, it would exit the whole process
    @exception = e
    if Thread.current.report_on_exception && !Primitive.is_a?(e, SystemExit)
      Truffle::ThreadOperations.report_exception(Thread.current, e)
    end
  ensure
    terminated
  end

  def terminated
    Ractor.__send__(:finished)
    @default_port.close
    monitors = LOCK.synchronize do
      @terminated = true
      CONDITION.broadcast
      @monitors
    end
    monitors.each { |port| notify(port) }
  end

  def terminated?
    @terminated
  end

  def notify(port)
    port.send(@exception ? :aborted : :exited)
  rescue ClosedError
    # a closed monitor port is ignored
  end

  # Only valid once terminated
  def result
    raise RemoteError.new(self), cause: @exception if @exception
    @result
  end

  def receive
    @default_port.receive
  end
  alias_method :recv, :receive

  def store_if_absent(key)
    key = storage_key(key)
    # Hash is thread-safe, synchronize only to not call the block twice
    TruffleRuby.synchronized(@storage) do
      @storage.fetch(key) { @storage[key] = yield }
    end
  end

  def storage_key(key)
    if Primitive.is_a?(key, Symbol)
      key
    elsif Primitive.is_a?(key, String)
      key.to_sym
    else
      raise TypeError, "#{key.inspect} is not a symbol nor a string"
    end
  end

  @count = 1
  @next_id = 1
  @main = allocate
  @main.__send__(:setup, 1, nil, nil, Thread.main)
end
