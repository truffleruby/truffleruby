# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  module Frontends
    module MRI

      CACHE_FILE = 'mri-rewrite-cache.rb'

      def self.load_mri(path, options)
        if options['--use-cache']
          load CACHE_FILE
        else
          require 'parser/current'
          require 'unparser'

          source = File.read(path)
          buffer = Parser::Source::Buffer.new(source)
          buffer.source = source
          parser = Parser::CurrentRuby.new
          ast = parser.parse(buffer)

          abort "AST rooted at unexpected #{ast.type.inspect}" unless ast.type == :begin
          last = ast.children.last
          abort "Last statement #{last.type.inspect} unexpected" unless [:while, :block, :send, :lvasgn].include? last.type

          assigns = {}

          rewriter = Class.new(Parser::Rewriter) do
            define_method :on_lvasgn do |node|
              if node == last
                on_node node
              elsif node.children.last.type == :int
                name = node.children.first
                value = node.children.last.children.last
                assigns[name] = value
              end
            end

            define_method :on_node do |node|
              return unless node == last

              assigns_source = ''

              assigns.each do |name, value|
                if node.to_s.include? "(lvar #{name.inspect})"
                  assigns_source += "#{name} = #{value}; "
                end
              end

              insert_before node.location.expression, 'BenchmarkInterface.benchmark { ' + assigns_source
              insert_after node.location.expression, ' }'
            end

            alias_method :on_while, :on_node
            alias_method :on_block, :on_node
            alias_method :on_send, :on_node
          end

          rewriter = rewriter.new
          rewritten = rewriter.rewrite(buffer, ast)

          if options['--show-rewrite']
            puts rewritten
          end

          if options['--cache']
            File.write(CACHE_FILE, rewritten)
            exit 1
          else
            Object.instance_eval rewritten
          end

        end
      end

    end
  end
end
