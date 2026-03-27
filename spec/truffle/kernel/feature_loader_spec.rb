# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::FeatureLoader.features_index_add" do
  it "creates correct entries in @loaded_features_index" do
    current_index = Truffle::FeatureLoader.instance_variable_get(:@loaded_features_index)
    current_index_backup = current_index.dup
    current_index.clear

    Truffle::FeatureLoader.features_index_add('foo', 0)
    current_index['foo'][0].index.should == 0

    Truffle::FeatureLoader.features_index_add('foo', 1)
    current_index['foo'][1].index.should == 1
    current_index.clear

    Truffle::FeatureLoader.features_index_add('foo.rb', 0)
    Truffle::FeatureLoader.features_index_add('foo.rb', 1)
    current_index['foo'][0].index.should == 0
    current_index['foo'][1].index.should == 1
    current_index.clear

    Truffle::FeatureLoader.features_index_add('one/two/foo.rb', 0)
    ['foo', 'foo.rb', 'two/foo', 'two/foo.rb', 'one/two/foo.rb', 'one/two/foo'].each do |feature|
      current_index['foo'].any? do |fe|
        if fe.include?(Truffle::FeatureLoader::FeatureEntry.new(feature))
          fe.index.should == 0
          true
        end
      end.should == true
    end
    current_index.clear

    Truffle::FeatureLoader.features_index_add('/two/foo.rb', 0)
    ['foo', 'foo.rb', 'two/foo', 'two/foo.rb', '/two/foo.rb', '/two/foo'].each do |feature|
      current_index['foo'].any? do |fe|
        if fe.include?(Truffle::FeatureLoader::FeatureEntry.new(feature))
          fe.index.should == 0
          true
        end
      end.should == true
    end
    current_index.clear
  ensure
    Truffle::FeatureLoader.instance_variable_set(:@loaded_features_index, current_index_backup)
  end
end

describe "Truffle::FeatureLoader.feature_path_loaded?" do
  it "returns path for matching feature" do
    load_path = ["/path/ruby/lib/ruby/2.6.0"]
    loaded_feature = "/path/ruby/lib/ruby/2.6.0/benchmark.rb"
    feature = "benchmark"
    path = Truffle::FeatureLoader.feature_path_loaded?(loaded_feature, feature, load_path)
    path.should be_true

    feature = "benchmark.rb"
    path = Truffle::FeatureLoader.feature_path_loaded?(loaded_feature, feature, load_path)
    path.should be_true
  end

  it "returns false for missing features" do
    load_path = ["/path/ruby/lib/ruby/2.6.0"]
    path = Truffle::FeatureLoader.feature_path_loaded?("/path/ruby/lib/ruby/2.6.0/benchmark.rb", "missing", load_path)
    path.should be_false

    long_feature = "/path/ruby/lib/ruby/2.6.0/extra-path/benchmark.rb"
    path = Truffle::FeatureLoader.feature_path_loaded?("/path/ruby/lib/ruby/2.6.0/benchmark.rb", long_feature, load_path)
    path.should be_false
  end

  it "returns correct paths for non-rb paths" do
    load_path = ["/path/ruby/lib/ruby/2.6.0"]
    loaded_feature = "/path/ruby/lib/ruby/2.6.0/benchmark.so"

    feature = "benchmark"
    path = Truffle::FeatureLoader.feature_path_loaded?(loaded_feature, feature, load_path)
    path.should be_true

    feature = "benchmark.so"
    path = Truffle::FeatureLoader.feature_path_loaded?(loaded_feature, feature, load_path)
    path.should be_true

    feature = "benchmark.rb"
    path = Truffle::FeatureLoader.feature_path_loaded?(loaded_feature, feature, load_path)
    path.should be_false
  end

end

describe "Truffle::FeatureLoaderFeatureEntry" do
  it "only a nested path at the end should match" do
    hash = {}
    stored_entry = Truffle::FeatureLoader::FeatureEntry.new("path/to/feature")
    hash[stored_entry.base] = [stored_entry]

    short_lookup_entry = Truffle::FeatureLoader::FeatureEntry.new("to/feature")
    hash['feature'].any? { |fe| fe.include?(short_lookup_entry) }.should be_true

    exact_lookup_entry = Truffle::FeatureLoader::FeatureEntry.new("path/to/feature")
    hash['feature'].any? { |fe| fe.include?(exact_lookup_entry) }.should be_true

    longer_lookup_entry = Truffle::FeatureLoader::FeatureEntry.new("long/path/to/feature")
    hash['feature'].any? { |fe| fe.include?(longer_lookup_entry) }.should be_false

    prefix_lookup_entry = Truffle::FeatureLoader::FeatureEntry.new("path/to")
    hash['feature'].any? { |fe| fe.include?(prefix_lookup_entry) }.should be_false
  end

  describe "when stored has an extension" do
    it "matches a lookup with or without extension" do
      hash = {}
      stored_entry = Truffle::FeatureLoader::FeatureEntry.new("path/to/feature.so")
      hash['feature'] = [stored_entry]

      lookup_entry_no_ext = Truffle::FeatureLoader::FeatureEntry.new("to/feature")
      hash['feature'].any? { |fe| fe.include?(lookup_entry_no_ext) }.should be_true

      lookup_entry_ext = Truffle::FeatureLoader::FeatureEntry.new("to/feature.so")
      hash['feature'].any? { |fe| fe.include?(lookup_entry_ext) }.should be_true

      lookup_entry_wrong_ext = Truffle::FeatureLoader::FeatureEntry.new("to/feature.rb")
      hash['feature'].any? { |fe| fe.include?(lookup_entry_wrong_ext) }.should be_false
    end
  end
end
