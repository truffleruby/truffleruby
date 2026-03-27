# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class MockLogger
  def debug(message)
  end

  def info(message)
  end
end

MOCK_LOGGER = MockLogger.new

class PSD
  def self.logger
    MOCK_LOGGER
  end
end
