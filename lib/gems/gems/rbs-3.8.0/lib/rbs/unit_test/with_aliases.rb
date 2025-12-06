# frozen_string_literal: true

module RBS
  module UnitTest
    module WithAliases
      include Convertibles

      class WithEnum
        include Enumerable

        def initialize(enum) = @enum = enum

        def each(&block) = @enum.each(&block)

        def and_nil(&block)
          self.and(nil, &_ = block)
        end

        def but(*cases, &block)
          return WithEnum.new to_enum(__method__ || raise, *cases) unless block

          each do |arg|
            yield arg unless cases.any? { (_ = _1) === arg }
          end
        end

        def and(*args, &block)
          return WithEnum.new to_enum(__method__ || raise, *args) unless block

          each(&block)
          args.each do |arg|
            if WithEnum === arg # use `===` as `arg` might not have `.is_a?` on it
              arg.each(&block)
            else
              block.call(_ = arg)
            end
          end
        end
      end

      def with(*args, &block)
        return WithEnum.new to_enum(__method__ || raise, *args) unless block
        args.each(&block)
      end

      def with_int(value = 3, &block)
        return WithEnum.new to_enum(__method__ || raise, value) unless block
        yield value
        yield ToInt.new(value)
      end

      def with_float(value = 0.1)
        return WithEnum.new to_enum(__method__ || raise, value) unless block_given?
        yield value
        yield ToF.new(value)
      end

      def with_string(value = '')
        return WithEnum.new to_enum(__method__ || raise, value) unless block_given?
        yield value
        yield ToStr.new(value)
      end

      def with_array(*elements)
        return WithEnum.new to_enum(__method__ || raise, *elements) unless block_given?

        yield _ = elements
        yield ToArray.new(*elements)
      end

      def with_hash(hash = {})
        return WithEnum.new to_enum(__method__ || raise, hash) unless block_given?

        yield _ = hash
        yield ToHash.new(hash)
      end

      def with_io(io = $stdout)
        return WithEnum.new to_enum(__method__ || raise, io) unless block_given?
        yield io
        yield ToIO.new(io)
      end

      def with_path(path = "/tmp/foo.txt", &block)
        return WithEnum.new to_enum(__method__ || raise, path) unless block

        with_string(path, &block)
        block.call ToPath.new(path)
      end

      def with_encoding(encoding = Encoding::UTF_8, &block)
        return WithEnum.new to_enum(__method__ || raise, encoding) unless block

        block.call encoding
        with_string(encoding.to_s, &block)
      end

      def with_interned(value = :&, &block)
        return WithEnum.new to_enum(__method__ || raise, value) unless block

        with_string(value.to_s, &block)
        block.call value.to_sym
      end

      def with_bool(&block)
        return WithEnum.new to_enum(__method__ || raise) unless block
        yield true
        yield false
      end

      def with_boolish(&block)
        return WithEnum.new to_enum(__method__ || raise) unless block
        with_bool(&block)
        [nil, 1, Object.new, BlankSlate.new, "hello, world!"].each(&block)
      end

      alias with_untyped with_boolish

      def with_range(start, stop, exclude_end = false)
        # If you need fixed starting and stopping points, you can just do `with_range with(1), with(2)`.
        raise ArgumentError, '`start` must be from a `with` method' unless start.is_a? WithEnum
        raise ArgumentError, '`stop` must be from a `with` method' unless stop.is_a? WithEnum

        start.each do |lower|
          stop.each do |upper|
            yield CustomRange.new(lower, upper, exclude_end)

            # `Range` requires `begin <=> end` to return non-nil, but doesn't actually
            # end up using the return value of it. This is to add that in when needed.
            def lower.<=>(rhs) = :not_nil unless defined? lower.<=>

            # If `lower <=> rhs` is defined but nil, then that means we're going to be constructing
            # an illegal range (eg `3..ToInt.new(4)`). So, we need to skip yielding an invalid range
            # in that case.
            next if defined?(lower.<=>) && nil == (lower <=> upper)

            yield Range.new(lower, upper, exclude_end)
          end
        end
      end
    end
  end
end
