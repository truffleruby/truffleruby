# frozen_string_literal: false
# $RoughId: extconf.rb,v 1.3 2001/11/24 17:49:26 knu Exp $
# $Id$

require 'mkmf'

def generate_dummy_makefile
  File.open("Makefile", "w") do |f|
    f.puts dummy_makefile("syslog_ext").join
  end
end

def windows?
  RbConfig::CONFIG["host_os"] =~ /mswin|mingw|cygwin/
end

if windows?
  generate_dummy_makefile
else
  have_library("log") # for Android

  have_header("syslog.h") &&
    have_func("openlog") &&
    have_func("setlogmask") &&
    create_makefile("syslog_ext")
end
