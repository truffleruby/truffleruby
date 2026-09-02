require_relative '../fixtures/classes'

describe :enumerator_lazy_value_propagation, shared: true do
  # @lazy_method: a Proc calling the method under test on a lazy enumerator, e.g. -> e { e.take(12) }.

  before :each do
    @yieldsmixed = EnumeratorLazySpecs::YieldsMixed.new.to_enum.lazy
  end

  it "passes the source yields on to a later method in the chain unchanged" do
    yields = []
    @lazy_method.call(@yieldsmixed).map { |v| yields << v }.force
    yields.should == EnumeratorLazySpecs::YieldsMixed.initial_yields
  end

  it "passes every value of a multi-value source yield to a splat block later in the chain" do
    args = nil
    @lazy_method.call(Enumerator.new { |y| y.yield 1, 2 }.lazy).map { |*a| args = a }.force
    args.should == [1, 2]
  end

  it "passes a source yield with no value on as a single nil" do
    args = nil
    @lazy_method.call(Enumerator.new { |y| y.yield }.lazy).map { |*a| args = a }.force
    args.should == [nil]
  end

  it "yields a single value once a method in the chain replaces the value" do
    yields = []
    @lazy_method.call(Enumerator.new { |y| y.yield 1, 2 }.lazy).map { |x| x }.map { |*a| yields << a }.force
    yields.should == [[1]]
  end
end
