# truffleruby_primitives: true

# Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Sharing is correctly propagated for" do

  before :all do
    # Start multithreading and make 'self' shared
    @share = nil
    Thread.new { self }.join
  end

  def shared?(obj)
    Truffle::Debug.shared?(obj)
  end

  def check_all_shared(map)
    map.each_pair do |k,v|
      shared?(k).should == true
      shared?(v).should == true
    end
  end

  describe "Object" do
    it "with instance_variable_set" do
      obj = Object.new
      obj.instance_variable_set(:@initial, Object.new)
      shared?(obj).should == false

      @share = obj
      shared?(obj).should == true
      shared?(obj.instance_variable_get(:@initial)).should == true

      obj.instance_variable_set(:@wb, Object.new)
      shared?(obj.instance_variable_get(:@wb)).should == true
    end

    it "with attr_accessor" do
      cls = Class.new do
        attr_accessor :initial, :wb
      end
      obj = cls.new
      obj.initial = Object.new
      shared?(obj).should == false

      @share = obj
      shared?(obj).should == true
      shared?(obj.initial).should == true

      obj.wb = Object.new
      shared?(obj.wb).should == true
    end

    it "with @ivar accesses" do
      cls = Class.new do
        def initialize(initial)
          @initial = initial
        end

        def wb=(value); @wb = value; end

        def initial; @initial; end
        def wb; @wb; end
      end
      obj = cls.new(Object.new)
      shared?(obj).should == false

      @share = obj
      shared?(obj).should == true
      shared?(obj.initial).should == true

      obj.wb = Object.new
      shared?(obj.wb).should == true
    end
  end

  describe "Array" do
    it "literal" do
      ary = [Object.new]
      shared?(ary).should == false

      @share = ary
      shared?(ary).should == true
      shared?(ary[0]).should == true

      ary << Object.new
      shared?(ary.last).should == true
    end

    it "#initialize" do
      ary = Array.allocate
      @share = ary
      shared?(ary).should == true

      ary.send(:initialize, 3, Object.new)
      ary.each { |e| shared?(e).should == true }

      ary.send(:initialize, 3) { Object.new }
      ary.each { |e| shared?(e).should == true }

      ary.send(:initialize, [Object.new, Object.new])
      ary.each { |e| shared?(e).should == true }
    end

    it "#[]=" do
      ary = []
      @share = ary
      shared?(ary).should == true

      ary[0] = Object.new
      shared?(ary[0]).should == true

      ary[0] = "foobar"
      shared?(ary[0]).should == true

      ary[42] = Object.new
      ary.each { |e| shared?(e).should == true }

      ary[0, 2] = [Object.new, Object.new]
      ary.each { |e| shared?(e).should == true }

      ary[0, 3] = [Object.new]
      ary.each { |e| shared?(e).should == true }
    end

    it "#<<" do
      ary = []
      @share = ary
      shared?(ary).should == true

      ary << Object.new
      shared?(ary.last).should == true
    end

    it "#concat" do
      ary = [Object.new]
      @share = ary
      shared?(ary).should == true

      ary.concat([Object.new, Object.new])
      ary.each { |e| shared?(e).should == true }
    end

    it "#fill" do
      ary = [Object.new, Object.new]
      @share = ary
      shared?(ary).should == true

      ary.fill(Object.new)
      ary.each { |e| shared?(e).should == true }
    end

    it "#map!" do
      ary = [Object.new, Object.new]
      @share = ary
      shared?(ary).should == true

      ary.map! { Object.new }
      ary.each { |e| shared?(e).should == true }
    end

    it "#replace" do
      ary = []
      @share = ary
      shared?(ary).should == true

      new_ary = [Object.new]
      ary.replace(new_ary)
      # new_ary is not shared, but any object it contains should be.
      shared?(new_ary).should == false
      new_ary.each { |e| shared?(e).should == true }
    end

    it ":steal_array_storage" do
      ary = []
      @share = ary
      shared?(ary).should == true

      new_ary = []
      Primitive.steal_array_storage(ary, new_ary)
      shared?(new_ary).should == true
    end

    it "#shift with copy-on-write DelegatedArrayStorage" do
      ary = [Object.new, Object.new, Object.new]
      ary.shift
      @share = ary
      shared?(ary).should == true
      ary.each { |e| shared?(e).should == true }

      wb = Object.new
      ary[0] = wb
      ary.each { |e| shared?(e).should == true }
    end

    it "#shift(n) with copy-on-write DelegatedArrayStorage" do
      ary = [Object.new, Object.new, Object.new, Object.new, Object.new]
      @share = ary
      new_ary = ary.shift(3)
      shared?(ary).should == true
      ary.each { |e| shared?(e).should == true }

      shared?(new_ary).should == false
      new_ary.each { |e| shared?(e).should == true }
    end

    it "#pop with copy-on-write DelegatedArrayStorage" do
      ary = [Object.new, Object.new, Object.new]
      ary.pop
      @share = ary
      shared?(ary).should == true
      ary.each { |e| shared?(e).should == true }

      wb = Object.new
      ary[0] = wb
      ary.each { |e| shared?(e).should == true }
    end

    it "#pop(n) with copy-on-write DelegatedArrayStorage" do
      ary = [Object.new, Object.new, Object.new, Object.new, Object.new]
      @share = ary
      new_ary = ary.pop(3)
      shared?(ary).should == true
      ary.each { |e| shared?(e).should == true }

      shared?(new_ary).should == false
      new_ary.each { |e| shared?(e).should == true }
    end
  end

  describe "Hash" do
    it "literal" do
      initial = Object.new
      hsh = { initial => Object.new }
      shared?(hsh).should == Truffle::Boot.get_option("concurrent-hash-always")

      @share = hsh
      shared?(hsh).should == true
      shared?(initial).should == true
      shared?(hsh[initial]).should == true
    end

    it "#[]=" do
      hsh = {}
      @share = hsh

      wb = Object.new
      hsh[wb] = Object.new
      shared?(wb).should == true
      shared?(hsh[wb]).should == true
    end

    it "default value" do
      default = Object.new
      hsh = Hash.new(default)
      shared?(hsh).should == false

      @share = hsh
      shared?(hsh).should == true
      shared?(default).should == true

      wb = Object.new
      hsh.default = wb
      shared?(wb).should == true
    end

    it "default Proc" do
      default_proc = proc { nil }
      hsh = Hash.new(&default_proc)
      shared?(hsh).should == false

      @share = hsh
      shared?(hsh).should == true
      shared?(default_proc).should == true

      wb = proc { 42 }
      hsh.default_proc = wb
      shared?(wb).should == true
    end

    it "#initialize" do
      hsh = Hash.allocate
      @share = hsh
      shared?(hsh).should == true

      default = Object.new
      hsh.send(:initialize, default)
      shared?(default).should == true

      default_proc = proc { 42 }
      hsh.send(:initialize, &default_proc)
      shared?(default_proc).should == true
    end

    it "#replace" do
      hsh = {}
      @share = hsh
      shared?(hsh).should == true

      new_hash = {}
      hsh.replace(new_hash)
      # new_hash is not shared, but any object it contains should be.
      shared?(new_hash).should == Truffle::Boot.get_option("concurrent-hash-always")
      check_all_shared(new_hash)
    end

    it "#initialize_copy" do
      hsh = { Object.new => Object.new }

      @share = copy = {}
      copy.send(:initialize_copy, hsh)
      check_all_shared(copy)
    end
  end

  describe "TruffleRuby::ConcurrentMap" do
    it "share globally" do
      map = TruffleRuby::ConcurrentMap.new
      map[Object.new] = Object.new
      @share = map
      check_all_shared(map)
    end

    it "#initialize_copy" do
      map = TruffleRuby::ConcurrentMap.new
      map[Object.new] = Object.new

      @share = copy = TruffleRuby::ConcurrentMap.new
      copy.send(:initialize_copy, map)
      check_all_shared(copy)
    end

    it "#[]=" do
      @share = map = TruffleRuby::ConcurrentMap.new
      map[Object.new] = Object.new
      check_all_shared(map)
    end

    it "#compute_if_absent" do
      @share = map = TruffleRuby::ConcurrentMap.new

      map.compute_if_absent(Object.new) { Object.new }
      check_all_shared(map)
    end

    it "#compute_if_present" do
      @share = map = TruffleRuby::ConcurrentMap.new

      k1 = "key"
      k2 = k1.dup
      wb = Object.new
      map[k1] = 1
      map.compute_if_present(k2) { wb }
      map[k1].should == wb
      shared?(wb).should == true
      shared?(k1).should == true
      shared?(k2).should == false
      check_all_shared(map)
    end

    it "#compute" do
      @share = map = TruffleRuby::ConcurrentMap.new

      map.compute(Object.new) { Object.new }
      map[:a] = Object.new
      map.compute(:a) { Object.new }
      map.size.should == 2
      check_all_shared(map)
    end

    it "#merge_pair" do
      @share = map = TruffleRuby::ConcurrentMap.new

      map.merge_pair(Object.new, Object.new) { raise }
      map[:key] = "a"
      map.merge_pair(:key, :ignored) { |v| v * 2 }.should == "aa"
      check_all_shared(map)
    end

    it "#replace_pair" do
      @share = map = TruffleRuby::ConcurrentMap.new

      old_value, new_value = Object.new, Object.new
      map[:key] = old_value
      map.replace_pair(:key, old_value, new_value).should == true
      check_all_shared(map)
    end

    it "#replace_if_exists" do
      @share = map = TruffleRuby::ConcurrentMap.new

      old_value, new_value = Object.new, Object.new
      map[:key] = old_value
      map.replace_if_exists(:key, new_value).should == old_value
      map[:key].should == new_value
      check_all_shared(map)
    end

    it "#get_and_set" do
      @share = map = TruffleRuby::ConcurrentMap.new

      map.get_and_set(Object.new, Object.new)

      old_value, new_value = Object.new, Object.new
      map[:key] = old_value
      map.get_and_set(:key, new_value).should == old_value
      map[:key].should == new_value
      check_all_shared(map)
    end
  end

  describe "Thread.new's" do
    it "block" do
      obj = Object.new
      t = Thread.new { sleep }
      begin
        shared?(obj).should == true
      ensure
        t.kill
        t.join
      end
    end

    # avoid capturing local variables in the spec example below
    thread_body = proc { sleep }

    it "arguments" do
      obj1 = Object.new
      obj2 = Object.new
      t = Thread.new(obj1, obj2, &thread_body)
      begin
        shared?(obj1).should == true
        shared?(obj2).should == true
      ensure
        t.kill
        t.join
      end
    end
  end

  it "Fiber local variables which share the value (since they can be accessed from other threads)" do
    require 'fiber'
    # Create a new Thread to make sure the root Fiber is shared as expected
    Thread.new do
      thread = Thread.current
      shared?(thread).should == true

      shared?(Fiber.current).should == true

      obj = Object.new
      thread[:sharing_spec] = obj
      begin
        shared?(obj).should == true
      ensure
        thread[:sharing_spec] = nil
      end
    end.join
  end

  it "Fiber which refers to an Array via instance variables" do
    a = [Object.new]
    f = Fiber.new { 42 }
    shared?(f).should == true

    f.instance_variable_set(:@a, a)
    shared?(a).should == true
    shared?(a[0]).should == true

    f.resume.should == 42
  end

  it "Thread local variables which share the value (probably they should not)" do
    thread = Thread.current
    shared?(thread).should == true

    obj = Object.new
    thread.thread_variable_set(:sharing_spec, obj)
    shared?(obj).should == true # TODO (eregon, 5 Jun 2019): this is the current non-ideal behavior
  end

  it "classes and constants and they are not shared until sharing is started" do
    ruby_exe("p Truffle::Debug.shared?(Object)").should == "false\n"
  end

  it "thread-local IO buffers which should not trigger sharing" do
    ruby_exe("File.read(#{__FILE__.inspect}); p Truffle::Debug.shared?(Object)").should == "false\n"
  end

end
