require_relative '../../spec_helper'
require_relative '../../fixtures/source_range_helpers'

ruby_version_is "4.1" do
  describe "Ruby::Prism" do
    before :each do
      skip "parse.y" if parse_y?
    end

    it "is autoloaded and does not load the prism gem" do
      expected = <<~EXPECTED
        ruby/prism
        Ruby::Prism::ProgramNode
        nil
        true
      EXPECTED

      ruby_exe(<<~RUBY).should == expected
        puts Ruby.autoload?(:Prism)
        puts Ruby::Prism.parse("1 + 2").value.class
        p defined?(::Prism)
        p $LOADED_FEATURES.any? { |feature| feature.end_with?("/ruby/prism.rb") }
      RUBY
    end

    it "is independent of the Prism module of the prism gem" do
      expected = <<~EXPECTED
        false
        Prism::ProgramNode
        Ruby::Prism::ProgramNode
      EXPECTED

      ruby_exe(<<~RUBY).should == expected
        require "prism"
        p Prism.equal?(Ruby::Prism)
        puts Prism.parse("1 + 2").value.class
        puts Ruby::Prism.parse("1 + 2").value.class
      RUBY
    end

    it "is the namespace of the nodes returned by #syntax_tree" do
      expected = <<~EXPECTED
        Ruby::Prism::BlockNode
        nil
      EXPECTED

      ruby_exe(<<~RUBY).should == expected
        puts proc { 42 }.syntax_tree.class
        p defined?(::Prism)
      RUBY
    end
  end
end
