# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Kernel
  def Array(obj)
    ary = Truffle::Type.rb_check_convert_type obj, Array, :to_ary

    return ary if ary

    if array = Truffle::Type.rb_check_convert_type(obj, Array, :to_a)
      array
    else
      [obj]
    end
  end
  module_function :Array

  def Complex(real, imag = undefined, exception: true)
    Complex.__send__(:convert, real, imag, exception: exception)
  end
  module_function :Complex

  def Float(obj, exception: true)
    raise_exception = !Primitive.false?(exception)
    obj = Truffle::Interop.unbox_if_needed(obj)

    case obj
    when String
      converted = Primitive.string_to_f obj
      if Primitive.nil?(converted) && raise_exception
        raise ArgumentError, "invalid value for Float(): #{obj.inspect}"
      else
        converted
      end
    when Float
      obj
    when nil
      if raise_exception
        raise TypeError, "can't convert nil into Float"
      else
        nil
      end
    when Complex
      if obj.respond_to?(:imag) && Primitive.equal?(obj.imag, 0)
        Truffle::Type.coerce_to obj, Float, :to_f
      else
        raise RangeError, "can't convert #{obj} into Float"
      end
    else
      if raise_exception
        Truffle::Type.rb_convert_type(obj, Float, :to_f)
      else
        Truffle::Type.rb_check_convert_type(obj, Float, :to_f)
      end
    end
  end
  module_function :Float

  def Hash(obj)
    return {} if Primitive.nil?(obj) || obj == []

    if hash = Truffle::Type.rb_check_convert_type(obj, Hash, :to_hash)
      return hash
    end

    raise TypeError, "can't convert #{Primitive.class(obj)} into Hash"
  end
  module_function :Hash

  def Integer(obj, base = 0, exception: true)
    obj = Truffle::Interop.unbox_if_needed(obj)
    converted_base = Truffle::Type.rb_check_to_integer(base, :to_int)
    base = Primitive.nil?(converted_base) ? 0 : converted_base
    raise_exception = !Primitive.false?(exception)

    if Primitive.is_a?(obj, String)
      Primitive.string_to_inum(obj, base, true, raise_exception)
    else
      bad_base_check = Proc.new do
        if base != 0
          return nil unless raise_exception
          raise ArgumentError, 'base is only valid for String values'
        end
      end
      case obj
      when Integer
        bad_base_check.call
        obj
      when Float
        bad_base_check.call
        if obj.nan? or obj.infinite?
          return nil unless raise_exception
        end
        # TODO BJF 14-Jan-2020 Add fixable conversion logic
        obj.to_int
      when NilClass
        bad_base_check.call
        return nil unless raise_exception
        raise TypeError, "can't convert nil into Integer"
      else
        if base != 0
          converted_to_str_obj = Truffle::Type.rb_check_convert_type(obj, String, :to_str)
          return Primitive.string_to_inum(converted_to_str_obj, base, true, raise_exception) unless Primitive.nil? converted_to_str_obj
          return nil unless raise_exception
          raise ArgumentError, 'base is only valid for String values'
        end
        converted_to_int_obj = Truffle::Type.rb_check_to_integer(obj, :to_int)
        return converted_to_int_obj unless Primitive.nil? converted_to_int_obj

        return Truffle::Type.rb_check_to_integer(obj, :to_i) unless raise_exception
        Truffle::Type.rb_convert_type(obj, Integer, :to_i)
      end
    end
  end
  module_function :Integer

  def Rational(a, b = 1, exception: true)
    Rational.__send__ :convert, a, b, exception
  end
  module_function :Rational

  def String(obj)
    str = Truffle::Type.rb_check_convert_type(obj, String, :to_str)
    if Primitive.nil? str
      str = Truffle::Type.rb_convert_type(obj, String, :to_s)
    end
    str
  end
  module_function :String

  ##
  # MRI uses a macro named StringValue which has essentially the same
  # semantics as Truffle::Type.rb_convert_type obj, String, :to_str, but rather than using that
  # long construction everywhere, we define a private method similar to
  # String().

  def StringValue(obj)
    Truffle::Type.rb_convert_type obj, String, :to_str
  end
  module_function :StringValue

  def `(str) #`
    str = StringValue(str) unless Primitive.is_a?(str, String)

    output = IO.popen(str) { |io| io.read }

    Truffle::Type.external_string output
  end
  module_function :` # `

  def !~(other)
    r = self =~ other ? false : true
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, $~)
    r
  end
  Primitive.always_split self, :!~

  def itself
    self
  end

  def abort(msg = nil)
    Process.abort msg
  end
  module_function :abort

  def autoload(name, file)
    nesting = Primitive.caller_nesting
    mod = nesting.first || (Primitive.equal?(Kernel, self) ? Kernel : Object)
    if Primitive.equal?(mod, self)
      super(name, file) # Avoid recursion
    else
      mod.autoload(name, file)
    end
  end
  module_function :autoload

  def autoload?(name)
    if Primitive.equal?(Kernel, self)
      super(name) # Avoid recursion
    else
      Object.autoload?(name)
    end
  end
  module_function :autoload?

  # Take this alias name so RubyGems will reuse this method
  # and skip the method below once RubyGems is loaded.
  private def gem_original_require(feature)
    feature = Truffle::Type.coerce_to_path(feature)

    status, path = Truffle::FeatureLoader.find_feature_or_file(feature)
    case status
    when :feature_loaded
      false
    when :feature_found
      Primitive.load_feature(feature, path)
    when :not_found
      raise Truffle::KernelOperations.load_error(feature)
    end
  end
  Truffle::Graal.never_split(instance_method(:gem_original_require))

  # A #require which lazily loads rubygems when needed.
  # The logic is inlined so there is no extra backtrace entry for lazy-rubygems.
  private def require(feature)
    feature = Truffle::Type.coerce_to_path(feature)

    lazy_rubygems = Truffle::Boot.get_option_or_default('lazy-rubygems', false)
    upgraded_default_gem = lazy_rubygems &&
        !Truffle::KernelOperations.loading_rubygems? &&
        Truffle::GemUtil.upgraded_default_gem?(feature)
    if upgraded_default_gem
      status, path = :not_found, nil # load RubyGems
    else
      status, path = Truffle::FeatureLoader.find_feature_or_file(feature)
    end

    case status
    when :feature_loaded
      false
    when :feature_found
      Primitive.load_feature(feature, path)
    when :not_found
      if lazy_rubygems
        Truffle::KernelOperations.loading_rubygems = true
        gem_original_require 'rubygems'
        Truffle::KernelOperations.loading_rubygems = false

        # Check that #require was redefined by RubyGems, otherwise we would end up in infinite recursion
        new_require = ::Kernel.instance_method(:require)
        if new_require == Truffle::KernelOperations::ORIGINAL_REQUIRE
          raise 'RubyGems did not redefine #require as expected, make sure $LOAD_PATH and home are set correctly'
        end
        new_require.bind_call(self, feature)
      else
        raise Truffle::KernelOperations.load_error(feature)
      end
    end
  end
  Truffle::Graal.never_split(instance_method(:require))
  Truffle::KernelOperations::ORIGINAL_REQUIRE = instance_method(:require)

  # Kernel.require is not redefined by RubyGems, and it should not load gems.
  # So define it as #gem_original_require, which is the same as the original #require
  # but without lazy-rubygems behavior. This replaces `module_function :require` which would be incorrect.
  define_singleton_method :require, instance_method(:gem_original_require)
  Truffle::KernelOperations::ORIGINAL_MODULE_REQUIRE = Kernel.method(:require)

  def require_relative(feature)
    feature = Truffle::Type.coerce_to_path(feature)
    feature = Primitive.get_caller_path(feature)

    status, path = Truffle::FeatureLoader.find_feature_or_file(feature)
    case status
    when :feature_loaded
      false
    when :feature_found
      # The first argument needs to be the expanded path here for patching to work correctly
      Primitive.load_feature(path, path)
    when :not_found
      raise Truffle::KernelOperations.load_error(feature)
    end
  end
  module_function :require_relative
  Truffle::Graal.never_split(instance_method(:require_relative))

  def display(port = $>)
    port.write self
  end

  def exec(*args)
    Process.exec(*args)
  end
  module_function :exec

  def exit(code = 0)
    Process.exit(code)
  end
  module_function :exit

  def exit!(code = 1)
    Process.exit!(code)
  end
  module_function :exit!

  def extend(*modules)
    raise ArgumentError, 'wrong number of arguments (0 for 1+)' if modules.empty?

    block = proc do |mod|
      if !Primitive.is_a?(mod, Module) or Primitive.is_a?(mod, Class)
        raise TypeError, "wrong argument type #{Primitive.class(mod)} (expected Module)"
      elsif Primitive.is_a?(mod, Refinement)
        raise TypeError, 'Cannot extend object with refinement'
      end

      mod.__send__ :extend_object, self
      mod.__send__ :extended, self
    end

    # __send__ calls above report polymorphism because they see different singleton classes for each module instance.
    # But these methods are called only once per object and module pair, so it is not worth to split for them.
    Truffle::Graal.never_split block

    modules.reverse_each(&block)

    self
  end
  Truffle::Graal.never_split instance_method(:extend)

  def getc
    $stdin.getc
  end
  module_function :getc

  def gets(...)
    line = ARGF.gets(...)
    Primitive.io_last_line_set(Primitive.caller_special_variables, line) if line
    line
  end
  module_function :gets

  def inspect
    prefix = "#<#{Primitive.class(self)}:0x#{self.__id__.to_s(16)}"

    ivars = Primitive.object_ivars self

    if ivars.empty?
      return "#{prefix}>"
    end

    Truffle::ThreadOperations.detect_recursion(self) do
      parts = ivars.map do |var|
        value = Primitive.object_ivar_get self, var
        "#{var}=#{value.inspect}"
      end
      return "#{prefix} #{parts.join(', ')}>"
    end

    # If it's already been inspected, return the ...
    "#{prefix} ...>"
  end

  def load(filename, wrap = false)
    filename = Truffle::Type.coerce_to_path filename

    # module_to_wrap either is a module or is nil
    if wrap
      module_to_wrap = Primitive.is_a?(wrap, Module) ? wrap : Module.new
    end

    # load absolute path
    if filename.start_with? File::SEPARATOR
      return Primitive.kernel_load File.expand_path(filename), module_to_wrap
    end

    # if path starts with . only try relative paths
    if filename.start_with? '.'
      return Primitive.kernel_load File.expand_path(filename), module_to_wrap
    end

    # try to resolve with current working directory
    if Truffle::FileOperations.exist?(filename)
      return Primitive.kernel_load File.expand_path(filename), module_to_wrap
    end

    # try to find relative path in $LOAD_PATH
    $LOAD_PATH.each do |dir|
      path = File.expand_path(File.join(dir, filename))
      if Truffle::FileOperations.exist?(path)
        return Primitive.kernel_load path, module_to_wrap
      end
    end

    # file not found trigger an error
    Primitive.kernel_load filename, module_to_wrap
  end
  module_function :load

  def loop
    return to_enum(:loop) { Float::INFINITY } unless block_given?

    begin
      while true
        yield
      end
    rescue StopIteration => si
      si.result
    end
  end
  module_function :loop

  def open(obj, *rest, **options, &block)
    if obj.respond_to?(:to_open)
      obj = obj.to_open(*rest, **options)

      if block_given?
        return yield(obj)
      else
        return obj
      end
    end

    path = Truffle::Type.coerce_to_path obj

    if Primitive.is_a?(path, String) and path.start_with? '|'
      return IO.popen(path[1..-1], *(rest + [options]), &block)
    end

    File.open(path, *rest, **options, &block)
  end
  module_function :open

  # Kernel#p is in post.rb

  def print(*args)
    args = [Primitive.io_last_line_get(Primitive.caller_special_variables)] if args.empty?
    args.each do |obj|
      $stdout.write obj.to_s
    end
    nil
  end
  module_function :print

  def putc(int)
    $stdout.putc(int)
  end
  module_function :putc

  def puts(*args)
    stdout = $stdout
    if Primitive.equal?(self, stdout)
      Truffle::IOOperations.puts(stdout, *args)
    else
      stdout.__send__(:puts, *args)
    end
    nil
  end
  module_function :puts

  def rand(limit = nil)
    randomizer = Primitive.thread_randomizer
    if Primitive.nil?(limit)
      randomizer.random_float
    elsif Primitive.is_a?(limit, Range)
      begin
        Truffle::RandomOperations.rand_range(randomizer, limit)
      rescue ArgumentError # invalid argument - negative limit
        nil
      end
    else
      max = Primitive.rb_to_int(limit)
      if max == 0
        randomizer.random_float
      else
        Truffle::RandomOperations.rand_int(randomizer, max, false)
      end
    end
  end
  module_function :rand

  def readline(sep = $/)
    ARGF.readline(sep)
  end
  module_function :readline

  def readlines(sep = $/)
    ARGF.readlines(sep)
  end
  module_function :readlines

  def select(*args)
    IO.select(*args)
  end
  module_function :select

  def srand(seed = undefined)
    if Primitive.undefined? seed
      seed = Primitive.thread_randomizer.generate_seed
    end

    seed = Truffle::Type.coerce_to seed, Integer, :to_int
    Primitive.thread_randomizer.swap_seed seed
  end
  module_function :srand

  def tap
    yield self
    self
  end

  def then
    if block_given?
      yield self
    else
      [self].to_enum { 1 }
    end
  end
  alias_method :yield_self, :then

  def test(cmd, file1, file2 = nil)
    case cmd
    when ?d
      File.directory? file1
    when ?e
      File.exist? file1
    when ?f
      File.file? file1
    when ?l
      File.symlink? file1
    when ?r
      File.readable? file1
    when ?R
      File.readable_real? file1
    when ?w
      File.writable? file1
    when ?W
      File.writable_real? file1
    when ?A
      File.atime file1
    when ?C
      File.ctime file1
    when ?M
      File.mtime file1
    else
      raise NotImplementedError, "command ?#{cmd.chr} not implemented"
    end
  end
  module_function :test

  def to_enum(method = :each, *args, **kwargs, &block)
    Enumerator.new(self, method, *args, **kwargs).tap do |enum|
      enum.__send__ :size=, block if block_given?
    end
  end
  alias_method :enum_for, :to_enum


  def trap(sig, prc = nil, &block)
    Signal.trap(sig, prc, &block)
  end
  module_function :trap

  def spawn(*args)
    Process.spawn(*args)
  end
  module_function :spawn

  def syscall(*args)
    raise NotImplementedError
  end
  module_function :syscall

  def system(*args)
    options = Truffle::Type.try_convert(args.last, Hash, :to_hash)
    exception = if options
                  args[-1] = options
                  options.delete(:exception)
                else
                  false
                end

    begin
      pid = Process.spawn(*args)
    rescue SystemCallError => e
      raise e if exception
      return nil
    end

    Process.waitpid pid
    result = $?.exitstatus == 0
    return true if result
    if exception
      # TODO  (bjfish, 9 Jan 2020): refactoring needed for more descriptive errors
      raise RuntimeError, 'command failed'
    else
      false
    end
  end
  module_function :system

  def trace_var(name, cmd = nil, &block)
    if !cmd && !block
      raise ArgumentError,
        'The 2nd argument should be a Proc/String, alternatively use a block'
    end

    # Truffle: not yet implemented
  end
  module_function :trace_var

  def untrace_var(name, cmd = undefined)
    # Truffle: not yet implemented
  end
  module_function :untrace_var

  def warn(*messages, uplevel: nil, category: nil)
    if !Primitive.nil?($VERBOSE) && !messages.empty?
      unless Primitive.nil?(category)
        category = Truffle::Type.rb_convert_type(category, Symbol, :to_sym)
        Truffle::WarningOperations.check_category(category)
        return nil unless Warning[category]
      end

      prefix = if Primitive.nil?(uplevel)
                 ''
               else
                 uplevel = Primitive.rb_to_int(uplevel)
                 raise ArgumentError, "negative level (#{uplevel})" unless uplevel >= 0

                 uplevel += 1 # skip Kernel#warn itself
                 initial, = Kernel.caller_locations(uplevel, 1)
                 caller = initial
                 # MRI would reuse the file:line of the user code caller for methods defined in C.
                 # Similarly, we skip <internal:* calls, notably to skip Kernel#require calls.
                 while caller and path = caller.path and
                     (path.start_with?('<internal:') || path.end_with?('/rubygems/core_ext/kernel_require.rb'))
                   uplevel += 1
                   caller, = Kernel.caller_locations(uplevel, 1)
                 end
                 caller = initial unless caller

                 if caller
                   "#{caller.path}:#{caller.lineno}: warning: "
                 else
                   'warning: '
                 end
               end

      stringio = Truffle::StringOperations::SimpleStringIO.new(+prefix)
      Truffle::IOOperations.puts(stringio, *messages)
      message = stringio.string

      if Primitive.equal?(self, Warning) # avoid recursion when redefining Warning#warn
        unless message.encoding.ascii_compatible?
          raise Encoding::CompatibilityError, "ASCII incompatible encoding: #{message.encoding}"
        end

        $stderr.write message
      else
        warning_warn = Warning.method(:warn)
        if warning_warn.arity == 1
          warning_warn.call(message)
        else
          warning_warn.call(message, category: category)
        end
      end
    end

    nil
  end
  module_function :warn

  def raise(exc = undefined, msg = undefined, ctx = nil, cause: undefined, **kwargs)
    cause_given = !Primitive.undefined?(cause)

    if Primitive.undefined?(exc) && cause_given
      raise ArgumentError, 'only cause is given with no arguments'
    end

    if Primitive.undefined?(exc) && $!
      exc = $!
    else
      if Primitive.undefined?(msg) && !kwargs.empty?
        msg = kwargs
      end

      exc = Truffle::ExceptionOperations.build_exception_for_raise(exc, msg)
      exc.set_backtrace(ctx) if ctx
      Primitive.exception_capture_backtrace(exc, 1) unless Truffle::ExceptionOperations.backtrace?(exc)

      if cause_given
        unless Primitive.is_a?(cause, ::Exception) || Primitive.nil?(cause)
          Truffle::ExceptionOperations.exception_object_expected!
        end
      else
        if Primitive.nil?(exc.cause) && !Primitive.exception_used_as_a_cause?(exc)
          cause = $!
        else
          cause = nil
        end
      end

      if !Primitive.nil?(cause) && (cause_given || Primitive.nil?(exc.cause)) && !Primitive.equal?(cause, exc)
        if Truffle::ExceptionOperations.circular_cause?(cause, exc)
          raise ArgumentError, 'circular causes'
        end

        Primitive.exception_set_cause exc, cause
        Primitive.exception_used_as_a_cause!(cause)
      end
    end

    Truffle::ExceptionOperations.show_exception_for_debug(exc, 1) if $DEBUG
    Primitive.vm_raise_exception exc
  end
  module_function :raise

  alias_method :fail, :raise
  module_function :fail

  def __dir__
    path = Kernel.caller_locations(1, 1).first.absolute_path
    File.dirname(path)
  end
  module_function :__dir__

  def printf(*args)
    return nil if args.empty?
    if Primitive.is_a?(args[0], String)
      print sprintf(*args)
    else
      io = args.shift
      io.write(sprintf(*args))
    end
    nil
  end
  module_function :printf
  Primitive.always_split self, :printf
  Primitive.always_split singleton_class, :printf

  private def pp(*args)
    require 'pp'
    pp(*args)
  end

  def caller(start = 1, limit = nil)
    args =  if Primitive.is_a?(start, Range)
              if Primitive.nil?(start.begin) and Primitive.nil?(start.end)
                [1]
              elsif Primitive.nil? start.begin
                size = start.end + 1
                size -= 1 if start.exclude_end?
                [1, size]
              elsif Primitive.nil? start.end
                [start.begin + 1]
              else
                [start.begin + 1, start.size]
              end
            elsif Primitive.nil? limit
              [start + 1]
            else
              [start + 1, limit]
            end
    Kernel.caller_locations(*args).map(&:to_s)
  end
  module_function :caller

  def caller_locations(omit = 1, length = undefined)
    # This could be implemented as a call to Thread#backtrace_locations, but we don't do this
    # to avoid the SafepointAction overhead in the primitive call.
    omit, length = Truffle::KernelOperations.normalize_backtrace_args(omit, length)
    Primitive.kernel_caller_locations(omit, length)
  end
  module_function :caller_locations

  def at_exit(&block)
    raise ArgumentError, 'called without a block' if Primitive.nil?(block)
    Primitive.add_regular_at_exit_hook(block)
  end
  module_function :at_exit

  def fork
    Process._fork
  end
  module_function :fork
  Primitive.method_unimplement instance_method(:fork)
  Primitive.method_unimplement method(:fork)

  def clone(freeze: nil)
    unless Primitive.boolean_or_nil?(freeze)
      raise ArgumentError, "unexpected value for freeze: #{Primitive.class(freeze)}"
    end

    Primitive.kernel_clone self, freeze
  end
  Primitive.always_split self, :clone
  Primitive.always_split singleton_class, :clone

  def initialize_clone(from, freeze: nil)
    initialize_copy(from)
  end

  Truffle::Boot.delay do
    if Truffle::Boot.get_option('gets-loop')
      def chomp(separator = $/)
        last_line = Primitive.io_last_line_get(Primitive.caller_special_variables)
        result = Truffle::KernelOperations.check_last_line(last_line).chomp(separator)
        Primitive.io_last_line_set(Primitive.caller_special_variables, result)
        result
      end
      module_function :chomp

      def chop
        last_line = Primitive.io_last_line_get(Primitive.caller_special_variables)
        result = Truffle::KernelOperations.check_last_line(last_line).chop
        Primitive.io_last_line_set(Primitive.caller_special_variables, result)
        result
      end
      module_function :chop
    end
  end
end
