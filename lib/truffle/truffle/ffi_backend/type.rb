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
  module NativeType
  end

  class Type
    attr_reader :size, :alignment

    # The public form is FFI::Type.new(value), where value is a Type to copy or the Integer id of a native type.
    # The other form is used by subclasses in this backend.
    def initialize(size, alignment = nil, nfi_type = nil)
      if Primitive.nil?(alignment)
        value = size
        if Primitive.is_a?(value, FFI::Type)
          size = value.size
          alignment = value.alignment
          nfi_type = value.nfi_type
        elsif Primitive.is_a?(value, Integer)
          # Like in the C backend, such a Type has the size and alignment of void
          size = 1
          alignment = 1
        else
          raise ArgumentError, 'wrong type'
        end
      end

      @size = size
      @alignment = alignment
      @nfi_type = nfi_type
    end

    def nfi_type
      @nfi_type or raise "Unknown nfi_type for #{inspect}"
    end

    def get_at(pointer, offset)
      raise "unimplemented for #{Primitive.class(self)}"
    end

    def put_at(pointer, offset, value)
      raise "unimplemented for #{Primitive.class(self)}"
    end

    class Builtin < Type
      attr_reader :name

      def initialize(name, nfi_type, size, alignment)
        super(size, alignment, nfi_type)
        @name = name
        @unsigned = name.to_s.start_with?('UINT') || name == :ULONG || name == :BOOL

        accessor = name.downcase
        if accessor == :bool
          eval <<~RUBY
          def self.get_at(pointer, offset)
            pointer.get_uint8(offset) != 0
          end

          def self.put_at(pointer, offset, value)
            pointer.put_uint8(offset, value ? 1 : 0)
          end
          RUBY
        else
          eval <<~RUBY
          def self.get_at(pointer, offset)
            pointer.get_#{accessor}(offset)
          end

          def self.put_at(pointer, offset, value)
            pointer.put_#{accessor}(offset, value)
          end
          RUBY
        end
      end

      def native_type
        self
      end

      def unsigned?
        @unsigned
      end

      def signed2unsigned(value)
        raise ArgumentError unless @unsigned
        if value >= 0
          value
        else
          value + (1 << (@size * 8))
        end
      end

      def inspect
        "#<#{Primitive.class(self)}:#{@name} size=#{@size} alignment=#{@alignment}>"
      end
    end

    class Mapped < Type
      attr_reader :native_type
      attr_reader :converter
      alias_method :type, :native_type

      def initialize(converter)
        %i[native_type to_native from_native].each do |meth|
          unless converter.respond_to?(meth)
            raise NoMethodError, "#{meth} method not implemented for #{converter}"
          end
        end

        native_type = converter.native_type
        unless Primitive.is_a?(native_type, FFI::Type)
          raise TypeError, 'native_type did not return instance of FFI::Type'
        end

        super(native_type.size, native_type.alignment, native_type.nfi_type)
        @native_type = native_type
        @converter = converter
      end

      def to_native(*args)
        @converter.to_native(*args)
      end

      def from_native(*args)
        @converter.from_native(*args)
      end

      def get_at(pointer, offset)
        native = @native_type.get_at(pointer, offset)
        from_native(native, nil)
      end

      def put_at(pointer, offset, value)
        native = to_native(value, nil)
        @native_type.put_at(pointer, offset, native)
      end
    end

    class ArrayType < Type
      attr_reader :length, :elem_type

      def initialize(component_type, length)
        size = component_type.size * length
        alignment = component_type.alignment
        super(size, alignment, 'ARRAY')

        @elem_type = component_type
        @elem_type_size = component_type.size
        @length = length
      end

      def char_array?
        FFI::Type::INT8 == @elem_type or FFI::Type::UINT8 == @elem_type
      end

      def get_element_at(pointer, offset, index)
        @elem_type.get_at(pointer, offset + index * @elem_type_size)
      end

      def put_element_at(pointer, offset, index, value)
        @elem_type.put_at(pointer, offset + index * @elem_type_size, value)
      end
    end
    FFI::Type::Array = ArrayType
  end

  class StructByValue < Type
    attr_reader :struct_class

    def initialize(struct_class)
      layout = struct_class.instance_variable_get(:@layout)
      unless Primitive.is_a?(layout, FFI::StructLayout)
        raise TypeError, 'wrong type in @layout ivar (expected FFI::StructLayout)'
      end
      super(layout.size, layout.alignment, 'STRUCT_BY_VALUE') # layout.nfi_type)
      @struct_class = struct_class
    end

    def get_at(pointer, offset)
      @struct_class.new(pointer + offset)
    end
  end
  FFI::Type::Struct = StructByValue

  class FunctionType < Type
    attr_reader :return_type, :param_types, :enums, :blocking, :function_index

    def initialize(return_type, param_types, options = {})
      # note: @nfi_type is updated below
      super(FFI::Type::POINTER.size, FFI::Type::POINTER.alignment, FFI::Type::POINTER.nfi_type)

      varargs = options[:varargs]

      @return_type = FFI.find_type(return_type)
      param_types = param_types.map { |type| FFI.find_type(type) }
      if varargs
        # Workaround an issue with NFI and var-args and a float argument.
        # The FFI C extension also converts float to double for VariadicInvoker#invoke.
        param_types = param_types.map { |type| type == Type::FLOAT ? Type::DOUBLE : type }
      end
      @param_types = param_types
      @enums = options[:enums]
      @blocking = options[:blocking]
      @function_index = param_types.index { |type| Primitive.is_a?(type, FFI::FunctionType) }

      if varargs
        @nfi_type = nfi_varags_signature(@return_type, varargs, @param_types)
      else
        @nfi_type = nfi_fixed_signature(@return_type, @param_types)
      end
    end

    def nfi_signature
      @nfi_signature ||= Primitive.interop_eval_nfi(@nfi_type)
    end

    private def nfi_varags_signature(return_type, fixed, param_types)
      nfi_return_type = return_type.nfi_type
      nfi_args_types = param_types.map(&:nfi_type)

      fixed_args_types = nfi_args_types[0...fixed]
      var_args_types = nfi_args_types[fixed..-1]
      "(#{fixed_args_types.join(',')},...#{var_args_types.join(',')}):#{nfi_return_type}"
    end

    private def nfi_fixed_signature(return_type, param_types)
      nfi_return_type = return_type.nfi_type
      nfi_args_types = param_types.map(&:nfi_type)
      "(#{nfi_args_types.join(',')}):#{nfi_return_type}"
    end
  end
  CallbackInfo = FunctionType
  FunctionInfo = FunctionType
  Type::Function = FunctionType

  types = [
    # builtin FFI type, NFI type, size, *aliases
    [:VOID,   'VOID', 1],
    [:INT8,   'SINT8', 1, :CHAR, :SCHAR],
    [:UINT8,  'UINT8', 1, :UCHAR],
    [:INT16,  'SINT16', 2, :SHORT, :SSHORT],
    [:UINT16, 'UINT16', 2, :USHORT],
    [:INT32,  'SINT32', 4, :INT, :SINT],
    [:UINT32, 'UINT32', 4, :UINT],
    [:INT64,  'SINT64', 8, :LONG_LONG, :SLONG_LONG],
    [:UINT64, 'UINT64', 8, :ULONG_LONG],
    [:LONG,   'SINT64', 8, :SLONG],
    [:ULONG,  'UINT64', 8],
    [:FLOAT32, 'FLOAT', 4, :FLOAT],
    [:FLOAT64, 'DOUBLE', 8, :DOUBLE],
    [:LONGDOUBLE, 'LONGDOUBLE', 16],
    [:POINTER, 'POINTER', 8],
    [:STRING, 'POINTER', 8],
    [:BUFFER_IN, 'POINTER', 8],
    [:BUFFER_OUT, 'POINTER', 8],
    [:BUFFER_INOUT, 'POINTER', 8],
    [:BOOL, 'UINT8', 1],
    [:VARARGS, 'VARARGS', 1],
  ]
  types.each do |name, nfi_type, size, *aliases|
    type = Type::Builtin.new(name, nfi_type, size, size)
    FFI::Type.const_set name, type
    FFI::NativeType.const_set name, type
    FFI.const_set "TYPE_#{name}", type
    aliases.each { |a| FFI::Type.const_set a, type  }
  end
end
