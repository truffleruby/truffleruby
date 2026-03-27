# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'

# All the directive specs are located in core/string/unpack/
describe "String#unpack" do
  it "should be able to unpack a tarball entry" do
    header = "metadata.gz0000444000000000000000000000426712721375272013457 0ustar00wheelwheel00000000000000"
    format = 'A100' + # name
                      'A8'   + # mode
                      'A8'   + # uid
                      'A8'   + # gid
                      'A12'  + # size
                      'A12'  + # mtime
                      'A8'   + # checksum
                      'A'    + # typeflag
                      'A100' + # linkname
                      'A6'   + # magic
                      'A2'   + # version
                      'A32'  + # uname
                      'A32'  + # gname
                      'A8'   + # devmajor
                      'A8'   + # devminor
                      'A155'   # prefix
    fields = header.unpack(format)
    fields.should == ["metadata.gz0000444000000000000000000000426712721375272013457 0ustar00wheelwheel00000000000000", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""]
  end
end
