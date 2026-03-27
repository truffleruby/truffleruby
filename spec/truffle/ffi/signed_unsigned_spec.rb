# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# From https://github.com/ffi/ffi/blob/master/spec/ffi/struct_spec.rb

# Copyright (c) 2008-2014 Ruby-FFI contributors

# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following
# conditions:

# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
# OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
# WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.

require 'ffi'

describe "FFI" do
  def int_field_test(type, values)
    klass = Class.new(FFI::Struct)
    klass.layout :value, type, :dummy, :long

    values.each do |value|
      it "#{type} field r/w #{value.to_s(16)}" do
        s = klass.new
        ptr = s.pointer

        # Write with s[:value] =
        s[:value] = value
        s[:dummy].should == 0

        ptr.send("read_#{type}").should == value
        ptr.send("get_#{type}", 0).should == value
        s[:value].should == value

        ptr.send("put_#{type}", 0, 0)
        s[:value].should == 0

        # Write with write_#{type}
        ptr.send("write_#{type}", value)
        s[:dummy].should == 0

        ptr.send("read_#{type}").should == value
        ptr.send("get_#{type}", 0).should == value
        s[:value].should == value

        ptr.send("write_#{type}", 0)
        s[:value].should == 0
      end
    end
  end

  int_field_test(:char, [ 0, 127, -128, -1 ])
  int_field_test(:uchar, [ 0, 0x7f, 0x80, 0xff ])
  int_field_test(:short, [ 0, 0x7fff, -0x8000, -1 ])
  int_field_test(:ushort, [ 0, 0x7fff, 0x8000, 0xffff ])
  int_field_test(:int, [ 0, 0x7fffffff, -0x80000000, -1 ])
  int_field_test(:uint, [ 0, 0x7fffffff, 0x80000000, 0xffffffff ])
  int_field_test(:long, [ 0, 0x7fffffffffffffff, -0x8000000000000000, -1 ])
  int_field_test(:ulong, [ 0, 0x7fffffffffffffff, 0x8000000000000000, 0xffffffffffffffff ])
  int_field_test(:long_long, [ 0, 0x7fffffffffffffff, -0x8000000000000000, -1 ])
  int_field_test(:ulong_long, [ 0, 0x7fffffffffffffff, 0x8000000000000000, 0xffffffffffffffff ])
end
