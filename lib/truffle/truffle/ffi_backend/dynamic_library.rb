# frozen_string_literal: true
# truffleruby_primitives: true

#
# Copyright (C) 2008-2010 Wayne Meissner
# Copyright (c) 2007, 2008 Evan Phoenix
#
# This file is part of ruby-ffi.
#
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
# * Neither the name of the Ruby FFI project nor the names of its contributors
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
#

module FFI
  class DynamicLibrary

    class Symbol < Pointer
      attr_reader :handle

      def initialize(handle)
        super(handle)
        @handle = handle
      end
    end

    RTLD_LAZY   = Truffle::Config['platform.dlopen.RTLD_LAZY']
    RTLD_NOW    = Truffle::Config['platform.dlopen.RTLD_NOW']
    RTLD_GLOBAL = Truffle::Config['platform.dlopen.RTLD_GLOBAL']
    RTLD_LOCAL  = Truffle::Config['platform.dlopen.RTLD_LOCAL']

    attr_reader :name

    def self.open(libname, flags)
      code = libname ? "load '#{libname}'" : 'default'
      begin
        handle = Primitive.interop_eval_nfi code
      rescue Polyglot::ForeignException => e
        raise LoadError, "Could not open library '#{libname || '[current process]'}': #{e.message}", cause: nil
      end
      DynamicLibrary.new(libname, handle)
    end

    def self.last_error
      raise 'unimplemented'
    end

    def initialize(name, handle)
      @name = name
      @handle = handle
    end

    def find_symbol(name)
      begin
        address = @handle[name]
      rescue NameError # not found
        address = nil
      end
      address ? Symbol.new(address) : nil
    end
    alias_method :find_function, :find_symbol
    alias_method :find_variable, :find_symbol

    def to_s
      "\#<#{Primitive.class(self)} @name=#{@name.inspect}>"
    end
    alias_method :inspect, :to_s
  end
end
