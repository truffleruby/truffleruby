require_relative '../../spec_helper'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby. Only shareable objects are used
# here, as an implementation without isolation does not need to freeze objects.
ruby_version_is "4.0" do
  describe "Ractor.make_shareable" do
    it "returns a shareable object as is" do
      [1, :sym, nil, true, 1.5, "frozen".freeze, [1, 2].freeze, Object, Ractor.current].each do |obj|
        Ractor.make_shareable(obj).should.equal?(obj)
        Ractor.shareable?(obj).should == true
      end
    end

    it "accepts a copy: keyword argument" do
      Ractor.make_shareable(1, copy: true).should == 1
      Ractor.make_shareable(1, copy: false).should == 1
      Ractor.make_shareable(:sym, copy: true).should == :sym
    end

    it "makes the object shareable" do
      obj = Ractor.make_shareable(["a".dup, "b".dup])
      Ractor.shareable?(obj).should == true
    end
  end
end
