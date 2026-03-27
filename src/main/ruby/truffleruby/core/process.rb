# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
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

module Process
  module Constants
    EXIT_SUCCESS = Truffle::Config['platform.process.EXIT_SUCCESS'] || 0
    EXIT_FAILURE = Truffle::Config['platform.process.EXIT_FAILURE'] || 1

    PRIO_PGRP    = Truffle::Config['platform.process.PRIO_PGRP']
    PRIO_PROCESS = Truffle::Config['platform.process.PRIO_PROCESS']
    PRIO_USER    = Truffle::Config['platform.process.PRIO_USER']

    RLIM_INFINITY  = Truffle::Config['platform.process.RLIM_INFINITY']
    RLIM_SAVED_MAX = Truffle::Config['platform.process.RLIM_SAVED_MAX']
    RLIM_SAVED_CUR = Truffle::Config['platform.process.RLIM_SAVED_CUR']

    RLIMIT_AS      = Truffle::Config['platform.process.RLIMIT_AS']
    RLIMIT_CORE    = Truffle::Config['platform.process.RLIMIT_CORE']
    RLIMIT_CPU     = Truffle::Config['platform.process.RLIMIT_CPU']
    RLIMIT_DATA    = Truffle::Config['platform.process.RLIMIT_DATA']
    RLIMIT_FSIZE   = Truffle::Config['platform.process.RLIMIT_FSIZE']
    RLIMIT_NOFILE  = Truffle::Config['platform.process.RLIMIT_NOFILE']
    RLIMIT_STACK   = Truffle::Config['platform.process.RLIMIT_STACK']

    %i[RLIMIT_MEMLOCK RLIMIT_NPROC RLIMIT_RSS RLIMIT_SBSIZE RLIMIT_RTPRIO
       RLIMIT_RTTIME RLIMIT_SIGPENDING RLIMIT_MSGQUEUE RLIMIT_NICE].each do |limit|
      if value = Truffle::Config.lookup("platform.process.#{limit}")
        const_set limit, value
      end
    end

    WNOHANG =   Truffle::Config['platform.process.WNOHANG']
    WUNTRACED = Truffle::Config['platform.process.WUNTRACED']
  end
  include Constants

  FFI = Truffle::FFI

  def self.exit(code = 0)
    case code
    when true
      code = 0
    when false
      code = 1
    else
      code = Primitive.convert_with_to_int code
    end

    raise SystemExit.new(code, 'exit')
  end

  def self.exit!(code = 1)
    case code
    when true
      code = 0
    when false
      code = 1
    else
      code = Primitive.convert_with_to_int code
    end

    Primitive.vm_exit code
  end

  section = 'platform.clocks.'
  Truffle::Config.section(section) do |key, value|
    const_set(Primitive.string_substring(key, section.size, key.length), value)
  end

  module_function

  def clock_getres(id, unit = :float_second)
    res = case id
          when :MACH_ABSOLUTE_TIME_BASED_CLOCK_MONOTONIC,
               CLOCK_MONOTONIC
            1
          when :GETTIMEOFDAY_BASED_CLOCK_REALTIME,
               :GETRUSAGE_BASED_CLOCK_PROCESS_CPUTIME_ID,
               :CLOCK_BASED_CLOCK_PROCESS_CPUTIME_ID
            1_000
          when CLOCK_REALTIME
            # https://bugs.openjdk.java.net/browse/JDK-8068730
            1000
          when :TIMES_BASED_CLOCK_MONOTONIC,
               :TIMES_BASED_CLOCK_PROCESS_CPUTIME_ID
            10_000_000
          when :TIME_BASED_CLOCK_REALTIME
            1_000_000_000
          when Symbol
            raise Errno::EINVAL
          else
            res_for_id = Truffle::POSIX.truffleposix_clock_getres(id)
            if res_for_id == 0
              Errno.handle
            else
              res_for_id
            end
          end

    if :hertz == unit
      1.0 / Truffle::ProcessOperations.nanoseconds_to_unit(res, :float_second)
    else
      Truffle::ProcessOperations.nanoseconds_to_unit(res, unit)
    end
  end

  def clock_gettime(id, unit = :float_second)
    if Primitive.is_a?(id, Symbol)
      id = case id
           when :GETTIMEOFDAY_BASED_CLOCK_REALTIME,
                :TIME_BASED_CLOCK_REALTIME
             CLOCK_REALTIME
           when :MACH_ABSOLUTE_TIME_BASED_CLOCK_MONOTONIC,
                :TIMES_BASED_CLOCK_MONOTONIC
             CLOCK_MONOTONIC
           when :GETRUSAGE_BASED_CLOCK_PROCESS_CPUTIME_ID,
                :TIMES_BASED_CLOCK_PROCESS_CPUTIME_ID,
                :CLOCK_BASED_CLOCK_PROCESS_CPUTIME_ID
             CLOCK_THREAD_CPUTIME_ID
           else
             raise Errno::EINVAL
           end
    end

    case id
    when CLOCK_MONOTONIC # most common clock id
      time = Primitive.process_time_nanotime
    when CLOCK_REALTIME
      time = Primitive.process_time_instant
    else
      time = Truffle::POSIX.truffleposix_clock_gettime(id)
      Errno.handle if time == 0
    end

    Truffle::ProcessOperations.nanoseconds_to_unit(time, unit)
  end

  ##
  # Sets the process title. Calling this method does not affect the value of
  # `$0` as per MRI behaviour. This method returns the title set.
  #
  # @param [String] title
  # @return [Title]
  #
  def setproctitle(title)
    title = Primitive.convert_with_to_str(title)
    argv = Primitive.vm_native_argv

    # Not run from ruby launcher, we cannot set process title
    return title if argv == 0

    max_length = Primitive.vm_native_argv_length
    new_title =
      if title.bytesize > max_length
        title.byteslice(0, max_length)
      else
        title + "\x00" * (max_length - title.bytesize)
      end

    argv_ptr = FFI::Pointer.new(:char, argv)
    argv0_ptr = argv_ptr.read_pointer.slice(0, max_length)
    argv0_ptr.write_bytes(new_title)

    title
  end

  def setrlimit(resource, cur_limit, max_limit = undefined)
    resource =  Truffle::ProcessOperations.coerce_rlimit_resource(resource)
    cur_limit = Primitive.convert_with_to_int cur_limit

    if Primitive.undefined? max_limit
      max_limit = cur_limit
    else
      max_limit = Primitive.convert_with_to_int max_limit
    end

    rlim_t = Truffle::Config['platform.typedef.rlim_t']
    raise rlim_t unless rlim_t == 'ulong' or rlim_t == 'ulong_long'

    Truffle::FFI::MemoryPointer.new(:rlim_t, 2) do |ptr|
      ptr[0].write_ulong cur_limit
      ptr[1].write_ulong max_limit
      ret = Truffle::POSIX.setrlimit(resource, ptr)
      Errno.handle if ret == -1
    end
    nil
  end

  def getrlimit(resource)
    resource = Truffle::ProcessOperations.coerce_rlimit_resource(resource)

    rlim_t = Truffle::Config['platform.typedef.rlim_t']
    raise rlim_t unless rlim_t == 'ulong' or rlim_t == 'ulong_long'

    Truffle::FFI::MemoryPointer.new(:rlim_t, 2) do |ptr|
      ret = Truffle::POSIX.getrlimit(resource, ptr)
      Errno.handle if ret == -1
      cur = ptr[0].read_ulong
      max = ptr[1].read_ulong
      [cur, max]
    end
  end

  def setsid
    pgid = Truffle::POSIX.setsid
    Errno.handle if pgid == -1
    pgid
  end

  def self.fork
    _fork
  end
  Primitive.method_unimplement method(:fork)

  # An internal API for fork. Do not call this method directly.
  # Currently, this is called via Kernel.#fork, Process.fork, and IO.popen("-").
  # This method is not for casual code but for application monitoring
  # libraries. You can add custom code before and after fork events
  # by overriding this method.
  def self._fork
    raise NotImplementedError, 'fork is not available'
  end
  Primitive.method_unimplement method(:_fork)

  def times
    Truffle::FFI::MemoryPointer.new(:double, 4) do |ptr|
      ret = Truffle::POSIX.truffleposix_getrusage(ptr)
      Errno.handle if ret == -1
      Process::Tms.new(*ptr.read_array_of_double(4))
    end
  end

  def kill(signal, *pids)
    raise ArgumentError, 'PID argument required' if pids.length == 0

    use_process_group = false
    signal = signal.to_s if Primitive.is_a?(signal, Symbol)

    if Primitive.is_a?(signal, String)
      if signal.start_with? '-'
        signal = signal[1..-1]
        use_process_group = true
      end

      if signal.start_with? 'SIG'
        signal = signal[3..-1]
      end

      signal = Signal::Names[signal]
    end

    raise ArgumentError unless Primitive.is_a?(signal, Integer)

    if signal < 0
      signal = -signal
      use_process_group = true
    end

    pids.each do |pid|
      pid = Primitive.convert_with_to_int pid

      if pid == Process.pid && signal != 0
        signal_name = Signal::Numbers[signal].to_sym
        result = Primitive.process_kill_raise signal_name
        if result == -1 # Try kill() if the java Signal.raise() failed
          result = Truffle::POSIX.kill(pid, signal)
          Errno.handle if result == -1
        end
      else
        pid = -pid if use_process_group
        result = Truffle::POSIX.kill(pid, signal)
        Errno.handle if result == -1
      end
    end

    pids.length
  end

  def self.abort(msg = nil)
    if msg
      msg = Primitive.convert_with_to_str(msg)
      $stderr.puts(msg)
    end
    raise SystemExit.new(1, msg)
  end

  def getpgid(pid)
    pid = Primitive.convert_with_to_int pid

    ret = Truffle::POSIX.getpgid(pid)
    Errno.handle if ret == -1
    ret
  end

  def setpgid(pid, int)
    pid = Primitive.convert_with_to_int pid
    int = Primitive.convert_with_to_int int

    ret = Truffle::POSIX.setpgid(pid, int)
    Errno.handle if ret == -1
    ret
  end

  def maxgroups
    Truffle::ProcessOperations.maxgroups
  end

  def maxgroups=(maxgroups)
    Truffle::ProcessOperations.maxgroups = maxgroups
  end

  def setpgrp
    setpgid(0, 0)
  end

  def getpgrp
    ret = Truffle::POSIX.getpgrp
    Errno.handle if ret == -1
    ret
  end

  def pid
    $$ or raise SecurityError, 'native access is not allowed'
  end

  def ppid
    Truffle::POSIX.getppid
  end

  def uid=(uid)
    uid =
      if name = Truffle::Type.rb_check_convert_type(uid, String, :to_str)
        require 'etc'
        Etc.getpwnam(name).uid
      else
        Truffle::Type.rb_num2ulong(uid)
      end

    # the 4 rescue clauses below are needed
    # until respond_to? can be used to query the implementation of methods attached via FFI
    # atm respond_to returns true if a method is attached but not implemented on the platform
    uid = Primitive.convert_with_to_int uid
    begin
      ret = Truffle::POSIX.setresuid(uid, -1, -1)
    rescue NotImplementedError
      begin
        ret = Truffle::POSIX.setreuid(uid, -1)
      rescue NotImplementedError
        begin
          ret = Truffle::POSIX.setruid(uid)
        rescue NotImplementedError
          if Process.euid == uid
            ret = Truffle::POSIX.setuid(uid)
          else
            raise NotImplementedError
          end
        end
      end
    end

    Errno.handle if ret == -1

    uid
  end

  def gid=(gid)
    gid =
      if name = Truffle::Type.rb_check_convert_type(gid, String, :to_str)
        require 'etc'
        Etc.getgrnam(name).gid
      else
        Truffle::Type.rb_num2ulong(gid)
      end

    Process::Sys.setgid gid
  end

  def euid=(uid)
    # the 4 rescue clauses below are needed
    # until respond_to? can be used to query the implementation of methods attached via FFI
    # atm respond_to returns true if a method is attached but not implemented on the platform
    uid =
      if name = Truffle::Type.rb_check_convert_type(uid, String, :to_str)
        require 'etc'
        Etc.getpwnam(name).uid
      else
        Truffle::Type.rb_num2ulong(uid)
      end

    begin
      ret = Truffle::POSIX.setresuid(-1, uid, -1)
    rescue NotImplementedError
      begin
        ret = Truffle::POSIX.setreuid(-1, uid)
      rescue NotImplementedError
        begin
          ret = Truffle::POSIX.seteuid(uid)
        rescue NotImplementedError
          if Process.uid == uid
            ret = Truffle::POSIX.setuid(uid)
          else
            raise NotImplementedError
          end
        end
      end
    end

    Errno.handle if ret == -1

    uid
  end

  def egid=(gid)
    gid =
      if name = Truffle::Type.rb_check_convert_type(gid, String, :to_str)
        require 'etc'
        Etc.getgrnam(name).gid
      else
        Truffle::Type.rb_num2ulong(gid)
      end

    Process::Sys.setegid gid
  end

  def uid
    Truffle::POSIX.getuid
  end

  def gid
    Truffle::POSIX.getgid
  end

  def euid
    Truffle::POSIX.geteuid
  end

  def egid
    Truffle::POSIX.getegid
  end

  def getpriority(kind, id)
    kind = Primitive.convert_with_to_int kind
    id =   Primitive.convert_with_to_int id

    ret = Truffle::POSIX.truffleposix_getpriority(kind, id)
    if ret <= -100
      raise SystemCallError.new(nil, -(ret + 100))
    end
    ret
  end

  def setpriority(kind, id, priority)
    kind = Primitive.convert_with_to_int kind
    id =   Primitive.convert_with_to_int id
    priority = Primitive.convert_with_to_int priority

    ret = Truffle::POSIX.setpriority(kind, id, priority)
    Errno.handle if ret == -1
    ret
  end

  def groups
    ngroups = Truffle::POSIX.getgroups(0, FFI::Pointer::NULL)
    Errno.handle if ngroups == -1

    gid_t = Truffle::Config['platform.typedef.gid_t']
    raise gid_t unless gid_t == 'uint'

    FFI::MemoryPointer.new(:gid_t, ngroups) do |ptr|
      ret = Truffle::POSIX.getgroups(ngroups, ptr)
      Errno.handle if ret == -1
      ptr.read_array_of_uint(ngroups)
    end
  end

  def groups=(groups)
    gid_t = Truffle::Config['platform.typedef.gid_t']
    raise gid_t unless gid_t == 'uint'

    Truffle::ProcessOperations.maxgroups = groups.size if groups.size > Truffle::ProcessOperations.maxgroups

    FFI::MemoryPointer.new(:gid_t, groups.size) do |ptr|
      ptr.write_array_of_uint(groups)
      r = Truffle::POSIX.setgroups(groups.size, ptr)
      Errno.handle if r == -1
    end
    groups
  end

  def initgroups(username, gid)
    username = Primitive.convert_with_to_str(username)
    gid = Primitive.convert_with_to_int gid

    if Truffle::POSIX.initgroups(username, gid) == -1
      Errno.handle
    end

    Process.groups
  end

  def self.last_status
    $?
  end

  #
  # Wait for the given process to exit.
  #
  # The pid may be the specific pid of some previously forked
  # process, or -1 to indicate to watch for *any* child process
  # exiting. Other options, such as process groups, may be available
  # depending on the system.
  #
  # With no arguments the default is to block waiting for any
  # child processes (pid -1.)
  #
  # The flag may be Process::WNOHANG, which indicates that
  # the child should only be quickly checked. If it has not
  # exited yet, nil is returned immediately instead.
  #
  # The return value is the exited pid or nil if Process::WNOHANG
  # was used and the child had not yet exited.
  #
  # If the pid has exited, the global $? is set to a Process::Status
  # object representing the exit status (and possibly other info) of
  # the child.
  #
  # If there exists no such pid (e.g. never forked or already
  # waited for), or no children at all, Errno::ECHILD is raised.
  #
  # TODO: Support other options such as WUNTRACED? --rue
  #
  def wait2(input_pid = -1, flags = nil)
    status = Truffle::ProcessOperations.wait(input_pid, flags, true, true)
    if status
      [status.pid, status]
    else
      nil
    end
  end

  #
  # Wait for all child processes.
  #
  # Blocks until all child processes have exited, and returns
  # an Array of [pid, Process::Status] results, one for each
  # child.
  #
  # Be mindful of the effects of creating new processes while
  # .waitall has been called (usually in a different thread.)
  # The .waitall call does not in any way check that it is only
  # waiting for children that existed at the time it was called.
  #
  def waitall
    statuses = []

    begin
      while status = Process.wait2
        statuses << status
      end
    rescue Errno::ECHILD
      nil # No child processes
    end

    statuses
  end

  def wait(pid = -1, flags = nil)
    Truffle::ProcessOperations.wait(pid, flags, true, true)&.pid
  end

  class << self
    alias_method :waitpid, :wait
    alias_method :waitpid2, :wait2
  end

  def warmup
    true
  end

  def daemon(*args)
    raise NotImplementedError, 'Process.daemon is not available'
  end
  Primitive.method_unimplement method(:daemon)


  def self.exec(*args)
    Truffle::ProcessOperations.exec(*args)
  end

  def self.spawn(*args)
    Truffle::ProcessOperations.spawn(*args)
  end

  # TODO: Should an error be raised on ECHILD? --rue
  #
  # TODO: This operates on the assumption that waiting on
  #       the event consumes very little resources. If this
  #       is not the case, the check should be made WNOHANG
  #       and called periodically.
  #
  def detach(pid)
    pid = Primitive.convert_with_to_int(pid)
    raise ArgumentError, 'Only positive pids may be detached' unless pid > 0

    thread = Thread.new do
      Process.wait pid; $?
    rescue Errno::ECHILD
      # suppress exception "No child processes - No child process: <PID> (Errno::ECHILD)"
    end

    thread[:pid] = pid
    def thread.pid; self[:pid] end

    thread
  end

  #--
  # TODO: Most of the fields aren't implemented yet.
  # TODO: Also, these objects should only need to be constructed by
  # Process.wait and family.
  #++

  class Status

    attr_reader :exitstatus, :termsig, :stopsig

    def initialize(pid = nil, exitstatus = nil, termsig = nil, stopsig = nil, raw_status = nil)
      @pid = pid
      @exitstatus = exitstatus
      @termsig = termsig
      @stopsig = stopsig
      @raw_status = raw_status
    end

    def to_i
      @raw_status
    end

    def &(num)
      @raw_status & num
    end

    def >>(num)
      @raw_status >> num
    end

    def ==(other)
      other = other.to_i if Primitive.is_a?(other, Process::Status)
      @raw_status == other
    end

    def coredump?
      false
    end

    def exited?
      !Primitive.nil?(@exitstatus)
    end

    def pid
      @pid
    end

    def signaled?
      !Primitive.nil?(@termsig)
    end

    def stopped?
      !Primitive.nil?(@stopsig)
    end

    def success?
      if exited?
        @exitstatus == 0
      else
        nil
      end
    end

    def to_s
      "pid #{@pid.inspect} exit #{@exitstatus.inspect}"
    end

    def inspect
      "#<Process::Status: #{self}>"
    end

    def self.wait(pid = -1, flags = nil)
      Truffle::ProcessOperations.wait(pid, flags, false, false)
    end
  end

  module Sys
    class << self
      def getegid
        Truffle::POSIX.getegid
      end

      def geteuid
        Truffle::POSIX.geteuid
      end

      def getgid
        Truffle::POSIX.getgid
      end

      def getuid
        Truffle::POSIX.getuid
      end

      def issetugid
        raise 'not implemented'
      end

      def setgid(gid)
        gid = Primitive.convert_with_to_int gid

        ret = Truffle::POSIX.setgid gid
        Errno.handle if ret == -1
        nil
      end

      def setuid(uid)
        uid = Primitive.convert_with_to_int uid

        ret = Truffle::POSIX.setuid uid
        Errno.handle if ret == -1
        nil
      end

      def setegid(egid)
        egid = Primitive.convert_with_to_int egid

        ret = Truffle::POSIX.setegid egid
        Errno.handle if ret == -1
        nil
      end

      def seteuid(euid)
        euid = Primitive.convert_with_to_int euid

        ret = Truffle::POSIX.seteuid euid
        Errno.handle if ret == -1
        nil
      end

      def setrgid(rgid)
        setregid(rgid, -1)
      end

      def setruid(ruid)
        setreuid(ruid, -1)
      end

      def setregid(rid, eid)
        rid = Primitive.convert_with_to_int rid
        eid = Primitive.convert_with_to_int eid

        ret = Truffle::POSIX.setregid rid, eid
        Errno.handle if ret == -1
        nil
      end

      def setreuid(rid, eid)
        rid = Primitive.convert_with_to_int rid
        eid = Primitive.convert_with_to_int eid

        ret = Truffle::POSIX.setreuid rid, eid
        Errno.handle if ret == -1
        nil
      end

      def setresgid(rid, eid, sid)
        rid = Primitive.convert_with_to_int rid
        eid = Primitive.convert_with_to_int eid
        sid = Primitive.convert_with_to_int sid

        ret = Truffle::POSIX.setresgid rid, eid, sid
        Errno.handle if ret == -1
        nil
      end

      def setresuid(rid, eid, sid)
        rid = Primitive.convert_with_to_int rid
        eid = Primitive.convert_with_to_int eid
        sid = Primitive.convert_with_to_int sid

        ret = Truffle::POSIX.setresuid rid, eid, sid
        Errno.handle if ret == -1
        nil
      end
    end
  end

  module UID
    class << self
      def change_privilege(uid)
        uid = Primitive.convert_with_to_int uid

        ret = Truffle::POSIX.setreuid(uid, uid)
        Errno.handle if ret == -1
        uid
      end

      def eid
        Truffle::POSIX.geteuid
      end

      def eid=(uid)
        uid = Primitive.convert_with_to_int uid

        ret = Truffle::POSIX.seteuid(uid)
        Errno.handle if ret == -1
        uid
      end
      alias_method :grant_privilege, :eid=

      def re_exchange
        real = Truffle::POSIX.getuid
        eff = Truffle::POSIX.geteuid
        ret = Truffle::POSIX.setreuid(eff, real)
        Errno.handle if ret == -1
        eff
      end

      def rid
        Truffle::POSIX.getuid
      end
    end
  end

  module GID
    class << self
      def change_privilege(gid)
        gid = Primitive.convert_with_to_int gid

        ret = Truffle::POSIX.setregid(gid, gid)
        Errno.handle if ret == -1
        gid
      end

      def eid
        Truffle::POSIX.getegid
      end

      def eid=(gid)
        gid = Primitive.convert_with_to_int gid

        ret = Truffle::POSIX.setegid(gid)
        Errno.handle if ret == -1
        gid
      end
      alias_method :grant_privilege, :eid=

      def re_exchange
        real = Truffle::POSIX.getgid
        eff = Truffle::POSIX.getegid
        ret = Truffle::POSIX.setregid(eff, real)
        Errno.handle if ret == -1
        eff
      end

      def rid
        Truffle::POSIX.getgid
      end
    end
  end

  xid = Module.new do
    def re_exchangeable?
      true
    end

    def sid_available?
      true
    end

    def switch
      eff = re_exchange
      if block_given?
        ret = yield
        re_exchange
        ret
      else
        eff
      end
    end
  end

  UID.extend xid
  GID.extend xid
end

Truffle::KernelOperations.define_hooked_variable(
  :'$0',
  -> { Primitive.global_variable_get :'$0' },
  -> _, v {
    v = Primitive.convert_with_to_str(v)
    Process.setproctitle(v)
    Primitive.global_variable_set :'$0', v
  })

alias $PROGRAM_NAME $0
