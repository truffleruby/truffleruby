# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
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

module Truffle::ProcOperations
  def self.curry(executable, args, arity)
    args.freeze

    name = executable.lambda? ? :lambda : :proc

    Proc.__send__(name) do |*a|
      all_args = args + a
      if all_args.size < arity
        curry executable, all_args, arity
      else
        executable[*all_args]
      end
    end
  end

  # variant of Thread::Backtrace::Location#syntax_tree to handle returning the CallNode instead of BlockNode
  def self.syntax_tree(receiver)
    require 'prism'
    range = receiver.source_range
    return nil unless range&.absolute_path
    result = Prism.parse_file(range.absolute_path, raise_error: true)
    start_offset = result.source.byte_offset(range.start_line, range.start_column)
    end_offset = result.source.byte_offset(range.end_line, range.end_column)
    result.value.tunnel(range.start_line, range.start_column).rfind do |n|
      case n
      when Prism::BlockNode
        nil
      when Prism::CallNode
        Primitive.is_a?(n.block, Prism::BlockNode) && n.block.start_offset == start_offset && n.block.end_offset == end_offset
      else
        n.start_offset == start_offset && n.end_offset == end_offset
      end
    end
  end
end
