# frozen_string_literal: true

module RBS
  module AST
    module Members
      class Base
      end

      class MethodDefinition < Base
        class Overload
          attr_reader :method_type, :annotations

          def initialize(method_type:, annotations:)
            @method_type = method_type
            @annotations = annotations
          end

          def ==(other)
            other.is_a?(Overload) && other.method_type == method_type && other.annotations == annotations
          end

          def hash
            method_type.hash ^ annotations.hash
          end

          alias eql? ==

          def update(annotations: self.annotations, method_type: self.method_type)
            Overload.new(annotations: annotations, method_type: method_type)
          end

          def sub(subst)
            update(method_type: self.method_type.sub(subst))
          end

          def to_json(state = _ = nil)
            {
              annotations: annotations,
              method_type: method_type
            }.to_json(state)
          end
        end

        attr_reader :name
        attr_reader :kind
        attr_reader :overloads
        attr_reader :annotations
        attr_reader :location
        attr_reader :comment
        attr_reader :overloading
        attr_reader :visibility

        def initialize(name:, kind:, overloads:, annotations:, location:, comment:, overloading:, visibility:)
          @name = name
          @kind = kind
          @overloads = overloads
          @annotations = annotations
          @location = location
          @comment = comment
          @overloading = overloading
          @visibility = visibility
        end

        def ==(other)
          other.is_a?(MethodDefinition) &&
            other.name == name &&
            other.kind == kind &&
            other.overloads == overloads &&
            other.overloading? == overloading? &&
            other.visibility == visibility
        end

        alias eql? ==

        def hash
          name.hash ^ kind.hash ^ overloads.hash ^ overloading?.hash ^ visibility.hash
        end

        def instance?
          kind == :instance || kind == :singleton_instance
        end

        def singleton?
          kind == :singleton || kind == :singleton_instance
        end

        def overloading?
          overloading
        end

        def update(name: self.name, kind: self.kind, overloads: self.overloads, annotations: self.annotations, location: self.location, comment: self.comment, overloading: self.overloading?, visibility: self.visibility)
          self.class.new(
            name: name,
            kind: kind,
            overloads: overloads,
            annotations: annotations,
            location: location,
            comment: comment,
            overloading: overloading,
            visibility: visibility
          )
        end

        def to_json(state = _ = nil)
          {
            member: :method_definition,
            name: name,
            kind: kind,
            overloads: overloads,
            annotations: annotations,
            location: location,
            comment: comment,
            overloading: overloading?,
            visibility: visibility
          }.to_json(state)
        end
      end

      module Var
        attr_reader :name
        attr_reader :type
        attr_reader :location
        attr_reader :comment

        def initialize(name:, type:, location:, comment:)
          @name = name
          @type = type
          @location = location
          @comment = comment
        end

        def ==(other)
          other.is_a?(self.class) && other.name == name && other.type == type
        end

        alias eql? ==

        def hash
          name.hash ^ type.hash
        end
      end

      class InstanceVariable < Base
        include Var

        def to_json(state = _ = nil)
          {
            member: :instance_variable,
            name: name,
            type: type,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      class ClassInstanceVariable < Base
        include Var

        def to_json(state = _ = nil)
          {
            member: :class_instance_variable,
            name: name,
            type: type,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      class ClassVariable < Base
        include Var

        def to_json(state = _ = nil)
          {
            member: :class_variable,
            name: name,
            type: type,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      module Mixin
        attr_reader :name
        attr_reader :args
        attr_reader :annotations
        attr_reader :location
        attr_reader :comment

        def initialize(name:, args:, annotations:, location:, comment:)
          @name = name
          @args = args
          @annotations = annotations
          @location = location
          @comment = comment
        end

        def ==(other)
          other.is_a?(self.class) && other.name == name && other.args == args
        end

        def eql?(other)
          self == other
        end

        def hash
          name.hash ^ args.hash
        end
      end

      class Include < Base
        include Mixin

        def to_json(state = _ = nil)
          {
            member: :include,
            name: name,
            args: args,
            annotations: annotations,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      class Extend < Base
        include Mixin

        def to_json(state = _ = nil)
          {
            member: :extend,
            name: name,
            args: args,
            annotations: annotations,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      class Prepend < Base
        include Mixin

        def to_json(state = _ = nil)
          {
            member: :prepend,
            name: name,
            args: args,
            annotations: annotations,
            location: location,
            comment: comment
          }.to_json(state)
        end
      end

      module Attribute
        attr_reader :name
        attr_reader :type
        attr_reader :kind
        attr_reader :ivar_name
        attr_reader :annotations
        attr_reader :location
        attr_reader :comment
        attr_reader :visibility

        def initialize(name:, type:, ivar_name:, kind:, annotations:, location:, comment:, visibility: nil)
          @name = name
          @type = type
          @ivar_name = ivar_name
          @annotations = annotations
          @location = location
          @comment = comment
          @kind = kind
          @visibility = visibility
        end

        def ==(other)
          other.is_a?(self.class) &&
            other.name == name &&
            other.type == type &&
            other.ivar_name == ivar_name &&
            other.kind == kind &&
            other.visibility == visibility
        end

        alias eql? ==

        def hash
          name.hash ^ type.hash ^ ivar_name.hash ^ kind.hash ^ visibility.hash
        end

        def update(name: self.name, type: self.type, ivar_name: self.ivar_name, kind: self.kind, annotations: self.annotations, location: self.location, comment: self.comment, visibility: self.visibility)
          klass = _ = self.class
          klass.new(
            name: name,
            type: type,
            ivar_name: ivar_name,
            kind: kind,
            annotations: annotations,
            location: location,
            comment: comment,
            visibility: visibility
          )
        end
      end

      class AttrReader < Base
        include Attribute

        def to_json(state = _ = nil)
          {
            member: :attr_reader,
            name: name,
            type: type,
            ivar_name: ivar_name,
            kind: kind,
            annotations: annotations,
            location: location,
            comment: comment,
            visibility: visibility
          }.to_json(state)
        end
      end

      class AttrAccessor < Base
        include Attribute

        def to_json(state = _ = nil)
          {
            member: :attr_accessor,
            name: name,
            type: type,
            ivar_name: ivar_name,
            kind: kind,
            annotations: annotations,
            location: location,
            comment: comment,
            visibility: visibility
          }.to_json(state)
        end
      end

      class AttrWriter < Base
        include Attribute

        def to_json(state = _ = nil)
          {
            member: :attr_writer,
            name: name,
            type: type,
            ivar_name: ivar_name,
            kind: kind,
            annotations: annotations,
            location: location,
            comment: comment,
            visibility: visibility
          }.to_json(state)
        end
      end

      module LocationOnly
        attr_reader :location

        def initialize(location:)
          @location = location
        end

        def ==(other)
          other.is_a?(self.class)
        end

        alias eql? ==

        def hash
          self.class.hash
        end
      end

      class Public < Base
        include LocationOnly

        def to_json(state = _ = nil)
          { member: :public, location: location }.to_json(state)
        end
      end

      class Private < Base
        include LocationOnly

        def to_json(state = _ = nil)
          { member: :private, location: location }.to_json(state)
        end
      end

      class Alias < Base
        attr_reader :new_name
        attr_reader :old_name
        attr_reader :kind
        attr_reader :annotations
        attr_reader :location
        attr_reader :comment

        def initialize(new_name:, old_name:, kind:, annotations:, location:, comment:)
          @new_name = new_name
          @old_name = old_name
          @kind = kind
          @annotations = annotations
          @location = location
          @comment = comment
        end

        def ==(other)
          other.is_a?(self.class) &&
            other.new_name == new_name &&
            other.old_name == old_name &&
            other.kind == kind
        end

        alias eql? ==

        def hash
          new_name.hash ^ old_name.hash ^ kind.hash
        end

        def to_json(state = _ = nil)
          {
            member: :alias,
            new_name: new_name,
            old_name: old_name,
            kind: kind,
            annotations: annotations,
            location: location,
            comment: comment
          }.to_json(state)
        end

        def instance?
          kind == :instance
        end

        def singleton?
          kind == :singleton
        end
      end
    end
  end
end
