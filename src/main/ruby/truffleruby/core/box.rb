# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Ruby
  # Ruby::Box isolates code loaded in a box from the main box and from other boxes:
  # each box has its own constants, global variables, loaded features, core class methods, etc.
  #
  # On TruffleRuby, the root box and each user box is a separate Polyglot::InnerContext.
  # Values crossing the boundary of a box are polyglot values: Integer, Float, true and false
  # are converted, other objects (including String, Symbol, nil, Array, Hash and Class objects)
  # are foreign objects wrapping the object of the box, see doc/user/polyglot.md.
  # Exceptions raised in a box are translated to an instance of the exception class of the same name in the caller box.
  #
  # Ruby::Box.main is not available inside a user box, as a box has no reference to its creator.
  class Box < Module
    ROOT_ID = 1
    MAIN_ID = 2
    private_constant :ROOT_ID, :MAIN_ID

    class << self
      def enabled?
        true
      end

      # The box in which the calling code runs
      def current
        @current || main
      end

      def main
        if @current and !@current.main?
          raise NotImplementedError, 'Ruby::Box.main is not available inside a user box on TruffleRuby'
        end

        @main ||= create(:main, MAIN_ID, false)
      end

      # The root box has only the core library loaded, like a new box, but it is not affected by later requires
      def root
        @root ||= create(:root, ROOT_ID, true)
      end

      def new
        create(:user, next_id, true)
      end

      private

      def create(kind, id, own_context)
        box = allocate
        box.__send__(:initialize, kind, id, own_context)
        box
      end

      def next_id
        TruffleRuby.synchronized(self) do
          @next_id ||= MAIN_ID + 1
          id = @next_id
          @next_id += 1
          id
        end
      end

      # Called by the box which created this context, to define which box this context is
      def setup_current(kind, id)
        @current = create(kind, id, false)
        @next_id = id + 1
      end
    end

    def initialize(kind, id, own_context)
      super()
      @kind = kind
      @id = id
      if own_context
        @inner_context = Polyglot::InnerContext.new
        @inner_context.eval('ruby', "Ruby::Box.__send__(:setup_current, #{kind.inspect}, #{id})")
        # The Ruby::Box object representing this box inside the box, used for #==
        @inner_box = @inner_context.eval('ruby', 'Ruby::Box.current')
        # Object of the box, from which constants of the box are read
        @object = @inner_context.eval('ruby', 'Object')
      else
        @inner_context = nil
        @inner_box = nil
        @object = nil
      end
    end

    def main?
      @kind == :main
    end

    def root?
      @kind == :root
    end

    def inspect
      case @kind
      when :root
        "#<Ruby::Box:#{@id},root>"
      when :main
        "#<Ruby::Box:#{@id},user,main>"
      else
        "#<Ruby::Box:#{@id},user,optional>"
      end
    end
    alias_method :to_s, :inspect

    # Also equal to the box object obtained with Ruby::Box.current inside the box
    def ==(other)
      Primitive.equal?(self, other) || (!Primitive.nil?(@inner_box) && Truffle::Interop.identical?(@inner_box, other))
    end

    # Evaluates the code at the top-level of this box and returns the result
    def eval(code)
      code = Primitive.convert_with_to_str(code)
      evaluate(code)
    end

    def require(feature)
      feature = Truffle::Type.coerce_to_path(feature)
      evaluate("require #{feature.dump}")
    end

    # Requires the file relative to the file calling this method, in this box
    def require_relative(feature)
      feature = Truffle::Type.coerce_to_path(feature)
      require(Primitive.get_caller_path(feature))
    end

    def load(path)
      path = Truffle::Type.coerce_to_path(path)
      evaluate("load #{path.dump}")
    end

    # The $LOAD_PATH of this box
    def load_path
      evaluate('$LOAD_PATH')
    end

    # Constants of this box, i.e. of the Object class of this box

    def constants(inherit = true)
      evaluate("Object.constants(#{Primitive.as_boolean(inherit)})")
    end

    def const_defined?(name, inherit = true)
      evaluate("Object.const_defined?(#{constant_name(name).dump}, #{Primitive.as_boolean(inherit)})")
    end

    def const_get(name, inherit = true)
      evaluate("Object.const_get(#{constant_name(name).dump}, #{Primitive.as_boolean(inherit)})")
    end

    # Implements box::CONST
    def const_missing(name)
      if Primitive.nil?(@inner_context)
        Object.const_get(name)
      elsif Truffle::Interop.member_readable?(@object, name)
        translate_exceptions { Truffle::Interop.read_member(@object, name) }
      else
        raise NameError.new("uninitialized constant #{inspect}::#{name}", name, receiver: self)
      end
    end

    private

    def constant_name(name)
      if Primitive.is_a?(name, Symbol)
        name.name
      else
        Primitive.convert_with_to_str(name)
      end
    end

    # foreign_box::CONST from another box sends readMember(foreign_box, "CONST")
    def polyglot_read_member(name)
      if name.match?(/\A[[:upper:]]/)
        const_get(name)
      else
        Primitive.dispatch_missing
      end
    end

    def polyglot_member_readable?(name)
      if name.match?(/\A[[:upper:]]/)
        const_defined?(name)
      else
        Primitive.dispatch_missing
      end
    end

    def evaluate(code)
      if Primitive.nil?(@inner_context)
        Kernel.eval(code, TOPLEVEL_BINDING.dup, '(eval)')
      else
        translate_exceptions { @inner_context.eval('ruby', code) }
      end
    end

    def translate_exceptions
      yield
    rescue Polyglot::ForeignException => foreign_exception
      raise translate_exception(foreign_exception), cause: nil
    end

    # Translates an exception raised in the box to an instance of the exception class of the same name in this box,
    # so that e.g. `rescue RuntimeError` works. If there is no such class here, the foreign exception is returned as is.
    def translate_exception(foreign_exception)
      class_name = Truffle::Interop.meta_qualified_name(Truffle::Interop.meta_object(foreign_exception)).to_s
      exception_class = begin
        Object.const_defined?(class_name) && Object.const_get(class_name)
      rescue NameError
        nil
      end
      unless Primitive.is_a?(exception_class, Class) && exception_class <= Exception
        return foreign_exception
      end

      message = foreign_exception.message
      exception = begin
        Primitive.nil?(message) ? exception_class.new : exception_class.new(message.to_s)
      rescue StandardError
        return foreign_exception
      end
      exception.set_backtrace(foreign_exception.backtrace)

      cause = foreign_exception.cause
      unless Primitive.nil?(cause)
        Primitive.exception_set_cause(exception, translate_exception(cause))
      end

      exception
    end
  end
end
