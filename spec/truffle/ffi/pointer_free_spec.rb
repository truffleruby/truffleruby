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

describe "Truffle::FFI::MemoryPointer#free" do
  it "frees the memory only once when called in the block of MemoryPointer.new, like the ffi gem" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4) do |ptr|
      ptr.free
      ptr
    end
    -> { pointer.free.should.equal?(pointer) }.should_not complain
  end

  it "frees the memory only once when called twice without autorelease" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4)
    pointer.autorelease = false
    pointer.free
    -> { pointer.free.should.equal?(pointer) }.should_not complain
  end

  it "disables autorelease since there is nothing left to release" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4)
    pointer.should.autorelease?
    pointer.free
    pointer.should_not.autorelease?
    pointer.free.should.equal?(pointer)
  end
end

describe "Truffle::FFI::Pointer#free" do
  it "warns and does not free memory which the pointer did not allocate, like the ffi gem" do
    memory = Truffle::FFI::MemoryPointer.new(:int, 4)
    memory.write_int(42)
    pointer = Truffle::FFI::Pointer.new(memory.address)

    -> { pointer.free.should.equal?(pointer) }.should complain(/calling free on non allocated pointer/)
    memory.read_int.should == 42
  end

  it "warns and does not free memory which the pointer already freed, like the ffi gem" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4).slice(0, 4).dup
    pointer.should.instance_of?(Truffle::FFI::Pointer)
    pointer.free

    -> { pointer.free.should.equal?(pointer) }.should complain(/calling free on non allocated pointer/)
  end
end

describe "Truffle::FFI::Pointer#autorelease=" do
  it "does nothing once the memory was freed" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4)
    pointer.free
    pointer.autorelease = true
    pointer.should_not.autorelease?
  end

  it "does nothing for a Pointer which did not allocate the memory" do
    pointer = Truffle::FFI::Pointer.new(0x1234)
    pointer.autorelease = true
    pointer.should_not.autorelease?
  end
end

describe "Truffle::FFI::Pointer#total=" do
  it "keeps the autorelease of the memory" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4)
    pointer.total = 8
    pointer.total.should == 8
    pointer.should.autorelease?
  end

  it "keeps the ownership of the memory" do
    pointer = Truffle::FFI::MemoryPointer.new(:int, 4)
    pointer.autorelease = false
    pointer.total = 8
    pointer.autorelease = true
    pointer.should.autorelease?
  end
end
