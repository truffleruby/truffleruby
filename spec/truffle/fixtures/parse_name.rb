# Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Object
  def show(method_name)
    method =
      if method_name.is_a?(Method) || method_name.is_a?(UnboundMethod) || method_name.is_a?(Proc)
        method_name
      elsif respond_to?(method_name, true)
        method(method_name)
      else
        instance_method(method_name)
      end

    if ARGV == %w[root_node_name]
      puts Truffle::Debug.runtime_name(method)
    else
      puts Truffle::Debug.parse_name(method)
    end
  end
end

module M
  class C
    show def regular_instance_method
    end

    show def self.sdef_class_method
    end

    class << self
      show def sclass_method
      end

      def block_in_sclass_method
        -> {
          -> { }
        }.call
      end
    end
    show block_in_sclass_method
  end
end

class M::D
  show def scoped_method
  end

  show def self.sdef_scoped_method
  end

  class << self
    show def sclass_scoped_method
    end
  end

  class ::Top
    show def top
    end
  end

  class ::Top::Nested
    show def top_nested
    end

    class C
      show def top_nested_c
      end
    end
  end
end

show def top_method
end

show def self.sdef_method_of_main
end

class << self
  show def sclass_method_of_main
  end
end

SOME_OBJECT = Object.new
SOME_OBJECT.instance_exec do
  show def unknown_def_singleton_method
  end

  show def self.unknown_sdef_singleton_method
  end
end

Object.module_eval do
  show def module_eval_method
  end

  show def self.sdef_module_eval_method
  end
end

def String.string_class_method
end
show String.method(:string_class_method)

module Enumerable
  def String.nested_class_method
  end
end
show String.method(:nested_class_method)

module Enumerable
  module_function def mod_function
  end
  show instance_method(:mod_function)
  show method(:mod_function)
end

show 1.method(:+) # Java core method
show method(:then) # Ruby core method
