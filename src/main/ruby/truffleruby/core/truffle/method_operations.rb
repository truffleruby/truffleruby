# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module MethodOperations
    def self.inspect_method(meth, origin, owner, receiver = undefined)
      extra = ''
      if Primitive.method_unimplemented? meth
        extra = ' (not-implemented)'
      else
        file, line = meth.source_location
        if file && line
          extra = " #{file}:#{line}"
        end
      end

      params = meth.parameters.map.with_index do |(type, name), i|
        case type
        when :req then "#{name || "param#{i+1}"}"
        when :opt then "#{name || "param#{i+1}"}=..."
        when :keyreq then "#{name || "kw#{i+1}"}:"
        when :key then "#{name || "kwparam#{i+1}"}: ..."
        when :rest then "*#{name || "rest"}"
        when :keyrest then "**#{name || "kwrest"}"
        when :block then "&#{name || "block"}"
        when :nokey then '**nil'
        end
      end.join(', ').prepend('(') << ')'

      if !Primitive.undefined?(receiver) && owner.singleton_class?
        "#<#{Primitive.class(meth)}: #{receiver.inspect}.#{meth.name}#{params}#{extra}>"
      else
        origin_owner = origin == owner ? origin : "#{origin}(#{owner})"
        "#<#{Primitive.class(meth)}: #{origin_owner}##{meth.name}#{params}#{extra}>"
      end
    end
  end
end
