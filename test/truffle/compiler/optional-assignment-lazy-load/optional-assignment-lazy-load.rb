# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort 'not running the GraalVM Compiler' unless TruffleRuby.jit?

def init_once
  $init_once ||= (Primitive.compiler_bailout('init_once compiled'); true)
end

begin
  loop do
    init_once
    Primitive.assert_not_compiled
  end
rescue Exception => e
  if e.message.include? 'assert_not_compiled'
    puts "correctly does not compile in the RHS of an init once expression"
  elsif e.message.include? 'init compiled'
    abort "incorrectly compiles in the RHS of an init once expression"
  else
    raise e
  end
end

def init_many
  $init_many ||= (Primitive.assert_not_compiled; true)
end

begin
  loop do
    init_many
    $init_many = false
  end
rescue Exception => e
  if e.message.include? 'assert_not_compiled'
    puts "correctly compiles in the RHS of an init many expression"
  else
    raise e
  end
end

def init_never
  $init_never ||= (Primitive.compiler_bailout('init_never compiled'); true)
end

begin
  $init_never = true
  loop do
    init_never
    Primitive.assert_not_compiled
  end
rescue Exception => e
  if e.message.include? 'assert_not_compiled'
    puts "correctly does not compile in the RHS of an init never expression"
  elsif e.message.include? 'init compiled'
    abort "incorrectly compiles in the RHS of an init never expression"
  else
    raise e
  end
end
