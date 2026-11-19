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

  def self.parse_source_range(range)
    require 'prism'
    return nil unless range&.absolute_path

    result = Prism.parse_file(range.absolute_path, raise_error: true)
    start_offset = result.source.byte_offset(range.start_line, range.start_column)
    end_offset = result.source.byte_offset(range.end_line, range.end_column)
    [result.value, start_offset, end_offset]
  end

  # The node of a Proc, Method or UnboundMethod is the node defining its body, and there is
  # at most one such node for a given source range. There is none for e.g. an attr_reader method.
  def self.callable_syntax_tree(range)
    root, start_offset, end_offset = parse_source_range(range)
    return nil unless root

    root.breadth_first_search do |node|
      node.start_offset == start_offset && node.end_offset == end_offset &&
        (Primitive.is_a?(node, Prism::DefNode) ||
          Primitive.is_a?(node, Prism::BlockNode) ||
          Primitive.is_a?(node, Prism::LambdaNode) ||
          Primitive.is_a?(node, Prism::ForNode))
    end
  end

  def self.location_syntax_tree(range)
    root, start_offset, end_offset = parse_source_range(range)
    return nil unless root

    # Prism::Node#tunnel only follows one child at each level. Some nodes with
    # the same range are siblings, notably the SymbolNode and implicit CallNode
    # for a keyword omission such as `target(missing:)`, so visit every branch
    # which contains the range.
    candidates = []
    stack = [[root, 0]]
    until stack.empty?
      node, depth = stack.pop
      if node.start_offset <= start_offset && node.end_offset >= end_offset
        if node.start_offset == start_offset && node.end_offset == end_offset
          candidates << [node, depth]
        end

        node.compact_child_nodes.reverse_each do |child|
          stack << [child, depth + 1]
        end
      end
    end

    eligible = candidates.select { |candidate,| location_syntax_tree_candidate?(candidate, candidates) }
    eligible = candidates if eligible.empty?
    eligible.max_by(&:last)&.first
  end

  def self.location_syntax_tree_candidate?(node, candidates)
    case node
    when Prism::ProgramNode,
        Prism::StatementsNode,
        Prism::BeginNode,
        Prism::EmbeddedStatementsNode,
        Prism::MatchWriteNode,
        Prism::ItParametersNode,
        Prism::NumberedParametersNode,
        Prism::AssocNode,
        Prism::AssocSplatNode,
        Prism::NoKeywordsParameterNode
      false
    when Prism::SymbolNode
      # A symbol can own an observable #=== call, except when it is the
      # structural key of an exact-location hash pattern.
      candidates.none? { |candidate,| Primitive.is_a?(candidate, Prism::HashPatternNode) }
    when Prism::SplatNode
      # A splat can own an observable #to_a call, except when the enclosing
      # array or array pattern owns the exact same source range.
      candidates.none? do |candidate,|
        Primitive.is_a?(candidate, Prism::ArrayNode) || Primitive.is_a?(candidate, Prism::ArrayPatternNode)
      end
    else
      true
    end
  end
end
