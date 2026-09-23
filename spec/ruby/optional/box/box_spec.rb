require_relative '../../spec_helper'

# Ruby::Box needs to be enabled at startup with RUBY_BOX=1 on CRuby.
# To avoid a subprocess per spec, when Ruby::Box is not enabled this file is run
# in a single subprocess with RUBY_BOX=1 and the specs below run in that subprocess.

ruby_version_is "4.0" do
  if !Ruby::Box.enabled?
    describe "Ruby::Box" do
      it "passes its specs when enabled with RUBY_BOX=1" do
        mspec_run = File.expand_path("../../../mspec/bin/mspec-run", __dir__)
        output = ruby_exe(nil, options: "-W:no-experimental #{mspec_run}", args: __FILE__, env: { "RUBY_BOX" => "1" })
        output.should =~ /\b0 failures, 0 errors\b/
      end
    end
  else

describe "Ruby::Box" do
  before :each do
    @box = Ruby::Box.new
  end

  it "is a subclass of Module" do
    Ruby::Box.superclass.should == Module
    @box.should.is_a?(Module)
  end

  describe ".enabled?" do
    it "returns true when Ruby::Box is enabled" do
      Ruby::Box.enabled?.should == true
    end
  end

  describe ".new" do
    it "creates a new user box" do
      @box.should.instance_of?(Ruby::Box)
      @box.main?.should == false
      @box.root?.should == false
    end

    it "creates independent boxes" do
      box2 = Ruby::Box.new
      @box.should_not == box2

      @box.eval("BOX_SPEC_ISOLATION = 'first'")
      box2.eval("BOX_SPEC_ISOLATION = 'second'")
      @box::BOX_SPEC_ISOLATION.should == "first"
      box2::BOX_SPEC_ISOLATION.should == "second"
      Object.const_defined?(:BOX_SPEC_ISOLATION).should == false
    end
  end

  describe ".main" do
    it "returns the main box" do
      Ruby::Box.main.should.instance_of?(Ruby::Box)
      Ruby::Box.main.main?.should == true
      Ruby::Box.main.root?.should == false
      Ruby::Box.main.should == Ruby::Box.main
    end

    it "evaluates code in the main box" do
      Ruby::Box.main.eval("1 + 1").should == 2
      Ruby::Box.main.eval("Ruby::Box.current.main?").should == true
      Ruby::Box.main.eval("$box_spec_main_gvar = 42")
      $box_spec_main_gvar.should == 42
    end
  end

  describe ".root" do
    it "returns the root box" do
      Ruby::Box.root.should.instance_of?(Ruby::Box)
      Ruby::Box.root.root?.should == true
      Ruby::Box.root.main?.should == false
      Ruby::Box.root.should == Ruby::Box.root
      Ruby::Box.root.should_not == Ruby::Box.main
    end

    it "is isolated from the main box" do
      $box_spec_root_gvar = 0
      Ruby::Box.root.eval("$box_spec_root_gvar = 1; $box_spec_root_gvar").should == 1
      $box_spec_root_gvar.should == 0

      Ruby::Box.root.eval("class BoxSpecRootClass; end")
      Object.const_defined?(:BoxSpecRootClass).should == false
      Ruby::Box.root.eval("Object.const_defined?(:BoxSpecRootClass)").should == true

      Ruby::Box.root.eval("Ruby::Box.current.root?").should == true
    end
  end

  describe ".current" do
    it "returns the main box at the top-level" do
      Ruby::Box.current.should == Ruby::Box.main
      Ruby::Box.current.main?.should == true
    end

    it "returns the box in which the code runs" do
      @box.should == @box.eval("Ruby::Box.current")
      @box.eval("Ruby::Box.current.main?").should == false
      @box.eval("Ruby::Box.current.root?").should == false
      @box.eval("Ruby::Box.current.inspect").to_s.should =~ /\A#<Ruby::Box:\d+,user,optional>\z/
    end
  end

  describe "#inspect" do
    it "returns a String describing the box" do
      @box.inspect.should =~ /\A#<Ruby::Box:\d+,user,optional>\z/
      Ruby::Box.main.inspect.should =~ /\A#<Ruby::Box:\d+,user,main>\z/
      Ruby::Box.root.inspect.should =~ /\A#<Ruby::Box:\d+,root>\z/
    end
  end

  describe "#eval" do
    it "evaluates the code in the box and returns the result" do
      @box.eval("1 + 1").should == 2
      @box.eval("1.5 * 2").should == 3.0
      @box.eval("true").should == true
      @box.eval("false").should == false
      @box.eval("nil").should.nil?
      @box.eval("'hello ' + 'world'").should == "hello world"
      @box.eval("[1, 2, 3]").to_a.should == [1, 2, 3]
    end

    it "supports local variables" do
      @box.eval("x = 10; y = 20; x + y").should == 30
    end

    it "defines constants in the box" do
      @box.eval("BOX_SPEC_EVAL_CONST = 42")
      @box::BOX_SPEC_EVAL_CONST.should == 42
      @box.const_get(:BOX_SPEC_EVAL_CONST).should == 42
      Object.const_defined?(:BOX_SPEC_EVAL_CONST).should == false
      -> { BOX_SPEC_EVAL_CONST }.should.raise(NameError)
    end

    it "defines classes in the box" do
      @box.eval("class BoxSpecEvalClass; def hello; 'from box'; end; end")
      @box::BoxSpecEvalClass.new.hello.should == "from box"
      Object.const_defined?(:BoxSpecEvalClass).should == false
    end

    it "raises SyntaxError for invalid code" do
      -> { @box.eval("1 +") }.should.raise(SyntaxError)
    end

    it "raises NameError for an undefined variable" do
      -> { @box.eval("box_spec_undefined_variable") }.should.raise(NameError)
    end

    it "propagates exceptions raised in the box and the box remains usable" do
      -> { @box.eval("raise 'boom'") }.should.raise(RuntimeError, "boom")
      -> { @box.eval("raise ArgumentError, 'bad'") }.should.raise(ArgumentError, "bad")
      @box.eval("2 + 2").should == 4
    end

    it "keeps the backtrace of exceptions raised in the box" do
      -> { @box.eval("\n\nraise 'boom'") }.should.raise(RuntimeError, "boom") { |e|
        e.backtrace.should.is_a?(Array)
        e.backtrace[0].should =~ /:3:in /
      }
    end
  end

  describe "#require" do
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

  describe "#require_relative" do
    it "requires the file relative to the caller in the box" do
      @box.require_relative("fixtures/box_a").should == true
      @box::BoxSpecA::VERSION.should == "1.0"
      Object.const_defined?(:BoxSpecA).should == false
    end
  end

  describe "#load" do
    it "loads the file in the box each time and returns true" do
      path = fixture(__FILE__, "load_count.rb")
      @box.load(path).should == true
      @box.eval("$box_spec_load_count").should == 1
      @box.load(path).should == true
      @box.eval("$box_spec_load_count").should == 2
      $box_spec_load_count.should.nil?
    end
  end

  describe "#load_path" do
    it "returns the $LOAD_PATH of the box" do
      @box.load_path.should_not.equal?($LOAD_PATH)
      @box.load_path.to_a.should == @box.eval("$LOAD_PATH").to_a
    end
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

  describe "#const_get" do
    it "returns a constant of the box" do
      @box.require(fixture(__FILE__, "box_a.rb"))
      @box.const_get(:BoxSpecA).should == @box::BoxSpecA
      @box.const_get("BoxSpecA").should == @box::BoxSpecA
      @box.const_get("BoxSpecA::VERSION").should == "1.0"
      @box.const_get(:String).should == @box::String
      -> { @box.const_get(:BoxSpecUndefined) }.should.raise(NameError)
    end
  end

  describe "#const_defined?" do
    it "returns whether the constant is defined in the box" do
      @box.const_defined?(:BoxSpecA).should == false
      @box.require(fixture(__FILE__, "box_a.rb"))
      @box.const_defined?(:BoxSpecA).should == true
      @box.const_defined?("BoxSpecA::VERSION").should == true
      @box.const_defined?(:String).should == true
    end
  end

  describe "#constants" do
    it "returns the constants of the box" do
      @box.require(fixture(__FILE__, "box_a.rb"))
      constants = @box.constants.to_a.map(&:to_s)
      constants.should.include?("BoxSpecA")
      constants.should.include?("String")
      Object.constants.should_not.include?(:BoxSpecA)
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
end
