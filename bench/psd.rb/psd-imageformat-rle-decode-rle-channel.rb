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

require 'psd/image_formats/rle'
require 'psd/color'
require 'psd/util'

SIZE = 1000
BYTES = ["\x04", "\x12", "\x34", "\x56", "\x78", "\x90", "\xFB", "\x11"]
BYTE_SEQUENCE = "\x12\x34\x56\x78\x90"

class MockFile
  def initialize
    @position = 0
  end

  def tell
    @position
  end

  def read(n)
    if n == 1
      value = BYTES[@position % BYTES.size]
      @position += 1
      value
    else
      @position += n
      BYTE_SEQUENCE
    end
  end
end

class MockImage
  include PSD::ImageFormat::RLE

  public :decode_rle_channel

  def initialize
    @byte_counts = [SIZE] * height
    @line_index = 0
    @chan_pos = 0
    @file = MockFile.new
    @channel_data = [0]
  end

  def height
    SIZE
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
  image.decode_rle_channel
  image.reset
end
