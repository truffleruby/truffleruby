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
$LOAD_PATH << File.join(File.dirname(__FILE__), '..', 'chunky_png', 'chunky_png', 'lib')

require 'mock-logger'

require 'chunky_png/color'
require 'psd/color'
require 'psd/util'
require 'psd/image_modes/cmyk'

WIDTH = 1000
HEIGHT = 1000

CHANNEL_DATA = [128] * WIDTH * HEIGHT * 5

class MockImage
  include PSD::ImageMode::CMYK

  public :combine_cmyk_channel

  def initialize
    @num_pixels = WIDTH * HEIGHT
    @channels_info = [{id: 0}, {id: 1}, {id: 2}, {id: 3}, {id: -1}]
    @channel_length = @num_pixels
    @channel_data = CHANNEL_DATA
    @pixel_data = []
  end

  def pixel_step
    1
  end

  def pixel_data
    @pixel_data
  end
  
  def reset
    @pixel_data.clear
  end
end

image = MockImage.new

benchmark do
  image.combine_cmyk_channel
  image.reset
end
