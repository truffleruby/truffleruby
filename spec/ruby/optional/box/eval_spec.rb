require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#eval" do
    before :each do
      @box = Ruby::Box.new
    end

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
end
