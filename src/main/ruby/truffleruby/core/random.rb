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

class Truffle::PRNGRandomizer < Truffle::Randomizer
  def initialize
    self.seed = generate_seed
  end

  def swap_seed(new_seed)
    old_seed = self.seed
    self.seed = new_seed
    old_seed
  end

  def random(limit)
    random_integer(limit - 1)
  end
end

class Truffle::SecureRandomizer < Truffle::Randomizer
end

class Truffle::CustomRandomizer < Truffle::Randomizer
  attr_reader :value
  def initialize(value)
    @value = value
  end
end

class Random
  def self.new_seed
    Primitive.thread_randomizer.generate_seed
  end

  def self.srand(seed = undefined)
    if Primitive.undefined?(seed)
      seed = Primitive.thread_randomizer.generate_seed
    end

    seed = Primitive.convert_type(seed, Integer, :to_int)
    Primitive.thread_randomizer.swap_seed(seed)
  end

  def self.rand(limit = undefined)
    Truffle::RandomOperations.random(Primitive.thread_randomizer, limit, TypeError)
  end

  def self.urandom(size)
    size = Primitive.rb_num2long(size)
    return String.new if size == 0
    Primitive.vm_dev_urandom_bytes(size)
  end

  def self.bytes(length)
    Primitive.randomizer_bytes(Primitive.thread_randomizer, length)
  end

  def self.seed
    Primitive.thread_randomizer.seed
  end

  def initialize(seed = undefined)
    @randomizer = Truffle::PRNGRandomizer.new
    unless Primitive.undefined?(seed)
      @randomizer.swap_seed Primitive.rb_to_int(seed)
    end
  end

  def rand(limit = undefined)
    Truffle::RandomOperations.random(@randomizer, limit, TypeError)
  end

  def seed
    @randomizer.seed
  end

  def state
    @randomizer.seed
  end
  private :state

  def ==(other)
    return false unless Primitive.is_a?(other, Random)
    seed == other.seed
  end

  # Returns a random binary string.
  # The argument size specified the length of the result string.
  def bytes(length)
    Primitive.randomizer_bytes @randomizer, length
  end
end

module Random::Formatter
  def random_number(limit = undefined)
    randomizer = if Primitive.equal?(self, Random)
                   Primitive.thread_randomizer
                 elsif defined?(@randomizer)
                   @randomizer
                 else
                   Truffle::CustomRandomizer.new(self)
                 end

    # Weird case, spec'd for SecureRandom.random_number
    if Primitive.is_a?(limit, Numeric) and limit <= 0
      return randomizer.random_float
    end

    Truffle::RandomOperations.random(randomizer, limit, ArgumentError)
  end
  alias_method :rand, :random_number
end

class Random
  include Formatter
  extend Formatter
end
