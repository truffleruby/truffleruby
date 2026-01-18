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

class Range

  include Enumerable

  # Only used if called directly with allocate + #initialize.
  # Range.new is defined in Java.
  def initialize(first, last, exclude_end = false)
    raise NameError, "'initialize' called twice" if self.begin

    unless Primitive.is_a?(first, Integer) && Primitive.is_a?(last, Integer)
      begin
        raise ArgumentError, 'bad value for range' unless first <=> last
      rescue
        raise ArgumentError, 'bad value for range'
      end
    end

    Primitive.range_initialize self, first, last, Primitive.as_boolean(exclude_end)
  end
  private :initialize

  def ==(other)
    return true if Primitive.equal?(self, other)

    Primitive.is_a?(other, Range) and
      self.begin == other.begin and
      self.end == other.end and
      self.exclude_end? == other.exclude_end?
  end

  def eql?(other)
    return true if Primitive.equal?(self, other)

    Primitive.is_a?(other, Range) and
        self.begin.eql?(other.begin) and
        self.end.eql?(other.end) and
        self.exclude_end? == other.exclude_end?
  end

  def bsearch(&block)
    start = self.begin
    stop  = self.end

    if Primitive.is_a?(start, Float) || Primitive.is_a?(stop, Float)
      bsearch_float(&block)
    elsif Primitive.is_a?(start, Integer)
      if Primitive.nil? stop
        bsearch_endless(&block)
      elsif Primitive.is_a?(stop, Integer)
        bsearch_integer(&block)
      else
        raise TypeError, "can't do binary search for #{Primitive.class(stop)}"
      end
    elsif Primitive.nil?(start) && Primitive.is_a?(stop, Integer)
      bsearch_beginless(&block)
    else
      raise TypeError, "can't do binary search for #{Primitive.class(start)}"
    end
  end

  private def midpoint(low, high)
    mid1 = (low + high) / 2
    mid2 = low/2 + high/2
    mid1.abs.infinite? ? mid2 : mid1 # mitigate potential overflow

    # NOTE(norswap, 23 Jan. 2020)
    #   This might not be a very "centered" midpoint, but it's sufficient to cut the range so that we still get
    #   ~ O(log n) binary search runtime.
  end

  private def bsearch_float(&block)
    return to_enum :bsearch_float unless block_given?

    normalized_begin = Primitive.nil?(self.begin) ? -Float::INFINITY : self.begin.to_f
    normalized_end = Primitive.nil?(self.end) ? Float::INFINITY : self.end.to_f
    normalized_end = normalized_end.prev_float if self.exclude_end?
    min = normalized_begin
    max = normalized_end
    last_admissible = nil
    stop = false

    # The next max-min comparisons simplify infinity handling.
    # Infinity-handling is necessary to be able to cut ranges in two.
    return nil if max < min || max == min && self.exclude_end? #-inf.prev_float == -inf!
    if max == min
      result = yield max
      return Primitive.true?(result) || result == 0 ? max : nil
    end

    # max == -Float::INFINITY will already be covered by the comparisons above.
    if max == Float::INFINITY
      result = yield max
      case result
      when Numeric
        if result == 0
          return max
        elsif result > 0
          # There is nothing above infinity.
          return nil
        end
      when true
        last_admissible = max
      end
      max = normalized_end = Float::MAX # guaranteed to be >= min
    end

    # min == Float::INFINITY is already covered by the comparisons above.
    if min == -Float::INFINITY
      result = yield min
      # Find-minimum mode: It's not going to get any smaller than negative infinity.
      return min if Primitive.true?(result) || result == 0
      return nil if Primitive.is_a?(result, Numeric) && result < 0
      min = normalized_begin = -Float::MAX # guaranteed to be <= max
    end

    mid = 0
    until stop
      mid = midpoint(min, max)
      result = yield mid

      # Must check this before modifying min or max.
      stop = true if min == mid || mid == max

      case result
      when Numeric
        if result > 0
          min = mid
        elsif result < 0
          max = mid
        else
          last_admissible = mid
          break
        end
      when true
        last_admissible = mid
        max = mid
      when false, nil
        min = mid
      else
        raise TypeError, "wrong argument type #{Primitive.class(result)} (must be numeric, true, false or nil)"
      end
    end

    # At the end of the loop, mid could be equal to min or max due to precision limits and the other bound
    # might not have been tested in some cases.
    if mid == min && max == normalized_end && (Primitive.nil?(last_admissible) || last_admissible == Float::INFINITY)
      # max is untested only if it's the end of the range.
      # It can only change the result of the search if we haven't found an admissible value yet (+ infinity edge case).
      result = yield max
      return max if Primitive.true?(result) || result == 0
    elsif mid == max && min == normalized_begin
      # min is untested only if it's the begin of the range.
      result = yield min
      # Favor smallest value in case we're in find-minimum mode.
      return min if Primitive.true?(result) || result == 0
    end

    last_admissible
  end

  private def bsearch_endless(&block)
    return to_enum :bsearch_endless unless block_given?

    min = self.begin
    cur = min
    diff = 1

    while true
      result = yield cur
      case result
      when 0
        return cur
      when Numeric
        return (min..cur).bsearch(&block) if result < 0
      when true
        return (min..cur).bsearch(&block)
      end
      cur += diff
      diff *= 2
    end
  end

  private def bsearch_beginless(&block)
    return to_enum :bsearch_beginless unless block_given?

    max = self.end
    cur = max
    diff = 1

    while true
      result = yield cur
      case result
      when 0
        return cur
      when Numeric
        return (cur..max).bsearch(&block) if result > 0
      when false
        return (cur..max).bsearch(&block)
      end
      cur -= diff
      diff *= 2
    end
  end

  private def bsearch_integer(&block)
    return to_enum :bsearch_integer unless block_given?

    min = self.begin
    max = self.end
    max -= 1 if Primitive.is_a?(max, Integer) and exclude_end?
    return nil if max < min
    last_admissible = nil
    stop = false

    until stop
      # Do one last iteration on the bounds when they converge.
      stop = true if min == max
      mid = (min + max) / 2 # integers don't overflow \o/
      result = yield mid

      case result
      when Numeric
        if result > 0
          min = mid + 1
        elsif result < 0
          max = mid
        else
          return mid
        end
      when true
        last_admissible = mid
        max = mid
      when false, nil
        min = mid + 1
      else
        raise TypeError, "wrong argument type #{Primitive.class(result)} (must be numeric, true, false or nil)"
      end
    end

    last_admissible
  end

  def count(item = undefined)
    if Primitive.nil?(self.begin) || Primitive.nil?(self.end)
      return Float::INFINITY unless block_given? || !Primitive.undefined?(item)
    end

    super
  end

  private def each_internal(&block)
    return to_enum { size } unless block_given?
    first, last = self.begin, self.end

    unless first.respond_to?(:succ) && !Primitive.is_a?(first, Time)
      raise TypeError, "can't iterate from #{Primitive.class(first)}"
    end

    return each_endless(first, &block) if Primitive.nil? last

    case first
    when Integer
      last -= 1 if exclude_end?

      i = first
      while i <= last
        yield i
        i += 1
      end
    when String
      first.upto(last, exclude_end?) do |str|
        yield str
      end
    when Symbol
      first.to_s.upto(last.to_s, exclude_end?) do |str|
        yield str.to_sym
      end
    else
      current = first
      if exclude_end?
        while (current <=> last) < 0
          yield current
          current = current.succ
        end
      else
        while (c = current <=> last) && c <= 0
          yield current
          break if c == 0
          current = current.succ
        end
      end
    end

    self
  end

  private def each_endless(first, &block)
    if Primitive.is_a?(first, Integer)
      i = first
      while true
        yield i
        i += 1
      end
    else
      current = first
      while true
        yield current
        current = current.succ
      end
    end
  end

  def first(n = undefined)
    raise RangeError, 'cannot get the first element of beginless range' if Primitive.nil?(self.begin)
    return self.begin if Primitive.undefined? n

    super
  end

  # Random number for hash codes. Stops hashes for similar values in
  # different classes from clashing, but defined as a constant so
  # that hashes will be deterministic.

  CLASS_SALT = 0xb36df84d

  private_constant :CLASS_SALT

  def hash
    val = Primitive.vm_hash_start(CLASS_SALT)
    val = Primitive.vm_hash_update(val, exclude_end? ? 1 : 0)
    val = Primitive.vm_hash_update(val, self.begin.hash)
    val = Primitive.vm_hash_update(val, self.end.hash)
    Primitive.vm_hash_end(val)
  end

  def include?(value)
    if self.begin.respond_to?(:to_int) ||
        self.end.respond_to?(:to_int) ||
        Primitive.is_a?(self.begin, Numeric) ||
        Primitive.is_a?(self.end, Numeric) ||
        Primitive.is_a?(self.begin, Time) ||
        Primitive.is_a?(self.end, Time)
      cover? value
    else
      super
    end
  end

  alias_method :member?, :include?

  def ===(value)
    cover?(value)
  end

  def inspect
    sep = exclude_end? ? '...' : '..'
    if (Primitive.nil?(self.begin) && Primitive.nil?(self.end))
      "nil#{sep}nil"
    else
      (Primitive.nil?(self.begin) ? '' : self.begin.inspect) + sep + (Primitive.nil?(self.end) ? '' : self.end.inspect)
    end
  end

  def last(n = undefined)
    raise RangeError, 'cannot get the last element of endless range' if Primitive.nil? self.end
    return self.end if Primitive.undefined? n

    to_a.last(n)
  end

  def max
    raise RangeError, 'cannot get the maximum of endless range' if Primitive.nil? self.end

    if block_given? || (exclude_end? && !Primitive.is_a?(self.end, Numeric))
      if Primitive.nil? self.begin
        raise RangeError, 'cannot get the maximum of beginless range with custom comparison method'
      end

      return super
    end

    if exclude_end?
      if !Primitive.is_a?(self.end, Integer)
        raise TypeError, 'cannot exclude non Integer end value'
      elsif !Primitive.is_a?(self.begin, Integer)
        raise TypeError, 'cannot exclude end value with non Integer begin value'
      elsif self.end <= self.begin
        return nil
      end

      self.end - 1
    else
      return nil if !Primitive.nil?(self.begin) && Comparable.compare_int(self.end <=> self.begin) < 0
      self.end
    end
  end

  def min
    raise RangeError, 'cannot get the minimum of beginless range' if Primitive.nil? self.begin
    if Primitive.nil? self.end
      raise RangeError, 'cannot get the minimum of endless range with custom comparison method' if block_given?
      return self.begin
    end
    return super if block_given?
    if Comparable.compare_int(self.end <=> self.begin) < 0
      return nil
    elsif exclude_end? && self.end == self.begin
      return nil
    end

    self.begin
  end

  def minmax(&block)
    if block
      super(&block)
    else
      [min, max]
    end
  end

  def overlap?(other)
    Truffle::Type.rb_check_type(other, Range)

    # ranges don't overlap
    return false if Truffle::RangeOperations.greater_than?(self.begin, other.end, other.exclude_end?)
    return false if Truffle::RangeOperations.greater_than?(other.begin, self.end, self.exclude_end?)

    # empty range doesn't overlap any other range
    return false if Truffle::RangeOperations.greater_than?(self.begin, self.end, self.exclude_end?)
    return false if Truffle::RangeOperations.greater_than?(other.begin, other.end, other.exclude_end?)

    true
  end

  def %(n)
    Truffle::RangeOperations.step_no_block(self, n)
  end

  def reverse_each(&block)
    return to_enum(:reverse_each) { reverse_each_size } unless block_given?

    if Primitive.nil?(self.end)
      raise TypeError, "can't iterate from NilClass"
    end

    if Primitive.is_a?(self.begin, Integer) && Primitive.is_a?(self.end, Integer)
      last = exclude_end? ? self.end - 1 : self.end

      i = last
      while i >= first
        yield i
        i -= 1
      end
    elsif Primitive.nil?(self.begin) && Primitive.is_a?(self.end, Integer)
      last = exclude_end? ? self.end - 1 : self.end

      i = last
      while true
        yield i
        i -= 1
      end
    else
      method(:reverse_each).super_method.call(&block)
    end

    self
  end

  private def step_internal(step_size = 1, &block) # :yields: object
    return Truffle::RangeOperations.step_no_block(self, step_size) unless block

    values = Truffle::RangeOperations.validate_step_size(self.begin, self.end, step_size)
    first = values[0]
    last = values[1]
    step_size = values[2]

    return step_endless(first, step_size, &block) if Primitive.nil?(last)

    case first
    when Float
      iterations = Truffle::NumericOperations.float_step_size(first, last, step_size, exclude_end?)

      i = 0
      while i < iterations
        curr = i * step_size + first
        curr = last if last < curr
        yield curr
        i += 1
      end
    when Numeric
      curr = first
      last -= 1 if exclude_end?

      while curr <= last
        yield curr
        curr += step_size
      end
    else
      i = 0
      each do |item|
        yield item if i % step_size == 0
        i += 1
      end
    end

    self
  end

  private def step_endless(first, step_size, &block)
    if Primitive.is_a?(first, Numeric)
      curr = first
      while true
        yield curr
        curr += step_size
      end
    else
      i = 0
      each_endless(first) do |item|
        yield item if i % step_size == 0
        i += 1
      end
    end
  end

  def to_s
    "#{self.begin}#{exclude_end? ? "..." : ".."}#{self.end}"
  end

  def cover?(value)
    if Primitive.is_a?(value, Range)
      Truffle::RangeOperations.range_cover?(self, value)
    else
      Truffle::RangeOperations.cover?(self, value)
    end
  end

  def size
    if Primitive.is_a?(self.begin, Integer)
      return Float::INFINITY if Primitive.nil?(self.end) || self.end == Float::INFINITY

      delta = self.end - self.begin
      delta += 1 unless exclude_end?
      return delta < 0 ? 0 : delta.floor
    end

    unless self.begin.respond_to?(:succ)
      raise TypeError, "can't iterate from #{Primitive.class(self.begin)}"
    end

    nil
  end

  private def reverse_each_size
    if Primitive.nil?(self.end)
      raise TypeError, "can't iterate from #{Primitive.class(self.end)}"
    end

    if Primitive.is_a?(self.begin, Integer)
      return Float::INFINITY if self.end == Float::INFINITY

      delta = self.end - self.begin
      delta += 1 if !exclude_end? || self.end % 1 != 0
      return delta < 0 ? 0 : delta.floor
    end

    if Primitive.nil?(self.begin)
      return Float::INFINITY if Primitive.is_a?(self.end, Integer)
    end

    unless self.begin.respond_to?(:succ)
      raise TypeError, "can't iterate from #{Primitive.class(self.end)}"
    end

    nil
  end

  def map(&block)
    ary = Primitive.range_integer_map(self, block)
    if !Primitive.undefined?(ary)
      ary
    else
      super(&block)
    end
  end
  Primitive.always_split self, :map
  alias_method :collect, :map

  private def to_a_internal # MODIFIED called from java to_a
    return to_a_from_enumerable unless Primitive.is_a?(self.begin, Integer) and Primitive.is_a?(self.end, Integer)

    fin = self.end
    fin += 1 unless exclude_end?

    size = fin - self.begin
    return [] if size <= 0

    ary = Array.new(size)

    i = 0
    while i < size
      ary[i] = self.begin + i
      i += 1
    end

    ary
  end

  private def to_a_from_enumerable(*arg)
    ary = []
    each(*arg) do
      o = Primitive.single_block_arg
      ary << o
      nil
    end
    ary
  end
end
