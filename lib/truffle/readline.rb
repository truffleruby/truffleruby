# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

Readline = Truffle::Readline

module Readline

  HISTORY = Object.new
  VERSION = 'JLine wrapper'

  %i[
    basic_quote_characters
    basic_quote_characters=
    completer_quote_characters
    completer_quote_characters=
    completer_word_break_characters
    completer_word_break_characters=
    completion_case_fold
    completion_case_fold=
    emacs_editing_mode
    emacs_editing_mode?
    filename_quote_characters
    filename_quote_characters=
    point=
    pre_input_hook
    pre_input_hook=
    redisplay
    refresh_line
    set_screen_size
    special_prefixes
    special_prefixes=
    vi_editing_mode
    vi_editing_mode?
  ].each do |method_name|
    define_singleton_method(method_name) do |*|
      raise NotImplementedError, "function Readline.#{method_name}() is unimplemented on this machine"
    end

    Primitive.method_unimplement method(method_name)
  end

  @completion_append_character = ' '
  @completion_proc = nil

  class << self
    attr_reader :completion_append_character, :completion_proc
  end

  def self.completion_append_character=(char)
    char = '' if Primitive.nil?(char)
    char = String(char)
    @completion_append_character = char.empty? ? nil : char[0]
  end

  def self.completion_proc=(proc)
    raise ArgumentError, "argument must respond to 'call'" unless proc.respond_to?(:call)

    Primitive.readline_set_completion_proc -> buffer {
      result = proc.call(buffer)
      unless Primitive.is_a?(result, Array)
        result = Array(result)
      end
      result.map do |e|
        Truffle::Type.coerce_to(e, String, :to_s)
      end.sort
    }

    @completion_proc = proc
  end

  def self.input=(input)
    Truffle::Type.rb_check_type(input, IO)
    Primitive.readline_set_input input.fileno, input
    input
  end

  def self.output=(output)
    Truffle::Type.rb_check_type(output, IO)
    Primitive.readline_set_output output.fileno, output
    output
  end

end

class << Readline::HISTORY

  include Enumerable
  include Truffle::ReadlineHistory

  def empty?
    size == 0
  end

  def to_s
    'HISTORY'
  end

end
