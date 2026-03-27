# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

$LOAD_PATH << File.join(File.dirname(__FILE__))
$LOAD_PATH << File.join(File.dirname(__FILE__), 'psd.rb', 'lib')

require 'mock-logger'

require 'psd/color'
require 'psd/util'
require 'psd/image_formats/layer_raw'

SIZE = 500_000

BYTE = "\x12"
BYTES = BYTE * SIZE

class MockFile
  def read(n)
    if n == 1
      BYTE
    else
      BYTES
    end
  end
end

class MockImage
  include PSD::ImageFormat::LayerRAW

  public :parse_raw!

  def initialize
    @ch_info = {:length => SIZE}
    @chan_pos = 0
    @file = MockFile.new
    @channel_data = [0]
  end

  def channel_data
    @channel_data
  end
  
  def reset
    @chan_pos = 0
  end
end

image = MockImage.new

benchmark do
  image.parse_raw!
  image.reset
end
