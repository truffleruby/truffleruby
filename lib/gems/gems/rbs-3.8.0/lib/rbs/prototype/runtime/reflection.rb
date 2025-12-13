# frozen_string_literal: true

module RBS
  module Prototype
    class Runtime
      module Reflection
        def self.object_class(value)
          @object_class ||= Object.instance_method(:class)
          @object_class.bind_call(value)
        end

        def self.constants_of(mod, inherit = true)
          @constants_of ||= Module.instance_method(:constants)
          @constants_of.bind_call(mod, inherit)
        end
      end
    end
  end
end
