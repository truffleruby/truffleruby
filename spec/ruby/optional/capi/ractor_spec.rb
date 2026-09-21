require_relative 'spec_helper'

load_extension("ractor")

describe "C-API ractor local storage function" do
  before :each do
    @f = CApiRactorSpecs.new
  end

  describe "rb_ractor_local_storage_value_set" do
    it "stores and retrieves a value" do
      @f.ractor_local_storage_value_set(:hello).should == :hello
      @f.ractor_local_storage_value.should == :hello
    end

    it "replaces a previously stored value" do
      @f.ractor_local_storage_value_set(:first)
      @f.ractor_local_storage_value_set(:second)
      @f.ractor_local_storage_value.should == :second
    end

    it "keeps the stored value alive across GC" do
      object = Object.new
      @f.ractor_local_storage_value_set(object)
      GC.start
      @f.ractor_local_storage_value.should equal(object)
    end
  end

  describe "rb_ractor_local_storage_value" do
    it "returns nil when no value is stored" do
      @f.fresh_ractor_local_storage_value.should == nil
    end
  end

  describe "rb_ractor_local_storage_value_lookup" do
    it "returns the stored value when set" do
      @f.ractor_local_storage_value_set(42)
      @f.ractor_local_storage_value_lookup.should == 42
    end

    it "returns nil when no value is stored" do
      @f.fresh_ractor_local_storage_value_lookup.should == nil
    end
  end
end
