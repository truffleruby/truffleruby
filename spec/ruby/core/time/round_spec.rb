require_relative '../../spec_helper'

describe "Time#round" do
  before do
    @time = Time.utc(2010, 3, 30, 5, 43, "25.123456789".to_r)
  end

  it "defaults to rounding to 0 places" do
    @time.round.should == Time.utc(2010, 3, 30, 5, 43, 25.to_r)
  end

  it "rounds to 0 decimal places with an explicit argument" do
    @time.round(0).should == Time.utc(2010, 3, 30, 5, 43, 25.to_r)
  end

  it "rounds to 7 decimal places with an explicit argument" do
    @time.round(7).should == Time.utc(2010, 3, 30, 5, 43, "25.1234568".to_r)
  end

  it "converts the number of decimal places with #to_int" do
    places = mock('places')
    places.should_receive(:to_int).and_return(7)
    @time.round(places).should == Time.utc(2010, 3, 30, 5, 43, "25.1234568".to_r)
  end

  it "raises an ArgumentError for a negative number of decimal places" do
    -> { @time.round(-1) }.should.raise(ArgumentError, "negative ndigits given")
  end

  it "returns an instance of Time, even if #round is called on a subclass" do
    subclass = Class.new(Time)
    instance = subclass.at(0)
    instance.class.should.equal? subclass
    instance.round.should.instance_of?(Time)
  end

  it "copies own timezone to the returning value" do
    @time.zone.should == @time.round.zone

    with_timezone "JST-9" do
      time = Time.at 0, 1
      time.zone.should == time.round.zone
    end
  end
end
