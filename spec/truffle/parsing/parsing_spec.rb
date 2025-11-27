# truffleruby_primitives: true

# Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

# Test cases are stored in YAML files of the following structure:
#
#   ``` yaml
#   subject: "short description"
#   description: "long description"
#   notes: >
#     "some additional details to explain what this case actually tests (optional)"
#   focused_on_node: "a node class name" (optional)
#   main_script: true
#   index: "integer, position of a node to focus on in AST when there are several such nodes and we need not the first one (optional)"
#   ruby: |
#     <Ruby source code>
#   ast: |
#     <Truffle AST>
#   ```
#
# The following attributes might be a multiline string,
# so leading and terminating blank characters should be removed:
# - description
# - source_code
# - expected_ast
#
# index is an optional attribute. Missing it means we need a node with index 0.
#
# main_script - false by default. Means it's a loaded/required Ruby source file or the main one that is run by user.
# Some logic is related to main script, e.g. initialization of the DATA constant etc.
#
# Don't run specs in multi-context mode and with JIT because they affect AST:
# - in multi-context mode some nodes are replaced with dynamic-lexical-scope related ones
# - with JIT some nodes are replaced with optimized ones (e.g. OptimizedCallTarget)
#
# To regenerate fixture YAML files with actual AST run the following command:
#
#   OVERWRITE_PARSING_RESULTS=true jt -q test spec/truffle/parsing/parsing_spec.rb
#
# To ensure that fixtures are valid and are passing on the new translator, use:
#
#   jt -q test spec/truffle/parsing/parsing_spec.rb
#
# An approach with YAML.dump has some downsides:
# - it adds a line `---` at a file beginning
# - it removes unnecessary "" for string literals
# - it looses the folded style (with > indicator) for multiline blocks
# - it looses comments inside YAML document
# So just replace AST in a YAML file using a regexp.

overwrite = ENV['OVERWRITE_PARSING_RESULTS'] == 'true'
fixtures_glob = ENV['TRUFFLE_PARSING_GLOB']

describe "Parsing" do
  require 'yaml'

  filenames = Dir["#{__dir__}/fixtures/**/*.yaml"]
  filenames = Dir[fixtures_glob] if fixtures_glob

  filenames.each do |filename|
    yaml = YAML.safe_load_file(filename)
    subject, description, focused_on_node, index, main_script, source_code, expected_ast = yaml.values_at("subject", "description", "focused_on_node", "index", "main_script", "ruby", "ast")

    description&.strip!
    focused_on_node ||= "org.truffleruby.language.RubyTopLevelRootNode"
    source_code.strip!
    expected_ast.strip!
    index = index.to_i
    main_script = !!main_script

    # multi-context mode, JIT and changed default inline cache size affect the produced Truffle AST.
    # So just don't run the parsing specs at all in such jobs on CI.
    guard -> { Primitive.vm_single_context? && !TruffleRuby.jit? && Truffle::Boot.get_option("default-cache") != 0 && !Truffle::Boot.get_option("concurrent-hash-always") } do
      it "a #{subject} (#{description}) case is parsed correctly" do
        # p "a #{subject} (#{description.strip}) case is parsed correctly"

        actual_ast = Truffle::Debug.parse_and_dump_truffle_ast(source_code, focused_on_node, index, main_script).strip

        if overwrite
          example = File.read(filename)
          actual_ast_with_indentation = actual_ast.lines.map { |line| "  " + line }.join
          replaced = example.sub!(/^ast: \|.+\Z/m, "ast: |\n" + actual_ast_with_indentation)

          File.write filename, example

          # ensure it's still a valid YAML document
          YAML.safe_load_file(filename)

          if replaced
            skip "overwritten result file"
          else
            raise "The file #{filename} wasn't updated with actual AST"
          end
        else
          # actual test check
          unless actual_ast == expected_ast
            $stderr.puts "\n#{filename}\nYARP AST:", Truffle::Debug.parse_ast(source_code)
          end
          actual_ast.should == expected_ast
        end
      end
    end
  end
end
