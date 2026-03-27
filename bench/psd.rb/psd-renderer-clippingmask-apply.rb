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

require 'psd/renderer/clipping_mask'
require 'chunky_png/color'
require 'psd/color'
require 'psd/util'

SIZE = 2_000
PIXEL = 0x11223344

class MockNode
  def clipped?
    true
  end

  def name
    "name"
  end
end

class MockCanvas
  def initialize
    @pixels = [PIXEL] * SIZE * SIZE
  end

  def top
    0
  end

  def left
    0
  end

  def width
    SIZE
  end

  def height
    SIZE
  end

  def pixels
    @pixels
  end

  def [](x, y)
    @pixels[y * SIZE + x]
  end

  def []=(x, y, pixel)
    @pixels[y * SIZE + x] = pixel
  end

  def canvas
    self
  end
end

class MockMask
  def initialize
    @node = MockNode.new
    @canvas = MockCanvas.new
  end

  def top
    0
  end

  def left
    0
  end

  def width
    SIZE
  end

  def height
    SIZE
  end

  attr_reader :node
  attr_reader :canvas
end

class MockClippingMask < PSD::Renderer::ClippingMask
  public :apply!

  def initialize
    @node = MockNode.new
    @canvas = MockCanvas.new
    @mask = MockMask.new
  end

  attr_reader :node
  attr_reader :canvas
  attr_reader :mask
end

mask = MockClippingMask.new

benchmark do
  mask.apply!
end
