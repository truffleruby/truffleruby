describe :ractor_local_storage_get, shared: true do
  before :each do
    @key = :"ractor_local_storage_#{rand(1_000_000)}"
  end

  it "returns nil if no value was stored for the key" do
    Ractor.current[@key].should == nil
    Ractor[@key].should == nil
  end

  it "returns the value stored for the key in the current Ractor" do
    Ractor.current[@key] = :value
    Ractor.current[@key].should == :value
    Ractor[@key].should == :value
  end

  it "converts a String key to a Symbol" do
    Ractor.current[@key] = :value
    Ractor.current[@key.to_s].should == :value
    Ractor[@key.to_s].should == :value
  end

  it "raises TypeError if the key is not a Symbol or a String" do
    -> { Ractor.current[42] }.should.raise(TypeError, "42 is not a symbol nor a string")
    -> { Ractor[42] }.should.raise(TypeError, "42 is not a symbol nor a string")
  end

  it "is Ractor-local" do
    Ractor.current[@key] = :main
    r = Ractor.new(@key) { |key| [Ractor.current[key], Ractor[key]] }
    r.value.should == [nil, nil]
  end

  it "is shared between Threads of the same Ractor" do
    Ractor.current[@key] = :main
    Thread.new { Ractor.current[@key] }.value.should == :main
    Thread.new { Ractor[@key] }.value.should == :main
  end
end

describe :ractor_local_storage_set, shared: true do
  before :each do
    @key = :"ractor_local_storage_#{rand(1_000_000)}"
  end

  it "stores the value for the key in the current Ractor" do
    Ractor.current[@key] = :value
    Ractor.current[@key].should == :value
    Ractor[@key] = :other
    Ractor.current[@key].should == :other
  end

  it "returns the value" do
    (Ractor.current[@key] = :value).should == :value
    (Ractor[@key] = :value).should == :value
  end

  it "converts a String key to a Symbol" do
    Ractor.current[@key.to_s] = :value
    Ractor.current[@key].should == :value
    Ractor[@key.to_s] = :other
    Ractor.current[@key].should == :other
  end

  it "raises TypeError if the key is not a Symbol or a String" do
    -> { Ractor.current[42] = :value }.should.raise(TypeError, "42 is not a symbol nor a string")
    -> { Ractor[42] = :value }.should.raise(TypeError, "42 is not a symbol nor a string")
  end

  it "is Ractor-local" do
    r = Ractor.new(@key) { |key| Ractor.current[key] = :ractor; Ractor[key] = :ractor2; Ractor.current[key] }
    r.value.should == :ractor2
    Ractor.current[@key].should == nil
  end

  it "is shared between Threads of the same Ractor" do
    Thread.new { Ractor.current[@key] = :thread }.join
    Ractor.current[@key].should == :thread
    Thread.new { Ractor[@key] = :thread2 }.join
    Ractor.current[@key].should == :thread2
  end
end
