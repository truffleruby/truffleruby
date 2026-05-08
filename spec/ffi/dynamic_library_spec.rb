#
# This file is part of ruby-ffi.
# For licensing, see LICENSE.SPECS
#

require File.expand_path(File.join(File.dirname(__FILE__), "spec_helper"))

describe FFI::DynamicLibrary do
  it "should be shareable for Ractor", :ractor do
    libtest = FFI::DynamicLibrary.open(TestLibrary::PATH,
        FFI::DynamicLibrary::RTLD_LAZY | FFI::DynamicLibrary::RTLD_GLOBAL)

    Ractor.make_shareable(libtest)
    res = Ractor.new(libtest) do |libtest2|
      libtest2.find_symbol("testClosureVrV").address
    end.value

    expect( res ).to be > 0
  end

  it "load a library in a Ractor", :ractor do
    res = Ractor.new do
      libtest = FFI::DynamicLibrary.open(TestLibrary::PATH,
          FFI::DynamicLibrary::RTLD_LAZY | FFI::DynamicLibrary::RTLD_GLOBAL)
      libtest.find_symbol("testClosureVrV")
    end.value

    expect(res.address).to be > 0
  end

  it "has a memsize function", skip: RUBY_ENGINE != "ruby" do
    base_size = ObjectSpace.memsize_of(Object.new)

    libtest = FFI::DynamicLibrary.open(TestLibrary::PATH,
        FFI::DynamicLibrary::RTLD_LAZY | FFI::DynamicLibrary::RTLD_GLOBAL)
    size = ObjectSpace.memsize_of(libtest)
    expect(size).to be > base_size
  end

  describe Symbol do
    before do
      @libtest = FFI::DynamicLibrary.open(
        TestLibrary::PATH,
        FFI::DynamicLibrary::RTLD_LAZY | FFI::DynamicLibrary::RTLD_GLOBAL,
      )
    end

    it "has a memsize function", skip: RUBY_ENGINE != "ruby" do
      base_size = ObjectSpace.memsize_of(Object.new)

      symbol = @libtest.find_symbol("gvar_gstruct_set")
      size = ObjectSpace.memsize_of(symbol)
      expect(size).to be > base_size
    end

    it "should be shareable for Ractor", :ractor do
      symbol = @libtest.find_symbol("gvar_gstruct_set")
      Ractor.make_shareable(symbol)
      expect(Ractor.shareable?(symbol)).to be true
    end
  end
end
