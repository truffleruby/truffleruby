#
# This file is part of ruby-ffi.
# For licensing, see LICENSE.SPECS
#

require_relative 'fixtures/compile'
require 'timeout'
require 'objspace'

if defined? Ractor
  class Ractor
    # compat with Ruby-3.4 and older
    alias value take unless method_defined? :value
  end
end

RSpec.configure do |c|
  c.filter_run_excluding gc_dependent: true unless ENV['FFI_TEST_GC'] == 'true'

  # Ractor is only usable on ruby-3.1+, but it hangs on Windows on ruby-3.3+
  c.filter_run_excluding( :ractor ) unless defined?(Ractor) && RUBY_VERSION >= "3.1" && (RUBY_VERSION !~ /^3.[34].|^4.[01]./ || RUBY_PLATFORM !~ /mingw|mswin/)
end

module TestLibrary
  def self.force_gc
    if RUBY_ENGINE == 'jruby'
      java.lang.System.gc
    elsif RUBY_ENGINE == 'rbx'
      GC.run(true)
    else
      GC.start
    end
  end
end

def external_run(cmd, rb_file, options: [], timeout: 60)
  path = File.join(File.dirname(__FILE__), rb_file)
  log = "#{path}.log"
  pid = spawn(cmd, "-Ilib", path, { [:out, :err] => log })
  begin
    Timeout.timeout(timeout){ Process.wait(pid) }
  rescue Timeout::Error
    Process.kill(9, pid)
    raise
  else
    if $?.exitstatus != 0
      raise "external process failed:\n#{ File.read(log) }"
    end
  end
  File.read(log)
end

module OrderHelper
  case FFI::Platform::BYTE_ORDER
  when FFI::Platform::LITTLE_ENDIAN
    ORDER = :little
    OTHER_ORDER = :big
  when FFI::Platform::BIG_ENDIAN
    ORDER = :big
    OTHER_ORDER = :little
  else
    raise
  end
end

if ENV['FFI_GC_STRESS'] == 'true'
  RSpec.configure do |config|
    config.before :each do
      GC.stress=true
    end
    config.after :each do
      GC.stress=false
    end
  end
end
