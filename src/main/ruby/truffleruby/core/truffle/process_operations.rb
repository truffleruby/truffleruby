# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
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

# Copyright (C) 1993-2017 Yukihiro Matsumoto. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
# 1. Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
# OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.

module Truffle
  module ProcessOperations
    # See https://github.com/ruby/ruby/blob/v2_7_2/process.c#L2514
    # '=' is only considered if before the first space
    SHELL_META_CHAR_PATTERN = /[*?{}\[\]<>()~&|\\$;'`"\n#]|\A\S*=/n

    def self.coerce_rlimit_resource(resource)
      case resource
      when Integer
        return resource
      when Symbol, String
        # do nothing
      else
        unless r = Truffle::Type.rb_check_convert_type(resource, String, :to_str)
          return Primitive.convert_with_to_int resource
        end

        resource = r
      end

      constant = "RLIMIT_#{resource}"
      unless Process::Constants.const_defined? constant
        raise ArgumentError, "invalid resource name: #{constant}"
      end
      Process::Constants.const_get constant
    end

    @maxgroups = 32
    def self.maxgroups
      @maxgroups
    end

    def self.maxgroups=(maxgroups)
      @maxgroups = maxgroups
    end

    def self.exec(*args)
      exe = Execute.new
      begin
        exe.parse(*args)
        exe.spawn_setup(true)
        exe.exec
      ensure
        exe.close_files
      end
    end

    def self.nanoseconds_to_unit(nanoseconds, unit)
      case unit # ordered by expected frequency
      when :float_second, nil
        nanoseconds / 1e9
      when :nanosecond
        nanoseconds
      when :microsecond
        nanoseconds / 1_000
      when :float_microsecond
        nanoseconds / 1e3
      when :float_millisecond
        nanoseconds / 1e6
      when :second
        nanoseconds / 1_000_000_000
      when :millisecond
        nanoseconds / 1_000_000
      else
        raise ArgumentError, "unexpected unit: #{unit}"
      end
    end

    def self.spawn(*args)
      exe = Execute.new
      begin
        exe.parse(*args)
        exe.spawn_setup(false)
        exe.spawn
      ensure
        exe.close_files
      end
    end

    def self.wait(input_pid, flags, set_status, raise_on_error)
      input_pid = Primitive.convert_with_to_int input_pid
      flags ||= 0

      FFI::MemoryPointer.new(:int, 4) do |ptr|
        pid = Truffle::POSIX.truffleposix_waitpid(input_pid, flags, ptr)
        if pid == 0
          return nil
        elsif raise_on_error && pid == -1
          Errno.handle "No child process: #{input_pid}"
        else
          ints = ptr.read_array_of_int(4)
          exitcode, termsig, stopsig, = ints.map { |e| e == -1000 ? nil : e }
          raw_status = ints.last

          status = Process::Status.new(pid, exitcode, termsig, stopsig, raw_status)
          Primitive.thread_set_return_code status if set_status

          status
        end
      end
    end

    class Execute
      def initialize
        @options = {}
        @files_for_child = []
      end

      def close_files
        @files_for_child.each { |f| f.close }
      end

      # Turns the various varargs incantations supported by Process.spawn into a
      # [env, prog, argv, redirects, options] tuple.
      #
      # The following method signature is supported:
      #   Process.spawn([env], command, ..., [options])
      #
      # The env and options hashes are optional. The command may be a variable
      # number of strings or an Array full of strings that make up the new process's
      # argv.
      #
      # Assigns @environment, @command, @argv, @redirects, @options. All
      # elements are guaranteed to be non-nil. When no env or options are
      # given, empty hashes are returned.
      def parse(env_or_cmd, *args)
        if options = Truffle::Type.try_convert(args.last, Hash, :to_hash)
          args.pop
        end

        options = options ? @options.merge(options) : @options

        if env = Truffle::Type.try_convert(env_or_cmd, Hash, :to_hash)
          unless command = args.shift
            raise ArgumentError, 'command argument expected'
          end
        else
          command = env_or_cmd
        end

        if args.empty? and cmd = Truffle::Type.try_convert(command, String, :to_str)
          @command = cmd
          @argv = []
        else
          if cmd = Truffle::Type.try_convert(command, Array, :to_ary)
            raise ArgumentError, 'wrong first argument' unless cmd.size == 2
            command = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(cmd[0]))
            name = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(cmd[1]))
          else
            name = command = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(command))
          end

          argv = [name]
          args.each { |arg| argv << Truffle::Type.check_null_safe(Primitive.convert_with_to_str(arg)) }

          @command = command
          @argv = argv
        end

        @command = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(@command))

        if @argv.empty? # A single String for both command and arguments
          if should_use_shell?(@command)
            @command, @argv = '/bin/sh', ['sh', '-c', @command]
          else
            # No shell processing, and posix_spawnp will do the lookup of the command if needed
            @command = @command.strip
            if @command.empty?
              # Special case for command == "" as split does not work
              @argv = [@command]
            else
              split_command, *split_args = @command.split(' ')
              @command = split_command
              @argv = [split_command] + split_args
            end
          end
        else
          # If arguments are explicitly passed, the semantics of this method (defined in Ruby) are to run the
          # command directly. posix_spawnp(3) will lookup the full path to the command if needed.
        end

        parse_options(options)

        @add_to_env = {}
        if env
          env.each_pair do |key, value|
            @add_to_env[convert_env_key(key)] = convert_env_value(value)
          end
        end
      end

      def parse_options(options)
        options.each do |key, value|
          case key
          when IO, Integer, :in, :out, :err
            child_fd = convert_io_fd key
            parent_fd = convert_to_fd value, child_fd
            redirect child_fd, parent_fd
          when Array

            # When redirecting multiple fds to one file, as in
            #
            # [:out, :err] => "/some/file"
            #
            # We want this to be equivalent to
            #
            # :hidden_fd => "/some/file", :out => :hidden_fd, :err => :hidden_fd
            #
            # opening the destination once, and only then
            # redirecting everything to that destination.
            #
            # Therefore we open all files in the parent process,
            # and only specify redirection for the child process.
            #
            # We don't use open actions for this as we want to
            # guarantee the order and need to work around a bug in
            # old versions of glibc which do not correctly
            # duplicate the string containing the file path.

            fds = key.map { |k| convert_io_fd(k) }
            parent_fd = convert_to_fd value, fds.first
            fds.each { |fd| redirect fd, parent_fd }
          when :unsetenv_others
            if value
              @options[:unsetenv_others] = true
            end
          when :pgroup
            if Primitive.true?(value)
              value = 0
            elsif value
              value = Primitive.convert_with_to_int value
              raise ArgumentError, "negative process group ID : #{value}" if value < 0
            else
              value = -1
            end
            @options[key] = value
          when :chdir
            @options[key] = Truffle::Type.coerce_to_path(value)
          when :umask
            @options[key] = value
          when :close_others
            @options[key] = value
          else
            raise ArgumentError, "unknown exec option: #{key.inspect}"
          end
        end
      end

      def redirect(child_fd, parent_fd)
        map = (@options[:redirect_fd] ||= [])
        if Primitive.nil?(parent_fd)
          fds_to_close = (@options[:fds_to_close] ||= [])
          fds_to_close << child_fd
        elsif child_fd == parent_fd
          # posix_spawn_file_actions_adddup2() noops (and so keeps the FD_CLOEXEC flag) on older OS and macOS:
          # https://pubs.opengroup.org/onlinepubs/000095399/functions/posix_spawn_file_actions_adddup2.html
          # This means the fd is actually closed in the child process.
          # Workaround by dup'ing the fd so it's never noop, and it has no FD_CLOEXEC flag, as needed to use it in the child.

          # autoclose: false means don't actually close(2) on IO#close (this IO instance does not own the fd)
          io = IO.for_fd(parent_fd, autoclose: false)
          # copies with F_DUPFD_CLOEXEC, which is nice to automatically close the copy in the child
          copy = io.dup
          @files_for_child << copy
          map << child_fd << copy.fileno
        else
          map << child_fd << parent_fd
        end
      end

      def convert_io_fd(obj)
        case obj
        when Integer
          obj
        when :in
          0
        when :out
          1
        when :err
          2
        when IO
          obj.fileno
        else
          raise ArgumentError, "wrong exec option: #{obj.inspect}"
        end
      end

      def convert_to_fd(obj, target)
        case obj
        when Integer
          obj
        when :in
          0
        when :out
          1
        when :err
          2
        when :close
          nil
        when IO
          obj.fileno
        when String
          open_file_for_child(obj, default_mode(target), 0644)
        when Array
          case obj.size
          when 1
            open_file_for_child(obj[0], File::RDONLY, 0644)
          when 2
            if obj[0] == :child
              fd = convert_to_fd obj[1], target
              Primitive.is_a?(fd, Integer) ?  -(fd + 1) : fd
            else
              open_file_for_child(obj[0], convert_file_mode(obj[1]), 0644)
            end
          when 3
            open_file_for_child(obj[0], convert_file_mode(obj[1]), obj[2])
          end
        else
          if obj.respond_to?(:to_io)
            obj.to_io.fileno
          else
            raise ArgumentError, "wrong exec redirect: #{obj.inspect}"
          end
        end
      end

      def open_file_for_child(name, mode, perms)
        f = File.new(name, mode, perms)
        @files_for_child << f
        f.fileno
      end

      def default_mode(target)
        if target == 1 or target == 2
          OFLAGS['w']
        else
          OFLAGS['r']
        end
      end

      def convert_file_mode(obj)
        case obj
        when Integer
          obj
        when String
          OFLAGS[obj]
        when nil
          OFLAGS['r']
        else
          Primitive.convert_with_to_int obj
        end
      end

      def convert_env_key(key)
        key = Truffle::Type.check_null_safe(Primitive.convert_with_to_str(key))

        if key.include?('=')
          raise ArgumentError, "environment name contains a equal : #{key}"
        end

        key
      end

      def convert_env_value(value)
        return nil if Primitive.nil? value
        Truffle::Type.check_null_safe(Primitive.convert_with_to_str(value))
      end

      # Mapping of string open modes to integer oflag versions.
      OFLAGS = {
        'r'  => File::RDONLY,
        'r+' => File::RDWR   | File::CREAT,
        'w'  => File::WRONLY | File::CREAT  | File::TRUNC,
        'w+' => File::RDWR   | File::CREAT  | File::TRUNC,
        'a'  => File::WRONLY | File::APPEND | File::CREAT,
        'a+' => File::RDWR   | File::APPEND | File::CREAT
      }

      def spawn_setup(alter_process)
        env = @options.delete(:unsetenv_others) ? {} : ENV.to_hash

        @add_to_env.each_pair do |key, value|
          if value
            env[key] = value
          else
            env.delete(key)
          end
        end

        @env_array = env.map { |k, v| "#{k}=#{v}" }

        if alter_process
          if pgroup = @options[:pgroup]
            Process.setpgid(0, pgroup)
          end

          if mask = @options[:mask]
            File.umask(mask)
          end

          if chdir = @options[:chdir]
            Dir.chdir(chdir)
          end

          if @options[:close_others]
            # TODO (kjmenard 27-Nov-18) There's a race here if another thread opens a file and sets it to close-on-exec=false.
            Dir.each_child('/dev/fd') do |entry|
              fd = entry.to_i

              if fd > 2
                flags = Truffle::POSIX.fcntl(fd, File::F_GETFD, 0)
                Truffle::POSIX.fcntl(fd, File::F_SETFD, flags | File::FD_CLOEXEC) unless flags < 0
              end
            end
          end

          if redirect_fd = @options[:redirect_fd]
            redirect_fd.each_slice(2) do |child_fd, parent_fd|
              redirect_file_descriptor(child_fd, parent_fd, alter_process)
            end
          end
        end

        nil
      end

      def safe_close_on_exec?(fd)
        flags = Truffle::POSIX.fcntl(fd, File::F_GETFD, 0)
        (flags & File::FD_CLOEXEC) != 0
      end

      def redirect_file_descriptor(child_fd, parent_fd, alter_process)
        parent_fd = (-parent_fd + 1) if parent_fd < 0
        if alter_process && child_fd == parent_fd
          flags = Truffle::POSIX.fcntl(child_fd, File::F_GETFD, 0)
          if flags >= 0 && (flags & File::FD_CLOEXEC) != 0
            Truffle::POSIX.fcntl(child_fd, File::F_SETFD, flags ^ File::FD_CLOEXEC)
          end
        end
        result = Truffle::POSIX.dup2(parent_fd, child_fd)
        Errno.handle if result < 0
      end

      def spawn
        log_command 'spawn'

        # Check if the command exists before posix_spawnp to avoid zombie processes
        # if posix_spawnp returned a pid (e.g. on Linux) but the command is not found/executable.
        # Also sets $? like MRI does when spawn raises.
        begin
          @command = resolve_in_path(@command)
        rescue Errno::ENOENT, Errno::EACCES
          Primitive.thread_set_return_code Process::Status.new(-1, 127, nil, nil)
          raise
        end

        pid = posix_spawnp @command, @argv, @env_array, @options
        raise SystemCallError.new("posix_spawnp #{@command}", -pid) if pid < 0
        pid
      end

      def posix_spawnp(command, args, env_array, options)
        redirects = []
        pgroup = -1
        fds_to_close = []
        chdir = nil

        options.each_pair do |key, value|
          case key
          when :redirect_fd
            redirects = value.map do |fd|
              if fd < 0
                # :child fd
                -fd - 1
              else
                fd
              end
            end
          when :pgroup
            pgroup = value
          when :close_others
            if value
              # TODO (kjmenard 27-Nov-18) There's a race here if another thread opens a file and sets it to close-on-exec=false or an open FD is closed.
              Dir.each_child('/dev/fd') do |entry|
                fd = entry.to_i

                # We never want to close STDIO. Other FDs only need to be explicitly closed if
                # they're not already configured to close-on-exec.
                if fd > 2 && !safe_close_on_exec?(fd)
                  fds_to_close << fd
                end
              end
            end
          when :chdir
            chdir = value
          when :fds_to_close
            value.each { |fd| fds_to_close << fd }
          else
            raise "Unknown spawn option: #{key}"
          end
        end

        use_helper = chdir || @add_to_env['PATH']
        if use_helper
          # Go through spawn-helper to change the working dir and then execve()
          spawn_helper = "#{Truffle::Boot.ruby_home}/lib/truffle/spawn-helper"
          raise "#{spawn_helper} does not exist!" unless File.exist?(spawn_helper)
          cwd = chdir || Dir.pwd
          args = [spawn_helper, cwd, command, *args]
          command = spawn_helper
        end

        Truffle::POSIX.with_array_of_ints(redirects) do |redirects_ptr|
          Truffle::POSIX.with_array_of_strings_pointer(args) do |argv|
            Truffle::POSIX.with_array_of_strings_pointer(env_array) do |env|
              Truffle::POSIX.with_array_of_ints(fds_to_close) do |fds_to_close_ptr|
                Truffle::POSIX.truffleposix_posix_spawnp(command, argv, env, redirects.size, redirects_ptr,
                                                         pgroup, fds_to_close.size, fds_to_close_ptr)
              end
            end
          end
        end
      end

      def exec
        log_command('exec')
        # exec validates the command only if it searches in $PATH
        if should_search_path?(@command)
          @command = resolve_in_path(@command)
        end

        Truffle::POSIX.with_array_of_strings_pointer(@argv) do |argv|
          Truffle::POSIX.with_array_of_strings_pointer(@env_array) do |env|
            ret = Truffle::POSIX.execve(@command, argv, env)
            Errno.handle if ret == -1
          end
        end

        raise SystemCallError.new('execve() should not return', 0)
      end

      Truffle::Boot.delay do
        LOG_SUBPROCESS = Truffle::Boot.get_option 'log-subprocess'
      end

      def log_command(type)
        if LOG_SUBPROCESS
          env = @add_to_env.map { |k,v| "#{k}=#{v}" }.join(' ')
          Truffle::Debug.log_info "#{type}: #{env}#{' ' unless env.empty?}#{@argv.join(' ')}"
        end
      end

      def should_use_shell?(command)
        command.b.match?(SHELL_META_CHAR_PATTERN)
      end

      def should_search_path?(command)
        !command.include?(File::SEPARATOR)
      end

      def resolve_in_path(command)
        unless should_search_path?(command)
          raise Errno::ENOENT, command unless File.exist?(command)
          raise Errno::EACCES, command unless File.file?(command) && File.executable?(command)

          return command
        end

        path = @add_to_env['PATH'] || ENV['PATH']

        if path
          path.split(File::PATH_SEPARATOR).each do |dir|
            file = File.join(dir, command)
            return file if File.file?(file) && File.executable?(file)
          end
        end
        raise Errno::ENOENT, command
      end
    end
  end
end
