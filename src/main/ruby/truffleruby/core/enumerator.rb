# frozen_string_literal: true

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

class Enumerator
  include Enumerable

  attr_writer :args, :kwargs, :size
  private :args=, :kwargs=, :size=

  private def initialize_enumerator(receiver, size, method_name, *method_args, **method_kwargs)
    @object = receiver
    @size = size
    @iter = method_name
    @args = method_args
    @kwargs = method_kwargs
    @generator = nil
    @lookahead = []
    @feedvalue = nil

    self
  end

  private def initialize(receiver_or_size = undefined, method_name = :each, *method_args, **method_kwargs, &block)
    size = nil

    if block_given?
      unless Primitive.undefined? receiver_or_size
        size = receiver_or_size
      end

      receiver = Generator.new(&block)
    else
      if Primitive.undefined? receiver_or_size
        raise ArgumentError, 'Enumerator#initialize requires a block when called without arguments'
      end

      receiver = receiver_or_size
    end

    method_name = Truffle::Type.coerce_to method_name, Symbol, :to_sym

    initialize_enumerator receiver, size, method_name, *method_args, **method_kwargs

    self
  end

  def inspect
    return "#<#{Primitive.class(self)}: uninitialized>" if Primitive.nil?(@object) && Primitive.nil?(@iter)

    args = @kwargs.empty? ? @args : [*@args, @kwargs]
    args = args.empty? ? '' : "(#{args.map(&:inspect).join(', ')})"
    "#<#{Primitive.class(self)}: #{@object.inspect}:#{@iter}#{args}>"
  end

  def each(*args, **kwargs, &block)
    enumerator = self
    unless args.empty?
      enumerator = dup
      new_args = @args.empty? ? args : (@args + args)
      enumerator.__send__ :args=, new_args
    end

    unless kwargs.empty?
      enumerator = dup
      new_kwargs = @kwargs.empty? ? kwargs : @kwargs.merge(kwargs)
      enumerator.__send__ :kwargs=, new_kwargs
    end

    if block
      Primitive.share_special_variables(Primitive.proc_special_variables(block))
      enumerator.__send__(:each_with_block) { |*yield_args| yield(*yield_args) }

    else
      enumerator
    end
  end

  def each_with_block(&block)
    Primitive.share_special_variables(Primitive.proc_special_variables(block))
    @object.__send__(@iter, *@args, **@kwargs) do |*args|
      ret = yield(*args)
      unless Primitive.nil? @feedvalue
        ret = @feedvalue
        @feedvalue = nil
      end
      ret
    end
  end
  private :each_with_block

  def each_with_index
    return to_enum(:each_with_index) { size } unless block_given?

    idx = 0

    each do
      o = Primitive.single_block_arg
      val = yield(o, idx)
      idx += 1
      val
    end
  end

  def each_with_object(memo)
    return to_enum(:each_with_object, memo) { size } unless block_given?

    each do
      obj = Primitive.single_block_arg
      yield obj, memo
    end
    memo
  end
  alias_method :with_object, :each_with_object

  def next
    return @lookahead.shift unless @lookahead.empty?

    unless @generator
      # Allow #to_generator to return nil, indicating it has none for
      # this method.
      if @object.respond_to? :to_generator
        @generator = @object.to_generator(@iter)
      end

      if !@generator
        @generator = FiberGenerator.new(self)
      end
    end

    begin
      return @generator.next if @generator.next?
    rescue StopIteration
      nil # the enumerator could change between next? and next leading to StopIteration
    end

    exception = StopIteration.new 'iteration reached end'
    exception.__send__ :result=, @generator.result

    raise exception
  end

  def next_values
    Array(self.next)
  end

  def peek
    return @lookahead.first unless @lookahead.empty?
    item = self.next
    @lookahead << item
    item
  end

  def peek_values
    Array(self.peek)
  end

  def rewind
    @object.rewind if @object.respond_to? :rewind
    @generator.rewind if @generator
    @lookahead = []
    @feedvalue = nil
    self
  end

  def size
    @size.respond_to?(:call) ? @size.call(*@args, **@kwargs) : @size
  end

  def with_index(offset = 0)
    if offset
      offset = Truffle::Type.coerce_to offset, Integer, :to_int
    else
      offset = 0
    end

    return to_enum(:with_index, offset) { size } unless block_given?

    each do
      o = Primitive.single_block_arg
      val = yield(o, offset)
      offset += 1
      val
    end
  end

  def feed(val)
    raise TypeError, 'Feed value already set' unless Primitive.nil? @feedvalue
    @feedvalue = val
    nil
  end

  def +(other)
    Enumerator::Chain.new(self, other)
  end

  def self.produce(initial = nil)
    # Taken from https://github.com/zverok/enumerator_generate
    raise ArgumentError, 'No block given' unless block_given?
    Enumerator.new(Float::INFINITY) do |y|
      val = Primitive.nil?(initial) ? yield() : initial

      loop do
        y << val
        val = yield(val)
      end
    end
  end

  def self.product(*enums, **nil, &block)
    return Product.new(*enums) if Primitive.nil?(block)
    Truffle::EnumeratorOperations.product_iterator([], enums, &block)
    nil
  end

  class Product < Enumerator
    def initialize(*enums, **nil)
      @enums = enums

      self
    end

    def initialize_copy(product)
      return self if Primitive.equal?(self, product)

      if Primitive.class(self) != Primitive.class(product)
        raise TypeError, 'initialize_copy should take same class object'
      end

      unless product.instance_variable_defined?(:@enums)
        raise ArgumentError, 'uninitialized product'
      end

      # don't check explicitly whether self is frozen
      # because assigning @enum instance variable will raise FrozenError in this case anyway
      @enums = product.instance_variable_get(:@enums)

      self
    end

    def each(&block)
      return to_enum(:each) { size } unless block_given?

      Truffle::EnumeratorOperations.product_iterator([], @enums, &block)

      self
    end

    def inspect
      "#<#{Primitive.class(self).name}: ...>" if Truffle::ThreadOperations.detect_recursion(self) do
        return "#<#{Primitive.class(self).name}: #{@enums || "uninitialized"}>"
      end
    end

    def rewind
      @enums.each do |e|
        e.rewind if e.respond_to?(:rewind)
      end

      self
    end

    def size
      @enums.map do |enum|
        return nil unless enum.respond_to?(:size)

        size = enum.size

        return size if Primitive.is_a?(size, Float) && size.infinite?
        return nil unless Primitive.is_a?(size, Integer)

        size
      end.reduce(1, &:*)
    end
  end

  class Yielder
    attr_accessor :memo

    def initialize(&block)
      raise LocalJumpError, 'Expected a block to be given' unless block_given?

      @proc = block
      @memo = nil

      self
    end

    def yield(*args, **kwargs)
      @proc.call(*args, **kwargs)
    end

    def <<(*args, **kwargs)
      self.yield(*args, **kwargs)

      self
    end

    def to_proc
      self.method(:yield).to_proc
    end
  end

  class Generator
    include Enumerable
    def initialize(&block)
      raise LocalJumpError, 'Expected a block to be given' unless block_given?

      @proc = block

      self
    end

    def each(*args, **kwargs, &block)
      Primitive.share_special_variables(Primitive.proc_special_variables(block)) if block

      yielder = Yielder.new do |*enclosed_args, **enclosed_kwargs|
        yield(*enclosed_args, **enclosed_kwargs)
      end

      @proc.call yielder, *args, **kwargs
    end
  end

  class Lazy < Enumerator

    aliases = Truffle::EnumeratorOperations::LAZY_OVERRIDE_METHODS.map do |m|
      name = :"_enumerable_#{m}"
      alias_method name, :"#{m}"
      name
    end
    private(*aliases)

    class StopLazyError < Exception # rubocop:disable Lint/InheritException
    end

    def initialize(receiver, size = nil)
      raise ArgumentError, 'Lazy#initialize requires a block' unless block_given?
      Primitive.check_frozen self

      super(size) do |yielder, *each_args|
        begin
          receiver.each(*each_args) do |*args|
            yield yielder, *args
          end
        rescue StopLazyError
          nil
        end
      end

      self
    end

    def to_enum(method_name = :each, *method_args, **method_kwargs, &block)
      size = block_given? ? block : nil
      ret = Lazy.allocate
      method_name = Truffle::EnumeratorOperations.lazy_method(method_name)

      ret.__send__ :initialize_enumerator, self, size, method_name, *method_args, **method_kwargs

      ret
    end
    alias_method :enum_for, :to_enum

    def lazy
      self
    end

    def eager
      Enumerator.instance_method(:enum_for).bind(self).call(:each) { size }
    end

    # TODO: rewind and/or to_a/force behave improperly on outputs of take, drop, uniq, possibly more

    alias_method :force, :to_a

    def take(n)
      n = Truffle::Type.coerce_to n, Integer, :to_int
      raise ArgumentError, 'attempt to take negative size' if n < 0

      current_size = enumerator_size
      if Primitive.is_a?(current_size, Numeric)
        # Not Primitive.min since current_size is not always an Integer
        set_size = n < current_size ? n : current_size
      else
        set_size = current_size
      end

      return to_enum(:cycle, 0).lazy if n.zero?

      taken = 0
      Lazy.new(self, set_size) do |yielder, *args|
        if taken < n
          yielder.yield(*args)
          taken += 1
          raise StopLazyError unless taken < n
        else
          raise StopLazyError
        end
      end
    end

    def drop(n)
      n = Truffle::Type.coerce_to n, Integer, :to_int
      raise ArgumentError, 'attempt to drop negative size' if n < 0

      current_size = enumerator_size
      if Primitive.is_a?(current_size, Integer)
        set_size = n < current_size ? current_size - n : 0
      else
        set_size = current_size
      end

      dropped = 0
      Lazy.new(self, set_size) do |yielder, *args|
        if dropped < n
          dropped += 1
        else
          yielder.yield(*args)
        end
      end
    end

    def take_while
      raise ArgumentError, 'Lazy#take_while requires a block' unless block_given?

      Lazy.new(self, nil) do |yielder, *args|
        if yield(*args)
          yielder.yield(*args)
        else
          raise StopLazyError
        end
      end
    end

    def drop_while
      raise ArgumentError, 'Lazy#drop_while requires a block' unless block_given?

      succeeding = true
      Lazy.new(self, nil) do |yielder, *args|
        if succeeding
          unless yield(*args)
            succeeding = false
            yielder.yield(*args)
          end
        else
          yielder.yield(*args)
        end
      end
    end

    def filter_map
      raise ArgumentError, 'Lazy#filter_map requires a block' unless block_given?

      Lazy.new(self, enumerator_size) do |yielder, *args|
        result = yield(*args)
        yielder.yield result if result
      end
    end

    def select
      raise ArgumentError, 'Lazy#{select,find_all} requires a block' unless block_given?

      Lazy.new(self, nil) do |yielder, *args|
        val = args.length >= 2 ? args : args.first
        yielder.yield(*args) if yield(val)
      end
    end
    alias_method :find_all, :select
    alias_method :filter, :select

    def reject
      raise ArgumentError, 'Lazy#reject requires a block' unless block_given?

      Lazy.new(self, nil) do |yielder, *args|
        val = args.length >= 2 ? args : args.first
        yielder.yield(*args) unless yield(val)
      end
    end

    def grep(pattern, &block)
      sv = block ? Primitive.proc_special_variables(block) : Primitive.caller_special_variables

      Lazy.new(self, nil) do |yielder, *args|
        Primitive.share_special_variables(sv)
        val = args.length >= 2 ? args : args.first
        matches = pattern === val

        if matches
          if block
            yielder.yield yield(val)
          else
            yielder.yield val
          end
        end
      end
    end

    def grep_v(pattern, &block)
      s = block ? Primitive.proc_special_variables(block) : Primitive.caller_special_variables

      Lazy.new(self, nil) do |yielder, *args|
        val = args.length >= 2 ? args : args.first
        matches = pattern === val
        Primitive.regexp_last_match_set(s, $~)

        unless matches
          if block
            yielder.yield yield(val)
          else
            yielder.yield val
          end
        end
      end
    end

    def map
      raise ArgumentError, 'Lazy#{map,collect} requires a block' unless block_given?

      Lazy.new(self, enumerator_size) do |yielder, *args|
        yielder.yield yield(*args)
      end
    end
    alias_method :collect, :map

    def flat_map
      raise ArgumentError, 'Lazy#{collect_concat,flat_map} requires a block' unless block_given?

      Lazy.new(self, nil) do |yielder, *args|
        yield_ret = yield(*args)

        if Primitive.respond_to?(yield_ret, :force, false) &&
           Primitive.respond_to?(yield_ret, :each, false)
          yield_ret.each do |v|
            yielder.yield v
          end
        else
          array = Truffle::Type.rb_check_convert_type yield_ret, Array, :to_ary
          if array
            array.each do |v|
              yielder.yield v
            end
          else
            yielder.yield yield_ret
          end
        end
      end
    end
    alias_method :collect_concat, :flat_map

    def with_index(offset = 0, &block)
      offset = if Primitive.nil?(offset)
                 0
               else
                 offset
               end

      Lazy.new(self, enumerator_size) do |yielder, *args|
        memo = yielder.memo || offset
        if block
          yielder.yield yield(*args, memo)
        else
          yielder.yield(*args, memo)
        end
        yielder.memo = Primitive.rb_num2long(memo) + 1
      end
    end

    # Synchronize with Enumerable#zip and Array#zip
    def zip(*lists)
      return super(*lists) { |entry| yield entry } if block_given?

      lists.map! do |list|
        array = Truffle::Type.rb_check_convert_type list, Array, :to_ary

        if array
          array
        elsif Primitive.respond_to?(list, :each, false)
          list.to_enum :each
        else
          raise TypeError, "wrong argument type #{Primitive.class(list)} (must respond to :each)"
        end
      end

      index = 0
      Lazy.new(self, enumerator_size) do |yielder, *args|
        val = args.length >= 2 ? args : args.first
        rests = lists.map do |list|
          case list
          when Array
            list[index]
          else
            begin
              list.next
            rescue StopIteration
              nil
            end
          end
        end
        yielder.yield [val, *rests]
        index += 1
      end
    end

    def chunk(&block)
      super(&block).lazy
    end

    def chunk_while(&block)
      super(&block).lazy
    end

    def compact
      Lazy.new(self, nil) do |yielder, e|
        yielder.yield(e) unless Primitive.nil?(e)
      end
    end

    def slice_after(&block)
      super(&block).lazy
    end

    def slice_before(&block)
      super(&block).lazy
    end

    def slice_when(&block)
      super(&block).lazy
    end

    def uniq
      if block_given?
        h = {}
        Lazy.new(self, nil) do |yielder, *args|
          val = args.length >= 2 ? args : args.first
          comp = yield(val)
          unless h.key?(comp)
            h[comp] = true
            yielder.yield(*args)
          end
        end
      else
        h = {}
        Lazy.new(self, nil) do |yielder, *args|
          val = args.length >= 2 ? args : args.first
          unless h.key?(val)
            h[val] = true
            yielder.yield(*args)
          end
        end
      end
    end
  end

  class FiberGenerator
    attr_reader :result

    def initialize(obj)
      @object = obj
      rewind
    end

    def next?
      !@done
    end

    def next
      reset unless @fiber

      val = @fiber.resume

      raise StopIteration, 'iteration has ended' if @done

      val
    end

    def rewind
      @fiber = nil
      @done = false
    end

    def reset
      @done = false
      @fiber = Fiber.new do
        obj = @object
        @result = obj.each do |*val|
          Fiber.yield(*val)
        end
        @done = true
      end
    end
  end
