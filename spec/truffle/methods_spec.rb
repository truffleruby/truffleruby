# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

# How to regenerate files:
#
# - switch to MRI, the version we are compatible with
# - run `jt -u ruby test spec/truffle/methods_spec.rb`

# jt test and jt tag can be used as normal,
# but instead of jt untag, jt purge must be used to remove tags:
# $ jt purge spec/truffle/methods_spec.rb

# socket modules are found with:
# m1=ObjectSpace.each_object(Module).to_a; require "socket"; m2=ObjectSpace.each_object(Module).to_a; p m2-m1

modules = %w[
  Array BasicObject Binding Class Complex Complex Data Dir ENV.singleton_class
  Encoding Enumerable Enumerator Enumerator::Lazy Exception FalseClass Fiber
  File FileTest Float GC GC.singleton_class Hash IO Integer Kernel Marshal MatchData Math Method
  Module Mutex NilClass Numeric Object ObjectSpace ObjectSpace::WeakKeyMap ObjectSpace::WeakMap
  Proc Process Process.singleton_class Queue Random
  Random::Formatter Random.singleton_class Range Rational Regexp Signal
  SizedQueue String Struct Symbol SystemExit Thread Time TracePoint TrueClass
  UnboundMethod Warning

  Digest Digest.singleton_class
  Digest::Class Digest::Class.singleton_class
  Digest::Base Digest::Base.singleton_class
  Digest::Instance Digest::Instance.singleton_class
  Digest::MD5 Digest::MD5.singleton_class
  Digest::SHA1 Digest::SHA1.singleton_class

  Addrinfo BasicSocket Socket IPSocket TCPSocket TCPServer UDPSocket UNIXSocket UNIXServer
  Socket::AncillaryData Socket::Constants Socket::Ifaddr Socket::Option Socket::UDPSource SocketError

  Pathname Pathname.singleton_class

  StringScanner StringScanner.singleton_class

  StringIO
]

requires = %w[digest pathname socket stringio strscan]
requires_code = requires.map { |lib| "require #{lib.inspect}" }.join("\n")

guard -> { !defined?(SKIP_SLOW_SPECS) } do
  if RUBY_ENGINE == "ruby"
    modules.each do |mod|
      file = File.expand_path("../methods/#{mod}.txt", __FILE__)
      code = "#{requires_code}\nputs #{mod}.public_instance_methods(false).sort"
      methods = ruby_exe(code)
      methods = methods.lines.map { |line| line.chomp.to_sym }
      contents = methods.map { |meth| "#{meth}\n" }.join
      File.write file, contents
    end
  end

  code = <<-RUBY
  #{requires_code}
  #{modules.inspect}.each { |m|
    puts m
    puts eval(m).public_instance_methods(false).sort
    puts
  }
  RUBY
  all_methods = {}
  ruby_exe(code).rstrip.split("\n\n").each do |group|
    mod, *methods = group.lines.map(&:chomp)
    all_methods[mod] = methods.map(&:to_sym)
  end

  modules.each do |mod|
    describe "Public methods on #{mod}" do
      file = File.expand_path("../methods/#{mod}.txt", __FILE__)
      expected = File.readlines(file).map { |line| line.chomp.to_sym }
      methods = all_methods[mod]

      if methods == expected
        it "are the same as on MRI" do
          methods.should == expected
        end
      else
        (methods - expected).each do |extra|
          it "should not include #{extra}" do
            methods.should_not include(extra)
          end
        end
        (expected - methods).each do |missing|
          it "should include #{missing}" do
            methods.should include(missing)
          end
        end
      end
    end
  end
end
