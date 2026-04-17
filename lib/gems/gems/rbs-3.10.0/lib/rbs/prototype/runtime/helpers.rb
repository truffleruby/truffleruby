# frozen_string_literal: true

module RBS
  module Prototype
    class Runtime
      module Helpers
        private

        # Returns the exact name & not compactly declared name
        def only_name(mod)
          # No nil check because this method is invoked after checking if the module exists
          const_name!(mod).split(/::/).last or raise # (A::B::C) => C
        end

        def const_name!(const)
          const_name(const) or raise
        end

        def const_name(const)
          @module_name_method ||= Module.instance_method(:name)
          name = @module_name_method.bind(const).call
          return nil unless name

          begin
            deprecated, Warning[:deprecated] = Warning[:deprecated], false
            Object.const_get(name)
          rescue NameError
            # Should generate const name if anonymous or internal module (e.g. NameError::message)
            nil
          else
            name
          ensure
            Warning[:deprecated] = deprecated
          end
        end

        def to_type_name(name, full_name: false)
          *prefix, last = name.split(/::/)

          last or raise

          if full_name
            if prefix.empty?
              TypeName.new(name: last.to_sym, namespace: Namespace.empty)
            else
              TypeName.new(name: last.to_sym, namespace: Namespace.parse(prefix.join("::")))
            end
          else
            TypeName.new(name: last.to_sym, namespace: Namespace.empty)
          end
        end

        def untyped
          @untyped ||= Types::Bases::Any.new(location: nil)
        end
      end
    end
  end
end
