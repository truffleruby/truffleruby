# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Refinement < Module

  undef_method :extend_object

  def import_methods(*modules)
    modules.each do |mod|
      if Primitive.class(mod) != Module
        raise TypeError, "wrong argument type #{Primitive.class(mod)} (expected Module)"
      end
    end

    modules.each do |mod|
      if mod.ancestors.length > 1
        warn("#{mod} has ancestors, but Refinement#import_methods doesn't import their methods", uplevel: 1)
      end
      Primitive.refinement_import_methods(self, mod)
    end
    self
  end

end
