require_relative '../../ruby/spec_helper'

describe "String#encode with UTF-16 BOM" do
  # Reproduces issue where UTF-16 BOM is not removed when encoding to UTF-8
  # See: https://github.com/ruby/irb/issues/52

  it "removes auto-added BOM when encoding UTF-16 to UTF-8" do
    # When a method is defined with UTF-16 name Symbol#to_s returns UTF-16 string
    obj = Object.new
    obj.define_singleton_method("test".encode(Encoding::UTF_16)) {}

    method_name = obj.methods(false).first.to_s
    method_name.encoding.should == Encoding::UTF_16

    # UTF-16s auto-added BOM should be removed when encoding to UTF-8
    utf8_name = method_name.encode(Encoding::UTF_8)
    utf8_name.should == "test"
    utf8_name.bytes.should == [116, 101, 115, 116]
  end
end
