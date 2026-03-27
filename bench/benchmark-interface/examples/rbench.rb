# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'rbench'

require File.expand_path('clamp', File.dirname(__FILE__))

RBench.run(10_000) do
  format :width => 65

  group 'clamp1' do
    report 'clamp_a1' do
      clamp_a(10, 40, 90)
    end
    report 'clamp_b1' do
      clamp_a(10, 40, 90)
    end
  end
end

RBench.run(10_000) do
  format :width => 65

  column :times
  column :one,  :title => "#1"
  column :two,  :title => "#2"
  column :diff, :title => "#1/#2", :compare => [:one,:two]

  group 'clamp2' do
    report 'clamp_a2' do
      one { clamp_a(10, 40, 90) }
      two { clamp_a(10, 40, 90) }
    end
    report 'clamp_b2' do
      one { clamp_a(10, 40, 90) }
      two { clamp_a(10, 40, 90) }
    end
    
    summary 'all methods (totals)'
  end
end
