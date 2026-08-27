# truffleruby_primitives: true

require Primitive.get_original_require(__FILE__)

# nokogiri 1.11.2 defaults to --disable-static, so this is noop on recent nokogiri.
# This fixes a compilation issue on older macOS for older nokogiri releases (GR-30240).
# About 2x faster to build than without --disable-static for older nokogiri releases on macOS.
module Truffle::NokogiriDefaultBuildsArgs
  def initialize(*args)
    super

    if @spec.name == 'nokogiri' and @build_args.empty?
      @build_args = ['--disable-static']
    end
  end
end

class Gem::Ext::Builder
  prepend Truffle::NokogiriDefaultBuildsArgs
end
