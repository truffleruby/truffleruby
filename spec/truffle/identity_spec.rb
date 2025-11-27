# Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

require 'rubygems'

describe "Identifying features such as" do

  it "RUBY_ENGINE is 'truffleruby'" do
    RUBY_ENGINE.should == 'truffleruby'
  end

  it 'RUBY_VERSION matches /\A\d+\.\d+\.\d+\z/' do
    RUBY_VERSION.should =~ /\A\d+\.\d+\.\d+\z/
  end

  it 'RUBY_ENGINE_VERSION matches /\A\d\d\.\d+.\d+(-dev-\h+)?\z/' do
    RUBY_ENGINE_VERSION.should =~ /\A\d\d\.\d+.\d+(-dev-\h+)?\z/
  end

  it 'RUBY_ENGINE_VERSION starts with the first 2 components of RUBY_VERSION concatenated' do
    a, b, * = RUBY_VERSION.split('.')
    RUBY_ENGINE_VERSION.should.start_with?("#{a}#{b}.")
  end

  it "RUBY_ENGINE_VERSION can be parsed as a Gem::Version" do
    Gem::Version.new(RUBY_ENGINE_VERSION).should be_kind_of(Gem::Version)
  end

  it "RUBY_PATCHLEVEL is 0" do
    RUBY_PATCHLEVEL.should == 0
  end

  it "RUBY_RELEASE_DATE is the date of the truffleruby commit used to build" do
    RUBY_RELEASE_DATE.should =~ /\A\d{4}-\d{2}-\d{2}\z/
  end

  guard -> { !TruffleRuby.native? } do
    it "RUBY_DESCRIPTION indicates TruffleRuby runs on JVM and which edition" do
      RUBY_DESCRIPTION.should =~ /\b(Interpreted|GraalVM CE|Oracle GraalVM) JVM\b/
      RUBY_DESCRIPTION.should_not include("Native")
      RUBY_DESCRIPTION.should_not include("native")
    end
  end

  guard -> { TruffleRuby.native? } do
    it "RUBY_DESCRIPTION indicates TruffleRuby runs on SVM and which edition" do
      RUBY_DESCRIPTION.should =~ /\b(GraalVM CE|Oracle GraalVM) Native\b/
      RUBY_DESCRIPTION.should_not include("JVM")
    end
  end

  it "Gem.ruby_engine matches RUBY_ENGINE" do
    Gem.ruby_engine.should == RUBY_ENGINE
  end

  it "Gem.ruby_version matches RUBY_VERSION as a Gem version object" do
    Gem.ruby_version.should == Gem::Version.new(RUBY_VERSION)
  end

  it "RbConfig::CONFIG['ruby_install_name'] is 'truffleruby'" do
    RbConfig::CONFIG['ruby_install_name'].should == 'truffleruby'
  end

  it "RbConfig::CONFIG['RUBY_INSTALL_NAME'] is 'truffleruby'" do
    RbConfig::CONFIG['RUBY_INSTALL_NAME'].should == 'truffleruby'
  end

  it "RbConfig::CONFIG['ruby_version'] is the ABI version and starts with RUBY_VERSION and has at least an extra component" do
    RbConfig::CONFIG['ruby_version'].should =~ /\A#{Regexp.escape RUBY_VERSION}\.\d+(\.\d+)*\z/
  end

  it "the ABI version is always different than RUBY_VERSION and RUBY_ENGINE_VERSION" do
    RbConfig::CONFIG['ruby_version'].should_not == RUBY_VERSION
    RbConfig::CONFIG['ruby_version'].should_not == RUBY_ENGINE_VERSION
    RUBY_VERSION.should_not == RUBY_ENGINE_VERSION # See lib/cext/include/truffleruby/truffleruby-abi-version.h
  end

  it "RbConfig::CONFIG['RUBY_BASE_NAME'] is 'ruby'" do
    RbConfig::CONFIG['RUBY_BASE_NAME'].should == 'ruby'
  end

  it 'TruffleRuby.revision matches /\A\h+\z/' do
    TruffleRuby.revision.should =~ /\A\h+\z/
  end

  describe "the Rubinius module" do

    it "is not defined" do
      defined?(Rubinius).should be_nil
    end

  end

  describe "the RubySL module" do

    before :all do
      # Require something that uses code from RubySL
      require 'stringio'
    end

    it "is not defined" do
      defined?(RubySL).should be_nil
    end

  end

end
