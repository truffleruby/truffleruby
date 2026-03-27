# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

# This test requires splitting (--engine.Splitting) which is only available with the OptimizedTruffleRuntime.
# It fails under --experimental-engine-caching because CallInternalMethodNode does not have cached specializations for
# !isSingleContext() and so ends up using an IndirectCallNode which prevents splitting.
guard -> { Primitive.vm_splitting_enabled? &&
  !Truffle::Boot.get_option('experimental-engine-caching') &&
  Truffle::Boot.get_option("default-cache") != 0 } do
  describe "Inline caching for dynamically-created Regexp works for" do
    before :each do
      @performance_warnings, Warning[:performance] = Warning[:performance], true
    end

    after :each do
      Warning[:performance] = @performance_warnings
    end

    it "Regexp.new" do
      # Check that separate call sites with fixed input does not warn
      -> {
        Regexp.new("a")
        Regexp.new("b")
        Regexp.new("c")
        Regexp.new("d")
        Regexp.new("e")
        Regexp.new("f")
        Regexp.new("g")
        Regexp.new("h")
        Regexp.new("i")
        Regexp.new("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          Regexp.new(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "Regexp.union with 1 argument" do
      # Check that separate call sites with fixed input do not warn
      -> {
        Regexp.union("a")
        Regexp.union("b")
        Regexp.union("c")
        Regexp.union("d")
        Regexp.union("e")
        Regexp.union("f")
        Regexp.union("g")
        Regexp.union("h")
        Regexp.union("i")
        Regexp.union("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          Regexp.union(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "Regexp.union with multiple arguments" do
      # Check that separate call sites with fixed input do not warn
      -> {
        Regexp.union("h", "a")
        Regexp.union("h", "b")
        Regexp.union("h", "c")
        Regexp.union("h", "d")
        Regexp.union("h", "e")
        Regexp.union("h", "f")
        Regexp.union("h", "g")
        Regexp.union("h", "h")
        Regexp.union("h", "i")
        Regexp.union("h", "j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          Regexp.union("h", pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "interpolated Regexp" do
      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          /#{pattern}/
        end
      }.should complain(/unstable interpolated regexps/)
    end

    it "String#scan" do
      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".scan("a")
        "zzz".scan("b")
        "zzz".scan("c")
        "zzz".scan("d")
        "zzz".scan("e")
        "zzz".scan("f")
        "zzz".scan("g")
        "zzz".scan("h")
        "zzz".scan("i")
        "zzz".scan("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        # "a".."z" and not just "a".."j" because there can be some late heuristic megamorphic splitting by TRegex (ExecCompiledRegexNode)
        ("a".."z").each do |pattern|
          "zzz".scan(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#sub" do
      # Don't use String explicitly to trigger Truffle::Type.coerce_to_regexp. String argument is handled with
      # Primitive.matchdata_create_single_group and isn't converted to Regexp immediately.
      pattern = Class.new do
        def initialize(string) = @string = string
        def to_str = @string
      end

      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".sub(pattern.new("a"), "replacement")
        "zzz".sub(pattern.new("b"), "replacement")
        "zzz".sub(pattern.new("c"), "replacement")
        "zzz".sub(pattern.new("d"), "replacement")
        "zzz".sub(pattern.new("e"), "replacement")
        "zzz".sub(pattern.new("f"), "replacement")
        "zzz".sub(pattern.new("g"), "replacement")
        "zzz".sub(pattern.new("h"), "replacement")
        "zzz".sub(pattern.new("i"), "replacement")
        "zzz".sub(pattern.new("j"), "replacement")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |s|
          "zzz".sub(pattern.new(s), "replacement")
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#sub!" do
      # Don't use String explicitly to trigger Truffle::Type.coerce_to_regexp. String argument is handled with
      # Primitive.matchdata_create_single_group and isn't converted to Regexp immediately.
      pattern = Class.new do
        def initialize(string) = @string = string
        def to_str = @string
      end

      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".sub!(pattern.new("a"), "replacement")
        "zzz".sub!(pattern.new("b"), "replacement")
        "zzz".sub!(pattern.new("c"), "replacement")
        "zzz".sub!(pattern.new("d"), "replacement")
        "zzz".sub!(pattern.new("e"), "replacement")
        "zzz".sub!(pattern.new("f"), "replacement")
        "zzz".sub!(pattern.new("g"), "replacement")
        "zzz".sub!(pattern.new("h"), "replacement")
        "zzz".sub!(pattern.new("i"), "replacement")
        "zzz".sub!(pattern.new("j"), "replacement")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |s|
          "zzz".sub!(pattern.new(s), "replacement")
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#gsub" do
      # Don't use String explicitly to trigger Truffle::Type.coerce_to_regexp. String argument is handled with
      # Primitive.matchdata_create_single_group and isn't converted to Regexp immediately.
      pattern = Class.new do
        def initialize(string) = @string = string
        def to_str = @string
      end

      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".gsub(pattern.new("a"), "replacement")
        "zzz".gsub(pattern.new("b"), "replacement")
        "zzz".gsub(pattern.new("c"), "replacement")
        "zzz".gsub(pattern.new("d"), "replacement")
        "zzz".gsub(pattern.new("e"), "replacement")
        "zzz".gsub(pattern.new("f"), "replacement")
        "zzz".gsub(pattern.new("g"), "replacement")
        "zzz".gsub(pattern.new("h"), "replacement")
        "zzz".gsub(pattern.new("i"), "replacement")
        "zzz".gsub(pattern.new("j"), "replacement")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |s|
          "zzz".gsub(pattern.new(s), "replacement")
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#gsub!" do
      # Don't use String explicitly to trigger Truffle::Type.coerce_to_regexp. String argument is handled with
      # Primitive.matchdata_create_single_group and isn't converted to Regexp immediately.
      pattern = Class.new do
        def initialize(string) = @string = string
        def to_str = @string
      end

      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".gsub!(pattern.new("a"), "replacement")
        "zzz".gsub!(pattern.new("b"), "replacement")
        "zzz".gsub!(pattern.new("c"), "replacement")
        "zzz".gsub!(pattern.new("d"), "replacement")
        "zzz".gsub!(pattern.new("e"), "replacement")
        "zzz".gsub!(pattern.new("f"), "replacement")
        "zzz".gsub!(pattern.new("g"), "replacement")
        "zzz".gsub!(pattern.new("h"), "replacement")
        "zzz".gsub!(pattern.new("i"), "replacement")
        "zzz".gsub!(pattern.new("j"), "replacement")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |s|
          "zzz".gsub!(pattern.new(s), "replacement")
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#match" do
      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".match("a")
        "zzz".match("b")
        "zzz".match("c")
        "zzz".match("d")
        "zzz".match("e")
        "zzz".match("f")
        "zzz".match("g")
        "zzz".match("h")
        "zzz".match("i")
        "zzz".match("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          "zzz".match(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "String#match?" do
      # Check that separate call sites with fixed input do not warn
      -> {
        "zzz".match?("a")
        "zzz".match?("b")
        "zzz".match?("c")
        "zzz".match?("d")
        "zzz".match?("e")
        "zzz".match?("f")
        "zzz".match?("g")
        "zzz".match?("h")
        "zzz".match?("i")
        "zzz".match?("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          "zzz".match?(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "Symbol#match" do
      # Check that separate call sites with fixed input do not warn
      -> {
        :zzz.match("a")
        :zzz.match("b")
        :zzz.match("c")
        :zzz.match("d")
        :zzz.match("e")
        :zzz.match("f")
        :zzz.match("g")
        :zzz.match("h")
        :zzz.match("i")
        :zzz.match("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          :zzz.match(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end

    it "Symbol#match?" do
      # Check that separate call sites with fixed input do not warn
      -> {
        :zzz.match?("a")
        :zzz.match?("b")
        :zzz.match?("c")
        :zzz.match?("d")
        :zzz.match?("e")
        :zzz.match?("f")
        :zzz.match?("g")
        :zzz.match?("h")
        :zzz.match?("i")
        :zzz.match?("j")
      }.should_not complain

      # Check that calling it with many different inputs has the warning
      -> {
        ("a".."z").each do |pattern|
          :zzz.match?(pattern)
        end
      }.should complain(/unbounded creation of regexps/)
    end
  end
end
