require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.store_if_absent" do
    before :each do
      @key = :"ractor_store_if_absent_#{rand(1_000_000)}"
    end

    it "stores the value of the block if there is no value for the key and returns it" do
      Ractor.store_if_absent(@key) { :value }.should == :value
      Ractor.current[@key].should == :value
    end

    it "returns the existing value without calling the block if there is a value for the key" do
      Ractor.current[@key] = :value
      Ractor.store_if_absent(@key) { raise "should not be called" }.should == :value
      Ractor.current[@key].should == :value
    end

    it "converts a String key to a Symbol" do
      Ractor.store_if_absent(@key.to_s) { :value }.should == :value
      Ractor.current[@key].should == :value
    end

    it "raises TypeError if the key is not a Symbol or a String" do
      -> { Ractor.store_if_absent(42) { :value } }.should.raise(TypeError, "42 is not a symbol nor a string")
    end

    it "is Ractor-local" do
      Ractor.store_if_absent(@key) { :main }
      r = Ractor.new(@key) { |key| Ractor.store_if_absent(key) { :ractor } }
      r.value.should == :ractor
      Ractor.current[@key].should == :main
    end
  end
end
