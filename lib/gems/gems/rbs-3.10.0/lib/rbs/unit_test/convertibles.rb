# frozen_string_literal: true

module RBS
  module UnitTest
    module Convertibles
      class BlankSlate < BasicObject
        instance_methods.each do |im|
          next if im == :__send__
          undef_method im
        end

        def __with_object_methods(*methods)
          methods.each do |method|
            singleton_class = ::Object.instance_method(:singleton_class).bind_call(self) #: Class
            singleton_class.instance_eval do
              define_method method, ::Object.instance_method(method)
            end
          end
          self
        end
      end

      class ToIO < BlankSlate
        def initialize(io = $stdout)
          @io = io
        end

        def to_io
          @io
        end
      end

      class ToI < BlankSlate
        def initialize(value = 3)
          @value = value
        end

        def to_i
          @value
        end
      end

      class ToInt < BlankSlate
        def initialize(value = 3)
          @value = value
        end

        def to_int
          @value
        end
      end

      class ToF < BlankSlate
        def initialize(value = 0.1)
          @value = value
        end

        def to_f
          @value
        end
      end

      class ToR < BlankSlate
        def initialize(value = 1r)
          @value = value
        end

        def to_r
          @value
        end
      end

      class ToC < BlankSlate
        def initialize(value = 1i)
          @value = value
        end

        def to_c
          @value
        end
      end

      class ToStr < BlankSlate
        def initialize(value = "")
          @value = value
        end

        def to_str
          @value
        end
      end

      class ToS < BlankSlate
        def initialize(value = "")
          @value = value
        end

        def to_s
          @value
        end
      end

      class ToSym < BlankSlate
        def initialize(value = :&)
          @value = value
        end

        def to_sym
          @value
        end
      end

      class ToA < BlankSlate
        def initialize(*args)
          @args = args
        end

        def to_a
          @args
        end
      end

      class ToArray < BlankSlate
        def initialize(*args)
          @args = args
        end

        def to_ary
          @args
        end
      end

      class ToHash < BlankSlate
        def initialize(hash = { 'hello' => 'world' })
          @hash = hash
        end

        def to_hash
          @hash
        end
      end

      class ToPath < BlankSlate
        def initialize(value = "")
          @value = value
        end

        def to_path
          @value
        end
      end

      class CustomRange < BlankSlate
        attr_reader :begin, :end

        def initialize(begin_, end_, exclude_end = false)
          @begin = begin_
          @end = end_
          @exclude_end = exclude_end
        end

        def exclude_end? = @exclude_end
      end

      class Each < BlankSlate
        def initialize(*args)
          @args = args
        end

        def each(&block)
          @args.each(&block)
        end
      end
    end
  end
end
