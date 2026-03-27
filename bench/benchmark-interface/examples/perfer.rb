# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'perfer'

require File.expand_path('clamp', File.dirname(__FILE__))

Perfer.session 'clamp' do |s|
  s.iterate 'clamp_a' do |n|
    n.times do
      clamp_a(10, 40, 90)
    end
  end

  s.iterate 'clamp_b' do |n|
    n.times do
      clamp_b(10, 40, 90)
    end
  end
end
