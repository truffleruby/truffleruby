# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
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

module Truffle
  module StatOperations

    def self.blockdev?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFBLK
    end

    def self.chardev?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFCHR
    end

    def self.directory?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFDIR
    end

    def self.file?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFREG
    end

    def self.pipe?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFIFO
    end

    def self.setgid?(mode)
      mode & File::Stat::S_ISGID != 0
    end

    def self.setuid?(mode)
      mode & File::Stat::S_ISUID != 0
    end

    def self.sticky?(mode)
      mode & File::Stat::S_ISVTX != 0
    end

    def self.socket?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFSOCK
    end

    def self.symlink?(mode)
      mode & File::Stat::S_IFMT == File::Stat::S_IFLNK
    end

    def self.world_readable?(mode)
      if mode & File::Stat::S_IROTH == File::Stat::S_IROTH
        tmp = mode & (File::Stat::S_IRUGO | File::Stat::S_IWUGO | File::Stat::S_IXUGO)
        Primitive.convert_with_to_int tmp
      else
        nil
      end
    end

    def self.world_writable?(mode)
      if mode & File::Stat::S_IWOTH == File::Stat::S_IWOTH
        tmp = mode & (File::Stat::S_IRUGO | File::Stat::S_IWUGO | File::Stat::S_IXUGO)
        Primitive.convert_with_to_int tmp
      else
        nil
      end
    end

    def self.ftype(mode)
      if file?(mode)
        +'file'
      elsif directory?(mode)
        +'directory'
      elsif chardev?(mode)
        +'characterSpecial'
      elsif blockdev?(mode)
        +'blockSpecial'
      elsif pipe?(mode)
        +'fifo'
      elsif socket?(mode)
        +'socket'
      elsif symlink?(mode)
        +'link'
      else
        +'unknown'
      end
    end

  end
end
