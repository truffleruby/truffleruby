require_relative '../../spec_helper'
require_relative 'fixtures/classes'

describe "Data#with" do
  it "returns self if given no arguments" do
    data = DataSpecs::Measure.new(amount: 42, unit: "km")
    data = data.with.should.equal?(data)
  end

  it "accepts keyword arguments" do
    data = DataSpecs::Measure.new(amount: 42, unit: "km")
    data = data.with(amount: 4, unit: "m")

    data.amount.should == 4
    data.unit.should == "m"
  end

  it "accepts String keyword arguments" do
    data = DataSpecs::Measure.new(amount: 42, unit: "km")
    data = data.with("amount" => 4, "unit" => "m")

    data.amount.should == 4
    data.unit.should == "m"
  end

  it "raises ArgumentError if no keyword arguments are given" do
    data = DataSpecs::Measure.new(amount: 42, unit: "km")

    -> {
      data.with(4, "m")
    }.should.raise(ArgumentError, "wrong number of arguments (given 2, expected 0)")
  end

  it "does not depend on the Data.new method" do
    subclass = Class.new(DataSpecs::Measure)
    data = subclass.new(amount: 42, unit: "km")

    def subclass.new(*)
      raise "Data.new is called"
    end

    data_copy = data.with(unit: "m")
    data_copy.amount.should == 42
    data_copy.unit.should == "m"
  end

  it "calls #initialize" do
    data = DataSpecs::DataWithOverriddenInitialize.new(42, "m")
    ScratchPad.clear

    data.with(amount: 0)

    ScratchPad.recorded.should == [:initialize, [], {amount: 0, unit: "m"}]
  end

  it "returns an instance of the subclass when called on an instance of a subclass" do
    data = DataSpecs::MeasureSubclass.new(amount: 42, unit: "km")
    data_copy = data.with(unit: "m")

    data_copy.should.instance_of?(DataSpecs::MeasureSubclass)
    data_copy.amount.should == 42
    data_copy.unit.should == "m"
  end

  it "calls #initialize of the subclass when called on an instance of a subclass" do
    subclass = Class.new(DataSpecs::Measure) do
      def initialize(*rest, **kw)
        super
        ScratchPad.record [:initialize, rest, kw]
      end
    end
    data = subclass.new(amount: 42, unit: "km")
    ScratchPad.clear

    data.with(amount: 0)

    ScratchPad.recorded.should == [:initialize, [], {amount: 0, unit: "km"}]
  end
end
