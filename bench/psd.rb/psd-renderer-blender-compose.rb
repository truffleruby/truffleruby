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
require 'psd/renderer/compose'
require 'psd/renderer/blender'

# Constant lookup not quite right in blender.rb - this fixes it
Compose = PSD::Compose

SIZE = 1_000
PIXEL = 0x11223344

class MockNode
  def blending_mode
    "normal"
  end

  def debug_name
    "name"
  end
end

class MockCanvas
  def initialize
    @pixels = [PIXEL] * SIZE * SIZE
    @node = MockNode.new
  end

  def top
    SIZE
  end

  def left
    SIZE
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

  attr_reader :node
end

class MockBlender < PSD::Renderer::Blender
  public :compose!
  public :compose_options

  def initialize
    @fg = MockCanvas.new
    @bg = MockCanvas.new
    @opacity = 128
    @fill_opacity = 128
  end
end

blender = MockBlender.new

benchmark do
  blender.compose!
end
