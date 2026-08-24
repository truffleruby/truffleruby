# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Truffle::POSIX" do
  it "captures errno from a failed native call" do
    Errno.errno = 0
    Truffle::POSIX.chdir("/definitely/not/a/truffleruby/path").should == -1
    Errno.errno.should == Errno::ENOENT::Errno
  end

  it "stores errno on the current Fiber" do
    Errno.errno = 123

    fiber = Fiber.new do
      initial = Errno.errno
      Errno.errno = 456
      Fiber.yield initial
      Errno.errno
    end

    fiber.resume.should == 0
    Errno.errno.should == 123
    fiber.resume.should == 456
    Errno.errno.should == 123
  end

  it "passes the creation mode to open" do
    path = tmp("truffle_posix_open_mode")
    mode = File::Constants::WRONLY | File::Constants::CREAT | File::Constants::TRUNC
    previous_umask = File.umask(0)

    begin
      fd = Truffle::POSIX.open(path, mode, 0745)
      fd.should >= 0
      Truffle::POSIX.close(fd).should == 0
      fd = nil

      (File.stat(path).mode & 0777).should == 0745
    ensure
      Truffle::POSIX.close(fd) if fd && fd >= 0
      File.umask(previous_umask)
      rm_r path
    end
  end

  it "accepts unsigned int arguments" do
    skip "would change ownership as root" if Process.uid == 0

    Errno.errno = 0
    Truffle::POSIX.chown(__FILE__, 1 << 31, Process.gid).should == -1
    Errno.errno.should_not == 0
  end

  it "accepts unsigned long arguments" do
    Errno.errno = 0
    Truffle::POSIX.ioctl(-1, 1 << 63, Truffle::FFI::Pointer::NULL).should == -1
    Errno.errno.should_not == 0
  end
end
