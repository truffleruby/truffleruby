require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#require" do
    before :each do
      @box = Ruby::Box.new
    end

    before :each do
      @path = fixture(__FILE__, "box_a.rb")
    end

    it "requires the file in the box and returns true" do
      @box.require(@path).should == true
      @box::BoxSpecA::VERSION.should == "1.0"
      @box::BoxSpecA.version.should == "1.0"
      @box::BoxSpecA.new.yay.should == "yay 1.0"
      @box::BoxSpecA::Nested::VALUE.should == 42
    end

    it "returns false if the feature is already loaded in the box" do
      @box.require(@path).should == true
      @box.require(@path).should == false
    end

    it "does not define the constants in the main box" do
      @box.require(@path)
      Object.const_defined?(:BoxSpecA).should == false
      -> { BoxSpecA }.should.raise(NameError)
    end

    it "adds the feature to $LOADED_FEATURES of the box only" do
      @box.require(@path)
      @box.eval("$LOADED_FEATURES").to_a.should.include?(@path)
      $LOADED_FEATURES.should_not.include?(@path)
    end

    it "uses the $LOAD_PATH of the box" do
      box2 = Ruby::Box.new
      @box.load_path << fixture(__FILE__, "v1")
      box2.load_path << fixture(__FILE__, "v2")

      @box.require("box_spec_lib").should == true
      box2.require("box_spec_lib").should == true

      @box::BoxSpecLib::VERSION.should == "1.0"
      box2::BoxSpecLib::VERSION.should == "2.0"
      -> { require "box_spec_lib" }.should.raise(LoadError)
    end

    it "raises LoadError if the feature cannot be found" do
      -> { @box.require("box_spec_nonexistent") }.should.raise(LoadError)
    end

    it "propagates exceptions raised while loading the file" do
      -> { @box.require(fixture(__FILE__, "raise.rb")) }.should.raise(RuntimeError, "Yay!")
    end
  end
end
