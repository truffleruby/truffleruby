# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module DirOperations

    AT_SYMLINK_NOFOLLOW = Truffle::Config['platform.file.AT_SYMLINK_NOFOLLOW']
    DT_DIR = Truffle::Config['platform.file.DT_DIR']
    DT_LNK  = Truffle::Config['platform.file.DT_LNK']
    DT_UNKNOWN  = Truffle::Config['platform.file.DT_UNKNOWN']

    MULTIPLE_READS_BUFFER_SIZE = 16384

    class Entry
      def initialize(name, type)
        @name = name
        @type = type
      end

      attr_reader :name
      attr_reader :type

      def <=>(other)
        name <=> other.name
      end
    end

    def self.readdir_multiple(dir, resolve_type, exclude_self_and_parent, entries)
      dir.__send__(:ensure_open)
      dirptr = Primitive.object_ivar_get(dir, :@ptr)
      dirents = Primitive.io_thread_buffer_allocate(MULTIPLE_READS_BUFFER_SIZE)
      begin
        res = Truffle::POSIX.truffleposix_readdir_multiple(dirptr, MULTIPLE_READS_BUFFER_SIZE, resolve_type, exclude_self_and_parent, dirents)
        num_read = dirents.read_int
        offset = 4
        num_read.times do
          str_len = dirents.get_int(offset)
          offset += 4
          str = fix_entry_encoding(dir, dirents.get_string(offset, str_len))
          offset += str_len
          type = dirents.get_uchar(offset);
          offset += 1
          alignment = (offset % 4)
          offset += (4 - alignment) if alignment > 0
          entries << Entry.new(str, type)
        end

        Errno.handle unless Errno.errno == 0
        res
      ensure
        Primitive.io_thread_buffer_free(dirents)
      end
    end

    def self.readdir_name(dir)
      dirptr = Primitive.object_ivar_get(dir, :@ptr)
      entry = Truffle::POSIX.truffleposix_readdir_name(dirptr)
      Errno.handle unless entry
      return if entry.empty?
      fix_entry_encoding(dir, entry)
    end

    def self.fix_entry_encoding(dir,str)
      if str
        str = str.force_encoding(Primitive.object_ivar_get(dir, :@encoding))

        if Encoding.default_external == Encoding::US_ASCII && !str.valid_encoding?
          str.force_encoding Encoding::BINARY
        else
          enc = Encoding.default_internal
          begin
            str = str.encode(enc) if enc
          rescue EncodingError
            # If the attempt to convert fails we'll return the string as is, just like MRI.
          end
        end
      end
      str
    end
  end
end
