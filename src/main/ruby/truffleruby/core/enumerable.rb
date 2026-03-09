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

# Just to save you 10 seconds, the reason we always use #each to extract
# elements instead of something simpler is because Enumerable can not assume
# any other methods than #each. If needed, class-specific versions of any of
# these methods can be written *in those classes* to override these.

# TODO (eregon, 1 Sep 2022): should we always_split all methods calling a block also in Array/Hash? Maybe by detecting yield/block.call in the parser?

module Enumerable
  def chunk(&original_block)
    return to_enum(:chunk) { enumerator_size } unless block_given?

    initial_state = nil
    Enumerator.new do |yielder|
      previous = nil
      accumulate = []
      if Primitive.nil? initial_state
        block = original_block
      else
        duplicated_initial_state = initial_state.dup
        block = Proc.new { |val| original_block.yield(val, duplicated_initial_state) }
      end
      each do |val|
        key = block.yield(val)
        if Primitive.nil?(key) || (Primitive.is_a?(key, Symbol) && key.to_s[0, 1] == '_')
          yielder.yield [previous, accumulate] unless accumulate.empty?
          accumulate = []
          previous = nil
          case key
          when nil, :_separator
          when :_alone
            yielder.yield [key, [val]]
          else
            raise RuntimeError, 'symbols beginning with an underscore are reserved'
          end
        else
          if Primitive.nil?(previous) || previous == key
            accumulate << val
          else
            yielder.yield [previous, accumulate] unless accumulate.empty?
            accumulate = [val]
          end
          previous = key
        end
      end
      yielder.yield [previous, accumulate] unless accumulate.empty?
    end
  end

  def chunk_while(&block)
    raise ArgumentError, 'tried to create Proc object without a block' unless block
    Enumerator.new do |yielder|
      accumulator = nil
      prev = nil
      each do |*elem|
        elem = elem[0] if elem.size == 1
        if Primitive.nil? accumulator
          accumulator = [elem]
          prev = elem
        else
          start_new = block.yield(prev, elem)
          if !start_new
            yielder.yield accumulator if accumulator
            accumulator = [elem]
          else
            accumulator << elem
          end
          prev = elem
        end
      end
      yielder.yield accumulator if accumulator
    end
  end

  def map(&block)
    if block
      return Primitive.hash_collect(self, block) if Primitive.is_a?(self, Hash) && self.method(:each).owner == Hash
      ary = []
      Primitive.share_special_variables(Primitive.proc_special_variables(block))
      b = Primitive.proc_create_same_arity(block, -> *o { ary << yield(*o) })
      each(&b)
      ary
    else
      to_enum(:collect) { enumerator_size }
    end
  end
  Primitive.always_split self, :map
  alias_method :collect, :map

  def compact
    array = []

    each do
      e = Primitive.single_block_arg
      array << e unless Primitive.nil?(e)
    end

    array
  end

  def count(item = undefined)
    seq = 0
    if !Primitive.undefined?(item)
      Primitive.warn_given_block_not_used if block_given?
      each do
        o = Primitive.single_block_arg
        seq += 1 if item == o
      end
    elsif block_given?
      each { |o| seq += 1 if yield(o) }
    else
      each { seq += 1 }
    end
    seq
  end

  def each_entry(*pass)
    return to_enum(:each_entry, *pass) { enumerator_size } unless block_given?
    each(*pass) do |*args|
      yield args.size == 1 ? args[0] : args
    end
    self
  end

  def each_with_object(memo)
    return to_enum(:each_with_object, memo) { enumerator_size } unless block_given?
    each do
      yield Primitive.single_block_arg, memo
    end
    memo
  end

  def flat_map
    return to_enum(:flat_map) { enumerator_size } unless block_given?

    array = []
    each do |*args|
      result = yield(*args)

      value = Truffle::Type.try_convert(result, Array, :to_ary) || result

      if Primitive.is_a?(value, Array)
        array.concat value
      else
        array.push value
      end
    end

    array
  end

  alias_method :collect_concat, :flat_map

  def lazy
    Enumerator::Lazy.new(self, enumerator_size) do |yielder, *args|
      yielder.<<(*args)
    end
  end

  def enumerator_size
    Primitive.respond_to?(self, :size, false) ? size : nil
  end
  private :enumerator_size

  def group_by
    return to_enum(:group_by) { enumerator_size } unless block_given?

    h = {}
    each do
      o = Primitive.single_block_arg
      key = yield(o)
      if h.key?(key)
        h[key] << o
      else
        h[key] = [o]
      end
    end
    h
  end

  def slice_after(arg = undefined, &block)
    has_arg = !(Primitive.undefined? arg)
    if block_given?
      raise ArgumentError, 'both pattern and block are given' if has_arg
    else
      raise ArgumentError, 'wrong number of arguments (0 for 1)' unless has_arg
      block = Proc.new { |elem| arg === elem }
    end
    Enumerator.new do |yielder|
      accumulator = nil
      each do |*elem|
        elem = elem[0] if elem.size == 1
        end_slice = block.yield(elem)
        accumulator ||= []
        if end_slice
          accumulator << elem
          yielder.yield accumulator
          accumulator = nil
        else
          accumulator << elem
        end
      end
      yielder.yield accumulator if accumulator
    end
  end

  def slice_before(arg = undefined, &block)
    if block_given?
      has_init = !(Primitive.undefined? arg)
      if has_init
        raise ArgumentError, 'both pattern and block are given'
      end
    else
      raise ArgumentError, 'wrong number of arguments (0 for 1)' if Primitive.undefined? arg
      block = Proc.new { |elem| arg === elem }
    end
    Enumerator.new do |yielder|
      init = arg.dup if has_init
      accumulator = nil
      each do |*elem|
        elem = elem[0] if elem.size == 1
        start_new = has_init ? block.yield(elem, init) : block.yield(elem)
        if start_new
          yielder.yield accumulator if accumulator
          accumulator = [elem]
        else
          accumulator ||= []
          accumulator << elem
        end
      end
      yielder.yield accumulator if accumulator
    end
  end

  def slice_when(&block)
    raise ArgumentError, 'tried to create Proc object without a block' unless block
    Enumerator.new do |yielder|
      accumulator = nil
      prev = nil
      each do |*elem|
        elem = elem[0] if elem.size == 1
        if Primitive.nil? accumulator
          accumulator = [elem]
          prev = elem
        else
          start_new = block.yield(prev, elem)
          if start_new
            yielder.yield accumulator if accumulator
            accumulator = [elem]
          else
            accumulator << elem
          end
          prev = elem
        end
      end
      yielder.yield accumulator if accumulator
    end
  end

  def tally(hash = undefined)
    if Primitive.undefined?(hash)
      hash = {}
    else
      hash = Primitive.convert_with_to_hash(hash)
      Primitive.check_frozen(hash)
    end

    each do
      e = Primitive.single_block_arg
      if hash.key?(e)
        hash[e] += 1
      else
        hash[e] = 1
      end
    end
    hash
  end

  def to_a(*args, **kwargs)
    ary = []
    each(*args, **kwargs) do
      o = Primitive.single_block_arg
      ary << o
      nil
    end
    ary
  end
  alias_method :entries, :to_a

  def to_h(*args)
    h = {}
    each(*args) do
      elem = Primitive.single_block_arg
      elem = yield(elem) if block_given?
      Truffle::HashOperations.assoc_key_value_pair(h, elem)
    end
    h
  end

  def to_set(klass = Set, ...)
    klass.new(self, ...)
  end

  # Synchronize with Enumerator#zip and Array#zip
  def zip(*enums)
    enums.map! do |enum|
      array = Truffle::Type.rb_check_convert_type(enum, Array, :to_ary)

      if array
        array
      elsif Primitive.respond_to?(enum, :each, false)
        enum.to_enum(:each)
      else
        raise TypeError, "wrong argument type #{Primitive.class(enum)} (must respond to :each)"
      end
    end

    results = []
    i = 0
    each do
      o = Primitive.single_block_arg
      entry = enums.inject([o]) do |ary, a|
        ary << case a
               when Array
                 a[i]
               else
                 begin
                   a.next
                 rescue StopIteration
                   nil
                 end
               end
      end

      yield entry if block_given?

      results << entry
      i += 1
    end

    return nil if block_given?
    results
  end

  def each_with_index(*args, &block)
    return to_enum(:each_with_index, *args) { enumerator_size } unless block_given?

    if Primitive.is_a?(self, Array)
      Primitive.array_each_with_index(self, block)
    else
      idx = 0
      each(*args) do
        o = Primitive.single_block_arg
        yield o, idx
        idx += 1
      end

      self
    end
  end
  Primitive.always_split self, :each_with_index

  def grep(pattern, &block)
    ary = []

    if block_given?
      Primitive.share_special_variables(Primitive.proc_special_variables(block))
      each do
        o = Primitive.single_block_arg
        matches = pattern === o
        if matches
          ary << yield(o)
        end
      end
    else
      if Primitive.is_a?(pattern, Regexp)
        each do
          o = Primitive.single_block_arg
          matchable =
            if Primitive.is_a?(o, Symbol)
              o
            else
              Truffle::Type.rb_check_convert_type(o, String, :to_str)
            end
          if matchable && pattern.match?(matchable)
            ary << o
          end
        end
      else
        each do
          o = Primitive.single_block_arg
          if pattern === o
            ary << o
          end
        end
      end
    end

    ary
  end

  def grep_v(pattern, &block)
    ary = []

    if block_given?
      each do
        o = Primitive.single_block_arg
        matches = pattern === o
        Primitive.regexp_last_match_set(Primitive.proc_special_variables(block), $~)
        unless matches
          ary << yield(o)
        end
      end
    else
      if Primitive.is_a?(pattern, Regexp)
        each do
          o = Primitive.single_block_arg
          matchable =
            if Primitive.is_a?(o, Symbol)
              o
            else
              Truffle::Type.rb_check_convert_type(o, String, :to_str)
            end
          unless matchable && pattern.match?(matchable)
            ary << o
          end
        end
      else
        each do
          o = Primitive.single_block_arg
          unless pattern === o
            ary << o
          end
        end
      end
    end

    ary
  end

  def sort(&prc)
    ary = to_a
    ary.frozen? ? ary.sort(&prc) : ary.sort!(&prc)
  end

  class SortedElement
    def initialize(val, sort_id)
      @value, @sort_id = val, sort_id
    end

    attr_reader :value
    attr_reader :sort_id

    def <=>(other)
      @sort_id <=> other.sort_id
    end
  end

  def sort_by
    return to_enum(:sort_by) { enumerator_size } unless block_given?

    # Transform each value to a tuple with the value and it's sort by value
    case self
    when Array
      sort_values = map do |x|
        SortedElement.new(x, yield(x))
      end
    else
      sort_values = []
      each do |*x|
        x = x[0] if x.size == 1
        sort_values << SortedElement.new(x, yield(x))
      end
    end

    # Now sort the tuple according to the sort by value
    sort_values.sort!

    # Now strip of the tuple leaving the original value
    sort_values.map! { |ary| ary.value }
  end

  # Enumerable#inject
  #   inject(symbol) -> object
  #   inject(initial_operand, symbol) -> object
  #   inject {|memo, operand| ... } -> object
  #   inject(initial_operand) {|memo, operand| ... } -> object
  def inject(initial = undefined, sym = undefined, &block)
    # inject()
    if !block && Primitive.undefined?(initial)
      raise ArgumentError, 'no block or symbol given'
    end

    # inject(initial_operand, symbol) { ... }
    if block && !Primitive.undefined?(sym)
      Primitive.warn_given_block_not_used
      block = nil
    end

    if Primitive.is_a?(self, ::Array)
      return Primitive.array_inject(self, initial, sym, block)
    end

    if Primitive.undefined?(sym) && block
      # Do the block version:
      #   inject {|memo, operand| ... } -> object
      #   inject(initial_operand) {|memo, operand| ... } -> object

      each do
        o = Primitive.single_block_arg
        if Primitive.undefined? initial
          initial = o
        else
          initial = yield(initial, o)
        end
      end
    else
      # Do the sym version:
      #   inject(symbol) -> object
      #   inject(initial_operand, symbol) -> object

      sym, initial = initial, undefined if Primitive.undefined?(sym)
      sym = Truffle::Type.coerce_to_symbol(sym)

      each do
        o = Primitive.single_block_arg
        if Primitive.undefined? initial
          initial = o
        else
          initial = initial.__send__(sym, o)
        end
      end
    end

    Primitive.undefined?(initial) ? nil : initial
  end
  Primitive.always_split self, :inject
  alias_method :reduce, :inject

  def all?(pattern = undefined)
    if !Primitive.undefined?(pattern)
      Primitive.warn_given_block_not_used if block_given?
      each { return false unless pattern === Primitive.single_block_arg }
    elsif block_given?
      each { |*e| return false unless yield(*e) }
    else
      each { return false unless Primitive.single_block_arg }
    end
    true
  end

  def any?(pattern = undefined)
    if !Primitive.undefined?(pattern)
      Primitive.warn_given_block_not_used if block_given?
      each { return true if pattern === Primitive.single_block_arg }
    elsif block_given?
      each { |*o| return true if yield(*o) }
    else
      each { return true if Primitive.single_block_arg }
    end
    false
  end

  def cycle(many = nil)
    unless block_given?
      return to_enum(:cycle, many) do
        Truffle::EnumerableOperations.cycle_size(enumerator_size, many)
      end
    end

    if many
      many = Primitive.rb_num2int many
      return nil if many <= 0
    else
      many = nil
    end

    cache = []
    each do
      elem = Primitive.single_block_arg
      cache << elem
      yield elem
    end

    return nil if cache.empty?

    if many
      i = 0
      many -= 1
      while i < many
        cache.each { |o| yield o }
        i += 1
      end
    else
      while true
        cache.each { |o| yield o }
      end
    end

    nil
  end

  def drop(n)
    n = Primitive.rb_num2int n
    raise ArgumentError, 'attempt to drop negative size' if n < 0

    ary = to_a
    return [] if n > ary.size
    ary[n...ary.size]
  end

  def drop_while
    return to_enum(:drop_while) unless block_given?

    ary = []
    dropping = true
    each do
      obj = Primitive.single_block_arg
      ary << obj unless dropping &&= yield(obj)
    end

    ary
  end

  def each_cons(num)
    n = Primitive.rb_num2int num
    raise ArgumentError, "invalid size: #{n}" if n <= 0

    unless block_given?
      return to_enum(:each_cons, num) do
        enum_size = enumerator_size
        if Primitive.nil? enum_size
          nil
        elsif enum_size == 0 || enum_size < n
          0
        else
          enum_size - n + 1
        end
      end
    end

    array = []
    each do
      element = Primitive.single_block_arg
      array << element
      array.shift if array.size > n
      yield array.dup if array.size == n
    end
    self
  end

  def each_slice(slice_size)
    n = Primitive.rb_num2int slice_size
    raise ArgumentError, "invalid slice size: #{n}" if n <= 0

    unless block_given?
      return to_enum(:each_slice, slice_size) do
        enum_size = enumerator_size
        Primitive.nil?(enum_size) ? nil : (enum_size.to_f / n).ceil
      end
    end

    a = []
    each do
      element = Primitive.single_block_arg
      a << element
      if a.length == n
        yield a
        a = []
      end
    end

    yield a unless a.empty?
    self
  end

  def find(ifnone = nil)
    return to_enum(:find, ifnone) unless block_given?

    each do
      o = Primitive.single_block_arg
      return o if yield(o)
    end

    ifnone.call if ifnone
  end

  alias_method :detect, :find

  def find_all
    return to_enum(:find_all) { enumerator_size } unless block_given?

    ary = []
    each do
      o = Primitive.single_block_arg
      ary << o if yield(o)
    end
    ary
  end

  alias_method :select, :find_all
  alias_method :filter, :find_all

  def find_index(value = undefined)
    if Primitive.undefined? value
      return to_enum(:find_index) unless block_given?

      i = 0
      each do |*args|
        return i if yield(*args)
        i += 1
      end
    else
      Primitive.warn_given_block_not_used if block_given?

      i = 0
      each do
        e = Primitive.single_block_arg
        return i if e == value
        i += 1
      end
    end
    nil
  end

  def first(n = undefined)
    return __take__(n) unless Primitive.undefined?(n)
    each do
      o = Primitive.single_block_arg
      return o
    end
    nil
  end

  def min(n = undefined, &block)
    return min_n(n, &block) if !Primitive.undefined?(n) && !Primitive.nil?(n)
    min_max(-1, &block)
  end

  def min_n(n, &block)
    raise ArgumentError, "negative size #{n}" if n < 0
    return [] if n == 0

    self.sort(&block).first(n)
  end
  private :min_n

  def max(n = undefined, &block)
    return max_n(n, &block) if !Primitive.undefined?(n) && !Primitive.nil?(n)
    min_max(+1, &block)
  end

  def max_n(n, &block)
    raise ArgumentError, "negative size #{n}" if n < 0
    return [] if n == 0

    self.sort(&block).last(n).reverse
  end
  private :max_n

  def min_max(relative)
    chosen = undefined
    each do
      o = Primitive.single_block_arg
      if Primitive.undefined? chosen
        chosen = o
      else
        comp = block_given? ? yield(o, chosen) : o <=> chosen
        unless comp
          raise ArgumentError, "comparison of #{Primitive.class(o)} with #{chosen} failed"
        end

        if (Comparable.compare_int(comp) <=> 0) == relative
          chosen = o
        end
      end
    end

    Primitive.undefined?(chosen) ? nil : chosen
  end
  private :min_max

  private def max_by_n(n, &block)
    n = Primitive.rb_num2long(n)
    raise ArgumentError, "negative size #{n}" if n < 0
    return [] if n == 0
    self.sort_by(&block).last(n).reverse
  end

  def max_by(n = nil, &block)
    return to_enum(:max_by) { enumerator_size } unless block_given?
    return max_by_n(n, &block) unless Primitive.nil?(n)

    max_object = nil
    max_result = undefined

    each do
      object = Primitive.single_block_arg
      result = yield object

      if Primitive.undefined?(max_result) or \
           Truffle::Type.coerce_to_comparison(max_result, result) < 0
        max_object = object
        max_result = result
      end
    end

    max_object
  end

  private def min_by_n(n, &block)
    n = Primitive.rb_num2long(n)
    raise ArgumentError, "negative size #{n}" if n < 0
    return [] if n == 0
    self.sort_by(&block).first(n)
  end

  def min_by(n = nil, &block)
    return to_enum(:min_by) { enumerator_size } unless block_given?
    return min_by_n(n, &block) unless Primitive.nil?(n)

    min_object = nil
    min_result = undefined

    each do
      object = Primitive.single_block_arg
      result = yield object

      if Primitive.undefined?(min_result) or \
           Truffle::Type.coerce_to_comparison(min_result, result) > 0
        min_object = object
        min_result = result
      end
    end

    min_object
  end

  def self.sort_proc
    @sort_proc ||= Proc.new do |a, b|
      unless ret = a <=> b
        raise ArgumentError, 'Improper spaceship value'
      end
      ret
    end
  end

  def minmax(&block)
    block = Enumerable.sort_proc unless block
    first_time = true
    min, max = nil

    each do
      object = Primitive.single_block_arg
      if first_time
        min = max = object
        first_time = false
      else
        unless min_cmp = block.call(min, object)
          raise ArgumentError, 'comparison failed'
        end
        min = object if min_cmp > 0

        unless max_cmp = block.call(max, object)
          raise ArgumentError, 'comparison failed'
        end

        max = object if max_cmp < 0
      end
    end
    [min, max]
  end

  def minmax_by(&block)
    return to_enum(:minmax_by) { enumerator_size } unless block_given?

    min_object = nil
    min_result = undefined

    max_object = nil
    max_result = undefined

    each do
      object = Primitive.single_block_arg
      result = yield object

      if Primitive.undefined?(min_result) or \
           Truffle::Type.coerce_to_comparison(min_result, result) > 0
        min_object = object
        min_result = result
      end

      if Primitive.undefined?(max_result) or \
           Truffle::Type.coerce_to_comparison(max_result, result) < 0
        max_object = object
        max_result = result
      end
    end

    [min_object, max_object]
  end

  def none?(pattern = undefined)
    if !Primitive.undefined?(pattern)
      Primitive.warn_given_block_not_used if block_given?
      each { return false if pattern === Primitive.single_block_arg }
    elsif block_given?
      each { |*o| return false if yield(*o) }
    else
      each { return false if Primitive.single_block_arg }
    end

    true
  end

  def one?(pattern = undefined)
    found_one = false

    if !Primitive.undefined?(pattern)
      Primitive.warn_given_block_not_used if block_given?
      each do
        if pattern === Primitive.single_block_arg
          return false if found_one
          found_one = true
        end
      end
    elsif block_given?
      each do |*o|
        if yield(*o)
          return false if found_one
          found_one = true
        end
      end
    else
      each do
        if Primitive.single_block_arg
          return false if found_one
          found_one = true
        end
      end
    end

    found_one
  end

  def partition
    return to_enum(:partition) { enumerator_size } unless block_given?

    left = []
    right = []
    each do
      o = Primitive.single_block_arg
      yield(o) ? left.push(o) : right.push(o)
    end

    [left, right]
  end

  def reject
    return to_enum(:reject) { enumerator_size } unless block_given?

    ary = []
    each do
      o = Primitive.single_block_arg
      ary << o unless yield(o)
    end

    ary
  end

  def filter_map
    return to_enum(:filter_map) { enumerator_size } unless block_given?

    ary = []
    each do
      o = Primitive.single_block_arg
      v = yield(o)
      ary << v if v
    end

    ary
  end

  def reverse_each(&block)
    return to_enum(:reverse_each) { enumerator_size } unless block_given?

    # There is no other way then to convert to an array first... see 1.9's source.
    to_a.reverse_each(&block)
    self
  end

  def take(n)
    n = Primitive.rb_num2int n
    raise ArgumentError, "attempt to take negative size: #{n}" if n < 0

    array = []

    unless n <= 0
      each do
        elem = Primitive.single_block_arg
        array << elem
        break if array.size >= n
      end
    end

    array
  end
  alias_method :__take__, :take
  private :__take__

  def take_while
    return to_enum(:take_while) unless block_given?

    array = []
    each do |elem|
      return array unless yield(elem)
      array << elem
    end

    array
  end

  def include?(obj)
    each { return true if Primitive.single_block_arg == obj }
    false
  end
  alias_method :member?, :include?

  def uniq(&block)
    result = []
    if block_given?
      h = {}
      each do
        e = Primitive.single_block_arg
        v = yield(e)
        unless h.key?(v)
          h[v] = true
          result << e
        end
      end
    else
      h = {}
      each do
        e = Primitive.single_block_arg
        unless h.key?(e)
          h[e] = true
          result << e
        end
      end
    end

    result
  end

  def sum(init = 0)
    compensation = Truffle::EnumerableOperations::Compensation.new
    if block_given?
      result = inject(init) do |sum, e|
        Truffle::EnumerableOperations.sum_add(sum, yield(e), compensation)
      end
      Primitive.is_a?(result, Float) ? result + compensation.value : result
    else
      result = inject(init) do |sum, e|
        Truffle::EnumerableOperations.sum_add(sum, e, compensation)
      end
      Primitive.is_a?(result, Float) ? result + compensation.value : result
    end
  end

  def chain(*enums)
    Enumerator::Chain.new(self, *enums)
  end

end

class Array
  # Copy methods from Enumerable that should also be defined on Array
  alias_method :any?, :any?
  alias_method :take, :take
  alias_method :drop_while, :drop_while
  alias_method :take_while, :take_while
  alias_method :uniq, :uniq
  alias_method :sum, :sum
  alias_method :all?, :all?
  alias_method :none?, :none?
  alias_method :one?, :one?
end
