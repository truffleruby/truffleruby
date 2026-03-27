# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# The part enclosed between START from MRI/END from MRI is licensed
# under LICENSE.RUBY as it is derived from lib/ruby/stdlib/digest.rb.

require_relative 'digest/version'
require_relative 'ffi'

module Truffle
  class Digest
    module Foreign
      class RbDigestMetadata < ::FFI::Struct
        layout :api_version, :int,
               :digest_len, :size_t,
               :block_len, :size_t,
               :ctx_size, :size_t,
               :init_func, ::FFI::FunctionType.new(:int, [:pointer]),
               :update_func, ::FFI::FunctionType.new(:void, [:pointer, :pointer, :size_t]),
               :finish_func, ::FFI::FunctionType.new(:int, [:pointer, :pointer])
      end
    end
  end
end


module Digest

  # START from MRI
  class ::Digest::Class
    # Creates a digest object and reads a given file, _name_.
    # Optional arguments are passed to the constructor of the digest
    # class.
    #
    #   p Digest::SHA256.file("X11R6.8.2-src.tar.bz2").hexdigest
    #   # => "f02e3c85572dc9ad7cb77c2a638e3be24cc1b5bea9fdbb0b0299c9668475c534"
    def self.file(name, *args)
      new(*args).file(name)
    end

    # Returns the base64 encoded hash value of a given _string_.  The
    # return value is properly padded with '=' and contains no line
    # feeds.
    def self.base64digest(str, *args)
      [digest(str, *args)].pack('m0')
    end
  end

  module Instance
    # Updates the digest with the contents of a given file _name_ and
    # returns self.
    def file(name)
      File.open(name, 'rb') do |f|
        buf = String.new
        while f.read(16384, buf)
          update buf
        end
      end
      self
    end

    # If none is given, returns the resulting hash value of the digest
    # in a base64 encoded form, keeping the digest's state.
    #
    # If a +string+ is given, returns the hash value for the given
    # +string+ in a base64 encoded form, resetting the digest to the
    # initial state before and after the process.
    #
    # In either case, the return value is properly padded with '=' and
    # contains no line feeds.
    def base64digest(str = nil)
      [str ? digest(str) : digest].pack('m0')
    end

    # Returns the resulting hash value and resets the digest to the
    # initial state.
    def base64digest!
      [digest!].pack('m0')
    end
  end
  # END from MRI

  NO_MESSAGE = Object.new

  def Digest.hexencode(message)
    Primitive.convert_with_to_str(message).unpack('H*').first
  end

  module Instance
    def new
      copy = clone
      copy.reset
      copy
    end

    def block_length
      raise RuntimeError, "#{Primitive.class(self).name} does not implement block_length()"
    end

    def update(message)
      raise RuntimeError, "#{Primitive.class(self).name} does not implement update()"
    end
    alias_method :<<, :update

    def reset
      raise RuntimeError, "#{Primitive.class(self).name} does not implement reset()"
    end

    def digest(message = NO_MESSAGE)
      if NO_MESSAGE == message
        clone.__send__ :finish
      else
        reset
        update message
        digested = finish
        reset
        digested
      end
    end

    def hexdigest(message = NO_MESSAGE)
      Digest.hexencode(digest(message))
    end
    alias_method :to_s, :hexdigest

    def digest!
      digested = finish
      reset
      digested
    end

    def finish
      Truffle::Digest.digest @digest
    end

    def hexdigest!
      digested = hexdigest
      reset
      digested
    end

    def digest_length
      Primitive.convert_with_to_str(digest).length
    end

    def size
      digest_length
    end
    alias_method :length, :size

    def ==(other)
      if Primitive.is_a?(other, Digest::Instance)
        self_str = self.digest
        other_str = other.digest
      else
        self_str = self.to_s
        other_str = Truffle::Type.rb_check_convert_type(other, String, :to_str)
        return false if Primitive.nil?(other_str)
      end
      Primitive.convert_with_to_str(self_str) == Primitive.convert_with_to_str(other_str)
    end

    def inspect
      "#<#{Primitive.class(self).name}: #{hexdigest}>"
    end
  end

  class ::Digest::Class
    include Instance

    def self.digest(message, *parameters)
      digest = new(*parameters)
      digest.update message
      digest.digest
    end

    def self.hexdigest(str, *args)
      Digest.hexencode(digest(str, *args))
    end
  end

  class Base < ::Digest::Class
    def self.inherited(klass)
      unless %w[Digest::MD5 Digest::SHA1 Digest::SHA2 Digest::SHA256 Digest::SHA384 Digest::SHA512].include?(klass.name)
        klass.include(Digest::Plugin)
      end
    end

    def block_length
      Truffle::Digest.digest_block_length @digest
    end

    def digest_length
      Truffle::Digest.digest_length @digest
    end

    def reset
      Truffle::Digest.reset @digest
      self
    end

    def update(str)
      str = Primitive.convert_with_to_str(str)
      Truffle::Digest.update(@digest, str)

      self
    end
    alias_method :<<, :update
  end

  module Plugin
    attr_reader :metadata

    def initialize
      super
      metadata_wrapped = Primitive.object_ivar_get(Primitive.class(self), :metadata)
      metadata_pointer = Primitive.object_hidden_var_get(metadata_wrapped, Truffle::CExt::DATA_STRUCT).data
      @metadata = Truffle::Digest::Foreign::RbDigestMetadata.new(FFI::Pointer.new(metadata_pointer))

      reset
    end

    def initialize_copy(from)
      @metadata = from.metadata
      @context = from.context.clone

      self
    end

    def context
      @context ||= Truffle::FFI::MemoryPointer.new(:uchar, metadata[:ctx_size])
    end

    def block_length
      metadata[:block_len]
    end

    def digest_length
      metadata[:digest_len]
    end

    def update(str)
      metadata[:update_func].call(context, Truffle::CExt.string_to_ffi_pointer_inplace(str), str.bytesize)

      self
    end
    alias_method :<<, :update

    def finish
      str = Truffle::FFI::MemoryPointer.new(:uchar, metadata[:digest_len], false)
      metadata[:finish_func].call(context, str)

      reset

      Primitive.pointer_read_bytes str.address, metadata[:digest_len]
    end

    def reset
      @context = nil
      if metadata[:init_func].call(context) != 1
        raise RuntimeError, 'Digest initialization failed'
      end
    end
  end

  class MD5 < Base
    def initialize
      @digest = Truffle::Digest.md5
    end
  end

  class SHA1 < Base
    def initialize
      @digest = Truffle::Digest.sha1
    end
  end

  class SHA256 < Base
    def initialize
      @digest = Truffle::Digest.sha256
    end
  end

  class SHA384 < Base
    def initialize
      @digest = Truffle::Digest.sha384
    end
  end

  class SHA512 < Base
    def initialize
      @digest = Truffle::Digest.sha512
    end
  end

  autoload :SHA2, 'digest/sha2'
end

def Digest(name)
  Digest.const_get(name.to_sym)
end
