# frozen_string_literal: true

# Some of the code in this file was copied from the polyfill-data gem.
#
# MIT License
#
# Copyright (c) 2023 Jim Gay, Joel Drapper, Nicholas Evans
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.


module Net
  class IMAP
    data_or_object = RUBY_VERSION >= "3.2.0" ? ::Data : Object
    class DataLite < data_or_object
      def encode_with(coder) coder.map = to_h.transform_keys(&:to_s)        end
      def init_with(coder) initialize(**coder.map.transform_keys(&:to_sym)) end
    end

    Data = DataLite
  end
end

# :nocov:
# Need to skip test coverage for the rest, because it isn't loaded by ruby 3.2+.
return if RUBY_VERSION >= "3.2.0"

module Net
  class IMAP
    # DataLite is a temporary substitute for ruby 3.2's +Data+ class.  DataLite
    # is aliased as Net::IMAP::Data, so that code using it won't need to be
    # updated when it is removed.
    #
    # See {ruby 3.2's documentation for Data}[https://docs.ruby-lang.org/en/3.2/Data.html].
    #
    # [When running ruby 3.1]
    #    This class reimplements the API for ruby 3.2's +Data+, and should be
    #    compatible for nearly all use-cases.  This reimplementation <em>will be
    #    removed</em> in +net-imap+ 0.6, when support for ruby 3.1 is dropped.
    #
    #    _NOTE:_ +net-imap+ no longer supports ruby versions prior to 3.1.
    # [When running ruby >= 3.2]
    #    This class inherits from +Data+ and _only_ defines the methods needed
    #    for YAML serialization.  This will be dropped when +psych+ adds support
    #    for +Data+.
    #
    # Some of the code in this class was copied or adapted from the
    # {polyfill-data gem}[https://rubygems.org/gems/polyfill-data], by Jim Gay
    # and Joel Drapper, under the MIT license terms.
    class DataLite
      singleton_class.undef_method :new

      TYPE_ERROR    = "%p is not a symbol nor a string"
      ATTRSET_ERROR = "invalid data member: %p"
      DUP_ERROR     = "duplicate member: %p"
      ARITY_ERROR   = "wrong number of arguments (given %d, expected %s)"
      private_constant :TYPE_ERROR, :ATTRSET_ERROR, :DUP_ERROR, :ARITY_ERROR

      # Defines a new Data class.
      #
      # _NOTE:_ Unlike ruby 3.2's +Data.define+, DataLite.define only supports
      # member names which are valid local variable names.  Member names can't
      # be keywords (e.g: +next+ or +class+) or start with capital letters, "@",
      # etc.
      def self.define(*args, &block)
        members = args.each_with_object({}) do |arg, members|
          arg = arg.to_str unless arg in Symbol | String if arg.respond_to?(:to_str)
          arg = arg.to_sym if     arg in String
          arg in Symbol     or  raise TypeError,     TYPE_ERROR    % [arg]
          arg in %r{=}      and raise ArgumentError, ATTRSET_ERROR % [arg]
          members.key?(arg) and raise ArgumentError, DUP_ERROR     % [arg]
          members[arg] = true
        end
        members = members.keys.freeze

        klass = ::Class.new(self)

        klass.singleton_class.undef_method :define
        klass.define_singleton_method(:members) { members }

        def klass.new(*args, **kwargs, &block)
          if kwargs.size.positive?
            if args.size.positive?
              raise ArgumentError, ARITY_ERROR % [args.size, 0]
            end
          elsif members.size < args.size
            expected = members.size.zero? ? 0 : 0..members.size
            raise ArgumentError, ARITY_ERROR % [args.size, expected]
          else
            kwargs = Hash[members.take(args.size).zip(args)]
          end
          allocate.tap do |instance|
            instance.__send__(:initialize, **kwargs, &block)
          end.freeze
        end

        klass.singleton_class.alias_method :[], :new
        klass.attr_reader(*members)

        # Dynamically defined initializer methods are in an included module,
        # rather than directly on DataLite (like in ruby 3.2+):
        # * simpler to handle required kwarg ArgumentErrors
        # * easier to ensure consistent ivar assignment order (object shape)
        # * faster than instance_variable_set
        klass.include(Module.new do
          if members.any?
            kwargs = members.map{"#{_1.name}:"}.join(", ")
            params = members.map(&:name).join(", ")
            ivars  = members.map{"@#{_1.name}"}.join(", ")
            attrs  = members.map{"attrs[:#{_1.name}]"}.join(", ")
            module_eval <<~RUBY, __FILE__, __LINE__ + 1
              protected
              def initialize(#{kwargs}) #{ivars} = #{params}; freeze end
              def marshal_load(attrs)   #{ivars} = #{attrs};  freeze end
            RUBY
          end
        end)

        klass.module_eval do _1.module_eval(&block) end if block_given?

        klass
      end

      ##
      # singleton-method: new
      # call-seq:
      #   new(*args) -> instance
      #   new(**kwargs) -> instance
      #
      # Constuctor for classes defined with ::define.
      #
      # Aliased as ::[].

      ##
      # singleton-method: []
      # call-seq:
      #   ::[](*args) -> instance
      #   ::[](**kwargs) -> instance
      #
      # Constuctor for classes defined with ::define.
      #
      # Alias for ::new

      ##
      def members;     self.class.members                              end
      def to_h(&block) block ? __to_h__.to_h(&block) : __to_h__        end
      def hash;        [self.class, __to_h__].hash                     end
      def ==(other)    self.class == other.class && to_h == other.to_h end
      def eql?(other)  self.class == other.class && hash == other.hash end
      def deconstruct; __to_h__.values                                 end

      def deconstruct_keys(keys)
        raise TypeError unless keys.is_a?(Array) || keys.nil?
        return __to_h__ if keys&.first.nil?
        __to_h__.slice(*keys)
      end

      def with(**kwargs)
        return self if kwargs.empty?
        self.class.new(**__to_h__.merge(kwargs))
      end

      def inspect
        __inspect_guard__(self) do |seen|
          return "#<data #{self.class}:...>" if seen
          attrs = __to_h__.map {|kv| "%s=%p" % kv }.join(", ")
          display = ["data", self.class.name, attrs].compact.join(" ")
          "#<#{display}>"
        end
      end
      alias_method :to_s, :inspect

      private

      def initialize_copy(source) super.freeze end
      def marshal_dump; __to_h__ end

      def __to_h__; Hash[members.map {|m| [m, send(m)] }] end

      # Yields +true+ if +obj+ has been seen already, +false+ if it hasn't.
      # Marks +obj+ as seen inside the block, so circuler references don't
      # recursively trigger a SystemStackError (stack level too deep).
      #
      # Making circular references inside a Data object _should_ be very
      # uncommon, but we'll support them for the sake of completeness.
      def __inspect_guard__(obj)
        preexisting = Thread.current[:__net_imap_data__inspect__]
        Thread.current[:__net_imap_data__inspect__] ||= {}.compare_by_identity
        inspect_guard = Thread.current[:__net_imap_data__inspect__]
        if inspect_guard.include?(obj)
          yield true
        else
          begin
            inspect_guard[obj] = true
            yield false
          ensure
            inspect_guard.delete(obj)
          end
        end
      ensure
        unless preexisting.equal?(inspect_guard)
          Thread.current[:__net_imap_data__inspect__] = preexisting
        end
      end

    end

  end
end
# :nocov:
