# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'objspace'

require_relative '../../ruby/spec_helper'

describe "ObjectSpace.reachable_objects_from" do

  # This is a standard method, but our implementation returns more objects so we add extra specs here

  it "finds the superclass and included/prepended modules of a class" do
    superclass = Class.new
    klass = Class.new(superclass)
    included = Module.new
    klass.include included
    prepended = Module.new
    klass.prepend prepended
    ObjectSpace.reachable_objects_from(klass).should include(prepended, included, superclass)
  end

  it "finds a variable captured by a block captured by #define_method" do
    captured = Object.new
    obj = Object.new
    block = -> {
      captured
    }
    obj.singleton_class.send(:define_method, :capturing_method, block)

    meth = obj.method(:capturing_method)
    reachable = ObjectSpace.reachable_objects_from(meth)
    reachable.should include(Method, obj)

    reachable = reachable + reachable.flat_map { |r| ObjectSpace.reachable_objects_from(r) }
    reachable.should include(captured)
  end

  it "finds finalizers" do
    object = Object.new
    finalizer = proc { }
    ObjectSpace.define_finalizer object, finalizer
    reachable = ObjectSpace.reachable_objects_from(object)
    reachable.should include(finalizer)
  end

end
