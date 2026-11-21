class BoxSpecA
  VERSION = "1.0"

  module Nested
    VALUE = 42
  end

  def self.version
    VERSION
  end

  def yay
    "yay #{VERSION}"
  end
end
