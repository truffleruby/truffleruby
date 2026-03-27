# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

load_extension("truffleruby_symbol_id")

describe "TruffleRuby ID2SYM" do
  before :each do
    @s = CApiTruffleRubySymbolIDSpecs.new
  end

  it "returns the correct symbol for the given identifier" do
    @s.ID2SYM('idPLUS').should == :+
    @s.ID2SYM('-').should == :-
    @s.ID2SYM('idLTLT').should == :<<
    @s.ID2SYM('idEmptyP').should == :empty?
    @s.ID2SYM('idMesg').should == :mesg
    @s.ID2SYM('idBACKREF').should == :$~
  end

end

describe "TruffleRuby SYM2ID" do
  before :each do
    @s = CApiTruffleRubySymbolIDSpecs.new
  end

  it "returns the correct symbol for the given identifier" do
    @s.SYM2ID(:+, 'idPLUS').should == true
    @s.SYM2ID(:-, '-').should == true
    @s.SYM2ID(:<<, 'idLTLT').should == true
    @s.SYM2ID(:empty?, 'idEmptyP').should == true
    @s.SYM2ID(:mesg, 'idMesg').should == true
    @s.SYM2ID(:$~, 'idBACKREF').should == true
  end

end

describe "TruffleRuby ID2SYM/SYM2ID" do
  before :each do
    @s = CApiTruffleRubySymbolIDSpecs.new
  end

  it "returns the same id after ID2SYM then SYM2ID" do
    @s.ID2SYM_SYM2ID('idPLUS').should == true
    @s.ID2SYM_SYM2ID('-').should  == true
    @s.ID2SYM_SYM2ID('idLTLT').should  == true
    @s.ID2SYM_SYM2ID('idEmptyP').should  == true
    @s.ID2SYM_SYM2ID('idMesg').should  == true
    @s.ID2SYM_SYM2ID('idBACKREF').should  == true
  end

  it "returns the same symbol after SYM2ID then ID2SYM" do
    @s.SYM2ID_ID2SYM(:+).should == :+
    @s.SYM2ID_ID2SYM(:-).should  == :-
    @s.SYM2ID_ID2SYM(:<<).should  == :<<
    @s.SYM2ID_ID2SYM(:empty?).should  == :empty?
    @s.SYM2ID_ID2SYM(:mesg).should  == :mesg
    @s.SYM2ID_ID2SYM(:$~).should  == :$~
    @s.SYM2ID_ID2SYM(:user_symbol).should  == :user_symbol
  end

end
