# frozen_string_literal: true

# Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
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

class Dir
  include Enumerable

  attr_reader :path
  alias_method :to_path, :path

  def initialize(path, options = undefined)
    path = Truffle::Type.coerce_to_path path
    Errno.handle path unless initialize_internal(path, options)
  end

  private def initialize_internal(path, options = undefined)
    @path = path

    if Primitive.undefined? options
      enc = Encoding.filesystem
    else
      options = Truffle::Type.coerce_to options, Hash, :to_hash
      enc = options[:encoding]
      enc = Truffle::Type.coerce_to_encoding enc if enc
    end

    @encoding = enc || Encoding.filesystem

    @ptr = Truffle::POSIX.opendir(path)
    @ptr.null? ? nil : self
  end

  private def initialize_from_file_descriptor(fd)
    @path = nil
    @encoding = Encoding.filesystem
    @ptr = Truffle::POSIX.fdopendir(fd)

    if @ptr.null?
      Errno.handle('fdopendir')
    end
  end

  private def ensure_open
    raise IOError, 'closed directory' if closed?
  end

  def fileno
    ensure_open
    Truffle::POSIX.dirfd(@ptr)
  end

  def pos
    ensure_open
    pos = Truffle::POSIX.telldir(@ptr)
    Errno.handle if pos == -1
    pos
  end
  alias_method :tell, :pos

  def seek(pos)
    ensure_open
    Truffle::POSIX.seekdir(@ptr, pos)
    self
  end

  def pos=(position)
    seek(position)
    position
  end

  def rewind
    ensure_open
    Truffle::POSIX.truffleposix_rewinddir(@ptr)
    self
  end

  def read
    ensure_open
    Truffle::DirOperations.readdir_name(self)
  end

  def chdir(&block)
    Dir.fchdir(fileno, &block)
  end

  def close
    unless closed?
      Truffle::POSIX.closedir(@ptr)
      @ptr = nil
    end
  end

  private def closed?
    Primitive.nil? @ptr
  end

  def each
    return to_enum unless block_given?

    rewind

    while s = read
      yield s
    end

    self
  end

  def children
    rewind

    ret = []
    while s = read
      ret << s if s != '.' and s != '..'
    end
    ret
  end

  def each_child
    return to_enum(:each_child) unless block_given?

    rewind

    while s = read
      yield s unless s == '.' or s == '..'
    end
    self
  end

  def inspect
    "#<#{Primitive.class(self)}:#{@path}>"
  end

  class << self

    # This seems silly, I know. But we do this to make Dir more resistant to people
    # screwing with ::File later (ie, fakefs)
    PrivateFile = ::File

    def open(path, options = undefined)
      dir = new path, options
      if block_given?
        begin
          yield dir
        ensure
          dir.close
        end
      else
        dir
      end
    end

    def children(*args)
      each_child(*args).to_a
    end

    def each_child(path, options = undefined)
      return to_enum(:each_child, path, options) unless block_given?

      open(path, options) do |dir|
        while s = dir.read
          yield s unless s == '.' or s == '..'
        end
      end
    end

    def entries(path, options = undefined)
      ret = []

      open(path, options) do |dir|
        while s = dir.read
          ret << s
        end
      end

      ret
    end

    def empty?(path)
      path = Truffle::Type.coerce_to_path(path)
      if Truffle::FileOperations.exist?(path) and !PrivateFile.directory?(path)
        return false
      end

      open(path) do |dir|
        while s = dir.read
          return false if s != '.' and s != '..'
        end
      end
      true
    end

    def exist?(path)
      PrivateFile.directory?(path)
    end

    def home(user = nil)
      user = StringValue(user) unless Primitive.nil?(user)

      if user and !user.empty?
        ptr = Truffle::POSIX.truffleposix_get_user_home(user)
        if !ptr.null?
          home = ptr.read_string.force_encoding(Encoding.filesystem)
          Truffle::POSIX.truffleposix_free ptr
          raise ArgumentError, "user #{user} does not exist" if home.empty?
          home
        else
          Errno.handle
        end
      else
        home = ENV['HOME'].dup
        if home
          home = home.dup.force_encoding(Encoding.filesystem)
        else
          ptr = Truffle::POSIX.truffleposix_get_current_user_home
          if !ptr.null?
            home = ptr.read_string.force_encoding(Encoding.filesystem)
            Truffle::POSIX.truffleposix_free ptr
            raise ArgumentError, "couldn't find home for uid '#{Process.uid}'" if home.empty?
            home
          else
            Errno.handle
          end
        end

        raise ArgumentError, 'non-absolute home' unless home.start_with?('/')
        home
      end
    end

    def [](*patterns, base: nil, sort: true)
      if patterns.size == 1
        pattern = Truffle::Type.coerce_to_path(patterns[0], false)
        return [] if pattern.empty?
        raise ArgumentError, 'nul-separated glob pattern is deprecated' if pattern.include? "\0"
        patterns = [pattern]
      end

      glob patterns, base: base, sort: sort
    end

    def glob(pattern, flags = 0, base: nil, sort: true, &block)
      if Primitive.is_a?(pattern, Array)
        patterns = pattern
      else
        pattern = Truffle::Type.coerce_to_path(pattern, false)

        return [] if pattern.empty?
        raise ArgumentError, 'nul-separated glob pattern is deprecated' if pattern.include? "\0"
        patterns = [pattern]
      end

      matches = []
      index = 0
      flags |= File::FNM_GLOB_NOSORT if Primitive.false?(sort)

      normalized_base = if Primitive.nil? base
                          nil
                        else
                          path = Truffle::Type.coerce_to_path(base)
                          path.empty? ? '.' : path
                        end

      patterns.each do |pat|
        pat = Truffle::Type.coerce_to_path pat
        enc = Primitive.encoding_compatible? pat, Encoding::US_ASCII
        unless enc
          raise Encoding::CompatibilityError, "incompatible character encodings: #{pat.encoding.name} and US-ASCII"
        end
        Dir::Glob.glob normalized_base, pat, flags, matches

        total = matches.size
        while index < total
          matches[index] = matches[index].force_encoding(enc) unless matches[index].encoding == enc
          index += 1
        end
      end

      if block
        matches.each(&block)
        nil
      else
        matches
      end
    end

    def foreach(path, **options)
      return to_enum(:foreach, path, **options) unless block_given?

      open(path, **options) do |dir|
        while s = dir.read
          yield s
        end
      end

      nil
    end

    def for_fd(fd)
      dir = Dir.allocate
      dir.send(:initialize_from_file_descriptor, Primitive.rb_num2int(fd))
      dir
    end

    def chdir(path = ENV['HOME'])
      path = Truffle::Type.coerce_to_path path
      path = path.dup.force_encoding(Encoding::LOCALE) if path.encoding == Encoding::BINARY

      if block_given?
        original_path = self.getwd
        Primitive.dir_set_truffle_working_directory(path)
        ret = Truffle::POSIX.chdir path
        Errno.handle(path) if ret != 0

        begin
          yield path
        ensure
          Primitive.dir_set_truffle_working_directory(original_path)
          ret = Truffle::POSIX.chdir original_path
          Errno.handle(original_path) if ret != 0
        end
      else
        Primitive.dir_set_truffle_working_directory(path)
        ret = Truffle::POSIX.chdir path
        Errno.handle path if ret != 0
        ret
      end
    end

    def fchdir(fd)
      fd = Primitive.rb_num2int(fd)

      if block_given?
        original_path = getwd
        original_dir = Dir.new(original_path)

        ret = Truffle::POSIX.fchdir fd
        Errno.handle('fchdir') if ret != 0
        Primitive.dir_set_truffle_working_directory(getwd)

        begin
          yield
        ensure
          begin
            ret = Truffle::POSIX.fchdir original_dir.fileno
            Errno.handle('fchdir') if ret != 0

            Primitive.dir_set_truffle_working_directory(original_path)
          ensure
            original_dir.close
          end
        end
      else
        ret = Truffle::POSIX.fchdir fd
        Errno.handle('fchdir') if ret != 0
        Primitive.dir_set_truffle_working_directory(getwd)
        ret
      end
    end

    def mkdir(path, mode = 0777)
      path = Truffle::Type.coerce_to_path(path)
      if mode
        mode = Truffle::Type.rb_convert_type(mode, Integer, :to_int)
      end
      ret = Truffle::POSIX.mkdir(path, mode)
      Errno.handle path if ret != 0
      ret
    end

    def rmdir(path)
      ret = Truffle::POSIX.rmdir(Truffle::Type.coerce_to_path(path))
      Errno.handle path if ret != 0
      ret
    end
    alias_method :delete, :rmdir
    alias_method :unlink, :rmdir

    def getwd
      ptr = Primitive.io_thread_buffer_allocate(Truffle::Platform::PATH_MAX)
      begin
        wd = Truffle::POSIX.getcwd(ptr, Truffle::Platform::PATH_MAX)
        Errno.handle unless wd

        Truffle::Type.external_string wd
      ensure
        Primitive.io_thread_buffer_free(ptr)
      end
    end
    alias_method :pwd, :getwd

    def chroot(path)
      path = Truffle::Type.coerce_to_path(path)
      ret = Truffle::POSIX.chroot path
      Errno.handle path if ret != 0
      ret
    end
  end
end
