# frozen_string_literal: true

# Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
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

class Array
  include Enumerable

  # The flow control for many of these methods is
  # pretty evil due to how MRI works. There is
  # also a lot of duplication of code due to very
  # subtle processing differences and, in some
  # cases, to avoid mutual dependency. Apologies.


  def self.[](*args)
    ary = allocate
    ary.replace args
    ary
  end

  # Try to convert obj into an array, using to_ary method.
  # Returns converted array or nil if obj cannot be converted
  # for any reason. This method is to check if an argument is an array.
  def self.try_convert(obj)
    Truffle::Type.try_convert obj, Array, :to_ary
  end

  def &(other)
    other = Truffle::Type.coerce_to other, Array, :to_ary

    h = {}
    other.each { |e| h[e] = true }
    select { |x| h.delete x }
  end

  def |(other)
    other = Truffle::Type.coerce_to other, Array, :to_ary

    h = {}
    each { |e| h[e] = true }
    other.each { |e| h[e] = true }
    h.keys
  end

  def -(other)
    other = Truffle::Type.coerce_to other, Array, :to_ary

    h = {}
    other.each { |e| h[e] = true }
    reject { |x| h.include? x }
  end

  def <=>(other)
    other = Truffle::Type.rb_check_convert_type other, Array, :to_ary
    return 0 if Primitive.equal?(self, other)
    return nil if Primitive.nil? other

    total = other.size

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      i = 0
      count = Primitive.min(total, size)

      while i < count
        order = self[i] <=> other[i]
        return order unless order == 0

        i += 1
      end
    end

    # subtle: if we are recursing on that pair, then let's
    # no go any further down into that pair;
    # any difference will be found elsewhere if need be
    size <=> total
  end

  def *(count)
    result = Primitive.array_mul(self, count)

    if !Primitive.undefined?(result)
      result
    elsif str = Truffle::Type.rb_check_convert_type(count, String, :to_str)
      join(str)
    else
      self * Truffle::Type.coerce_to(count, Integer, :to_int)
    end
  end

  def ==(other)
    result = Primitive.array_equal? self, other
    unless Primitive.undefined?(result)
      return result
    end

    return true if Primitive.equal?(self, other)
    unless Primitive.is_a?(other, Array)
      return false unless other.respond_to? :to_ary
      return other == self
    end

    return false unless size == other.size

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      i = 0
      total = size

      while i < total
        return false unless Primitive.same_or_equal?(self[i], other[i])
        i += 1
      end
    end

    true
  end

  private def slice_arithmetic_sequence(seq)
    len = size

    if seq.step < 0 # inverse range with negative step
      start = seq.end
      stop  = seq.begin
      step  = seq.step
    else
      start = seq.begin
      stop  = seq.end
      step  = seq.step
    end

    start ||= 0 # begin-less range
    stop ||= -1 # endless range

    # negative indexes refer to the end of array
    start += len if start < 0
    stop  += len if stop < 0
    diff = stop - start + (seq.exclude_end? ? 0 : 1)

    is_out_of_bound = start < 0 || start > len

    if step < -1 || step > 1
      raise RangeError, "#{seq.inspect} out of range" if is_out_of_bound || diff > len
    elsif is_out_of_bound
      return nil
    end

    return [] if diff <= 0

    diff = len - start if (len < diff || len < start + diff)

    return self[start, diff] if step == 1 # step == 1 is a simple slice

    # optimize when no step will be done and only start element is returned
    return self[start, 1] if (step > 0 && step > diff)
    return self[stop, 1] if (step < 0 && step < -diff)

    ustep = step.abs
    nlen = (diff + ustep - 1) / ustep
    i = 0
    j = start
    j += diff - (seq.exclude_end? ? 0 : 1) if step < 0 # because we inverted negative step ranges

    res = Array.new(nlen)

    while i < nlen
      res[i] = self[j]
      i += 1
      j += step
    end

    res
  end

  def assoc(obj)
    each do |x|
      if Primitive.is_a?(x, Array) and x.first == obj
        return x
      end
    end

    nil
  end

  def bsearch(&block)
    return to_enum :bsearch unless block_given?

    if idx = bsearch_index(&block)
      self[idx]
    else
      nil
    end
  end

  def bsearch_index
    return to_enum :bsearch_index unless block_given?

    min = 0
    max = total = size

    last_true = nil
    i = size / 2

    while max >= min and i >= 0 and i < total
      x = yield at(i)

      return i if x == 0

      case x
      when Numeric
        if x > 0
          min = i + 1
        else
          max = i - 1
        end
      when true
        last_true = i
        max = i - 1
      when false, nil
        min = i + 1
      else
        raise TypeError, 'wrong argument type (must be numeric, true, false or nil)'
      end

      i = min + (max - min) / 2
    end

    return i if max > min
    return last_true if last_true

    nil
  end

  def combination(num)
    num = Primitive.rb_num2int num

    unless block_given?
      return to_enum(:combination, num) do
        combination_size(num)
      end
    end

    if num == 0
      yield []
    elsif num == 1
      each do |i|
        yield [i]
      end
    elsif num == size
      yield self.dup
    elsif num >= 0 && num < size
      stack = Array.new(num + 1, 0)
      chosen = Array.new(num)
      lev = 0
      done = false
      stack[0] = -1
      until done
        chosen[lev] = self.at(stack[lev+1])
        while lev < num - 1
          lev += 1
          chosen[lev] = self.at(stack[lev+1] = stack[lev] + 1)
        end
        yield chosen.dup
        lev += 1
        begin
          done = lev == 0
          stack[lev] += 1
          lev -= 1
        end while stack[lev+1] + num == size + lev + 1
      end
    end
    self
  end

  def count(item = undefined)
    seq = 0
    if !Primitive.undefined?(item)
      Primitive.warn_given_block_not_used if block_given?
      each { |o| seq += 1 if item == o }
    elsif block_given?
      each { |o| seq += 1 if yield(o) }
    else
      return size
    end
    seq
  end

  def cycle(n = nil)
    unless block_given?
      return to_enum(:cycle, n) do
        Truffle::EnumerableOperations.cycle_size(size, n)
      end
    end

    return nil if empty?

    if Primitive.nil?(n)
      until empty?
        each { |x| yield x }
      end
    else
      n = Primitive.rb_num2int n
      n.times do
        each { |x| yield x }
      end
    end
    nil
  end

  def delete_if(&block)
    return to_enum(:delete_if) { size } unless block_given?
    reject!(&block)
    self
  end

  def difference(*others)
    other = []
    others.each { |a| other = other | a }
    self - other
  end

  def dig(idx, *more)
    result = self.at(idx)
    if Primitive.nil?(result) || more.empty?
      result
    else
      Truffle::Diggable.dig(result, more)
    end
  end

  def deconstruct
    self
  end

  def each_index
    return to_enum(:each_index) { size } unless block_given?

    i = 0

    while i < size
      yield i
      i += 1
    end

    self
  end

  def eql?(other)
    result = Primitive.array_eql? self, other
    unless Primitive.undefined?(result)
      return result
    end

    return true if Primitive.equal?(self, other)
    return false unless Primitive.is_a?(other, Array)
    return false if size != other.size

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      i = 0
      each do |x|
        return false unless x.eql? other[i]
        i += 1
      end
    end

    true
  end

  def fetch(idx, default = undefined)
    orig = idx
    idx = Primitive.rb_num2int idx

    idx += size if idx < 0

    if idx < 0 or idx >= size
      if block_given?
        Primitive.warn_block_supersedes_default_value_argument unless Primitive.undefined?(default)

        return yield(orig)
      end

      return default unless Primitive.undefined?(default)

      raise IndexError, "index #{orig} outside of array bounds: #{-size}...#{size}"
    end

    at(idx)
  end

  def fetch_values(*idxs, &block)
    idxs.map do |idx|
      fetch(idx, &block)
    end
  end

  private def fill_internal(a = undefined, b = undefined, c = undefined)
    Primitive.check_frozen self

    if block_given?
      unless Primitive.undefined?(c)
        raise ArgumentError, 'wrong number of arguments'
      end
      index = a
      length = b
    else
      if Primitive.undefined?(a)
        raise ArgumentError, 'wrong number of arguments'
      end
      obj = a
      index = b
      length = c
    end

    if Primitive.undefined?(index) || !index
      left = 0
      right = size
    elsif Primitive.is_a?(index, Range)
      raise TypeError, 'length invalid with range' unless Primitive.undefined?(length)

      left, length = Primitive.range_normalized_start_length(index, size)
      raise RangeError, "#{index.inspect} out of range" if left < 0
      right = left + length
      return self if right <= left # Nothing to modify

    elsif index
      left = Primitive.rb_num2int index
      left += size if left < 0
      left = 0 if left < 0

      if !Primitive.undefined?(length) and length
        right = Primitive.rb_num2int length
        return self if right == 0
        right += left
      else
        right = size
      end
    end

    unless Truffle::Type.fits_into_int?(left) && Truffle::Type.fits_into_int?(right)
      raise ArgumentError, 'argument too big'
    end

    i = left
    if block_given?
      while i < right
        self[i] = yield(i)
        i += 1
      end
    else
      while i < right
        self[i] = obj
        i += 1
      end
    end

    self
  end

  def first(n = undefined)
    return at(0) if Primitive.undefined?(n)

    n = Primitive.rb_num2int n
    raise ArgumentError, 'Size must be positive' if n < 0

    Array.new self[0, n]
  end

  def flatten(level = -1)
    level = Primitive.rb_num2int level
    return Array.new(self) if level == 0

    out = [] # new_reserved size
    Primitive.array_flatten_helper(self, out, level)
    out
  end

  def flatten!(level = -1)
    Primitive.check_frozen self

    level = Primitive.rb_num2int level
    return nil if level == 0

    out = Primitive.class(self).allocate # new_reserved size
    if Primitive.array_flatten_helper(self, out, level)
      Primitive.steal_array_storage(self, out)
      return self
    end

    nil
  end

  def hash
    unless Primitive.array_can_contain_object?(self)
      # Primitive arrays do not need the recursion check
      return Primitive.hash_internal(self)
    end

    hash_val = size

    # This is duplicated and manually inlined code from Thread for performance
    # reasons. Before refactoring it, please benchmark it and compare your
    # refactoring against the original.

    id = object_id
    objects = Primitive.thread_recursive_objects

    # If there is already an our version running...
    if objects.key? :__detect_outermost_recursion__

      # If we've seen self, unwind back to the outer version
      if objects.key? id
        raise Truffle::ThreadOperations::InnerRecursionDetected
      end

      # .. or compute the hash value like normal
      begin
        objects[id] = true

        hash_val = Primitive.hash_internal(self)
      ensure
        objects.delete id
      end

      return hash_val
    else
      # Otherwise, we're the outermost version of this code..
      begin
        objects[:__detect_outermost_recursion__] = true
        objects[id] = true

        hash_val = Primitive.hash_internal(self)

        # An inner version will raise to return back here, indicating that
        # the whole structure is recursive. In which case, abandon most of
        # the work and return a simple hash value.
      rescue Truffle::ThreadOperations::InnerRecursionDetected
        return size
      ensure
        objects.delete :__detect_outermost_recursion__
        objects.delete id
      end
    end

    hash_val
  end
  Primitive.always_split self, :hash

  def find_index(obj = undefined)
    super
  end
  alias_method :index, :find_index

  def insert(idx, *items)
    Primitive.check_frozen self
    return self if items.length == 0

    idx = Primitive.rb_num2int idx
    idx += (size + 1) if idx < 0    # Negatives add AFTER the element
    raise IndexError, "#{idx} out of bounds" if idx < 0

    # This check avoids generalizing to Object[] needlessly when the element is an int/long/...
    # We still generalize needlessly on bigger arrays, but avoiding this would require iterating the array.
    if items.size == 1
      self[idx, 0] = [items[0]]
    else
      self[idx, 0] = items
    end
    self
  end
  Primitive.always_split self, :insert

  def inspect
    return '[]'.encode(Encoding::US_ASCII) if empty?
    result = +'['

    return +'[...]' if Truffle::ThreadOperations.detect_recursion self do
      each_with_index do |element, index|
        temp = Truffle::Type.rb_inspect(element)
        result.force_encoding(temp.encoding) if index == 0
        result << temp << ', '
      end
    end

    Truffle::StringOperations.shorten!(result, 2)
    result << ']'
    result
  end
  alias_method :to_s, :inspect

  def intersect?(other)
    other = Truffle::Type.coerce_to other, Array, :to_ary

    shorter, longer = size > other.size ? [other, self] : [self, other]
    return false if shorter.empty?

    shorter_set = {}
    shorter.each { |e| shorter_set[e] = true }

    longer.each do |e|
      return true if shorter_set.include?(e)
    end

    false
  end

  def intersection(*others)
    return self & others.first if others.size == 1

    common = {}
    each { |e| common[e] = true }

    others.each do |other|
      other = Truffle::Type.coerce_to other, Array, :to_ary

      other_hash = {}
      other.each { |e| other_hash[e] = true }

      common.each_key do |x|
        common.delete(x) unless other_hash.include?(x)
      end
    end

    common.keys
  end

  def join(sep = nil)
    return ''.encode(Encoding::US_ASCII) if size == 0

    out = +''
    raise ArgumentError, 'recursive array join' if Truffle::ThreadOperations.detect_recursion self do
      sep = Primitive.nil?(sep) ? $, : StringValue(sep)

      # We've manually unwound the first loop entry for performance
      # reasons.
      x = self[0]

      if str = String.try_convert(x)
        x = str
      elsif ary = Array.try_convert(x)
        x = ary.join(sep)
      else
        x = x.to_s
      end

      out.force_encoding(x.encoding)
      out << x

      total = size()
      i = 1

      while i < total
        out << sep if sep

        x = self[i]

        if str = String.try_convert(x)
          x = str
        elsif ary = Array.try_convert(x)
          x = ary.join(sep)
        else
          x = x.to_s
        end

        out << x
        i += 1
      end
    end

    out
  end

  def keep_if(&block)
    return to_enum(:keep_if) { size } unless block_given?
    select!(&block)
    self
  end

  def last(n = undefined)
    if Primitive.undefined?(n)
      return at(-1)
    elsif size < 1
      return []
    end

    n = Primitive.rb_num2int n
    return [] if n == 0

    raise ArgumentError, 'count must be positive' if n < 0

    n = size if n > size
    Array.new self[-n..-1]
  end

  def pack(format, buffer: nil)
    if Primitive.nil? buffer
      Primitive.array_pack(self, format, '')
    else
      unless Primitive.is_a?(buffer, String)
        raise TypeError, "buffer must be String, not #{Primitive.class(buffer)}"
      end

      string = Primitive.array_pack(self, format, buffer)
      buffer.replace string.force_encoding(buffer.encoding)
    end
  end
  Primitive.always_split self, :pack

  def permutation(num = undefined, &block)
    unless block_given?
      return to_enum(:permutation, num) do
        permutation_size(num)
      end
    end

    if Primitive.undefined? num
      num = size
    else
      num = Primitive.rb_num2int num
    end

    if num < 0 || size < num
      # no permutations, yield nothing
    elsif num == 0
      # exactly one permutation: the zero-length array
      yield []
    elsif num == 1
      # this is a special, easy case
      each { |val| yield [val] }
    else
      # this is the general case
      perm = Array.new(num)
      used = Array.new(size, false)

      if block
        # offensive (both definitions) copy.
        offensive = dup
        offensive.__send__ :__permute__, num, perm, 0, used, &block
      else
        __permute__(num, perm, 0, used, &block)
      end
    end

    self
  end

  def max(n = undefined)
    super(n)
  end

  def min(n = undefined)
    super(n)
  end

  def minmax(&block)
    if block_given?
      super(&block)
    else
      [self.min, self.max]
    end
  end

  def permutation_size(num)
    n = self.size
    if Primitive.undefined? num
      k = self.size
    else
      k = Primitive.rb_num2int num
    end
    descending_factorial(n, k)
  end
  private :permutation_size

  def descending_factorial(from, how_many)
    cnt = how_many >= 0 ? 1 : 0
    while (how_many) > 0
      cnt = cnt*(from)
      from -= 1
      how_many -= 1
    end
    cnt
  end
  private :descending_factorial

  def __permute__(num, perm, index, used, &block)
    # Recursively compute permutations of r elements of the set [0..n-1].
    # When we have a complete permutation of array indexes, copy the values
    # at those indexes into a new array and yield that array.
    #
    # num: the number of elements in each permutation
    # perm: the array (of size num) that we're filling in
    # index: what index we're filling in now
    # used: an array of booleans: whether a given index is already used
    #
    # Note: not as efficient as could be for big num.
    size.times do |i|
      unless used[i]
        perm[index] = i
        if index < num-1
          used[i] = true
          __permute__(num, perm, index+1, used, &block)
          used[i] = false
        else
          yield values_at(*perm)
        end
      end
    end
  end
  private :__permute__

  # Implementation notes: We build a block that will generate all the
  # combinations by building it up successively using "inject" and starting
  # with one responsible to append the values.
  def product(*args)
    args.map! { |x| Truffle::Type.coerce_to(x, Array, :to_ary) }

    # Check the result size will fit in an Array.
    sum = args.inject(size) { |n, x| n * x.size }

    unless Primitive.integer_fits_into_long?(sum)
      raise RangeError, 'product result is too large'
    end

    # TODO rewrite this to not use a tree of Proc objects.

    # to get the results in the same order as in MRI, vary the last argument first
    args.reverse!

    result = []
    args.push self

    outer_lambda = args.inject(result.method(:push)) do |trigger, values|
      -> partial do
        values.each do |val|
          trigger.call(partial.dup << val)
        end
      end
    end

    outer_lambda.call([])

    if block_given?
      result.each { |v| yield(v) }
      self
    else
      result
    end
  end

  def rassoc(obj)
    each do |elem|
      if Primitive.is_a?(elem, Array) and elem.at(1) == obj
        return elem
      end
    end

    nil
  end

  def reject!
    return to_enum(:reject!) { size } unless block_given?

    Primitive.check_frozen self

    original_size = size
    kept = 0
    each_with_index do |e, i|
      begin
        exception = true
        remove = yield e
        exception = false
      ensure
        if exception
          self[kept..-1] = self[i..-1]
        end
      end

      unless remove
        self[kept] = e
        kept += 1
      end
    end

    self[kept..-1] = []

    self unless kept == original_size
  end

  def repeated_combination(combination_size, &block)
    combination_size = combination_size.to_i
    unless block_given?
      return to_enum(:repeated_combination, combination_size) do
        repeated_combination_size(combination_size)
      end
    end

    if combination_size < 0
      # yield nothing
    else
      dup.__send__ :compile_repeated_combinations, combination_size, [], 0, combination_size, &block
    end

    self
  end

  def compile_repeated_combinations(combination_size, place, index, depth, &block)
    if depth > 0
      (length - index).times do |i|
        place[combination_size-depth] = index + i
        compile_repeated_combinations(combination_size,place,index + i,depth-1, &block)
      end
    else
      yield place.map { |element| self[element] }
    end
  end

  private :compile_repeated_combinations

  def repeated_permutation(combination_size, &block)
    combination_size = combination_size.to_i
    unless block_given?
      return to_enum(:repeated_permutation, combination_size) do
        repeated_permutation_size(combination_size)
      end
    end

    if combination_size < 0
      # yield nothing
    elsif combination_size == 0
      yield []
    else
      dup.__send__ :compile_repeated_permutations, combination_size, [], 0, &block
    end

    self
  end

  def repeated_permutation_size(combination_size)
    return 0 if combination_size < 0
    self.size ** combination_size
  end
  private :repeated_permutation_size

  def repeated_combination_size(combination_size)
    return 1 if combination_size == 0
    binomial_coefficient(combination_size, self.size + combination_size - 1)
  end
  private :repeated_combination_size

  def binomial_coefficient(comb, size)
    comb = size-comb if (comb > size-comb)
    return 0 if comb < 0
    descending_factorial(size, comb) / descending_factorial(comb, comb)
  end
  private :binomial_coefficient

  def combination_size(num)
    binomial_coefficient(num, self.size)
  end
  private :combination_size

  def compile_repeated_permutations(combination_size, place, index, &block)
    length.times do |i|
      place[index] = i
      if index < (combination_size-1)
        compile_repeated_permutations(combination_size, place, index + 1, &block)
      else
        yield place.map { |element| self[element] }
      end
    end
  end
  private :compile_repeated_permutations

  def reverse
    Array.new dup.reverse!
  end

  def reverse_each
    return to_enum(:reverse_each) { size } unless block_given?

    i = size - 1
    while i >= 0
      yield at(i)
      i -= 1
    end
    self
  end

  def rindex(obj = undefined)
    if Primitive.undefined?(obj)
      return to_enum(:rindex, obj) unless block_given?

      i = size - 1
      while i >= 0
        return i if yield at(i)

        # Compensate for the array being modified by the block
        i = size if i > size

        i -= 1
      end
    else
      Primitive.warn_given_block_not_used if block_given?

      i = size - 1
      while i >= 0
        return i if at(i) == obj
        i -= 1
      end
    end
    nil
  end

  def rotate(n = 1)
    n = Primitive.rb_num2int n

    len = self.length
    return Array.new(self) if len <= 1

    n = n % len
    return Array.new(self) if n == 0
    Primitive.array_rotate self, n
  end

  def rotate!(n = 1)
    n = Primitive.rb_num2int n
    Primitive.check_frozen self

    len = self.length
    return self if len <= 1

    n = n % len
    return self if n == 0
    Primitive.array_rotate_inplace self, n
  end

  class SampleRandom
    def initialize(rng)
      @rng = rng
    end

    def rand(size)
      random = Primitive.rb_num2int @rng.rand(size)
      raise RangeError, 'random value must be >= 0' if random < 0
      raise RangeError, 'random value must be less than Array size' unless random < size

      random
    end
  end

  def sample(count = undefined, random: nil)
    if random and random.respond_to?(:rand)
      rng = SampleRandom.new random
    else
      rng = Kernel
    end

    if Primitive.undefined?(count)
      return at(size < 2 ? 0 : rng.rand(size))
    end

    count = Primitive.rb_num2int count
    raise ArgumentError, 'count must be >= 0' if count < 0

    size = self.size
    count = size if count > size

    case count
    when 0
      []
    when 1
      [at(rng.rand(size))]
    when 2
      i = rng.rand(size)
      j = rng.rand(size - 1)
      if j >= i
        j += 1
      end
      [at(i), at(j)]
    else
      sample_many(count, rng)
    end
  end

  private def sample_many(count, rng)
    if count <= 70 # three implementations; choice determined experimentally
      if 2.0 * size / count  <= count  + 13
        sample_many_swap(count, rng)
      else
        sample_many_quad(count, rng)
      end
    else
      if size <= -1100.0 + 59.5 * count
        sample_many_swap(count, rng)
      else
        sample_many_hash(count,rng)
      end
    end
  end

  private def sample_many_swap(count, rng)
    # linear dependence on count,
    result = Array.new(self)

    count.times do |i|
      r = i + rng.rand(result.size - i)
      result.__send__ :swap, i, r
    end

    count == size ? result : result[0, count]
  end

  private def sample_many_quad(count, rng)
    # quadratic time due to linear time collision check but low overhead
    result = Array.new count
    i = 1

    result[0] = rng.rand(size)

    while i < count
      k = rng.rand(size)
      spin = false

      while true
        j = 0
        while j < i
          if k == result[j]
            spin = true
            break
          end

          j += 1
        end

        if spin
          k = rng.rand(size)
          spin = false
        else
          break
        end
      end

      result[i] = k
      i += 1
    end

    i = 0
    while i < count
      result[i] = at result[i]
      i += 1
    end

    result
  end

  private def sample_many_hash(count, rng)
    # use hash for constant time collision check but higher overhead
    result = Array.new count
    i = 1

    result[0] = rng.rand(size)
    result_set = { result[0] => 0 }

    while i < count
      k = rng.rand(size)

      while true
        if result_set.include?(k)
          k = rng.rand(size)
        else
          break
        end
      end

      result[i] = k
      result_set[i] = k

      i += 1
    end

    i = 0
    while i < count
      result[i] = at result[i]
      i += 1
    end

    result
  end

  def select!(&block)
    return to_enum(:select!) { size } unless block_given?

    Primitive.check_frozen self

    original_size = size
    selected_size = 0

    each_with_index do |e, i|
      abnormal_termination = true
      to_select = yield e
      abnormal_termination = false

      if to_select
        self[selected_size] = e
        selected_size += 1
      end
    ensure
      self[selected_size..-1] = self[i..-1] if abnormal_termination
    end

    self[selected_size..-1] = []
    self unless selected_size == original_size
  end
  alias_method :filter!, :select!

  def shuffle(options = undefined)
    return dup.shuffle!(options) if instance_of? Array
    Array.new(self).shuffle!(options)
  end

  def shuffle!(options = undefined)
    Primitive.check_frozen self

    random_generator = Kernel

    unless Primitive.undefined? options
      options = Truffle::Type.coerce_to options, Hash, :to_hash
      random_generator = options[:random] if options[:random].respond_to?(:rand)
    end

    # Start at the end and work toward the beginning for compatibility with CRuby.
    (size - 1).downto(0) do |i|
      r = random_generator.rand(i + 1).to_int
      raise RangeError, "random number too small #{r}" if r < 0
      raise RangeError, "random number too big #{r}" if r > i
      swap(i, r)
    end
    self
  end

  def drop(n)
    n = Primitive.rb_num2int n
    raise ArgumentError, 'attempt to drop negative size' if n < 0

    new_size = size - n
    return [] if new_size <= 0

    self[n..-1]
  end

  def sort_by!(&block)
    return to_enum(:sort_by!) { size } unless block_given?

    Primitive.check_frozen self

    Primitive.steal_array_storage(self, sort_by(&block))
  end

  def to_a
    if self.instance_of? Array
      self
    else
      Array.new(self)
    end
  end

  def to_ary
    self
  end

  def to_h
    h = {}
    each_with_index do |elem, i|
      elem = yield(elem) if block_given?
      Truffle::HashOperations.assoc_key_value_pair_with_position(h, elem, i)
    end
    h
  end

  def transpose
    return [] if empty?

    out = []
    max = nil

    each do |ary|
      ary = Truffle::Type.coerce_to ary, Array, :to_ary
      max ||= ary.size

      # Catches too-large as well as too-small (for which #fetch would suffice)
      raise IndexError, 'All arrays must be same length' if ary.size != max

      ary.size.times do |i|
        entry = (out[i] ||= [])
        entry << ary.at(i)
      end
    end

    out
  end

  def union(*others)
    res = Array.new(self).uniq
    others.each { |other| res = res | other }
    res
  end

  alias_method :prepend, :unshift

  def values_at(*args)
    out = []

    args.each do |elem|
      # Cannot use #[] because of subtly different errors
      if Primitive.is_a?(elem, Range)
        start, length = Primitive.range_normalized_start_length(elem, size)
        finish = start + length - 1
        next if start < 0
        next if finish < start
        start.upto(finish) { |i| out << at(i) }
      else
        i = Primitive.rb_num2int elem
        out << at(i)
      end
    end

    out
  end

  # Synchronize with Enumerator#zip and Enumerable#zip
  def zip(*others)
    if !block_given? and others.size == 1 and Primitive.is_a?(others[0], Array)
      return Primitive.array_zip self, others[0]
    end

    out = Array.new(size) unless block_given?

    others.map! do |other|
      array = Truffle::Type.rb_check_convert_type(other, Array, :to_ary)

      if array
        array
      elsif Primitive.respond_to?(other, :each, false)
        other.to_enum(:each)
      else
        raise TypeError, "wrong argument type #{Primitive.class(other)} (must respond to :each)"
      end
    end

    size.times do |i|
      tuple = [at(i)]
      others.each do |other|
        tuple << case other
                 when Array
                   other.at(i)
                 else
                   begin
                     other.next
                   rescue StopIteration
                     nil # the enumerator could change between next? and next leading to StopIteration
                   end
                 end
      end

      if block_given?
        yield tuple
      else
        out[i] = tuple
      end
    end

    if block_given?
      nil
    else
      out
    end
  end

  private def sort_fallback(&block)
    # Use this instead of #dup as we want an instance of Array
    sorted = Array.new(self)
    if block
      sorted.__send__ :mergesort_block!, block
    else
      sorted.__send__ :mergesort!
    end
    sorted
  end

  # Non-recursive sort using a temporary array for scratch storage.
  # This is a hybrid mergesort; it's hybrid because for short runs under
  # 8 elements long we use insertion sort and then merge those sorted
  # runs back together.
  def mergesort!
    width = 7
    source = self
    scratch = Array.new(size, at(0))

    # do a pre-loop to create a bunch of short sorted runs; isort on these
    # 7-element sublists is more efficient than doing merge sort on 1-element
    # sublists
    left = 0
    finish = size
    while left < finish
      right = left + width
      right = Primitive.min(right, finish)
      last = left + (2 * width)
      last = Primitive.min(last, finish)

      isort!(left, right)
      isort!(right, last)

      left += 2 * width
    end

    # now just merge together those sorted lists from the prior loop
    width = 7
    while width < size
      left = 0
      while left < finish
        right = left + width
        right = Primitive.min(right, finish)
        last = left + (2 * width)
        last = Primitive.min(last, finish)

        bottom_up_merge(left, right, last, source, scratch)
        left += 2 * width
      end

      source, scratch = scratch, source
      width *= 2
    end

    Primitive.steal_array_storage(self, source)

    self
  end
  private :mergesort!

  def bottom_up_merge(left, right, last, source, scratch)
    left_index = left
    right_index = right
    i = left

    while i < last
      left_element = source.at(left_index)
      right_element = source.at(right_index)

      if left_index < right && (right_index >= last || (left_element <=> right_element) <= 0)
        scratch[i] = left_element
        left_index += 1
      else
        scratch[i] = right_element
        right_index += 1
      end

      i += 1
    end
  end
  private :bottom_up_merge

  def mergesort_block!(block)
    width = 7
    source = self
    scratch = Array.new(size, at(0))

    left = 0
    finish = size
    while left < finish
      right = left + width
      right = Primitive.min(right, finish)
      last = left + (2 * width)
      last = Primitive.min(last, finish)

      isort_block!(left, right, block)
      isort_block!(right, last, block)

      left += 2 * width
    end

    width = 7
    while width < size
      left = 0
      while left < finish
        right = left + width
        right = Primitive.min(right, finish)
        last = left + (2 * width)
        last = Primitive.min(last, finish)

        bottom_up_merge_block(left, right, last, source, scratch, block)
        left += 2 * width
      end

      source, scratch = scratch, source
      width *= 2
    end

    Primitive.steal_array_storage(self, source)

    self
  end
  private :mergesort_block!

  def bottom_up_merge_block(left, right, last, source, scratch, block)
    left_index = left
    right_index = right
    i = left

    while i < last
      left_element = source.at(left_index)
      right_element = source.at(right_index)

      if left_index < right && (right_index >= last || block.call(left_element, right_element) <= 0)
        scratch[i] = left_element
        left_index += 1
      else
        scratch[i] = right_element
        right_index += 1
      end

      i += 1
    end
  end
  private :bottom_up_merge_block

  # Insertion sort in-place between the given indexes.
  def isort!(left, right)
    i = left + 1

    while i < right
      j = i

      while j > left
        jp = j - 1
        el1 = at(jp)
        el2 = at(j)

        unless cmp = (el1 <=> el2)
          raise ArgumentError, "comparison of #{el1.inspect} with #{el2.inspect} failed (#{j})"
        end

        break unless cmp > 0

        self[j] = el1
        self[jp] = el2

        j = jp
      end

      i += 1
    end
  end
  private :isort!

  # Insertion sort in-place between the given indexes using a block.
  def isort_block!(left, right, block)
    i = left + 1

    while i < right
      j = i

      while j > left
        el1 = at(j - 1)
        el2 = at(j)
        block_result = block.call(el1, el2)

        if Primitive.nil? block_result
          raise ArgumentError, 'block returned nil'
        elsif block_result > 0
          self[j] = el1
          self[j - 1] = el2
          j -= 1
        else
          break
        end
      end

      i += 1
    end
  end
  private :isort_block!

  def reverse!
    Primitive.check_frozen self
    return self unless size > 1

    i = 0
    while i < size / 2
      swap i, size-i-1
      i += 1
    end

    self
  end

  def slice!(start, length = undefined)
    Primitive.check_frozen self

    if Primitive.undefined? length
      if Primitive.is_a?(start, Range)
        range = start
        out = self[range]

        range_start, range_length = Primitive.range_normalized_start_length(range, size)
        range_end = range_start + range_length - 1
        if range_end >= size
          range_end = size - 1
          range_length = size - range_start
        end

        if range_start < size && range_start >= 0 && range_end < size && range_end >= 0 && range_length > 0
          delete_range(range_start, range_length)
        end
      else
        # make sure that negative values are not passed through to the
        # []= assignment
        start = Primitive.rb_num2int start
        start = start + size if start < 0

        # This is to match the MRI behaviour of not extending the array
        # with nil when specifying an index greater than the length
        # of the array.
        return out unless start >= 0 and start < size

        out = at start

        # Check for shift style.
        if start == 0
          self[0] = nil
          self.shift
        else
          delete_range(start, 1)
        end
      end
    else
      start = Primitive.rb_num2int start
      length = Primitive.rb_num2int length
      return nil if length < 0

      out = self[start, length]

      if start < 0
        start = size + start
      end
      if start + length > size
        length = size - start
      end

      if start < size && start >= 0
        delete_range(start, length)
      end
    end

    out
  end

  def delete_range(index, del_length)
    reg_start = index + del_length
    reg_length = size - reg_start
    if reg_start <= size
      # copy tail
      self[index, reg_length] = self[reg_start, reg_length]

      self.pop(del_length)
    end
  end
  private :delete_range

  def uniq!(&block)
    Primitive.check_frozen self
    result = uniq(&block)

    if self.size == result.size
      nil
    else
      Primitive.steal_array_storage(self, result)
      self
    end
  end

  def sort!(&block)
    Primitive.check_frozen self

    Primitive.steal_array_storage(self, sort(&block))
  end

  private

  def swap(a, b)
    temp = at(a)
    self[a] = at(b)
    self[b] = temp
  end
end
