# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "key_info" do

  def key_info(object, index)
    if Integer === index
      [*(:readable if Truffle::Interop.array_element_readable?(object, index)),
       *(:modifiable if Truffle::Interop.array_element_modifiable?(object, index)),
       *(:insertable if Truffle::Interop.array_element_insertable?(object, index)),
       *(:removable if Truffle::Interop.array_element_removable?(object, index))]
    else
      [*(:readable if Truffle::Interop.member_readable?(object, index)),
       *(:modifiable if Truffle::Interop.member_modifiable?(object, index)),
       *(:insertable if Truffle::Interop.member_insertable?(object, index)),
       *(:removable if Truffle::Interop.member_removable?(object, index)),
       *(:internal if Truffle::Interop.member_internal?(object, index)),
       *(:invocable if Truffle::Interop.member_invocable?(object, index))]
    end
  end

  describe "for an Array" do

    before :each do
      @array = [1, 2, 3]
    end

    describe "has READABLE" do

      describe "set" do

        it "for an integer in bounds" do
          key_info(@array, 1).should include(:readable)
        end

        it "for instance variables that exist" do
          @array.instance_variable_set :@foo, 14
          key_info(@array, :@foo).should include(:readable)
        end

        it "for a method" do
          key_info(@array, :to_s).should include(:readable)
        end
      end

      describe "not set" do

        it "for an integer out of bounds" do
          key_info(@array, 100).should_not include(:readable)
        end

        it "for instance variables that don't exist" do
          key_info(@array, :@foo).should_not include(:readable)
        end

        it "for something other than an integer" do
          key_info(@array, :foo).should_not include(:readable)
        end

      end

    end

    describe "has INSERTABLE and/or MODIFIABLE" do

      describe "set" do

        it "for an integer in bounds" do
          key_info(@array, 1).should include(:modifiable)
        end

        it "for instance variables that exist" do
          @array.instance_variable_set :@foo, 14
          key_info(@array, :@foo).should include(:modifiable)
        end

        it "for instance variables that don't exist" do
          key_info(@array, :@foo).should include(:insertable)
        end

      end

      describe "not set" do

        it "for an integer in bounds if the array is frozen" do
          @array.freeze
          key_info(@array, 1).should_not include_any_of(:insertable, :modifiable)
        end

        it "for an integer out of bounds" do
          key_info(@array, 100).should_not include_any_of(:modifiable)
        end

        it "for something other than an integer" do
          key_info(@array, :foo).should_not include_any_of(:insertable, :modifiable)
        end

        it "for a method" do
          key_info(@array, :to_s).should_not include_any_of(:insertable, :modifiable)
        end

      end

    end

    it "for instance variables that exist" do
      @array.instance_variable_set :@foo, 14
      key_info(@array, :@foo).should include_any_of(:removable, :internal)
      key_info(@array, :@foo).should_not include_any_of(:invocable)
    end

    describe "not set" do

      it "for an integer in bounds" do
        key_info(@array, 1).should_not include_any_of(:invocable, :internal)
      end

      it "for an integer in bounds if the array is frozen" do
        @array.freeze
        key_info(@array, 1).should_not include_any_of(:removable, :invocable, :internal)
      end

      it "for an integer out of bounds" do
        key_info(@array, 100).should_not include_any_of(:invocable, :internal)
      end

      it "for something other than an integer" do
        key_info(@array, :foo).should_not include_any_of(:removable, :invocable, :internal)
      end

      it "for instance variables that don't exist" do
        key_info(@array, :@foo).should_not include_any_of(:removable, :invocable)
      end

      it "for a method" do
        key_info(@array, :to_s).should_not include_any_of(:removable, :modifiable, :internal)
      end

    end

  end

  describe "for a Hash" do

    before :each do
      @hash = {'a' => 1, 'b' => 2, 'c' => 3}
    end

    describe "has READABLE" do

      describe "set" do

        it "for a method" do
          key_info(@hash, :to_s).should include(:readable)
        end

        it "for instance variables that exist" do
          @hash.instance_variable_set :@foo, 14
          key_info(@hash, :@foo).should include(:readable)
        end
      end

      describe "not set" do
        it "if the key is not found" do
          key_info(@hash, 'b').should_not include(:readable)
        end

        it "if the key is not found" do
          key_info(@hash, 'foo').should_not include(:readable)
        end

        it "for instance variables that don't exist" do
          key_info(@hash, :@foo).should_not include(:readable)
        end
      end

    end

    describe "has INSERTABLE" do

      describe "set" do

        it "for instance variables that don't exist" do
          key_info(@hash, :@foo).should include(:insertable)
        end
      end

      describe "not set" do

        it "for instance variables that exist" do
          @hash.instance_variable_set :@foo, 14
          # since the key is not there
          key_info(@hash, :@foo).should_not include(:insertable)
        end

        it "if the key is not found" do
          key_info(@hash, 'foo').should_not include(:insertable)
        end

        it "if the key is found and the hash is frozen" do
          @hash.freeze
          key_info(@hash, 'b').should_not include(:insertable)
        end

        it "if the key is found" do
          key_info(@hash, 'b').should_not include(:insertable)
        end

        it "for a method" do
          key_info(@hash, :to_s).should_not include(:insertable)
        end

      end

    end

    describe "has MODIFIABLE and REMOVABLE" do

      describe "set" do

        it "for instance variables that exist" do
          @hash.instance_variable_set :@foo, 14
          key_info(@hash, :@foo).should include_any_of(:modifiable, :removable)
        end
      end

      describe "not set" do

        it "if the key not found" do
          key_info(@hash, 'b').should_not include(:modifiable, :removable)
        end

        it "if the key is found and the hash is frozen" do
          @hash.freeze
          key_info(@hash, 'b').should_not include_any_of(:modifiable, :removable)
        end

        it "if the key is not found" do
          key_info(@hash, 'foo').should_not include_any_of(:modifiable)
        end

        it "for instance variables that don't exist" do
          key_info(@hash, :@foo).should_not include_any_of(:modifiable, :removable)
        end

        it "for a method" do
          key_info(@hash, :to_s).should_not include_any_of(:modifiable, :removable)
        end

      end

    end

    describe "not set" do

      it "if the key is found" do
        key_info(@hash, 'b').should_not include_any_of(:invocable, :internal)
      end

      it "if the key is found and the hash is frozen" do
        @hash.freeze
        key_info(@hash, 'b').should_not include_any_of(:invocable, :internal)
      end

      it "if the key is not found" do
        key_info(@hash, 'foo').should_not include_any_of(:invocable, :internal)
      end

      it "for instance variables that exist" do
        @array.instance_variable_set :@foo, 14
        key_info(@array, :@foo).should_not include_any_of(:invocable)
      end

      it "for instance variables that don't exist" do
        key_info(@array, :@foo).should_not include_any_of(:invocable)
      end

      it "for a method" do
        key_info(@array, :to_s).should_not include_any_of(:internal)
      end

    end

  end

  describe "for an object without an #[] method" do

    describe "with a name that starts with an @" do

      before :each do
        @object = TruffleInteropSpecs::InteropKeysClass.new
      end

      describe "has READABLE" do

        describe "set" do

          it "if the variable exists" do
            key_info(@object, :@a).should include(:readable)
          end

        end

        describe "not set" do
          it "if the variable does not exist" do
            key_info(@object, :@foo).should_not include(:readable)
          end
        end

      end

      describe "has INSERTABLE" do

        describe "set" do

          it "if the object is not frozen" do
            key_info(@object, :@new).should include(:insertable)
          end

        end

        describe "not set" do

          it "if the object is frozen" do
            @object.freeze
            key_info(@object, :@new).should_not include(:insertable)
          end

        end

      end

      describe "has MODIFIABLE and REMOVABLE" do

        describe "set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should include(:modifiable, :removable)
          end

        end

        describe "not set" do

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should_not include_any_of(:modifiable, :removable)
          end

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include_any_of(:modifiable, :removable)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include_any_of(:modifiable, :removable)
          end

        end

      end

      describe "has INVOCABLE" do

        describe "not set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should_not include(:invocable)
          end

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should_not include(:invocable)
          end

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include(:invocable)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include(:invocable)
          end

        end

      end

      describe "has INTERNAL" do

        describe "not set" do

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include(:internal)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include(:internal)
          end
        end

        describe "set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should include(:internal)
          end

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should include(:internal)
          end

        end

      end

    end

    describe "with a name that doesn't start with an @" do

      describe "has READABLE" do

        describe "set" do

          it "if the object has a method of that name" do
            @object = TruffleInteropSpecs::ReadHasMethod.new
            key_info(@object, :foo).should include(:readable)
          end

        end

        describe "not set" do

          it "if the object does not have a method of that name" do
            @object = Object.new
            key_info(@object, :foo).should_not include(:readable)
          end

        end

      end

      describe "has INSERTABLE and MODIFIABLE" do

        describe "not set" do

          it "if the object has a method of that name" do
            @object = TruffleInteropSpecs::WriteHasMethod.new
            key_info(@object, :foo).should_not include_any_of(:insertable, :modifiable)
          end

          it "if the object does not have a method of that name" do
            @object = Object.new
            key_info(@object, :foo).should_not include_any_of(:insertable, :modifiable)
          end

        end

      end

      describe "has REMOVABLE and INTERNAL" do

        describe "not set" do

          it "if the object has a method of that name" do
            @object = TruffleInteropSpecs::WriteHasMethod.new
            key_info(@object, :foo).should_not include_any_of(:removable, :internal)
          end

          it "if the object does not have a method of that name" do
            @object = Object.new
            key_info(@object, :foo).should_not include_any_of(:removable, :internal)
          end

        end

      end

      describe "has INVOCABLE" do

        describe "set" do

          it "if the object has a method of that name" do
            @object = TruffleInteropSpecs::ReadHasMethod.new
            key_info(@object, :foo).should include(:invocable)
          end

        end

        describe "not set" do

          it "if the object does not have a method of that name" do
            @object = Object.new
            key_info(@object, :foo).should_not include(:invocable)
          end

        end

      end

    end

  end

  describe "for an object with an #[] method" do

    describe "with a name that starts with an @" do

      before :each do
        @object = TruffleInteropSpecs::InteropKeysIndexClass.new
      end

      describe "has READABLE" do

        describe "set" do

          it "if the variable exists" do
            key_info(@object, :@a).should include(:readable)
          end

        end

        describe "not set" do

          it "if the variable does not exist" do
            key_info(@object, :@foo).should_not include(:readable)
          end

        end

      end

      describe "has INSERTABLE" do

        describe "set" do

          it "if the object is not frozen" do
            key_info(@object, :@new).should include(:insertable)
          end

        end

        describe "not set" do

          it "if the object is frozen" do
            @object.freeze
            key_info(@object, :@new).should_not include(:insertable)
          end

        end

      end

      describe "has MODIFIABLE and REMOVABLE" do

        describe "set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should include(:modifiable, :removable)
          end

        end

        describe "not set" do

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should_not include_any_of(:modifiable, :removable)
          end

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include_any_of(:modifiable, :removable)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include_any_of(:modifiable, :removable)
          end

        end

      end

      describe "has INVOCABLE" do

        describe "not set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should_not include(:invocable)
          end

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should_not include(:invocable)
          end

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include(:invocable)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include(:invocable)
          end

        end

      end

      describe "has INTERNAL" do

        describe "set" do

          it "if the variable exists and the object is not frozen" do
            key_info(@object, :@a).should include(:internal)
          end

          it "if the variable exists and the object is frozen" do
            @object.freeze
            key_info(@object, :@a).should include(:internal)
          end

        end

        describe "not set" do

          it "if the variable does not exist and the object is not frozen" do
            key_info(@object, :@foo).should_not include(:internal)
          end

          it "if the variable does not exist and the object is frozen" do
            @object.freeze
            key_info(@object, :@foo).should_not include(:internal)

          end
        end

      end

    end

    describe "with a name that doesn't start with an @" do

      describe "has READABLE" do

        it "set" do
          @object = TruffleInteropSpecs::PolyglotMember.new
          Truffle::Interop.write_member @object, :foo, :val
          key_info(@object, :foo).should include(:readable)
        end

      end

      describe "has INSERTABLE and MODIFIABLE" do

        describe "set" do

          it "if the object has a index set method" do
            @object = TruffleInteropSpecs::PolyglotMember.new
            Truffle::Interop.write_member @object, :foo, :val
            key_info(@object, :foo).should include(:modifiable)
          end

        end

      end

      describe "has REMOVABLE and INTERNAL" do

        describe "not set" do

          it "if the object has a index set method" do
            @object = TruffleInteropSpecs::WriteHasIndexSetAndIndex.new
            key_info(@object, :foo).should_not include_any_of(:removable, :internal)
          end

        end

      end

      describe "has INVOCABLE" do

        it "not set" do
          @object = TruffleInteropSpecs::ReadHasIndex.new
          key_info(@object, :foo).should_not include(:invocable)
        end

      end

    end

  end


end
