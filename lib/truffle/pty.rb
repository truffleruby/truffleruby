# frozen_string_literal: true

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

require 'fcntl'

module PTY
  class ChildExited < RuntimeError
    attr_reader :status

    def initialize(message = nil, status = nil)
      super(message)
      @status = status
    end
  end

  SLAVE_NAME_SIZE = 1024
  private_constant :SLAVE_NAME_SIZE

  # Allocates a pty and returns [master_io, slave_file], or yields them and closes them after the block.
  def self.open
    master_fd, slave_fd, slave_name = open_pty
    # Only the owner can write to the terminal
    File.chmod(0600, slave_name)

    master = IO.for_fd(master_fd, 'r+', path: "masterpty:#{slave_name}")
    master.sync = true
    slave = File.for_fd(slave_fd, 'r+', path: slave_name)
    slave.sync = true

    if block_given?
      begin
        yield [master, slave]
      ensure
        master.close unless master.closed?
        slave.close unless slave.closed?
      end
    else
      [master, slave]
    end
  end

  # Spawns the command on a newly allocated pty, which is the controlling terminal
  # and the stdin, stdout and stderr of the command.
  # Returns or yields [r, w, pid], where r and w are both the master side of the pty.
  def self.spawn(*args)
    args = [default_shell] if args.empty?

    master_fd, slave_fd, slave_name = open_pty
    slave = File.for_fd(slave_fd, 'r+')
    begin
      pid = Truffle::ProcessOperations.spawn_with_controlling_terminal(slave, *args)
    rescue Exception # rubocop:disable Lint/RescueException
      Truffle::POSIX.close(master_fd)
      raise
    ensure
      slave.close
    end

    r = File.for_fd(master_fd, 'r', path: slave_name)
    w = File.for_fd(Truffle::POSIX.fcntl(master_fd, Fcntl::F_DUPFD_CLOEXEC, 3), 'w', path: slave_name)
    w.sync = true

    if block_given?
      begin
        yield [r, w, pid]
      ensure
        # Reap the process if it already exited, to prevent it from becoming a zombie
        begin
          Process.waitpid(pid, Process::WNOHANG)
        rescue Errno::ECHILD
          nil # already reaped
        end
      end
      nil
    else
      [r, w, pid]
    end
  end

  class << self
    alias_method :getpty, :spawn
  end

  # Returns the Process::Status of the child process pid if it exited or stopped, and nil if it is still running.
  # If raise is true, raises PTY::ChildExited instead of returning the status.
  def self.check(pid, raise = false)
    begin
      cpid = Process.waitpid(pid, Process::WNOHANG | Process::WUNTRACED)
    rescue SystemCallError
      return nil
    end
    return nil unless cpid

    status = $?
    return status unless raise

    state = if status.stopped?
              'stopped'
            elsif Truffle::POSIX.kill(cpid, 0) == 0
              'changed'
            else
              'exited'
            end
    Kernel.raise ChildExited.new("pty - #{state}: #{cpid}", status)
  end

  def self.open_pty
    # Layout: int fds[2], followed by the path of the slave device
    Truffle::FFI::MemoryPointer.new(8 + SLAVE_NAME_SIZE) do |ptr|
      name_ptr = ptr + 8
      ret = Truffle::POSIX.truffleposix_openpty(ptr, name_ptr, SLAVE_NAME_SIZE)
      Errno.handle('openpty') if ret == -1
      [ptr.get_int(0), ptr.get_int(4), name_ptr.get_string(0).force_encoding(Encoding.filesystem)]
    end
  end
  private_class_method :open_pty

  def self.default_shell
    shell = ENV['SHELL']
    return shell if shell

    require 'etc'
    Etc.getpwuid&.shell || '/bin/sh'
  end
  private_class_method :default_shell
end
