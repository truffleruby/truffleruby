# frozen_string_literal: true
# truffleruby_primitives: true

#
# Copyright (C) 2008-2010 Wayne Meissner
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
  class Function < Pointer
    # @param [Type, Symbol] return_type return type for the function
    # @param [Array<Type, Symbol>] param_types array of parameters types
    # @param [Proc || FFI::Pointer || FFI::DynamicLibrary::Symbol] the function Proc or native address
    # @param [Hash] options see {FFI::FunctionType} for available options
    def initialize(return_type, param_types, function = nil, options = {}, &block)
      function ||= block

      if Primitive.is_a?(return_type, FunctionType) and nil == param_types
        @function_info = return_type
      else
        @function_info = FunctionType.new(return_type, param_types, options)
      end

      if Primitive.is_a?(function, FFI::DynamicLibrary::Symbol)
        @function = @function_info.nfi_signature.bind(function.handle)
        @native_wrapper = nil
        super(@function)
      elsif Primitive.is_a?(function, FFI::Pointer)
        @function = @function_info.nfi_signature.bind(function)
        @native_wrapper = nil
        super(@function)
      elsif Primitive.is_a?(function, Proc) || Primitive.is_a?(function, Method)
        @function = function
        @native_wrapper = create_native_wrapper(@function, @function_info)
        super(@native_wrapper)
      else
        raise ArgumentError, "Unknown how to convert #{function} to a function"
      end

      @autorelease = true
    end

    def call(*args, &block)
      param_types = @function_info.param_types
      return_type = @function_info.return_type
      enums = @function_info.enums
      blocking = @function_info.blocking

      args.insert(@function_info.function_index, block) if block

      unless args.size == param_types.size
        raise ArgumentError, "wrong number of arguments (given #{args.size}, expected #{param_types.size})"
      end

      args.each_with_index do |arg, i|
        args[i] = convert_ruby_to_native(param_types[i], arg, enums)
      end

      if blocking
        begin
          result = Primitive.thread_run_blocking_nfi_system_call(@function, args)
        end while Primitive.is_a?(result, Integer) and result == -1 and Errno.errno == Errno::EINTR::Errno
      else
        result = @function.call(*args)
      end

      convert_native_to_ruby(return_type, result)
    end

    private def callback(function, function_info)
      param_types = function_info.param_types
      return_type = function_info.return_type
      enums = function_info.enums

      -> *args do
        converted_args = args.dup
        converted_args.each_with_index do |arg, i|
          converted_args[i] = convert_native_to_ruby(param_types[i], arg)
        end

        result = function.call(*converted_args)

        convert_ruby_to_native(return_type, result, enums)
      end
    end

    def attach(mod, name)
      this = self
      body = -> *args, &block do
        this.call(*args, &block)
      end
      mod.define_method(name, body)
      mod.define_singleton_method(name, body)
      self
    end

    def autorelease?
      @autorelease
    end

    # Actually, we always have autorelease for FFI::Function
    def autorelease=(value)
      @autorelease = value
    end

    def free
      unless @native_wrapper
        raise RuntimeError, 'cannot free function which was not allocated'
      end

      # TODO (eregon): Actually free() the @native_wrapper eagerly
      @native_wrapper = nil
    end

    private def type
      @function_info
    end

    private def convert_ruby_to_native(type, value, enums)
      if Primitive.is_a?(type, FFI::Type::Mapped)
        type.to_native(value, nil)
      elsif enums and Primitive.is_a?(value, Symbol)
        enums.__map_symbol(value)
      elsif FFI::Type::UINT64 == type or FFI::Type::ULONG == type
        Truffle::Type.rb_num2ulong(value)
      elsif FFI::Type::FLOAT32 == type
        Truffle::Type.rb_num2dbl(value)
      elsif FFI::Type::POINTER == type
        get_pointer_value(value)
      elsif FFI::Type::STRING == type
        if Primitive.nil?(value)
          Truffle::FFI::Pointer::NULL
        else
          Truffle::Type.check_null_safe(value)
          Truffle::CExt.string_to_ffi_pointer_copy(value)
        end
      elsif Primitive.is_a?(type, FFI::FunctionType) and Primitive.is_a?(value, Proc)
        callback(value, type)
      else
        value
      end
    end

    private def convert_native_to_ruby(type, value)
      if Primitive.is_a?(type, Type::Mapped)
        # #to_native expects a Ruby object, not e.g., a NFI NativePointer
        ruby_value = convert_native_to_ruby(type.native_type, value)
        type.from_native(ruby_value, nil)
      elsif FFI::Type::POINTER == type
        FFI::Pointer.new(Truffle::Interop.as_pointer(value))
      elsif FFI::Type::BOOL == type
        value != 0
      elsif FFI::Type::STRING == type
        if Primitive.interop_null?(value)
          nil
        elsif Primitive.is_a?(value, String)
          value
        else
          FFI::Pointer.new(Truffle::Interop.as_pointer(value)).read_string_to_null
        end
      elsif FFI::Type::UINT64 == type or FFI::Type::ULONG == type
        # GR-15358: No uint64 in interop yet
        type.signed2unsigned(value)
      elsif Primitive.is_a?(type, FFI::FunctionType)
        ptr = FFI::Pointer.new(Truffle::Interop.as_pointer(value))
        FFI::Function.new(type, nil, ptr)
      else
        value
      end
    end

    # Returns a Truffle::FFI::Pointer, to correctly keep the argument alive during the native call
    private def get_pointer_value(value)
      if Primitive.is_a?(value, Truffle::FFI::Pointer)
        value
      elsif Primitive.nil?(value)
        Truffle::FFI::Pointer::NULL
      elsif Primitive.is_a?(value, String)
        Truffle::CExt.string_to_ffi_pointer_inplace(value)
      elsif value.respond_to?(:to_ptr)
        Primitive.convert_type value, Truffle::FFI::Pointer, :to_ptr
      else
        raise ArgumentError, "#{value.inspect} (#{Primitive.class(value)}) is not a pointer"
      end
    end

    private def create_native_wrapper(function, function_info)
      function_info.nfi_signature.createClosure(callback(function, function_info))
    end
  end

  class VariadicInvoker
    attr_reader :return_type

    def initialize(function, args_types, return_type, options)
      @function = function
      @return_type = return_type
      @options = options
      @fixed = args_types.map { |type| FFI.find_type(type) }.reject { |type| type == Type::VARARGS }
      @type_map = options[:type_map]

      @options[:varargs] = @fixed.size
    end

    def invoke(param_types, param_values, &block)
      Function.new(@return_type, param_types, @function, @options).call(*param_values, &block)
    end
  end
end
