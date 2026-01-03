# frozen_string_literal: true

module RBS
  module AST
    module Directives
      class Base
      end

      class Use < Base
        class SingleClause
          attr_reader :type_name, :new_name, :location

          def initialize(type_name:, new_name:, location:)
            @type_name = type_name
            @new_name = new_name
            @location = location
          end
        end

        class WildcardClause
          attr_reader :namespace, :location

          def initialize(namespace:, location:)
            @location = location
            @namespace = namespace
          end
        end

        attr_reader :clauses, :location

        def initialize(clauses:, location:)
          @clauses = clauses
          @location = location
        end
      end

    end
  end
end
