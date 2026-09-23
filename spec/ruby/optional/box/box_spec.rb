require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box" do
    before :each do
      @box = Ruby::Box.new
    end

    it "is a subclass of Module" do
      Ruby::Box.superclass.should == Module
      @box.should.is_a?(Module)
    end

    describe "constant lookup with ::" do
      before :each do
        @box.require(fixture(__FILE__, "box_a.rb"))
      end

      it "returns a constant defined in the box" do
        @box::BoxSpecA.should == @box.eval("BoxSpecA")
        @box::BoxSpecA::VERSION.should == "1.0"
        @box::BoxSpecA::Nested.should == @box.eval("BoxSpecA::Nested")
        @box::BoxSpecA::Nested::VALUE.should == 42
      end

      it "returns a core class of the box" do
        @box::String.should == @box.eval("String")
        @box::Object.should == @box.eval("Object")
      end

      it "raises NameError for a constant not defined in the box" do
        -> { @box::BoxSpecUndefined }.should.raise(NameError) { |e|
          e.name.should == :BoxSpecUndefined
          e.receiver.should == @box
          e.message.should.include?("BoxSpecUndefined")
        }
        -> { @box::BoxSpecA::UNDEFINED }.should.raise(NameError)
      end

      it "triggers autoload in the box" do
        @box.require(fixture(__FILE__, "autoload.rb"))
        autoloaded = fixture(__FILE__, "autoloaded.rb")
        @box.eval("BoxSpecAutoload.autoload?(:Autoloaded)").should == autoloaded.delete_suffix(".rb")

        @box::BoxSpecAutoload::Autoloaded::VALUE.should == 42
        @box.eval("BoxSpecAutoload.autoload?(:Autoloaded)").should.nil?
        @box.eval("$LOADED_FEATURES").to_a.should.include?(autoloaded)

        Object.const_defined?(:BoxSpecAutoload).should == false
        $LOADED_FEATURES.should_not.include?(autoloaded)
      end

      it "works with a box created in a box" do
        @box.require(fixture(__FILE__, "nested_box.rb"))
        @box::BOX_SPEC_NESTED::BoxSpecA::VERSION.should == "1.0"
        @box::BOX_SPEC_NESTED::BoxSpecA::Nested::VALUE.should == 42
      end
    end

    describe "objects from a box" do
      before :each do
        @box.require(fixture(__FILE__, "box_a.rb"))
      end

      it "can be used from the main box" do
        obj = @box::BoxSpecA.new
        obj.yay.should == "yay 1.0"
        obj.should.is_a?(@box::BoxSpecA)
        (@box::BoxSpecA === obj).should == true
      end

      it "resolve constants in the box when called from the main box" do
        proc_from_box = @box.eval("-> { BoxSpecA::VERSION }")
        proc_from_box.call.should == "1.0"
        @box.eval("BoxSpecA.method(:version)").call.should == "1.0"
      end
    end

    describe "isolation" do
      it "keeps methods added to core classes in a box invisible in the main box" do
        @box.eval("class String; def box_spec_yay; 'yay'; end; end")
        @box.eval("'x'.box_spec_yay").should == "yay"
        -> { "x".box_spec_yay }.should.raise(NoMethodError)
        String.method_defined?(:box_spec_yay).should == false
      end

      it "keeps constants added to core classes in a box invisible in the main box" do
        @box.eval("String::BOX_SPEC_STRING_CONST = 1")
        @box.eval("String::BOX_SPEC_STRING_CONST").should == 1
        String.const_defined?(:BOX_SPEC_STRING_CONST).should == false
        -> { String::BOX_SPEC_STRING_CONST }.should.raise(NameError)
      end

      it "keeps instance variables set on core classes in a box invisible in the main box" do
        @box.eval("String.instance_variable_set(:@box_spec_ivar, 1)")
        @box.eval("String.instance_variable_get(:@box_spec_ivar)").should == 1
        String.instance_variable_defined?(:@box_spec_ivar).should == false
      end

      it "has separate global variables per box" do
        $box_spec_gvar = "main"
        @box.eval("$box_spec_gvar").should.nil?
        @box.eval("$box_spec_gvar = 'box'; $box_spec_gvar").should == "box"
        $box_spec_gvar.should == "main"
      ensure
        $box_spec_gvar = nil
      end

      it "does not see requires done in the main box" do
        path = fixture(__FILE__, "box_a.rb")
        @box.require(path)
        box2 = Ruby::Box.new
        box2.const_defined?(:BoxSpecA).should == false
        box2.eval("$LOADED_FEATURES").to_a.should_not.include?(path)
      end
    end
  end
end
