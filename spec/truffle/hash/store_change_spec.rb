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

require_relative '../../ruby/spec_helper'

# Calling #hash or #eql? on a key runs arbitrary Ruby code, which can change the internal store of the Hash
# in the middle of a Hash operation: sharing the Hash with another Thread converts it to a concurrent store,
# and adding entries can promote it to a bigger store. The operation must then continue on the new store.
describe "A Hash whose store changes while calling #hash on a key" do
  before :each do
    @key_class = Class.new do
      attr_accessor :table, :on_hash

      def hash
        if (callback = @on_hash)
          @on_hash = nil
          callback.call(@table)
        end
        1
      end

      def eql?(other)
        other.is_a?(self.class)
      end
    end

    @share = -> table { Thread.new { table }.join } # makes the Hash reachable from another Thread
    @grow = -> table { 10.times { |i| table[i] = i } }
  end

  def key(hash, on_hash)
    key = @key_class.new
    key.table = hash
    key.on_hash = on_hash
    key
  end

  [["small", -> { { 1 => 1 } }], ["big", -> { { 1 => 1, 2 => 2, 3 => 3, 4 => 4 } }]].each do |kind, initial|
    describe "for a #{kind} Hash" do
      it "shared during Hash#[]" do
        hash = initial.call
        hash[@key_class.new] = :value
        hash[key(hash, @share)].should == :value
        hash[key(hash, @share)].should == :value
        hash[key(hash, nil)].should == :value
      end

      it "shared during Hash#[] of a missing key" do
        hash = initial.call
        hash[key(hash, @share)].should == nil
        hash[key(hash, nil)].should == nil
      end

      it "shared during Hash#[]=" do
        hash = initial.call
        size = hash.size
        hash[key(hash, @share)] = :value
        hash.size.should == size + 1
        hash.keys.size.should == size + 1
        hash[key(hash, nil)].should == :value
      end

      it "shared during Hash#[]= of an existing key" do
        hash = initial.call
        hash[@key_class.new] = :value
        size = hash.size
        hash[key(hash, @share)] = :new_value
        hash.size.should == size
        hash.keys.size.should == size
        hash[key(hash, nil)].should == :new_value
      end

      it "shared during Hash#delete" do
        hash = initial.call
        hash[@key_class.new] = :value
        size = hash.size
        hash.delete(key(hash, @share)).should == :value
        hash.size.should == size - 1
        hash.keys.size.should == size - 1
        hash[key(hash, nil)].should == nil
      end

      it "shared during Hash#delete of a missing key" do
        hash = initial.call
        size = hash.size
        hash.delete(key(hash, @share)).should == nil
        hash.size.should == size
        hash.keys.size.should == size
      end

      it "grown during Hash#[]" do
        hash = initial.call
        hash[@key_class.new] = :value
        hash[key(hash, @grow)].should == :value
        hash.size.should == hash.keys.size
      end

      it "grown during Hash#[]=" do
        hash = initial.call
        hash[key(hash, @grow)] = :value
        hash.size.should == hash.keys.size
        hash[key(hash, nil)].should == :value
        (0...10).each { |i| hash[i].should == i }
      end

      it "grown during Hash#delete" do
        hash = initial.call
        hash[@key_class.new] = :value
        hash.delete(key(hash, @grow)).should == :value
        hash.size.should == hash.keys.size
        hash[key(hash, nil)].should == nil
        (0...10).each { |i| hash[i].should == i }
      end
    end
  end

  it "grown from empty during Hash#[]=" do
    hash = {}
    hash[key(hash, @grow)] = :value
    hash.size.should == 11
    hash.keys.size.should == 11
    hash[key(hash, nil)].should == :value
  end

  it "shared during Hash#[]= on an empty Hash" do
    hash = {}
    hash[key(hash, @share)] = :value
    hash.size.should == 1
    hash.keys.size.should == 1
    hash[key(hash, nil)].should == :value
  end
end
