# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

str1 = 'white chocolate'
str2 = 'a1'
str3 = 'dog'

regex2 = /\d/
regex3 = /\w+/

benchmark 'core-string-gsub-string' do
  str1.gsub('white', 'dark')
end

benchmark "core-string-gsub-regex" do
  str2.gsub(regex2, '2')
end

benchmark "core-string-gsub-regex-block" do
  str3.gsub(regex3) { |animal| animal == 'dog' ? 'cat' : 'dog' }
end
