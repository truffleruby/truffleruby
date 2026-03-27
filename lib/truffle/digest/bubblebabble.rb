# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'digest'

module Digest

  def self.bubblebabble(message)
    Truffle::Digest.bubblebabble(Primitive.convert_with_to_str(message))
  end

  class Class

    def self.bubblebabble(message)
      digest = new
      digest.update message
      digest.bubblebabble
    end

    def bubblebabble(message = NO_MESSAGE)
      Digest.bubblebabble(digest(message))
    end

  end

end
