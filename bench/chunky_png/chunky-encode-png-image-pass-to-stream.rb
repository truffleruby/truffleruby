# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

$LOAD_PATH << File.join(File.dirname(__FILE__), 'chunky_png', 'lib')

if ENV['USE_CEXTS']
  $LOAD_PATH << File.join(File.dirname(__FILE__), 'oily_png', 'lib')
  $LOAD_PATH << File.join(File.dirname(__FILE__), 'oily_png', 'ext')
  require 'oily_png'
else
  require 'chunky_png'
end

WIDTH = 2000
HEIGHT = 2000
COLOR_MODE = ChunkyPNG::COLOR_TRUECOLOR_ALPHA
DEPTH = 8
PIXEL = 0x12345678

PIXELS = [PIXEL] * WIDTH * HEIGHT

class MockCanvas
  
  if ENV['USE_CEXTS']
    include OilyPNG::PNGEncoding
  else
    include ChunkyPNG::Canvas::PNGEncoding
  end

  public :encode_png_image_pass_to_stream

  def initialize
    @pixels = PIXELS
  end

  def width
    WIDTH
  end

  def height
    HEIGHT
  end

  def pixels
    @pixels
  end

  def row(y)
    pixels.slice(y * width, width)
  end
end

canvas = MockCanvas.new

benchmark do
  canvas.encode_png_image_pass_to_stream('', COLOR_MODE, DEPTH, 0)
end