end

class Enumerator::ArithmeticSequence < Enumerator

  def initialize(obj, method_name, enum_begin, enum_end, step, exclude_end)
    @begin = enum_begin
    @end =  enum_end
    @step = step
    @exclude_end = exclude_end
    super(obj, method_name)
  end

  attr_reader :begin, :end, :step

  def exclude_end?
    @exclude_end
  end

  def last(n = undefined)
    from, to, step, exclude_end  = @begin, @end, @step, @exclude_end

    raise RangeError, 'cannot get the last element of endless arithmetic sequence' if Primitive.nil? to

    len = (to - from).div(step)
    if len.negative?
      return Primitive.undefined?(n) ? nil : []
    end
    last = from + (step * len)
    if exclude_end && last == to
      last = last - step
    end

    return last if Primitive.undefined?(n)

    n = Primitive.rb_to_int(n) if !Primitive.is_a?(n, Integer)

    raise ArgumentError, 'negative array size' if n < 0

    ary = Array.new
    last.step(first, -step) do |e|
      ary.unshift(e)
      break if ary.size == n
    end
    ary
  end

  def inspect
    if Primitive.is_a?(@object, Range)
      step = @step == 1 ? '' : "(#{@step})"
      to = @end.to_s
      exclude_end = exclude_end? ? '.' : ''
      "((#{@begin}..#{exclude_end}#{to}).step#{step})"
    else
      if @step == 1
        if Primitive.nil? @end
          "(#{@begin}.step)"
        else
          "(#{@begin}.step(#{@end}))"
        end
      else
        "(#{@begin}.step(#{@end}, #{@step}))"
      end
    end
  end

  def ==(other)
    Primitive.is_a?(other, Enumerator::ArithmeticSequence) &&
        @begin == other.begin &&
        @end == other.end &&
        @exclude_end == other.exclude_end? &&
        @step == other.step
  end
  alias_method :===, :==
  alias_method :eql?, :==

  def hash
    val = Primitive.vm_hash_start(@exclude_end ? 1 : 0)
    val = Primitive.vm_hash_update val, @begin.hash
    val = Primitive.vm_hash_update val, @end.hash
    val = Primitive.vm_hash_update val, @step.hash
    Primitive.vm_hash_end val
  end

  def each(&block)
    return self if Primitive.nil? block
    Primitive.share_special_variables(Primitive.proc_special_variables(block))
    from, to, step, exclude_end  = @begin, @end, @step, @exclude_end
    from.step(to: to, by: step) do |val|
      break if exclude_end && (step.negative? ? val <= to : val >= to)
      yield val
    end
    self
  end

  def size
    from, to, step, exclude_end  = @begin, @end, @step, @exclude_end
    unless Primitive.is_a?(from, Float) || Primitive.is_a?(to, Float) || Primitive.is_a?(step, Float)
      step = Primitive.rb_to_int(step)
    end
    Truffle::NumericOperations.step_size(from, to, step, true, exclude_end)
  end
end

class Enumerator::Chain < Enumerator
  def initialize(*args, &block)
    Primitive.check_frozen self
    @enums = args.freeze
    @pos = -1
    self
  end

  def each
    return to_enum :each unless block_given?
    @enums.each_with_index do |enum, idx|
      @pos = idx
      enum.each do |*args|
        yield(*args)
      end
    end
  end

  def size
    total = 0
    @enums.each do |e|
      size = e.size
      if Primitive.nil?(size) || (Primitive.is_a?(size, Float) && size.infinite?)
        return size
      end
      unless Primitive.is_a?(size, Integer)
        return nil
      end
      total += size
    end
    total
  end

  def rewind
    while @pos >= 0
      Truffle::Type.check_funcall(@enums[@pos], :rewind)
      @pos -= 1
    end
    self
  end

  def inspect
    "#<#{Primitive.class(self).name}: ...>" if Truffle::ThreadOperations.detect_recursion(self) do
      return "#<#{Primitive.class(self).name}: #{@enums || "uninitialized"}>"
    end
  end
end
