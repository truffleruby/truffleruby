# Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "#inspect and #to_s on a foreign" do
  guard -> { !TruffleRuby.native? } do
    describe "Java null" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Debug.java_null
        foreign.inspect.should == "#<Polyglot::ForeignNull[Java] null>"
        foreign.to_s.should == "#<Polyglot::ForeignNull[Java] null>"
      end
    end

    describe "Java list" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.to_java_list([1, 2, 3])
        foreign.inspect.should =~ /\A#<Polyglot::ForeignArray\[Java\] java\.util\.Arrays\$ArrayList:0x\h+ \[1, 2, 3\]>\z/
        foreign.to_s.should == "#<Polyglot::ForeignArray[Java] [1, 2, 3]>"
      end
    end

    describe "Java array" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.to_java_array([1, 2, 3])
        foreign.inspect.should =~ /\A#<Polyglot::ForeignArray\[Java\] int\[\]:0x\h+ \[1, 2, 3\]>\z/
        foreign.to_s.should == "#<Polyglot::ForeignArray[Java] [1, 2, 3]>"
      end
    end

    describe "Java map" do
      it "gives a similar representation to Ruby" do
        hash = {a: 1, b: 2, c: 3}
        foreign = Truffle::Interop.to_java_map(hash)

        foreign.inspect.should =~ /\A#<Polyglot::ForeignHash\[Java\] java\.util\.HashMap:0x\h+ {"a" => 1, "b" => 2, "c" => 3}>\z/
        foreign.to_s.should == "#<Polyglot::ForeignHash[Java] {a=1, b=2, c=3}>"
      end
    end

    describe "Java exception" do
      it "gives a similar representation to Ruby" do
        integer_class = Truffle::Interop.java_type("java.lang.Integer")
        -> {
          integer_class.valueOf("abc")
        }.should raise_error(Polyglot::ForeignException) { |exc|
          exc.inspect.should =~ /\A#<Polyglot::ForeignException\[Java\] java\.lang\.NumberFormatException:0x\h+: For input string: "abc">\z/
          exc.to_s.should == '#<Polyglot::ForeignException[Java] java.lang.NumberFormatException: For input string: "abc">'
        }
      end
    end

    describe "Java type" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.java_type("java.math.BigInteger")
        foreign.inspect.should == "#<Polyglot::ForeignClass[Java] type java.math.BigInteger>"
        foreign.to_s.should == "#<Polyglot::ForeignClass[Java] type java.math.BigInteger>"

        foreign = Truffle::Interop.java_type("int")
        foreign.inspect.should == "#<Polyglot::ForeignMetaObject[Java] type int>"
        foreign.to_s.should == "#<Polyglot::ForeignMetaObject[Java] type int>"
      end
    end

    describe "Java java.lang.Class instance" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.java_type("java.math.BigInteger")[:class]
        foreign.inspect.should =~ /\A#<Polyglot::ForeignClass\[Java\] java\.lang\.Class:0x\h+ java\.math\.BigInteger static={\.\.\.}>\z/
        foreign.to_s.should == "#<Polyglot::ForeignClass[Java] java.math.BigInteger>"
      end
    end

    describe "Java object" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.java_type("java.lang.Object").new
        foreign.inspect.should =~ /\A#<Polyglot::ForeignObject\[Java\] java\.lang\.Object:0x\h+>\z/
        foreign.to_s.should =~ /\A#<Polyglot::ForeignObject\[Java\] java\.lang\.Object@\h+>\z/
      end
    end

    describe "Java BigInteger" do
      it "gives a similar representation to Ruby" do
        foreign = Truffle::Interop.java_type("java.math.BigInteger").new('14')
        foreign.inspect.should =~ /\A#<Polyglot::ForeignNumber\[Java\] java\.math\.BigInteger 14>\z/
        foreign.to_s.should =~ /\A#<Polyglot::ForeignNumber\[Java\] 14>\z/
      end
    end
  end

  describe "recursive array" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_array
      foreign[0] = foreign
      foreign.inspect.should =~ /\A#<Polyglot::ForeignArray:0x\h+ \[\[...\], 2, 3\]>\z/
      foreign.to_s.should == "#<Polyglot::ForeignArray [foreign array]>"
    end
  end

  describe "null" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_null
      foreign.inspect.should == "#<Polyglot::ForeignNull null>"
      foreign.to_s.should == "#<Polyglot::ForeignNull [foreign null]>"
    end
  end

  describe "number" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_boxed_value(42)
      foreign.inspect.should == "#<Polyglot::ForeignNumber 42>"
      foreign.to_s.should == "#<Polyglot::ForeignNumber 42>"

      foreign = Truffle::Debug.foreign_boxed_value(1 << 84)
      foreign.inspect.should == "#<Polyglot::ForeignNumber[Ruby] Integer 19342813113834066795298816>"
      foreign.to_s.should == "#<Polyglot::ForeignNumber[Ruby] 19342813113834066795298816>"

      foreign = Truffle::Debug.foreign_boxed_value(3.14)
      foreign.inspect.should == "#<Polyglot::ForeignNumber 3.14>"
      foreign.to_s.should == "#<Polyglot::ForeignNumber 3.14>"
    end
  end

  describe "executable" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_executable(14)
      foreign.inspect.should =~ /\A#<Polyglot::ForeignExecutable:0x\h+ proc>\z/
      foreign.to_s.should == "#<Polyglot::ForeignExecutable [foreign executable]>"
    end
  end

  describe "pointer" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_pointer(0x1234)
      foreign.inspect.should == "#<Polyglot::ForeignPointer 0x1234>"
      foreign.to_s.should == "#<Polyglot::ForeignPointer [foreign pointer]>"
    end
  end

  describe "array" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_array
      foreign.inspect.should =~ /\A#<Polyglot::ForeignArray:0x\h+ \[1, 2, 3\]>\z/
      foreign.to_s.should == "#<Polyglot::ForeignArray [foreign array]>"
    end

    it "gives a similar representation to Ruby, even if it is also a pointer" do
      foreign = Truffle::Debug.foreign_pointer_array
      foreign.inspect.should =~ /\A#<Polyglot::ForeignArrayPointer 0x0 \[1, 2, 3\]>\z/
      foreign.to_s.should == "#<Polyglot::ForeignArrayPointer [foreign pointer array]>"
    end
  end

  describe "hash" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_hash
      foreign.inspect.should =~ /\A#<Polyglot::ForeignHash:0x\h+ {:a => 1, :b => 2}>\z/
      foreign.to_s.should == "#<Polyglot::ForeignHash [foreign hash]>"
    end
  end

  describe "exception" do
    it "gives a similar representation to Ruby" do
      exc = Truffle::Debug.foreign_exception("foo")
      exc.inspect.should =~ /\A#<Polyglot::ForeignException:0x\h+: foo>\z/
      exc.to_s.should == '#<Polyglot::ForeignException [foreign exception]>'
    end
  end

  describe "object without members" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_object
      foreign.inspect.should =~ /\A#<Polyglot::ForeignObject:0x\h+>\z/
      foreign.to_s.should == "#<Polyglot::ForeignObject [foreign object]>"
    end
  end

  describe "object with members" do
    it "gives a similar representation to Ruby" do
      foreign = Truffle::Debug.foreign_object_with_members
      foreign.inspect.should =~ /\A#<Polyglot::ForeignObject:0x\h+ a=1, b=2, c=3>\z/
      foreign.to_s.should == "#<Polyglot::ForeignObject [foreign object with members]>"
    end
  end
end
